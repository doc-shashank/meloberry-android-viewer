package com.meloberry.android.data.workspace.source

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Mirrors CLI `FileContentInspector.TryDetectImageExtension` to verify magic bytes for
 * PNG, JPEG, GIF, WebP, BMP, and strict-UTF8 sanitized SVGs.
 */
internal object ImageAssetVerifier {

    private val DANGEROUS_SVG_REGEX = Regex(
        """<\s*(?:script|foreignObject)\b|\bon[a-z]+\s*=|(?:javascript|vbscript)\s*:|data\s*:\s*text/html""",
        RegexOption.IGNORE_CASE
    )

    fun isValidSafeImage(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        return runCatching { isValidSafeImage(file.readBytes()) }.getOrDefault(false)
    }

    fun isValidSafeImage(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false

        // PNG
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte() &&
            bytes[4] == 0x0D.toByte() &&
            bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() &&
            bytes[7] == 0x0A.toByte()
        ) {
            return true
        }

        // JPEG
        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        ) {
            return true
        }

        // GIF (GIF87a / GIF89a)
        if (bytes.size >= 6 &&
            bytes[0] == 'G'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == '8'.code.toByte() &&
            (bytes[4] == '7'.code.toByte() || bytes[4] == '9'.code.toByte()) &&
            bytes[5] == 'a'.code.toByte()
        ) {
            return true
        }

        // WebP (RIFF....WEBP)
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() &&
            bytes[11] == 'P'.code.toByte()
        ) {
            return true
        }

        // BMP
        if (bytes.size >= 2 &&
            bytes[0] == 'B'.code.toByte() &&
            bytes[1] == 'M'.code.toByte()
        ) {
            return true
        }

        // Sanitized UTF-8 SVG
        if (bytes.size >= 4 && bytes.none { it == 0.toByte() }) {
            val decoded = decodeStrictUtf8(bytes) ?: return false
            val fullText = decoded.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
            val probe = if (fullText.length > 1024) fullText.substring(0, 1024) else fullText
            val looksLikeSvg = probe.startsWith("<svg", ignoreCase = true) ||
                (probe.startsWith("<?xml", ignoreCase = true) && probe.contains("<svg", ignoreCase = true))
            if (looksLikeSvg && !DANGEROUS_SVG_REGEX.containsMatchIn(fullText)) {
                return true
            }
        }

        return false
    }

    private fun decodeStrictUtf8(bytes: ByteArray): String? = runCatching {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        decoder.decode(ByteBuffer.wrap(bytes)).toString()
    }.getOrNull()
}
