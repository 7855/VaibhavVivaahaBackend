# -*- coding: utf-8 -*-
"""Member import 2026-10-18, batch 13: Hariharan I (groom, Puducherry, Kulalar → FCB)."""
import json, sys, base64, urllib.request, os, time, subprocess
BASE = "https://vaibhavvivaahamatrimony.com/BE"; H = {"User-Agent": "Mozilla/5.0", "Content-Type": "application/json"}
HERE = os.path.dirname(os.path.abspath(__file__))
PHOTOS = "/private/tmp/claude-501/-Users-prodian-Documents-Personal-VaibhavVivaahaApp/07aefd60-f0fa-4197-983a-ac81c3cbdc8d/scratchpad/photos"
def call(path, method="GET", body=None):
    for attempt in range(4):
        try:
            req = urllib.request.Request(BASE + path, data=json.dumps(body).encode() if body is not None else None, headers=H, method=method)
            with urllib.request.urlopen(req, timeout=60) as r: return json.loads(r.read().decode(), strict=False)
        except urllib.error.HTTPError as e: return json.loads(e.read().decode(), strict=False)
        except Exception:
            if attempt == 3: raise
            time.sleep(5)
pin = lambda m: base64.b64encode(m[-4:].encode()).decode()
b64 = lambda u: base64.b64encode(str(u).encode()).decode()
NEW = [
 dict(firstName="Hariharan", lastName="I", gender="M", dob="1999-10-26", age="26", casteId=5, mobile="9629788102", pin=pin("9629788102"),
      location="Other", placeOfBirth="Puducherry", star="Bharani", moonSign="Mesham", degree="PG", educationInDetail="M.Com",
      occupation="Photographer", jobPlace="Jothi Studio, Saram, Pondicherry", employedAt="PRIVATE", annualIncome="300000", height="5'3\"",
      fatherName="Iyappan", fatherOccupation="Driver", motherName="Pushpa Iyappan", motherOccupation="JIPMER (DLR)", noOfSisters="1", sistersMarried="1", noOfBrothers="0",
      currentAddress="Shanmuga Nagar, Karuvadikuppam, Puducherry - 605008", userStatus="APPROVED",
      about="Hi, I'm Hariharan I (M.Com), a photographer at Jothi Studio, Saram, Pondicherry. Community: Kulalar (MBC). Younger sister married. Contact is my mother's number."),
]
for q in ("Hariharan", "9629788102", "9384255706"):
    d = call(f"/admin/users?page=0&size=8&search={q}").get("data"); rows = d if isinstance(d, list) else ((d or {}).get("content") or [])
    hits = [(x.get("userId"), x.get("username"), x.get("isActive")) for x in rows if x.get("isActive") == "Y" or q.isdigit()]
    print("CHECK", q, hits)
    if hits and q.isdigit(): sys.exit("number already registered - stopping")
log = {"created": [], "lagnam": [], "photos": [], "errors": []}
import sys as _s
RETRY = "--retry" in _s.argv
for m in NEW:
    if RETRY and m["firstName"] != "Hariharan": continue
    dto = {k: v for k, v in m.items() if not k.startswith("_")}
    print("CREATE", m["firstName"])
    if "--apply" not in sys.argv: continue
    r = call("/admin/users", "POST", dto); d = r.get("data"); uid = d.get("userId") if isinstance(d, dict) else d
    print("   ->", r.get("code"), r.get("message"), d)
    if r.get("code") not in (200, 201) or not uid:
        log["errors"].append({"create": m["firstName"], "resp": r}); continue
    log["created"].append({"userId": uid, "name": f"{m['firstName']} {m['lastName']}"})
    if m.get("_lagnam"):
        a = call("/user-details/update-astrology-info", "POST", dict(userId=b64(uid), star=m["star"], moonSign=m["moonSign"], dosham=m.get("dosham",""), birthTime=m.get("birthTime",""), lagnam=m["_lagnam"]))
        print("   lagnam ->", a.get("code")); log["lagnam"].append({"userId": uid, "code": a.get("code")})
    if m.get("_photo"):
        out = subprocess.run(["curl", "-s", "-m", "120", "-A", "Mozilla/5.0", "-F", f"userId={uid}", "-F", f"file=@{PHOTOS}/{m['_photo']};type=image/jpeg", BASE + "/user/updateProfileImage"], capture_output=True, text=True).stdout
        print("   photo ->", out[:120]); log["photos"].append({"userId": uid, "resp": out[:200]})
    full = call(f"/user/getUserByUserId/{b64(uid)}").get("data") or {}
    ud = (full.get("userDetail") or [{}])[0]; raw = ud.get("astronomicInfo")
    try: raw = json.loads(raw) if isinstance(raw, str) else raw
    except Exception: pass
    ast = raw[0] if isinstance(raw, list) and raw else (raw or {})
    sub = call(f"/userSubscriptions/getActiveUserSubscriptionByUserId/{uid}"); plan = (sub.get("data") or {}).get("planTitle") if isinstance(sub.get("data"), dict) else sub.get("code")
    print("   VERIFY", uid, full.get("memberId"), full.get("firstName"), full.get("lastName"), full.get("gender"), full.get("mobile"), full.get("location"), "| astro", ast.get("star"), ast.get("moon_sign"), "lag", ast.get("lagnam"), ast.get("birth_time"), "| plan", plan, "| photo", "Y" if full.get("profileImage") else "-", "| active", full.get("isActive"))
    lg = call("/user/login", "POST", {"mobile": m["mobile"], "pin": m["mobile"][-4:]}); print("   LOGIN", lg.get("code"), "token" if (lg.get("data") or {}).get("token") else lg.get("message"))
if "--apply" in sys.argv:
    json.dump(log, open(os.path.join(HERE, "import_2026-10-19a" + ("-retry" if RETRY else "") + ".log.json"), "w"), indent=1)
