package com.newapp.fieldreport
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
object XlsxWriter{
 private const val H="<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
 fun write(file:File,headers:List<String>,rows:List<List<Any>>){ZipOutputStream(FileOutputStream(file)).use{z->add(z,"[Content_Types].xml",types());add(z,"_rels/.rels",root());add(z,"xl/workbook.xml",book());add(z,"xl/_rels/workbook.xml.rels",bookRel());add(z,"xl/worksheets/sheet1.xml",sheet(headers,rows))}}
 private fun add(z:ZipOutputStream,n:String,s:String){z.putNextEntry(ZipEntry(n));z.write(s.toByteArray());z.closeEntry()}
 private fun types()=H+"<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>"
 private fun root()=H+"<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>"
 private fun book()=H+"<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Entries\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"
 private fun bookRel()=H+"<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>"
 private fun sheet(h:List<String>,rows:List<List<Any>>):String{val s=StringBuilder(H+"<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView rightToLeft=\"1\" workbookViewId=\"0\"/></sheetViews><sheetData>");row(s,1,h);rows.forEachIndexed{i,r->row(s,i+2,r)};return s.append("</sheetData></worksheet>").toString()}
 private fun row(s:StringBuilder,n:Int,v:List<Any>){s.append("<row r=\"$n\">");v.forEachIndexed{i,x->val ref="${('A'.code+i).toChar()}$n";if(x is Number)s.append("<c r=\"$ref\"><v>$x</v></c>")else s.append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(x.toString())}</t></is></c>")};s.append("</row>")}
 private fun esc(x:String)=x.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
}
