// SuperTux Multi — Input Dispatcher
// Copyright (C) 2026 sky1241
// GPL v3+

#pragma once

#include "network/player_input.hpp"

class Controller;

namespace network {

class WsServer;

/// Reads phone controller inputs from the WebSocket server
/// and applies them to a SuperTux Controller.
/// Call apply_to_controller() once per frame for each player.
class InputDispatcher final
{
public:
  /// Apply phone input for the given player_id to the given controller.
  /// stick_x maps to LEFT/RIGHT, btn_a to JUMP, btn_b to ACTION.
  static void apply_to_controller(const PlayerInput& input, Controller& controller);
};

} // namespace network
