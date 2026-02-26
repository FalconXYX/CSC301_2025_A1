#!/usr/bin/env python3
"""
WorkloadParser for CSC301 A2.

Workload file format (space-delimited):
  USER create <id> <username> <email> <password>
  USER get <id>
  USER update <id> username:<u> email:<e> password:<p>
  USER delete <id> <username> <email> <password>
  PRODUCT create <id> <name> <price> <quantity>
  PRODUCT info <id>
  PRODUCT update <id> name:<n> price:<p> quantity:<q>
  PRODUCT delete <id> <name> <price> <quantity>
  ORDER place <product_id> <user_id> <quantity>
  shutdown
  restart
"""

import json
import sys
import time
import urllib.request
import urllib.error


# ---------------------------------------------------------------------------
# HTTP helpers
# ---------------------------------------------------------------------------

def http_request(url: str, method: str = "GET", body: dict = None, timeout: int = 10):
    """
    Send an HTTP request and return (status_code, response_body_str).
    Returns (-1, error_message) on connection failure.
    """
    try:
        data = json.dumps(body).encode("utf-8") if body is not None else None
        headers = {"Content-Type": "application/json"} if data else {}
        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8")
    except Exception as exc:
        return -1, str(exc)


# ---------------------------------------------------------------------------
# Command builders  →  (method, path, body_dict_or_None)
# ---------------------------------------------------------------------------

def build_user_command(parts):
    """Return (method, path, body) for USER commands."""
    if len(parts) < 2:
        return None
    sub = parts[1].lower()
    if sub == "create" and len(parts) >= 6:
        return ("POST", "/user", {
            "command": "create",
            "id": int(parts[2]),
            "username": parts[3],
            "email": parts[4],
            "password": parts[5],
        })
    elif sub == "get" and len(parts) >= 3:
        return ("GET", f"/user/{parts[2]}", None)
    elif sub == "update" and len(parts) >= 4:
        body = {"command": "update", "id": int(parts[2])}
        for kv in parts[3:]:
            if ":" in kv:
                k, v = kv.split(":", 1)
                body[k] = v
        return ("POST", "/user", body)
    elif sub == "delete" and len(parts) >= 6:
        return ("POST", "/user", {
            "command": "delete",
            "id": int(parts[2]),
            "username": parts[3],
            "email": parts[4],
            "password": parts[5],
        })
    return None


def build_product_command(parts):
    """Return (method, path, body) for PRODUCT commands."""
    if len(parts) < 2:
        return None
    sub = parts[1].lower()
    if sub == "create" and len(parts) >= 7:
        # PRODUCT create <id> <name> <description> <price> <quantity>
        return ("POST", "/product", {
            "command": "create",
            "id": int(parts[2]),
            "name": parts[3],
            "description": parts[4],
            "price": float(parts[5]),
            "quantity": int(parts[6]),
        })
    elif sub in ("info", "get") and len(parts) >= 3:
        return ("GET", f"/product/{parts[2]}", None)
    elif sub == "update" and len(parts) >= 4:
        body = {"command": "update", "id": int(parts[2])}
        for kv in parts[3:]:
            if ":" in kv:
                k, v = kv.split(":", 1)
                if k == "price":
                    body[k] = float(v)
                elif k == "quantity":
                    body[k] = int(v)
                else:
                    body[k] = v
        return ("POST", "/product", body)
    elif sub == "delete" and len(parts) >= 6:
        # PRODUCT delete <id> <name> <price> <quantity>
        return ("POST", "/product", {
            "command": "delete",
            "id": int(parts[2]),
            "name": parts[3],
            "price": float(parts[4]),
            "quantity": int(parts[5]),
        })
    return None


def build_order_command(parts):
    """Return (method, path, body) for ORDER commands."""
    if len(parts) < 2:
        return None
    sub = parts[1].lower()
    if sub == "place" and len(parts) >= 5:
        return ("POST", "/order", {
            "command": "place order",
            "product_id": int(parts[2]),
            "user_id": int(parts[3]),
            "quantity": int(parts[4]),
        })
    return None


# ---------------------------------------------------------------------------
# Main parser
# ---------------------------------------------------------------------------

def parse_workload(workload_file: str, order_service_url: str):
    with open(workload_file, "r") as f:
        lines = f.readlines()

    total = 0
    ok = 0
    failed = 0
    start = time.time()

    for line_num, raw_line in enumerate(lines, 1):
        line = raw_line.strip()
        # Skip blank lines and comments
        if not line or line.startswith("#"):
            continue

        parts = line.split()
        cmd_type = parts[0].upper()

        # ------------------------------------------------------------------ #
        # Special control commands
        # ------------------------------------------------------------------ #
        if cmd_type == "SHUTDOWN":
            print(f"Line {line_num}: SHUTDOWN — sending shutdown to all services")
            total += 1
            # Shutdown OrderService (which will propagate or each can shut down)
            status, _ = http_request(f"{order_service_url}/shutdown", "POST", {})
            if status in (200, -1):  # -1 is fine; service already down
                ok += 1
            else:
                failed += 1
            # Small wait for services to actually stop
            time.sleep(1)
            continue

        if cmd_type == "RESTART":
            print(f"Line {line_num}: RESTART — sending restart to Order Service")
            total += 1
            status, _ = http_request(f"{order_service_url}/restart", "POST", {})
            if status == 200:
                ok += 1
            else:
                failed += 1
            continue

        # ------------------------------------------------------------------ #
        # Regular service commands
        # ------------------------------------------------------------------ #
        method = path = body = None

        if cmd_type == "USER":
            result = build_user_command(parts)
        elif cmd_type == "PRODUCT":
            result = build_product_command(parts)
        elif cmd_type == "ORDER":
            result = build_order_command(parts)
        else:
            print(f"Line {line_num}: Unknown command type '{cmd_type}'")
            continue

        if result is None:
            print(f"Line {line_num}: Could not parse command: {line}")
            failed += 1
            total += 1
            continue

        method, path, body = result
        url = f"{order_service_url}{path}"
        total += 1

        status, resp_body = http_request(url, method, body)
        if status == 200:
            ok += 1
        else:
            if status > 0:
                print(f"Line {line_num}: [{method} {path}] -> HTTP {status}")
            else:
                print(f"Line {line_num}: [{method} {path}] -> Connection error: {resp_body}")
            failed += 1

    elapsed = time.time() - start
    print(f"\nWorkload Summary:")
    print(f"  Total:      {total}")
    print(f"  Successful: {ok}")
    print(f"  Failed:     {failed}")
    print(f"  Elapsed:    {elapsed:.2f}s")


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python3 WorkloadParser.py <workload_file> [order_service_url]")
        sys.exit(1)

    workload_file = sys.argv[1]
    order_service_url = sys.argv[2] if len(sys.argv) > 2 else "http://127.0.0.1:14000"

    print(f"Parsing workload from: {workload_file}")
    print(f"Order Service URL:     {order_service_url}")
    parse_workload(workload_file, order_service_url)

    print(f"Order Service URL: {order_service_url}\n")
    
    parse_workload(workload_file, order_service_url)
