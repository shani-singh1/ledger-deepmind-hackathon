package com.khataagent.di

import android.content.Context
import com.khataagent.BuildConfig
import com.khataagent.agent.AgentOrchestrator
import com.khataagent.agent.LiteRtInferenceEngine
import com.khataagent.agent.StubInferenceEngine
import com.khataagent.audio.AudioRecorder
import com.khataagent.core.agent.InferenceBackend
import com.khataagent.data.DemoSeed
import com.khataagent.data.RoomLedgerRepository
import com.khataagent.data.provideDatabase
import com.khataagent.data.provideStateBlockBuilder
import com.khataagent.connectivity.DemoConnectivityMonitor
import com.khataagent.core.agent.InferenceEngine
import com.khataagent.engine.ResilientInferenceEngine
import com.khataagent.engine.RoutingInferenceEngine
import com.khataagent.escalate.GeminiEscalationClient
import com.khataagent.escalate.GeminiInferenceEngine
import com.khataagent.fake.FakeEscalationClient
import com.khataagent.status.AppStatusController
import com.khataagent.core.escalate.EscalationClient
import com.khataagent.validate.KhataValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Phase-2 manual DI. The LOCAL LOOP is fully real: Room repository (seeded), the KhataValidator
 * "check step", the state-block builder, and the LiteRT-LM Gemma engine (stub fallback) behind
 * the AgentOrchestrator. Connectivity stays on the toggleable [FakeConnectivityMonitor] for
 * reliable stage control (flip online/offline without touching the device radio); escalation
 * uses the real [GeminiEscalationClient] gated by that same monitor when a Gemini API key is
 * configured (see `gemini.key` / `GEMINI_API_KEY` in app/build.gradle.kts), falling back to the
 * polished [FakeEscalationClient] otherwise so the Insights demo never depends on a key being
 * present at the venue. The whole local pipeline is unaffected by any of that — it never touches
 * the network.
 */
class AppContainer(context: Context, scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val db = provideDatabase(appContext)

    val repository = RoomLedgerRepository(
        customerDao = db.customerDao(),
        transactionDao = db.transactionDao(),
        inventoryDao = db.inventoryDao(),
        deferralDao = db.deferralDao(),
    )
    private val stateBlockBuilder = provideStateBlockBuilder(repository)
    private val validator = KhataValidator()

    // Android 13+ scoped storage blocks reading /sdcard/Download directly, so the model lives in
    // this app's own external files dir (no permission needed). Push it with:
    //   adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.khataagent/files/
    private val modelFile = java.io.File(
        appContext.getExternalFilesDir(null),
        "gemma-4-E2B-it.litertlm",
    )
    private val liteRt = LiteRtInferenceEngine(appContext, modelFile.absolutePath)
    private val localEngine = ResilientInferenceEngine(primary = liteRt, fallback = StubInferenceEngine())

    // Live internet state -> automatic online/offline switching (manual override for stage control).
    val connectivity = DemoConnectivityMonitor(appContext)

    private val geminiKey = BuildConfig.GEMINI_API_KEY
    private val cloudEngine: InferenceEngine? =
        if (geminiKey.isNotBlank()) GeminiInferenceEngine(apiKey = geminiKey) else null

    // ONLINE -> Gemini (cloud); OFFLINE -> on-device Gemma. Decided per turn from live connectivity;
    // the cloud path falls back to local on any failure so a turn never dies on a flaky network.
    private val engine = RoutingInferenceEngine(local = localEngine, cloud = cloudEngine, connectivity = connectivity)

    val orchestrator = AgentOrchestrator(
        engine = engine,
        validator = validator,
        repository = repository,
        stateBlockBuilder = stateBlockBuilder,
    )

    val audioRecorder = AudioRecorder()

    // Escalation: real Gemini when a key is set (gated by the same connectivity), else the polished
    // fake so Insights still renders keylessly.
    val escalationClient: EscalationClient =
        if (geminiKey.isNotBlank()) {
            GeminiEscalationClient(connectivityMonitor = connectivity, apiKey = geminiKey)
        } else {
            FakeEscalationClient(connectivity)
        }
    val statusController = AppStatusController(connectivity, scope)

    private val _voiceAvailable = MutableStateFlow(false)
    val voiceAvailable: () -> Boolean = { _voiceAvailable.value }

    /** Human-readable brain label for the UI hint (cloud / on-device GPU / CPU). */
    private val _backendLabel = MutableStateFlow("starting…")
    val backendLabel: () -> String = { _backendLabel.value }

    init {
        scope.launch {
            runCatching { DemoSeed.seedIfEmpty(db) }
            runCatching { engine.warmUp() }
            _backendLabel.value = when {
                cloudEngine != null && connectivity.isOnline.value -> "cloud · Gemini"
                localEngine.backend == InferenceBackend.GPU -> "on-device · GPU"
                localEngine.backend == InferenceBackend.CPU -> "on-device · CPU"
                else -> "on-device"
            }
        }
    }
}
