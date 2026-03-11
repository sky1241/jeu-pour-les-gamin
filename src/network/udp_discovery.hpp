// SuperTux Multi — UDP Discovery Broadcaster
// Copyright (C) 2026 sky1241
// GPL v3+

#pragma once

#include <atomic>
#include <string>
#include <thread>

namespace network {

/// Broadcasts a UDP discovery packet every second so phones on the LAN
/// can find the SuperTux server without entering an IP manually.
class UdpDiscovery final
{
public:
  UdpDiscovery();
  ~UdpDiscovery();

  /// Start broadcasting in a background thread.
  bool start();

  /// Stop broadcasting and join the thread.
  void stop();

  bool is_running() const { return m_running.load(); }

private:
  void broadcast_loop();
  std::string get_local_ip() const;

private:
  std::atomic<bool> m_running;
  std::thread m_thread;

  UdpDiscovery(const UdpDiscovery&) = delete;
  UdpDiscovery& operator=(const UdpDiscovery&) = delete;
};

} // namespace network
