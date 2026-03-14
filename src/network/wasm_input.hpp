// SuperTux Multi — WebAssembly Input Client
// Copyright (C) 2026 sky1241
// GPL v3+
//
// In wasm builds, the browser's native WebSocket API is used instead of
// IXWebSocket.  This module connects to the phone's WS server as a
// "tv_client" (passive listener), receives relayed input messages, and
// exposes the same get_all_inputs / get_player_ids interface used by
// screen_manager.cpp so the dispatch code can be shared with a simple #ifdef.

#pragma once

#ifdef __EMSCRIPTEN__

#include <map>
#include <mutex>
#include <string>
#include <vector>

#include "network/player_input.hpp"
#include "network/protocol.hpp"

namespace network {

class WasmInputClient final
{
public:
  /// Connect to ws://window.location.hostname:WS_PORT, register as tv_client.
  /// Must be called once after Emscripten runtime is fully initialized.
  static void init();

  /// Thread-safe snapshot of all player inputs received from the phone.
  static std::map<std::string, PlayerInput> get_all_inputs();

  /// Player IDs in join order (from last "state" message).
  static std::vector<std::string> get_player_ids();

  /// Number of currently-known players.
  static int get_player_count();

  // ---- Called from exported C functions (JS → C++) ----

  /// Update or create a player's input from an "input" message.
  static void dispatch_input(const char* player_id,
                             float stick_x, float stick_y,
                             int btn_a, int btn_b);

  /// Rebuild the player roster from a "state" message.
  /// player_ids_csv: comma-separated ordered list of player_id values.
  static void dispatch_state(const char* player_ids_csv);

private:
  WasmInputClient() = delete;
};

} // namespace network

#endif // __EMSCRIPTEN__
