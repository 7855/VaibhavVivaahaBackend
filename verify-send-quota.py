#!/usr/bin/env python3
"""
verify-send-quota.py — prove the SEND_REQUEST quota is enforced by the SERVER.

Run right after deploying the 2026-09-26 jar, against the live API. Uses only the
hidden caste×plan test accounts (isActive='N'), cleans up after itself.

    python3 verify-send-quota.py                # production
    python3 verify-send-quota.py http://localhost:9100

What it checks (each line prints PASS/FAIL):
  1. A direct sendInterestRequest call (no app-side updateUsedCount) DECREMENTS the quota.
  2. updateUsedCount for the SEND_REQUEST feature no longer increments (peek only) —
     so an old APK's pre-check + the server's charge cannot double-charge.
  3. A duplicate send answers 400 and does NOT spend a request.
  4. A Free member (limit 3) is refused with 403 REQUEST_LIMIT_EXCEEDED on the 4th send,
     and the refusal does not create a row.
"""
import base64, json, sys, urllib.request, urllib.error

BASE = sys.argv[1].rstrip("/") if len(sys.argv) > 1 else "https://vaibhavvivaahamatrimony.com/BE"
H = {"User-Agent": "Mozilla/5.0", "Content-Type": "application/json"}
enc = lambda u: base64.b64encode(str(u).encode()).decode()

def call(path, method="GET", body=None):
    req = urllib.request.Request(BASE + path, data=(json.dumps(body).encode() if body is not None else None),
                                 headers=H, method=method)
    try:
        with urllib.request.urlopen(req, timeout=25) as r: return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        try: return json.loads(e.read().decode())
        except Exception: return {"code": e.code}

def quota(u):
    d = call(f"/userFeatureUsage/requestQuota/{enc(u)}").get("data") or {}
    return d.get("remaining"), d.get("total"), d.get("unlimited")

def send(a, b):  return call(f"/interestRequest/sendInterestRequest/{enc(a)}/{b}", "POST")
def unfriend(a, b): return call(f"/mailbox/removeConnection/{enc(a)}/{b}", "DELETE")
def sub_id(u): return (call(f"/userSubscriptions/getActiveUserSubscriptionByUserId/{u}").get("data") or {}).get("subscriptionId")

results = []
def check(name, ok, detail=""):
    results.append(ok); print(f"{'PASS' if ok else 'FAIL'}  {name}  {detail}")

TRIAL, FREE = 698, 696          # Trial male (25/plan), Free male (3/plan) — both Vanniyar, hidden
FEM = [699, 697, 701, 703]      # female Vanniyar test accounts to send to
SEND_REQUEST_FEATURE_ID = 4     # what the app hardcodes in updateSendRequestCount(uid, subId, 4)

# make sure the pairs start clean
for a in (TRIAL, FREE):
    for b in FEM: unfriend(a, b)

# 1. direct send decrements
r0 = quota(TRIAL)[0]
r = send(TRIAL, FEM[0])
r1 = quota(TRIAL)[0]
check("1. direct send is charged server-side", r.get("code") == 201 and r1 == r0 - 1,
      f"(code {r.get('code')}, remaining {r0} -> {r1})")

# 2. updateUsedCount for SEND_REQUEST is a peek, not a charge
sid = sub_id(TRIAL)
u = call(f"/userFeatureUsage/updateUsedCount/{TRIAL}/{sid}/{SEND_REQUEST_FEATURE_ID}", "POST")
r2 = quota(TRIAL)[0]
check("2. updateUsedCount(SEND_REQUEST) does not increment", u.get("code") == 200 and r2 == r1,
      f"(code {u.get('code')}, remaining {r1} -> {r2})")

# 3. duplicate send: 400, no charge
d = send(TRIAL, FEM[0])
r3 = quota(TRIAL)[0]
check("3. duplicate send refused without charge", d.get("code") == 400 and r3 == r2,
      f"(code {d.get('code')}, remaining {r2} -> {r3})")
unfriend(TRIAL, FEM[0])

# 4. Free member hits its limit of 3 → 4th is 403 REQUEST_LIMIT_EXCEEDED
rem, tot, unl = quota(FREE)
if unl or tot is None or tot > 3:
    check("4. Free-plan limit reached -> 403", False, f"(unexpected Free quota {rem}/{tot}, unlimited={unl})")
else:
    codes = []
    for b in FEM[:tot]:
        codes.append(send(FREE, b).get("code"))
    fourth = send(FREE, FEM[tot]) if len(FEM) > tot else {}
    check("4. Free-plan limit reached -> 403 REQUEST_LIMIT_EXCEEDED",
          all(c == 201 for c in codes) and fourth.get("code") == 403 and fourth.get("message") == "REQUEST_LIMIT_EXCEEDED",
          f"(first {tot}: {codes}; next: {fourth.get('code')} {fourth.get('message')} {fourth.get('data')})")
    sent_after = call(f"/mailbox/sent/{enc(FREE)}").get("data") or []
    check("   refused send created no row", len(sent_after) == tot, f"(sent rows = {len(sent_after)})")
    for b in FEM: unfriend(FREE, b)

# residue check
for a in (TRIAL, FREE):
    left = len(call(f"/mailbox/sent/{enc(a)}").get("data") or [])
    print(f"cleanup: user {a} sent rows now {left}")
print("\nALL PASS" if all(results) else "\nSOME FAILED")
sys.exit(0 if all(results) else 1)
