package com.meloberry.android.core.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meloberry.android.core.model.ContentAlign
import com.meloberry.android.core.ui.theme.LocalMeloberryColors
import com.meloberry.android.core.ui.theme.MeloberryColors

private val NUMBERED_ITEM_REGEX = Regex("^\\d+\\.\\s+.*")
private val TASK_ITEM_REGEX = Regex("^[-*+]\\s+\\[([ xX])]\\s+(.*)$")

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class ListItem(val prefix: String, val text: String, val indentLevel: Int = 0) : MdBlock
    data class TaskItem(val checked: Boolean, val text: String, val indentLevel: Int = 0) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class CodeBlock(val code: String) : MdBlock
    data class DisplayMath(val tex: String) : MdBlock
    data class Table(
        val headers: List<String>,
        val alignments: List<TableColAlign>,
        val rows: List<List<String>>
    ) : MdBlock
    data object Divider : MdBlock
}

private data class ParsedInlineMarkdown(
    val annotated: AnnotatedString,
    val mathFormulas: Map<String, String>
)

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    baseFontSizeSp: Int = 16,
    promptFontSizeSp: Int? = null,
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
                    val headingSp = headingSizeSp(block.level, baseFontSizeSp, promptFontSizeSp)
                    val parsed = remember(block.text, palette) { buildInlineMarkdown(block.text, palette) }
                    val inlineContent = rememberInlineMathContent(parsed.mathFormulas, headingSp.toInt().coerceAtLeast(12), palette.text)
                    Text(
                        text = parsed.annotated,
                        inlineContent = inlineContent,
                        color = palette.text,
                        fontSize = headingSp.sp,
                        lineHeight = (headingSp * 1.28f).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = textAlign,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is MdBlock.Paragraph -> {
                    val parsed = remember(block.text, palette) { buildInlineMarkdown(block.text, palette) }
                    val inlineContent = rememberInlineMathContent(parsed.mathFormulas, baseFontSizeSp, palette.text)
                    Text(
                        text = parsed.annotated,
                        inlineContent = inlineContent,
                        color = palette.text,
                        fontSize = baseFontSizeSp.sp,
                        lineHeight = (baseFontSizeSp * 1.45f).sp,
                        fontWeight = if (isBoldByDefault) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = textAlign,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is MdBlock.ListItem -> {
                    val parsed = remember(block.text, palette) { buildInlineMarkdown(block.text, palette) }
                    val inlineContent = rememberInlineMathContent(parsed.mathFormulas, baseFontSizeSp, palette.text)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (block.indentLevel * 16).dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = block.prefix,
                            color = palette.accent,
                            fontSize = baseFontSizeSp.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = parsed.annotated,
                            inlineContent = inlineContent,
                            color = palette.text,
                            fontSize = baseFontSizeSp.sp,
                            lineHeight = (baseFontSizeSp * 1.45f).sp,
                            textAlign = textAlign,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MdBlock.TaskItem -> {
                    val parsed = remember(block.text, palette) { buildInlineMarkdown(block.text, palette) }
                    val inlineContent = rememberInlineMathContent(parsed.mathFormulas, baseFontSizeSp, palette.text)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (block.indentLevel * 16).dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = if (block.checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                            contentDescription = if (block.checked) "Checked" else "Unchecked",
                            tint = if (block.checked) palette.accent else palette.muted,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(18.dp)
                        )
                        Text(
                            text = parsed.annotated,
                            inlineContent = inlineContent,
                            color = if (block.checked) palette.muted else palette.text,
                            fontSize = baseFontSizeSp.sp,
                            lineHeight = (baseFontSizeSp * 1.45f).sp,
                            textAlign = textAlign,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MdBlock.Quote -> {
                    val parsed = remember(block.text, palette) { buildInlineMarkdown(block.text, palette) }
                    val inlineContent = rememberInlineMathContent(parsed.mathFormulas, baseFontSizeSp, palette.text)
                    val shape = RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .clip(shape)
                            .background(palette.accentSoft)
                            .border(1.dp, palette.border, shape)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(palette.accent)
                        )
                        Text(
                            text = parsed.annotated,
                            inlineContent = inlineContent,
                            color = palette.text,
                            fontSize = baseFontSizeSp.sp,
                            lineHeight = (baseFontSizeSp * 1.45f).sp,
                            fontStyle = FontStyle.Italic,
                            textAlign = textAlign,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
                is MdBlock.CodeBlock -> {
                    val codeSize = (baseFontSizeSp - 2).coerceAtLeast(12)
                    val shape = RoundedCornerShape(12.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .clip(shape)
                            .background(if (palette.isDark) Color(0xFF0B0D12) else palette.accentSoft)
                            .border(1.dp, palette.border, shape)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(palette.accent)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = block.code,
                                color = palette.text,
                                fontFamily = FontFamily.Monospace,
                                fontSize = codeSize.sp,
                                lineHeight = (codeSize * 1.45f).sp
                            )
                        }
                    }
                }
                is MdBlock.DisplayMath -> {
                    DisplayMathBlock(
                        tex = block.tex,
                        baseFontSizeSp = baseFontSizeSp,
                        color = palette.text,
                        contentAlign = contentAlign
                    )
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

private fun headingSizeSp(level: Int, baseFontSizeSp: Int, promptFontSizeSp: Int?): Float {
    if (promptFontSizeSp != null) {
        val ratio = when (level) {
            1 -> 1.25f
            2 -> 1.00f
            3 -> 0.88f
            4 -> 0.80f
            else -> 0.76f
        }
        return promptFontSizeSp * ratio
    }
    val scale = when (level) {
        1 -> 1.45f
        2 -> 1.25f
        3 -> 1.12f
        else -> 1.04f
    }
    return baseFontSizeSp * scale
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
            (maxChars * cellFontSize * 0.62f + 22f).dp.coerceIn(52.dp, 260.dp)
        }
    }
    val shape = RoundedCornerShape(10.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, palette.border, shape)
            .horizontalScroll(rememberScrollState())
    ) {
        Row(modifier = Modifier.background(palette.accentSoft)) {
            repeat(colCount) { col ->
                val raw = table.headers.getOrNull(col).orEmpty()
                val parsed = remember(raw, palette) { buildInlineMarkdown(raw, palette) }
                val inlineContent = rememberInlineMathContent(parsed.mathFormulas, cellFontSize, palette.accent)
                Text(
                    text = parsed.annotated,
                    inlineContent = inlineContent,
                    color = palette.accent,
                    fontSize = cellFontSize.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = table.alignments.getOrNull(col).toTextAlign(),
                    modifier = Modifier
                        .width(colWidths[col])
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
        table.rows.forEachIndexed { rowIdx, row ->
            HorizontalDivider(color = palette.border)
            val rowBg = if (rowIdx % 2 == 1) palette.background else palette.surface
            Row(modifier = Modifier.background(rowBg)) {
                repeat(colCount) { col ->
                    val raw = row.getOrNull(col).orEmpty()
                    val parsed = remember(raw, palette) { buildInlineMarkdown(raw, palette) }
                    val inlineContent = rememberInlineMathContent(parsed.mathFormulas, cellFontSize, palette.text)
                    Text(
                        text = parsed.annotated,
                        inlineContent = inlineContent,
                        color = palette.text,
                        fontSize = cellFontSize.sp,
                        lineHeight = (cellFontSize * 1.35f).sp,
                        textAlign = table.alignments.getOrNull(col).toTextAlign(),
                        modifier = Modifier
                            .width(colWidths[col])
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

private fun TableColAlign?.toTextAlign(): TextAlign = when (this) {
    TableColAlign.CENTER -> TextAlign.Center
    TableColAlign.RIGHT -> TextAlign.End
    else -> TextAlign.Start
}

private fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    val lines = raw.replace("\r\n", "\n").split('\n')
    val result = mutableListOf<MdBlock>()
    val paragraphBuffer = mutableListOf<String>()
    val quoteBuffer = mutableListOf<String>()
    val codeBuffer = mutableListOf<String>()
    val mathBuffer = mutableListOf<String>()
    var inCodeBlock = false
    var inMathBlock = false

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

        if (!inMathBlock && trimmed.startsWith("```")) {
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

        if (MarkdownMathParser.isDisplayMathFence(trimmed)) {
            flushAll()
            if (inMathBlock) {
                val tex = mathBuffer.joinToString("\n").trim()
                if (tex.isNotEmpty()) result += MdBlock.DisplayMath(tex)
                mathBuffer.clear()
                inMathBlock = false
            } else {
                inMathBlock = true
            }
            idx++
            continue
        }

        if (inMathBlock) {
            mathBuffer += line
            idx++
            continue
        }

        val singleLineMath = MarkdownMathParser.extractSingleLineDisplayMath(trimmed)
        if (singleLineMath != null) {
            flushAll()
            result += MdBlock.DisplayMath(singleLineMath)
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

        if (idx + 1 < lines.size && MarkdownMathParser.isPipeTableRow(trimmed)) {
            val alignments = MarkdownMathParser.parseTableSeparatorAlignments(lines[idx + 1].trim())
            if (alignments != null) {
                flushAll()
                val headers = MarkdownMathParser.parsePipeCells(trimmed)
                val rows = mutableListOf<List<String>>()
                idx += 2
                while (idx < lines.size && MarkdownMathParser.isPipeTableRow(lines[idx].trim())) {
                    rows += MarkdownMathParser.parsePipeCells(lines[idx].trim())
                    idx++
                }
                result += MdBlock.Table(headers, alignments, rows)
                continue
            }
        }

        val leadingSpaces = line.takeWhile { it == ' ' || it == '\t' }.fold(0) { acc, ch ->
            acc + if (ch == '\t') 4 else 1
        }
        val indentLevel = (leadingSpaces / 2).coerceIn(0, 4)

        val taskMatch = TASK_ITEM_REGEX.matchEntire(trimmed)
        when {
            taskMatch != null -> {
                flushAll()
                val checked = taskMatch.groupValues[1].equals("x", ignoreCase = true)
                result += MdBlock.TaskItem(checked, taskMatch.groupValues[2].trim(), indentLevel)
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                flushAll()
                result += MdBlock.ListItem("•", trimmed.drop(2).trim(), indentLevel)
            }
            NUMBERED_ITEM_REGEX.matches(trimmed) -> {
                flushAll()
                val dot = trimmed.indexOf('.')
                result += MdBlock.ListItem("${trimmed.substring(0, dot)}.", trimmed.substring(dot + 1).trim(), indentLevel)
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
    if (inMathBlock && mathBuffer.isNotEmpty()) {
        result += MdBlock.DisplayMath(mathBuffer.joinToString("\n").trim())
    }
    flushAll()
    return result
}

private fun findClosingMarker(text: String, marker: String, startIndex: Int): Int {
    var i = startIndex
    while (i <= text.length - marker.length) {
        if (text[i] == '\\') {
            i += 2
            continue
        }
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

private fun parseMarkdownLinkAt(text: String, startIndex: Int): Triple<String, String, Int>? {
    if (startIndex >= text.length || text[startIndex] != '[') return null
    val closeBracket = text.indexOf(']', startIndex + 1)
    if (closeBracket <= startIndex + 1 || closeBracket + 1 >= text.length || text[closeBracket + 1] != '(') {
        return null
    }
    var depth = 1
    var cursor = closeBracket + 2
    while (cursor < text.length && depth > 0) {
        when (text[cursor]) {
            '(' -> depth++
            ')' -> depth--
        }
        if (depth > 0) cursor++
    }
    if (depth != 0) return null
    val label = text.substring(startIndex + 1, closeBracket)
    val rawTarget = text.substring(closeBracket + 2, cursor).trim()
    val url = rawTarget.substringBefore(' ').trim('<', '>')
    return Triple(label, url, cursor + 1)
}

private fun buildInlineMarkdown(text: String, palette: MeloberryColors): ParsedInlineMarkdown {
    val formulas = LinkedHashMap<String, String>()
    val annotated = buildAnnotatedString {
        appendInlineSegment(text, palette, formulas)
    }
    return ParsedInlineMarkdown(annotated, formulas)
}

private fun AnnotatedString.Builder.appendInlineSegment(
    text: String,
    palette: MeloberryColors,
    formulas: MutableMap<String, String>
) {
    var i = 0
    while (i < text.length) {
        when {
            text[i] == '\\' && i + 1 < text.length -> {
                val next = text[i + 1]
                if (next == '$' || next == '*' || next == '_' || next == '`' || next == '[' || next == ']' || next == '~' || next == '\\') {
                    append(next)
                    i += 2
                } else {
                    append(text[i])
                    i++
                }
            }
            text[i] == '$' -> {
                val close = MarkdownMathParser.findClosingInlineMath(text, i)
                if (close > i + 1) {
                    val tex = text.substring(i + 1, close).trim()
                    val mathId = "math_${formulas.size}"
                    formulas[mathId] = tex
                    appendInlineContent(mathId, tex)
                    i = close + 1
                } else {
                    append('$')
                    i++
                }
            }
            text.startsWith("***", i) -> {
                val end = findClosingMarker(text, "***", i + 3)
                if (end > i + 3) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                        appendInlineSegment(text.substring(i + 3, end), palette, formulas)
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
                        appendInlineSegment(text.substring(i + 2, end), palette, formulas)
                    }
                    i = end + 2
                } else {
                    append(delim)
                    i += 2
                }
            }
            text.startsWith("~~", i) -> {
                val end = findClosingMarker(text, "~~", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        appendInlineSegment(text.substring(i + 2, end), palette, formulas)
                    }
                    i = end + 2
                } else {
                    append("~~")
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
                            color = palette.accent,
                            fontWeight = FontWeight.SemiBold
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
            text[i] == '[' -> {
                val link = parseMarkdownLinkAt(text, i)
                if (link != null) {
                    val (label, url, nextIndex) = link
                    if (MarkdownMathParser.isSafeUrl(url)) {
                        withLink(
                            LinkAnnotation.Url(
                                url = url,
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        color = palette.accent,
                                        textDecoration = TextDecoration.Underline,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            )
                        ) {
                            appendInlineSegment(label, palette, formulas)
                        }
                    } else {
                        appendInlineSegment(label, palette, formulas)
                    }
                    i = nextIndex
                } else {
                    append('[')
                    i++
                }
            }
            text[i] == '*' -> {
                val end = findClosingMarker(text, "*", i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendInlineSegment(text.substring(i + 1, end), palette, formulas)
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
                        appendInlineSegment(text.substring(i + 1, end), palette, formulas)
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
