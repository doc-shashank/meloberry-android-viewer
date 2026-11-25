package com.meloberry.android.data.workspace.source

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Mirrors CLI `FileContentInspector.TryDetectImageExtension` to verify magic bytes for
 * PNG, JPEG, GIF, WebP, BMP, and strict-UTF8 sanitized SVGs without loading large rasters into heap.
 */
internal object ImageAssetVerifier {

    const val MAX_SVG_BYTES: Long = 5L * 1024L * 1024L
    private const val HEADER_PROBE_BYTES = 16

    private val DANGEROUS_SVG_GLOBAL_REGEX = Regex(
        """<\s*(?:script|foreignObject)\b|(?:javascript|vbscript)\s*:|data\s*:\s*text/html""",
        RegexOption.IGNORE_CASE
    )
    private val SVG_TAG_REGEX = Regex("""<[^>]+>""")
    private val SVG_EVENT_ATTR_REGEX = Regex("""(?:\s|/|"|')on[a-z]+\s*=""", RegexOption.IGNORE_CASE)

    fun isValidSafeImage(file: File): Boolean {
        if (!file.isFile) return false
        val length = file.length()
        if (length <= 0L) return false

        return runCatching {
            val header = ByteArray(HEADER_PROBE_BYTES)
            var bytesRead = 0
            FileInputStream(file).use { input ->
                while (bytesRead < HEADER_PROBE_BYTES) {
                    val read = input.read(header, bytesRead, HEADER_PROBE_BYTES - bytesRead)
                    if (read <= 0) break
                    bytesRead += read
                }
            }
            if (isValidRasterHeader(header, bytesRead)) {
                return@runCatching true
            }
            if (length > MAX_SVG_BYTES) {
                return@runCatching false
            }
            isValidSafeSvg(file.readBytes())
        }.getOrDefault(false)
    }

    fun isValidSafeImage(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        if (isValidRasterHeader(bytes, bytes.size)) return true
        if (bytes.size.toLong() > MAX_SVG_BYTES) return false
        return isValidSafeSvg(bytes)
    }

    private fun isValidRasterHeader(bytes: ByteArray, length: Int): Boolean {
        // PNG
        if (length >= 8 &&
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
        if (length >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        ) {
            return true
        }

        // GIF (GIF87a / GIF89a)
        if (length >= 6 &&
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
        if (length >= 12 &&
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
        if (length >= 2 &&
            bytes[0] == 'B'.code.toByte() &&
            bytes[1] == 'M'.code.toByte()
        ) {
            return true
        }

        return false
    }

    private fun isValidSafeSvg(bytes: ByteArray): Boolean {
        if (bytes.size < 4 || bytes.any { it == 0.toByte() }) return false
        val decoded = decodeStrictUtf8(bytes) ?: return false
        val fullText = decoded.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        val probe = if (fullText.length > 1024) fullText.substring(0, 1024) else fullText
        val looksLikeSvg = probe.startsWith("<svg", ignoreCase = true) ||
            (probe.startsWith("<?xml", ignoreCase = true) && probe.contains("<svg", ignoreCase = true))
        if (!looksLikeSvg) return false
        if (DANGEROUS_SVG_GLOBAL_REGEX.containsMatchIn(fullText)) return false
        val hasEventAttr = SVG_TAG_REGEX.findAll(fullText).any { tagMatch ->
            SVG_EVENT_ATTR_REGEX.containsMatchIn(tagMatch.value)
        }
        return !hasEventAttr
    }

    private fun decodeStrictUtf8(bytes: ByteArray): String? = runCatching {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        decoder.decode(ByteBuffer.wrap(bytes)).toString()
    }.getOrNull()
}
