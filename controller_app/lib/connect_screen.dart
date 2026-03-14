import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'ws_client.dart';
import 'discovery.dart';
import 'controller_screen.dart';

class ConnectScreen extends StatefulWidget {
  const ConnectScreen({super.key});

  @override
  State<ConnectScreen> createState() => _ConnectScreenState();
}

class _ConnectScreenState extends State<ConnectScreen> with SingleTickerProviderStateMixin {
  final _nameController = TextEditingController(text: 'Player');
  final _ipController = TextEditingController();

  bool _searching = true;
  bool _connecting = false;
  bool _showManual = false;
  String? _error;
  String? _foundIp;

  Discovery? _discovery;
  late AnimationController _pulseController;

  @override
  void initState() {
    super.initState();
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 2),
    )..repeat();
    // Load saved name first, then auto-connect
    _init();
  }

  Future<void> _init() async {
    // Restore player name from last session
    final prefs = await SharedPreferences.getInstance();
    final savedName = prefs.getString('player_name') ?? '';
    if (savedName.isNotEmpty && mounted) {
      setState(() => _nameController.text = savedName);
    }
    if (!mounted) return;
    // Try localhost first (SuperTux on same device), then UDP discovery
    _tryLocalhostThenDiscover();
  }

  Future<void> _saveName(String name) async {
    final prefs = await SharedPreferences.getInstance();
    prefs.setString('player_name', name);
  }

  Future<void> _tryLocalhostThenDiscover() async {
    WsClient? client;
    try {
      final name = _nameController.text.trim().isEmpty ? 'Player' : _nameController.text.trim();
      client = WsClient(host: '127.0.0.1', port: 9876, playerName: name);
      await client.connect().timeout(const Duration(seconds: 2));
      if (!mounted) { client.disconnect(); return; }
      await _saveName(name);
      if (!mounted) { client.disconnect(); return; }
      Navigator.of(context).pushReplacement(
        MaterialPageRoute(builder: (_) => ControllerScreen(client: client!)),
      );
      return;
    } catch (_) {
      client?.disconnect(); // close partially-opened channel
    }
    if (!mounted) return;
    _startDiscovery();
  }

  @override
  void dispose() {
    _discovery?.stop();
    _pulseController.dispose();
    _nameController.dispose();
    _ipController.dispose();
    super.dispose();
  }

  void _startDiscovery() {
    _discovery?.stop();
    _discovery = Discovery(
      onFound: (server) {
        if (!mounted || _connecting) return;
        _discovery?.stop();
        setState(() {
          _searching = false;
          _foundIp = server.ip;
        });
        _autoConnect(server.ip, server.port);
      },
    );
    _discovery!.startListening();
  }

  Future<void> _autoConnect(String ip, int port) async {
    final name = _nameController.text.trim().isEmpty ? 'Player' : _nameController.text.trim();

    setState(() {
      _connecting = true;
      _error = null;
    });

    WsClient? client;
    try {
      client = WsClient(host: ip, port: port, playerName: name);
      await client.connect().timeout(const Duration(seconds: 5));

      if (!mounted) { client.disconnect(); return; }
      await _saveName(name);
      if (!mounted) { client.disconnect(); return; }
      Navigator.of(context).pushReplacement(
        MaterialPageRoute(
          builder: (_) => ControllerScreen(client: client!),
        ),
      );
    } catch (e) {
      client?.disconnect(); // close partially-opened channel
      if (!mounted) return;
      setState(() {
        _connecting = false;
        _searching = true;
        _error = 'Connection failed, retrying...';
      });
      _startDiscovery();
    }
  }

  Future<void> _manualConnect() async {
    final ip = _ipController.text.trim();
    if (ip.isEmpty) {
      setState(() => _error = 'Enter an IP address');
      return;
    }
    _discovery?.stop();
    await _autoConnect(ip, 9876);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SizedBox(
            width: 400,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Text(
                  'SuperTux Controller',
                  style: TextStyle(
                    fontSize: 28,
                    fontWeight: FontWeight.bold,
                    color: Colors.white,
                  ),
                ),
                const SizedBox(height: 24),

                // Name field
                TextField(
                  controller: _nameController,
                  decoration: const InputDecoration(
                    labelText: 'Your name',
                    border: OutlineInputBorder(),
                    prefixIcon: Icon(Icons.person),
                  ),
                ),
                const SizedBox(height: 24),

                // Search / connecting status
                if (_searching || _connecting) ...[
                  AnimatedBuilder(
                    animation: _pulseController,
                    builder: (context, child) {
                      final scale = 1.0 + 0.15 * _pulseController.value;
                      return Transform.scale(
                        scale: scale,
                        child: Icon(
                          _connecting ? Icons.wifi : Icons.wifi_find,
                          size: 48,
                          color: _connecting
                              ? const Color(0xFF4CAF50)
                              : Colors.white54,
                        ),
                      );
                    },
                  ),
                  const SizedBox(height: 12),
                  Text(
                    _connecting
                        ? 'Connecting...'
                        : 'Searching for SuperTux on the network...',
                    style: const TextStyle(
                      color: Colors.white70,
                      fontSize: 16,
                    ),
                  ),
                ],

                if (_foundIp != null && !_searching && !_connecting) ...[
                  const Icon(Icons.check_circle, size: 48, color: Color(0xFF4CAF50)),
                  const SizedBox(height: 8),
                  Text(
                    'Found server at $_foundIp',
                    style: const TextStyle(color: Colors.white70),
                  ),
                ],

                if (_error != null) ...[
                  const SizedBox(height: 12),
                  Text(_error!, style: const TextStyle(color: Colors.orangeAccent)),
                ],

                const SizedBox(height: 24),

                // Manual IP toggle
                TextButton(
                  onPressed: () => setState(() => _showManual = !_showManual),
                  child: Text(
                    _showManual ? 'Hide manual connection' : 'Enter IP manually',
                    style: const TextStyle(color: Colors.white38, fontSize: 13),
                  ),
                ),

                if (_showManual) ...[
                  const SizedBox(height: 8),
                  TextField(
                    controller: _ipController,
                    decoration: const InputDecoration(
                      labelText: 'Server IP (e.g. 192.168.1.42)',
                      border: OutlineInputBorder(),
                      prefixIcon: Icon(Icons.wifi),
                      isDense: true,
                    ),
                    keyboardType: TextInputType.number,
                  ),
                  const SizedBox(height: 8),
                  SizedBox(
                    width: double.infinity,
                    height: 44,
                    child: ElevatedButton(
                      onPressed: _connecting ? null : _manualConnect,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFF4CAF50),
                      ),
                      child: const Text('Connect', style: TextStyle(fontSize: 16)),
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}
