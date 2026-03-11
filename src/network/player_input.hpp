// SuperTux Multi — Player Input from phone controller
// Copyright (C) 2026 sky1241
// GPL v3+

#pragma once

#include <string>

namespace network {

struct PlayerInput
{
  std::string player_id;
  std::string player_name;
  std::string color;

  float stick_x = 0.0f;  // -1.0 (left) to 1.0 (right)
  float stick_y = 0.0f;  // -1.0 (down) to 1.0 (up)
  bool btn_a = false;     // jump
  bool btn_b = false;     // action

  bool connected = false;
};

} // namespace network
