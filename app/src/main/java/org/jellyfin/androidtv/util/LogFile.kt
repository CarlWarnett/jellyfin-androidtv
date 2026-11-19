package org.jellyfin.androidtv.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import org.jellyfin.androidtv.BuildConfig
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Keeps the app log in a file (so it survives restarts) and shares it with another app,
 * e.g. LocalSend, from Settings > Send log.
 */
object LogFile {
	private const val MAX_BYTES = 1_000_000L
	private val writer = Executors.newSingleThreadExecutor()
	private val lineTime = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

	private fun dir(context: Context) = File(context.filesDir, "logs").apply { mkdirs() }
	private fun current(context: Context) = File(dir(context), "app.log")
	private fun previous(context: Context) = File(dir(context), "app.1.log")

	fun tree(context: Context): Timber.Tree = object : Timber.DebugTree() {
		private val appContext = context.applicationContext

		override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
			val level = when (priority) {
				Log.VERBOSE -> 'V'
				Log.DEBUG -> 'D'
				Log.INFO -> 'I'
				Log.WARN -> 'W'
				Log.ERROR -> 'E'
				else -> 'A'
			}
			val line = buildString {
				append(lineTime.format(Date())).append(' ').append(level).append(' ').append(tag ?: "-").append(": ").append(message)
				if (t != null) append('\n').append(Log.getStackTraceString(t))
				append('\n')
			}
			writer.execute {
				try {
					val file = current(appContext)
					if (file.length() > MAX_BYTES) file.renameTo(previous(appContext).apply { delete() })
					file.appendText(line)
				} catch (_: Exception) {
					// Never let logging break the app
				}
			}
		}
	}

	/** Writes the log file plus this process's logcat (ExoPlayer etc.) to one file and opens the share sheet. */
	fun share(context: Context) {
		val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
		val outDir = File(context.cacheDir, "shared").apply { mkdirs() }
		outDir.listFiles()?.forEach { it.delete() }
		val out = File(outDir, "jellyfin-clean-log-$stamp.txt")

		writer.submit {
			out.bufferedWriter().use { w ->
				w.appendLine("Jellyfin Clean ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
				w.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
				w.appendLine("Created: $stamp")
				w.appendLine()
				w.appendLine("==== App log ====")
				listOf(previous(context), current(context)).filter { it.exists() }.forEach { w.write(it.readText()) }
				w.appendLine()
				w.appendLine("==== logcat (this app) ====")
				try {
					val proc = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", "5000", "--pid=${Process.myPid()}")
						.redirectErrorStream(true)
						.start()
					proc.inputStream.bufferedReader().use { r -> r.copyTo(w) }
				} catch (e: Exception) {
					w.appendLine("Could not read logcat: $e")
				}
			}
		}.get()

		val uri = FileProvider.getUriForFile(context, "${context.packageName}.logs", out)
		val send = Intent(Intent.ACTION_SEND).apply {
			type = "text/plain"
			putExtra(Intent.EXTRA_STREAM, uri)
			putExtra(Intent.EXTRA_SUBJECT, out.name)
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		}
		try {
			context.startActivity(Intent.createChooser(send, out.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
		} catch (e: Exception) {
			Timber.e(e, "No app to share the log with")
			Toast.makeText(context, "No app found to send the log. Install LocalSend.", Toast.LENGTH_LONG).show()
		}
	}
}
