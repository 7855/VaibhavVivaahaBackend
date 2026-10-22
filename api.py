#!/usr/bin/env python3
"""Thin API client for the VVM production backend (test harness)."""
import json, sys, base64, urllib.request, urllib.error

BASE = "https://vaibhavvivaahamatrimony.com/BE"
UA = {"User-Agent": "Mozilla/5.0", "Content-Type": "application/json"}

def enc(uid):  return base64.b64encode(str(uid).encode()).decode()
def dec(b64):  return base64.b64decode(b64).decode()

def call(path, method="GET", body=None, timeout=25):
    url = path if path.startswith("http") else BASE + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, headers=UA, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            raw = r.read().decode()
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
    except Exception as e:
        return {"_transport_error": str(e)}
    try:
        return json.loads(raw)
    except Exception:
        return {"_raw": raw[:400]}

def code(r): return r.get("code")
def data(r): return r.get("data")

if __name__ == "__main__":
    m = sys.argv[1] if len(sys.argv) > 1 else "GET"
    p = sys.argv[2]
    b = json.loads(sys.argv[3]) if len(sys.argv) > 3 else None
    print(json.dumps(call(p, m, b), indent=1)[:3000])
