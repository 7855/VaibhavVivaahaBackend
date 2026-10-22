# -*- coding: utf-8 -*-
"""Member import 2026-10-18, batch 11: Kokila A (bride, Tiruvannamalai, photo) and Venkatesan P (Kanchipuram, photo)."""
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
 dict(firstName="Kokila", lastName="A", gender="F", dob="1997-06-08", age="29", casteId=1, mobile="9443913134", pin=pin("9443913134"),
      location="Tiruvannamalai", placeOfBirth="Tiruvannamalai", star="Punarpusam", moonSign="Kadagam", birthTime="02:00 PM", degree="UG", educationInDetail="B.Sc (Agri)",
      physicalStatus="Normal", fatherName="Arunagiri S", fatherOccupation="Retired Teacher (M.A., M.Ed.)", motherName="Kotteeswari A",
      noOfBrothers="2", brothersMarried="0", noOfSisters="0", currentAddress="Sengam Main Road, opp. Bharat Petrol Bunk, Periyakolapadi, Tiruvannamalai - 606704", userStatus="APPROVED",
      about="Hi, I'm Kokila A (B.Sc Agri). Gotram: Jambu Maharishi. Eldest; two younger brothers. Family owns 17 acres of farmland. Looking forward to a happy future together.",
      _lagnam="Kanni", _photo="kokila.jpg"),
 dict(firstName="Venkatesan", lastName="P", gender="M", dob="1992-04-10", age="34", casteId=1, mobile="9787276920", pin=pin("9787276920"),
      location="Kanchipuram", placeOfBirth="Thiruvengaranai, Kanchipuram", birthTime="10:00 PM", jobPlace="Foxconn Hai Pvt Ltd, Oragadam", employedAt="PRIVATE",
      annualIncome="180000", height="165 cm", physicalStatus="Normal", fatherName="Perumal K", motherName="Kamala P", noOfBrothers="2", brothersMarried="2",
      noOfSisters="1", sistersMarried="1", currentAddress="87, Durgai Amman Koil Street, Thiruvengaranai, Kanchipuram District - 631604", userStatus="APPROVED",
      about="Hi, I'm Venkatesan P, working at Foxconn Hai Pvt Ltd, Oragadam. Youngest of four; two elder brothers and an elder sister, all married. Family: own house and 35 cents of land. Looking forward to a happy future together.",
      _photo="venkat.jpg"),
]
log = {"created": [], "lagnam": [], "photos": [], "errors": []}
import sys as _s
RETRY = "--retry" in _s.argv
for m in NEW:
    if RETRY and m["firstName"] != "Venkatesan": continue
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
    json.dump(log, open(os.path.join(HERE, "import_2026-10-18k" + ("-retry" if RETRY else "") + ".log.json"), "w"), indent=1)
