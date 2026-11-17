package top.wkbin.taixu.runtime.webchat

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.lang.reflect.Proxy
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.shell.LinuxSession
import top.wkbin.taixu.runtime.shell.TerminalOutput

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WebTerminalBridgeTest {
    @Test fun concurrentCreationNeverExceedsThePtyLimit() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val server = AndroidHttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 32)
        val serverExecutor = Executors.newFixedThreadPool(24)
        val clientExecutor = Executors.newFixedThreadPool(20)
        val client = OkHttpClient.Builder()
            .dispatcher(Dispatcher(clientExecutor).apply {
                maxRequests = 32
                maxRequestsPerHost = 32
            })
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        val started = AtomicInteger()
        val firstStarted = CountDownLatch(1)
        val releaseStarts = CountDownLatch(1)
        val runtime = Proxy.newProxyInstance(
            LinuxRuntime::class.java.classLoader,
            arrayOf(LinuxRuntime::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getActiveDistroId" -> MutableStateFlow("ubuntu")
                "startSession" -> {
                    started.incrementAndGet()
                    firstStarted.countDown()
                    check(releaseStarts.await(10, TimeUnit.SECONDS)) { "测试未放行 PTY 创建" }
                    TestSession()
                }
                else -> error("Unexpected runtime call ${method.name}")
            }
        } as LinuxRuntime
        val logger = AppLogger(ApplicationProvider.getApplicationContext<Application>()) { it }
        val auth = WebChatAuth()
        val bridge = WebTerminalBridge(runtime, logger, scope, auth)
        try {
            auth.rotate("123456")
            val token = (auth.pair("test", "123456") as PairOutcome.Paired).sessionToken
            bridge.registerRoutes(server)
            server.executor = serverExecutor
            server.start()
            val socketField = AndroidHttpServer::class.java.getDeclaredField("serverSocket").apply {
                isAccessible = true
            }
            val port = (socketField.get(server) as ServerSocket).localPort
            val request = Request.Builder()
                .url("http://127.0.0.1:$port/webchat/api/terminal")
                .header("Authorization", "Bearer $token")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            val responses = (1..17).map {
                async(Dispatchers.IO) { client.newCall(request).execute().use { response -> response.code } }
            }
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS))
            val contentionDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
            while (started.get() < 17 && System.nanoTime() < contentionDeadline) delay(10)
            releaseStarts.countDown()
            val codes = withTimeout(20_000) { responses.awaitAll() }
            assertEquals(16, codes.count { it == 200 })
            assertEquals(1, codes.count { it == 429 })
            assertEquals(16, started.get())
        } finally {
            releaseStarts.countDown()
            bridge.shutdown()
            scope.cancel()
            server.stop(0)
            serverExecutor.shutdownNow()
            client.dispatcher.executorService.shutdownNow()
        }
    }

    private class TestSession : LinuxSession {
        override val isAlive = true
        override val output = MutableSharedFlow<TerminalOutput>()
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun resize(columns: Int, rows: Int) = Unit
        override suspend fun interrupt() = Unit
        override suspend fun close() = Unit
    }
}
