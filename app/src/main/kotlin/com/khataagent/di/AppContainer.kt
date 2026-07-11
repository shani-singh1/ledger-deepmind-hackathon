package com.khataagent.di

import android.content.Context
import com.khataagent.agent.AgentOrchestrator
import com.khataagent.agent.LiteRtInferenceEngine
import com.khataagent.agent.StubInferenceEngine
import com.khataagent.audio.AudioRecorder
import com.khataagent.core.agent.InferenceBackend
import com.khataagent.data.DemoSeed
import com.khataagent.data.RoomLedgerRepository
import com.khataagent.data.provideDatabase
import com.khataagent.data.provideStateBlockBuilder
import com.khataagent.engine.ResilientInferenceEngine
import com.khataagent.fake.FakeConnectivityMonitor
import com.khataagent.fake.FakeEscalationClient
import com.khataagent.status.AppStatusController
import com.khataagent.validate.KhataValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Phase-2 manual DI. The LOCAL LOOP is fully real: Room repository (seeded), the KhataValidator
 * "check step", the state-block builder, and the LiteRT-LM Gemma engine (stub fallback) behind
 * the AgentOrchestrator. Connectivity + escalation stay on the polished fakes (no Gemini key at
 * the venue), gated by a manual online/offline toggle for reliable stage control of the Insights
 * demo. The whole local pipeline is unaffected by any of that — it never touches the network.
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
    private val engine = ResilientInferenceEngine(primary = liteRt, fallback = StubInferenceEngine())

    val orchestrator = AgentOrchestrator(
        engine = engine,
        validator = validator,
        repository = repository,
        stateBlockBuilder = stateBlockBuilder,
    )

    val audioRecorder = AudioRecorder()

    // Escalation / connectivity: kept on fakes (P2 polish, no API key). Manual toggle drives them.
    val connectivity = FakeConnectivityMonitor(initiallyOnline = true)
    val escalationClient = FakeEscalationClient(connectivity)
    val statusController = AppStatusController(connectivity, scope)

    private val _voiceAvailable = MutableStateFlow(false)
    val voiceAvailable: () -> Boolean = { _voiceAvailable.value }

    /** Human-readable backend for the UI hint ("on-device GPU" / "CPU" / "demo mode"). */
    private val _backendLabel = MutableStateFlow("starting…")
    val backendLabel: () -> String = { _backendLabel.value }

    init {
        scope.launch {
            runCatching { DemoSeed.seedIfEmpty(db) }
            runCatching { engine.warmUp() }
            _voiceAvailable.value = engine.usingRealEngine && liteRt.audioAvailable
            _backendLabel.value = when (engine.backend) {
                InferenceBackend.GPU -> "on-device · GPU"
                InferenceBackend.CPU -> "on-device · CPU"
                InferenceBackend.STUB -> "demo mode (model not loaded)"
            }
        }
    }
}
