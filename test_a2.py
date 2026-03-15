#!/usr/bin/env python3
"""
CSC301 A2 – Integration Test Suite
====================================
Covers:
  • All A1 User/Product/Order CRUD cases (status codes + response body)
  • A2 /user/purchased/{userId} endpoint
  • A2 Persistence:  Shutdown → Restart  (data kept)
                     Shutdown → No Restart (data wiped)

Usage:
  python3 test_a2.py                   # run all tests (assumes fresh services)
  python3 test_a2.py --skip-persistence # skip persistence tests
  python3 test_a2.py --reset            # docker-compose restart services first
"""

import json
import math
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

# ──────────────────────────────────────────────────────────────────────────────
# Config
# ──────────────────────────────────────────────────────────────────────────────
ORDER_URL = "http://127.0.0.1:14000"
BASEDIR   = os.path.dirname(os.path.abspath(__file__))

# ──────────────────────────────────────────────────────────────────────────────
# Terminal colours
# ──────────────────────────────────────────────────────────────────────────────
GREEN  = "\033[92m"
RED    = "\033[91m"
YELLOW = "\033[93m"
CYAN   = "\033[96m"
RESET  = "\033[0m"
BOLD   = "\033[1m"

# ──────────────────────────────────────────────────────────────────────────────
# Test counters
# ──────────────────────────────────────────────────────────────────────────────
_pass = 0
_fail = 0


def _ok(name: str):
    global _pass
    _pass += 1
    print(f"  {GREEN}✓{RESET} {name}")


def _bad(name: str, reason: str, body: str = ""):
    global _fail
    _fail += 1
    preview = (body or "")[:160]
    print(f"  {RED}✗{RESET} {name}")
    print(f"      {YELLOW}reason:{RESET} {reason}")
    if preview:
        print(f"      {YELLOW}body  :{RESET} {preview}")


def section(title: str):
    print(f"\n{BOLD}{CYAN}{'─' * 62}{RESET}")
    print(f"{BOLD}{CYAN}  {title}{RESET}")
    print(f"{BOLD}{CYAN}{'─' * 62}{RESET}")


# ──────────────────────────────────────────────────────────────────────────────
# HTTP helper
# ──────────────────────────────────────────────────────────────────────────────
def http(url: str, method: str = "GET", body=None, timeout: int = 10):
    """Returns (status_code, body_string).  (-1, msg) on network error."""
    try:
        data = json.dumps(body).encode() if body is not None else None
        hdrs = {"Content-Type": "application/json"} if data else {}
        req = urllib.request.Request(url, data=data, headers=hdrs, method=method)
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()
    except Exception as exc:
        return -1, str(exc)


# ──────────────────────────────────────────────────────────────────────────────
# Assertion helpers
# ──────────────────────────────────────────────────────────────────────────────
def check(name: str, status: int, body: str, want_status: int,
          want_body: dict = None, approx_floats: bool = False):
    """Verify status code, then optionally check a subset of JSON fields."""
    if status != want_status:
        _bad(name, f"status {status} ≠ {want_status}", body)
        return False

    if want_body:
        try:
            actual = json.loads(body)
        except Exception as e:
            _bad(name, f"JSON parse error: {e}", body)
            return False

        for k, v in want_body.items():
            av = actual.get(k)
            if approx_floats and isinstance(v, float):
                if not (isinstance(av, (int, float)) and math.isclose(av, v, rel_tol=1e-3)):
                    _bad(name, f"field '{k}': {av!r} ≈≠ {v!r}", body)
                    return False
            elif isinstance(v, str) and isinstance(av, str):
                # case-insensitive string compare (covers hex password hashes)
                if av.lower() != v.lower():
                    _bad(name, f"field '{k}': {av!r} ≠ {v!r}", body)
                    return False
            else:
                if av != v:
                    _bad(name, f"field '{k}': {av!r} ≠ {v!r}", body)
                    return False

    _ok(name)
    return True


def check_json_keys(name: str, status: int, body: str, want_status: int,
                    required_keys: list):
    """Check status then that body JSON has all required keys."""
    if status != want_status:
        _bad(name, f"status {status} ≠ {want_status}", body)
        return False
    try:
        data = json.loads(body)
        for k in required_keys:
            if str(k) not in data and k not in data:
                _bad(name, f"key '{k}' missing from response", body)
                return False
    except Exception as e:
        _bad(name, f"JSON parse error: {e}", body)
        return False
    _ok(name)
    return True


def check_empty_json(name: str, status: int, body: str, want_status: int):
    """Check status then that body is an empty JSON object {}."""
    if status != want_status:
        _bad(name, f"status {status} ≠ {want_status}", body)
        return False
    try:
        data = json.loads(body)
        if data != {}:
            _bad(name, f"expected {{}} but got: {body[:120]}")
            return False
    except Exception as e:
        _bad(name, f"JSON parse error: {e}", body)
        return False
    _ok(name)
    return True


# ──────────────────────────────────────────────────────────────────────────────
# Service readiness
# ──────────────────────────────────────────────────────────────────────────────
def wait_for_order_service(retries: int = 40, delay: int = 2) -> bool:
    print(f"  Waiting for Order Service at {ORDER_URL} ...", end="", flush=True)
    for _ in range(retries):
        s, _ = http(f"{ORDER_URL}/user/0", timeout=3)
        if s in (200, 400, 404, 409):
            print(f" {GREEN}ready{RESET}")
            return True
        print(".", end="", flush=True)
        time.sleep(delay)
    print(f" {RED}timed out{RESET}")
    return False


# ──────────────────────────────────────────────────────────────────────────────
# A1 – User Tests
# ──────────────────────────────────────────────────────────────────────────────
def test_users_a1():
    section("A1 – User Service (CRUD)")

    # ── Create valid users 1000-1009 ──────────────────────────────────────────
    for uid in range(1000, 1010):
        s, b = http(f"{ORDER_URL}/user", "POST", {
            "command": "create", "id": uid,
            "username": f"tester{uid}",
            "email": f"test{uid}@test.com",
            "password": f"password{uid}",
        })
        check(f"CREATE user {uid} → 200", s, b, 200, {"id": uid, "username": f"tester{uid}"})

    # ── Create – error cases ──────────────────────────────────────────────────
    s, b = http(f"{ORDER_URL}/user", "POST", {
        "command": "create", "id": 1010,
        "username": "", "email": "test1010@test.com", "password": "pw",
    })
    check("CREATE user – empty username → 400", s, b, 400)

    s, b = http(f"{ORDER_URL}/user", "POST", {
        "command": "create", "id": 1011,
        "username": "tester1011", "email": "test1011@test.com",
    })
    check("CREATE user – missing password → 400", s, b, 400)

    s, b = http(f"{ORDER_URL}/user", "POST", {
        "command": "create", "id": 1012,
        "username": "tester1012", "email": 1012, "password": "pw",
    })
    check("CREATE user – invalid email type (int) → 400", s, b, 400)

    s, b = http(f"{ORDER_URL}/user", "POST", {
        "command": "create", "id": 1000,
        "username": "dup", "email": "dup@test.com", "password": "pw",
    })
    check("CREATE user – duplicate id 1000 → 409", s, b, 409)

    # ── Update ────────────────────────────────────────────────────────────────
    s, b = http(f"{ORDER_URL}/user", "POST", {
        "command": "update", "id": 1001,
        "username": "tester1001-update",
        "email": "testupdate1001@test.com",
        "password": "password1001-update",
    })
    check("UPDATE user 1001 (all fields) → 200", s, b, 200,
          {"id": 1001, "username": "tester1001-update", "email": "testupdate1001@test.com"})

    s, b = http(f"{ORDER_URL}/user", "POST", {"command": "update", "id": 1002})
    check("UPDATE user 1002 (no fields) → 200 unchanged", s, b, 200,
          {"id": 1002, "username": "tester1002", "email": "test1002@test.com"})

    s, b = http(f"{ORDER_URL}/user", "POST", {"command": "update", "username": "x"})
    check("UPDATE user – missing id → 400", s, b, 400)

    s, b = http(f"{ORDER_URL}/user", "POST", {"command": "update", "id": 1003, "email": 1003})
    check("UPDATE user – invalid email type (int) → 400", s, b, 400)

    s, b = http(f"{ORDER_URL}/user", "POST", {
        "command": "update", "id": 1004,
        "username": "tester1004-update", "email": "", "password": "",
    })
    check("UPDATE user – empty fields → 400", s, b, 400)

    s, b = http(f"{ORDER_URL}/user", "POST", {
        "command": "update", "id": 10001,
        "username": "x", "email": "x@x.com", "password": "pw",
    })
    check("UPDATE user – non-existent id 10001 → 404", s, b, 404)

    # ── Delete ────────────────────────────────────────────────────────────────
    s, b = http(f"{ORDER_URL}/user", "POST", {
        "command": "delete", "id": 1005,
        "username": "tester1005", "email": "test1005@test.com", "password": "password1005",
    })
    check("DELETE user 1005 → 200", s, b, 200)

    s, b = http(f"{ORDER_URL}/user", "POST", {
        "command": "delete", "id": 1006,
        "username": "tester1006", "password": "password1006",
    })
    check("DELETE user – missing email → 400", s, b, 400)

    s, b = http(f"{ORDER_URL}/user", "POST", {
        "command": "delete", "id": 10002,
        "username": "tester10002", "email": "test10002@test.com", "password": "pw",
    })
    check("DELETE user – non-existent id → 404", s, b, 404)

    s, b = http(f"{ORDER_URL}/user", "POST", {
        "command": "delete", "id": 1007,
        "username": "tester1007", "email": "test1007@test.com", "password": "WrongPassword",
    })
    check("DELETE user – wrong password → 404", s, b, 404)

    # ── Get ───────────────────────────────────────────────────────────────────
    s, b = http(f"{ORDER_URL}/user/1000", "GET")
    check("GET user 1000 → 200", s, b, 200,
          {"id": 1000, "username": "tester1000", "email": "test1000@test.com"})

    s, b = http(f"{ORDER_URL}/user/1001", "GET")
    check("GET user 1001 (after update) → 200", s, b, 200,
          {"id": 1001, "username": "tester1001-update", "email": "testupdate1001@test.com"})

    s, b = http(f"{ORDER_URL}/user/10000", "GET")
    check("GET user – non-existent id 10000 → 404", s, b, 404)

    s, b = http(f"{ORDER_URL}/user/5", "GET")
    check("GET user 1005 (deleted) → 404", s, b, 404)


# ──────────────────────────────────────────────────────────────────────────────
# A1 – Product Tests
# ──────────────────────────────────────────────────────────────────────────────
_PRODUCTS = [
    (2000, "product2000", "This is product 2000", 162.58, 90),
    (2001, "product2001", "This is product 2001", 202.66, 60),
    (2002, "product2002", "This is product 2002",  98.47, 96),
    (2003, "product2003", "This is product 2003", 197.51,  3),
    (2004, "product2004", "This is product 2004",  98.61, 26),
    (2005, "product2005", "This is product 2005", 152.70, 34),
    (2006, "product2006", "This is product 2006", 269.23, 89),
    (2007, "product2007", "This is product 2007",  79.18, 89),
    (2008, "product2008", "This is product 2008",  77.68, 79),
    (2009, "product2009", "This is product 2009", 283.03, 71),
    (2010, "product2010", "This is product 2010",  37.32, 75),
    (2011, "product2011", "This is product 2011",   8.50, 28),
    (2012, "product2012", "This is product 2012", 173.31, 74),
    (2013, "product2013", "This is product 2013", 139.85, 97),
    (2014, "product2014", "This is product 2014", 157.00, 35),
    (2015, "product2015", "This is product 2015", 175.73, 35),
    (2016, "product2016", "This is product 2016",  52.38, 19),
    (2017, "product2017", "This is product 2017", 221.85, 82),
    (2018, "product2018", "This is product 2018", 111.86, 14),
    (2019, "product2019", "This is product 2019", 209.20, 51),
    (2020, "product2020", "This is product 2020",  37.31, 82),
    (2021, "product2021", "This is product 2021", 150.56, 68),
    (2022, "product2022", "This is product 2022",  22.43, 54),
    (2023, "product2023", "This is product 2023", 160.78, 27),
]


def test_products_a1():
    section("A1 – Product Service (CRUD)")

    # ── Create valid products ─────────────────────────────────────────────────
    for pid, name, desc, price, qty in _PRODUCTS:
        s, b = http(f"{ORDER_URL}/product", "POST", {
            "command": "create", "id": pid, "name": name,
            "description": desc, "price": price, "quantity": qty,
        })
        check(f"CREATE product {pid} → 200", s, b, 200, {"id": pid, "name": name},
              approx_floats=True)

    # ── Create – error cases ──────────────────────────────────────────────────
    s, b = http(f"{ORDER_URL}/product", "POST", {
        "command": "create", "id": 2024, "name": "",
        "description": "desc", "price": 2.3, "quantity": 4,
    })
    check("CREATE product – empty name → 400", s, b, 400)

    s, b = http(f"{ORDER_URL}/product", "POST", {
        "command": "create", "id": 2025, "name": "product2025",
        "price": 2.3, "quantity": 4,
    })
    check("CREATE product – missing description → 400", s, b, 400)

    s, b = http(f"{ORDER_URL}/product", "POST", {
        "command": "create", "id": 2026, "name": "product2026",
        "description": "desc", "price": 5.0, "quantity": 0.5597,
    })
    check("CREATE product – float quantity → 400", s, b, 400)

    s, b = http(f"{ORDER_URL}/product", "POST", {
        "command": "create", "id": 2027, "name": "product2027",
        "description": "desc", "price": 5.0, "quantity": -4,
    })
    check("CREATE product – negative quantity → 400", s, b, 400)

    s, b = http(f"{ORDER_URL}/product", "POST", {
        "command": "create", "id": 2028, "name": "product2028",
        "description": "desc", "price": -5.0, "quantity": 4,
    })
    check("CREATE product – negative price → 400", s, b, 400)

    s, b = http(f"{ORDER_URL}/product", "POST", {
        "command": "create", "id": 2000, "name": "dup",
        "description": "dup desc", "price": 1.0, "quantity": 1,
    })
    check("CREATE product – duplicate id 2000 → 409", s, b, 409)

    # ── Update ────────────────────────────────────────────────────────────────
    s, b = http(f"{ORDER_URL}/product", "POST", {
        "command": "update", "id": 2001,
        "name": "product2001-update",
        "description": "This is product 2001 version 2",
        "price": 199.99, "quantity": 100,
    })
    check("UPDATE product 2001 (all fields) → 200", s, b, 200, {
        "id": 2001, "name": "product2001-update",
        "description": "This is product 2001 version 2",
        "price": 199.99, "quantity": 100,
    }, approx_floats=True)

    s, b = http(f"{ORDER_URL}/product", "POST", {"command": "update", "name": "x"})
    check("UPDATE product – missing id → 400", s, b, 400)

    s, b = http(f"{ORDER_URL}/product", "POST", {
        "command": "update", "id": 2003, "description": "",
    })
    check("UPDATE product – empty description → 400", s, b, 400)

    s, b = http(f"{ORDER_URL}/product", "POST", {
        "command": "update", "id": 2004, "price": -6.0,
    })
    check("UPDATE product – negative price → 400", s, b, 400)

    s, b = http(f"{ORDER_URL}/product", "POST", {
        "command": "update", "id": 2005, "quantity": -6,
    })
    check("UPDATE product – negative quantity → 400", s, b, 400)

    s, b = http(f"{ORDER_URL}/product", "POST", {
        "command": "update", "id": 2006, "quantity": 0.6,
    })
    check("UPDATE product – float quantity → 400", s, b, 400)

    s, b = http(f"{ORDER_URL}/product", "POST", {
        "command": "update", "id": 10000, "description": "ghost",
    })
    check("UPDATE product – non-existent id → 404", s, b, 404)

    # ── Delete ────────────────────────────────────────────────────────────────
    s, b = http(f"{ORDER_URL}/product", "POST", {
        "command": "delete", "id": 2007,
        "name": "product2007", "price": 79.18, "quantity": 89,
    })
    check("DELETE product 2007 → 200", s, b, 200)

    s, b = http(f"{ORDER_URL}/product", "POST", {
        "command": "delete", "id": 2008, "price": 77.68, "quantity": 79,
    })
    check("DELETE product – missing name → 400", s, b, 400)

    s, b = http(f"{ORDER_URL}/product", "POST", {
        "command": "delete", "id": 10000,
        "name": "product10000", "price": 2.3, "quantity": 4,
    })
    check("DELETE product – non-existent id → 404", s, b, 404)

    s, b = http(f"{ORDER_URL}/product", "POST", {
        "command": "delete", "id": 2009,
        "name": "product2009", "price": 280.0, "quantity": 71,
    })
    check("DELETE product – fields don't match → 404", s, b, 404)

    # ── Get ───────────────────────────────────────────────────────────────────
    s, b = http(f"{ORDER_URL}/product/2001", "GET")
    check("GET product 2001 (after update) → 200", s, b, 200, {
        "id": 2001, "name": "product2001-update",
        "description": "This is product 2001 version 2",
        "price": 199.99, "quantity": 100,
    }, approx_floats=True)

    s, b = http(f"{ORDER_URL}/product/2010", "GET")
    check("GET product 2010 → 200", s, b, 200,
          {"id": 2010, "name": "product2010", "quantity": 75}, approx_floats=True)

    # 2007 was deleted
    s, b = http(f"{ORDER_URL}/product/2007", "GET")
    check("GET product 2007 (deleted) → 404", s, b, 404)

    s, b = http(f"{ORDER_URL}/product/10000", "GET")
    check("GET product – non-existent id → 404", s, b, 404)


# ──────────────────────────────────────────────────────────────────────────────
# A1 – Order Tests
# ──────────────────────────────────────────────────────────────────────────────
def test_orders_a1():
    section("A1 – Order Service (place order)")

    # product 2011: qty=28, user 1009 exists
    s, b = http(f"{ORDER_URL}/order", "POST", {
        "command": "place order",
        "product_id": 2011, "user_id": 1009, "quantity": 20,
    })
    check("PLACE ORDER valid (20 × product 2011 for user 1009) → 200 Success",
          s, b, 200, {"status": "Success", "product_id": 2011, "user_id": 1009, "quantity": 20})

    # Missing user_id
    s, b = http(f"{ORDER_URL}/order", "POST", {
        "command": "place order", "product_id": 2012, "quantity": 1,
    })
    check("PLACE ORDER – missing user_id → 400", s, b, 400)

    # Non-existent user
    s, b = http(f"{ORDER_URL}/order", "POST", {
        "command": "place order", "product_id": 2013, "user_id": 10000, "quantity": 2,
    })
    check("PLACE ORDER – non-existent user_id 10000 → 404", s, b, 404)

    # Non-existent product
    s, b = http(f"{ORDER_URL}/order", "POST", {
        "command": "place order", "product_id": 10000, "user_id": 1009, "quantity": 3,
    })
    check("PLACE ORDER – non-existent product_id 10000 → 404", s, b, 404)

    # Negative quantity
    s, b = http(f"{ORDER_URL}/order", "POST", {
        "command": "place order", "product_id": 2014, "user_id": 1009, "quantity": -1,
    })
    check("PLACE ORDER – negative quantity → 400", s, b, 400)

    # Exceed: product 2011 now has 28-20=8, try to order 9
    s, b = http(f"{ORDER_URL}/order", "POST", {
        "command": "place order", "product_id": 2011, "user_id": 1009, "quantity": 9,
    })
    check("PLACE ORDER – exceeded quantity → 200 'Exceeded quantity limit'",
          s, b, 200, {"status": "Exceeded quantity limit"})


# ──────────────────────────────────────────────────────────────────────────────
# A2 – /user/purchased Tests
# ──────────────────────────────────────────────────────────────────────────────
def test_purchased_a2():
    section("A2 – GET /user/purchased/{userId}")

    # user 1009 already placed: 20 × product 2011
    s, b = http(f"{ORDER_URL}/user/purchased/1009", "GET")
    if check("GET purchased/1009 → 200", s, b, 200):
        try:
            data = json.loads(b)
            qty = data.get("2011") or data.get(2011)
            if qty == 20:
                _ok("  purchased/1009 body contains {\"2011\": 20}")
            else:
                _bad("  purchased/1009 body contains {\"2011\": 20}",
                     f"got {b[:120]}")
        except Exception as e:
            _bad("  purchased/1009 body parse", str(e), b)

    # user who has made no orders (user 1002)
    s, b = http(f"{ORDER_URL}/user/purchased/1002", "GET")
    check_empty_json("GET purchased/1002 (no orders) → 200 {}", s, b, 200)

    # non-existent user
    s, b = http(f"{ORDER_URL}/user/purchased/99999", "GET")
    check("GET purchased/99999 (non-existent) → 404", s, b, 404)

    # Place a second order for user 1009, verify aggregation
    http(f"{ORDER_URL}/order", "POST", {
        "command": "place order", "product_id": 2012, "user_id": 1009, "quantity": 5,
    })
    s, b = http(f"{ORDER_URL}/user/purchased/1009", "GET")
    if check("GET purchased/1009 after 2nd order → 200", s, b, 200):
        try:
            data = json.loads(b)
            has_2011 = "2011" in data or 2011 in data
            has_2012 = "2012" in data or 2012 in data
            if has_2011 and has_2012:
                _ok("  purchased/1009 aggregates both product 2011 and 2012")
            else:
                _bad("  purchased/1009 aggregation",
                     f"expected both 2011+2012, got {b[:120]}")
        except Exception as e:
            _bad("  purchased/1009 body parse", str(e), b)

    # Place two orders for different users and verify independence
    http(f"{ORDER_URL}/order", "POST", {
        "command": "place order", "product_id": 2013, "user_id": 1008, "quantity": 3,
    })
    s, b = http(f"{ORDER_URL}/user/purchased/1008", "GET")
    if check("GET purchased/1008 → 200", s, b, 200):
        try:
            data = json.loads(b)
            qty = data.get("2013") or data.get(2013)
            if qty == 3:
                _ok("  purchased/1008 body contains {\"2013\": 3}")
            else:
                _bad("  purchased/1008 body check", f"expected 2013:3, got {b[:120]}")
        except Exception as e:
            _bad("  purchased/1008 body parse", str(e), b)


# ──────────────────────────────────────────────────────────────────────────────
# A2 – Persistence Tests  (requires Docker)
# ──────────────────────────────────────────────────────────────────────────────
def _local_restart(*service_names) -> tuple:
    """Kill and restart local Java services (used when docker compose doesn't manage them)."""
    config_file = os.path.join(BASEDIR, "config.json")
    service_map = {
        "order-service":   (os.path.join(BASEDIR, "OrderService",   "target", "order-service-1.0.0.jar"),   "/tmp/order.log"),
        "user-service":    (os.path.join(BASEDIR, "UserService",    "target", "user-service-1.0.0.jar"),    "/tmp/user.log"),
        "product-service": (os.path.join(BASEDIR, "ProductService", "target", "product-service-1.0.0.jar"), "/tmp/product.log"),
    }
    # Force-kill surviving processes (SIGKILL avoids waiting for graceful JVM shutdown)
    for name in service_names:
        jar_path, _ = service_map.get(name, ("", ""))
        if jar_path:
            subprocess.run(["pkill", "-9", "-f", os.path.basename(jar_path)], capture_output=True)
    time.sleep(3)  # Give the OS time to release ports
    # Restart them
    for name in service_names:
        jar_path, log_path = service_map.get(name, ("", ""))
        if jar_path and os.path.exists(jar_path):
            with open(log_path, "w") as lf:
                subprocess.Popen(["java", "-jar", jar_path, config_file], stdout=lf, stderr=lf)
    return 0, f"restarted locally: {', '.join(service_names)}"


def _docker(*args) -> tuple:
    r = subprocess.run(["docker", "compose", *args],
                       cwd=BASEDIR, capture_output=True, text=True)
    # Fall back to local restart when app services aren't managed by docker compose
    if r.returncode != 0 and len(args) >= 2 and args[0] == "restart":
        return _local_restart(*args[1:])
    return r.returncode, (r.stdout + r.stderr).strip()


def _wait_order_service(retries=40, delay=2) -> bool:
    for _ in range(retries):
        s, _ = http(f"{ORDER_URL}/user/0", timeout=3)
        if s in (200, 400, 404, 409):
            return True
        time.sleep(delay)
    return False


def test_persistence_a2():
    section("A2 – Persistence (Shutdown / Restart / Wipe)")

    # ── Setup: create dedicated persistence-test data ─────────────────────────
    print(f"  {YELLOW}Setting up persistence test data...{RESET}")
    http(f"{ORDER_URL}/user", "POST", {
        "command": "create", "id": 7001,
        "username": "persist_user7001", "email": "persist7001@test.com", "password": "pw",
    })
    http(f"{ORDER_URL}/product", "POST", {
        "command": "create", "id": 8001,
        "name": "persist_prod8001", "description": "persistence product",
        "price": 10.0, "quantity": 100,
    })
    http(f"{ORDER_URL}/order", "POST", {
        "command": "place order", "product_id": 8001, "user_id": 7001, "quantity": 7,
    })

    s, _ = http(f"{ORDER_URL}/user/7001", "GET")
    if s != 200:
        print(f"  {RED}Setup failed (could not create user 7001). "
              f"Skipping persistence tests.{RESET}")
        return

    # ══════════════════════════════════════════════════════════════════════════
    # TEST 1: Shutdown → docker restart → send /restart first → data persists
    # ══════════════════════════════════════════════════════════════════════════
    print(f"\n  {CYAN}[ Test 1 ] Shutdown → Restart  (data should PERSIST){RESET}")

    http(f"{ORDER_URL}/shutdown", "POST", {})
    time.sleep(2)

    # Docker only manages postgres — restart app services locally
    _local_restart("order-service", "user-service", "product-service")
    print(f"  services restarting locally...")

    # Poll POST /restart — this makes /restart the first command so data is preserved
    restart_s, restart_b = -1, ""
    for _attempt in range(40):
        restart_s, restart_b = http(f"{ORDER_URL}/restart", "POST", {}, timeout=3)
        if restart_s == 200:
            break
        time.sleep(2)

    if restart_s != 200:
        print(f"  {RED}Services did not come back up — skipping test 1{RESET}")
    else:
        check("  /restart as first command → 200", restart_s, restart_b, 200)

        s, _ = http(f"{ORDER_URL}/user/7001", "GET")
        check("  User 7001 still exists after Shutdown→Restart", s, _, 200)

        s, b = http(f"{ORDER_URL}/user/purchased/7001", "GET")
        check("  purchased/7001 → 200", s, b, 200)
        if s == 200:
            try:
                data = json.loads(b)
                qty = data.get("8001") or data.get(8001)
                if qty == 7:
                    _ok("  purchase {\"8001\": 7} persisted correctly")
                else:
                    _bad("  purchase data check",
                         f"expected 8001:7, got {b[:120]}")
            except Exception as e:
                _bad("  purchase data parse", str(e), b)

    # ══════════════════════════════════════════════════════════════════════════
    # TEST 2: Shutdown → docker restart → non-restart first cmd → data wiped
    # ══════════════════════════════════════════════════════════════════════════
    print(f"\n  {CYAN}[ Test 2 ] Shutdown → No Restart  (data should be WIPED){RESET}")

    # If Test 1's restart failed, services may be down — recover before continuing
    probe_s, _ = http(f"{ORDER_URL}/user/7001", "GET", timeout=3)
    if probe_s == -1:
        print(f"  Services not running after Test 1, restarting before Test 2...")
        _local_restart("order-service", "user-service", "product-service")
        for _ in range(40):
            probe_s, _ = http(f"{ORDER_URL}/user/7001", "GET", timeout=3)
            if probe_s != -1:
                break
            time.sleep(2)
        if probe_s == -1:
            print(f"  {RED}Services failed to restart — skipping test 2{RESET}")
            return

    # Create a fresh marker user so we can verify it's gone after wipe
    http(f"{ORDER_URL}/user", "POST", {
        "command": "create", "id": 7002,
        "username": "wipe_user7002", "email": "wipe7002@test.com", "password": "pw",
    })
    s, _ = http(f"{ORDER_URL}/user/7002", "GET")
    if s != 200:
        print(f"  {YELLOW}Could not create wipe marker user — skipping test 2{RESET}")
        return

    http(f"{ORDER_URL}/shutdown", "POST", {})
    time.sleep(2)

    # Docker only manages postgres — restart app services locally
    _local_restart("order-service", "user-service", "product-service")
    print(f"  services restarting locally...")

    if not _wait_order_service():
        print(f"  {RED}Services did not come back up — skipping test 2{RESET}")
        return

    # First command is NOT /restart — should trigger data wipe
    s, b = http(f"{ORDER_URL}/user/7002", "GET")
    check("  First cmd (GET user 7002) triggers wipe → 404", s, b, 404)

    s, _ = http(f"{ORDER_URL}/user/7001", "GET")
    check("  User 7001 gone after wipe", s, _, 404)

    s, b = http(f"{ORDER_URL}/user/purchased/7001", "GET")
    check("  purchased/7001 after wipe → 404", s, b, 404)

    print(f"\n  {GREEN}Persistence tests complete.{RESET}")


# ──────────────────────────────────────────────────────────────────────────────
# Docker reset helper
# ──────────────────────────────────────────────────────────────────────────────
def docker_reset():
    section("Resetting Services via Docker Compose")
    print("  Restarting order-service, user-service, product-service ...")
    rc, out = subprocess.run(
        ["docker", "compose", "restart", "order-service", "user-service", "product-service"],
        cwd=BASEDIR, capture_output=True, text=True,
    ).returncode, ""
    time.sleep(4)
    print(f"  rc={rc}")


# ──────────────────────────────────────────────────────────────────────────────
# Main
# ──────────────────────────────────────────────────────────────────────────────
def main():
    args = sys.argv[1:]
    skip_persistence = "--skip-persistence" in args
    do_reset         = "--reset" in args

    print(f"\n{BOLD}{'═' * 62}{RESET}")
    print(f"{BOLD}  CSC301 A2 – Integration Test Suite{RESET}")
    print(f"{BOLD}  Target: {ORDER_URL}{RESET}")
    print(f"{BOLD}{'═' * 62}{RESET}")

    if do_reset:
        docker_reset()

    section("Waiting for Order Service")
    if not wait_for_order_service():
        print(f"\n{RED}Order Service is not reachable. "
              "Start services with:  ./runme.sh -d{RESET}\n")
        sys.exit(1)

    test_users_a1()
    test_products_a1()
    test_orders_a1()
    test_purchased_a2()

    if skip_persistence:
        print(f"\n{YELLOW}Skipping persistence tests (--skip-persistence){RESET}")
    else:
        test_persistence_a2()

    # ── Summary ───────────────────────────────────────────────────────────────
    total = _pass + _fail
    colour = GREEN if _fail == 0 else RED
    print(f"\n{BOLD}{'═' * 62}{RESET}")
    print(f"{BOLD}  Results: {colour}{_pass} passed{RESET}{BOLD}, "
          f"{RED}{_fail} failed{RESET}{BOLD} / {total} total{RESET}")
    print(f"{BOLD}{'═' * 62}{RESET}\n")

    sys.exit(0 if _fail == 0 else 1)


if __name__ == "__main__":
    main()
