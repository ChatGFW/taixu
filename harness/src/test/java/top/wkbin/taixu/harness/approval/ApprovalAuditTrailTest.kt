package top.wkbin.taixu.harness.approval

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wkbin.taixu.core.database.AgentApprovalAuditDao
import top.wkbin.taixu.core.database.AgentApprovalAuditEntity
import top.wkbin.taixu.core.database.AgentApprovalAuditRepository
import top.wkbin.taixu.core.database.AgentApprovalRepository
import top.wkbin.taixu.core.database.AppDatabase
import top.wkbin.taixu.core.database.HarnessSessionEntity
import top.wkbin.taixu.core.database.RoomHarnessSessionRepository
import top.wkbin.taixu.core.model.ApprovalMode
import top.wkbin.taixu.core.network.FileDownloader
import top.wkbin.taixu.core.security.SecretRedactor
import top.wkbin.taixu.harness.ApprovalPolicyEngine
import top.wkbin.taixu.harness.AskUserToolBackend
import top.wkbin.taixu.harness.ContextMemoryToolBackend
import top.wkbin.taixu.harness.DownloadToolBackend
import top.wkbin.taixu.harness.HarnessPathResolver
import top.wkbin.taixu.harness.HarnessServiceToolBackend
import top.wkbin.taixu.harness.HarnessTool
import top.wkbin.taixu.harness.HostCapabilityToolBackend
import top.wkbin.taixu.harness.LinuxCommandToolBackend
import top.wkbin.taixu.harness.PromptAssetToolBackend
import top.wkbin.taixu.harness.ToolCall
import top.wkbin.taixu.harness.ToolExecutor
import top.wkbin.taixu.harness.WorkspaceFileAccess
import top.wkbin.taixu.harness.WorkspaceMutationSnapshots
import top.wkbin.taixu.harness.core.ToolCheckpoints
import top.wkbin.taixu.harness.directory.CapabilityToolGateway
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.harness.websearch.StubSearchProvider
import top.wkbin.taixu.harness.SearchWebToolBackend

/**
 * 审批审计台账（DSH 借鉴项）：会话授权豁免 / deferred 交接必须留下持久痕迹；
 * 审批仓库缺失（unavailable）必须 fail-closed 为失败结果，绝不静默放行。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApprovalAuditTrailTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var database: AppDatabase
    private lateinit var sessions: RoomHarnessSessionRepository
    private lateinit var approvals: AgentApprovalRepository
    private lateinit var audits: AgentApprovalAuditRepository
    private lateinit var grants: SessionApprovalGrants
    private lateinit var auditor: ApprovalAuditRecorder
    private lateinit var root: File
    private val call = ToolCall("call-write", 1, HarnessTool.WRITE,
        Json.parseToJsonElement("""{"path":"a.txt","content":"written"}""") as JsonObject, rawToolName = "write")

    @Before fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        root = temporary.newFolder("workspace")
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        sessions = RoomHarnessSessionRepository(database.harnessSessionDao())
        approvals = AgentApprovalRepository(database.agentApprovalDao())
        audits = AgentApprovalAuditRepository(database.agentApprovalAuditDao())
        grants = SessionApprovalGrants()
        auditor = ApprovalAuditRecorder(audits)
        seed()
    }

    @After fun tearDown() { database.close() }

    private suspend fun seed(mode: String = ApprovalMode.FULL_ACCESS.id) = sessions.upsert(
        HarnessSessionEntity("session", "test", 1, 1, null, approvalMode = mode, runMode = "build"),
    )

    private fun executor(approvalRepo: AgentApprovalRepository? = approvals): ToolExecutor {
        val paths = HarnessPathResolver()
        return ToolExecutor(
            WorkspaceFileAccess(root), paths, ApprovalPolicyEngine(paths), SecretRedactor(),
            hostToolBackend = HostCapabilityToolBackend(secretRedactor = SecretRedactor()),
            linuxCommandToolBackend = LinuxCommandToolBackend(unusedPort(), paths),
            downloadToolBackend = DownloadToolBackend(
                unusedPort<FileDownloader>(), WorkspaceFileAccess(root), WorkspaceMutationSnapshots(),
            ),
            contextMemoryToolBackend = ContextMemoryToolBackend(),
            askUserToolBackend = AskUserToolBackend(ApprovalPolicyEngine(paths), approvals),
            promptAssetToolBackend = PromptAssetToolBackend(),
            harnessServiceToolBackend = HarnessServiceToolBackend(),
            searchWebToolBackend = SearchWebToolBackend(top.wkbin.taixu.harness.websearch.WebSearchProviders(listOf(StubSearchProvider()))),
            capabilityToolGateway = CapabilityToolGateway(null) { _, _, _, _ -> error("unexpected host dispatch") },
            approvalRepository = approvalRepo, sessionDao = sessions,
            sessionApprovalGrants = grants,
            approvalAuditor = auditor,
        )
    }

    @Test
    fun `session grant exemption executes and leaves audit trail`() = runBlocking {
        seed(ApprovalMode.REQUEST.id)
        grants.grant("session", "write", call.args.toString(), "normal")
        val result = executor().execute(call, "session")
        assertTrue(result.success)
        assertTrue(File(root, "a.txt").exists())
        // 没有走审批请求，但豁免执行留下了持久审计
        assertTrue(approvals.pendingNow("session").isEmpty())
        val rows = audits.listForSession("session")
        assertEquals(1, rows.size)
        assertEquals(AgentApprovalAuditEntity.KIND_GRANT_EXEMPTED, rows.single().kind)
        assertEquals("write", rows.single().toolName)
        assertEquals(call.id, rows.single().toolCallId)
        assertNotNull(rows.single().grantKey)
        assertEquals(RunInitiator.PERSON, rows.single().initiatorKind)
    }

    @Test
    fun `deferred lane handoff leaves audit trail`() = runBlocking {
        seed(ApprovalMode.REQUEST.id)
        val result = executor().execute(call, "session", allowApprovalRequest = false)
        assertTrue(result.approvalDeferred)
        assertFalse(result.success)
        val rows = audits.listForSession("session")
        assertEquals(1, rows.size)
        assertEquals(AgentApprovalAuditEntity.KIND_DEFERRED, rows.single().kind)
        assertEquals(call.id, rows.single().toolCallId)
    }

    @Test
    fun `schedule initiator is stamped on grant exemption audit`() = runBlocking {
        seed(ApprovalMode.REQUEST.id)
        grants.grant("session", "write", call.args.toString(), "normal")
        kotlinx.coroutines.withContext(RunInitiatorContext(RunInitiator.schedule("cron-1"))) {
            executor().execute(call, "session")
        }
        val row = audits.listForSession("session").single()
        assertEquals(AgentApprovalAuditEntity.KIND_GRANT_EXEMPTED, row.kind)
        assertEquals(RunInitiator.SCHEDULE, row.initiatorKind)
        assertEquals("cron-1", row.initiatorId)
    }

    @Test
    fun `plan mode denial leaves policy_denied audit`() = runBlocking {
        sessions.upsert(HarnessSessionEntity("session", "test", 1, 1, null, approvalMode = ApprovalMode.FULL_ACCESS.id, runMode = "plan"))
        val result = executor().execute(call, "session")
        assertFalse(result.success)
        assertFalse(File(root, "a.txt").exists())
        val row = audits.listForSession("session").single()
        assertEquals(AgentApprovalAuditEntity.KIND_POLICY_DENIED, row.kind)
        assertEquals(call.id, row.toolCallId)
    }

    @Test
    fun `unattended computer failure leaves forward and failed dual audit`() = runBlocking {
        val edit = ToolCall(
            "call-edit", 2, HarnessTool.EDIT,
            Json.parseToJsonElement("""{"path":"missing.txt","oldText":"a","newText":"b"}""") as JsonObject,
            rawToolName = "edit",
        )
        kotlinx.coroutines.withContext(RunInitiatorContext(RunInitiator.schedule("cron-dual"))) {
            val result = executor().execute(edit, "session")
            assertFalse(result.success)
        }
        val rows = audits.listForSession("session").sortedBy { it.createdAt }
        assertEquals(
            listOf(
                AgentApprovalAuditEntity.KIND_DECISION_FORWARDED,
                AgentApprovalAuditEntity.KIND_ACTION_FAILED,
            ),
            rows.map { it.kind },
        )
        assertTrue(rows.all { it.initiatorKind == RunInitiator.SCHEDULE && it.initiatorId == "cron-dual" })
    }

    @Test
    fun `missing approval repository fails closed instead of executing`() = runBlocking {
        seed(ApprovalMode.REQUEST.id)
        val result = executor(approvalRepo = null).execute(call, "session")
        assertFalse(result.success)
        assertFalse(File(root, "a.txt").exists())
    }

    @Test
    fun `control taken and released land in browser-control session`() = runBlocking {
        auditor.controlTaken()
        auditor.controlReleased()
        val rows = audits.listForSession(AgentApprovalAuditEntity.SESSION_BROWSER_CONTROL)
            .sortedBy { it.createdAt }
        assertEquals(
            listOf(
                AgentApprovalAuditEntity.KIND_CONTROL_TAKEN,
                AgentApprovalAuditEntity.KIND_CONTROL_RELEASED,
            ),
            rows.map { it.kind },
        )
        assertTrue(rows.all { it.toolName == AgentApprovalAuditEntity.TOOL_BROWSER_CONTROL })
        assertTrue(rows.all { it.initiatorKind == RunInitiator.PERSON })
    }

    @Test
    fun `help requested keeps refused tool name`() = runBlocking {
        auditor.helpRequested("browser.click", "用户正在接管")
        val row = audits.listForSession(AgentApprovalAuditEntity.SESSION_BROWSER_CONTROL).single()
        assertEquals(AgentApprovalAuditEntity.KIND_HELP_REQUESTED, row.kind)
        assertEquals("browser.click", row.toolName)
        assertTrue(row.detail.orEmpty().contains("接管"))
    }

    @Test
    fun `grant written records key and audit cleans with session`() = runBlocking {
        auditor.grantWritten(
            sessionId = "session", toolName = "write",
            argumentsJson = call.args.toString(), riskLevel = "normal", approvalRequestId = "req_1",
        )
        val rows = audits.listForSession("session")
        assertEquals(1, rows.size)
        assertEquals(AgentApprovalAuditEntity.KIND_GRANT_WRITTEN, rows.single().kind)
        assertEquals("req_1", rows.single().approvalRequestId)
        assertNotNull(rows.single().grantKey)
        auditor.deleteForSession("session")
        assertTrue(audits.listForSession("session").isEmpty())
    }

    @Test
    fun `audit insert failure never blocks the tool call`() = runBlocking {
        seed(ApprovalMode.REQUEST.id)
        grants.grant("session", "write", call.args.toString(), "normal")
        val broken = ApprovalAuditRecorder(AgentApprovalAuditRepository(FailingAuditDao))
        val paths = HarnessPathResolver()
        val executor = ToolExecutor(
            WorkspaceFileAccess(root), paths, ApprovalPolicyEngine(paths), SecretRedactor(),
            hostToolBackend = HostCapabilityToolBackend(secretRedactor = SecretRedactor()),
            linuxCommandToolBackend = LinuxCommandToolBackend(unusedPort(), paths),
            downloadToolBackend = DownloadToolBackend(
                unusedPort<FileDownloader>(), WorkspaceFileAccess(root), WorkspaceMutationSnapshots(),
            ),
            contextMemoryToolBackend = ContextMemoryToolBackend(),
            askUserToolBackend = AskUserToolBackend(ApprovalPolicyEngine(paths), approvals),
            promptAssetToolBackend = PromptAssetToolBackend(),
            harnessServiceToolBackend = HarnessServiceToolBackend(),
            searchWebToolBackend = SearchWebToolBackend(top.wkbin.taixu.harness.websearch.WebSearchProviders(listOf(StubSearchProvider()))),
            capabilityToolGateway = CapabilityToolGateway(null) { _, _, _, _ -> error("unexpected host dispatch") },
            approvalRepository = approvals, sessionDao = sessions,
            sessionApprovalGrants = grants,
            approvalAuditor = broken,
        )
        val result = executor.execute(call, "session")
        assertTrue(result.success)
        assertTrue(File(root, "a.txt").exists())
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : Any> unusedPort(): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
    ) { _, _, _ -> error("unexpected backend use") } as T

    private object FailingAuditDao : AgentApprovalAuditDao {
        override suspend fun insert(entry: AgentApprovalAuditEntity) = error("audit storage down")
        override fun observeForSession(sessionId: String) = kotlinx.coroutines.flow.MutableStateFlow(emptyList<AgentApprovalAuditEntity>())
        override suspend fun listForSession(sessionId: String, limit: Int) = emptyList<AgentApprovalAuditEntity>()
        override suspend fun countForSession(sessionId: String) = 0
        override fun observeUnattendedRecent(limit: Int) = kotlinx.coroutines.flow.MutableStateFlow(emptyList<AgentApprovalAuditEntity>())
        override fun observeControlRecent(limit: Int) = kotlinx.coroutines.flow.MutableStateFlow(emptyList<AgentApprovalAuditEntity>())
        override suspend fun deleteForSession(sessionId: String) = Unit
    }
}
