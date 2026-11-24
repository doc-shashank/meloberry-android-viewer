package com.meloberry.android.domain

import java.util.Locale

enum class RecallMatchKind {
    EXACT,
    FUZZY,
    WRONG
}

data class RecallMatchResult(
    val isMatch: Boolean,
    val isFuzzyMatch: Boolean,
    val matchedAnswer: String?,
    val kind: RecallMatchKind = when {
        !isMatch -> RecallMatchKind.WRONG
        isFuzzyMatch -> RecallMatchKind.FUZZY
        else -> RecallMatchKind.EXACT
    }
)

object RecallAnswerMatcher {
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val DASH_REGEX = Regex("[\u2010\u2011\u2012\u2013\u2014\u2212]")
    private val PUNCT_REGEX = Regex("[,.;:()]")
    private val COMPACT_REGEX = Regex("[-\\s]")

    private val GREEK_MAP: List<Pair<String, String>> = listOf(
        "alpha" to "\u03b1", "beta" to "\u03b2", "gamma" to "\u03b3", "delta" to "\u03b4",
        "epsilon" to "\u03b5", "epsilon" to "\u03f5", "zeta" to "\u03b6", "eta" to "\u03b7",
        "theta" to "\u03b8", "theta" to "\u03d1", "iota" to "\u03b9", "kappa" to "\u03ba",
        "lambda" to "\u03bb", "mu" to "\u03bc", "mu" to "\u00b5", "nu" to "\u03bd",
        "xi" to "\u03be", "omicron" to "\u03bf", "pi" to "\u03c0", "rho" to "\u03c1",
        "sigma" to "\u03c3", "sigma" to "\u03c2", "tau" to "\u03c4", "upsilon" to "\u03c5",
        "phi" to "\u03c6", "phi" to "\u03d5", "chi" to "\u03c7", "psi" to "\u03c8", "omega" to "\u03c9",
        "Alpha" to "\u0391", "Beta" to "\u0392", "Gamma" to "\u0393", "Delta" to "\u0394",
        "Epsilon" to "\u0395", "Zeta" to "\u0396", "Eta" to "\u0397", "Theta" to "\u0398",
        "Iota" to "\u0399", "Kappa" to "\u039a", "Lambda" to "\u039b", "Mu" to "\u039c",
        "Nu" to "\u039d", "Xi" to "\u039e", "Omicron" to "\u039f", "Pi" to "\u03a0",
        "Rho" to "\u03a1", "Sigma" to "\u03a3", "Tau" to "\u03a4", "Upsilon" to "\u03a5",
        "Phi" to "\u03a6", "Chi" to "\u03a7", "Psi" to "\u03a8", "Omega" to "\u03a9"
    )

    fun normalizeRecallAnswer(raw: String?, caseSensitive: Boolean = false): String {
        var s = (raw ?: "").trim().replace(WHITESPACE_REGEX, " ")
        if (!caseSensitive) {
            s = s.lowercase(Locale.ROOT)
        }
        for ((name, sym) in GREEK_MAP) {
            if (s.contains(sym)) {
                s = s.replace(sym, if (caseSensitive) name else name.lowercase(Locale.ROOT))
            }
        }
        s = s.replace(DASH_REGEX, "-")
            .replace(PUNCT_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
        return s
    }

    fun levenshteinDistance(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[m][n]
    }

    fun evaluateRecallMatch(
        userInput: String,
        acceptedAnswers: List<String>,
        fuzzy: Boolean,
        caseSensitive: Boolean
    ): RecallMatchResult {
        val uNorm = normalizeRecallAnswer(userInput, caseSensitive)
        if (uNorm.isEmpty()) {
            return RecallMatchResult(isMatch = false, isFuzzyMatch = false, matchedAnswer = null, kind = RecallMatchKind.WRONG)
        }
        val uCompact = uNorm.replace(COMPACT_REGEX, "")
        for (ans in acceptedAnswers) {
            val aNorm = normalizeRecallAnswer(ans, caseSensitive)
            if (uNorm == aNorm || uCompact == aNorm.replace(COMPACT_REGEX, "")) {
                return RecallMatchResult(isMatch = true, isFuzzyMatch = false, matchedAnswer = ans, kind = RecallMatchKind.EXACT)
            }
        }
        if (fuzzy) {
            for (ans in acceptedAnswers) {
                val aNorm = normalizeRecallAnswer(ans, caseSensitive)
                if (aNorm.length >= 6 && levenshteinDistance(uNorm, aNorm) <= 1) {
                    return RecallMatchResult(isMatch = true, isFuzzyMatch = true, matchedAnswer = ans, kind = RecallMatchKind.FUZZY)
                }
                val aCompact = aNorm.replace(COMPACT_REGEX, "")
                if (aCompact.length >= 6 && levenshteinDistance(uCompact, aCompact) <= 1) {
                    return RecallMatchResult(isMatch = true, isFuzzyMatch = true, matchedAnswer = ans, kind = RecallMatchKind.FUZZY)
                }
            }
        }
        return RecallMatchResult(isMatch = false, isFuzzyMatch = false, matchedAnswer = null, kind = RecallMatchKind.WRONG)
    }
}
