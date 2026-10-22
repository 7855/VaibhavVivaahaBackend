# -*- coding: utf-8 -*-
"""Member import 2026-10-18, batch 6: two doctor brides — Dr. Archana Natarajan (Chennai) and Dr. Priyadarshini V (Trichy), both with photos."""
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
 dict(firstName="Archana", lastName="Natarajan", gender="F", dob="1993-10-19", age="33", casteId=1, mobile="9943780940", pin=pin("9943780940"),
      location="Chennai", placeOfBirth="Kanpur, Uttar Pradesh", nativePlace="Tiruvannamalai", star="Kettai", moonSign="Viruchigam", birthTime="08:12 AM",
      degree="DOCTOR", educationInDetail="MBBS, MD Anaesthesia", occupation="Doctor (Anaesthesiologist)", height="5'5\"", weight="58",
      fatherName="Natarajan P", fatherOccupation="Retd Indian Air Force, Retd VAO", motherName="Mahalakshmi N", motherOccupation="Asst Headmistress",
      noOfSisters="1", sistersMarried="1", noOfBrothers="0", currentAddress="Avadi, Chennai", userStatus="APPROVED",
      about="Hi, I'm Dr. Archana Natarajan (MBBS, MD Anaesthesia). Sister Vaishnavi (B.E, Senior Software Engineer, London) is married. Looking forward to a happy future together.",
      _photo="archana.jpg"),
 dict(firstName="Priyadarshini", lastName="V", gender="F", dob="1995-04-19", age="31", casteId=1, mobile="9489830888", pin=pin("9489830888"),
      location="Tiruchirappalli", star="Kettai", moonSign="Viruchigam", birthTime="02:52 AM", degree="DOCTOR", educationInDetail="MBBS",
      occupation="Government Medical Officer", employedAt="GOVT", annualIncome="960000", height="164 cm", physicalStatus="Normal",
      fatherName="Veerannan T", fatherOccupation="Retd VAO", motherName="Padmini R", motherOccupation="Retd Teacher", noOfBrothers="1", noOfSisters="0",
      currentAddress="341, 3rd Cross, Shanmuga Nagar, Uyyakondanthirumalai, Trichy - 620102", userStatus="APPROVED",
      about="Hi, I'm Dr. Priyadarshini V (MBBS), working as a Government Medical Officer. Family: own houses, farmland and plots in Trichy. Looking forward to a happy future together.",
      _photo="priya.jpg"),
]
log = {"created": [], "lagnam": [], "photos": [], "errors": []}
for m in NEW:
    dto = {k: v for k, v in m.items() if not k.startswith("_")}
    print("CREATE", m["firstName"])
    if "--apply" not in sys.argv: continue
    r = call("/admin/users", "POST", dto); d = r.get("data"); uid = d.get("userId") if isinstance(d, dict) else d
    print("   ->", r.get("code"), r.get("message"), d)
    if r.get("code") not in (200, 201) or not uid:
        log["errors"].append({"create": m["firstName"], "resp": r}); continue
    log["created"].append({"userId": uid, "name": f"{m['firstName']} {m['lastName']}"})
    if m.get("_lagnam"):
        a = call("/user-details/update-astrology-info", "POST", dict(userId=b64(uid), star=m["star"], moonSign=m["moonSign"], dosham="", birthTime=m["birthTime"], lagnam=m["_lagnam"]))
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
    json.dump(log, open(os.path.join(HERE, "import_2026-10-18f.log.json"), "w"), indent=1)
