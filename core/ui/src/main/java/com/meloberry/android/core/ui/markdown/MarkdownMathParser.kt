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

    private val PIPE_TABLE_SEP_CELL = Regex("^:?-{3,}:?$")

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
                    val codeEnd = text.indexOf('`', i + 1)
                    if (codeEnd > i) {
                        i = codeEnd + 1
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

    fun isPipeTableRow(trimmed: String): Boolean {
        val cells = splitPipeCells(trimmed)
        return cells.size >= 2 && containsUnescapedPipe(trimmed)
    }

    fun parsePipeCells(trimmed: String): List<String> = splitPipeCells(trimmed)

    fun parseTableSeparatorAlignments(trimmed: String): List<TableColAlign>? {
        if (!containsUnescapedPipe(trimmed)) return null
        val cells = splitPipeCells(trimmed)
        if (cells.size < 2) return null
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
            if (text[i] == '\\') {
                i += 2
                continue
            }
            if (text[i] == '|') return true
            i++
        }
        return false
    }

    private fun splitPipeCells(trimmed: String): List<String> {
        var s = trimmed.trim()
        if (s.startsWith('|')) s = s.drop(1)
        if (s.endsWith('|') && !s.endsWith("\\|")) s = s.dropLast(1)

        val cells = mutableListOf<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length && s[i + 1] == '|') {
                cur.append('|')
                i += 2
            } else if (s[i] == '|') {
                cells += cur.toString().trim()
                cur.clear()
                i++
            } else {
                cur.append(s[i])
                i++
            }
        }
        cells += cur.toString().trim()
        return cells
    }
}
