package com.newapp.fieldreport

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object XlsxWriter {
    private const val H = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"

    val ENTRY_HEADERS = listOf(
        "Record_ID", "Role", "Record_Type", "Project_ID", "Item_ID", "Item_Name", "Unit",
        "Quantity", "Hours", "Status", "Priority", "Person_Code", "Person_Name", "Recorded_At", "Notes",
        "Person_Title", "Report_Date", "Latitude", "Longitude", "Photo_Ref",
        "Link_Type", "Link_Person", "Link_Item_Code", "Link_Item_Name"
    )
    val ITEM_HEADERS = listOf("Item_ID", "Item_Name", "Item_Name_AR", "Unit", "Category", "Kind")
    val ROLE_HEADERS = listOf("Role_Code", "Role_Name_AR", "Role_Name_EN")

    private class Sheet(val name: String, val headers: List<String>, val rows: List<List<Any>>)

    fun write(file: File, p: List<Project>, r: List<Role>, i: List<Item>, e: List<Entry>) {
        val sheets = listOf(
            Sheet("Projects", listOf("Project_ID", "Project_Name", "Project_Name_AR"),
                p.map { listOf<Any>(it.code, it.name, it.nameAr) }),
            Sheet("Roles", ROLE_HEADERS, r.map { listOf<Any>(it.code, it.nameAr, it.nameEn) }),
            Sheet("Items", ITEM_HEADERS,
                i.map { listOf<Any>(it.code, it.name, it.nameAr, it.unit, it.category, it.kind) }),
            Sheet("Entries", ENTRY_HEADERS, e.map {
                listOf<Any>(
                    it.id, it.role, it.recordType, it.projectCode, it.itemCode, it.itemName, it.unit,
                    it.quantity ?: "", it.hours ?: "", it.status, it.priority, it.personCode, it.personName,
                    it.eventAt, it.notes, it.personTitle, it.reportDate, it.latitude ?: "", it.longitude ?: "",
                    it.photoRef, it.linkType, it.linkPerson, it.linkItemCode, it.linkItemName
                )
            })
        )
        ZipOutputStream(FileOutputStream(file)).use { z ->
            add(z, "[Content_Types].xml", types(sheets.size))
            add(z, "_rels/.rels", root())
            add(z, "xl/workbook.xml", book(sheets))
            add(z, "xl/_rels/workbook.xml.rels", bookRel(sheets.size))
            sheets.forEachIndexed { n, sh -> add(z, "xl/worksheets/sheet${n + 1}.xml", sheet(sh)) }
        }
    }

    private fun add(z: ZipOutputStream, n: String, s: String) {
        z.putNextEntry(ZipEntry(n)); z.write(s.toByteArray(Charsets.UTF_8)); z.closeEntry()
    }

    private fun types(n: Int) = H + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
        (1..n).joinToString("") { "<Override PartName=\"/xl/worksheets/sheet$it.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" } +
        "</Types>"

    private fun root() = H + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>"

    private fun book(s: List<Sheet>) = H +
        "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>" +
        s.mapIndexed { n, x -> "<sheet name=\"${x.name}\" sheetId=\"${n + 1}\" r:id=\"rId${n + 1}\"/>" }.joinToString("") +
        "</sheets></workbook>"

    private fun bookRel(n: Int) = H + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        (1..n).joinToString("") { "<Relationship Id=\"rId$it\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet$it.xml\"/>" } +
        "</Relationships>"

    private fun sheet(sh: Sheet): String {
        val s = StringBuilder(H + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView rightToLeft=\"1\" workbookViewId=\"0\"/></sheetViews><sheetData>")
        row(s, 1, sh.headers)
        sh.rows.forEachIndexed { x, r -> row(s, x + 2, r) }
        return s.append("</sheetData></worksheet>").toString()
    }

    /** Column letters for any index: 0 -> A, 25 -> Z, 26 -> AA ... */
    internal fun col(index: Int): String {
        var n = index
        val s = StringBuilder()
        do { s.insert(0, 'A' + n % 26); n = n / 26 - 1 } while (n >= 0)
        return s.toString()
    }

    private fun row(s: StringBuilder, n: Int, v: List<Any>) {
        s.append("<row r=\"$n\">")
        v.forEachIndexed { x, a ->
            val ref = "${col(x)}$n"
            if (a is Number) s.append("<c r=\"$ref\"><v>$a</v></c>")
            else s.append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(a.toString())}</t></is></c>")
        }
        s.append("</row>")
    }

    private fun esc(x: String) = buildString {
        for (ch in x) {
            when {
                ch == '&' -> append("&amp;")
                ch == '<' -> append("&lt;")
                ch == '>' -> append("&gt;")
                ch == '"' -> append("&quot;")
                ch == '\'' -> append("&apos;")
                ch.code < 0x20 && ch != '\t' && ch != '\n' && ch != '\r' -> {}
                else -> append(ch)
            }
        }
    }
}
