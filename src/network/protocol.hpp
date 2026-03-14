// SuperTux Multi — Network Protocol Definition
// Copyright (C) 2026 sky1241
// GPL v3+

#pragma once

// =============================================================
// PROTOCOL: Phone (controller) <-> SuperTux (game server)
// Transport: WebSocket (JSON over text frames)
// Port: 9876 (WebSocket), 9877 (UDP discovery broadcast)
// =============================================================

// --- Message types ---
// Phone -> Game:
//   "join"   — first message after WebSocket connect
//   "input"  — controller state, sent at up to 60 Hz (delta only)
//   "leave"  — graceful disconnect
//   "start"  — player 1 starts the game from lobby
//
// Game -> Phone:
//   "state"   — lobby/game state update
//   "victory" — a player reached the end of the level
//
// UDP Discovery (broadcast on port 9877):
//   Game -> LAN: {"type":"discover","ip":"x.x.x.x","port":9876}

// --- JSON schemas ---

/*
// Phone -> Game (once, on connect)
{
  "type": "join",
  "player_id": "uuid-string",
  "player_name": "Tux1"
}

// Phone -> Game (up to 60x/sec, only when input changes)
{
  "type": "input",
  "player_id": "uuid-string",
  "stick_x": 0.0,    // -1.0 (left) to 1.0 (right)
  "stick_y": 0.0,    // -1.0 (down) to 1.0 (up) — optional for platformer
  "btn_a": false,     // jump
  "btn_b": false      // action
}

// Phone -> Game
{
  "type": "leave",
  "player_id": "uuid-string"
}

// Phone -> Game (player 1 only, from lobby)
{
  "type": "start",
  "player_id": "uuid-string"
}

// Game -> Phone (state update)
{
  "type": "state",
  "players": [
    {"player_id": "...", "player_name": "Tux1", "color": "#FF6B6B"}
  ],
  "level": "world1/level1",
  "status": "lobby"   // "lobby" | "playing" | "victory"
}

// Game -> All Phones (on level completion)
{
  "type": "victory",
  "winner_name": "Tux1"
}

// UDP Discovery broadcast (Game -> LAN, every 1 second)
{
  "type": "discover",
  "ip": "192.168.1.X",
  "port": 9876
}
*/

namespace network {

constexpr int WS_PORT = 9876;
constexpr int UDP_DISCOVERY_PORT = 9877;
constexpr int MAX_PLAYERS = 4;
constexpr int INPUT_SEND_HZ = 60;

// Message type strings
constexpr const char* MSG_JOIN     = "join";
constexpr const char* MSG_INPUT    = "input";
constexpr const char* MSG_LEAVE    = "leave";
constexpr const char* MSG_START    = "start";
constexpr const char* MSG_STATE    = "state";
constexpr const char* MSG_VICTORY  = "victory";
constexpr const char* MSG_DISCOVER  = "discover";
constexpr const char* MSG_TV_CLIENT = "tv_client"; // TV browser registers as passive listener

// Game status strings
constexpr const char* STATUS_LOBBY   = "lobby";
constexpr const char* STATUS_PLAYING = "playing";
constexpr const char* STATUS_VICTORY = "victory";

// Player colors (assigned in order of connection)
constexpr const char* PLAYER_COLORS[] = {
  "#4CAF50",  // Player 1: green (Tux default)
  "#FF6B6B",  // Player 2: red
  "#42A5F5",  // Player 3: blue
  "#FFA726"   // Player 4: orange
};

} // namespace network
