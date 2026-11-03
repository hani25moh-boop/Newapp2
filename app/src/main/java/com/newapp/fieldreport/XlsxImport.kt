package com.newapp.fieldreport
import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
object XlsxImport{
 data class Book(val sheets:Map<String,List<List<String>>>)
 fun read(r:ContentResolver,u:Uri):Book{val files=mutableMapOf<String,ByteArray>();r.openInputStream(u)?.use{ZipInputStream(it).use{z->var e=z.nextEntry;while(e!=null){files[e.name]=z.readBytes();e=z.nextEntry}}};val shared=files["xl/sharedStrings.xml"]?.let{shared(it)}?:emptyList();val rel=files["xl/_rels/workbook.xml.rels"]?.let{rels(it)}?:emptyMap();val wb=files["xl/workbook.xml"]?:return Book(emptyMap());val d=db(wb);val out=linkedMapOf<String,List<List<String>>>();val ns=d.getElementsByTagName("sheet");for(i in 0 until ns.length){val s=ns.item(i) as Element;val id=s.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships","id");val target=rel[id]?:continue;val path=if(target.startsWith("/"))target.removePrefix("/") else "xl/$target";files[path]?.let{out[s.getAttribute("name")]=sheet(it,shared)}};return Book(out)}
 private fun db(b:ByteArray)=DocumentBuilderFactory.newInstance().apply{isNamespaceAware=true}.newDocumentBuilder().parse(ByteArrayInputStream(b))
 private fun shared(b:ByteArray):List<String>{val d=db(b);val out=mutableListOf<String>();val n=d.getElementsByTagName("si");for(i in 0 until n.length){val t=(n.item(i) as Element).getElementsByTagName("t");out+=buildString{for(j in 0 until t.length)append(t.item(j).textContent)}};return out}
 private fun rels(b:ByteArray):Map<String,String>{val d=db(b);val o=mutableMapOf<String,String>();val n=d.getElementsByTagName("Relationship");for(i in 0 until n.length){val e=n.item(i) as Element;o[e.getAttribute("Id")]=e.getAttribute("Target")};return o}
 private fun sheet(b:ByteArray,shared:List<String>):List<List<String>>{val d=db(b);val rows=d.getElementsByTagName("row");val out=mutableListOf<List<String>>();for(i in 0 until rows.length){val cs=(rows.item(i) as Element).getElementsByTagName("c");val v=MutableList(26){""};for(j in 0 until cs.length){val c=cs.item(j) as Element;val ref=c.getAttribute("r");val col=ref.takeWhile{it.isLetter()}.fold(0){a,ch->a*26+(ch.uppercaseChar()-'A')+1}-1;val x=c.getElementsByTagName("v");val raw=if(x.length>0)x.item(0).textContent else "";v[col]=if(c.getAttribute("t")=="s")shared.getOrNull(raw.toIntOrNull()?:-1).orEmpty() else raw};out+=v.dropLastWhile{it.isEmpty()}};return out}
}
