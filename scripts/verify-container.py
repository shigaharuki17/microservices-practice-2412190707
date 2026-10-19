#!/usr/bin/env python3
"""Verify the live container API; run before/after a Compose recreation."""
import argparse
import json
from pathlib import Path
from urllib.request import Request, build_opener, ProxyHandler

parser = argparse.ArgumentParser()
parser.add_argument("phase", choices=["before", "after"])
parser.add_argument("--base-url", default="http://localhost:8081")
args = parser.parse_args()
base = args.base_url.rstrip("/")
evidence = Path(__file__).resolve().parents[1] / "docs/homework/week-05/evidence"
evidence.mkdir(parents=True, exist_ok=True)
opener = build_opener(ProxyHandler({}))

def request(method, path, expected, body=None):
    data = None if body is None else json.dumps(body).encode("utf-8")
    print(f"{method} {base}{path}", flush=True)
    if body is not None:
        print("Request: " + json.dumps(body, ensure_ascii=False))
    req = Request(base + path, data=data, method=method,
                  headers={"Content-Type": "application/json"})
    with opener.open(req, timeout=15) as response:
        payload = json.load(response)
        print(f"HTTP {response.status}")
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        if response.status != expected:
            raise RuntimeError(f"Expected HTTP {expected}")
        return payload

health = request("GET", "/actuator/health", 200)
if health.get("status") != "UP":
    raise RuntimeError("Application health is not UP")
if args.phase == "before":
    created = request("POST", "/api/repair-orders", 201, {
        "residentName": "Week05 resident", "contactPhone": "13900000005",
        "location": "Building 5 Room 501", "description": "Week05 persistence verification",
        "category": "PLUMBING"})
    order = request("GET", f"/api/repair-orders/{created['id']}", 200)
    # MySQL DATETIME(6) truncates Java nanoseconds; compare business fields here.
    keys = set(created) - {"createdAt", "updatedAt"}
    if any(order.get(key) != created[key] for key in keys):
        raise RuntimeError("Created and queried records differ")
    (evidence / "order-before.json").write_text(json.dumps(order, indent=2) + "\n", encoding="utf-8")
    print("CREATE_AND_QUERY=PASS")
else:
    before = json.loads((evidence / "order-before.json").read_text(encoding="utf-8"))
    after = request("GET", f"/api/repair-orders/{before['id']}", 200)
    if before != after:
        raise RuntimeError("Record changed after container recreation")
    (evidence / "order-after.json").write_text(json.dumps(after, indent=2) + "\n", encoding="utf-8")
    print("PERSISTENCE=PASS: ID and all response fields are unchanged")
