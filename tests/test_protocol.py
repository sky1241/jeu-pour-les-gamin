#!/usr/bin/env python3
"""
BRIQUE 1 — Protocol validation tests.
Validates all JSON message schemas for the SuperTux Multi protocol.
Run: python3 tests/test_protocol.py
"""

import json
import sys

PASS = 0
FAIL = 0

def check(name: str, condition: bool, detail: str = ""):
    global PASS, FAIL
    if condition:
        PASS += 1
        print(f"  [OK] {name}")
    else:
        FAIL += 1
        print(f"  [FAIL] {name} — {detail}")

def validate_join(msg: dict):
    check("join: has type", msg.get("type") == "join")
    check("join: has player_id (string)", isinstance(msg.get("player_id"), str) and len(msg["player_id"]) > 0)
    check("join: has player_name (string)", isinstance(msg.get("player_name"), str) and len(msg["player_name"]) > 0)

def validate_input(msg: dict):
    check("input: has type", msg.get("type") == "input")
    check("input: has player_id", isinstance(msg.get("player_id"), str))
    check("input: stick_x is float in [-1,1]", isinstance(msg.get("stick_x"), (int, float)) and -1.0 <= msg["stick_x"] <= 1.0)
    check("input: stick_y is float in [-1,1]", isinstance(msg.get("stick_y"), (int, float)) and -1.0 <= msg["stick_y"] <= 1.0)
    check("input: btn_a is bool", isinstance(msg.get("btn_a"), bool))
    check("input: btn_b is bool", isinstance(msg.get("btn_b"), bool))

def validate_leave(msg: dict):
    check("leave: has type", msg.get("type") == "leave")
    check("leave: has player_id", isinstance(msg.get("player_id"), str))

def validate_start(msg: dict):
    check("start: has type", msg.get("type") == "start")
    check("start: has player_id", isinstance(msg.get("player_id"), str))

def validate_state(msg: dict):
    check("state: has type", msg.get("type") == "state")
    check("state: has players (list)", isinstance(msg.get("players"), list))
    if isinstance(msg.get("players"), list) and len(msg["players"]) > 0:
        p = msg["players"][0]
        check("state: player has player_id", isinstance(p.get("player_id"), str))
        check("state: player has player_name", isinstance(p.get("player_name"), str))
        check("state: player has color", isinstance(p.get("color"), str) and p["color"].startswith("#"))
    check("state: has level (string)", isinstance(msg.get("level"), str))
    check("state: status is valid", msg.get("status") in ("lobby", "playing", "victory"))

def validate_victory(msg: dict):
    check("victory: has type", msg.get("type") == "victory")
    check("victory: has winner_name", isinstance(msg.get("winner_name"), str))

def validate_discover(msg: dict):
    check("discover: has type", msg.get("type") == "discover")
    check("discover: has ip", isinstance(msg.get("ip"), str))
    check("discover: has port (int)", isinstance(msg.get("port"), int) and msg["port"] > 0)

# ===== TEST DATA =====

join_msg = '{"type":"join","player_id":"550e8400-e29b-41d4-a716-446655440000","player_name":"Tux1"}'
input_msg = '{"type":"input","player_id":"550e8400-e29b-41d4-a716-446655440000","stick_x":0.75,"stick_y":0.0,"btn_a":true,"btn_b":false}'
leave_msg = '{"type":"leave","player_id":"550e8400-e29b-41d4-a716-446655440000"}'
start_msg = '{"type":"start","player_id":"550e8400-e29b-41d4-a716-446655440000"}'
state_msg = '{"type":"state","players":[{"player_id":"550e8400-e29b-41d4-a716-446655440000","player_name":"Tux1","color":"#4CAF50"}],"level":"world1/level1","status":"lobby"}'
victory_msg = '{"type":"victory","winner_name":"Tux1"}'
discover_msg = '{"type":"discover","ip":"192.168.1.42","port":9876}'

# ===== RUN TESTS =====

print("=== BRIQUE 1 — Protocol Validation ===\n")

print("[JOIN]")
validate_join(json.loads(join_msg))

print("\n[INPUT]")
validate_input(json.loads(input_msg))

print("\n[LEAVE]")
validate_leave(json.loads(leave_msg))

print("\n[START]")
validate_start(json.loads(start_msg))

print("\n[STATE]")
validate_state(json.loads(state_msg))

print("\n[VICTORY]")
validate_victory(json.loads(victory_msg))

print("\n[DISCOVER]")
validate_discover(json.loads(discover_msg))

# ===== EDGE CASES =====

print("\n[EDGE CASES]")

# Input at boundaries
edge_input = '{"type":"input","player_id":"test","stick_x":-1.0,"stick_y":1.0,"btn_a":false,"btn_b":true}'
validate_input(json.loads(edge_input))

# Input at zero
zero_input = '{"type":"input","player_id":"test","stick_x":0,"stick_y":0,"btn_a":false,"btn_b":false}'
validate_input(json.loads(zero_input))

# Multiple players in state
multi_state = '{"type":"state","players":[{"player_id":"p1","player_name":"Tux1","color":"#4CAF50"},{"player_id":"p2","player_name":"Tux2","color":"#FF6B6B"}],"level":"world1/level2","status":"playing"}'
validate_state(json.loads(multi_state))

# ===== VERDICT =====

print(f"\n{'='*40}")
print(f"PASS: {PASS}  |  FAIL: {FAIL}")
if FAIL == 0:
    print("BRIQUE 1 -- Protocol -- PASS")
    sys.exit(0)
else:
    print("BRIQUE 1 -- Protocol -- FAIL")
    sys.exit(1)
