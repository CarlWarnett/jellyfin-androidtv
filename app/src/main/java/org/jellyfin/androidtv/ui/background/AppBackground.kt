package org.jellyfin.androidtv.ui.background

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import org.jellyfin.androidtv.data.service.BackgroundService
import org.koin.compose.koinInject

/** Dark base of the "Jellyfin Clean" look. */
private val CleanBackground = Color(0xFF101014)

/** Shown when there is no backdrop: the plain dark base instead of the theme's background image. */
@Composable
private fun AppThemeBackground() {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(CleanBackground)
	)
}

@Composable
fun AppBackground() {
	val backgroundService = koinInject<BackgroundService>()
	val currentBackground by backgroundService.currentBackground.collectAsState()
	val enabled by backgroundService.enabled.collectAsState()

	if (enabled) {
		AnimatedContent(
			targetState = currentBackground,
			transitionSpec = {
				val duration = (BackgroundService.TRANSITION_DURATION.inWholeMilliseconds / 2).toInt()
				fadeIn(tween(durationMillis = duration)) togetherWith fadeOut(snap(delayMillis = duration))
			},
			label = "BackgroundTransition",
		) { background ->
			if (background != null) {
				// Plex-style: a sharp backdrop in the top-right that fades into a dark base on the
				// left and bottom, so text and rows stay readable without blurring the picture
				Box(Modifier.fillMaxSize().background(CleanBackground)) {
					Image(
						bitmap = background,
						contentDescription = null,
						alignment = Alignment.TopEnd,
						contentScale = ContentScale.Crop,
						colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.25f), BlendMode.SrcAtop),
						modifier = Modifier
							.align(Alignment.TopEnd)
							.fillMaxWidth(0.72f)
							.fillMaxHeight(0.8f)
					)
					Box(
						Modifier
							.fillMaxSize()
							.background(
								Brush.horizontalGradient(
									0.0f to CleanBackground,
									0.32f to CleanBackground,
									0.62f to CleanBackground.copy(alpha = 0.55f),
									1.0f to Color.Transparent,
								)
							)
					)
					Box(
						Modifier
							.fillMaxSize()
							.background(
								Brush.verticalGradient(
									0.0f to Color.Transparent,
									0.45f to Color.Transparent,
									0.8f to CleanBackground,
									1.0f to CleanBackground,
								)
							)
					)
				}
			} else {
				AppThemeBackground()
			}
		}
	}
}
