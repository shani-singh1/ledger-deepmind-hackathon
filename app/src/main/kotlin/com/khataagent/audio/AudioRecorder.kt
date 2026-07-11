package com.khataagent.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * Captures 16 kHz mono 16-bit PCM from the mic into a [ShortArray] — the exact format Gemma's
 * audio tower expects (BUILD.md Latency: "16kHz mono PCM"). Utterance length is capped so a long
 * press can't inflate prefill. Caller must hold RECORD_AUDIO before [start].
 */
class AudioRecorder(
    private val sampleRate: Int = 16_000,
    private val maxSeconds: Int = 15,
) {
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var recording = false
    private val chunks = ArrayList<ShortArray>()

    @SuppressLint("MissingPermission")
    fun start() {
        stopInternal()
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(sampleRate)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf,
        )
        record = rec
        synchronized(chunks) { chunks.clear() }
        recording = true
        rec.startRecording()
        val cap = sampleRate * maxSeconds
        thread = Thread {
            val buf = ShortArray(minBuf)
            var total = 0
            while (recording && total < cap) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) {
                    synchronized(chunks) { chunks.add(buf.copyOf(n)) }
                    total += n
                }
            }
        }.also { it.start() }
    }

    /** Stop and return everything captured as one contiguous PCM buffer. */
    fun stop(): ShortArray {
        recording = false
        thread?.join(600)
        thread = null
        stopInternal()
        return synchronized(chunks) {
            val total = chunks.sumOf { it.size }
            val out = ShortArray(total)
            var o = 0
            for (c in chunks) { c.copyInto(out, o); o += c.size }
            out
        }
    }

    private fun stopInternal() {
        record?.let { r ->
            runCatching { if (r.recordingState == AudioRecord.RECORDSTATE_RECORDING) r.stop() }
            runCatching { r.release() }
        }
        record = null
    }
}
