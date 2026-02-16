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

#include <config.h>
#include "control/mobile_controller.hpp"

#include <string>

#include "SDL.h"

#include "gui/menu_manager.hpp"
#include "util/log.hpp"
#include "control/controller.hpp"
#include "math/vector.hpp"
#include "supertux/globals.hpp"
#include "supertux/gameconfig.hpp"
#include "video/drawing_context.hpp"
#include "video/surface.hpp"

#define CONTROL_INT(x) (static_cast<std::underlying_type_t<Control>>(Control::x))

MobileController::MobileController() :
  m_input(),
  m_input_last(),
  m_fingers(),
  // Tilt control
  m_accelerometer(nullptr),
  m_tilt_x(0.0f),
  m_tilt_deadzone(1.5f),    // 1.5 m/s² dead zone
  m_tilt_sensitivity(1.0f),
  // D-pad fallback
  m_rect_directions(0.f, 0.f, 128.f, 128.f),
  m_draw_directions(0.f, 0.f, 128.f, 128.f),
  // Jump button (initial position, recalculated in draw())
  m_rect_jump(-80.f, -80.f, -16.f, -16.f),
  m_draw_jump(-80.f, -80.f, -16.f, -16.f),
  m_jump_finger(-1),
  m_jump_held(false),
  // Existing buttons
  m_rect_action(-240.f, -80.f, -176.f, -16.f),
  m_rect_cheats(-160.f, 16.f, -96.f, 80.f),
  m_rect_debug(-80.f, 16.f, -16.f, 80.f),
  m_rect_escape({96.f, 14.f}, Sizef{48.f, 48.f}),
  m_rect_item({0.f, 0.f}, Sizef{128.f, 128.f}),
  m_draw_action(-240.f, -80.f, -176.f, -16.f),
  m_draw_cheats(-160.f, 16.f, -96.f, 80.f),
  m_draw_debug(-80.f, 16.f, -16.f, 80.f),
  m_draw_escape(Vector{192.f, 14.f}, Sizef{48.f, 48.f}),
  // Textures
  m_tex_btn(Surface::from_file("/images/engine/mobile/button.png")),
  m_tex_btn_press(Surface::from_file("/images/engine/mobile/button_press.png")),
  m_tex_pause(Surface::from_file("/images/engine/mobile/pause.png")),
  m_tex_jump(Surface::from_file("/images/engine/mobile/jump.png")),
  m_tex_action(Surface::from_file("/images/engine/mobile/action.png")),
  m_tex_cheats(Surface::from_file("/images/engine/mobile/cheats.png")),
  m_tex_debug(Surface::from_file("/images/engine/mobile/debug.png")),
  m_tex_directions(Surface::from_file("/images/engine/mobile/direction.png")),
  m_tex_dir_hl_left(Surface::from_file("/images/engine/mobile/direction_hightlight_left.png")),
  m_tex_dir_hl_right(Surface::from_file("/images/engine/mobile/direction_hightlight_right.png")),
  m_tex_dir_hl_up(Surface::from_file("/images/engine/mobile/direction_hightlight_up.png")),
  m_tex_dir_hl_down(Surface::from_file("/images/engine/mobile/direction_hightlight_down.png")),
  m_screen_width(),
  m_screen_height(),
  m_mobile_controls_scale(),
  m_haptic(nullptr, SDL_HapticClose),
  m_haptic_timer(0)
{
#ifdef __ANDROID__
  SDL_InitSubSystem(SDL_INIT_HAPTIC | SDL_INIT_TIMER | SDL_INIT_SENSOR);

  // Init haptic feedback
  m_haptic.reset(SDL_HapticOpen(0));
  if (m_haptic)
  {
    if (!SDL_HapticRumbleSupported(m_haptic.get()))
      m_haptic.reset();

    if (m_haptic && SDL_HapticRumbleInit(m_haptic.get()) != 0)
    {
      log_warning << "Haptic device at index 0 couldn't be initialized: " << SDL_GetError() << std::endl;
      m_haptic.reset();
    }
  }

  // Init accelerometer
  int num_sensors = SDL_NumSensors();
  for (int i = 0; i < num_sensors; ++i)
  {
    if (SDL_SensorGetDeviceType(i) == SDL_SENSOR_ACCEL)
    {
      m_accelerometer = SDL_SensorOpen(i);
      if (m_accelerometer)
      {
        log_info << "Accelerometer opened for tilt controls" << std::endl;
      }
      else
      {
        log_warning << "Failed to open accelerometer: " << SDL_GetError() << std::endl;
      }
      break;
    }
  }

  if (!m_accelerometer)
  {
    log_warning << "No accelerometer found — tilt controls disabled, falling back to touch D-pad" << std::endl;
  }
#endif
}

MobileController::~MobileController()
{
  if (m_accelerometer)
  {
    SDL_SensorClose(m_accelerometer);
    m_accelerometer = nullptr;
  }
}

bool
MobileController::use_tilt() const
{
  return g_config->tilt_enabled && m_accelerometer != nullptr;
}

void
MobileController::buzz()
{
  if (!m_haptic || !g_config->touch_haptic_feedback)
    return;

  if (m_haptic_timer == 0)
    SDL_HapticRumblePlay(m_haptic.get(), 0.5f, 2000);

  m_haptic_timer = SDL_AddTimer(30, [](Uint32 val, void* _data) -> Uint32 {
    MobileController* data = static_cast<MobileController*>(_data);
    SDL_HapticRumbleStop(data->m_haptic.get());
    data->m_haptic_timer = 0;
    return 0;
  }, this);
}

// ========== ACCELEROMETER READING ==========
void
MobileController::update_accelerometer()
{
  if (!m_accelerometer)
    return;

  float data[3]; // X, Y, Z in m/s²
  if (SDL_SensorGetData(m_accelerometer, data, 3) == 0)
  {
    // SDL accelerometer: X = lateral tilt, Y = forward/back, Z = gravity
    // When phone is landscape (game orientation):
    //   Tilt right → positive X → move RIGHT
    //   Tilt left  → negative X → move LEFT
    m_tilt_x = data[0] * m_tilt_sensitivity;

    // Apply dead zone for horizontal movement
    if (m_tilt_x > m_tilt_deadzone)
    {
      m_input.set(CONTROL_INT(RIGHT), true);
    }
    else if (m_tilt_x < -m_tilt_deadzone)
    {
      m_input.set(CONTROL_INT(LEFT), true);
    }

    // DOWN when phone tilted sharply forward (for ducking / butt jump)
    // Normal gravity ~9.8 on Y when flat; >8.0 means tilted forward
    if (data[1] > 8.0f)
    {
      m_input.set(CONTROL_INT(DOWN), true);
    }

    // UP when phone tilted sharply backward (for looking up / entering doors)
    if (data[1] < -2.0f)
    {
      m_input.set(CONTROL_INT(UP), true);
    }
  }
}

// ========== DRAW ==========
void
MobileController::draw(DrawingContext& context)
{
  if (!g_config->mobile_controls || !g_config->touch_controls_visible)
    return;

  if (m_screen_width != static_cast<int>(context.get_width()) ||
      m_screen_height != static_cast<int>(context.get_height()) ||
      m_mobile_controls_scale != g_config->m_mobile_controls_scale)
  {
    m_screen_width = static_cast<int>(context.get_width());
    m_screen_height = static_cast<int>(context.get_height());
    float width = static_cast<float>(m_screen_width);
    float height = static_cast<float>(m_screen_height);
    m_mobile_controls_scale = g_config->m_mobile_controls_scale;

#ifdef __ANDROID__
    const float BUTTON_SCALE = 0.4f * g_config->m_mobile_controls_scale;
#else
    const float BUTTON_SCALE = 0.2f * g_config->m_mobile_controls_scale;
#endif

    float btn_size = height * BUTTON_SCALE;

    // === JUMP BUTTON (bottom-right) ===
    m_rect_jump.set_size(btn_size * 1.3f, btn_size * 1.3f);
    m_rect_jump.set_pos(Vector(width - btn_size * 1.3f, height - btn_size * 1.3f));
    m_draw_jump = m_rect_jump.grown(-m_rect_jump.get_height() * 3 / 8);

    // === ACTION BUTTON (left of jump button) ===
    m_rect_action.set_size(btn_size, btn_size);
    m_rect_action.set_pos(Vector(width - 2.5f * btn_size, height - btn_size));
    m_draw_action = m_rect_action.grown(-m_rect_action.get_height() * 3 / 8);

    // === ESCAPE/PAUSE ===
    m_rect_escape.set_size(btn_size / 2, btn_size / 2);
    m_draw_escape = m_rect_escape.grown(-m_rect_escape.get_height() / 4);

    // === DEV BUTTONS ===
    m_rect_cheats.set_size(btn_size / 2, btn_size / 2);
    m_rect_cheats.set_pos(Vector(width - 2 * btn_size / 2, 0));
    m_draw_cheats = m_rect_cheats.grown(-m_rect_cheats.get_height() / 4);

    m_rect_debug.set_size(btn_size / 2, btn_size / 2);
    m_rect_debug.set_pos(Vector(width - btn_size / 2, 0));
    m_draw_debug = m_rect_debug.grown(-m_rect_debug.get_height() / 4);

    // D-pad fallback layout (always calculated, drawn only when tilt is off)
    float dpad_size = btn_size * 2.0f;
    m_rect_directions.set_size(dpad_size, dpad_size);
    m_rect_directions.set_pos(Vector(0.f, height - dpad_size));
    m_draw_directions = m_rect_directions.grown(-m_rect_directions.get_height() / 8);
  }

  PaintStyle translucent;
  translucent.set_alpha(0.5f);

  // Draw D-pad when tilt controls are not available
  if (!use_tilt())
  {
    context.color().draw_surface_scaled(m_tex_directions, m_draw_directions, LAYER_GUI + 99, translucent);
    if (m_input[CONTROL_INT(LEFT)])
      context.color().draw_surface_scaled(m_tex_dir_hl_left, m_draw_directions, LAYER_GUI + 99, translucent);
    if (m_input[CONTROL_INT(RIGHT)])
      context.color().draw_surface_scaled(m_tex_dir_hl_right, m_draw_directions, LAYER_GUI + 99, translucent);
    if (m_input[CONTROL_INT(UP)])
      context.color().draw_surface_scaled(m_tex_dir_hl_up, m_draw_directions, LAYER_GUI + 99, translucent);
    if (m_input[CONTROL_INT(DOWN)])
      context.color().draw_surface_scaled(m_tex_dir_hl_down, m_draw_directions, LAYER_GUI + 99, translucent);
  }

  // Draw ACTION button
  context.color().draw_surface_scaled(m_input[CONTROL_INT(ACTION)] ? m_tex_btn_press : m_tex_btn, m_draw_action, LAYER_GUI + 99, translucent);
  context.color().draw_surface_scaled(m_tex_action, m_draw_action, LAYER_GUI + 99, translucent);

  // Draw JUMP button (short press = short hop, long press = full arc)
  PaintStyle jump_style;
  jump_style.set_alpha(m_jump_held ? 0.8f : 0.5f);
  context.color().draw_surface_scaled(m_jump_held ? m_tex_btn_press : m_tex_btn, m_draw_jump, LAYER_GUI + 99, jump_style);
  context.color().draw_surface_scaled(m_tex_jump, m_draw_jump, LAYER_GUI + 99, jump_style);

  // Draw PAUSE button
  context.color().draw_surface_scaled(m_input[CONTROL_INT(ESCAPE)] ? m_tex_btn_press : m_tex_btn, m_draw_escape, LAYER_GUI + 99, translucent);
  context.color().draw_surface_scaled(m_tex_pause, m_draw_escape.grown(-m_draw_escape.get_height() / 8), LAYER_GUI + 99, translucent);

  // Dev mode buttons
  if (g_config->developer_mode)
  {
    context.color().draw_surface_scaled(m_input[CONTROL_INT(CHEAT_MENU)] ? m_tex_btn_press : m_tex_btn, m_draw_cheats, LAYER_GUI + 99, translucent);
    context.color().draw_surface_scaled(m_tex_cheats, m_draw_cheats, LAYER_GUI + 99, translucent);

    context.color().draw_surface_scaled(m_input[CONTROL_INT(DEBUG_MENU)] ? m_tex_btn_press : m_tex_btn, m_draw_debug, LAYER_GUI + 99, translucent);
    context.color().draw_surface_scaled(m_tex_debug, m_draw_debug, LAYER_GUI + 99, translucent);
  }
}

// ========== UPDATE — called every frame ==========
void
MobileController::update()
{
  if (!g_config->mobile_controls)
    return;

  m_input_last = m_input;
  m_input.reset();

  // Sync tilt config from game settings
  m_tilt_deadzone = g_config->tilt_deadzone;
  m_tilt_sensitivity = g_config->tilt_sensitivity;

  // 1. Read accelerometer for LEFT/RIGHT/DOWN/UP (if tilt enabled and available)
  if (use_tilt())
    update_accelerometer();

  // 2. Allow using on-screen controls with the mouse (for desktop testing)
  int x, y;
  auto buttons = SDL_GetMouseState(&x, &y);
  if ((buttons & SDL_BUTTON_LMASK) != 0)
  {
    activate_widget_at_pos(static_cast<float>(x), static_cast<float>(y));
  }

  // 3. Read finger positions for buttons
  for (auto& i : m_fingers)
  {
    activate_widget_at_pos(i.second.x, i.second.y);
  }

  // 4. Jump = held as long as finger stays on button
  if (m_jump_held)
  {
    m_input.set(CONTROL_INT(JUMP), true);
  }

  // 5. Haptic feedback on new button press
  for (size_t i = 0; i < static_cast<size_t>(Control::CONTROLCOUNT); ++i)
  {
    if (m_input[i] != m_input_last[i] && m_input[i] == true)
    {
      if (g_config->touch_just_directional &&
          !(i >= CONTROL_INT(LEFT) && i < CONTROL_INT(JUMP)))
      {
        continue;
      }
      buzz();
      break;
    }
  }
}

// ========== APPLY — push state to controller ==========
void
MobileController::apply(Controller& controller) const
{
  if (!g_config->mobile_controls)
    return;

  for (size_t i = 0; i < static_cast<size_t>(Control::CONTROLCOUNT); ++i)
    if (m_input[i] != m_input_last[i])
      controller.set_control(static_cast<Control>(i), static_cast<bool>(m_input[i]));

  // something is pressed
  if (m_input != 0)
  {
    controller.set_touchscreen(true);
  }
}

// ========== FINGER DOWN ==========
bool
MobileController::process_finger_down_event(const SDL_TouchFingerEvent& event)
{
  Vector pos(event.x * float(m_screen_width), event.y * float(m_screen_height));
  m_fingers[event.fingerId] = pos;

  // JUMP: held as long as finger stays on button (short press = short hop, long = full arc)
  if (m_rect_jump.contains(pos))
  {
    m_jump_held = true;
    m_jump_finger = event.fingerId;
    buzz();
    return true;
  }

  return m_rect_action.contains(pos) ||
    m_rect_escape.contains(pos) ||
    m_rect_item.contains(pos) ||
    (!use_tilt() && m_rect_directions.contains(pos)) ||
    (g_config->developer_mode && m_rect_cheats.contains(pos)) ||
    (g_config->developer_mode && m_rect_debug.contains(pos));
}

// ========== FINGER UP ==========
bool
MobileController::process_finger_up_event(const SDL_TouchFingerEvent& event)
{
  Vector pos(event.x * float(m_screen_width), event.y * float(m_screen_height));
  m_fingers.erase(event.fingerId);

  // If this is the jump finger releasing → stop the jump
  if (m_jump_held && event.fingerId == m_jump_finger)
  {
    m_jump_held = false;
    m_jump_finger = -1;
    return true;
  }

  return m_rect_jump.contains(pos) ||
    m_rect_action.contains(pos) ||
    m_rect_escape.contains(pos) ||
    m_rect_item.contains(pos) ||
    (!use_tilt() && m_rect_directions.contains(pos)) ||
    (g_config->developer_mode && m_rect_cheats.contains(pos)) ||
    (g_config->developer_mode && m_rect_debug.contains(pos));
}

// ========== FINGER MOTION ==========
bool
MobileController::process_finger_motion_event(const SDL_TouchFingerEvent& event)
{
  Vector pos(event.x * float(m_screen_width), event.y * float(m_screen_height));
  m_fingers[event.fingerId] = pos;

  // If jump finger slides off the button → release
  if (m_jump_held && event.fingerId == m_jump_finger)
  {
    if (!m_rect_jump.contains(pos))
    {
      m_jump_held = false;
      m_jump_finger = -1;
    }
    return true;
  }

  return m_rect_jump.contains(pos) ||
    m_rect_action.contains(pos) ||
    m_rect_escape.contains(pos) ||
    m_rect_item.contains(pos) ||
    (!use_tilt() && m_rect_directions.contains(pos)) ||
    (g_config->developer_mode && m_rect_cheats.contains(pos)) ||
    (g_config->developer_mode && m_rect_debug.contains(pos));
}

// ========== ACTIVATE WIDGET AT POSITION ==========
void
MobileController::activate_widget_at_pos(float x, float y)
{
  if (!g_config->mobile_controls)
    return;

  Vector pos(x, y);

  // ACTION button
  if (m_rect_action.contains(pos))
    m_input.set(CONTROL_INT(ACTION), true);

  // ITEM button
  if (m_rect_item.contains(pos))
    m_input.set(CONTROL_INT(ITEM), true);

  // ESCAPE/PAUSE button
  if (m_rect_escape.contains(pos))
    m_input.set(CONTROL_INT(ESCAPE), true);

  // Dev mode buttons
  if (g_config->developer_mode)
  {
    if (m_rect_cheats.contains(pos))
      m_input.set(CONTROL_INT(CHEAT_MENU), true);
    if (m_rect_debug.contains(pos))
      m_input.set(CONTROL_INT(DEBUG_MENU), true);
  }

  // D-pad fallback: detect direction zones when tilt is not active
  if (!use_tilt() && m_rect_directions.contains(pos))
  {
    float cx = (m_rect_directions.get_left() + m_rect_directions.get_right()) / 2.0f;
    float cy = (m_rect_directions.get_top() + m_rect_directions.get_bottom()) / 2.0f;
    float half_w = m_rect_directions.get_width() / 2.0f;
    float half_h = m_rect_directions.get_height() / 2.0f;
    float dx = (pos.x - cx) / half_w;
    float dy = (pos.y - cy) / half_h;

    const float threshold = 0.3f;
    if (dx < -threshold) m_input.set(CONTROL_INT(LEFT), true);
    if (dx > threshold) m_input.set(CONTROL_INT(RIGHT), true);
    if (dy < -threshold) m_input.set(CONTROL_INT(UP), true);
    if (dy > threshold) m_input.set(CONTROL_INT(DOWN), true);
  }

  // NOTE: Jump buttons are handled in process_finger_down/up for precise timing
}
