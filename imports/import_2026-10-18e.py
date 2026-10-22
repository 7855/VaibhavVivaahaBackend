# -*- coding: utf-8 -*-
"""Member import 2026-10-18, batch 5: two brides — N. Ezhilarasi (Puducherry) and S. Seema (Krishnagiri, with photo)."""
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
 dict(firstName="Ezhilarasi", lastName="N", gender="F", dob="1996-01-19", age="30", casteId=1, subcasteId=2, mobile="9487062584", pin=pin("9487062584"),
      location="Other", placeOfBirth="Puducherry", star="Pooradam", moonSign="Dhanusu", birthTime="09:07 AM", degree="UG", educationInDetail="B.Arch",
      occupation="Architect", employedAt="PRIVATE", height="5'3\"", fatherName="Nandagopal J (Late)", fatherOccupation="Asst. Agricultural Officer (Retd), Agriculture Dept, Puducherry",
      motherName="Saraswathi N", motherOccupation="Junior Engineer, Public Works Dept, Puducherry", noOfBrothers="1", noOfSisters="0",
      currentAddress="Plot No.105, Raghava Chettiyar Street, Sivagami Nagar, Ariyankuppam, Puducherry - 605007", userStatus="APPROVED",
      about="Hi, I'm Ezhilarasi N, working as an Architect in the private sector. Brother N. Prithiviraj (B.Tech Mech, M.Tech). Looking forward to a happy future together.",
      _lagnam="Kumbam"),
 dict(firstName="Seema", lastName="S", gender="F", dob="2000-11-06", age="25", casteId=1, mobile="9942398495", pin=pin("9942398495"),
      location="Krishnagiri", placeOfBirth="Krishnagiri", star="Sathayam", moonSign="Kumbam", birthTime="07:45 PM", degree="PG", educationInDetail="M.Sc, B.Ed",
      employedAt="UNEMPLOYED", height="5'5\"", physicalStatus="Normal", fatherName="Selvam B", fatherOccupation="Agriculture & Business",
      motherName="Saradhamani R", motherOccupation="Private School Teacher (M.Sc, B.Ed)", noOfBrothers="1", noOfSisters="0", familyStatus="Upper Middle Class",
      currentAddress="Krishnagiri - 635112", nativePlace="Kaveripattinam, Krishnagiri", userStatus="APPROVED",
      about="Hi, I'm Seema S (M.Sc, B.Ed). Brother holds B.Sc, M.Sc, DHT. Family: own house, 3 acres farmland and shops. Looking for a groom from Hosur. Looking forward to a happy future together.",
      _lagnam="Rishabam", _photo="seema.jpg"),
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
    json.dump(log, open(os.path.join(HERE, "import_2026-10-18e.log.json"), "w"), indent=1)
