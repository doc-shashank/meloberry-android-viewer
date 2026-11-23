package com.meloberry.android.core.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun gfmTables_supportOptionalOuterPipesAndAlignments() {
        assertTrue(MarkdownMathParser.isPipeTableRow("Col A | Col B | Col C"))
        assertEquals(
            listOf(TableColAlign.LEFT, TableColAlign.CENTER, TableColAlign.RIGHT),
            MarkdownMathParser.parseTableSeparatorAlignments(":--- | :---: | ---:")
        )
        assertEquals(
            listOf("A", "B", "C"),
            MarkdownMathParser.parsePipeCells("A | B | C")
        )
    }
}
