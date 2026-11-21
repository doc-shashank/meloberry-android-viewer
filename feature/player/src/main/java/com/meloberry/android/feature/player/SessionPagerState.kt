package com.meloberry.android.feature.player

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Stable
class SessionPagerState(
    val pagerState: PagerState,
    private val scope: CoroutineScope,
    private val slideTransitionMs: Int
) {
    private val slideEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    fun animateToSlide(index: Int) {
        val target = index.coerceIn(0, (pagerState.pageCount - 1).coerceAtLeast(0))
        scope.launch {
            pagerState.animateScrollToPage(
                page = target,
                animationSpec = tween(durationMillis = slideTransitionMs, easing = slideEasing)
            )
        }
    }
}

@Composable
fun rememberSessionPagerState(
    targetPage: Int,
    pageCount: Int,
    slideTransitionMs: Int,
    onPageSettled: (Int) -> Unit
): SessionPagerState {
    val clampedTarget = targetPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = clampedTarget,
        pageCount = { pageCount.coerceAtLeast(1) }
    )
    val scope = rememberCoroutineScope()
    val sessionPager = remember(pagerState, scope, slideTransitionMs) {
        SessionPagerState(pagerState = pagerState, scope = scope, slideTransitionMs = slideTransitionMs)
    }

    val latestTarget by rememberUpdatedState(clampedTarget)
    val latestOnSettled by rememberUpdatedState(onPageSettled)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settled ->
                if (settled != latestTarget) {
                    latestOnSettled(settled)
                }
            }
    }

    LaunchedEffect(clampedTarget) {
        if (pagerState.currentPage != clampedTarget || pagerState.settledPage != clampedTarget) {
            sessionPager.animateToSlide(clampedTarget)
        }
    }

    return sessionPager
}
