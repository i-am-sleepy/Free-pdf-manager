package com.freepdf.manager.pdf

import org.apache.poi.xssf.usermodel.XSSFCell
import org.apache.poi.xssf.usermodel.XSSFRow
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFShape
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File

/**
 * Offline office-document conversion (docx / pptx / xlsx -> text), built on
 * Apache POI. The extracted text is then turned into a PDF by [PdfEngine].
 *
 * Everything runs on-device — no network. POI 5.3.0 (verified against the
 * official source jars: XWPFDocument.getParagraphs(), XMLSlideShow.getSlides(),
 * XSSFSheet.getRow()/getLastRowNum(), POIXMLDocument.close()).
 */
object OfficeConverter {

    /** Detects the office kind from a file name. */
    enum class Kind { DOCX, PPTX, XLSX, UNKNOWN }

    fun kindOf(name: String): Kind = when (name.substringAfterLast('.', "").lowercase()) {
        "docx" -> Kind.DOCX
        "pptx" -> Kind.PPTX
        "xlsx" -> Kind.XLSX
        else -> Kind.UNKNOWN
    }

    /** Extracts readable text from an office document. */
    fun extractText(file: File): String {
        val kind = kindOf(file.name)
        return when (kind) {
            Kind.DOCX -> extractDocx(file)
            Kind.PPTX -> extractPptx(file)
            Kind.XLSX -> extractXlsx(file)
            Kind.UNKNOWN -> throw IllegalArgumentException("Unsupported file: ${file.name}")
        }
    }

    // region DOCX

    private fun extractDocx(file: File): String {
        val sb = StringBuilder()
        XWPFDocument(file.inputStream()).use { doc ->
            for (para in doc.paragraphs) {
                val t = para.text
                if (t.isNotBlank()) {
                    sb.append(t).append('\n')
                }
            }
        }
        return sb.toString().trim()
    }

    // endregion

    // region PPTX

    private fun extractPptx(file: File): String {
        val sb = StringBuilder()
        XMLSlideShow(file.inputStream()).use { show ->
            show.slides.forEachIndexed { index, slide ->
                sb.append("Slide ${index + 1}\n")
                collectShapeText(slide.shapes, sb, depth = 0)
                sb.append('\n')
            }
        }
        return sb.toString().trim()
    }

    private fun collectShapeText(shapes: List<XSLFShape>, sb: StringBuilder, depth: Int) {
        for (shape in shapes) {
            if (shape is XSLFTextShape) {
                val t = shape.text
                if (t.isNotBlank()) {
                    repeat(depth) { sb.append("  ") }
                    sb.append(t.trim()).append('\n')
                }
            }
            // recurse into groups
            try {
                val sub = shape.shapes
                if (sub.isNotEmpty()) {
                    collectShapeText(sub, sb, depth + 1)
                }
            } catch (e: Exception) {
                // not a container
            }
        }
    }

    // endregion

    // region XLSX

    private fun extractXlsx(file: File): String {
        val sb = StringBuilder()
        XSSFWorkbook(file.inputStream()).use { wb ->
            for (i in 0 until wb.numberOfSheets) {
                val sheet: XSSFSheet = wb.getSheetAt(i)
                sb.append("Sheet: ${sheet.sheetName}\n")
                for (r in sheet.firstRowNum..sheet.lastRowNum) {
                    val row: XSSFRow? = sheet.getRow(r) ?: continue
                    val cells = mutableListOf<String>()
                    for (c in 0..(row.lastCellNum.toInt() - 1)) {
                        val cell = row.getCell(c)
                        cells.add(cellValue(cell))
                    }
                    if (cells.any { it.isNotBlank() }) {
                        sb.append(cells.joinToString("\t")).append('\n')
                    }
                }
                sb.append('\n')
            }
        }
        return sb.toString().trim()
    }

    private fun cellValue(cell: XSSFCell?): String {
        if (cell == null) return ""
        return try {
            when (cell.cellType) {
                org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue
                org.apache.poi.ss.usermodel.CellType.NUMERIC ->
                    if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                        cell.dateCellValue.toInstant().toString()
                    } else {
                        val d = cell.numericCellValue
                        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
                    }
                org.apache.poi.ss.usermodel.CellType.BOOLEAN -> cell.booleanCellValue.toString()
                org.apache.poi.ss.usermodel.CellType.FORMULA ->
                    try { cell.stringCellValue } catch (e: Exception) { cell.numericCellValue.toString() }
                else -> ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    // endregion
}
