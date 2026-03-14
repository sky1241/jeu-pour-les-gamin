// SuperTux Multi — WebSocket Server
// Copyright (C) 2026 sky1241
// GPL v3+

#include "network/ws_server.hpp"

#include <algorithm>
#include <cmath>

#include <ixwebsocket/IXWebSocketServer.h>
#include <ixwebsocket/IXWebSocket.h>
#include <nlohmann/json.hpp>

#include "util/log.hpp"

using json = nlohmann::json;

namespace network {

WsServer::WsServer() :
  m_server(nullptr),
  m_mutex(),
  m_inputs(),
  m_player_order(),
  m_ws_to_player(),
  m_start_requested(false),
  m_running(false),
  m_game_level(""),
  m_game_status(STATUS_LOBBY)
{
}

WsServer::~WsServer()
{
  stop();
}

bool
WsServer::start()
{
  if (m_running)
    return true;

  m_server = std::make_unique<ix::WebSocketServer>(WS_PORT, "0.0.0.0");

  m_server->setOnClientMessageCallback(
    [this](std::shared_ptr<ix::ConnectionState> state,
           ix::WebSocket& ws,
           const ix::WebSocketMessagePtr& msg)
    {
      if (msg->type == ix::WebSocketMessageType::Message)
      {
        on_message(ws, msg->str);
      }
      else if (msg->type == ix::WebSocketMessageType::Close)
      {
        on_close(ws);
      }
      else if (msg->type == ix::WebSocketMessageType::Open)
      {
        log_info << "[WS] New connection from " << state->getRemoteIp() << std::endl;
      }
    }
  );

  auto res = m_server->listen();
  if (!res.first)
  {
    log_warning << "[WS] Failed to listen on port " << WS_PORT << ": " << res.second << std::endl;
    return false;
  }

  m_server->start();
  m_running = true;
  log_info << "[WS] Server started on port " << WS_PORT << std::endl;

  return true;
}

void
WsServer::stop()
{
  if (!m_running)
    return;

  if (m_server)
  {
    m_server->stop();
    m_server.reset();
  }
  m_running = false;
  log_info << "[WS] Server stopped" << std::endl;
}

void
WsServer::on_message(ix::WebSocket& ws, const std::string& msg)
{
  // Reject oversized messages to prevent OOM from malicious clients
  if (msg.size() > 2048)
    return;

  try
  {
    auto j = json::parse(msg);
    std::string type = j.value("type", "");

    // TV clients are passive listeners — reject player actions from them
    if (type != MSG_TV_CLIENT)
    {
      std::lock_guard<std::mutex> lock(m_mutex);
      if (m_tv_clients.count(&ws))
        return;
    }

    if (type == MSG_TV_CLIENT)
    {
      // TV browser (wasm) registers as a passive listener.
      // It receives relayed inputs + state updates but is NOT a player.
      //
      // NOTE: m_tv_clients tracks membership only (for on_close cleanup).
      // Relay is done via broadcast() which uses the server's shared_ptr list
      // internally — this avoids any raw-pointer use-after-free risk.
      std::string state_str;
      int tv_count = 0;
      {
        std::lock_guard<std::mutex> lock(m_mutex);
        m_tv_clients.insert(&ws);
        tv_count = static_cast<int>(m_tv_clients.size());

        // Build state JSON inline — do NOT call make_state_json() here
        // because that function also acquires m_mutex, causing a deadlock.
        json sj;
        sj["type"]   = MSG_STATE;
        sj["level"]  = m_game_level;
        sj["status"] = m_game_status;
        json parr = json::array();
        for (const auto& pid : m_player_order)
        {
          auto it = m_inputs.find(pid);
          if (it != m_inputs.end() && it->second.connected)
          {
            json p;
            p["player_id"]   = it->second.player_id;
            p["player_name"] = it->second.player_name;
            p["color"]       = it->second.color;
            parr.push_back(p);
          }
        }
        sj["players"] = parr;
        state_str = sj.dump();
      } // release mutex before sending

      ws.send(state_str);
      log_info << "[WS] TV client connected (" << tv_count << " total)" << std::endl;
    }
    else if (type == MSG_JOIN)
    {
      std::string pid = j.value("player_id", "");
      std::string pname = j.value("player_name", "Player");

      if (pid.empty())
        return;

      // Cap player_id to 64 chars (UUID is 36 — generous safety cap)
      if (pid.size() > 64)
        pid = pid.substr(0, 64);

      // Cap name to 32 chars to prevent broadcast bloat
      if (pname.size() > 32)
        pname = pname.substr(0, 32);

      std::string state_str;
      {
        std::lock_guard<std::mutex> lock(m_mutex);

        auto order_it = std::find(m_player_order.begin(), m_player_order.end(), pid);
        bool is_reconnect = (order_it != m_player_order.end());

        if (!is_reconnect)
        {
          // Count only currently connected players — disconnected slots can be reclaimed
          int connected_count = 0;
          for (const auto& order_pid : m_player_order)
          {
            auto inp_it = m_inputs.find(order_pid);
            if (inp_it != m_inputs.end() && inp_it->second.connected)
              connected_count++;
          }

          if (connected_count >= MAX_PLAYERS)
          {
            log_warning << "[WS] Max active players reached, rejecting " << pname << std::endl;
            return;
          }

          // All UUID slots occupied but we have room (some disconnected) — evict oldest disconnected
          if (static_cast<int>(m_player_order.size()) >= MAX_PLAYERS)
          {
            for (auto slot_it = m_player_order.begin(); slot_it != m_player_order.end(); ++slot_it)
            {
              auto inp_it = m_inputs.find(*slot_it);
              if (inp_it != m_inputs.end() && !inp_it->second.connected)
              {
                log_info << "[WS] Evicting stale slot: " << *slot_it << std::endl;
                m_inputs.erase(inp_it);
                m_player_order.erase(slot_it);
                break;
              }
            }
          }
        }

        m_ws_to_player[&ws] = pid;

        if (is_reconnect)
        {
          // Preserve original color and slot — only restore connected flag
          auto inp_it = m_inputs.find(pid);
          if (inp_it != m_inputs.end())
          {
            inp_it->second.connected = true;
            inp_it->second.player_name = pname;
          }
          int idx = static_cast<int>(std::distance(m_player_order.begin(), order_it));
          log_info << "[WS] Player reconnected: " << pname << " (" << pid << ") slot " << (idx + 1) << std::endl;
        }
        else
        {
          // Assign first color not already in use (handles gaps after evictions)
          std::set<std::string> used_colors;
          for (const auto& order_pid : m_player_order)
          {
            auto inp_it = m_inputs.find(order_pid);
            if (inp_it != m_inputs.end())
              used_colors.insert(inp_it->second.color);
          }
          int color_idx = 0;
          while (color_idx < MAX_PLAYERS && used_colors.count(PLAYER_COLORS[color_idx]))
            color_idx++;

          int idx = static_cast<int>(m_player_order.size());
          PlayerInput input;
          input.player_id = pid;
          input.player_name = pname;
          input.color = PLAYER_COLORS[color_idx % MAX_PLAYERS];
          input.connected = true;
          m_inputs[pid] = input;
          m_player_order.push_back(pid);
          log_info << "[WS] Player joined: " << pname << " (" << pid << ") as Player " << (idx + 1) << std::endl;
        }

        // Build state JSON and collect clients — all while holding the lock
        json state_j;
        state_j["type"] = MSG_STATE;
        state_j["level"] = m_game_level;
        state_j["status"] = m_game_status;
        json players_arr = json::array();
        for (const auto& order_pid : m_player_order)
        {
          auto inp_it = m_inputs.find(order_pid);
          if (inp_it != m_inputs.end() && inp_it->second.connected)
          {
            json pj;
            pj["player_id"] = inp_it->second.player_id;
            pj["player_name"] = inp_it->second.player_name;
            pj["color"] = inp_it->second.color;
            players_arr.push_back(pj);
          }
        }
        state_j["players"] = players_arr;
        state_str = state_j.dump();
      } // release lock before sending

      // Broadcast state to all clients (send is non-blocking in IXWebSocket)
      broadcast(state_str);
    }
    else if (type == MSG_INPUT)
    {
      std::string pid = j.value("player_id", "");

      bool has_tv_clients = false;
      {
        std::lock_guard<std::mutex> lock(m_mutex);
        // Verify the WebSocket connection actually owns this player_id
        auto ws_it = m_ws_to_player.find(&ws);
        if (ws_it == m_ws_to_player.end() || ws_it->second != pid)
          return; // spoofed player_id — ignore

        auto it = m_inputs.find(pid);
        if (it != m_inputs.end())
        {
          float sx = j.value("stick_x", 0.0f);
          float sy = j.value("stick_y", 0.0f);
          it->second.stick_x = std::isfinite(sx) ? std::clamp(sx, -1.0f, 1.0f) : 0.0f;
          it->second.stick_y = std::isfinite(sy) ? std::clamp(sy, -1.0f, 1.0f) : 0.0f;
          it->second.btn_a   = j.value("btn_a", false);
          it->second.btn_b   = j.value("btn_b", false);
        }
        has_tv_clients = !m_tv_clients.empty();
      }
      // Relay raw input JSON to all connected clients (phones will ignore it;
      // TV wasm browsers need it).  Uses getClients() shared_ptrs — no raw
      // pointer dereference, so lifetime is safe even under concurrent close.
      if (has_tv_clients)
        broadcast(msg);
    }
    else if (type == MSG_LEAVE)
    {
      on_close(ws);
    }
    else if (type == MSG_START)
    {
      std::string pid = j.value("player_id", "");

      bool do_broadcast_start = false;
      {
        std::lock_guard<std::mutex> lock(m_mutex);
        // Verify the WebSocket connection actually owns this player_id
        auto ws_it = m_ws_to_player.find(&ws);
        if (ws_it == m_ws_to_player.end() || ws_it->second != pid)
          return; // spoofed player_id — ignore

        // Only the first *connected* player (session host) can start
        std::string host_pid;
        for (const auto& order_pid : m_player_order)
        {
          auto inp_it = m_inputs.find(order_pid);
          if (inp_it != m_inputs.end() && inp_it->second.connected)
          {
            host_pid = order_pid;
            break;
          }
        }
        if (!host_pid.empty() && host_pid == pid)
        {
          m_start_requested = true;
          do_broadcast_start = true;
          log_info << "[WS] Start requested by " << pid << std::endl;
        }
      } // release lock before sending

      if (do_broadcast_start)
      {
        json start_j;
        start_j["type"] = MSG_START;
        broadcast(start_j.dump());
      }
    }
  }
  catch (const std::exception& e)
  {
    log_warning << "[WS] Message error: " << e.what() << std::endl;
  }
}

void
WsServer::on_close(ix::WebSocket& ws)
{
  std::lock_guard<std::mutex> lock(m_mutex);

  // Remove from TV clients set if it was a TV browser
  m_tv_clients.erase(&ws);

  auto it = m_ws_to_player.find(&ws);
  if (it != m_ws_to_player.end())
  {
    std::string pid = it->second;
    log_info << "[WS] Player disconnected: " << pid << std::endl;

    // Mark as disconnected but keep in map (for reconnection)
    auto input_it = m_inputs.find(pid);
    if (input_it != m_inputs.end())
    {
      input_it->second.connected = false;
      input_it->second.stick_x = 0.0f;
      input_it->second.stick_y = 0.0f;
      input_it->second.btn_a = false;
      input_it->second.btn_b = false;
    }

    m_ws_to_player.erase(it);
  }
}

std::map<std::string, PlayerInput>
WsServer::get_all_inputs() const
{
  std::lock_guard<std::mutex> lock(m_mutex);
  return m_inputs;
}

std::vector<std::string>
WsServer::get_player_ids() const
{
  std::lock_guard<std::mutex> lock(m_mutex);
  return m_player_order;
}

int
WsServer::get_player_count() const
{
  std::lock_guard<std::mutex> lock(m_mutex);
  int count = 0;
  for (const auto& pid : m_player_order)
  {
    auto it = m_inputs.find(pid);
    if (it != m_inputs.end() && it->second.connected)
      count++;
  }
  return count;
}

bool
WsServer::consume_start_request()
{
  std::lock_guard<std::mutex> lock(m_mutex);
  bool val = m_start_requested;
  m_start_requested = false;
  return val;
}

std::string
WsServer::get_game_status() const
{
  std::lock_guard<std::mutex> lock(m_mutex);
  return m_game_status;
}

void
WsServer::clear_disconnected()
{
  std::lock_guard<std::mutex> lock(m_mutex);
  auto it = m_player_order.begin();
  while (it != m_player_order.end())
  {
    auto inp_it = m_inputs.find(*it);
    if (inp_it == m_inputs.end() || !inp_it->second.connected)
    {
      if (inp_it != m_inputs.end())
        m_inputs.erase(inp_it);
      it = m_player_order.erase(it);
    }
    else
    {
      ++it;
    }
  }
}

void
WsServer::broadcast_state(const std::string& level, const std::string& status)
{
  {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_game_level = level;
    m_game_status = status;
  }
  broadcast(make_state_json(level, status));
}

void
WsServer::broadcast_victory(const std::string& winner_name)
{
  json j;
  j["type"] = MSG_VICTORY;
  j["winner_name"] = winner_name;
  broadcast(j.dump());
}

void
WsServer::broadcast(const std::string& json_str)
{
  // Grab shared_ptrs under lock so m_server can't be destroyed mid-call
  std::set<std::shared_ptr<ix::WebSocket>> clients;
  {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (!m_server)
      return;
    clients = m_server->getClients();
  }

  for (auto& client : clients)
  {
    client->send(json_str);
  }
}

std::string
WsServer::make_state_json(const std::string& level, const std::string& status) const
{
  json j;
  j["type"] = MSG_STATE;
  j["level"] = level;
  j["status"] = status;

  json players = json::array();
  std::lock_guard<std::mutex> lock(m_mutex);
  for (const auto& pid : m_player_order)
  {
    auto it = m_inputs.find(pid);
    if (it != m_inputs.end() && it->second.connected)
    {
      json p;
      p["player_id"] = it->second.player_id;
      p["player_name"] = it->second.player_name;
      p["color"] = it->second.color;
      players.push_back(p);
    }
  }
  j["players"] = players;

  return j.dump();
}

} // namespace network
