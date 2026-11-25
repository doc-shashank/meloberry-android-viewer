package com.meloberry.android.core.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownMathParserTest {

    @Test
    fun displayMath_extractsSingleLineAndFence() {
        assertTrue(MarkdownMathParser.isDisplayMathFence("\$\$"))
        assertFalse(MarkdownMathParser.isDisplayMathFence("\$\$x\$\$"))
        assertEquals("v = u + at", MarkdownMathParser.extractSingleLineDisplayMath("\$\$v = u + at\$\$"))
        assertNull(MarkdownMathParser.extractSingleLineDisplayMath("\$\$\$\$"))
    }

    @Test
    fun inlineMath_matchesValidFormulas_andIgnoresCurrencyOrCodeOrEscapes() {
        val sample = "Speed is \$O(\\log n)\$ and escaped \\\$x\\\$ costs \$5 and \$10."
        val openIdx = sample.indexOf("\$O(")
        val closeIdx = MarkdownMathParser.findClosingInlineMath(sample, openIdx)
        assertEquals("O(\\log n)", sample.substring(openIdx + 1, closeIdx))

        val currency = "Price is \$5 and \$10 today"
        val firstDollar = currency.indexOf('$')
        assertEquals(-1, MarkdownMathParser.findClosingInlineMath(currency, firstDollar))

        val escaped = "Literal \\\$x\\\$"
        assertEquals(-1, MarkdownMathParser.findClosingInlineMath(escaped, escaped.indexOf('$')))

        // Inline display-math delimiter $$...$$ inside paragraph/cell text
        val inlineDisplay = "Energy is \$\$E = mc^2\$\$ in rest frame."
        val displayOpen = inlineDisplay.indexOf("\$\$")
        val displayClose = MarkdownMathParser.findClosingDisplayMath(inlineDisplay, displayOpen)
        assertEquals("E = mc^2", inlineDisplay.substring(displayOpen + 2, displayClose))
    }

    @Test
    fun isSafeUrl_blocksDangerousSchemes_andAllowsWebAndMailto() {
        assertTrue(MarkdownMathParser.isSafeUrl("https://example.com/docs"))
        assertTrue(MarkdownMathParser.isSafeUrl("http://localhost:8080"))
        assertTrue(MarkdownMathParser.isSafeUrl("mailto:study@meloberry.local"))

        assertFalse(MarkdownMathParser.isSafeUrl("javascript:alert(1)"))
        assertFalse(MarkdownMathParser.isSafeUrl("JaVaScRiPt:alert(1)"))
        assertFalse(MarkdownMathParser.isSafeUrl("java\nscript:alert(1)"))
        assertFalse(MarkdownMathParser.isSafeUrl("javascript&colon;alert(1)"))
        assertFalse(MarkdownMathParser.isSafeUrl("vbscript:msgbox(1)"))
        assertFalse(MarkdownMathParser.isSafeUrl("data:text/html,<script>alert(1)</script>"))
    }

    @Test
    fun gfmTables_supportOptionalOuterPipes_shortSeparators_singleColumn_andMathOrCodePipes() {
        assertTrue(MarkdownMathParser.isPipeTableRow("Col A | Col B | Col C"))
        assertEquals(
            listOf(TableColAlign.LEFT, TableColAlign.CENTER, TableColAlign.RIGHT),
            MarkdownMathParser.parseTableSeparatorAlignments(":--- | :---: | ---:")
        )
        assertEquals(
            listOf(TableColAlign.LEFT, TableColAlign.CENTER, TableColAlign.RIGHT),
            MarkdownMathParser.parseTableSeparatorAlignments("|-|:-:| -:|")
        )

        // Single-column table
        assertTrue(MarkdownMathParser.isPipeTableRow("| Only Column |"))
        assertEquals(listOf("Only Column"), MarkdownMathParser.parsePipeCells("| Only Column |"))
        assertEquals(listOf(TableColAlign.LEFT), MarkdownMathParser.parseTableSeparatorAlignments("|-|"))

        // Pipes inside inline math, display math, and multi-backtick code do not split cells
        val rowWithMathAndCode = "| \$|x| = 5\$ | \$\$|a| + |b|\$\$ | ``foo | bar`` |"
        assertTrue(MarkdownMathParser.isPipeTableRow(rowWithMathAndCode))
        assertEquals(
            listOf("\$|x| = 5\$", "\$\$|a| + |b|\$\$", "``foo | bar``"),
            MarkdownMathParser.parsePipeCells(rowWithMathAndCode)
        )

        // Standalone math or code containing '|' is NOT a pipe table row
        assertFalse(MarkdownMathParser.isPipeTableRow("\$|x| = 5\$"))
        assertFalse(MarkdownMathParser.isPipeTableRow("`a | b`"))
    }

    @Test
    fun emphasis_skipsMathAndCodeSpans_andMultiBackticksAndNestedLinksWork() {
        val boldWithMath = "**bold \$a * b\$ text**"
        val boldEnd = MarkdownMathParser.findClosingEmphasisMarker(boldWithMath, "**", 2)
        assertEquals(boldWithMath.length - 2, boldEnd)

        val italicWithSubscript = "*italic \$x_1 + x_2\$*"
        val italicEnd = MarkdownMathParser.findClosingEmphasisMarker(italicWithSubscript, "*", 1)
        assertEquals(italicWithSubscript.length - 1, italicEnd)

        val underscoreItalicWithMath = "_value \$a_1 * b_2\$ end_"
        val underEnd = MarkdownMathParser.findClosingEmphasisMarker(underscoreItalicWithMath, "_", 1)
        assertEquals(underscoreItalicWithMath.length - 1, underEnd)

        val doubleTicks = "``code with ` tick`` tail"
        val span = MarkdownMathParser.findClosingBackticks(doubleTicks, 0)
        assertNotNull(span)
        assertEquals(2, span!!.first)
        assertEquals(18, span.second)

        val nestedLink = "[[1]](https://example.com/a(b))"
        val parsedLink = MarkdownMathParser.parseMarkdownLinkAt(nestedLink, 0)
        assertNotNull(parsedLink)
        assertEquals("[1]", parsedLink!!.first)
        assertEquals("https://example.com/a(b)", parsedLink.second)
        assertEquals(nestedLink.length, parsedLink.third)
    }
}
