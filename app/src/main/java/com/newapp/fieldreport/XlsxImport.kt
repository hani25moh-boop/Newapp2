package com.newapp.fieldreport

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

object XlsxImport {
    data class WorkbookData(val sheets: Map<String, List<List<String>>>)
    fun read(resolver: ContentResolver, uri: Uri): WorkbookData {
        val files = mutableMapOf<String, ByteArray>()
        resolver.openInputStream(uri)?.use { input -> ZipInputStream(input).use { zip -> var e = zip.nextEntry; while (e != null) { files[e.name] = zip.readBytes(); e = zip.nextEntry } } }
        val shared = files["xl/sharedStrings.xml"]?.let { parseShared(it) } ?: emptyList()
        val wb = files["xl/workbook.xml"] ?: return WorkbookData(emptyMap())
        val rels = files["xl/_rels/workbook.xml.rels"]?.let { parseRels(it) } ?: emptyMap()
        val doc = db(wb); val result = linkedMapOf<String, List<List<String>>>()
        val nodes = doc.getElementsByTagName("sheet")
        for (i in 0 until nodes.length) { val s = nodes.item(i) as Element; val name = s.getAttribute("name"); val rid = s.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id"); val target = rels[rid] ?: continue; val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"; files[path]?.let { result[name] = parseSheet(it, shared) } }
        return WorkbookData(result)
    }
    private fun db(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    private fun parseShared(bytes: ByteArray): List<String> { val d=db(bytes); val out=mutableListOf<String>(); val ns=d.getElementsByTagName("si"); for(i in 0 until ns.length){ val ts=(ns.item(i) as Element).getElementsByTagName("t"); val s=buildString{for(j in 0 until ts.length) append(ts.item(j).textContent)}; out+=s }; return out }
    private fun parseRels(bytes: ByteArray): Map<String,String> { val d=db(bytes); val out=mutableMapOf<String,String>(); val ns=d.getElementsByTagName("Relationship"); for(i in 0 until ns.length){ val e=ns.item(i) as Element; out[e.getAttribute("Id")]=e.getAttribute("Target") }; return out }
    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<List<String>> { val d=db(bytes); val rows=d.getElementsByTagName("row"); val out=mutableListOf<List<String>>(); for(i in 0 until rows.length){ val row=rows.item(i) as Element; val cells=row.getElementsByTagName("c"); val values=MutableList(32){""}; for(j in 0 until cells.length){ val c=cells.item(j) as Element; val ref=c.getAttribute("r"); val col=ref.takeWhile{it.isLetter()}.fold(0){a,ch->a*26+(ch.uppercaseChar()-'A'+1)}-1; val v=c.getElementsByTagName("v"); val raw=if(v.length>0)v.item(0).textContent else c.getElementsByTagName("t").itemOrNull(0)?.textContent.orEmpty(); values[col]=if(c.getAttribute("t")=="s") shared.getOrNull(raw.toIntOrNull()?:-1).orEmpty() else raw }; out+=values.dropLastWhile{it.isEmpty()} }; return out }
    private fun org.w3c.dom.NodeList.itemOrNull(i:Int)=if(i in 0 until length)item(i) else null
}
