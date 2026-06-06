package com.example.hermesbridge

import android.app.Application
import android.util.Log
import com.example.hermesbridge.audio.BluetoothAudioRouteManager
import com.example.hermesbridge.bridge.MetaWearableInputSource
import com.example.hermesbridge.bridge.PhoneTextInputSource
import com.example.hermesbridge.conversation.ConversationTurnCoordinator
import com.example.hermesbridge.conversation.ConversationTurnSource
import com.example.hermesbridge.meta.MetaDatManager
import com.example.hermesbridge.metrics.InteractionMetricsCollector
import com.example.hermesbridge.speech.AndroidSpeechRecognizerInput
import com.example.hermesbridge.speech.AndroidTtsSpeechOutput
import com.example.hermesbridge.wakeword.PorcupineWakeWordEngine
import com.example.hermesbridge.wakeword.WakeWordConversationCoordinator
import com.example.hermesbridge.wakeword.WakeWordTestManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

class HermesBridgeApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var metaDatManager: MetaDatManager
    lateinit var audioRouteManager: BluetoothAudioRouteManager
    lateinit var metricsCollector: InteractionMetricsCollector
    lateinit var wearableInputSource: MetaWearableInputSource
    lateinit var speechToText: AndroidSpeechRecognizerInput
    lateinit var wakeWordManager: WakeWordTestManager
    lateinit var turnCoordinator: ConversationTurnCoordinator
    lateinit var wakeTurnCoordinator: WakeWordConversationCoordinator
    
    lateinit var repository: AgentRepository
    lateinit var phoneInputSource: PhoneTextInputSource
    lateinit var bridgeController: BridgeController
    lateinit var speechOutput: AndroidTtsSpeechOutput

    override fun onCreate() {
        super.onCreate()
        Log.i("HermesApp", "Application onCreate")

        metricsCollector = InteractionMetricsCollector(this)
        audioRouteManager = BluetoothAudioRouteManager(this)
        metaDatManager = MetaDatManager(this)
        wearableInputSource = MetaWearableInputSource()
        phoneInputSource = PhoneTextInputSource()
        
        speechToText = AndroidSpeechRecognizerInput(this) {
            audioRouteManager.isRoutingToBluetooth()
        }

        val api = OkHttpAgentApi()
        repository = AgentRepository(api)
        
        metaDatManager.initialize()
        
        speechOutput = AndroidTtsSpeechOutput(this)

        val porcupineEngine = PorcupineWakeWordEngine(this, BuildConfig.PICOVOICE_ACCESS_KEY)
        wakeWordManager = WakeWordTestManager(this, porcupineEngine) {
            audioRouteManager.isRoutingToBluetooth()
        }

        bridgeController = BridgeController(
            this,
            applicationScope,
            repository,
            speechOutput,
            metaDatManager,
            audioRouteManager,
            speechToText,
            wakeWordManager,
            metricsCollector
        )

        turnCoordinator = ConversationTurnCoordinator(
            applicationScope,
            bridgeController,
            metaDatManager,
            audioRouteManager,
            speechToText,
            wearableInputSource,
            metricsCollector
        )

        wakeTurnCoordinator = WakeWordConversationCoordinator(
            applicationScope,
            bridgeController,
            wakeWordManager,
            turnCoordinator,
            metricsCollector
        )
        
        wearableInputSource.setListener { event ->
            bridgeController.submitBridgeEvent(event.text, ConversationTurnSource.MetaWearableVoice)
        }
        
        phoneInputSource.setListener { event ->
            bridgeController.submitBridgeEvent(event.text, ConversationTurnSource.PhoneText)
        }
        
        phoneInputSource.start()
        wearableInputSource.start()
        
        applicationScope.launch {
            turnCoordinator.turnState.collect { bridgeController.updateConversationTurnState(it) }
        }
    }
}
