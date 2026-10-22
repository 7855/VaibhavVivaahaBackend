# -*- coding: utf-8 -*-
"""Member import 2026-10-18, batch 8: Murali M (Goa, photo), Deepan Chakravarthi S (Kallakurichi), Gokul V (Kanchipuram), Sugavaneswari P V (bride, Kanchipuram)."""
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
 dict(firstName="Murali", lastName="M", gender="M", dob="1989-07-04", age="37", casteId=1, mobile="9843897668", pin=pin("9843897668"),
      location="Other", placeOfBirth="Puducherry", nativePlace="Ariyankuppam, Puducherry", star="Punarpusam", moonSign="Mithunam", birthTime="07:25 AM",
      degree="ENGINEER", educationInDetail="B.Tech (Mech)", occupation="Manager", jobPlace="Built-up Engineering Pvt Ltd, Goa", employedAt="PRIVATE",
      annualIncome="1200000", height="5'7\"", physicalStatus="Normal", fatherName="Mohankumar M", fatherOccupation="Private company (retd)",
      motherName="Jaya M", motherOccupation="Homemaker", noOfBrothers="1", noOfSisters="0", currentAddress="Puducherry - 605007", userStatus="APPROVED",
      about="Hi, I'm Murali M, working as Manager at Built-up Engineering Pvt Ltd, Goa. Eldest of two; younger brother (DME). Family: own house and 2 plots in Puducherry. Looking forward to a happy future together.",
      _lagnam="Kadagam", _photo="murali.jpg"),
 dict(firstName="Deepan Chakravarthi", lastName="S", gender="M", dob="1997-05-21", age="29", casteId=1, mobile="9514718166", pin=pin("9514718166"),
      location="Kallakurichi", star="Visagam", moonSign="Thulam", birthTime="09:50 AM", degree="ENGINEER", educationInDetail="B.E Civil (CIT, Coimbatore)",
      occupation="Assistant Engineer", jobPlace="Fisheries Department, Cuddalore", employedAt="GOVT", annualIncome="900000",
      fatherName="Shanmugam R", motherName="Ramani S", noOfSisters="1", sistersMarried="1", noOfBrothers="0",
      currentAddress="Ayyanar Koil Street, Sirulapattu Village, Ulundurpet Taluk, Kallakurichi District - 607204", userStatus="APPROVED",
      about="Hi, I'm Deepan Chakravarthi S, Assistant Engineer in the Fisheries Department, Cuddalore. Elder sister (Inspector, HR&CE) is married. Family: 10 acres, 2 houses. Looking for an educated, good-natured girl; govt job a plus.",
      _lagnam="Kadagam"),
 dict(firstName="Gokul", lastName="V", gender="M", dob="1999-06-18", age="27", casteId=1, mobile="7708464977", pin=pin("7708464977"),
      location="Kanchipuram", star="Magam", moonSign="Simmam", degree="PG", educationInDetail="B.E, MBA", occupation="Paver block manufacturing (own business)",
      employedAt="SELF", annualIncome="1080000", physicalStatus="Normal", fatherName="Vijayakumar C", fatherOccupation="Agriculture (10 acres, 2 pumpsets)",
      motherName="Meena V", noOfBrothers="1", brothersMarried="0", noOfSisters="0",
      currentAddress="Vinayagar Koil Street, Meyyur Odai Village, Kanchipuram District", userStatus="APPROVED",
      about="Hi, I'm Gokul V (B.E, MBA), running a paver block manufacturing business. Gotram: Jambu Maharishi. Blood group O+. Looking forward to a happy future together.",
      _lagnam="Thulam"),
 dict(firstName="Sugavaneswari", lastName="P V", gender="F", dob="2002-07-02", age="24", casteId=1, mobile="7200145966", pin=pin("7200145966"),
      location="Kanchipuram", placeOfBirth="Salem", star="Uthirattathi", moonSign="Meenam", dosham="Chevvai Dosham", birthTime="07:25 AM",
      degree="UG", educationInDetail="B.Sc (Physics), B.Ed", physicalStatus="Normal", fatherName="Palanisamy M", fatherOccupation="Co-optex",
      motherName="Vijayalakshmi P", noOfBrothers="1", brothersMarried="0", noOfSisters="0",
      currentAddress="No.133/50, Kamatchi Amman Sannathi Street, Kanchipuram", userStatus="APPROVED",
      about="Hi, I'm Sugavaneswari P V (B.Sc Physics, B.Ed). Gotram: Sivan Jambu Maharishi. Eldest; one younger brother. Family owns a house in Salem. Chevvai dosham (2, 4, 12) — parihara done. Looking forward to a happy future together.",
      _lagnam="Kadagam"),
]
log = {"created": [], "lagnam": [], "photos": [], "errors": []}
import sys as _s
RETRY = "--retry" in _s.argv
for m in NEW:
    if RETRY and m["firstName"] != "Deepan Chakravarthi": continue
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
    json.dump(log, open(os.path.join(HERE, "import_2026-10-18h" + ("-retry" if RETRY else "") + ".log.json"), "w"), indent=1)
