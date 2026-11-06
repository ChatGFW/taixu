package top.wkbin.taixu.core.document

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Only file descriptors cross the boundary. No host paths, URIs, provider settings or model keys. */
internal class PdfDocumentClient(private val context: Context,
    private val supported: () -> Boolean = { Build.VERSION.SDK_INT >= 35 },
    private val limits: ExtractionLimits = ExtractionLimits()) {
    suspend fun extract(file: File): DocumentExtraction {
        if (!supported()) return unavailable(DocumentExtraction.Reason.PDF_REQUIRES_ANDROID_15)
        return try {
            validateInput(file, limits)
            withTimeoutOrNull(limits.timeoutMs) { gate.withLock { request(file) } }
                ?: unavailable(DocumentExtraction.Reason.TIMEOUT)
        } catch (e: CancellationException) { throw e }
        catch (e: ExtractionStop) { unavailable(e.reason) }
        catch (_: Exception) { unavailable(DocumentExtraction.Reason.IO) }
    }

    private suspend fun request(file: File): DocumentExtraction {
        val connected = CompletableDeferred<Messenger>()
        val result = CompletableDeferred<DocumentExtraction>()
        val output = File.createTempFile("document-pdf-", ".txt", context.cacheDir)
        val thread = HandlerThread("DocumentResult").apply { start() }
        val reply = Messenger(Handler(thread.looper) { message ->
            if (message.what == PdfTextService.RESULT && result.isActive) {
                result.complete(readPdfResult(output, message.data.getString("status"),
                    message.data.getBoolean("truncated"), limits))
            }
            true
        })
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) { connected.complete(Messenger(binder)) }
            override fun onServiceDisconnected(name: ComponentName) = fail()
            override fun onBindingDied(name: ComponentName) = fail()
            override fun onNullBinding(name: ComponentName) = fail()
            private fun fail() {
                connected.completeExceptionally(IllegalStateException("Document worker unavailable"))
                result.complete(unavailable(DocumentExtraction.Reason.IO))
            }
        }
        var bound = false
        try {
            bound = context.bindIsolatedService(Intent(context, PdfTextService::class.java),
                Context.BIND_AUTO_CREATE or Context.BIND_NOT_FOREGROUND,
                "document_" + UUID.randomUUID().toString().replace("-", ""), context.mainExecutor, connection)
            if (!bound) return unavailable(DocumentExtraction.Reason.IO)
            val remote = connected.await()
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { source ->
                ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE).use { target ->
                    remote.send(Message.obtain().apply {
                        what = PdfTextService.EXTRACT
                        replyTo = reply
                        data = Bundle().apply { putParcelable("source", source); putParcelable("target", target) }
                    })
                }
            }
            return result.await()
        } finally {
            connected.cancel(); result.cancel()
            if (bound) runCatching { context.unbindService(connection) }
            thread.quitSafely()
            output.delete()
        }
    }

    companion object {
        private val gate = Mutex()
        private fun unavailable(reason: DocumentExtraction.Reason) = DocumentExtraction.Unavailable(reason)
    }
}
