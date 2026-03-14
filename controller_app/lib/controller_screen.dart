import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
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
  String? _victoryWinner;
  bool _reconnecting = false;
  int _reconnectAttempts = 0;
  static const int _maxReconnectAttempts = 12; // ~24s before giving up

  @override
  void initState() {
    super.initState();
    widget.client.onDisconnect = () {
      if (!mounted) return;
      _tryReconnect();
    };
    widget.client.onVictory = (winner) {
      if (!mounted) return;
      HapticFeedback.heavyImpact();
      setState(() => _victoryWinner = winner);
    };
    // Clear victory overlay when server returns to lobby state
    widget.client.onStateUpdate = (players) {
      if (!mounted) return;
      if (_victoryWinner != null && !widget.client.gameStarted) {
        setState(() => _victoryWinner = null);
      }
    };
  }

  Future<void> _tryReconnect() async {
    if (!mounted) return;
    setState(() {
      _reconnecting = true;
      _reconnectAttempts = 0;
    });
    while (_reconnectAttempts < _maxReconnectAttempts) {
      _reconnectAttempts++;
      await Future.delayed(const Duration(seconds: 2));
      if (!mounted) return;
      try {
        await widget.client.connect().timeout(const Duration(seconds: 5));
        if (!mounted) return;
        setState(() {
          _reconnecting = false;
          _reconnectAttempts = 0;
        });
        return;
      } catch (_) {
        if (!mounted) return; // widget disposed during failed attempt — stop loop
      }
    }
    // Exhausted retries — fall back to connect screen
    if (mounted) Navigator.of(context).pop();
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
    if (pressed) HapticFeedback.lightImpact();
    _updateInput();
  }

  void _onBtnBChanged(bool pressed) {
    _btnB = pressed;
    if (pressed) HapticFeedback.lightImpact();
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
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Stack(
            children: [
              // Connection indicator — top center
              Positioned(
                top: 8,
                left: 0,
                right: 0,
                child: Center(
                  child: Container(
                    constraints: const BoxConstraints(minHeight: 48),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 8,
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
                        color: Colors.white,
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                ),
              ),

              // Joystick on the left
              Positioned(
                left: 16,
                top: 0,
                bottom: 0,
                child: Center(
                  child: JoystickWidget(
                    size: 200,
                    onChanged: _onJoystickChanged,
                  ),
                ),
              ),

              // Buttons on the right
              Positioned(
                right: 24,
                top: 0,
                bottom: 0,
                child: Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      ActionButton(
                        label: 'B',
                        color: Colors.orange,
                        size: 80,
                        onChanged: _onBtnBChanged,
                      ),
                      const SizedBox(height: 24),
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

              // Reconnecting overlay
              if (_reconnecting)
                Positioned.fill(
                  child: Container(
                    color: Colors.black54,
                    alignment: Alignment.center,
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const CircularProgressIndicator(color: Colors.white),
                        const SizedBox(height: 16),
                        const Text(
                          'Reconnecting...',
                          style: TextStyle(color: Colors.white, fontSize: 20),
                        ),
                      ],
                    ),
                  ),
                ),

              // Victory overlay
              if (_victoryWinner != null)
                Positioned.fill(
                  child: Container(
                    color: Colors.black54,
                    alignment: Alignment.center,
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Text(
                          '🏆',
                          style: TextStyle(fontSize: 64),
                        ),
                        const SizedBox(height: 16),
                        Text(
                          '$_victoryWinner wins!',
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 32,
                            fontWeight: FontWeight.bold,
                          ),
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
