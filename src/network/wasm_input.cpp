// SuperTux Multi — WebAssembly Input Client
// Copyright (C) 2026 sky1241
// GPL v3+

#ifdef __EMSCRIPTEN__

#include "network/wasm_input.hpp"
#include "network/protocol.hpp"

#include <algorithm>
#include <sstream>

#include <emscripten.h>

namespace network {

// ---------------------------------------------------------------------------
// Internal state (module-level, protected by s_mutex)
// ---------------------------------------------------------------------------

static std::mutex                          s_mutex;
static std::map<std::string, PlayerInput>  s_inputs;      // player_id -> input
static std::vector<std::string>            s_player_order; // join order from state msgs

// ---------------------------------------------------------------------------
// JavaScript glue — runs in the browser context
// ---------------------------------------------------------------------------

EM_JS(void, wasm_input_connect_js, (), {
  // Derive phone WebSocket URL from the page's own hostname
  // (The TV opened http://PHONE_IP:8080/ so hostname == phone IP)
  var hostname = window.location.hostname;
  var wsUrl    = "ws://" + hostname + ":" + 9876;
  console.log("[WasmInput] Connecting to " + wsUrl);

  function connect() {
    var ws = new WebSocket(wsUrl);
    window._supertux_ws = ws;

    ws.onopen = function() {
      console.log("[WasmInput] Connected — registering as tv_client");
      ws.send(JSON.stringify({ type: "tv_client" }));
    };

    ws.onmessage = function(ev) {
      try {
        var msg = JSON.parse(ev.data);

        if (msg.type === "input") {
          // Relay: phone controller input → wasm C++
          var pid  = (msg.player_id  || "").substring(0, 64);
          var sx   = +(msg.stick_x   || 0);
          var sy   = +(msg.stick_y   || 0);
          var a    = msg.btn_a  ? 1 : 0;
          var b    = msg.btn_b  ? 1 : 0;

          // Pass string to C via stack (no heap allocation needed)
          var pidLen = lengthBytesUTF8(pid) + 1;
          var stack  = stackSave();
          var pidPtr = stackAlloc(pidLen);
          stringToUTF8(pid, pidPtr, pidLen);
          Module._wasm_dispatch_input_c(pidPtr, sx, sy, a, b);
          stackRestore(stack);

        } else if (msg.type === "state") {
          // Rebuild ordered player list from state message
          var players = msg.players || [];
          var csvIds  = players.map(function(p){ return p.player_id || ""; }).join(",");
          var csvLen  = lengthBytesUTF8(csvIds) + 1;
          var stack2  = stackSave();
          var csvPtr  = stackAlloc(csvLen);
          stringToUTF8(csvIds, csvPtr, csvLen);
          Module._wasm_dispatch_state_c(csvPtr);
          stackRestore(stack2);
        }
        // victory: ignore for now
      } catch(e) {
        console.warn("[WasmInput] parse error:", e);
      }
    };

    ws.onclose = function() {
      console.log("[WasmInput] WS closed — retry in 3 s");
      setTimeout(connect, 3000);
    };

    ws.onerror = function(e) {
      console.warn("[WasmInput] WS error:", e);
    };
  }

  connect();
});

// ---------------------------------------------------------------------------
// Exported C functions — called from JavaScript via Module._xxx
// ---------------------------------------------------------------------------

extern "C" {

EMSCRIPTEN_KEEPALIVE
void wasm_dispatch_input_c(const char* pid, float sx, float sy, int btn_a, int btn_b)
{
  std::lock_guard<std::mutex> lock(s_mutex);
  auto& inp = s_inputs[pid];
  if (inp.player_id.empty()) {
    inp.player_id  = pid;
    inp.connected  = true;
    // Assign color from slot index (order determined by first input received)
    if (std::find(s_player_order.begin(), s_player_order.end(), std::string(pid))
        == s_player_order.end()) {
      s_player_order.push_back(pid);
    }
    int idx       = static_cast<int>(
      std::find(s_player_order.begin(), s_player_order.end(), std::string(pid))
      - s_player_order.begin());
    inp.color = PLAYER_COLORS[idx % MAX_PLAYERS];
  }
  inp.stick_x = sx;
  inp.stick_y = sy;
  inp.btn_a   = btn_a != 0;
  inp.btn_b   = btn_b != 0;
}

EMSCRIPTEN_KEEPALIVE
void wasm_dispatch_state_c(const char* player_ids_csv)
{
  // Parse comma-separated player ID list from a "state" message.
  // This sets the authoritative join order (including for color assignment).
  std::vector<std::string> ids;
  std::istringstream ss(player_ids_csv);
  std::string tok;
  while (std::getline(ss, tok, ',')) {
    if (!tok.empty()) ids.push_back(tok);
  }

  std::lock_guard<std::mutex> lock(s_mutex);
  s_player_order = ids;
  // Ensure an entry exists for every player in the list
  for (int i = 0; i < static_cast<int>(ids.size()); ++i) {
    auto& inp = s_inputs[ids[i]];
    if (inp.player_id.empty()) {
      inp.player_id = ids[i];
      inp.connected = true;
      inp.color     = PLAYER_COLORS[i % MAX_PLAYERS];
    }
  }
}

} // extern "C"

// ---------------------------------------------------------------------------
// Public C++ API
// ---------------------------------------------------------------------------

void WasmInputClient::init()
{
  wasm_input_connect_js();
}

std::map<std::string, PlayerInput> WasmInputClient::get_all_inputs()
{
  std::lock_guard<std::mutex> lock(s_mutex);
  return s_inputs;
}

std::vector<std::string> WasmInputClient::get_player_ids()
{
  std::lock_guard<std::mutex> lock(s_mutex);
  return s_player_order;
}

int WasmInputClient::get_player_count()
{
  std::lock_guard<std::mutex> lock(s_mutex);
  int count = 0;
  for (const auto& pid : s_player_order) {
    auto it = s_inputs.find(pid);
    if (it != s_inputs.end() && it->second.connected)
      ++count;
  }
  return count;
}

void WasmInputClient::dispatch_input(const char* player_id,
                                     float stick_x, float stick_y,
                                     int btn_a, int btn_b)
{
  wasm_dispatch_input_c(player_id, stick_x, stick_y, btn_a, btn_b);
}

void WasmInputClient::dispatch_state(const char* player_ids_csv)
{
  wasm_dispatch_state_c(player_ids_csv);
}

} // namespace network

#endif // __EMSCRIPTEN__
