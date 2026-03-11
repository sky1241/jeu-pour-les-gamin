import 'package:flutter/material.dart';

class ActionButton extends StatefulWidget {
  final String label;
  final Color color;
  final double size;
  final void Function(bool pressed) onChanged;

  const ActionButton({
    super.key,
    required this.label,
    required this.color,
    this.size = 80,
    required this.onChanged,
  });

  @override
  State<ActionButton> createState() => _ActionButtonState();
}

class _ActionButtonState extends State<ActionButton> {
  bool _pressed = false;

  void _setPressed(bool value) {
    if (_pressed == value) return;
    setState(() => _pressed = value);
    widget.onChanged(value);
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapDown: (_) => _setPressed(true),
      onTapUp: (_) => _setPressed(false),
      onTapCancel: () => _setPressed(false),
      child: Container(
        width: widget.size,
        height: widget.size,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          color: _pressed
              ? widget.color.withAlpha(230)
              : widget.color.withAlpha(150),
          border: Border.all(
            color: Colors.white.withAlpha(_pressed ? 230 : 100),
            width: 3,
          ),
          boxShadow: _pressed
              ? []
              : [
                  BoxShadow(
                    color: widget.color.withAlpha(100),
                    blurRadius: 12,
                    spreadRadius: 2,
                  ),
                ],
        ),
        child: Center(
          child: Text(
            widget.label,
            style: TextStyle(
              color: Colors.white,
              fontSize: widget.size * 0.35,
              fontWeight: FontWeight.bold,
            ),
          ),
        ),
      ),
    );
  }
}
