package top.wkbin.taixu.core.document

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.Process

/** Isolated, bound-only worker. Its only file access is the two descriptors supplied by the host. */
class PdfTextService : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var messenger: Messenger
    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("PdfText").apply { start() }
        messenger = Messenger(Handler(thread.looper) { message ->
            if (message.what == EXTRACT) extract(message)
            true
        })
    }
    override fun onBind(intent: Intent): IBinder = messenger.binder
    @SuppressLint("NewApi")
    private fun extract(message: Message) {
        val source = message.data.getParcelable("source", ParcelFileDescriptor::class.java)
        val target = message.data.getParcelable("target", ParcelFileDescriptor::class.java)
        val result = try {
            requireNotNull(source); requireNotNull(target)
            source.use {
                target.use {
                    val limits = ExtractionLimits()
                    if (source.statSize <= 0) throw ExtractionStop(DocumentExtraction.Reason.INVALID)
                    if (source.statSize > limits.maxInputBytes) throw ExtractionStop(DocumentExtraction.Reason.TOO_LARGE)
                    if (Build.VERSION.SDK_INT < 35) throw ExtractionStop(DocumentExtraction.Reason.PDF_REQUIRES_ANDROID_15)
                    val extracted = PdfRenderer(source).use { pdf ->
                        collectPdfText(pdf.pageCount, limits) { index ->
                            pdf.openPage(index).use { page -> page.textContents.map { it.text } }
                        }
                    }
                    if (extracted is DocumentExtraction.Text) {
                        ParcelFileDescriptor.AutoCloseOutputStream(target).bufferedWriter(Charsets.UTF_8).use { it.write(extracted.content) }
                    }
                    extracted
                }
            }
        } catch (e: ExtractionStop) { DocumentExtraction.Unavailable(e.reason) }
        catch (_: SecurityException) { DocumentExtraction.Unavailable(DocumentExtraction.Reason.PROTECTED) }
        catch (_: Exception) { DocumentExtraction.Unavailable(DocumentExtraction.Reason.INVALID) }
        finally { runCatching { source?.close() }; runCatching { target?.close() } }
        runCatching {
            message.replyTo?.send(Message.obtain().apply {
                what = RESULT
                data = Bundle().apply {
                    putString("status", if (result is DocumentExtraction.Text) "text" else (result as DocumentExtraction.Unavailable).reason.name)
                    putBoolean("truncated", (result as? DocumentExtraction.Text)?.truncated == true)
                }
            })
        }
    }
    override fun onDestroy() {
        thread.quitSafely()
        super.onDestroy()
        // Unbinding after timeout must also terminate a non-cooperative native parser.
        // The manifest dedicates an isolated process to this service; never kill the host process.
        if (Process.isIsolated()) Process.killProcess(Process.myPid())
    }
    companion object {
        internal const val EXTRACT = 1
        internal const val RESULT = 2
    }
}
