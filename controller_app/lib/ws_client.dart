import 'dart:async';
import 'dart:convert';
import 'dart:math';
import 'package:web_socket_channel/web_socket_channel.dart';

String _generatePlayerId() {
  final rng = Random.secure();
  final bytes = List<int>.generate(16, (_) => rng.nextInt(256));
  bytes[6] = (bytes[6] & 0x0f) | 0x40; // UUID version 4
  bytes[8] = (bytes[8] & 0x3f) | 0x80; // UUID variant
  final hex = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).toList();
  return '${hex.sublist(0, 4).join()}-${hex.sublist(4, 6).join()}-'
      '${hex.sublist(6, 8).join()}-${hex.sublist(8, 10).join()}-'
      '${hex.sublist(10, 16).join()}';
}

class WsClient {
  final String host;
  final int port;
  final String playerName;

  // Generated once at creation — never changes across reconnects
  final String playerId;

  WebSocketChannel? _channel;
  StreamSubscription? _streamSub;
  String? color;
  bool connected = false;

  // Lobby state from server
  final List<Map<String, dynamic>> players = [];
  bool gameStarted = false;

  // Callbacks
  void Function(List<Map<String, dynamic>> players)? onStateUpdate;
  void Function()? onGameStart;
  void Function()? onDisconnect;
  void Function(String winnerName)? onVictory;

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
  }) : playerId = _generatePlayerId();

  Future<void> connect() async {
    // Cancel any previous timer before reconnecting
    _sendTimer?.cancel();
    _sendTimer = null;
    // Reset state so reconnect always sends a full first frame
    _lastStickX = -999;
    _lastStickY = -999;
    _lastBtnA = false;
    _lastBtnB = false;
    gameStarted = false;
    players.clear();
    color = null;

    // Close any stale channel from a previous attempt before creating a new one
    _channel?.sink.close();
    final uri = Uri.parse('ws://$host:$port');
    _channel = WebSocketChannel.connect(uri);

    await _channel!.ready;

    connected = true;

    _streamSub?.cancel();
    _streamSub = _channel!.stream.listen(
      _onMessage,
      onDone: _onDone,
      onError: (e) => _onDone(),
    );

    // FIXED: send player_id and player_name (server rejects if player_id empty)
    _send({
      'type': 'join',
      'player_id': playerId,
      'player_name': playerName,
    });

    _sendTimer = Timer.periodic(
      const Duration(milliseconds: 16), // ~60 Hz
      (_) => _sendInputIfChanged(),
    );
  }

  void _onMessage(dynamic raw) {
    final Map<String, dynamic> msg;
    try {
      msg = jsonDecode(raw as String) as Map<String, dynamic>;
    } catch (_) {
      return; // ignore malformed JSON
    }
    final type = msg['type'] as String?;

    switch (type) {
      case 'state':
        final list = msg['players'] as List<dynamic>? ?? [];
        players
          ..clear()
          ..addAll(list.cast<Map<String, dynamic>>());

        // find own color by looking up our playerId in the players list
        final self = players.where((p) => p['player_id'] == playerId).firstOrNull;
        if (self != null) {
          color = self['color'] as String?;
        }

        // Keep gameStarted in sync with server status
        final status = msg['status'] as String?;
        if (status == 'lobby') gameStarted = false;
        if (status == 'playing') gameStarted = true;

        onStateUpdate?.call(players);

      case 'start':
        gameStarted = true;
        onGameStart?.call();

      case 'victory':
        final winner = msg['winner_name'] as String? ?? 'Tux';
        onVictory?.call(winner);
    }
  }

  void _onDone() {
    if (!connected) return; // guard: onError + onDone both fire on stream error
    connected = false;
    _sendTimer?.cancel();
    _streamSub?.cancel();
    _streamSub = null;
    _channel = null;
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

    final qx = ((_stickX * 100).roundToDouble()) / 100;
    final qy = ((_stickY * 100).roundToDouble()) / 100;

    if (qx == _lastStickX &&
        qy == _lastStickY &&
        _btnA == _lastBtnA &&
        _btnB == _lastBtnB) {
      return;
    }

    _lastStickX = qx;
    _lastStickY = qy;
    _lastBtnA = _btnA;
    _lastBtnB = _btnB;

    // FIXED: include player_id so server can find the right input slot
    _send({
      'type': 'input',
      'player_id': playerId,
      'stick_x': qx,
      'stick_y': qy,
      'btn_a': _btnA,
      'btn_b': _btnB,
    });
  }

  void sendStart() {
    // FIXED: include player_id (only player 1 can start)
    _send({'type': 'start', 'player_id': playerId});
  }

  void _send(Map<String, dynamic> msg) {
    if (_channel == null) return;
    _channel!.sink.add(jsonEncode(msg));
  }

  void disconnect() {
    _sendTimer?.cancel();
    _streamSub?.cancel();
    _streamSub = null;
    _channel?.sink.close();
    _channel = null;
    connected = false;
  }
}
