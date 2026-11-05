package com.newapp.fieldreport

import android.content.ContentResolver
import android.net.Uri
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Minimal .xlsx reader. Supports the three ways Excel/our writer store text:
 *  - shared strings (t="s")     -> what Excel writes after you save a file
 *  - inline strings (t="inlineStr") -> what this app's exporter writes
 *  - formula strings (t="str")
 */
object XlsxImport {
    data class Book(val sheets: Map<String, List<List<String>>>)

    private const val MAX_COLS = 64
    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    fun read(r: ContentResolver, u: Uri): Book {
        val input = r.openInputStream(u) ?: return Book(emptyMap())
        return input.use { parse(it) }
    }

    fun parse(stream: java.io.InputStream): Book {
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(stream).use { z ->
            var e = z.nextEntry
            while (e != null) {
                files[e.name] = z.readBytes()
                e = z.nextEntry
            }
        }
        val shared = files["xl/sharedStrings.xml"]?.let { sharedStrings(it) } ?: emptyList()
        val rel = files["xl/_rels/workbook.xml.rels"]?.let { relationships(it) } ?: emptyMap()
        val wb = files["xl/workbook.xml"] ?: return Book(emptyMap())
        val doc = dom(wb)
        val out = linkedMapOf<String, List<List<String>>>()
        val nodes = doc.getElementsByTagName("sheet")
        for (i in 0 until nodes.length) {
            val s = nodes.item(i) as Element
            val id = s.getAttributeNS(REL_NS, "id")
            val target = rel[id] ?: continue
            val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
            files[path]?.let { out[s.getAttribute("name")] = sheet(it, shared) }
        }
        return Book(out)
    }

    private fun dom(b: ByteArray) =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(ByteArrayInputStream(b))

    private fun textOf(e: Element): String {
        val t = e.getElementsByTagName("t")
        return buildString { for (j in 0 until t.length) append(t.item(j).textContent) }
    }

    private fun sharedStrings(b: ByteArray): List<String> {
        val n = dom(b).getElementsByTagName("si")
        return List(n.length) { textOf(n.item(it) as Element) }
    }

    private fun relationships(b: ByteArray): Map<String, String> {
        val n = dom(b).getElementsByTagName("Relationship")
        val o = mutableMapOf<String, String>()
        for (i in 0 until n.length) {
            val e = n.item(i) as Element
            o[e.getAttribute("Id")] = e.getAttribute("Target")
        }
        return o
    }

    private fun valueOf(c: Element): String {
        val x = c.getElementsByTagName("v")
        return if (x.length > 0) x.item(0).textContent else ""
    }

    private fun sheet(b: ByteArray, shared: List<String>): List<List<String>> {
        val rows = dom(b).getElementsByTagName("row")
        val out = mutableListOf<List<String>>()
        for (i in 0 until rows.length) {
            val cells = (rows.item(i) as Element).getElementsByTagName("c")
            val v = MutableList(MAX_COLS) { "" }
            for (j in 0 until cells.length) {
                val c = cells.item(j) as Element
                val ref = c.getAttribute("r")
                val col = ref.takeWhile { it.isLetter() }
                    .fold(0) { a, ch -> a * 26 + (ch.uppercaseChar() - 'A') + 1 } - 1
                if (col !in v.indices) continue
                v[col] = when (c.getAttribute("t")) {
                    "s" -> shared.getOrNull(valueOf(c).trim().toIntOrNull() ?: -1).orEmpty()
                    "inlineStr" -> textOf(c)
                    else -> valueOf(c)
                }
            }
            out += v.dropLastWhile { it.isEmpty() }
        }
        return out
    }
}
