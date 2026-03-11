import 'dart:math';
import 'package:flutter/material.dart';

class JoystickWidget extends StatefulWidget {
  final double size;
  final void Function(double x, double y) onChanged;

  const JoystickWidget({
    super.key,
    this.size = 200,
    required this.onChanged,
  });

  @override
  State<JoystickWidget> createState() => _JoystickWidgetState();
}

class _JoystickWidgetState extends State<JoystickWidget> {
  double _thumbX = 0;
  double _thumbY = 0;

  void _updateThumb(Offset localPos) {
    final center = widget.size / 2;
    final maxRadius = center - 30; // thumb radius ~30

    double dx = localPos.dx - center;
    double dy = localPos.dy - center;
    final dist = sqrt(dx * dx + dy * dy);

    if (dist > maxRadius) {
      dx = dx / dist * maxRadius;
      dy = dy / dist * maxRadius;
    }

    setState(() {
      _thumbX = dx / maxRadius; // -1..1
      _thumbY = -dy / maxRadius; // -1..1 (up = positive)
    });

    widget.onChanged(_thumbX, _thumbY);
  }

  void _resetThumb() {
    setState(() {
      _thumbX = 0;
      _thumbY = 0;
    });
    widget.onChanged(0, 0);
  }

  @override
  Widget build(BuildContext context) {
    final size = widget.size;
    final center = size / 2;
    final maxRadius = center - 30;

    return GestureDetector(
      onPanStart: (d) => _updateThumb(d.localPosition),
      onPanUpdate: (d) => _updateThumb(d.localPosition),
      onPanEnd: (_) => _resetThumb(),
      onPanCancel: _resetThumb,
      child: SizedBox(
        width: size,
        height: size,
        child: CustomPaint(
          painter: _JoystickPainter(
            thumbOffsetX: _thumbX * maxRadius,
            thumbOffsetY: -_thumbY * maxRadius,
          ),
        ),
      ),
    );
  }
}

class _JoystickPainter extends CustomPainter {
  final double thumbOffsetX;
  final double thumbOffsetY;

  _JoystickPainter({
    required this.thumbOffsetX,
    required this.thumbOffsetY,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final baseRadius = size.width / 2 - 10;

    // Base circle
    final basePaint = Paint()
      ..color = Colors.white.withAlpha(25)
      ..style = PaintingStyle.fill;
    canvas.drawCircle(center, baseRadius, basePaint);

    // Base border
    final borderPaint = Paint()
      ..color = Colors.white.withAlpha(76)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2;
    canvas.drawCircle(center, baseRadius, borderPaint);

    // Crosshair lines
    final crossPaint = Paint()
      ..color = Colors.white.withAlpha(38)
      ..strokeWidth = 1;
    canvas.drawLine(
      Offset(center.dx - baseRadius, center.dy),
      Offset(center.dx + baseRadius, center.dy),
      crossPaint,
    );
    canvas.drawLine(
      Offset(center.dx, center.dy - baseRadius),
      Offset(center.dx, center.dy + baseRadius),
      crossPaint,
    );

    // Thumb
    final thumbCenter = Offset(
      center.dx + thumbOffsetX,
      center.dy + thumbOffsetY,
    );
    final thumbPaint = Paint()
      ..color = Colors.white.withAlpha(204)
      ..style = PaintingStyle.fill;
    canvas.drawCircle(thumbCenter, 28, thumbPaint);

    final thumbBorder = Paint()
      ..color = Colors.white
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2;
    canvas.drawCircle(thumbCenter, 28, thumbBorder);
  }

  @override
  bool shouldRepaint(covariant _JoystickPainter old) =>
      old.thumbOffsetX != thumbOffsetX || old.thumbOffsetY != thumbOffsetY;
}
