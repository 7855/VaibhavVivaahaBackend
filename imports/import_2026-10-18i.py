# -*- coding: utf-8 -*-
"""Member import 2026-10-18, batch 9: Harikrishnan M (Kanchipuram, photo), Murugan P (Villupuram, from a profile screenshot), Aravindhan A (Cuddalore)."""
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
 dict(firstName="Harikrishnan", lastName="M", gender="M", dob="1993-02-05", age="33", casteId=1, mobile="9842351773", pin=pin("9842351773"),
      location="Kanchipuram", star="Punarpusam", moonSign="Mithunam", birthTime="05:30 AM", degree="UG", educationInDetail="B.C.A., D.M.L.T.",
      occupation="Lab Technician", jobPlace="Blood Bank, Govt. Head Quarters Hospital, Kanchipuram", employedAt="GOVT", annualIncome="360000", height="5'8\"",
      fatherName="Mohan D", motherName="Padmavathi M", noOfBrothers="2", brothersMarried="2", noOfSisters="6", sistersMarried="6",
      currentAddress="No.134, near Chettikuttai School, Thimmasamudram Village & Post, Kanchipuram District", userStatus="APPROVED",
      about="Hi, I'm Harikrishnan M, Lab Technician at the Blood Bank, Govt. Head Quarters Hospital, Kanchipuram. Youngest of nine; all siblings married. Looking forward to a happy future together.",
      _lagnam="Magaram", _photo="hari.jpg"),
 dict(firstName="Murugan", lastName="P", gender="M", age="35", casteId=5, mobile="9003581703", pin=pin("9003581703"),
      location="Villupuram", degree="PG", educationInDetail="B.E, M.E (Anna University, Chennai)", occupation="Software Professional", jobPlace="HCL Technologies, Chennai",
      employedAt="PRIVATE", annualIncome="2000000", height="5'8\"", weight="65", maritalStatus="Unmarried", motherLanguage="Tamil", physicalStatus="Normal",
      profileCreatedFor="Myself", currentAddress="Elavathadi Village, Kaplambadi Post, Melmalayanur Taluk, Villupuram District", userStatus="APPROVED",
      about="Hi, I'm Murugan P, working as a software professional at HCL Technologies, Chennai (B.E, M.E). Looking forward to a happy future together."),
 dict(firstName="Aravindhan", lastName="A", gender="M", dob="1996-09-21", age="30", casteId=1, mobile="6379652587", pin=pin("6379652587"),
      location="Cuddalore", star="Pooradam", moonSign="Dhanusu", degree="UG", educationInDetail="B.Sc (Agri)", occupation="Agri Marketing", employedAt="PRIVATE",
      height="5'8\"", physicalStatus="Normal", fatherName="Annadurai K", fatherOccupation="Cane Officer, MRK Co-op Sugar Mill, Sethiyathope", motherName="Ushanandhini A",
      motherOccupation="Homemaker", noOfSisters="1", sistersMarried="1", noOfBrothers="0",
      currentAddress="Main Road, Thethampattu, Srimushnam Taluk, Cuddalore District - 608701", userStatus="APPROVED",
      about="Hi, I'm Aravindhan A (B.Sc Agri), working in agri marketing. Sister Anju (B.E) is married. Family: house, 30 acres farmland, 4 motors, plots. Looking forward to a happy future together.",
      _lagnam="Dhanusu"),
]
log = {"created": [], "lagnam": [], "photos": [], "errors": []}
import sys as _s
RETRY = "--retry" in _s.argv
for m in NEW:
    if RETRY and m["firstName"] != "Murugan": continue
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
    json.dump(log, open(os.path.join(HERE, "import_2026-10-18i" + ("-retry" if RETRY else "") + ".log.json"), "w"), indent=1)
