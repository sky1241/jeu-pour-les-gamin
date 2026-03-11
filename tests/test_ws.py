#!/usr/bin/env python3
"""
BRIQUE 2 — WebSocket Server test.
Requires: pip install websockets
Usage: Start SuperTux desktop, then run this script.
       python tests/test_ws.py
"""

import asyncio
import json
import sys
import uuid

WS_URL = "ws://localhost:9876"

PASS = 0
FAIL = 0

def check(name, condition, detail=""):
    global PASS, FAIL
    if condition:
        PASS += 1
        print(f"  [OK] {name}")
    else:
        FAIL += 1
        print(f"  [FAIL] {name} -- {detail}")

async def main():
    global PASS, FAIL

    try:
        import websockets
    except ImportError:
        print("ERROR: pip install websockets")
        sys.exit(1)

    print("=== BRIQUE 2 -- WebSocket Server Test ===\n")

    player_id = str(uuid.uuid4())
    player_name = "TestTux"

    # Test 1: Connect
    print("[CONNECT]")
    try:
        ws = await asyncio.wait_for(
            websockets.connect(WS_URL),
            timeout=5.0
        )
        check("WebSocket connect", True)
    except Exception as e:
        check("WebSocket connect", False, str(e))
        print(f"\nMake sure SuperTux desktop is running!")
        print(f"PASS: {PASS}  |  FAIL: {FAIL}")
        print("BRIQUE 2 -- WebSocket Server -- FAIL")
        sys.exit(1)

    # Test 2: Send join
    print("\n[JOIN]")
    join_msg = json.dumps({
        "type": "join",
        "player_id": player_id,
        "player_name": player_name
    })
    await ws.send(join_msg)
    check("Send join message", True)

    # Test 3: Receive state response
    try:
        response = await asyncio.wait_for(ws.recv(), timeout=3.0)
        state = json.loads(response)
        check("Receive state response", state.get("type") == "state", f"got: {state.get('type')}")
        check("State has players list", isinstance(state.get("players"), list))
        if isinstance(state.get("players"), list) and len(state["players"]) > 0:
            p = state["players"][-1]
            check("Player in state", p.get("player_id") == player_id)
            check("Player name correct", p.get("player_name") == player_name)
            check("Player has color", p.get("color", "").startswith("#"))
        check("State has status", state.get("status") in ("lobby", "playing", "victory"))
    except asyncio.TimeoutError:
        check("Receive state response", False, "timeout — server may not send state on join yet")

    # Test 4: Send input
    print("\n[INPUT]")
    input_msg = json.dumps({
        "type": "input",
        "player_id": player_id,
        "stick_x": 0.75,
        "stick_y": 0.0,
        "btn_a": True,
        "btn_b": False
    })
    await ws.send(input_msg)
    check("Send input message", True)

    # Test 5: Send leave
    print("\n[LEAVE]")
    leave_msg = json.dumps({
        "type": "leave",
        "player_id": player_id
    })
    await ws.send(leave_msg)
    check("Send leave message", True)

    await ws.close()
    check("WebSocket close", True)

    # Verdict
    print(f"\n{'='*40}")
    print(f"PASS: {PASS}  |  FAIL: {FAIL}")
    if FAIL == 0:
        print("BRIQUE 2 -- WebSocket Server -- PASS")
    else:
        print("BRIQUE 2 -- WebSocket Server -- FAIL")
    sys.exit(0 if FAIL == 0 else 1)

if __name__ == "__main__":
    asyncio.run(main())
