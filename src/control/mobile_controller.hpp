//  SuperTux
//  Copyright (C) 2021 A. Semphris <semphris@protonmail.com>
//  Copyright (C) 2026 sky1241 - Tilt controls + dual jump mod
//
//  This program is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.
//
//  This program is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.
//
//  You should have received a copy of the GNU General Public License
//  along with this program.  If not, see <http://www.gnu.org/licenses/>.

#pragma once

#include <SDL.h>
#include <map>
#include <bitset>

#include "config.h"

#include "controller.hpp"
#include "math/rectf.hpp"
#include "math/vector.hpp"
#include "video/surface_ptr.hpp"

class Controller;
class DrawingContext;

class MobileController final
{
public:
  MobileController();
  ~MobileController();

  void draw(DrawingContext& context);
  void apply(Controller& controller) const;
  void update();

  /** returns true if the finger event was inside the screen button area */
  bool process_finger_down_event(const SDL_TouchFingerEvent& event);
  /** returns true if the finger event was inside the screen button area */
  bool process_finger_up_event(const SDL_TouchFingerEvent& event);
  /** returns true if the finger event was inside the screen button area */
  bool process_finger_motion_event(const SDL_TouchFingerEvent& event);

  void buzz();

private:
  void activate_widget_at_pos(float x, float y);
  void update_accelerometer();

private:
  std::bitset<(size_t)Control::CONTROLCOUNT> m_input, m_input_last;

  std::map<SDL_FingerID, Vector> m_fingers;

  // ========== TILT CONTROL (replaces D-pad) ==========
  SDL_Sensor* m_accelerometer;
  float m_tilt_x;            // Raw X axis value
  float m_tilt_deadzone;     // Minimum threshold (default 1.5 m/s²)
  float m_tilt_sensitivity;  // Multiplier for responsiveness

  // ========== DUAL JUMP BUTTONS ==========
  Rectf m_rect_small_jump;   // Small jump touch zone
  Rectf m_rect_big_jump;     // Big jump touch zone
  Rectf m_draw_small_jump;   // Small jump draw zone
  Rectf m_draw_big_jump;     // Big jump draw zone

  // Auto-release timer for small jump
  bool m_small_jump_active;
  Uint32 m_small_jump_start_time;
  static const Uint32 SMALL_JUMP_DURATION_MS = 80; // 80ms = short hop

  // Track which finger is on big jump
  SDL_FingerID m_big_jump_finger;
  bool m_big_jump_held;

  // ========== EXISTING BUTTONS (kept) ==========
  Rectf m_rect_action, m_rect_cheats,
        m_rect_debug, m_rect_escape, m_rect_item;
  Rectf m_draw_action, m_draw_cheats,
        m_draw_debug, m_draw_escape;

  // ========== TEXTURES ==========
  const SurfacePtr m_tex_btn, m_tex_btn_press, m_tex_pause,
                   m_tex_jump, m_tex_action, m_tex_cheats, m_tex_debug,
                   m_tex_small_jump, m_tex_big_jump;

  int m_screen_width, m_screen_height;
  float m_mobile_controls_scale;

  // We need the timer to be away from the game loop to stop vibration
  SDL_TimerID m_haptic_timer;
  std::unique_ptr<SDL_Haptic, decltype(&SDL_HapticClose)> m_haptic;

private:
  MobileController(const MobileController&) = delete;
  MobileController& operator=(const MobileController&) = delete;
};
