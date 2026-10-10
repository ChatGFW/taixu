package top.wkbin.taixu.harness

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.core.database.AppDatabase
import top.wkbin.taixu.core.database.RoomAiModelRepository
import top.wkbin.taixu.core.datastore.AgentPreferences
import top.wkbin.taixu.core.datastore.ProviderPreferences
import top.wkbin.taixu.core.datastore.SettingsDataStore
import top.wkbin.taixu.core.security.SecretManager
import top.wkbin.taixu.core.tools.ProviderRepository
import top.wkbin.taixu.harness.core.LlmApi

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProviderModelResolverTest {
    private lateinit var database: AppDatabase
    private lateinit var models: RoomAiModelRepository
    private lateinit var preferences: AgentPreferences
    private lateinit var resolver: ProviderModelResolver
    private val profile = AiModelEntity(
        id = "profile", name = "gateway", provider = "custom", model = "gpt-4o-mini,gpt-4.1-mini",
        baseUrl = "https://gateway.example/v1", isActive = true, createdAt = 1,
    )

    @Before fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        models = RoomAiModelRepository(database.aiModelDao())
        val settings = SettingsDataStore(context, SecretManager())
        preferences = AgentPreferences(settings)
        preferences.setDefaultReasoningDepth("auto")
        resolver = ProviderModelResolver(ProviderRepository(ProviderPreferences(settings)), models, preferences)
    }

    @After fun tearDown() { database.close() }

    @Test fun `requested variant is resolved before inferred context capacity`() = runBlocking {
        models.upsert(profile)
        val resolved = resolver.resolveRequestedModel("GPT-4.1-MINI")
        assertEquals("gpt-4.1-mini", resolved.model)
        assertEquals(ModelContextWindows.resolve("gpt-4.1-mini", "custom"), resolved.contextTokens)
        assertNotNull(resolved.contextTokens)
    }

    @Test fun `explicit limits and protocol flags survive profile resolution`() = runBlocking {
        models.upsert(profile.copy(
            provider = "Claude", responseApiEnabled = true, contextTokens = 12345, maxTokens = 2345,
            toolCallMode = "json", visionEnabled = false, compactionKeepRecentTokens = 3456,
        ))
        val resolved = resolver.resolveConfigured()
        assertEquals(LlmApi.OPENAI_RESPONSES, resolved.api)
        assertEquals(12345, resolved.descriptor.capabilities.contextWindow)
        assertEquals(2345, resolved.descriptor.capabilities.maxOutputTokens)
        assertEquals(3456, resolved.compactionKeepRecentTokens)
        assertTrue(resolved.capabilities.textTools)
        assertFalse(resolved.capabilities.images)
        assertFalse(resolved.capabilities.promptCaching)
    }

    @Test fun `global reasoning disable is ignored when selected serializer cannot encode it`() = runBlocking {
        preferences.setDefaultReasoningDepth("disabled")
        models.upsert(profile.copy(responseApiEnabled = true))
        assertEquals(ReasoningMode.AUTO, resolver.resolveConfigured().reasoningMode)
    }

    @Test fun `global reasoning depth uses messages budget on custom anthropic gateway`() = runBlocking {
        preferences.setDefaultReasoningDepth("high")
        models.upsert(profile.copy(provider = "Claude"))
        val resolved = resolver.resolveConfigured()
        assertEquals(ReasoningMode.ENABLED, resolved.reasoningMode)
        assertEquals(ReasoningEffort.HIGH, resolved.reasoningEffort)
        assertNotNull(ReasoningAdapter.anthropicThinking(resolved))
    }

    @Test fun `explicit reasoning disable takes priority over global depth`() = runBlocking {
        preferences.setDefaultReasoningDepth("high")
        models.upsert(profile.copy(reasoningMode = "disabled"))
        assertEquals(ReasoningMode.DISABLED, resolver.resolveConfigured().reasoningMode)
    }

    @Test fun `missing saved profile or removed variant never silently uses active model`() = runBlocking {
        models.upsert(profile)
        assertTrue(runCatching { resolver.resolveSavedModelProfile("missing", null) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { resolver.resolveSavedModelProfile(profile.id, "removed-model") }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { resolver.resolveRequestedModel("removed-model") }.exceptionOrNull() is IllegalArgumentException)
    }
}
