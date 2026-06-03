import React, { useState, useEffect, useRef } from "react";
import { 
  Send, 
  Settings, 
  Volume2, 
  Square, 
  Server, 
  Smartphone, 
  Cpu, 
  CheckCircle2, 
  Terminal, 
  Trash2, 
  Wifi, 
  HelpCircle,
  Eye,
  RefreshCw,
  Sliders,
  Glasses,
  Battery,
  BatteryCharging
} from "lucide-react";

interface BridgeEvent {
  id: string;
  type: "INPUT" | "SENDING" | "RESPONSE" | "SPEECH" | "ERROR";
  message: string;
  timestamp: string;
}

export default function App() {
  // Mobile Simulator state variables corresponding to AgentUiState & configurations
  const [apiUrl, setApiUrl] = useState("https://mock-subdomain.mydomain.com/api/agent/message");
  const [deviceId, setDeviceId] = useState("phone-test-001");
  const [sessionId, setSessionId] = useState("");
  const [inputText, setInputText] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [isTtsReady, setIsTtsReady] = useState(true);
  const [isTtsSpeaking, setIsTtsSpeaking] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [inputSource, setInputSource] = useState<"phone_text" | "wearable_meta">("phone_text");
  const [isBackendConnected, setIsBackendConnected] = useState(true);
  const [batteryLevel, setBatteryLevel] = useState(88);
  const [isBatteryCharging, setIsBatteryCharging] = useState(false);
  
  // Simulation log queue
  const [events, setEvents] = useState<BridgeEvent[]>([]);
  const [isConfigExpanded, setIsConfigExpanded] = useState(false);
  
  // Environment simulation configuration
  const [simulationResponseText, setSimulationResponseText] = useState(
    "Acknowledged, Hermes payload received! Synthesizing instructions now."
  );
  const [useRealNetwork, setUseRealNetwork] = useState(false);

  // Auto scroll reference
  const terminalEndRef = useRef<HTMLDivElement>(null);

  // Generate responsive session ID on load
  useEffect(() => {
    const randomHex = Math.random().toString(36).substring(2, 8);
    setSessionId(`phone-session-${randomHex}`);
    
    // Add initialization event
    const now = new Date().toLocaleTimeString();
    setEvents([
      {
        id: "init",
        type: "RESPONSE",
        message: "[Session Sync] Local session ID auto-generated and active.",
        timestamp: now,
      }
    ]);
  }, []);

  // Sync scroll
  useEffect(() => {
    if (terminalEndRef.current) {
      terminalEndRef.current.scrollIntoView({ behavior: "smooth" });
    }
  }, [events]);

  // Native Battery Monitoring
  useEffect(() => {
    let batteryInstance: any = null;

    const handleBatteryChange = (batt: any) => {
      setBatteryLevel(Math.round(batt.level * 100));
      setIsBatteryCharging(batt.charging);
    };

    if (typeof navigator !== "undefined" && "getBattery" in navigator) {
      (navigator as any).getBattery().then((batt: any) => {
        batteryInstance = batt;
        handleBatteryChange(batt);

        batt.addEventListener("levelchange", () => handleBatteryChange(batt));
        batt.addEventListener("chargingchange", () => handleBatteryChange(batt));
      });
    }

    return () => {
      if (batteryInstance) {
        batteryInstance.removeEventListener("levelchange", () => handleBatteryChange(batteryInstance));
        batteryInstance.removeEventListener("chargingchange", () => handleBatteryChange(batteryInstance));
      }
    };
  }, []);

  const addTelemetryEvent = (type: BridgeEvent["type"], message: string) => {
    const now = new Date().toLocaleTimeString();
    setEvents((prev) => [
      ...prev,
      {
        id: Math.random().toString(),
        type,
        message,
        timestamp: now,
      },
    ]);
  };

  // Browser-native dynamic TTS speak
  const triggerSpeech = (text: string) => {
    if ("speechSynthesis" in window) {
      window.speechSynthesis.cancel();
      const utterance = new SpeechSynthesisUtterance(text);
      utterance.onstart = () => setIsTtsSpeaking(true);
      utterance.onend = () => setIsTtsSpeaking(false);
      utterance.onerror = () => setIsTtsSpeaking(false);
      window.speechSynthesis.speak(utterance);
      addTelemetryEvent("SPEECH", `TTS Engine speaking: "${text}"`);
    } else {
      addTelemetryEvent("ERROR", "Web SpeechSynthesis is not supported on this device's browser.");
    }
  };

  const stopSpeech = () => {
    if ("speechSynthesis" in window) {
      window.speechSynthesis.cancel();
      setIsTtsSpeaking(false);
      addTelemetryEvent("SPEECH", "Speech synthesis playback manually aborted.");
    }
  };

  const clearCurrentEvents = () => {
    setEvents([]);
  };

  // Main input submission pipeline (Matches PhoneTextInputSource -> AgentRepository flow)
  const handleSendMessage = async (textToSend?: string) => {
    const rawText = textToSend || inputText;
    if (!rawText.trim() || isLoading) return;

    setErrorMessage(null);
    if (!textToSend) {
      setInputText("");
    }

    const capturePrefix = inputSource === "wearable_meta" ? "[Wearable State Capture] " : "";
    const processedText = `${capturePrefix}${rawText}`;

    // 1. Emit input event
    addTelemetryEvent("INPUT", processedText);
    setIsLoading(true);

    const timestampIso = new Date().toISOString();
    const mockRequestPayload = {
      device_id: deviceId,
      session_id: sessionId,
      input_type: "text",
      text: processedText,
      timestamp: timestampIso,
      metadata: {
        platform: "android_phone_test",
        source: inputSource,
        wearable: inputSource === "wearable_meta" ? "meta_glasses" : "none",
        audio_transcribed_on_edge: true,
        test_mode: true
      }
    };

    // 2. Network Transmission simulation/triggering
    addTelemetryEvent("SENDING", `POST ${apiUrl} | Payload: ${JSON.stringify(mockRequestPayload.metadata)}`);

    try {
      if (useRealNetwork) {
        // Real connection request
        const res = await fetch(apiUrl, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(mockRequestPayload),
        });

        if (!res.ok) {
          throw new Error(`HTTP Error status: ${res.status}`);
        }

        const data = await res.json();
        setIsLoading(false);

        if (data.ok) {
          setIsBackendConnected(true);
          addTelemetryEvent("RESPONSE", `Response OK: "${data.response_text}"`);
          
          // Verify session rotation payload
          if (data.session_id && data.session_id !== sessionId) {
            setSessionId(data.session_id);
            addTelemetryEvent("RESPONSE", `[Session Sync] Updated local Session ID to: ${data.session_id}`);
          }

          triggerSpeech(data.response_text);
        } else {
          setIsBackendConnected(false);
          const errMsg = data.error || "Backend return flag failed (ok=false).";
          setErrorMessage(errMsg);
          addTelemetryEvent("ERROR", errMsg);
        }
      } else {
        // Pure simulator delay to reproduce actual Android response experience
        await new Promise((resolve) => setTimeout(resolve, 850));
        setIsLoading(false);
        setIsBackendConnected(true);
        
        addTelemetryEvent("RESPONSE", `Response (Simulated API): "${simulationResponseText}"`);
        triggerSpeech(simulationResponseText);
      }
    } catch (err: any) {
      setIsLoading(false);
      setIsBackendConnected(false);
      const errMsg = err.message || "Unknown networking failure during post.";
      setErrorMessage(errMsg);
      addTelemetryEvent("ERROR", `API Connection Error: ${errMsg}`);
    }
  };

  const selectPresetText = (txt: string) => {
    setInputText(txt);
  };

  return (
    <div className="min-h-screen bg-[#0A0E14] text-[#F8F9FA] font-sans antialiased p-4 lg:p-8 flex flex-col md:flex-row gap-6 max-w-7xl mx-auto">
      
      {/* LEFT: Premium architectural monitor & guidelines */}
      <div className="flex-1 flex flex-col gap-6 max-w-xl">
        <div className="p-6 rounded-xl bg-[#131924] border border-[#1F293D] shadow-lg">
          <div className="flex items-center gap-3 mb-4">
            <Cpu className="text-[#00E676] w-6 h-6 animate-pulse" />
            <h1 className="text-xl font-bold tracking-tight text-white">
              Hermes Mobile Bridge
            </h1>
          </div>
          <p className="text-sm text-[#8892B0] leading-relaxed mb-4">
            This clean-architecture Android / Kotlin application acts as an edge gateway. 
            It captures input data from replaceable adapters, ships it over telemetry POST APIs to the brain, 
            and routes natural language speech responses back to edge hardware.
          </p>

          <div className="border-t border-[#1F293D] pt-4 flex flex-col gap-3">
            <div className="text-xs font-semibold text-slate-400 tracking-wider uppercase mb-1">
              Active Clean Interfaces
            </div>
            
            <div className="grid grid-cols-2 gap-2 text-xs font-mono bg-[#0B0F17] p-3 rounded border border-slate-800">
              <div className="text-emerald-400">InputSource.kt</div>
              <div className="text-slate-400">Clean contract</div>
              
              <div className="text-emerald-400">BridgeEvent.kt</div>
              <div className="text-slate-400">Standardized pipeline</div>
              
              <div className="text-sky-400">AgentRepository.kt</div>
              <div className="text-slate-400">Handles POST API</div>
              
              <div className="text-fuchsia-400">ResponseOutput.kt</div>
              <div className="text-slate-400">High-fidelity voice</div>
            </div>
          </div>
        </div>

        {/* Development Gates Progress Status Card */}
        <div className="p-6 rounded-xl bg-[#131924] border border-[#1F293D] flex-1">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-sm font-semibold uppercase tracking-wider text-slate-300">
              Development Gates Roadmap
            </h2>
            <span className="text-[10px] font-mono bg-[#1E293B] text-[#00E676] px-2 py-0.5 rounded border border-[#00E676]/30">
              Active Code verified
            </span>
          </div>

          <div className="space-y-4">
            <div className="flex items-start gap-3">
              <CheckCircle2 className="w-5 h-5 text-[#00E676] shrink-0 mt-0.5" />
              <div>
                <h3 className="text-xs font-semibold text-white">Gate 0: Stable Compilation</h3>
                <p className="text-[11px] text-[#8892B0]">Stable Kotlin Gradle DSL with strict type stripping builds successfully on Android Studio.</p>
              </div>
            </div>

            <div className="flex items-start gap-3">
              <CheckCircle2 className="w-5 h-5 text-[#00E676] shrink-0 mt-0.5" />
              <div>
                <h3 className="text-xs font-semibold text-white">Gate 1: InputSource Captures</h3>
                <p className="text-[11px] text-[#8892B0]">On-Screen text or external telemetry streams translate cleanly into standardized BridgeEvent payloads.</p>
              </div>
            </div>

            <div className="flex items-start gap-3">
              <CheckCircle2 className="w-5 h-5 text-[#00E676] shrink-0 mt-0.5" />
              <div>
                <h3 className="text-xs font-semibold text-white">Gate 2: Pipeline POST Out</h3>
                <p className="text-[11px] text-[#8892B0]">Repository generates valid HTTP JSON POST matching the physical schema requirements.</p>
              </div>
            </div>

            <div className="flex items-start gap-3">
              <CheckCircle2 className="w-5 h-5 text-[#00E676] shrink-0 mt-0.5" />
              <div>
                <h3 className="text-xs font-semibold text-white">Gate 3 &amp; 4: Layout &amp; TextToSpeech</h3>
                <p className="text-[11px] text-[#8892B0]">Received telemetry is placed into the list view, and spoken over standard Android TTS API.</p>
              </div>
            </div>

            <div className="flex items-start gap-3">
              <div className="p-1 rounded bg-[#00E676]/10 border border-[#00E676]/30 text-[#00E676] shrink-0 mt-0.5">
                <Glasses size={12} />
              </div>
              <div>
                <h3 className="text-xs font-semibold text-green-300">Gate 5: Meta Wearable Input Swap</h3>
                <p className="text-[11px] text-green-400">MetaWearableInputSource stub acts as a modular drop-in replacement, without any API model modifications.</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* RIGHT: Live Responsive Phone Simulator Screen */}
      <div className="flex-1 flex justify-center items-center">
        <div className="w-full max-w-[390px] rounded-[40px] border-8 border-[#1E2530] bg-[#0F141C] shadow-2xl p-4 flex flex-col h-[760px] relative overflow-hidden">
          
          {/* Top Notch Area */}
          <div className="absolute top-2 left-1/2 -translate-x-1/2 w-32 h-4 bg-[#1E2530] rounded-full z-20 flex justify-center items-center">
            <div className="w-2.5 h-2.5 rounded-full bg-black mr-2"></div>
            <div className="w-8 h-1 rounded-full bg-[#354358]"></div>
          </div>

          {/* Phone Header Toolbar */}
          <div className="flex items-center justify-between pt-6 pb-2 border-b border-white/5">
            <div className="flex items-center gap-2">
              <div className={`w-2.5 h-2.5 rounded-full ${isTtsReady ? 'bg-[#00E676] shadow-[0_0_8px_#00E676]' : 'bg-[#8892B0]'}`}></div>
              <div>
                <h2 className="text-xs font-bold font-mono text-white tracking-wide">
                  HERMES BRIDGE
                </h2>
                <span className="text-[9px] font-mono text-[#8892B0] block">
                  Device: {deviceId.substring(0, 15)}
                </span>
              </div>
            </div>
            
            <div className="flex items-center gap-1">
              {/* Battery Indicator */}
              <div 
                className="flex items-center gap-1 text-[10px] font-mono mr-1 cursor-help" 
                title={`Battery: ${batteryLevel}% ${isBatteryCharging ? '(Charging)' : ''}`}
              >
                {isBatteryCharging ? (
                  <BatteryCharging size={13} className="text-[#00E676] animate-pulse" />
                ) : (
                  <Battery 
                    size={13} 
                    className={batteryLevel <= 20 ? "text-[#FF5252] animate-bounce" : "text-[#8892B0]"} 
                  />
                )}
                <span className={isBatteryCharging ? "text-[#00E676] font-semibold" : batteryLevel <= 20 ? "text-[#FF5252] font-semibold" : "text-white"}>
                  {batteryLevel}%
                </span>
              </div>

              <div className="flex items-center gap-1.5 mr-2">
                <span className={`flex h-2 w-2 relative ${isBackendConnected ? "" : "hidden"}`}>
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-[#00E676] opacity-75"></span>
                  <span className="relative inline-flex rounded-full h-2 w-2 bg-[#00E676]"></span>
                </span>
                {!isBackendConnected && (
                  <span className="h-2 w-2 rounded-full bg-[#FF5252]"></span>
                )}
                <span className={`text-[10px] font-mono font-bold tracking-tight uppercase ${isBackendConnected ? "text-[#00E676]" : "text-[#FF5252]"}`}>
                  {isBackendConnected ? "ONLINE" : "OFFLINE"}
                </span>
              </div>

              <button 
                onClick={clearCurrentEvents} 
                className="p-1.5 rounded hover:bg-slate-800 text-[#8892B0] hover:text-white transition-colors"
                title="Clear Logs"
              >
                <Trash2 size={13} />
              </button>
              <button 
                onClick={() => setIsConfigExpanded(!isConfigExpanded)} 
                className={`p-1.5 rounded transition-colors ${isConfigExpanded ? 'text-[#00E676] bg-slate-800' : 'text-[#8892B0] hover:text-white'}`}
                title="Config Console"
              >
                <Settings size={13} />
              </button>
            </div>
          </div>

          {/* Config console accordion */}
          {isConfigExpanded && (
            <div className="bg-[#1E2530] rounded-xl p-3 mt-2 border border-slate-700/50 flex flex-col gap-2 z-10">
              <span className="text-[10px] font-mono text-[#00E676] font-bold uppercase tracking-wider block">
                CONFIG CONSOLE
              </span>
              
              <div>
                <label className="text-[9px] font-mono text-slate-400 block mb-0.5">Server Endpoint URL</label>
                <input 
                  type="text" 
                  value={apiUrl} 
                  onChange={(e) => setApiUrl(e.target.value)}
                  className="w-full text-[10px] font-mono bg-[#0F141C] border border-slate-700 rounded p-1 text-white focus:outline-none focus:border-[#00E676]"
                />
              </div>

              <div>
                <label className="text-[9px] font-mono text-slate-400 block mb-0.5">Device ID</label>
                <input 
                  type="text" 
                  value={deviceId} 
                  onChange={(e) => setDeviceId(e.target.value)}
                  className="w-full text-[10px] font-mono bg-[#0F141C] border border-slate-700 rounded p-1 text-white focus:outline-none focus:border-[#00E676]"
                />
              </div>

              <div>
                <label className="text-[9px] font-mono text-slate-400 block mb-0.5">Session ID</label>
                <input 
                  type="text" 
                  value={sessionId} 
                  onChange={(e) => setSessionId(e.target.value)}
                  className="w-full text-[10px] font-mono bg-[#0F141C] border border-slate-700 rounded p-1 text-white focus:outline-none"
                />
              </div>

              <div className="border-t border-slate-700/40 pt-2 flex flex-col gap-1">
                <span className="text-[9px] font-mono text-[#00E676] font-bold uppercase tracking-wider block">
                  Simulate Battery telemetry
                </span>
                <div className="flex items-center justify-between text-[9px] font-mono text-slate-300">
                  <span>Battery Level: {batteryLevel}%</span>
                  <input 
                    type="range" 
                    min="1" 
                    max="100" 
                    value={batteryLevel} 
                    onChange={(e) => setBatteryLevel(Number(e.target.value))}
                    className="w-24 accent-[#00E676]" 
                  />
                </div>
                <div className="flex items-center justify-between text-[9px] font-mono text-slate-300">
                  <span>Is Charging</span>
                  <input 
                    type="checkbox" 
                    checked={isBatteryCharging} 
                    onChange={(e) => setIsBatteryCharging(e.target.checked)}
                    className="accent-[#00E676]" 
                  />
                </div>
              </div>

              <div className="border-t border-slate-700/40 pt-2 flex flex-col gap-1.5">
                <div className="flex items-center justify-between text-[9px] font-mono text-slate-300">
                  <span>Use Active Server Connection</span>
                  <input 
                    type="checkbox"
                    checked={useRealNetwork}
                    onChange={(e) => setUseRealNetwork(e.target.checked)}
                    className="accent-[#00E676]"
                  />
                </div>
                {!useRealNetwork && (
                  <div>
                    <label className="text-[9px] font-mono text-slate-400 block mb-0.5">Simulated Response Payload</label>
                    <textarea
                      rows={1}
                      value={simulationResponseText}
                      onChange={(e) => setSimulationResponseText(e.target.value)}
                      className="w-full text-[10px] font-mono bg-[#0F141C] border border-slate-700 rounded p-1 text-white focus:outline-none resize-none"
                    />
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Input Source Toggle Segment (Clean Architecture representation) */}
          <div className="bg-[#1D2431]/60 p-1.5 rounded-lg my-2 flex items-center justify-between text-xs gap-1.5">
            <span className="text-[9px] font-mono text-[#8892B0] pl-1.5 uppercase font-bold tracking-wider">
              Input Adapter:
            </span>
            <div className="flex bg-[#0F141C] rounded p-0.5 shrink-0">
              <button 
                onClick={() => {
                  setInputSource("phone_text");
                  addTelemetryEvent("RESPONSE", "[Source Sync] System initialized input key-value to: PhoneTextInputSource");
                }}
                className={`px-2 py-1 text-[9px] font-mono rounded flex items-center gap-1 transition-all ${inputSource === "phone_text" ? "bg-slate-700 text-white font-semibold" : "text-[#8892B0] hover:text-white"}`}
              >
                <Smartphone size={10} /> Keyboard
              </button>
              <button 
                onClick={() => {
                  setInputSource("wearable_meta");
                  addTelemetryEvent("RESPONSE", "[Source Sync] Drop-in swap active: MetaWearableInputSource loaded");
                }}
                className={`px-2 py-1 text-[9px] font-mono rounded flex items-center gap-1 transition-all ${inputSource === "wearable_meta" ? "bg-[#00E676] text-[#0F141C] font-semibold" : "text-[#8892B0] hover:text-white"}`}
              >
                <Glasses size={10} /> Wearable
              </button>
            </div>
          </div>

          {/* Error display */}
          {errorMessage && (
            <div className="my-2 bg-red-950/40 border border-red-500/50 rounded-lg p-2.5 flex items-start gap-2 text-red-100">
              <HelpCircle className="w-4 h-4 shrink-0 mt-0.5 text-red-500" />
              <div>
                <div className="text-[10px] font-bold font-mono tracking-wide uppercase text-red-300">Transmission Failed</div>
                <div className="text-[9px] font-mono mt-0.5">{errorMessage}</div>
              </div>
            </div>
          )}

          {/* Central Telemetry Feed Panel (Interactive scrolling Console) */}
          <div className="flex-1 min-h-0 bg-[#0F141C] border border-slate-800/40 rounded-xl my-2 p-3 overflow-y-auto flex flex-col gap-2 relative">
            {events.length === 0 ? (
              <div className="flex-1 flex flex-col justify-center items-center text-center p-4">
                <Terminal className="text-slate-700 w-10 h-10 mb-2 stroke-[1.5]" />
                <h4 className="text-[11px] font-bold text-slate-500 font-mono tracking-wider">HERMES PIPE IDLE</h4>
                <p className="text-[9px] text-slate-600 font-mono mt-1 max-w-[200px]">
                  Send text using the presets or type custom prompts to trigger the pipeline.
                </p>
              </div>
            ) : (
              <div className="flex flex-col gap-2">
                {events.map((ev) => {
                  const isInput = ev.type === "INPUT";
                  const isSending = ev.type === "SENDING";
                  const isResp = ev.type === "RESPONSE";
                  const isSpeech = ev.type === "SPEECH";
                  const isErr = ev.type === "ERROR";

                  let colorClass = "text-slate-400 bg-slate-900/40";
                  let prefix = "PIPE";

                  if (isInput) {
                    colorClass = "text-[#00E676] bg-[#00E676]/5";
                    prefix = "INPUT";
                  } else if (isSending) {
                    colorClass = "text-yellow-400 bg-yellow-400/5";
                    prefix = "SENDING";
                  } else if (isResp) {
                    colorClass = "text-[#33B5E5] bg-[#33B5E5]/5";
                    prefix = "RESPONSE";
                  } else if (isSpeech) {
                    colorClass = "text-[#E040FB] bg-[#E040FB]/5";
                    prefix = "SPEECH";
                  } else if (isErr) {
                    colorClass = "text-red-400 bg-red-400/5";
                    prefix = "ERROR";
                  }

                  return (
                    <div key={ev.id} className={`p-2 rounded border border-white/5 font-mono text-[10px] ${colorClass}`}>
                      <div className="flex justify-between items-center opacity-80 mb-0.5 text-[8px] font-bold">
                        <span>{prefix}</span>
                        <span>{ev.timestamp}</span>
                      </div>
                      <p className="leading-tight break-all">{ev.message}</p>
                    </div>
                  );
                })}
                <div ref={terminalEndRef} />
              </div>
            )}
          </div>

          {/* Active Speaking Bar Overlay */}
          {isTtsSpeaking && (
            <div className="bg-[#1E2530] border border-[#00E676]/30 rounded-lg p-2 mb-2 flex items-center justify-between shadow-lg">
              <div className="flex items-center gap-2">
                <div className="relative flex h-2 w-2">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-[#00E676] opacity-75"></span>
                  <span className="relative inline-flex rounded-full h-2 w-2 bg-[#00E676]"></span>
                </div>
                <span className="text-[10px] font-mono text-white">Speaking response payload...</span>
              </div>
              <button 
                onClick={stopSpeech} 
                className="p-1 px-2 text-[9px] font-mono bg-red-500/20 text-red-300 hover:bg-red-500/30 rounded border border-red-500/40"
              >
                Stop
              </button>
            </div>
          )}

          {/* Quick chip recommendations */}
          <div className="flex overflow-x-auto gap-1 py-1 no-scrollbar text-[9px]">
            {["Introduce yourself", "System health check", "Ping check", "Reset Session"].map((sample) => (
              <button
                key={sample}
                onClick={() => selectPresetText(sample)}
                className="bg-[#1E2530] hover:bg-slate-700 text-slate-300 hover:text-white px-2 py-1 rounded-full whitespace-nowrap border border-slate-800 transition-colors shrink-0"
              >
                {sample}
              </button>
            ))}
          </div>

          {/* Bottom input area matching standard phone keyboard entry */}
          <div className="mt-2 pt-2 border-t border-white/5 flex gap-2">
            <input
              type="text"
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              placeholder={inputSource === "wearable_meta" ? "Simulate glasses gesture transcript..." : "Type text for backend..."}
              onKeyDown={(e) => e.key === "Enter" && handleSendMessage()}
              disabled={isLoading}
              className="flex-1 bg-[#1E2530] text-xs text-white placeholder-slate-500 rounded-lg px-3 py-2 border border-slate-800 focus:outline-none focus:border-[#00E676] disabled:opacity-50 font-mono"
            />
            <button
              onClick={() => handleSendMessage()}
              disabled={isLoading || !inputText.trim()}
              className="p-2.5 rounded-lg bg-[#00E676] text-slate-900 hover:opacity-90 disabled:opacity-40 transition-opacity font-bold"
            >
              <Send size={14} />
            </button>
          </div>

          {/* Bottom Bar indicator */}
          <div className="w-24 h-1 bg-[#354358] rounded-full mx-auto mt-3 shrink-0"></div>
        </div>
      </div>
    </div>
  );
}
