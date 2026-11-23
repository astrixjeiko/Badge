package com.astrix.badge

import android.content.Context
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportRow(
    val timestamp: Long,
    val note: String,
    val amount: Double,
    val category: String,
    val account: String
)

suspend fun loadExportRows(context: Context): List<ExportRow> {
    val dao = BadgeDatabase.get(context).dao()
    val categories = dao.allCategories().associateBy { it.id }
    val accounts = dao.allAccounts().associateBy { it.id }
    return dao.allExpenses().sortedBy { it.timestamp }.map { e ->
        ExportRow(
            e.timestamp,
            e.note,
            e.amount,
            categories[e.categoryId]?.name ?: "",
            accounts[e.accountId]?.name ?: ""
        )
    }
}

private fun amountHeader() = "Amount (" + CurrencyStore.symbol + ")"

// CSV. The BOM makes Excel read emoji and accents correctly; Google Sheets doesn't want it.
fun buildCsv(rows: List<ExportRow>, bom: Boolean): String {
    val zone = ZoneId.systemDefault()
    fun cell(text: String): String =
        if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + text.replace("\"", "\"\"") + "\""
        } else text

    val sb = StringBuilder()
    if (bom) sb.append('\uFEFF')
    sb.append("Date,Time,Name,").append(cell(amountHeader())).append(",Category,Account\r\n")
    rows.forEach { r ->
        val t = Instant.ofEpochMilli(r.timestamp).atZone(zone)
        sb.append(t.toLocalDate()).append(',')
            .append(String.format(Locale.US, "%02d:%02d", t.hour, t.minute)).append(',')
            .append(cell(r.note)).append(',')
            .append(String.format(Locale.US, "%.2f", r.amount)).append(',')
            .append(cell(r.category)).append(',')
            .append(cell(r.account)).append("\r\n")
    }
    return sb.toString()
}

private fun xml(text: String) =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private fun textCell(ref: String, text: String, style: Int = 0): String =
    "<c r=\"$ref\" s=\"$style\" t=\"inlineStr\"><is><t xml:space=\"preserve\">" +
            xml(text) + "</t></is></c>"

// Excel (.xlsx) is a zip of small XML files
fun buildXlsx(rows: List<ExportRow>): ByteArray {
    val zone = ZoneId.systemDefault()
    val sheet = StringBuilder()
    sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
    sheet.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
    sheet.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
    sheet.append("<cols>")
    listOf(12, 8, 30, 14, 18, 16).forEachIndexed { i, w ->
        sheet.append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$w\" customWidth=\"1\"/>")
    }
    sheet.append("</cols><sheetData><row r=\"1\">")
    listOf("Date", "Time", "Name", amountHeader(), "Category", "Account").forEachIndexed { i, h ->
        sheet.append(textCell(('A' + i) + "1", h, 1))
    }
    sheet.append("</row>")
    rows.forEachIndexed { i, r ->
        val n = i + 2
        val t = Instant.ofEpochMilli(r.timestamp).atZone(zone)
        val day = t.toLocalDate().toEpochDay() + 25569           // Excel date number
        val time = t.toLocalTime().toSecondOfDay() / 86400.0       // fraction of a day
        sheet.append("<row r=\"$n\">")
        sheet.append("<c r=\"A$n\" s=\"2\"><v>$day</v></c>")
        sheet.append("<c r=\"B$n\" s=\"3\"><v>$time</v></c>")
        sheet.append(textCell("C$n", r.note))
        sheet.append("<c r=\"D$n\" s=\"4\"><v>${r.amount}</v></c>")
        if (r.category.isNotEmpty()) sheet.append(textCell("E$n", r.category))
        if (r.account.isNotEmpty()) sheet.append(textCell("F$n", r.account))
        sheet.append("</row>")
    }
    sheet.append("</sheetData></worksheet>")

    val head = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
    val main = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    val rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    val pkgRel = "http://schemas.openxmlformats.org/package/2006/relationships"

    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        fun add(name: String, text: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(text.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        add(
            "[Content_Types].xml",
            head + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                    "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                    "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                    "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                    "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                    "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
                    "</Types>"
        )
        add(
            "_rels/.rels",
            head + "<Relationships xmlns=\"$pkgRel\"><Relationship Id=\"rId1\" Type=\"$rel/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>"
        )
        add(
            "xl/workbook.xml",
            head + "<workbook xmlns=\"$main\" xmlns:r=\"$rel\"><sheets><sheet name=\"Expenses\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"
        )
        add(
            "xl/_rels/workbook.xml.rels",
            head + "<Relationships xmlns=\"$pkgRel\">" +
                    "<Relationship Id=\"rId1\" Type=\"$rel/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                    "<Relationship Id=\"rId2\" Type=\"$rel/styles\" Target=\"styles.xml\"/></Relationships>"
        )
        add(
            "xl/styles.xml",
            head + "<styleSheet xmlns=\"$main\">" +
                    "<numFmts count=\"3\"><numFmt numFmtId=\"164\" formatCode=\"yyyy\\-mm\\-dd\"/>" +
                    "<numFmt numFmtId=\"165\" formatCode=\"hh:mm\"/><numFmt numFmtId=\"166\" formatCode=\"#,##0.00\"/></numFmts>" +
                    "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
                    "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>" +
                    "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
                    "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
                    "<cellXfs count=\"5\">" +
                    "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
                    "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
                    "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
                    "<xf numFmtId=\"165\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
                    "<xf numFmtId=\"166\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
                    "</cellXfs></styleSheet>"
        )
        add("xl/worksheets/sheet1.xml", sheet.toString())
    }
    return out.toByteArray()
}