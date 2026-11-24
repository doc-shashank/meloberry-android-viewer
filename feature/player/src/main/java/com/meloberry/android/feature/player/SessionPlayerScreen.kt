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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meloberry.android.core.model.QuestionSlideItem
import com.meloberry.android.core.model.SlideUiModel
import com.meloberry.android.core.ui.components.MeloberrySnackbarEffect
import com.meloberry.android.core.ui.components.PendingDocumentExportEffect
import com.meloberry.android.core.ui.theme.LocalMeloberryColors
import com.meloberry.android.core.ui.theme.MeloberryTheme
import kotlinx.coroutines.launch

data class PlayerV2Callbacks(
    val onUpdateFillBlankInput: (String, Int, String) -> Unit = { _, _, _ -> },
    val onCheckFillBlank: (String) -> Unit = {},
    val onOverrideFillBlankSlot: (String, Int) -> Unit = { _, _ -> },
    val onOverrideFillBlankCorrect: (String) -> Unit = {},
    val onRetryFillBlank: (String) -> Unit = {},
    val onSelectAssertionReason: (String, String) -> Unit = { _, _ -> },
    val onCheckAssertionReason: (String) -> Unit = {},
    val onRetryAssertionReason: (String) -> Unit = {},
    val onToggleSpotErrorSpan: (String, Int) -> Unit = { _, _ -> },
    val onToggleSpotErrorNoError: (String) -> Unit = {},
    val onUpdateSpotErrorFix: (String, Int, String) -> Unit = { _, _, _ -> },
    val onCheckSpotError: (String) -> Unit = {},
    val onRetrySpotError: (String) -> Unit = {},
    val onUpdateDiffFillInput: (String, Int, Int, String) -> Unit = { _, _, _, _ -> },
    val onToggleDiffErrorCell: (String, Int, Int) -> Unit = { _, _, _ -> },
    val onToggleDiffNoError: (String) -> Unit = {},
    val onUpdateDiffRearrangeRows: (String, List<List<String>>) -> Unit = { _, _ -> },
    val onCheckDifferentiation: (String) -> Unit = {},
    val onOverrideDifferentiationCorrect: (String) -> Unit = {},
    val onRetryDifferentiation: (String) -> Unit = {},
    val onUpdateMechanismSequence: (String, List<Int>, List<Int>) -> Unit = { _, _, _ -> },
    val onCheckMechanism: (String) -> Unit = {},
    val onRetryMechanism: (String) -> Unit = {},
    val onToggleRelabelRevealBox: (String, Int) -> Unit = { _, _ -> },
    val onRateRelabelReveal: (String, String) -> Unit = { _, _ -> },
    val onAssignRelabelBankLabel: (String, Int, String) -> Unit = { _, _, _ -> },
    val onUpdateRelabelRecallInput: (String, Int, String) -> Unit = { _, _, _ -> },
    val onCheckRelabelImage: (String) -> Unit = {},
    val onOverrideRelabelCorrect: (String) -> Unit = {},
    val onRetryRelabelImage: (String) -> Unit = {}
)

@Composable
fun SessionPlayerRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionPlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> viewModel.onPauseTelemetry()
                Lifecycle.Event.ON_RESUME -> viewModel.onResumeTelemetry()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    PendingDocumentExportEffect(
        pendingExport = uiState.pendingExport,
        onExportConsumed = viewModel::onExportConsumed,
        onSaveExportToUri = viewModel::onSaveExportToUri
    )

    val pendingShare = uiState.pendingShareText
    LaunchedEffect(pendingShare) {
        if (pendingShare != null) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Meloberry Session Analysis — ${uiState.workspaceId}")
                putExtra(Intent.EXTRA_TEXT, pendingShare)
            }
            runCatching {
                context.startActivity(Intent.createChooser(intent, "Share Session Analysis"))
            }
            viewModel.onShareConsumed()
        }
    }

    val v2Callbacks = remember(viewModel) {
        PlayerV2Callbacks(
            onUpdateFillBlankInput = viewModel::onUpdateFillBlankInput,
            onCheckFillBlank = viewModel::onCheckFillBlank,
            onOverrideFillBlankSlot = viewModel::onOverrideFillBlankSlot,
            onOverrideFillBlankCorrect = viewModel::onOverrideFillBlankCorrect,
            onRetryFillBlank = viewModel::onRetryFillBlank,
            onSelectAssertionReason = viewModel::onSelectAssertionReason,
            onCheckAssertionReason = viewModel::onCheckAssertionReason,
            onRetryAssertionReason = viewModel::onRetryAssertionReason,
            onToggleSpotErrorSpan = viewModel::onToggleSpotErrorSpan,
            onToggleSpotErrorNoError = viewModel::onToggleSpotErrorNoError,
            onUpdateSpotErrorFix = viewModel::onUpdateSpotErrorFix,
            onCheckSpotError = viewModel::onCheckSpotError,
            onRetrySpotError = viewModel::onRetrySpotError,
            onUpdateDiffFillInput = viewModel::onUpdateDiffFillInput,
            onToggleDiffErrorCell = viewModel::onToggleDiffErrorCell,
            onToggleDiffNoError = viewModel::onToggleDiffNoError,
            onUpdateDiffRearrangeRows = viewModel::onUpdateDiffRearrangeRows,
            onCheckDifferentiation = viewModel::onCheckDifferentiation,
            onOverrideDifferentiationCorrect = viewModel::onOverrideDifferentiationCorrect,
            onRetryDifferentiation = viewModel::onRetryDifferentiation,
            onUpdateMechanismSequence = viewModel::onUpdateMechanismSequence,
            onCheckMechanism = viewModel::onCheckMechanism,
            onRetryMechanism = viewModel::onRetryMechanism,
            onToggleRelabelRevealBox = viewModel::onToggleRelabelRevealBox,
            onRateRelabelReveal = viewModel::onRateRelabelReveal,
            onAssignRelabelBankLabel = viewModel::onAssignRelabelBankLabel,
            onUpdateRelabelRecallInput = viewModel::onUpdateRelabelRecallInput,
            onCheckRelabelImage = viewModel::onCheckRelabelImage,
            onOverrideRelabelCorrect = viewModel::onOverrideRelabelCorrect,
            onRetryRelabelImage = viewModel::onRetryRelabelImage
        )
    }

    SessionPlayerScreen(
        uiState = uiState,
        onBack = onBack,
        onStartSession = viewModel::onStartSession,
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
        v2Callbacks = v2Callbacks,
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionPlayerScreen(
    uiState: SessionPlayerUiState,
    onBack: () -> Unit,
    onStartSession: () -> Unit = {},
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
    v2Callbacks: PlayerV2Callbacks = PlayerV2Callbacks(),
    modifier: Modifier = Modifier
) {
    MeloberryTheme(themeMode = uiState.themeMode) {
        val palette = LocalMeloberryColors.current
        val snackbarHostState = remember { SnackbarHostState() }
        var showEarlyFinishDialog by remember { mutableStateOf(false) }
        var showSearchModal by remember { mutableStateOf(false) }

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

        if (uiState.loadError != null) {
            PlayerMessageScreen(
                message = "Unable to open workspace '${uiState.workspaceId}': ${uiState.loadError}",
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

        if (!uiState.hasStartedSession) {
            SessionGreetingScreen(
                uiState = uiState,
                snackbarHostState = snackbarHostState,
                onBack = onBack,
                onStartSession = onStartSession,
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

        if (showSearchModal) {
            SessionSearchModal(
                slides = uiState.slides,
                onJumpToEntry = { entry ->
                    showSearchModal = false
                    if (entry.quizId != null && entry.quizQuestionIndex != null) {
                        onSelectQuizQuestion(entry.quizId, entry.quizQuestionIndex)
                    } else {
                        sessionPager.animateToSlide(entry.outerSlideIndex)
                    }
                },
                onDismiss = { showSearchModal = false }
            )
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
            modifier = modifier
                .fillMaxSize()
                .onPreviewKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyDown && ev.key == Key.K && (ev.isCtrlPressed || ev.isMetaPressed)) {
                        showSearchModal = true
                        true
                    } else {
                        false
                    }
                }
                .onKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyDown && ev.key == Key.Slash && !showSearchModal) {
                        showSearchModal = true
                        true
                    } else {
                        false
                    }
                },
            containerColor = palette.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Surface(color = palette.surface, border = BorderStroke(1.dp, palette.border)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = palette.text)
                        }
                        Text(
                            text = uiState.title.ifBlank { "Meloberry" },
                            color = palette.accent,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = palette.background,
                            border = BorderStroke(1.dp, palette.border)
                        ) {
                            Text(
                                text = "${activeIndex + 1} / $totalPages",
                                color = palette.muted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        if (uiState.totalMcqCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = palette.accentSoft,
                                border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = "${uiState.currentMcqScore}/${uiState.totalMcqCount}",
                                    color = palette.accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        IconButton(onClick = { showSearchModal = true }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search session",
                                tint = palette.accent
                            )
                        }
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

                            val prevComponentLabel = when (val prevSlide = uiState.slides.getOrNull(activeIndex - 1)) {
                                is SlideUiModel.ArticleSlide -> prevSlide.title?.takeIf { it.isNotBlank() } ?: "Article"
                                is SlideUiModel.QuizSlide -> prevSlide.title?.takeIf { it.isNotBlank() } ?: "Quiz"
                                is SlideUiModel.McqSlide -> "MCQ"
                                is SlideUiModel.FlashcardSlide -> "Flashcard"
                                is SlideUiModel.FillBlankSlide -> "Fill in the Blank"
                                is SlideUiModel.AssertionReasonSlide -> "Assertion–Reason"
                                is SlideUiModel.SpotErrorSlide -> "Spot the Error"
                                is SlideUiModel.DifferentiationSlide -> "Differentiation"
                                is SlideUiModel.MechanismSlide -> "Mechanism"
                                is SlideUiModel.RelabelImageSlide -> "Relabel Image"
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
                val slideScrollState = rememberScrollState()
                val scrollScope = rememberCoroutineScope()
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(slideScrollState)
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        when (val slide = uiState.slides[pageIndex]) {
                            is SlideUiModel.ArticleSlide -> ArticleSlideCard(slide = slide)
                            is SlideUiModel.QuizSlide -> QuizBundleSlideContent(
                                quizSlide = slide,
                                activeQuestionIndex = (uiState.activeQuizQuestionIndex[slide.id] ?: 0)
                                    .coerceIn(0, (slide.questions.size - 1).coerceAtLeast(0)),
                                slideTransitionMs = uiState.slideTransitionMs,
                                uiState = uiState,
                                onSelectQuestion = { qIdx -> onSelectQuizQuestion(slide.id, qIdx) },
                                onToggleMcqOption = onToggleMcqOption,
                                onCheckMcqAnswer = onCheckMcqAnswer,
                                onFlipFlashcard = onFlipFlashcard,
                                onRateFlashcard = onRateFlashcard,
                                v2Callbacks = v2Callbacks
                            )
                            is QuestionSlideItem -> QuestionSlideContent(
                                question = slide,
                                uiState = uiState,
                                onToggleMcqOption = onToggleMcqOption,
                                onCheckMcqAnswer = onCheckMcqAnswer,
                                onFlipFlashcard = onFlipFlashcard,
                                onRateFlashcard = onRateFlashcard,
                                v2Callbacks = v2Callbacks
                            )
                        }
                    }

                    if (slideScrollState.canScrollForward) {
                        SmallFloatingActionButton(
                            onClick = {
                                scrollScope.launch {
                                    slideScrollState.animateScrollTo(
                                        (slideScrollState.value + 360).coerceAtMost(slideScrollState.maxValue)
                                    )
                                }
                            },
                            containerColor = palette.surface,
                            contentColor = palette.accent,
                            shape = CircleShape,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Scroll down"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionSlideContent(
    question: QuestionSlideItem,
    uiState: SessionPlayerUiState,
    onToggleMcqOption: (String, Int) -> Unit,
    onCheckMcqAnswer: (String) -> Unit,
    onFlipFlashcard: (String) -> Unit,
    onRateFlashcard: (String, String) -> Unit,
    v2Callbacks: PlayerV2Callbacks
) {
    when (question) {
        is SlideUiModel.McqSlide -> McqSlideCard(
            slide = question,
            runtimeState = uiState.mcqStates[question.id] ?: McqSlideRuntimeState(),
            onToggleOption = { optIdx -> onToggleMcqOption(question.id, optIdx) },
            onCheckAnswer = { onCheckMcqAnswer(question.id) }
        )
        is SlideUiModel.FlashcardSlide -> FlashcardSlideCard(
            slide = question,
            runtimeState = uiState.flashcardStates[question.id] ?: FlashcardSlideRuntimeState(),
            onFlip = { onFlipFlashcard(question.id) },
            onRate = { rating -> onRateFlashcard(question.id, rating) }
        )
        is SlideUiModel.FillBlankSlide -> FillBlankSlideCard(
            slide = question,
            runtimeState = uiState.fillBlankStates[question.id] ?: FillBlankSlideRuntimeState(),
            onUpdateInput = { slotIdx, value -> v2Callbacks.onUpdateFillBlankInput(question.id, slotIdx, value) },
            onCheckAnswer = { v2Callbacks.onCheckFillBlank(question.id) },
            onOverrideSlot = { slotIdx -> v2Callbacks.onOverrideFillBlankSlot(question.id, slotIdx) },
            onOverrideCorrect = { v2Callbacks.onOverrideFillBlankCorrect(question.id) },
            onRetry = { v2Callbacks.onRetryFillBlank(question.id) }
        )
        is SlideUiModel.AssertionReasonSlide -> AssertionReasonSlideCard(
            slide = question,
            runtimeState = uiState.assertionReasonStates[question.id] ?: AssertionReasonSlideRuntimeState(),
            onSelectCode = { code -> v2Callbacks.onSelectAssertionReason(question.id, code) },
            onCheckAnswer = { v2Callbacks.onCheckAssertionReason(question.id) },
            onRetry = { v2Callbacks.onRetryAssertionReason(question.id) }
        )
        is SlideUiModel.SpotErrorSlide -> SpotErrorSlideCard(
            slide = question,
            runtimeState = uiState.spotErrorStates[question.id] ?: SpotErrorSlideRuntimeState(),
            onToggleSpan = { spanIdx -> v2Callbacks.onToggleSpotErrorSpan(question.id, spanIdx) },
            onToggleNoError = { v2Callbacks.onToggleSpotErrorNoError(question.id) },
            onUpdateFix = { spanIdx, fix -> v2Callbacks.onUpdateSpotErrorFix(question.id, spanIdx, fix) },
            onCheckAnswer = { v2Callbacks.onCheckSpotError(question.id) },
            onRetry = { v2Callbacks.onRetrySpotError(question.id) }
        )
        is SlideUiModel.DifferentiationSlide -> DifferentiationSlideCard(
            slide = question,
            runtimeState = uiState.differentiationStates[question.id] ?: DifferentiationSlideRuntimeState(),
            onUpdateFillInput = { r, c, value -> v2Callbacks.onUpdateDiffFillInput(question.id, r, c, value) },
            onToggleErrorCell = { r, c -> v2Callbacks.onToggleDiffErrorCell(question.id, r, c) },
            onToggleNoError = { v2Callbacks.onToggleDiffNoError(question.id) },
            onUpdateRearrangeRows = { rows -> v2Callbacks.onUpdateDiffRearrangeRows(question.id, rows) },
            onCheckAnswer = { v2Callbacks.onCheckDifferentiation(question.id) },
            onOverrideCorrect = { v2Callbacks.onOverrideDifferentiationCorrect(question.id) },
            onRetry = { v2Callbacks.onRetryDifferentiation(question.id) }
        )
        is SlideUiModel.MechanismSlide -> MechanismSlideCard(
            slide = question,
            runtimeState = uiState.mechanismStates[question.id] ?: MechanismSlideRuntimeState(),
            onUpdateSequence = { bank, placed -> v2Callbacks.onUpdateMechanismSequence(question.id, bank, placed) },
            onCheckAnswer = { v2Callbacks.onCheckMechanism(question.id) },
            onRetry = { v2Callbacks.onRetryMechanism(question.id) }
        )
        is SlideUiModel.RelabelImageSlide -> RelabelImageSlideCard(
            slide = question,
            runtimeState = uiState.relabelImageStates[question.id] ?: RelabelImageSlideRuntimeState(),
            onToggleRevealBox = { boxIdx -> v2Callbacks.onToggleRelabelRevealBox(question.id, boxIdx) },
            onRateReveal = { rating -> v2Callbacks.onRateRelabelReveal(question.id, rating) },
            onAssignBankLabel = { boxIdx, label -> v2Callbacks.onAssignRelabelBankLabel(question.id, boxIdx, label) },
            onUpdateRecallInput = { boxIdx, value -> v2Callbacks.onUpdateRelabelRecallInput(question.id, boxIdx, value) },
            onCheckAnswer = { v2Callbacks.onCheckRelabelImage(question.id) },
            onOverrideCorrect = { v2Callbacks.onOverrideRelabelCorrect(question.id) },
            onRetry = { v2Callbacks.onRetryRelabelImage(question.id) }
        )
    }
}

@Composable
private fun SessionGreetingScreen(
    uiState: SessionPlayerUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onStartSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMeloberryColors.current
    val itemCount = uiState.leafSlideCount.takeIf { it > 0 } ?: uiState.slides.size

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = palette.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Library", tint = palette.text)
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
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            FramedSlideCard(
                tabLabel = "MELOBERRY",
                maxWidthDp = 560,
                contentPaddingDp = 28,
                topContentPaddingDp = 20,
                contentSpacingDp = 14
            ) {
                Text(
                    text = uiState.title.ifBlank { uiState.workspaceId },
                    color = palette.text,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 30.sp
                )
                if (uiState.description.isNotBlank()) {
                    Text(
                        text = uiState.description,
                        color = palette.muted,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                }
                Text(
                    text = "${uiState.slides.size} component(s)  •  $itemCount item(s)",
                    color = palette.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Button(
                    onClick = onStartSession,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Text(
                        text = "Start Session →",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
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
    uiState: SessionPlayerUiState,
    onSelectQuestion: (Int) -> Unit,
    onToggleMcqOption: (String, Int) -> Unit,
    onCheckMcqAnswer: (String) -> Unit,
    onFlipFlashcard: (String) -> Unit,
    onRateFlashcard: (String, String) -> Unit,
    v2Callbacks: PlayerV2Callbacks
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
        if (!quizSlide.title.isNullOrBlank()) {
            Text(
                text = quizSlide.title.orEmpty(),
                color = palette.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
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
                    is SlideUiModel.McqSlide -> uiState.mcqStates[questionItem.id]?.isChecked == true
                    is SlideUiModel.FlashcardSlide -> uiState.flashcardStates[questionItem.id]?.rating != null
                    is SlideUiModel.FillBlankSlide -> uiState.fillBlankStates[questionItem.id]?.isChecked == true
                    is SlideUiModel.AssertionReasonSlide -> uiState.assertionReasonStates[questionItem.id]?.isChecked == true
                    is SlideUiModel.SpotErrorSlide -> uiState.spotErrorStates[questionItem.id]?.isChecked == true
                    is SlideUiModel.DifferentiationSlide -> uiState.differentiationStates[questionItem.id]?.isChecked == true
                    is SlideUiModel.MechanismSlide -> uiState.mechanismStates[questionItem.id]?.isChecked == true
                    is SlideUiModel.RelabelImageSlide -> uiState.relabelImageStates[questionItem.id]?.isChecked == true
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
                QuestionSlideContent(
                    question = questions[qIndex],
                    uiState = uiState,
                    onToggleMcqOption = onToggleMcqOption,
                    onCheckMcqAnswer = onCheckMcqAnswer,
                    onFlipFlashcard = onFlipFlashcard,
                    onRateFlashcard = onRateFlashcard,
                    v2Callbacks = v2Callbacks
                )
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
