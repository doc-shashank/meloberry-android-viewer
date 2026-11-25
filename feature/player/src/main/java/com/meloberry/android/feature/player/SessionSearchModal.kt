package com.meloberry.android.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meloberry.android.core.model.ArticleBlockKind
import com.meloberry.android.core.model.QuestionSlideItem
import com.meloberry.android.core.model.SlideUiModel
import com.meloberry.android.core.ui.theme.LocalMeloberryColors
import java.util.Locale

internal data class SessionSearchEntry(
    val id: String,
    val outerSlideIndex: Int,
    val quizId: String?,
    val quizQuestionIndex: Int?,
    val kindLabel: String,
    val title: String,
    val previewText: String,
    val searchableText: String
)

internal data class SessionSearchMatch(
    val entry: SessionSearchEntry,
    val snippet: String,
    val score: Int
)

internal object SessionSearchIndex {

    private val FILL_BLANK_TOKEN_REGEX = Regex("""\{\{[^{}]*\}\}""")
    private val SPOT_ERROR_MARKUP_REGEX = Regex("""\[~~(.*?)~~\s*->\s*[^\]]*\]""")

    internal fun sanitizeAnswerMarkup(raw: String): String = raw
        .replace(SPOT_ERROR_MARKUP_REGEX, "$1")
        .replace(FILL_BLANK_TOKEN_REGEX, "[blank]")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun build(slides: List<SlideUiModel>): List<SessionSearchEntry> {
        val entries = mutableListOf<SessionSearchEntry>()
        slides.forEachIndexed { outerIdx, slide ->
            when (slide) {
                is SlideUiModel.ArticleSlide -> {
                    val parts = buildList {
                        slide.title?.let(::add)
                        slide.blocks.forEach { block ->
                            when (block.kind) {
                                ArticleBlockKind.TEXT -> block.textContent?.let(::add)
                                ArticleBlockKind.IMAGE -> {
                                    block.caption?.let(::add)
                                    block.alt?.let(::add)
                                }
                                ArticleBlockKind.CALLOUT -> {
                                    block.calloutTitle?.let(::add)
                                    block.textContent?.let(::add)
                                }
                                ArticleBlockKind.TABLE -> {
                                    block.tablePayload?.let { t ->
                                        t.title?.let(::add)
                                        t.caption?.let(::add)
                                        addAll(t.headers)
                                        t.rows.forEach { addAll(it) }
                                        t.footnote?.let(::add)
                                    }
                                }
                            }
                        }
                    }
                    val normalized = parts.joinToString(" ").replace(Regex("\\s+"), " ").trim()
                    entries += SessionSearchEntry(
                        id = slide.id,
                        outerSlideIndex = outerIdx,
                        quizId = null,
                        quizQuestionIndex = null,
                        kindLabel = "ARTICLE",
                        title = slide.title?.takeIf { it.isNotBlank() } ?: "Article #${outerIdx + 1}",
                        previewText = normalized,
                        searchableText = normalized
                    )
                }
                is SlideUiModel.QuizSlide -> {
                    val quizTitlePrefix = slide.title?.takeIf { it.isNotBlank() }?.let { "$it • " }.orEmpty()
                    slide.questions.forEachIndexed { qIdx, qSlide ->
                        entries += buildQuestionEntry(
                            qSlide = qSlide,
                            outerIdx = outerIdx,
                            quizId = slide.id,
                            quizQuestionIdx = qIdx,
                            titlePrefix = "${quizTitlePrefix}Q${qIdx + 1}"
                        )
                    }
                }
                is QuestionSlideItem -> {
                    entries += buildQuestionEntry(
                        qSlide = slide,
                        outerIdx = outerIdx,
                        quizId = null,
                        quizQuestionIdx = null,
                        titlePrefix = "Q${outerIdx + 1}"
                    )
                }
            }
        }
        return entries
    }

    fun search(entries: List<SessionSearchEntry>, rawQuery: String): List<SessionSearchMatch> {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) {
            return entries.map { entry ->
                SessionSearchMatch(
                    entry = entry,
                    snippet = entry.previewText.take(120),
                    score = 0
                )
            }
        }
        val tokens = trimmed.lowercase(Locale.ROOT)
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()

        val fullLower = trimmed.lowercase(Locale.ROOT)
        return entries.mapNotNull { entry ->
            val titleLower = entry.title.lowercase(Locale.ROOT)
            val kindLower = entry.kindLabel.lowercase(Locale.ROOT)
            val bodyLower = entry.searchableText.lowercase(Locale.ROOT)
            val combined = "$titleLower $kindLower $bodyLower"

            if (!tokens.all { it in combined }) return@mapNotNull null

            var score = 0
            if (fullLower in titleLower) score += 100
            if (fullLower in bodyLower) score += 50
            tokens.forEach { tok ->
                if (tok in titleLower) score += 20
                if (tok in kindLower) score += 10
                if (tok in bodyLower) score += 5
            }

            SessionSearchMatch(
                entry = entry,
                snippet = extractSnippet(entry.previewText, tokens),
                score = score
            )
        }.sortedByDescending { it.score }
    }

    private fun buildQuestionEntry(
        qSlide: QuestionSlideItem,
        outerIdx: Int,
        quizId: String?,
        quizQuestionIdx: Int?,
        titlePrefix: String
    ): SessionSearchEntry {
        val (kindLabel, titleSuffix, previewParts, searchParts) = when (qSlide) {
            is SlideUiModel.McqSlide -> {
                val q = qSlide.question
                val preview = buildList {
                    add(q.promptText)
                    q.options.forEach { it.text?.let(::add) }
                }
                val search = preview + listOfNotNull(q.explanation)
                Quad("MCQ", q.promptText.take(64), preview, search)
            }
            is SlideUiModel.FlashcardSlide -> {
                val q = qSlide.question
                val frontPrompt = q.front.text?.takeIf { it.isNotBlank() } ?: "Flashcard"
                val preview = listOf(frontPrompt)
                val search = listOfNotNull(q.front.text, q.back.text)
                Quad("FLASHCARD", frontPrompt.take(64), preview, search)
            }
            is SlideUiModel.FillBlankSlide -> {
                val pl = qSlide.question.payload
                val sanitizedTemplate = sanitizeAnswerMarkup(pl.template)
                val preview = listOf(sanitizedTemplate)
                val search = buildList {
                    add(sanitizedTemplate)
                    pl.blanks.forEach { addAll(it.answers) }
                    pl.explanation?.let(::add)
                }
                Quad("FILL BLANK", sanitizedTemplate.take(64), preview, search)
            }
            is SlideUiModel.AssertionReasonSlide -> {
                val pl = qSlide.question.payload
                val preview = listOf(pl.assertion, pl.reason)
                val search = preview + listOfNotNull(pl.explanation)
                Quad("ASSERTION-REASON", pl.assertion.take(64), preview, search)
            }
            is SlideUiModel.SpotErrorSlide -> {
                val pl = qSlide.question.payload
                val sanitizedTemplate = sanitizeAnswerMarkup(pl.template)
                val preview = listOf(sanitizedTemplate)
                val search = buildList {
                    add(sanitizedTemplate)
                    pl.segments.forEach { seg ->
                        add(seg.text)
                        seg.corrections?.let(::addAll)
                    }
                    pl.explanation?.let(::add)
                }
                Quad("SPOT ERROR", sanitizedTemplate.take(64), preview, search)
            }
            is SlideUiModel.DifferentiationSlide -> {
                val pl = qSlide.question.payload
                val sanitizedRows = pl.rows.flatMap { row -> row.map(::sanitizeAnswerMarkup) }
                val preview = buildList {
                    pl.title?.let(::add)
                    addAll(pl.headers)
                    addAll(sanitizedRows)
                }
                val search = buildList {
                    addAll(preview)
                    pl.specialCells.forEach { sc ->
                        sc.blankAnswers?.let(::addAll)
                        sc.wrongText?.let(::add)
                        sc.corrections?.let(::addAll)
                    }
                    pl.explanation?.let(::add)
                }
                Quad("DIFFERENTIATION", (pl.title ?: pl.headers.joinToString(" vs ")).take(64), preview, search)
            }
            is SlideUiModel.MechanismSlide -> {
                val pl = qSlide.question.payload
                val safeTitle = pl.title?.takeIf { it.isNotBlank() } ?: "Mechanism (${pl.steps.size} steps)"
                val preview = listOf(safeTitle)
                val search = buildList {
                    pl.title?.let(::add)
                    pl.steps.forEach { add(it.text) }
                    pl.explanation?.let(::add)
                }
                Quad("MECHANISM", safeTitle.take(64), preview, search)
            }
            is SlideUiModel.RelabelImageSlide -> {
                val pl = qSlide.question.payload
                val safePrompt = pl.prompt?.takeIf { it.isNotBlank() }
                    ?: "Label ${pl.boxes.size} diagram region(s)"
                val preview = listOf(safePrompt)
                val search = buildList {
                    pl.prompt?.let(::add)
                    pl.boxes.forEach { box ->
                        add(box.label)
                        addAll(box.aliases)
                    }
                    addAll(pl.distractors)
                    pl.explanation?.let(::add)
                }
                Quad("RELABEL IMAGE", safePrompt.take(64), preview, search)
            }
        }
        val cleanTitle = "$titlePrefix — ${titleSuffix.replace('\n', ' ').trim()}"
        return SessionSearchEntry(
            id = qSlide.id,
            outerSlideIndex = outerIdx,
            quizId = quizId,
            quizQuestionIndex = quizQuestionIdx,
            kindLabel = kindLabel,
            title = cleanTitle,
            previewText = previewParts.joinToString(" ").replace(Regex("\\s+"), " ").trim(),
            searchableText = searchParts.joinToString(" ").replace(Regex("\\s+"), " ").trim()
        )
    }

    private data class Quad(
        val first: String,
        val second: String,
        val third: List<String>,
        val fourth: List<String>
    )

    private fun extractSnippet(raw: String, tokens: List<String>): String {
        if (raw.length <= 130) return raw
        val lower = raw.lowercase(Locale.ROOT)
        val firstHit = tokens.mapNotNull { tok ->
            lower.indexOf(tok).takeIf { it >= 0 }
        }.minOrNull() ?: 0
        val start = (firstHit - 36).coerceAtLeast(0)
        val end = (start + 130).coerceAtMost(raw.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < raw.length) "…" else ""
        return "$prefix${raw.substring(start, end)}$suffix"
    }
}

@Composable
internal fun SessionSearchModal(
    slides: List<SlideUiModel>,
    onJumpToEntry: (SessionSearchEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalMeloberryColors.current
    var query by remember { mutableStateOf("") }
    val entries = remember(slides) { SessionSearchIndex.build(slides) }
    val tokens = remember(query) {
        query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotBlank() }
    }
    val matches = remember(entries, query) { SessionSearchIndex.search(entries, query) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .imePadding()
                .fillMaxWidth(0.94f)
                .heightIn(max = 580.dp),
            shape = RoundedCornerShape(18.dp),
            color = palette.surface,
            border = BorderStroke(1.dp, palette.border),
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Search Session",
                        color = palette.accent,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close search", tint = palette.muted)
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = palette.muted, modifier = Modifier.size(18.dp))
                            }
                        }
                    } else null,
                    placeholder = { Text("Search articles, tables, callouts, questions...", color = palette.muted, fontSize = 14.sp) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.border,
                        focusedTextColor = palette.text,
                        unfocusedTextColor = palette.text
                    )
                )

                Text(
                    text = if (query.isBlank()) "Outline (${matches.size} items)" else "${matches.size} match(es)",
                    color = palette.muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                if (matches.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No matching slides found.", color = palette.muted, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        items(matches, key = { "${it.entry.outerSlideIndex}:${it.entry.id}" }) { match ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onJumpToEntry(match.entry) },
                                shape = RoundedCornerShape(12.dp),
                                color = palette.background,
                                border = BorderStroke(1.dp, palette.border)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(palette.accentSoft, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = match.entry.kindLabel,
                                                color = palette.accent,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                        Text(
                                            text = match.entry.title,
                                            color = palette.text,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (match.snippet.isNotBlank()) {
                                        Text(
                                            text = highlightSnippet(match.snippet, tokens, palette.accent, palette.accentSoft),
                                            color = palette.muted,
                                            fontSize = 12.sp,
                                            lineHeight = 17.sp,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun highlightSnippet(
    text: String,
    tokens: List<String>,
    highlightColor: Color,
    highlightBg: Color
): AnnotatedString {
    if (tokens.isEmpty()) return AnnotatedString(text)
    val lower = text.lowercase(Locale.ROOT)
    val ranges = mutableListOf<IntRange>()
    tokens.forEach { tok ->
        var idx = lower.indexOf(tok)
        while (idx >= 0) {
            ranges += idx until (idx + tok.length)
            idx = lower.indexOf(tok, idx + tok.length)
        }
    }
    if (ranges.isEmpty()) return AnnotatedString(text)
    ranges.sortBy { it.first }
    val merged = mutableListOf<IntRange>()
    for (r in ranges) {
        if (merged.isEmpty() || r.first > merged.last().last + 1) {
            merged += r
        } else {
            val prev = merged.removeAt(merged.lastIndex)
            merged += prev.first..maxOf(prev.last, r.last)
        }
    }
    return buildAnnotatedString {
        var cursor = 0
        for (r in merged) {
            val start = r.first.coerceIn(0, text.length)
            val endExclusive = (r.last + 1).coerceIn(start, text.length)
            if (start > cursor) append(text.substring(cursor, start))
            if (endExclusive > start) {
                withStyle(SpanStyle(color = highlightColor, background = highlightBg, fontWeight = FontWeight.Bold)) {
                    append(text.substring(start, endExclusive))
                }
            }
            cursor = endExclusive
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}
