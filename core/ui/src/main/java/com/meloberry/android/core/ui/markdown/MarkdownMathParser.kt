package com.meloberry.android.core.ui.markdown

enum class TableColAlign {
    LEFT,
    CENTER,
    RIGHT
}

/**
 * Pure JVM Markdown + LaTeX math parsing and URL sanitization helpers shared by [MarkdownText].
 */
object MarkdownMathParser {

    private val PIPE_TABLE_SEP_CELL = Regex("^:?-+:?$")

    fun isSafeUrl(rawUrl: String): Boolean {
        val decoded = rawUrl
            .replace("&colon;", ":", ignoreCase = true)
            .replace("&#58;", ":")
            .replace("&#x3a;", ":", ignoreCase = true)
            .filterNot { it.isWhitespace() || it.isISOControl() }

        if (decoded.isEmpty()) return false
        val lower = decoded.lowercase()
        if (lower.startsWith("javascript:") ||
            lower.startsWith("vbscript:") ||
            lower.startsWith("data:text/html")
        ) {
            return false
        }
        return lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("mailto:")
    }

    fun isDisplayMathFence(trimmed: String): Boolean = trimmed == "$$"

    fun extractSingleLineDisplayMath(trimmed: String): String? {
        if (trimmed.length <= 4) return null
        if (!trimmed.startsWith("$$") || !trimmed.endsWith("$$")) return null
        val inner = trimmed.substring(2, trimmed.length - 2).trim()
        return inner.takeIf { it.isNotEmpty() }
    }

    /**
     * Matches a CommonMark backtick code span starting at [openIndex].
     * Returns `(tickCount, closeStartIndex)` or `null` if no matching closing run exists.
     */
    fun findClosingBackticks(text: String, openIndex: Int): Pair<Int, Int>? {
        if (openIndex < 0 || openIndex >= text.length || text[openIndex] != '`') return null
        var tickCount = 0
        while (openIndex + tickCount < text.length && text[openIndex + tickCount] == '`') {
            tickCount++
        }
        var i = openIndex + tickCount
        while (i < text.length) {
            if (text[i] == '`') {
                val runStart = i
                var runLen = 0
                while (i < text.length && text[i] == '`') {
                    runLen++
                    i++
                }
                if (runLen == tickCount) {
                    return tickCount to runStart
                }
            } else {
                i++
            }
        }
        return null
    }

    /**
     * Finds the starting index of the matching closing `$$` for a display math span starting at [openIndex].
     */
    fun findClosingDisplayMath(text: String, openIndex: Int): Int {
        if (openIndex < 0 || openIndex + 1 >= text.length) return -1
        if (text[openIndex] != '$' || text[openIndex + 1] != '$') return -1
        if (openIndex > 0 && text[openIndex - 1] == '\\') return -1

        var i = openIndex + 2
        while (i + 1 < text.length) {
            if (text[i] == '\\') {
                i += 2
                continue
            }
            if (text[i] == '$' && text[i + 1] == '$') {
                return i
            }
            i++
        }
        return -1
    }

    /**
     * Finds the matching closing `$` for an inline math span starting at [openIndex].
     * Ignores escaped `\$`, display `$$`, whitespace right after `$` or right before closing `$`,
     * inline code spans `` `...` ``, and currency patterns followed immediately by a digit.
     */
    fun findClosingInlineMath(text: String, openIndex: Int): Int {
        if (openIndex < 0 || openIndex >= text.length || text[openIndex] != '$') return -1
        if (openIndex > 0 && text[openIndex - 1] == '\\') return -1
        if (openIndex + 1 < text.length && text[openIndex + 1] == '$') return -1
        if (openIndex + 1 >= text.length || text[openIndex + 1].isWhitespace()) return -1

        var i = openIndex + 1
        while (i < text.length) {
            when (text[i]) {
                '\\' -> {
                    i += 2
                    continue
                }
                '`' -> {
                    val codeSpan = findClosingBackticks(text, i)
                    if (codeSpan != null) {
                        i = codeSpan.second + codeSpan.first
                        continue
                    }
                }
                '$' -> {
                    if (i + 1 < text.length && text[i + 1] == '$') {
                        return -1
                    }
                    val charBefore = text[i - 1]
                    val charAfter = text.getOrNull(i + 1)
                    if (!charBefore.isWhitespace() && (charAfter == null || !charAfter.isDigit())) {
                        return i
                    }
                }
            }
            i++
        }
        return -1
    }

    /**
     * Finds the closing delimiter for emphasis/strikethrough markers (`*`, `_`, `**`, `__`, `***`, `~~`)
     * while skipping escaped characters, multi-backtick code spans, `$$...$$`, and `$...$` math spans.
     */
    fun findClosingEmphasisMarker(text: String, marker: String, startIndex: Int): Int {
        var i = startIndex
        while (i <= text.length - marker.length) {
            if (text[i] == '\\') {
                i += 2
                continue
            }
            if (text[i] == '`') {
                val codeSpan = findClosingBackticks(text, i)
                if (codeSpan != null) {
                    i = codeSpan.second + codeSpan.first
                    continue
                }
            }
            if (text[i] == '$') {
                if (i + 1 < text.length && text[i + 1] == '$') {
                    val displayClose = findClosingDisplayMath(text, i)
                    if (displayClose > i) {
                        i = displayClose + 2
                        continue
                    }
                } else {
                    val mathClose = findClosingInlineMath(text, i)
                    if (mathClose > i) {
                        i = mathClose + 1
                        continue
                    }
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

    /**
     * Parses a Markdown link `[label](url)` starting at [startIndex], supporting nested balanced
     * brackets in `label` (e.g. `[[1]](https://example.com)`) and balanced parentheses in `url`.
     */
    fun parseMarkdownLinkAt(text: String, startIndex: Int): Triple<String, String, Int>? {
        if (startIndex < 0 || startIndex >= text.length || text[startIndex] != '[') return null

        var bracketDepth = 1
        var cursor = startIndex + 1
        while (cursor < text.length && bracketDepth > 0) {
            when (text[cursor]) {
                '\\' -> {
                    cursor += 2
                    continue
                }
                '`' -> {
                    val codeSpan = findClosingBackticks(text, cursor)
                    if (codeSpan != null) {
                        cursor = codeSpan.second + codeSpan.first
                        continue
                    }
                }
                '$' -> {
                    val mathEnd = if (cursor + 1 < text.length && text[cursor + 1] == '$') {
                        findClosingDisplayMath(text, cursor).takeIf { it > cursor }?.let { it + 2 }
                    } else {
                        findClosingInlineMath(text, cursor).takeIf { it > cursor }?.let { it + 1 }
                    }
                    if (mathEnd != null) {
                        cursor = mathEnd
                        continue
                    }
                }
                '[' -> bracketDepth++
                ']' -> bracketDepth--
            }
            if (bracketDepth > 0) cursor++
        }

        if (bracketDepth != 0) return null
        val closeBracket = cursor
        if (closeBracket <= startIndex + 1 || closeBracket + 1 >= text.length || text[closeBracket + 1] != '(') {
            return null
        }

        var parenDepth = 1
        var urlCursor = closeBracket + 2
        while (urlCursor < text.length && parenDepth > 0) {
            when (text[urlCursor]) {
                '\\' -> {
                    urlCursor += 2
                    continue
                }
                '(' -> parenDepth++
                ')' -> parenDepth--
            }
            if (parenDepth > 0) urlCursor++
        }
        if (parenDepth != 0) return null

        val label = text.substring(startIndex + 1, closeBracket)
        val rawTarget = text.substring(closeBracket + 2, urlCursor).trim()
        val url = rawTarget.substringBefore(' ').trim('<', '>')
        return Triple(label, url, urlCursor + 1)
    }

    fun isPipeTableRow(trimmed: String): Boolean {
        if (!containsUnescapedPipe(trimmed)) return false
        val cells = splitPipeCells(trimmed)
        return cells.isNotEmpty()
    }

    fun parsePipeCells(trimmed: String): List<String> = splitPipeCells(trimmed)

    fun parseTableSeparatorAlignments(trimmed: String): List<TableColAlign>? {
        if (!containsUnescapedPipe(trimmed)) return null
        val cells = splitPipeCells(trimmed)
        if (cells.isEmpty()) return null
        val alignments = mutableListOf<TableColAlign>()
        for (cell in cells) {
            val c = cell.trim()
            if (!PIPE_TABLE_SEP_CELL.matches(c)) return null
            alignments += when {
                c.startsWith(':') && c.endsWith(':') -> TableColAlign.CENTER
                c.endsWith(':') -> TableColAlign.RIGHT
                else -> TableColAlign.LEFT
            }
        }
        return alignments
    }

    private fun containsUnescapedPipe(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            when (text[i]) {
                '\\' -> {
                    i += 2
                    continue
                }
                '`' -> {
                    val codeSpan = findClosingBackticks(text, i)
                    if (codeSpan != null) {
                        i = codeSpan.second + codeSpan.first
                        continue
                    }
                }
                '$' -> {
                    val mathEnd = if (i + 1 < text.length && text[i + 1] == '$') {
                        findClosingDisplayMath(text, i).takeIf { it > i }?.let { it + 2 }
                    } else {
                        findClosingInlineMath(text, i).takeIf { it > i }?.let { it + 1 }
                    }
                    if (mathEnd != null) {
                        i = mathEnd
                        continue
                    }
                }
                '|' -> return true
            }
            i++
        }
        return false
    }

    private fun splitPipeCells(trimmed: String): List<String> {
        var s = trimmed.trim()
        if (s.startsWith('|')) s = s.drop(1)
        if (s.endsWith('|') && !endsWithEscapedPipe(s)) s = s.dropLast(1)

        val cells = mutableListOf<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < s.length) {
            when {
                s[i] == '\\' && i + 1 < s.length && s[i + 1] == '|' -> {
                    cur.append('|')
                    i += 2
                }
                s[i] == '`' -> {
                    val codeSpan = findClosingBackticks(s, i)
                    if (codeSpan != null) {
                        val endExclusive = codeSpan.second + codeSpan.first
                        cur.append(s, i, endExclusive)
                        i = endExclusive
                    } else {
                        cur.append('`')
                        i++
                    }
                }
                s[i] == '$' -> {
                    val mathEnd = if (i + 1 < s.length && s[i + 1] == '$') {
                        findClosingDisplayMath(s, i).takeIf { it > i }?.let { it + 2 }
                    } else {
                        findClosingInlineMath(s, i).takeIf { it > i }?.let { it + 1 }
                    }
                    if (mathEnd != null) {
                        cur.append(s, i, mathEnd)
                        i = mathEnd
                    } else {
                        cur.append('$')
                        i++
                    }
                }
                s[i] == '|' -> {
                    cells += cur.toString().trim()
                    cur.clear()
                    i++
                }
                else -> {
                    cur.append(s[i])
                    i++
                }
            }
        }
        cells += cur.toString().trim()
        return cells
    }

    private fun endsWithEscapedPipe(text: String): Boolean {
        if (!text.endsWith('|')) return false
        var backslashes = 0
        var idx = text.length - 2
        while (idx >= 0 && text[idx] == '\\') {
            backslashes++
            idx--
        }
        return backslashes % 2 == 1
    }
}
