import 'package:flutter/material.dart';
import 'ws_client.dart';
import 'joystick_widget.dart';
import 'action_button.dart';

class ControllerScreen extends StatefulWidget {
  final WsClient client;

  const ControllerScreen({super.key, required this.client});

  @override
  State<ControllerScreen> createState() => _ControllerScreenState();
}

class _ControllerScreenState extends State<ControllerScreen> {
  double _stickX = 0;
  double _stickY = 0;
  bool _btnA = false;
  bool _btnB = false;

  @override
  void initState() {
    super.initState();
    widget.client.onDisconnect = () {
      if (!mounted) return;
      Navigator.of(context).pop();
    };
  }

  @override
  void dispose() {
    widget.client.disconnect();
    super.dispose();
  }

  void _onJoystickChanged(double x, double y) {
    _stickX = x;
    _stickY = y;
    _updateInput();
  }

  void _onBtnAChanged(bool pressed) {
    _btnA = pressed;
    _updateInput();
  }

  void _onBtnBChanged(bool pressed) {
    _btnB = pressed;
    _updateInput();
  }

  void _updateInput() {
    widget.client.updateInput(
      stickX: _stickX,
      stickY: _stickY,
      btnA: _btnA,
      btnB: _btnB,
    );
  }

  @override
  Widget build(BuildContext context) {
    final playerColor = widget.client.color;
    Color accent = const Color(0xFF4CAF50);
    if (playerColor != null && playerColor.startsWith('#')) {
      final hex = playerColor.replaceFirst('#', '');
      accent = Color(int.parse('FF$hex', radix: 16));
    }

    return Scaffold(
      body: SafeArea(
        // UX: Content inside safe areas (Infernal Wheel rule)
        child: Padding(
          // UX: 16dp lateral margins (Material Design 3 / Apple HIG compact)
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Stack(
            children: [
              // Connection indicator — top center
              Positioned(
                top: 8, // 8dp spacing token
                left: 0,
                right: 0,
                child: Center(
                  child: Container(
                    // UX: 48dp min touch target height
                    constraints: const BoxConstraints(minHeight: 48),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16, // 16dp horizontal padding
                      vertical: 8,    // 8dp vertical padding
                    ),
                    decoration: BoxDecoration(
                      color: accent.withAlpha(50),
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: accent.withAlpha(100)),
                    ),
                    alignment: Alignment.center,
                    child: Text(
                      widget.client.playerName,
                      style: TextStyle(
                        color: Colors.white, // UX: high contrast on dark bg
                        fontSize: 16, // UX: Body text >= 16sp
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                ),
              ),

              // Joystick on the left
              Positioned(
                left: 16, // 16dp from edge
                top: 0,
                bottom: 0,
                child: Center(
                  child: JoystickWidget(
                    size: 200,
                    onChanged: _onJoystickChanged,
                  ),
                ),
              ),

              // Buttons on the right — 24dp spacing between buttons
              Positioned(
                right: 24, // 24dp from edge
                top: 0,
                bottom: 0,
                child: Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      // B button (action) on top — 80dp (>= 48dp touch target)
                      ActionButton(
                        label: 'B',
                        color: Colors.orange,
                        size: 80,
                        onChanged: _onBtnBChanged,
                      ),
                      const SizedBox(height: 24), // 24dp spacing token
                      // A button (jump) on bottom — 96dp (primary action, larger)
                      ActionButton(
                        label: 'A',
                        color: Colors.green,
                        size: 96,
                        onChanged: _onBtnAChanged,
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
