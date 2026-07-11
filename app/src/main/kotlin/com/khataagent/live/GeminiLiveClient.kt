package com.khataagent.live

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Online-only real-time voice bridge to the Gemini Live API (BidiGenerateContent over WebSocket).
 * Mirrors the exact message shapes from the LIVE build task:
 *  - client -> server: one `setup` message, then a stream of `realtimeInput.mediaChunks`
 *    (base64 16kHz mono PCM) while the shopkeeper is talking.
 *  - server -> client: `setupComplete`, then `serverContent.modelTurn.parts[].inlineData`
 *    (base64 24kHz mono PCM) which is decoded and played live, plus `turnComplete` /
 *    `interrupted` flags.
 *
 * This class owns the mic (AudioRecord, 16kHz) and the speaker (AudioTrack, 24kHz) directly so
 * the UI layer only has to react to [status]. Nothing here touches the local ledger loop.
 */
class GeminiLiveClient(
    private val apiKey: String,
    private val primaryModel: String = "models/gemini-2.5-flash-native-audio-latest",
    private val fallbackModel: String = "models/gemini-3.1-flash-live-preview",
) {

    sealed class LiveStatus {
        data object Idle : LiveStatus()
        data object Connecting : LiveStatus()
        /** Connected and actively streaming mic audio up to Gemini. */
        data object Listening : LiveStatus()
        /** Gemini's spoken reply is playing back. */
        data object Speaking : LiveStatus()
        data class Error(val message: String) : LiveStatus()
    }

    private companion object {
        const val RECORD_SAMPLE_RATE = 16_000
        const val PLAYBACK_SAMPLE_RATE = 24_000
        const val WS_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    }

    private val _status = MutableStateFlow<LiveStatus>(LiveStatus.Idle)
    val status: StateFlow<LiveStatus> = _status.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // long-lived socket, no read timeout
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var systemInstruction: String = ""
    private var usedFallback = false
    private var setupAcked = false

    // ---- mic capture ----
    private var audioRecord: AudioRecord? = null
    private var recordThread: Thread? = null
    @Volatile private var recording = false

    // ---- speaker playback ----
    private var audioTrack: AudioTrack? = null
    private var playbackThread: Thread? = null
    @Volatile private var playbackRunning = false
    private val playbackQueue = LinkedBlockingQueue<ByteArray>()

    /** Opens the socket and sends the one-time `setup` message with the ledger-aware system prompt. */
    fun connect(systemInstruction: String) {
        this.systemInstruction = systemInstruction
        usedFallback = false
        setupAcked = false
        startPlaybackThreadIfNeeded()
        openSocket(primaryModel)
    }

    private fun openSocket(model: String) {
        _status.value = LiveStatus.Connecting
        val request = Request.Builder().url("$WS_URL?key=$apiKey").build()
        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                ws.send(buildSetupMessage(model))
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                if (!usedFallback && model == primaryModel) {
                    // First model errored (e.g. not available on this key/region) — retry once
                    // with the older fallback model before surfacing an error to the user.
                    usedFallback = true
                    openSocket(fallbackModel)
                } else {
                    _status.value = LiveStatus.Error(t.message ?: "Couldn't connect to Gemini Live")
                }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (_status.value !is LiveStatus.Error) _status.value = LiveStatus.Idle
            }
        })
    }

    private fun buildSetupMessage(model: String): String {
        val setup = JSONObject().apply {
            put(
                "setup",
                JSONObject().apply {
                    put("model", model)
                    put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("AUDIO")))
                    put(
                        "systemInstruction",
                        JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction))),
                    )
                },
            )
        }
        return setup.toString()
    }

    private fun handleServerMessage(text: String) {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return

        if (json.has("setupComplete")) {
            setupAcked = true
            if (_status.value == LiveStatus.Connecting) _status.value = LiveStatus.Idle
            return
        }

        json.optJSONObject("serverContent")?.let { serverContent ->
            if (serverContent.optBoolean("interrupted", false)) {
                // The shopkeeper started talking again — drop whatever's still queued to play.
                playbackQueue.clear()
            }
            val parts = serverContent.optJSONObject("modelTurn")?.optJSONArray("parts")
            var queuedAudio = false
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val inlineData = parts.optJSONObject(i)?.optJSONObject("inlineData") ?: continue
                    val data = inlineData.optString("data", "")
                    if (data.isNotEmpty()) {
                        playbackQueue.put(Base64.getDecoder().decode(data))
                        queuedAudio = true
                    }
                }
            }
            if (queuedAudio) _status.value = LiveStatus.Speaking
            if (serverContent.optBoolean("turnComplete", false) && !queuedAudio) {
                _status.value = if (recording) LiveStatus.Listening else LiveStatus.Idle
            }
        }

        json.optJSONObject("error")?.let { error ->
            _status.value = LiveStatus.Error(error.optString("message", "Live chat error"))
        }
    }

    /** Sends one chunk of mic audio (16kHz mono PCM16) up to Gemini as `realtimeInput`. */
    fun sendAudio(pcm: ShortArray) {
        val ws = webSocket ?: return
        val bytes = ByteArray(pcm.size * 2)
        var o = 0
        for (s in pcm) {
            val v = s.toInt()
            bytes[o++] = (v and 0xFF).toByte()
            bytes[o++] = ((v shr 8) and 0xFF).toByte()
        }
        val message = JSONObject().apply {
            put(
                "realtimeInput",
                JSONObject().put(
                    "mediaChunks",
                    JSONArray().put(
                        JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=$RECORD_SAMPLE_RATE")
                            put("data", Base64.getEncoder().encodeToString(bytes))
                        },
                    ),
                ),
            )
        }
        ws.send(message.toString())
    }

    /** Starts capturing the mic and streaming it up while the shopkeeper holds/taps to talk. */
    @SuppressLint("MissingPermission")
    fun startTalking() {
        if (recording) return
        val minBuf = AudioRecord.getMinBufferSize(
            RECORD_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(RECORD_SAMPLE_RATE / 5)
        val record = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                RECORD_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf,
            )
        }.getOrNull()?.takeIf { it.state == AudioRecord.STATE_INITIALIZED }
            ?: AudioRecord(
                MediaRecorder.AudioSource.MIC,
                RECORD_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf,
            )
        audioRecord = record
        recording = true
        _status.value = LiveStatus.Listening
        record.startRecording()
        recordThread = Thread {
            val buf = ShortArray(RECORD_SAMPLE_RATE / 20) // ~50ms chunks
            while (recording) {
                val n = record.read(buf, 0, buf.size)
                if (n > 0) sendAudio(buf.copyOf(n))
            }
        }.also { it.start() }
    }

    /** Stops mic capture. The connection stays open so the shopkeeper can talk again. */
    fun stopTalking() {
        if (!recording) return
        recording = false
        recordThread?.join(400)
        recordThread = null
        audioRecord?.let { r ->
            runCatching { if (r.recordingState == AudioRecord.RECORDSTATE_RECORDING) r.stop() }
            runCatching { r.release() }
        }
        audioRecord = null
        if (_status.value == LiveStatus.Listening) _status.value = LiveStatus.Idle
    }

    private fun startPlaybackThreadIfNeeded() {
        if (playbackRunning) return
        playbackRunning = true
        val minBuf = AudioTrack.getMinBufferSize(
            PLAYBACK_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(PLAYBACK_SAMPLE_RATE / 2)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(PLAYBACK_SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuf * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()
        audioTrack = track
        playbackThread = Thread {
            while (playbackRunning) {
                val chunk = runCatching { playbackQueue.poll(200, TimeUnit.MILLISECONDS) }.getOrNull()
                if (chunk != null) {
                    runCatching { track.write(chunk, 0, chunk.size) }
                } else if (playbackQueue.isEmpty() && _status.value == LiveStatus.Speaking) {
                    // Ran dry — go back to listening (still connected) rather than sit on "Speaking".
                    _status.value = if (recording) LiveStatus.Listening else LiveStatus.Idle
                }
            }
        }.also { it.start() }
    }

    private fun stopPlaybackThread() {
        playbackRunning = false
        playbackThread?.join(400)
        playbackThread = null
        playbackQueue.clear()
        audioTrack?.let { t ->
            runCatching { t.stop() }
            runCatching { t.release() }
        }
        audioTrack = null
    }

    /** Tears down the mic, speaker, and socket. Safe to call multiple times. */
    fun close() {
        stopTalking()
        stopPlaybackThread()
        webSocket?.close(1000, "done")
        webSocket = null
        setupAcked = false
        _status.value = LiveStatus.Idle
    }
}
