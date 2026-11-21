package com.meloberry.android.core.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meloberry.android.core.model.ContentAlign
import com.meloberry.android.core.ui.theme.LocalMeloberryColors
import com.meloberry.android.core.ui.theme.MeloberryColors

private val NUMBERED_ITEM_REGEX = Regex("^\\d+\\.\\s+.*")
private val TABLE_SEPARATOR_REGEX = Regex("^\\|?(?:\\s*:?-{3,}:?\\s*\\|)+\\s*:?-{3,}:?\\s*\\|?$")

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class ListItem(val prefix: String, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class CodeBlock(val code: String) : MdBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdBlock
    data object Divider : MdBlock
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    baseFontSizeSp: Int = 16,
    contentAlign: ContentAlign = ContentAlign.LEFT,
    isBoldByDefault: Boolean = false
) {
    val palette = LocalMeloberryColors.current
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val textAlign = when (contentAlign) {
        ContentAlign.LEFT -> TextAlign.Start
        ContentAlign.CENTER -> TextAlign.Center
        ContentAlign.RIGHT -> TextAlign.End
    }
    val columnAlign = when (contentAlign) {
        ContentAlign.LEFT -> Alignment.Start
        ContentAlign.CENTER -> Alignment.CenterHorizontally
        ContentAlign.RIGHT -> Alignment.End
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = columnAlign
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val scale = when (block.level) {
                        1 -> 1.45f
                        2 -> 1.25f
                        3 -> 1.12f
                        else -> 1.04f
                    }
                    Text(
                        text = remember(block.text, palette) { buildInlineAnnotated(block.text, palette) },
                        color = palette.text,
                        fontSize = (baseFontSizeSp * scale).sp,
                        lineHeight = (baseFontSizeSp * scale * 1.3f).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = textAlign,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is MdBlock.Paragraph -> {
                    Text(
                        text = remember(block.text, palette) { buildInlineAnnotated(block.text, palette) },
                        color = palette.text,
                        fontSize = baseFontSizeSp.sp,
                        lineHeight = (baseFontSizeSp * 1.45f).sp,
                        fontWeight = if (isBoldByDefault) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = textAlign,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is MdBlock.ListItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = block.prefix,
                            color = palette.accent,
                            fontSize = baseFontSizeSp.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = remember(block.text, palette) { buildInlineAnnotated(block.text, palette) },
                            color = palette.text,
                            fontSize = baseFontSizeSp.sp,
                            lineHeight = (baseFontSizeSp * 1.45f).sp,
                            textAlign = textAlign,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MdBlock.Quote -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.accentSoft.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .border(1.dp, palette.accent.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = remember(block.text, palette) { buildInlineAnnotated(block.text, palette) },
                            color = palette.muted,
                            fontSize = baseFontSizeSp.sp,
                            fontStyle = FontStyle.Italic,
                            textAlign = textAlign,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is MdBlock.CodeBlock -> {
                    val codeSize = (baseFontSizeSp - 2).coerceAtLeast(12)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (palette.isDark) Color(0xFF0B0D12) else Color(0xFFF3EFF2),
                                RoundedCornerShape(8.dp)
                            )
                            .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = block.code,
                            color = palette.text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = codeSize.sp,
                            lineHeight = (codeSize * 1.4f).sp
                        )
                    }
                }
                is MdBlock.Table -> {
                    MarkdownTableBlock(
                        table = block,
                        baseFontSizeSp = baseFontSizeSp,
                        palette = palette
                    )
                }
                MdBlock.Divider -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .height(1.dp)
                            .background(palette.border)
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownTableBlock(
    table: MdBlock.Table,
    baseFontSizeSp: Int,
    palette: MeloberryColors
) {
    val colCount = maxOf(table.headers.size, table.rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    val cellFontSize = (baseFontSizeSp - 1).coerceAtLeast(11)
    val colWidths = remember(table, cellFontSize) {
        List(colCount) { col ->
            val maxChars = maxOf(
                table.headers.getOrNull(col)?.length ?: 0,
                table.rows.maxOfOrNull { it.getOrNull(col)?.length ?: 0 } ?: 0
            )
            (maxChars * cellFontSize * 0.62f + 22f).dp.coerceIn(44.dp, 260.dp)
        }
    }
    val shape = RoundedCornerShape(8.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, palette.border, shape)
            .horizontalScroll(rememberScrollState())
    ) {
        Row(modifier = Modifier.background(palette.accentSoft.copy(alpha = 0.65f))) {
            repeat(colCount) { col ->
                val text = table.headers.getOrNull(col).orEmpty()
                Text(
                    text = remember(text, palette) { buildInlineAnnotated(text, palette) },
                    color = palette.text,
                    fontSize = cellFontSize.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .width(colWidths[col])
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
        table.rows.forEach { row ->
            HorizontalDivider(color = palette.border)
            Row {
                repeat(colCount) { col ->
                    val text = row.getOrNull(col).orEmpty()
                    Text(
                        text = remember(text, palette) { buildInlineAnnotated(text, palette) },
                        color = palette.text,
                        fontSize = cellFontSize.sp,
                        lineHeight = (cellFontSize * 1.35f).sp,
                        modifier = Modifier
                            .width(colWidths[col])
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

private fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    val lines = raw.replace("\r\n", "\n").split('\n')
    val result = mutableListOf<MdBlock>()
    val paragraphBuffer = mutableListOf<String>()
    val quoteBuffer = mutableListOf<String>()
    val codeBuffer = mutableListOf<String>()
    var inCodeBlock = false

    fun flushParagraph() {
        if (paragraphBuffer.isNotEmpty()) {
            result += MdBlock.Paragraph(paragraphBuffer.joinToString("\n"))
            paragraphBuffer.clear()
        }
    }

    fun flushQuote() {
        if (quoteBuffer.isNotEmpty()) {
            result += MdBlock.Quote(quoteBuffer.joinToString("\n"))
            quoteBuffer.clear()
        }
    }

    fun flushAll() {
        flushParagraph()
        flushQuote()
    }

    var idx = 0
    while (idx < lines.size) {
        val line = lines[idx]
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            flushAll()
            if (inCodeBlock) {
                result += MdBlock.CodeBlock(codeBuffer.joinToString("\n"))
                codeBuffer.clear()
                inCodeBlock = false
            } else {
                inCodeBlock = true
            }
            idx++
            continue
        }

        if (inCodeBlock) {
            codeBuffer += line
            idx++
            continue
        }

        if (trimmed.isEmpty()) {
            flushAll()
            idx++
            continue
        }

        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            flushAll()
            result += MdBlock.Divider
            idx++
            continue
        }

        val headingHashes = trimmed.takeWhile { it == '#' }.length
        if (headingHashes in 1..6 && trimmed.length > headingHashes && trimmed[headingHashes] == ' ') {
            flushAll()
            result += MdBlock.Heading(headingHashes, trimmed.substring(headingHashes + 1).trim())
            idx++
            continue
        }

        if (isPipeTableRow(trimmed) && idx + 1 < lines.size && TABLE_SEPARATOR_REGEX.matches(lines[idx + 1].trim())) {
            flushAll()
            val headers = parsePipeCells(trimmed)
            val rows = mutableListOf<List<String>>()
            idx += 2
            while (idx < lines.size && isPipeTableRow(lines[idx].trim())) {
                rows += parsePipeCells(lines[idx].trim())
                idx++
            }
            result += MdBlock.Table(headers, rows)
            continue
        }

        when {
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushAll()
                result += MdBlock.ListItem("•", trimmed.drop(2).trim())
            }
            NUMBERED_ITEM_REGEX.matches(trimmed) -> {
                flushAll()
                val dot = trimmed.indexOf('.')
                result += MdBlock.ListItem("${trimmed.substring(0, dot)}.", trimmed.substring(dot + 1).trim())
            }
            trimmed.startsWith(">") -> {
                flushParagraph()
                quoteBuffer += trimmed.removePrefix(">").trimStart()
            }
            else -> {
                flushQuote()
                paragraphBuffer += trimmed
            }
        }
        idx++
    }

    if (inCodeBlock && codeBuffer.isNotEmpty()) {
        result += MdBlock.CodeBlock(codeBuffer.joinToString("\n"))
    }
    flushAll()
    return result
}

private fun isPipeTableRow(trimmed: String): Boolean =
    trimmed.startsWith('|') && trimmed.indexOf('|', 1) > 0

private fun parsePipeCells(trimmed: String): List<String> =
    trimmed.removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

private fun findClosingMarker(text: String, marker: String, startIndex: Int): Int {
    var i = startIndex
    while (i <= text.length - marker.length) {
        if (text[i] == '`') {
            val codeEnd = text.indexOf('`', i + 1)
            if (codeEnd > i) {
                i = codeEnd + 1
                continue
            }
        }
        if (marker.length == 1 && text[i] == marker[0]) {
            var run = 0
            while (i + run < text.length && text[i + run] == marker[0]) run++
            if (run > 1) {
                i += run
                continue
            }
            if (marker[0] != '_' || i + 1 == text.length || !text[i + 1].isLetterOrDigit()) {
                return i
            }
        } else if (text.startsWith(marker, i)) {
            return i
        }
        i++
    }
    return -1
}

private fun buildInlineAnnotated(text: String, palette: MeloberryColors): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("***", i) -> {
                    val end = findClosingMarker(text, "***", i + 3)
                    if (end > i + 3) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                            append(buildInlineAnnotated(text.substring(i + 3, end), palette))
                        }
                        i = end + 3
                    } else {
                        append("***")
                        i += 3
                    }
                }
                text.startsWith("**", i) || text.startsWith("__", i) -> {
                    val delim = text.substring(i, i + 2)
                    val end = findClosingMarker(text, delim, i + 2)
                    if (end > i + 2) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(buildInlineAnnotated(text.substring(i + 2, end), palette))
                        }
                        i = end + 2
                    } else {
                        append(delim)
                        i += 2
                    }
                }
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i + 1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = palette.accentSoft,
                                color = palette.accent
                            )
                        ) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append('`')
                        i++
                    }
                }
                text[i] == '*' -> {
                    val end = findClosingMarker(text, "*", i + 1)
                    if (end > i + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(buildInlineAnnotated(text.substring(i + 1, end), palette))
                        }
                        i = end + 1
                    } else {
                        append('*')
                        i++
                    }
                }
                text[i] == '_' && (i == 0 || !text[i - 1].isLetterOrDigit()) -> {
                    val end = findClosingMarker(text, "_", i + 1)
                    if (end > i + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(buildInlineAnnotated(text.substring(i + 1, end), palette))
                        }
                        i = end + 1
                    } else {
                        append('_')
                        i++
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
