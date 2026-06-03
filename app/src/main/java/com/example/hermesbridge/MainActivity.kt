package com.example.hermesbridge

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: AgentViewModel
    private var ttsOutput: AndroidTtsResponseOutput? = null

    private val batteryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = if (level >= 0 && scale > 0) {
                (level * 100 / scale.toFloat()).toInt()
            } else {
                100
            }

            val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == android.os.BatteryManager.BATTERY_STATUS_FULL

            viewModel.updateBatteryState(batteryPct, isCharging)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initialize Inputs and Repositories
        val inputSource = PhoneTextInputSource()
        val api = OkHttpAgentApi()
        val repository = AgentRepository(api)

        // 2. Instantiate MVVM-lite ViewModel
        val factory = AgentViewModelFactory(
            application = this.application,
            repository = repository,
            inputSource = inputSource
        )
        viewModel = ViewModelProvider(this, factory)[AgentViewModel::class.java]

        // 3. Initialize Android's standard TTS response output player on the UI thread
        ttsOutput = AndroidTtsResponseOutput(this) { success ->
            if (success) {
                Log.d("MainActivity", "TextToSpeech successfully initialized and connected to layout pipeline")
                ttsOutput?.let { viewModel.setResponseOutput(it) }
            } else {
                Log.e("MainActivity", "TextToSpeech initialization failed")
            }
        }

        // Register battery status receiver
        registerReceiver(batteryReceiver, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))

        // 4. Inject layouts and Compose Theme
        setContent {
            MaterialTheme {
                AgentScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Stop speaking immediately when backgrounded
        viewModel.stopSpeaking()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Already unregistered or wasn't registered
        }
        // Release hardware synthesis bindings
        ttsOutput?.release()
    }
}
