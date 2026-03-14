import 'dart:async';
import 'dart:convert';
import 'dart:io';

class DiscoveredServer {
  final String ip;
  final int port;

  DiscoveredServer({required this.ip, required this.port});
}

class Discovery {
  RawDatagramSocket? _socket;
  StreamSubscription? _sub;
  bool _stopped = false;
  final void Function(DiscoveredServer server)? onFound;

  Discovery({this.onFound});

  Future<void> startListening() async {
    _stopped = false;
    // Bind to UDP port 9877 to receive discovery broadcasts
    final socket = await RawDatagramSocket.bind(
      InternetAddress.anyIPv4,
      9877,
      reuseAddress: true,
    );
    // stop() may have been called while awaiting bind — close the orphaned socket
    if (_stopped) {
      socket.close();
      return;
    }
    _socket = socket;

    _socket!.broadcastEnabled = true;

    _sub?.cancel();
    _sub = _socket!.listen((event) {
      if (event == RawSocketEvent.read) {
        final datagram = _socket!.receive();
        if (datagram == null) return;

        try {
          final msg = jsonDecode(utf8.decode(datagram.data)) as Map<String, dynamic>;
          if (msg['type'] == 'discover') {
            final ip = msg['ip'] as String? ?? datagram.address.address;
            final port = msg['port'] as int? ?? 9876;
            onFound?.call(DiscoveredServer(ip: ip, port: port));
          }
        } catch (_) {
          // Ignore malformed packets
        }
      }
    });
  }

  void stop() {
    _stopped = true;
    _sub?.cancel();
    _sub = null;
    _socket?.close();
    _socket = null;
  }
}
