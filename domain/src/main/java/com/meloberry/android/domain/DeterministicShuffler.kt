package com.meloberry.android.domain

import kotlin.math.floor

object DeterministicShuffler {

    fun <T> deterministicShuffle(
        items: List<T>,
        seedStr: String,
        keyFn: ((T) -> String)? = null
    ): List<T> {
        val out = items.toMutableList()
        if (out.size < 2) return out

        var h: UInt = 2166136261u
        for (i in seedStr.indices) {
            h = (h xor seedStr[i].code.toUInt()) * 16777619u
        }

        fun nextRand(): Double {
            h = (1664525u * h) + 1013904223u
            return h.toDouble() / 4294967296.0
        }

        for (i in out.lastIndex downTo 1) {
            val j = floor(nextRand() * (i + 1)).toInt()
            val tmp = out[i]
            out[i] = out[j]
            out[j] = tmp
        }

        if (keyFn != null && out.map(keyFn).toSet().size >= 2) {
            var same = true
            for (i in out.indices) {
                if (keyFn(out[i]) != keyFn(items[i])) {
                    same = false
                    break
                }
            }
            if (same) {
                val swapIdx = out.indexOfFirst { keyFn(it) != keyFn(out[0]) }
                if (swapIdx > 0) {
                    val tmp = out[0]
                    out[0] = out[swapIdx]
                    out[swapIdx] = tmp
                }
            }
        }

        return out
    }
}
