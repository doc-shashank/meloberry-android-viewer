package com.meloberry.android.feature.player

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meloberry.android.core.model.SlideUiModel
import com.meloberry.android.core.ui.components.MeloberrySnackbarEffect
import com.meloberry.android.core.ui.components.PendingDocumentExportEffect
import com.meloberry.android.core.ui.theme.LocalMeloberryColors
import com.meloberry.android.core.ui.theme.MeloberryTheme

@Composable
fun SessionPlayerRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionPlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    PendingDocumentExportEffect(
        pendingExport = uiState.pendingExport,
        onExportConsumed = viewModel::onExportConsumed,
        onSaveExportToUri = viewModel::onSaveExportToUri
    )

    val pendingShare = uiState.pendingShareText
    LaunchedEffect(pendingShare) {
        if (pendingShare != null) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/markdown"
                putExtra(Intent.EXTRA_SUBJECT, "Meloberry Session Analysis — ${uiState.workspaceId}")
                putExtra(Intent.EXTRA_TEXT, pendingShare)
            }
            runCatching {
                context.startActivity(Intent.createChooser(intent, "Share Session Analysis"))
            }
            viewModel.onShareConsumed()
        }
    }

    SessionPlayerScreen(
        uiState = uiState,
        onBack = onBack,
        onSlideChanged = viewModel::onSlideChanged,
        onSelectQuizQuestion = viewModel::onSelectQuizQuestion,
        onFinishAndAnalyze = viewModel::onFinishAndAnalyze,
        onReturnToSlides = viewModel::onReturnToSlides,
        onToggleMcqOption = viewModel::onToggleMcqOption,
        onCheckMcqAnswer = viewModel::onCheckMcqAnswer,
        onFlipFlashcard = viewModel::onFlipFlashcard,
        onRateFlashcard = viewModel::onRateFlashcard,
        onExportJson = viewModel::onRequestExportJson,
        onExportMarkdown = viewModel::onRequestExportMarkdown,
        onShareMarkdown = viewModel::onRequestShareMarkdown,
        onMessageShown = viewModel::onMessageShown,
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionPlayerScreen(
    uiState: SessionPlayerUiState,
    onBack: () -> Unit,
    onSlideChanged: (Int) -> Unit,
    onSelectQuizQuestion: (String, Int) -> Unit,
    onFinishAndAnalyze: () -> Unit,
    onReturnToSlides: () -> Unit,
    onToggleMcqOption: (String, Int) -> Unit,
    onCheckMcqAnswer: (String) -> Unit,
    onFlipFlashcard: (String) -> Unit,
    onRateFlashcard: (String, String) -> Unit,
    onExportJson: () -> Unit,
    onExportMarkdown: () -> Unit,
    onShareMarkdown: () -> Unit,
    onMessageShown: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    MeloberryTheme(themeMode = uiState.themeMode) {
        val palette = LocalMeloberryColors.current
        val snackbarHostState = remember { SnackbarHostState() }
        var showEarlyFinishDialog by remember { mutableStateOf(false) }

        MeloberrySnackbarEffect(
            messages = uiState.userMessages,
            snackbarHostState = snackbarHostState,
            onMessageShown = onMessageShown
        )

        if (uiState.isLoading) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(palette.background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = palette.accent)
            }
            return@MeloberryTheme
        }

        if (uiState.notFound) {
            PlayerMessageScreen(
                message = "Workspace '${uiState.workspaceId}' was not found.",
                onBack = onBack,
                snackbarHostState = snackbarHostState,
                modifier = modifier
            )
            return@MeloberryTheme
        }

        if (uiState.isFinished) {
            FinishSummaryScreen(
                uiState = uiState,
                snackbarHostState = snackbarHostState,
                onReturnToSlides = onReturnToSlides,
                onDoneToLibrary = onBack,
                onExportJson = onExportJson,
                onExportMarkdown = onExportMarkdown,
                onShareMarkdown = onShareMarkdown,
                modifier = modifier
            )
            return@MeloberryTheme
        }

        val totalPages = uiState.slides.size
        if (totalPages == 0) {
            PlayerMessageScreen(
                message = "This workspace does not have any slides yet.",
                onBack = onBack,
                snackbarHostState = snackbarHostState,
                modifier = modifier
            )
            return@MeloberryTheme
        }

        val sessionPager = rememberSessionPagerState(
            targetPage = uiState.currentSlideIndex,
            pageCount = totalPages,
            slideTransitionMs = uiState.slideTransitionMs,
            onPageSettled = onSlideChanged
        )

        val activeIndex = sessionPager.pagerState.currentPage
        val isLastComponent = activeIndex >= totalPages - 1
        val activeQuizSlide = uiState.slides.getOrNull(activeIndex) as? SlideUiModel.QuizSlide
        val activeQuizQuestionCount = activeQuizSlide?.questions?.size ?: 0
        val activeQuizQuestionIdx = if (activeQuizSlide != null && activeQuizQuestionCount > 0) {
            (uiState.activeQuizQuestionIndex[activeQuizSlide.id] ?: 0)
                .coerceIn(0, activeQuizQuestionCount - 1)
        } else {
            0
        }
        val isAtEndOfCurrentQuiz = activeQuizSlide == null ||
            activeQuizQuestionCount == 0 ||
            activeQuizQuestionIdx >= activeQuizQuestionCount - 1
        val isFinalSaveStep = isLastComponent && isAtEndOfCurrentQuiz
        val canGoPrevious = (activeQuizSlide != null && activeQuizQuestionIdx > 0) || activeIndex > 0
        val canLongPressPrevious = activeQuizSlide != null && activeIndex > 0
        var showPrevComponentMenu by remember { mutableStateOf(false) }

        LaunchedEffect(activeIndex) {
            showPrevComponentMenu = false
        }

        if (showEarlyFinishDialog) {
            AlertDialog(
                onDismissRequest = { showEarlyFinishDialog = false },
                title = { Text("Finish session early?") },
                text = { Text("You are in between components. Do you want to finish now and save your session analysis?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showEarlyFinishDialog = false
                            onFinishAndAnalyze()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                    ) {
                        Text("Finish", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEarlyFinishDialog = false }) { Text("Continue") }
                }
            )
        }

        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = palette.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Surface(color = palette.surface, border = BorderStroke(1.dp, palette.border)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = palette.text)
                        }
                        Text(
                            text = uiState.title.ifBlank { "Meloberry" },
                            color = palette.accent,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                }
            },
            bottomBar = {
                Surface(color = palette.surface, border = BorderStroke(1.dp, palette.border)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = palette.surface,
                                border = BorderStroke(1.dp, palette.border),
                                modifier = Modifier
                                    .alpha(if (canGoPrevious) 1f else 0.45f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .combinedClickable(
                                        enabled = canGoPrevious,
                                        role = Role.Button,
                                        onClick = {
                                            showPrevComponentMenu = false
                                            if (activeQuizSlide != null && activeQuizQuestionIdx > 0) {
                                                onSelectQuizQuestion(activeQuizSlide.id, activeQuizQuestionIdx - 1)
                                            } else if (activeIndex > 0) {
                                                sessionPager.animateToSlide(activeIndex - 1)
                                            }
                                        },
                                        onLongClick = if (canLongPressPrevious) {
                                            { showPrevComponentMenu = true }
                                        } else null
                                    )
                            ) {
                                Text(
                                    text = "← Previous",
                                    color = palette.text,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                )
                            }

                            val prevComponentLabel = when (uiState.slides.getOrNull(activeIndex - 1)) {
                                is SlideUiModel.ArticleSlide -> "Article"
                                is SlideUiModel.QuizSlide -> "Quiz"
                                is SlideUiModel.McqSlide -> "MCQ"
                                is SlideUiModel.FlashcardSlide -> "Flashcard"
                                null -> "Component"
                            }
                            DropdownMenu(
                                expanded = showPrevComponentMenu && canLongPressPrevious,
                                onDismissRequest = { showPrevComponentMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Previous $prevComponentLabel", fontWeight = FontWeight.SemiBold) },
                                    onClick = {
                                        showPrevComponentMenu = false
                                        if (activeIndex > 0) sessionPager.animateToSlide(activeIndex - 1)
                                    }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(totalPages) { dotIdx ->
                                val isActive = dotIdx == activeIndex
                                Box(
                                    modifier = Modifier
                                        .size(if (isActive) 12.dp else 9.dp)
                                        .clip(CircleShape)
                                        .background(if (isActive) palette.accent else palette.border)
                                        .clickable { sessionPager.animateToSlide(dotIdx) }
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = palette.accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .combinedClickable(
                                    role = Role.Button,
                                    onClick = {
                                        showPrevComponentMenu = false
                                        when {
                                            activeQuizSlide != null && !isAtEndOfCurrentQuiz ->
                                                onSelectQuizQuestion(activeQuizSlide.id, activeQuizQuestionIdx + 1)
                                            isLastComponent -> onFinishAndAnalyze()
                                            else -> sessionPager.animateToSlide(activeIndex + 1)
                                        }
                                    },
                                    onLongClick = if (!isFinalSaveStep) {
                                        { showEarlyFinishDialog = true }
                                    } else null
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isFinalSaveStep) {
                                    Icon(
                                        imageVector = Icons.Default.Save,
                                        contentDescription = "Save and finish",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text("Next", fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 14.sp)
                                } else {
                                    Text("Next →", fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            HorizontalPager(
                state = sessionPager.pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) { pageIndex ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    when (val slide = uiState.slides[pageIndex]) {
                        is SlideUiModel.ArticleSlide -> ArticleSlideCard(slide = slide)
                        is SlideUiModel.McqSlide -> McqSlideCard(
                            slide = slide,
                            runtimeState = uiState.mcqStates[slide.id] ?: McqSlideRuntimeState(),
                            onToggleOption = { optIdx -> onToggleMcqOption(slide.id, optIdx) },
                            onCheckAnswer = { onCheckMcqAnswer(slide.id) }
                        )
                        is SlideUiModel.FlashcardSlide -> FlashcardSlideCard(
                            slide = slide,
                            runtimeState = uiState.flashcardStates[slide.id] ?: FlashcardSlideRuntimeState(),
                            onFlip = { onFlipFlashcard(slide.id) },
                            onRate = { rating -> onRateFlashcard(slide.id, rating) }
                        )
                        is SlideUiModel.QuizSlide -> QuizBundleSlideContent(
                            quizSlide = slide,
                            activeQuestionIndex = (uiState.activeQuizQuestionIndex[slide.id] ?: 0)
                                .coerceIn(0, (slide.questions.size - 1).coerceAtLeast(0)),
                            slideTransitionMs = uiState.slideTransitionMs,
                            mcqStates = uiState.mcqStates,
                            flashcardStates = uiState.flashcardStates,
                            onSelectQuestion = { qIdx -> onSelectQuizQuestion(slide.id, qIdx) },
                            onToggleMcqOption = onToggleMcqOption,
                            onCheckMcqAnswer = onCheckMcqAnswer,
                            onFlipFlashcard = onFlipFlashcard,
                            onRateFlashcard = onRateFlashcard
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerMessageScreen(
    message: String,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val palette = LocalMeloberryColors.current
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = palette.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = message, color = palette.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
            ) {
                Text("Back to Library")
            }
        }
    }
}

@Composable
private fun QuizBundleSlideContent(
    quizSlide: SlideUiModel.QuizSlide,
    activeQuestionIndex: Int,
    slideTransitionMs: Int,
    mcqStates: Map<String, McqSlideRuntimeState>,
    flashcardStates: Map<String, FlashcardSlideRuntimeState>,
    onSelectQuestion: (Int) -> Unit,
    onToggleMcqOption: (String, Int) -> Unit,
    onCheckMcqAnswer: (String) -> Unit,
    onFlipFlashcard: (String) -> Unit,
    onRateFlashcard: (String, String) -> Unit
) {
    val palette = LocalMeloberryColors.current
    val questions = quizSlide.questions
    if (questions.isEmpty()) return

    val clampedActiveIndex = activeQuestionIndex.coerceIn(0, questions.lastIndex)
    val questionPager = rememberSessionPagerState(
        targetPage = clampedActiveIndex,
        pageCount = questions.size,
        slideTransitionMs = slideTransitionMs,
        onPageSettled = onSelectQuestion
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = quizSlide.layout.cardMaxWidthDp.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            questions.forEachIndexed { idx, questionItem ->
                val isSelected = idx == clampedActiveIndex
                val isAnswered = when (questionItem) {
                    is SlideUiModel.McqSlide -> mcqStates[questionItem.id]?.isChecked == true
                    is SlideUiModel.FlashcardSlide -> flashcardStates[questionItem.id]?.rating != null
                }
                val bubbleBg = when {
                    isSelected -> palette.accent
                    isAnswered -> palette.accentSoft
                    else -> palette.surface
                }
                val bubbleText = when {
                    isSelected -> Color.White
                    isAnswered -> palette.accent
                    else -> palette.text
                }
                val bubbleBorder = if (isSelected || isAnswered) palette.accent else palette.border

                Surface(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 38.dp, minHeight = 38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectQuestion(idx) },
                    shape = RoundedCornerShape(10.dp),
                    color = bubbleBg,
                    border = BorderStroke(1.dp, bubbleBorder)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Q${idx + 1}",
                            color = bubbleText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        HorizontalPager(
            state = questionPager.pagerState,
            pageSpacing = 12.dp,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth()
        ) { qIndex ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                when (val question = questions[qIndex]) {
                    is SlideUiModel.McqSlide -> McqSlideCard(
                        slide = question,
                        runtimeState = mcqStates[question.id] ?: McqSlideRuntimeState(),
                        onToggleOption = { optIdx -> onToggleMcqOption(question.id, optIdx) },
                        onCheckAnswer = { onCheckMcqAnswer(question.id) }
                    )
                    is SlideUiModel.FlashcardSlide -> FlashcardSlideCard(
                        slide = question,
                        runtimeState = flashcardStates[question.id] ?: FlashcardSlideRuntimeState(),
                        onFlip = { onFlipFlashcard(question.id) },
                        onRate = { rating -> onRateFlashcard(question.id, rating) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FinishSummaryScreen(
    uiState: SessionPlayerUiState,
    snackbarHostState: SnackbarHostState,
    onReturnToSlides: () -> Unit,
    onDoneToLibrary: () -> Unit,
    onExportJson: () -> Unit,
    onExportMarkdown: () -> Unit,
    onShareMarkdown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMeloberryColors.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = palette.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(color = palette.surface, border = BorderStroke(1.dp, palette.border)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onReturnToSlides) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to slides", tint = palette.text)
                        }
                        Text(
                            text = "Finish & Analysis",
                            color = palette.accent,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    TextButton(onClick = onDoneToLibrary) {
                        Text("Library", color = palette.accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            AnalysisSummaryCard(
                report = uiState.completedReport,
                onExportJson = onExportJson,
                onExportMarkdown = onExportMarkdown,
                onShareMarkdown = onShareMarkdown
            )
        }
    }
}
