#!/usr/bin/env python3
"""
BRIQUE 3 — Input Dispatcher test.
Sends simulated joystick inputs to SuperTux via WebSocket.
Requires: pip install websockets
Usage: Start SuperTux desktop (load a level), then run:
       python tests/test_multi_input.py
Watch the game — Tux should move right for 2s, then jump.
"""

import asyncio
import json
import sys
import uuid
import time

WS_URL = "ws://localhost:9876"

async def main():
    try:
        import websockets
    except ImportError:
        print("ERROR: pip install websockets")
        sys.exit(1)

    print("=== BRIQUE 3 -- Input Dispatcher Test ===\n")

    player_id = str(uuid.uuid4())

    # Connect
    print("[1] Connecting...")
    try:
        ws = await asyncio.wait_for(websockets.connect(WS_URL), timeout=5.0)
        print("  Connected!")
    except Exception as e:
        print(f"  FAIL: {e}")
        print("  Make sure SuperTux desktop is running with a level loaded!")
        sys.exit(1)

    # Join
    print("[2] Joining as TestTux...")
    await ws.send(json.dumps({
        "type": "join",
        "player_id": player_id,
        "player_name": "TestTux"
    }))
    response = await asyncio.wait_for(ws.recv(), timeout=3.0)
    state = json.loads(response)
    print(f"  Joined! State: {state.get('status')}, players: {len(state.get('players', []))}")

    # Send stick_x = 1.0 for 2 seconds (move right)
    print("[3] Sending stick_x=1.0 (move RIGHT) for 2 seconds...")
    start = time.time()
    while time.time() - start < 2.0:
        await ws.send(json.dumps({
            "type": "input",
            "player_id": player_id,
            "stick_x": 1.0,
            "stick_y": 0.0,
            "btn_a": False,
            "btn_b": False
        }))
        await asyncio.sleep(1.0 / 60.0)  # 60 Hz
    print("  Done moving right!")

    # Send jump for 0.5 seconds
    print("[4] Sending btn_a=true (JUMP) for 0.5 seconds...")
    start = time.time()
    while time.time() - start < 0.5:
        await ws.send(json.dumps({
            "type": "input",
            "player_id": player_id,
            "stick_x": 0.0,
            "stick_y": 0.0,
            "btn_a": True,
            "btn_b": False
        }))
        await asyncio.sleep(1.0 / 60.0)
    print("  Done jumping!")

    # Release all
    print("[5] Releasing all inputs...")
    await ws.send(json.dumps({
        "type": "input",
        "player_id": player_id,
        "stick_x": 0.0,
        "stick_y": 0.0,
        "btn_a": False,
        "btn_b": False
    }))

    await asyncio.sleep(0.5)

    # Leave
    print("[6] Leaving...")
    await ws.send(json.dumps({
        "type": "leave",
        "player_id": player_id
    }))
    await ws.close()

    print("\n========================================")
    print("BRIQUE 3 -- Input Dispatcher -- VISUAL CHECK")
    print("Did Tux move right and then jump?")
    print("If yes: BRIQUE 3 -- PASS")
    print("If no:  BRIQUE 3 -- FAIL")

if __name__ == "__main__":
    asyncio.run(main())
