// SuperTux Multi — WebSocket Server
// Copyright (C) 2026 sky1241
// GPL v3+

#pragma once

#include <functional>
#include <map>
#include <memory>
#include <mutex>
#include <set>
#include <string>
#include <thread>

#include "network/player_input.hpp"
#include "network/protocol.hpp"

namespace ix { class WebSocketServer; class WebSocket; }

namespace network {

class WsServer final
{
public:
  WsServer();
  ~WsServer();

  /// Start listening on WS_PORT in a background thread.
  bool start();

  /// Stop the server and join the thread.
  void stop();

  /// Thread-safe: get a snapshot of all connected players' inputs.
  std::map<std::string, PlayerInput> get_all_inputs() const;

  /// Thread-safe: get the list of connected player IDs (in join order).
  std::vector<std::string> get_player_ids() const;

  /// Thread-safe: get player count.
  int get_player_count() const;

  /// Send a state message to all connected phones.
  void broadcast_state(const std::string& level, const std::string& status);

  /// Send a victory message to all connected phones.
  void broadcast_victory(const std::string& winner_name);

  /// Check if the server is running.
  bool is_running() const { return m_running; }

  /// Check if a "start" message was received (from player 1).
  bool consume_start_request();

private:
  void on_message(ix::WebSocket& ws, const std::string& msg);
  void on_close(ix::WebSocket& ws);
  void broadcast(const std::string& json_str);
  std::string make_state_json(const std::string& level, const std::string& status) const;

private:
  std::unique_ptr<ix::WebSocketServer> m_server;

  mutable std::mutex m_mutex;
  std::map<std::string, PlayerInput> m_inputs;         // player_id -> input
  std::vector<std::string> m_player_order;             // join order
  std::map<ix::WebSocket*, std::string> m_ws_to_player; // raw ptr -> player_id

  bool m_start_requested;
  bool m_running;

  WsServer(const WsServer&) = delete;
  WsServer& operator=(const WsServer&) = delete;
};

} // namespace network
