package com.example.hermesbridge.wakeword

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

class WakeWordTestManager(
    private val context: Context,
    private val engine: WakeWordEngine,
    private val isRouteActive: () -> Boolean
) {
    private val _status = MutableStateFlow<WakeWordStatus>(WakeWordStatus.Idle)
    val status: StateFlow<WakeWordStatus> = _status.asStateFlow()

    private val _lastDetection = MutableStateFlow<WakeWordDetection?>(null)
    val lastDetection: StateFlow<WakeWordDetection?> = _lastDetection.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var listeningJob: Job? = null
    
    private var debounceMs: Long = 1000
    private var lastDetectionTime: Long = 0

    fun startTest() {
        if (_status.value is WakeWordStatus.Listening) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _status.value = WakeWordStatus.PermissionRequired
            return
        }

        if (!isRouteActive()) {
            _status.value = WakeWordStatus.AudioRouteRequired
            return
        }

        listeningJob = scope.launch {
            runDetectionLoop()
        }
    }

    private suspend fun runDetectionLoop() = withContext(Dispatchers.IO) {
        var audioRecord: AudioRecord? = null
        try {
            _status.value = WakeWordStatus.Starting
            engine.start()

            val sourceSampleRate = 8000
            val targetSampleRate = engine.sampleRate
            val frameLength = engine.frameLength

            val minBufferSize = AudioRecord.getMinBufferSize(
                sourceSampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sourceSampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize.coerceAtLeast(sourceSampleRate * 2)
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                _status.value = WakeWordStatus.Error("AudioRecord init failed")
                return@withContext
            }

            audioRecord.startRecording()
            _status.value = WakeWordStatus.Listening

            val sourceFrameLength = (frameLength * (sourceSampleRate.toDouble() / targetSampleRate)).roundToInt()
            val sourceBuffer = ShortArray(sourceFrameLength)
            val engineBuffer = ShortArray(frameLength)

            while (isActive && _status.value is WakeWordStatus.Listening) {
                if (!isRouteActive()) {
                    _status.value = WakeWordStatus.AudioRouteRequired
                    break
                }

                val read = audioRecord.read(sourceBuffer, 0, sourceBuffer.size)
                if (read == sourceBuffer.size) {
                    for (i in 0 until sourceFrameLength) {
                        engineBuffer[i * 2] = sourceBuffer[i]
                        engineBuffer[i * 2 + 1] = sourceBuffer[i]
                    }

                    val detection = engine.processFrame(engineBuffer)
                    if (detection != null) {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastDetectionTime < debounceMs) {
                            Log.d("HermesWake", "Duplicate detection ignored")
                        } else {
                            lastDetectionTime = now
                            _lastDetection.value = detection
                            _status.value = WakeWordStatus.Detected
                            delay(500)
                            if (isActive) _status.value = WakeWordStatus.Listening
                        }
                    }
                } else if (read < 0) {
                    _status.value = WakeWordStatus.Error("Audio read error: $read")
                    break
                }
            }

        } catch (e: Exception) {
            Log.e("HermesWake", "Detection loop exception", e)
            _status.value = WakeWordStatus.Error(e.message ?: "Unknown error")
        } finally {
            audioRecord?.stop()
            audioRecord?.release()
            engine.stop()
            if (_status.value !is WakeWordStatus.Error) {
                _status.value = WakeWordStatus.Stopped
            }
        }
    }

    fun stopTest() {
        listeningJob?.cancel()
        listeningJob = null
    }

    fun release() {
        stopTest()
        engine.release()
    }

    fun setSensitivity(sensitivity: Float) {
        engine.setSensitivity(sensitivity)
    }

    fun setDebounce(ms: Long) {
        debounceMs = ms
    }
}
