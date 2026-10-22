# -*- coding: utf-8 -*-
"""Member import 2026-10-18, batch 12: four grooms — Vijay Anand G (Puducherry/Maldives), Satheeshkumar B (Singapore, photo), Satheeshkumar R L (Cheyyar, photo), Ilansooriyan M (Cuddalore)."""
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
 dict(firstName="Vijay Anand", lastName="G", gender="M", dob="1991-09-01", age="35", casteId=1, mobile="9940807224", pin=pin("9940807224"),
      location="Other", placeOfBirth="Kanchipuram", star="Krithikai", moonSign="Rishabam", birthTime="08:35 AM", degree="ENGINEER", educationInDetail="B.E Civil Engineering",
      occupation="Senior Engineer (Civil)", jobPlace="Leo Trading Company, Maldives", employedAt="PRIVATE", annualIncome="1450000",
      fatherName="Giridharan K", fatherOccupation="Business", motherName="Bama G", noOfSisters="1", sistersMarried="0", noOfBrothers="0",
      currentAddress="No.33, 2nd Cross, Krishna Nagar, Puducherry", userStatus="APPROVED",
      about="Hi, I'm Vijay Anand G (B.E Civil), Senior Engineer at Leo Trading Company, Maldives (returning to India in March). Gotram: Jambu Maharishi. One younger sister.",
      _lagnam="Kanni"),
 dict(firstName="Satheeshkumar", lastName="B", gender="M", dob="1990-03-04", age="36", casteId=1, mobile="8098111139", pin=pin("8098111139"),
      location="Cuddalore", star="Rohini", moonSign="Rishabam", birthTime="06:32 AM", degree="UG", educationInDetail="B.B.A.", occupation="A/C Mechanic Supervisor",
      jobPlace="Singapore", employedAt="PRIVATE", annualIncome="1200000", height="165 cm", physicalStatus="Normal", fatherName="Balasubramanian P (Late)", motherName="Mallika B",
      noOfBrothers="1", brothersMarried="1", noOfSisters="1", sistersMarried="1",
      currentAddress="Sivan Koil Street, Usuppur, Ammapettai Post, Chidambaram Taluk, Cuddalore District", userStatus="APPROVED",
      about="Hi, I'm Satheeshkumar B (B.B.A.), working in Singapore as an A/C Mechanic Supervisor. Brother and sister both married. Family: own house and 6 plots.",
      _lagnam="Kumbam", _photo="satB.jpg"),
 dict(firstName="Satheeshkumar", lastName="R L", gender="M", dob="1987-09-11", age="39", casteId=1, mobile="9894994425", pin=pin("9894994425"),
      location="Tiruvannamalai", placeOfBirth="Kalpakkam", nativePlace="Vandavasi", star="Aswini", moonSign="Mesham", birthTime="02:30 PM", degree="ENGINEER",
      educationInDetail="D.EEE, B.E", occupation="Production Engineer", jobPlace="SADC Die Casting India Pvt Ltd, Chennai", employedAt="PRIVATE", annualIncome="480000",
      height="5'6\"", physicalStatus="Normal", fatherName="Loganathan R", fatherOccupation="Retd (Fire & Industrial Safety, Kalpakkam)", motherName="Lalitha L",
      motherOccupation="Homemaker", noOfSisters="1", sistersMarried="1", noOfBrothers="0", currentAddress="Cheyyar Taluk - 604407, Tiruvannamalai District", userStatus="APPROVED",
      about="Hi, I'm Satheeshkumar R L, Production Engineer at SADC Die Casting India, Chennai. Second born; elder sister married. Family: own house and plot.",
      _lagnam="Dhanusu", _photo="satRL.jpg"),
 dict(firstName="Ilansooriyan", lastName="M", gender="M", dob="1994-03-15", age="32", casteId=1, mobile="9443186060", pin=pin("9443186060"),
      location="Cuddalore", star="Aswini", moonSign="Mesham", birthTime="08:30 PM", degree="ENGINEER", educationInDetail="B.Tech (Mechanical)",
      occupation="Junior Cinematographer (SICA)", employedAt="PRIVATE", height="173 cm", physicalStatus="Normal",
      fatherName="Madhiyazhagan P", fatherOccupation="M.A.; political organiser (farm workers wing), Cuddalore district", motherName="Devika M", motherOccupation="Homemaker",
      noOfSisters="2", sistersMarried="2", noOfBrothers="0", currentAddress="153/164, Main Road, Thazhanallur Salai, Konur, Thittakudi Taluk, Cuddalore District", userStatus="APPROVED",
      about="Hi, I'm Ilansooriyan M (B.Tech Mech), a Junior Cinematographer with SICA. Two elder sisters (M.A. B.Ed; MBBS) both married. Family: 3.5 acres with pumpset, house, 2 plots.",
      _lagnam="Thulam"),
]
log = {"created": [], "lagnam": [], "photos": [], "errors": []}
import sys as _s
RETRY = "--retry" in _s.argv
for m in NEW:
    if RETRY and m["firstName"] != "Vijay Anand": continue
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
    json.dump(log, open(os.path.join(HERE, "import_2026-10-18l" + ("-retry" if RETRY else "") + ".log.json"), "w"), indent=1)
