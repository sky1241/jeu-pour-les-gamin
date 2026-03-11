import 'dart:async';
import 'dart:convert';
import 'package:web_socket_channel/web_socket_channel.dart';

class WsClient {
  final String host;
  final int port;
  final String playerName;

  WebSocketChannel? _channel;
  String? playerId;
  String? color;
  bool connected = false;

  // Lobby state from server
  final List<Map<String, dynamic>> players = [];
  bool gameStarted = false;

  // Callbacks
  void Function(List<Map<String, dynamic>> players)? onStateUpdate;
  void Function()? onGameStart;
  void Function()? onDisconnect;

  // Delta encoding: only send when input changes
  double _lastStickX = 0;
  double _lastStickY = 0;
  bool _lastBtnA = false;
  bool _lastBtnB = false;

  // 60 Hz send timer
  Timer? _sendTimer;
  double _stickX = 0;
  double _stickY = 0;
  bool _btnA = false;
  bool _btnB = false;

  WsClient({
    required this.host,
    required this.port,
    required this.playerName,
  });

  Future<void> connect() async {
    final uri = Uri.parse('ws://$host:$port');
    _channel = WebSocketChannel.connect(uri);

    // Wait for the connection to be ready
    await _channel!.ready;

    connected = true;

    // Listen for messages
    _channel!.stream.listen(
      _onMessage,
      onDone: _onDone,
      onError: (e) => _onDone(),
    );

    // Send join message
    _send({
      'type': 'join',
      'name': playerName,
    });

    // Start 60 Hz input send loop
    _sendTimer = Timer.periodic(
      const Duration(milliseconds: 16), // ~60 Hz
      (_) => _sendInputIfChanged(),
    );
  }

  void _onMessage(dynamic raw) {
    final msg = jsonDecode(raw as String) as Map<String, dynamic>;
    final type = msg['type'] as String?;

    switch (type) {
      case 'state':
        playerId = msg['your_id'] as String?;
        color = msg['your_color'] as String?;
        final list = msg['players'] as List<dynamic>? ?? [];
        players
          ..clear()
          ..addAll(list.cast<Map<String, dynamic>>());
        onStateUpdate?.call(players);

      case 'start':
        gameStarted = true;
        onGameStart?.call();
    }
  }

  void _onDone() {
    connected = false;
    _sendTimer?.cancel();
    onDisconnect?.call();
  }

  void updateInput({
    required double stickX,
    required double stickY,
    required bool btnA,
    required bool btnB,
  }) {
    _stickX = stickX;
    _stickY = stickY;
    _btnA = btnA;
    _btnB = btnB;
  }

  void _sendInputIfChanged() {
    if (!connected) return;

    // Quantize stick to reduce noise (2 decimal places)
    final qx = ((_stickX * 100).roundToDouble()) / 100;
    final qy = ((_stickY * 100).roundToDouble()) / 100;

    if (qx == _lastStickX &&
        qy == _lastStickY &&
        _btnA == _lastBtnA &&
        _btnB == _lastBtnB) {
      return; // No change, skip
    }

    _lastStickX = qx;
    _lastStickY = qy;
    _lastBtnA = _btnA;
    _lastBtnB = _btnB;

    _send({
      'type': 'input',
      'stick_x': qx,
      'stick_y': qy,
      'btn_a': _btnA,
      'btn_b': _btnB,
    });
  }

  void sendStart() {
    _send({'type': 'start'});
  }

  void _send(Map<String, dynamic> msg) {
    if (_channel == null) return;
    _channel!.sink.add(jsonEncode(msg));
  }

  void disconnect() {
    _sendTimer?.cancel();
    _channel?.sink.close();
    connected = false;
  }
}
