# -*- coding: utf-8 -*-
"""Member import 2026-10-20: Jeyavani R, Madhumitha S, Vidhurasarani S, Rajkumar R, Nathiya R, Sathish Kumar E (client biodata images + PDF)."""
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
 dict(firstName="Jeyavani", lastName="R", gender="F", dob="1996-01-22", age="30", casteId=1, subcasteId=2, mobile="9443074742", pin=pin("9443074742"),
      location="Cuddalore", star="Avittam", moonSign="Kumbam", degree="ENGINEER", educationInDetail="B.Tech (Computer Science & Engineering)",
      occupation="Preparing for TNPSC Group I & II exams", employedAt="UNEMPLOYED", height="5'6\"",
      fatherName="V. Ravi", fatherOccupation="Owner, Sri Sabari Medicals", motherName="R. Kalaiyarasi", motherOccupation="Housewife",
      noOfBrothers="0", noOfSisters="0", maritalStatus="Unmarried", motherLanguage="Tamil",
      currentAddress="No.12, Athikesavalu Nagar, Pathirikuppam, Cuddalore - 607401", userStatus="APPROVED",
      about="Hi, I'm Jeyavani R (B.Tech CSE), preparing for TNPSC Group I & II. Vanniya Kula Kshatriyar (Gounder). Only child. Family owns a terrace house, house plots, 6 acres of land and the medical shop building.",
      _lagnam="Rishabam", _birthTime="03:25 PM", _photo="jeyavani.jpg"),
 dict(firstName="Madhumitha", lastName="S", gender="F", dob="1999-02-02", age="27", casteId=1, mobile="9585544993", pin=pin("9585544993"),
      location="Cuddalore", star="Magam", moonSign="Simmam", degree="ENGINEER", educationInDetail="B.E (ECE)",
      occupation="Software Engineer", jobPlace="CTS, Chennai", employedAt="PRIVATE", annualIncome="600000", height="5'7\"",
      fatherName="C. Senguttuvan", fatherOccupation="B.E (Agriculture)", motherName="S. Indira", motherOccupation="Homemaker",
      noOfBrothers="1", noOfSisters="0", maritalStatus="Unmarried", motherLanguage="Tamil",
      currentAddress="Puliyur Kattusagai, Kullanchavadi, Kurinjipadi Taluk; Govindasamy Nagar, Villvanagar, Cuddalore", userStatus="APPROVED",
      about="Hi, I'm Madhumitha S (B.E ECE), working at CTS Chennai. Fair complexion. Brother S. Dineshbabu, B.E (ECE), Bangalore. Family owns 2 houses and 25 acres of land.",
      _lagnam="Simmam", _birthTime="07:24 PM"),
 dict(firstName="Vidhurasarani", lastName="S", gender="F", dob="1997-03-22", age="29", casteId=1, mobile="9943951118", pin=pin("9943951118"),
      location="Salem", degree="PG", educationInDetail="M.A (English)",
      occupation="Not working", employedAt="UNEMPLOYED",
      fatherName="K. Sathasivam", fatherOccupation="Manager, SDCC Bank, Attur Branch (M.Com)", motherName="M. Kalaiselvi",
      noOfBrothers="1", brothersMarried="0", noOfSisters="1", sistersMarried="1", maritalStatus="Unmarried", motherLanguage="Tamil",
      currentAddress="No.24, Sriram Garden, Ariyur Street, Pethanaickenpalayam, Salem District - 636109", userStatus="APPROVED",
      about="Hi, I'm Vidhurasarani S (M.A English). Vanniyar (Panthal). Elder sister S. Sailanthiri, Inspector of Police (married); younger brother S. Magandh, Accenture Bangalore (unmarried).",
      _lagnam="", _birthTime="09:20 AM"),
 dict(firstName="Rajkumar", lastName="R", gender="M", dob="1986-05-14", age="40", casteId=3, mobile="8056980101", pin=pin("8056980101"),
      location="Mayiladuthurai", star="Punarpusam", moonSign="Mithunam", degree="OTHERS", educationInDetail="D.ECE - Diploma in Electronics & Communication Engineering",
      occupation="Business - RK e-Sevai Maiyam & RK Cement Works; Panchayat President, Thalanjeri", employedAt="SELF", annualIncome="900000", height="5'10\"",
      fatherName="Late C. Rajangam", fatherOccupation="Co-operative Department (Retd)", motherName="R. Aachi", motherOccupation="Former Panchayat President",
      noOfBrothers="1", brothersMarried="1", noOfSisters="0", maritalStatus="Unmarried", motherLanguage="Tamil",
      currentAddress="Thalanjeri Main Road, Korukkai (Post), Mayiladuthurai Taluk & District - 609203", userStatus="APPROVED",
      about="Hi, I'm Rajkumar R (D.ECE), Panchayat President of Thalanjeri and running RK e-Sevai Maiyam and RK Cement Works. Fair complexion. Own terrace house and 5.5 acres of land. Brother R. Rajavelu, M.Sc (Agri), married.",
      _lagnam="Rishabam"),
 dict(firstName="Nathiya", lastName="R", gender="F", dob="1993-12-15", age="32", casteId=1, mobile="9629883877", pin=pin("9629883877"),
      location="Kallakurichi", placeOfBirth="Chinnasalem", star="Pooradam", moonSign="Dhanusu", degree="PG", educationInDetail="M.Com (CA)",
      occupation="Not working", employedAt="UNEMPLOYED", height="5'2\"",
      fatherName="S. Ramalingam", fatherOccupation="Farming, vegetable market", motherName="R. Lakshmi", motherOccupation="Housewife",
      noOfBrothers="0", noOfSisters="2", sistersMarried="2", maritalStatus="Unmarried", motherLanguage="Tamil",
      currentAddress="Thiru Vi Ka Nagar, Pandiyankuppam, Chinnasalem (TK), Kallakurichi", userStatus="APPROVED",
      about="Hi, I'm Nathiya R (M.Com CA). Arasu Vanniyar. Fair complexion. Two elder sisters, both married. Family owns a tiled house and 3 acres of land.",
      _lagnam="Kumbam"),
 dict(firstName="Sathish Kumar", lastName="E", gender="M", dob="1995-03-18", age="31", casteId=1, mobile="9444885491", pin=pin("9444885491"),
      location="Chennai", placeOfBirth="Chennai", star="Hastham", moonSign="Kanni", degree="ENGINEER", educationInDetail="B.E Mechanical Engineering",
      occupation="Senior Executive", jobPlace="CEVA Logistics (CMA CGM Group)", employedAt="PRIVATE", annualIncome="600000",
      fatherName="P. Elumalai", fatherOccupation="Chennai Corporation (Retd)", motherName="Rukmani", motherOccupation="Housewife",
      noOfBrothers="1", brothersMarried="0", noOfSisters="0", maritalStatus="Unmarried", motherLanguage="Tamil",
      currentAddress="No.34, Rajalakshmi Nagar, 8th Street, Pallikaranai, Chennai - 600100", userStatus="APPROVED",
      about="Hi, I'm Sathish Kumar E (B.E Mechanical), Senior Executive at CEVA Logistics. Vanniyakula Kshatriya. Medium complexion. Salary Rs.50,000/month plus rental income. Own houses in Pallikaranai and Velachery. One younger brother.",
      _lagnam="Rishabam", _birthTime="11:20 AM"),
]
for m in NEW:
    assert len(m["about"]) <= 255, (m["firstName"], len(m["about"]))
for m in NEW:
    for q in (m["mobile"],):
        d = call(f"/admin/users?page=0&size=8&search={q}").get("data"); rows = d if isinstance(d, list) else ((d or {}).get("content") or [])
        print("CHECK", m["firstName"], q, [(x.get("userId"), x.get("username")) for x in rows])
        if rows: sys.exit("number already registered - stopping")
log = {"created": [], "lagnam": [], "photos": [], "errors": []}
import sys as _s
RETRY = "--retry" in _s.argv
for m in NEW:
    dto = {k: v for k, v in m.items() if not k.startswith("_")}
    print("CREATE", m["firstName"])
    if "--apply" not in sys.argv: continue
    r = call("/admin/users", "POST", dto); d = r.get("data"); uid = d.get("userId") if isinstance(d, dict) else d
    print("   ->", r.get("code"), r.get("message"), d)
    if r.get("code") not in (200, 201) or not uid:
        log["errors"].append({"create": m["firstName"], "resp": r}); continue
    log["created"].append({"userId": uid, "name": f"{m['firstName']} {m['lastName']}"})
    if m.get("_lagnam") is not None or m.get("_birthTime"):
        a = call("/user-details/update-astrology-info", "POST", dict(userId=b64(uid), star=m.get("star",""), moonSign=m.get("moonSign",""), dosham=m.get("dosham",""), birthTime=m.get("_birthTime",""), lagnam=m.get("_lagnam","")))
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
    json.dump(log, open(os.path.join(HERE, "import_2026-10-20a" + ("-retry" if RETRY else "") + ".log.json"), "w"), indent=1)
