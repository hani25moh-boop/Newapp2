package com.newapp.fieldreport

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal .xlsx writer (no external libraries).
 * Strings are written as inline strings, numbers as numeric cells.
 */
object XlsxWriter {

    private const val XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"

    fun write(file: File, headers: List<String>, rows: List<List<Any>>) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            addEntry(zip, "[Content_Types].xml", contentTypes())
            addEntry(zip, "_rels/.rels", rootRels())
            addEntry(zip, "xl/workbook.xml", workbook())
            addEntry(zip, "xl/_rels/workbook.xml.rels", workbookRels())
            addEntry(zip, "xl/worksheets/sheet1.xml", sheetXml(headers, rows))
        }
    }

    private fun addEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun contentTypes(): String =
        XML_HEADER +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
            "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
            "</Types>"

    private fun rootRels(): String =
        XML_HEADER +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "</Relationships>"

    private fun workbook(): String =
        XML_HEADER +
            "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
            "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
            "<sheets><sheet name=\"Report\" sheetId=\"1\" r:id=\"rId1\"/></sheets>" +
            "</workbook>"

    private fun workbookRels(): String =
        XML_HEADER +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
            "</Relationships>"

    private fun sheetXml(headers: List<String>, rows: List<List<Any>>): String {
        val sb = StringBuilder()
        sb.append(XML_HEADER)
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        sb.append("<sheetViews><sheetView rightToLeft=\"1\" workbookViewId=\"0\"/></sheetViews>")
        sb.append("<cols>")
        val widths = listOf(14, 28, 10, 12, 40)
        for (i in headers.indices) {
            val w = widths.getOrElse(i) { 16 }
            sb.append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$w\" customWidth=\"1\"/>")
        }
        sb.append("</cols><sheetData>")
        appendRow(sb, 1, headers)
        rows.forEachIndexed { index, row -> appendRow(sb, index + 2, row) }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun appendRow(sb: StringBuilder, rowNumber: Int, cells: List<Any>) {
        sb.append("<row r=\"$rowNumber\">")
        cells.forEachIndexed { col, value ->
            val ref = "${'A' + col}$rowNumber"
            if (value is Number) {
                sb.append("<c r=\"$ref\"><v>$value</v></c>")
            } else {
                sb.append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                sb.append(escape(value.toString()))
                sb.append("</t></is></c>")
            }
        }
        sb.append("</row>")
    }

    private fun escape(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            when {
                c == '&' -> sb.append("&amp;")
                c == '<' -> sb.append("&lt;")
                c == '>' -> sb.append("&gt;")
                c == '"' -> sb.append("&quot;")
                c == '\'' -> sb.append("&apos;")
                c.code < 32 && c != '\n' && c != '\t' && c != '\r' -> Unit
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
