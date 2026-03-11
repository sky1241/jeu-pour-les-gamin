// SuperTux Multi — Input Dispatcher
// Copyright (C) 2026 sky1241
// GPL v3+

#include "network/input_dispatcher.hpp"

#include "control/controller.hpp"
#include "network/ws_server.hpp"

namespace network {

void
InputDispatcher::apply_to_controller(const PlayerInput& input, Controller& controller)
{
  // Joystick deadzone threshold
  constexpr float DEADZONE = 0.2f;

  // Horizontal movement: stick_x maps to LEFT/RIGHT
  controller.set_control(Control::LEFT, input.stick_x < -DEADZONE);
  controller.set_control(Control::RIGHT, input.stick_x > DEADZONE);

  // Vertical: stick_y maps to UP/DOWN
  // stick_y > 0 = up, stick_y < 0 = down
  controller.set_control(Control::UP, input.stick_y > DEADZONE);
  controller.set_control(Control::DOWN, input.stick_y < -DEADZONE);

  // Buttons
  controller.set_control(Control::JUMP, input.btn_a);
  controller.set_control(Control::ACTION, input.btn_b);
}

} // namespace network
