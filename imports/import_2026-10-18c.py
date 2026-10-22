# -*- coding: utf-8 -*-
"""Member import 2026-10-18, batch 3: S. Manojkumar (Tamil jathaga kurippu). Same mechanism as the earlier batches."""
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
M = dict(firstName="Manojkumar", lastName="S", gender="M", dob="1983-04-22", age="43", casteId=1, mobile="9566908331", pin=pin("9566908331"),
         location="Vellore", star="Magam", moonSign="Simmam", birthTime="07:25 PM", degree="UG", educationInDetail="B.Com",
         occupation="Typist (Permanent)", jobPlace="Principal District Court, Sathuvachari, Vellore", employedAt="GOVT", annualIncome="480000",
         fatherName="Surendranathan A", motherName="Nalini S", noOfSisters="2", sistersMarried="2", noOfBrothers="0",
         currentAddress="No.84, Kasthameedu Street, Kosapettai, Vellore - 632001", userStatus="APPROVED",
         about="Hi, I'm Manojkumar S, working as Typist (Permanent) at the Principal District Court, Vellore. Gotram: Jambu Maharishi. Looking forward to a happy future together.")
LAGNAM = "Thulam"
print("CREATE", json.dumps(M, ensure_ascii=False))
if "--apply" in sys.argv:
    r = call("/admin/users", "POST", M); d = r.get("data"); uid = d.get("userId") if isinstance(d, dict) else d
    print("   ->", r.get("code"), r.get("message"), d)
    if r.get("code") in (200, 201) and uid:
        a = call("/user-details/update-astrology-info", "POST", dict(userId=b64(uid), star=M["star"], moonSign=M["moonSign"], dosham="", birthTime=M["birthTime"], lagnam=LAGNAM))
        print("   lagnam ->", a.get("code"), a.get("message"))
        json.dump({"created": [{"userId": uid, "name": "Manojkumar S"}], "lagnam": a.get("code")}, open(os.path.join(HERE, "import_2026-10-18c.log.json"), "w"), indent=1)
        full = call(f"/user/getUserByUserId/{b64(uid)}").get("data") or {}
        ud = (full.get("userDetail") or [{}])[0]; raw = ud.get("astronomicInfo")
        try: raw = json.loads(raw) if isinstance(raw, str) else raw
        except Exception: pass
        ast = raw[0] if isinstance(raw, list) and raw else (raw or {})
        sub = call(f"/userSubscriptions/getActiveUserSubscriptionByUserId/{uid}"); plan = (sub.get("data") or {}).get("planTitle") if isinstance(sub.get("data"), dict) else sub.get("code")
        print("VERIFY", uid, full.get("memberId"), full.get("firstName"), full.get("lastName"), full.get("mobile"), full.get("location"), "| astro", ast.get("star"), ast.get("moon_sign"), "lag", ast.get("lagnam"), ast.get("birth_time"), "| plan", plan, "| active", full.get("isActive"))
        lg = call("/user/login", "POST", {"mobile": M["mobile"], "pin": "8331"}); print("LOGIN", lg.get("code"), "token" if (lg.get("data") or {}).get("token") else lg.get("message"))
