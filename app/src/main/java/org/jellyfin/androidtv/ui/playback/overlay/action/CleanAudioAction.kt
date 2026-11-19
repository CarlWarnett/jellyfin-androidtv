package org.jellyfin.androidtv.ui.playback.overlay.action

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.androidtv.ui.playback.overlay.CustomPlaybackTransportControlGlue
import org.jellyfin.androidtv.ui.playback.overlay.VideoPlayerAdapter

/**
 * Profanity filter on/off. Index 0 = on (clean audio), index 1 = off (original audio).
 */
class CleanAudioAction(
	context: Context,
	customPlaybackTransportControlGlue: CustomPlaybackTransportControlGlue,
) : CustomAction(context, customPlaybackTransportControlGlue) {
	init {
		setDrawables(
			arrayOf(
				ContextCompat.getDrawable(context, R.drawable.ic_profanity_filter_on),
				ContextCompat.getDrawable(context, R.drawable.ic_profanity_filter_off),
			)
		)
		setLabels(
			arrayOf(
				context.getString(R.string.lbl_profanity_filter_on),
				context.getString(R.string.lbl_profanity_filter_off),
			)
		)
	}

	fun sync(enabled: Boolean) {
		index = if (enabled) INDEX_ON else INDEX_OFF
	}

	override fun handleClickAction(
		playbackController: PlaybackController,
		videoPlayerAdapter: VideoPlayerAdapter,
		context: Context,
		view: View,
	) {
		val enabled = !playbackController.isProfanityFilterEnabled
		playbackController.setProfanityFilterEnabled(enabled)
		sync(enabled)
		Toast.makeText(
			context,
			if (enabled) R.string.msg_profanity_filter_on else R.string.msg_profanity_filter_off,
			Toast.LENGTH_SHORT,
		).show()
	}

	private companion object {
		const val INDEX_ON = 0
		const val INDEX_OFF = 1
	}
}
