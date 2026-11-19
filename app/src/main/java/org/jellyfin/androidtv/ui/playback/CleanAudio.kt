package org.jellyfin.androidtv.ui.playback

import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType

/**
 * Finds the tracks written by whisper-filter (github.com/CarlWarnett/whisper-filter):
 * an audio track with the swear words muted and a subtitle track with them starred out.
 */
object CleanAudio {
	const val AUDIO_TITLE = "Clean Audio (Muted Profanity)"
	const val SUBTITLE_TITLE = "Clean Subtitles (English)"

	private fun MediaStream.hasTitle(title: String) =
		this.title.equals(title, ignoreCase = true) || displayTitle?.contains(title, ignoreCase = true) == true

	private fun MediaStream.isEnglish() = language.isNullOrBlank() || language.equals("eng", true) || language.equals("en", true)

	private fun streams(source: MediaSourceInfo?, type: MediaStreamType) =
		source?.mediaStreams.orEmpty().filter { it.type == type }

	@JvmStatic
	fun cleanAudioIndex(source: MediaSourceInfo?): Int? =
		streams(source, MediaStreamType.AUDIO).firstOrNull { it.hasTitle(AUDIO_TITLE) }?.index

	/** The original audio track: the first English one that is not the clean track. */
	@JvmStatic
	fun originalAudioIndex(source: MediaSourceInfo?): Int? {
		val others = streams(source, MediaStreamType.AUDIO).filterNot { it.hasTitle(AUDIO_TITLE) }
		return (others.firstOrNull { it.isEnglish() } ?: others.firstOrNull())?.index
	}

	@JvmStatic
	fun cleanSubtitleIndex(source: MediaSourceInfo?): Int? =
		streams(source, MediaStreamType.SUBTITLE).firstOrNull { it.hasTitle(SUBTITLE_TITLE) }?.index

	@JvmStatic
	fun isCleanSubtitle(source: MediaSourceInfo?, index: Int?): Boolean =
		index != null && index >= 0 && index == cleanSubtitleIndex(source)

	/** A normal English subtitle track (not forced, not the clean one), for when the filter is off. */
	@JvmStatic
	fun originalSubtitleIndex(source: MediaSourceInfo?): Int? {
		val others = streams(source, MediaStreamType.SUBTITLE).filterNot { it.hasTitle(SUBTITLE_TITLE) }
		return (others.firstOrNull { it.isEnglish() && !it.isForced } ?: others.firstOrNull { it.isEnglish() })?.index
	}

	/**
	 * The subtitle track to show given the filter setting, or [current] unchanged when no swap applies.
	 * Only swaps between the clean and the normal English track; subtitles that are off stay off.
	 */
	@JvmStatic
	fun preferredSubtitleIndex(source: MediaSourceInfo?, current: Int?, filterOn: Boolean): Int? {
		if (current == null || current < 0) return current
		val clean = cleanSubtitleIndex(source) ?: return current
		val currentStream = streams(source, MediaStreamType.SUBTITLE).firstOrNull { it.index == current } ?: return current
		return when {
			filterOn && current != clean && currentStream.isEnglish() && !currentStream.isForced -> clean
			!filterOn && current == clean -> originalSubtitleIndex(source) ?: current
			else -> current
		}
	}
}
