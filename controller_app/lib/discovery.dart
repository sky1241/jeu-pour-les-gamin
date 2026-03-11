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
  Timer? _timer;
  final void Function(DiscoveredServer server)? onFound;

  Discovery({this.onFound});

  Future<void> startListening() async {
    // Bind to UDP port 9877 to receive discovery broadcasts
    _socket = await RawDatagramSocket.bind(
      InternetAddress.anyIPv4,
      9877,
      reuseAddress: true,
    );

    _socket!.broadcastEnabled = true;

    _socket!.listen((event) {
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
    _timer?.cancel();
    _socket?.close();
    _socket = null;
  }
}
