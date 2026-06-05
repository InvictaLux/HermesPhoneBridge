package com.example.hermesbridge.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

class PcmCaptureManager(
    private val context: Context,
    private val audioManager: android.media.AudioManager
) {
    private val _status = MutableStateFlow<PcmCaptureStatus>(PcmCaptureStatus.Idle)
    val status: StateFlow<PcmCaptureStatus> = _status.asStateFlow()

    private val _result = MutableStateFlow(PcmCaptureResult())
    val result: StateFlow<PcmCaptureResult> = _result.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)
    private var captureJob: Job? = null

    private val SAMPLE_RATE = 8000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private val AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_COMMUNICATION

    fun startCapture() {
        if (_status.value is PcmCaptureStatus.Recording) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _status.value = PcmCaptureStatus.PermissionRequired
            return
        }

        _status.value = PcmCaptureStatus.Preparing
        _result.value = PcmCaptureResult()

        captureJob = scope.launch {
            runCapture()
        }
    }

    private suspend fun runCapture() {
        var audioRecord: AudioRecord? = null
        try {
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                updateError("Invalid buffer size: $minBufferSize")
                return
            }

            val totalExpectedSamples = SAMPLE_RATE * 3
            val pcmData = ShortArray(totalExpectedSamples)
            
            audioRecord = AudioRecord(
                AUDIO_SOURCE,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                minBufferSize.coerceAtLeast(totalExpectedSamples * 2)
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                updateError("AudioRecord initialization failed")
                return
            }

            if (!verifyBluetoothRoute()) {
                updateError("Bluetooth route not confirmed")
                return
            }

            audioRecord.startRecording()
            if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                updateError("Failed to start recording")
                return
            }

            _status.value = PcmCaptureStatus.Recording

            var totalSamplesRead = 0
            val startTime = System.currentTimeMillis()
            val tempBuffer = ShortArray(1024)

            while (totalSamplesRead < totalExpectedSamples && (System.currentTimeMillis() - startTime) < 4000) {
                if (!verifyBluetoothRoute()) {
                    updateError("Bluetooth route lost during capture")
                    break
                }

                val remaining = totalExpectedSamples - totalSamplesRead
                val toRead = tempBuffer.size.coerceAtMost(remaining)
                val readResult = audioRecord.read(tempBuffer, 0, toRead)

                if (readResult > 0) {
                    for (i in 0 until readResult) {
                        pcmData[totalSamplesRead + i] = tempBuffer[i]
                    }
                    totalSamplesRead += readResult
                } else if (readResult < 0) {
                    updateError("Error reading PCM: $readResult")
                    break
                }
                delay(10)
            }

            if (totalSamplesRead > 0) {
                computeAndReportMetrics(pcmData, totalSamplesRead, System.currentTimeMillis() - startTime)
            }

        } catch (e: Exception) {
            Log.e("HermesPcm", "Capture exception", e)
            updateError(e.message ?: "Unknown capture error")
        } finally {
            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (e: Exception) {
                Log.e("HermesPcm", "Error releasing AudioRecord", e)
            }
            if (_status.value is PcmCaptureStatus.Recording) {
                _status.value = PcmCaptureStatus.Completed
            }
        }
    }

    private fun verifyBluetoothRoute(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val device = audioManager.communicationDevice
            return device != null && (
                device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            )
        } else {
            return audioManager.isBluetoothScoOn
        }
    }

    private fun updateError(msg: String) {
        _status.value = PcmCaptureStatus.Error(msg)
        _result.value = PcmCaptureResult(error = msg)
    }

    private fun computeAndReportMetrics(data: ShortArray, count: Int, duration: Long) {
        var sumSquares = 0.0
        var peak = 0
        var nonZero = 0L

        for (i in 0 until count) {
            val sample = data[i].toInt()
            val absSample = abs(sample)
            if (absSample > peak) peak = absSample
            if (sample != 0) nonZero++
            sumSquares += (sample.toDouble() * sample.toDouble())
        }

        val rms = if (count > 0) sqrt(sumSquares / count) else 0.0
        val signalPresent = nonZero > (count * 0.01) && rms > 20.0

        val res = PcmCaptureResult(
            samplesCaptured = count.toLong(),
            bytesCaptured = count.toLong() * 2,
            durationMs = duration,
            rmsAmplitude = rms,
            peakAmplitude = peak,
            nonZeroSampleCount = nonZero,
            likelySignalPresent = signalPresent
        )

        _result.value = res
    }

    fun stopCapture() {
        captureJob?.cancel()
        captureJob = null
        if (_status.value is PcmCaptureStatus.Recording) {
            _status.value = PcmCaptureStatus.Stopped
        }
    }
}
