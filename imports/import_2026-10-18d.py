# -*- coding: utf-8 -*-
"""Member import 2026-10-18, batch 4: V. Sneha Varadhan (bride, Tamil jathaga kurippu). Same mechanism as the earlier batches."""
import json, sys, base64, urllib.request, os, time
BASE = "https://vaibhavvivaahamatrimony.com/BE"; H = {"User-Agent": "Mozilla/5.0", "Content-Type": "application/json"}
HERE = os.path.dirname(os.path.abspath(__file__))
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
M = dict(firstName="Sneha", lastName="Varadhan", gender="F", dob="2002-01-20", age="24", casteId=1, subcasteId=2, mobile="9443990389", pin=pin("9443990389"),
         location="Chengalpattu", star="Revathi", moonSign="Meenam", birthTime="03:24 PM", degree="LAWYER", height="5'6\"",
         educationInDetail="BA LLB (Hons) - SRM, Potheri; LLM - Dr. Ambedkar Government Law College, Taramani, Chennai",
         fatherName="Varadappa Naicker S K", fatherOccupation="Sri Sivasakthi Pharmacy, Lab & Hospital; agriculture", motherName="Valli Varadhan",
         motherOccupation="Homemaker", noOfSisters="1", sistersMarried="1", noOfBrothers="0", familyType="Joint",
         currentAddress="Theradi Street, near City Union Bank, Madurantakam, Chengalpattu District", userStatus="APPROVED",
         about="Hi, I'm Sneha Varadhan, a law graduate (BA LLB Hons, LLM). Gotram: Jambu Maharishi. Elder sister Dr. Keerthika Varadhan (BDS, MDS) is married. Looking for a well-educated groom (Doctor / Engineer / MBA / CA / Law / Govt job) from a good family.")
LAGNAM = "Rishabam"
for q in ("Sneha", "9443990389", "9344475471"):
    d = call(f"/admin/users?page=0&size=8&search={q}").get("data"); rows = d if isinstance(d, list) else ((d or {}).get("content") or [])
    hits = [(x.get("userId"), x.get("username"), x.get("isActive")) for x in rows if x.get("isActive") == "Y" or q.isdigit()]
    print("CHECK", q, hits)
    if hits and q.isdigit(): sys.exit("number already registered - stopping")
print("CREATE", json.dumps(M, ensure_ascii=False))
if "--apply" in sys.argv:
    r = call("/admin/users", "POST", M); d = r.get("data"); uid = d.get("userId") if isinstance(d, dict) else d
    print("   ->", r.get("code"), r.get("message"), d)
    if r.get("code") in (200, 201) and uid:
        a = call("/user-details/update-astrology-info", "POST", dict(userId=b64(uid), star=M["star"], moonSign=M["moonSign"], dosham="", birthTime=M["birthTime"], lagnam=LAGNAM))
        print("   lagnam ->", a.get("code"), a.get("message"))
        json.dump({"created": [{"userId": uid, "name": "Sneha Varadhan"}], "lagnam": a.get("code")}, open(os.path.join(HERE, "import_2026-10-18d.log.json"), "w"), indent=1)
        full = call(f"/user/getUserByUserId/{b64(uid)}").get("data") or {}
        ud = (full.get("userDetail") or [{}])[0]; raw = ud.get("astronomicInfo")
        try: raw = json.loads(raw) if isinstance(raw, str) else raw
        except Exception: pass
        ast = raw[0] if isinstance(raw, list) and raw else (raw or {})
        sub = call(f"/userSubscriptions/getActiveUserSubscriptionByUserId/{uid}"); plan = (sub.get("data") or {}).get("planTitle") if isinstance(sub.get("data"), dict) else sub.get("code")
        print("VERIFY", uid, full.get("memberId"), full.get("firstName"), full.get("lastName"), full.get("gender"), full.get("mobile"), full.get("location"), "| astro", ast.get("star"), ast.get("moon_sign"), "lag", ast.get("lagnam"), ast.get("birth_time"), "| plan", plan, "| active", full.get("isActive"))
        lg = call("/user/login", "POST", {"mobile": M["mobile"], "pin": "0389"}); print("LOGIN", lg.get("code"), "token" if (lg.get("data") or {}).get("token") else lg.get("message"))
