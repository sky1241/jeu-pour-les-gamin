// SuperTux Multi — UDP Discovery Broadcaster
// Copyright (C) 2026 sky1241
// GPL v3+

#include "network/udp_discovery.hpp"

#include <chrono>
#include <cstring>

#ifdef _WIN32
#  include <winsock2.h>
#  include <ws2tcpip.h>
#  pragma comment(lib, "ws2_32.lib")
   using socket_t = SOCKET;
#  define CLOSE_SOCKET closesocket
#  define INVALID_SOCK INVALID_SOCKET
#else
#  include <unistd.h>
#  include <sys/socket.h>
#  include <netinet/in.h>
#  include <arpa/inet.h>
#  include <ifaddrs.h>
   using socket_t = int;
#  define CLOSE_SOCKET close
#  define INVALID_SOCK (-1)
#endif

#include <nlohmann/json.hpp>

#include "network/protocol.hpp"
#include "util/log.hpp"

using json = nlohmann::json;

namespace network {

UdpDiscovery::UdpDiscovery() :
  m_running(false),
  m_thread()
{
}

UdpDiscovery::~UdpDiscovery()
{
  stop();
}

bool
UdpDiscovery::start()
{
  if (m_running.load())
    return true;

#ifdef _WIN32
  WSADATA wsa;
  if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0)
  {
    log_warning << "[UDP] WSAStartup failed" << std::endl;
    return false;
  }
#endif

  m_running.store(true);
  m_thread = std::thread(&UdpDiscovery::broadcast_loop, this);

  log_info << "[UDP] Discovery broadcaster started on port " << UDP_DISCOVERY_PORT << std::endl;
  return true;
}

void
UdpDiscovery::stop()
{
  if (!m_running.load())
    return;

  m_running.store(false);
  if (m_thread.joinable())
    m_thread.join();

  log_info << "[UDP] Discovery broadcaster stopped" << std::endl;
}

void
UdpDiscovery::broadcast_loop()
{
  socket_t sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
  if (sock == INVALID_SOCK)
  {
    log_warning << "[UDP] Failed to create socket" << std::endl;
    m_running.store(false);
    return;
  }

  // Enable broadcast
  int broadcast_enable = 1;
  setsockopt(sock, SOL_SOCKET, SO_BROADCAST,
             reinterpret_cast<const char*>(&broadcast_enable),
             sizeof(broadcast_enable));

  struct sockaddr_in dest;
  std::memset(&dest, 0, sizeof(dest));
  dest.sin_family = AF_INET;
  dest.sin_port = htons(UDP_DISCOVERY_PORT);
  dest.sin_addr.s_addr = htonl(INADDR_BROADCAST);

  while (m_running.load())
  {
    std::string local_ip = get_local_ip();

    json j;
    j["type"] = MSG_DISCOVER;
    j["ip"] = local_ip;
    j["port"] = WS_PORT;
    std::string msg = j.dump();

    sendto(sock, msg.c_str(), static_cast<int>(msg.size()), 0,
           reinterpret_cast<struct sockaddr*>(&dest), sizeof(dest));

    // Sleep 1 second between broadcasts, checking for stop every 100ms
    for (int i = 0; i < 10 && m_running.load(); ++i)
    {
      std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }
  }

  CLOSE_SOCKET(sock);
}

std::string
UdpDiscovery::get_local_ip() const
{
#ifdef _WIN32
  // Create a UDP socket and "connect" to a public IP to find local address
  socket_t sock = socket(AF_INET, SOCK_DGRAM, 0);
  if (sock == INVALID_SOCKET)
    return "127.0.0.1";

  struct sockaddr_in serv;
  std::memset(&serv, 0, sizeof(serv));
  serv.sin_family = AF_INET;
  serv.sin_port = htons(80);
  inet_pton(AF_INET, "8.8.8.8", &serv.sin_addr);

  if (connect(sock, reinterpret_cast<struct sockaddr*>(&serv), sizeof(serv)) != 0)
  {
    closesocket(sock);
    return "127.0.0.1";
  }

  struct sockaddr_in local;
  int addrlen = sizeof(local);
  getsockname(sock, reinterpret_cast<struct sockaddr*>(&local), &addrlen);
  closesocket(sock);

  char buf[INET_ADDRSTRLEN];
  inet_ntop(AF_INET, &local.sin_addr, buf, sizeof(buf));
  return std::string(buf);
#else
  struct ifaddrs* addrs = nullptr;
  if (getifaddrs(&addrs) != 0)
    return "127.0.0.1";

  // Two-pass selection:
  //   Pass 1: prefer wlan* / eth* (the real WiFi/Ethernet interface)
  //   Pass 2: fallback to any non-loopback, non-p2p interface
  // This prevents Smart View / Wi-Fi Direct (p2p0, 192.168.49.x) from being
  // selected instead of the actual WiFi address (wlan0).
  std::string preferred  = "127.0.0.1";  // wlan/eth
  std::string fallback   = "127.0.0.1";  // any other non-loopback

  for (struct ifaddrs* ifa = addrs; ifa != nullptr; ifa = ifa->ifa_next)
  {
    if (!ifa->ifa_addr || ifa->ifa_addr->sa_family != AF_INET)
      continue;

    auto* sa = reinterpret_cast<struct sockaddr_in*>(ifa->ifa_addr);
    char buf[INET_ADDRSTRLEN];
    inet_ntop(AF_INET, &sa->sin_addr, buf, sizeof(buf));
    std::string ip(buf);

    if (ip == "127.0.0.1")
      continue;

    std::string iface(ifa->ifa_name);

    // Skip Wi-Fi Direct / Miracast / dummy interfaces
    if (iface.substr(0, 3) == "p2p" ||
        iface.substr(0, 5) == "dummy" ||
        iface.substr(0, 2) == "lo")
      continue;

    if (preferred == "127.0.0.1" &&
        (iface.substr(0, 4) == "wlan" || iface.substr(0, 3) == "eth"))
    {
      preferred = ip;
    }
    else if (fallback == "127.0.0.1")
    {
      fallback = ip;
    }
  }

  freeifaddrs(addrs);
  return (preferred != "127.0.0.1") ? preferred : fallback;
#endif
}

} // namespace network
