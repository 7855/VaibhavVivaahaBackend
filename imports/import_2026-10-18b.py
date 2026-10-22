# -*- coding: utf-8 -*-
"""Member import 2026-10-18, batch 2 (four biodatas: Karthik S image, Madhan A Tamil jathagam, Lokesh G Tamil biodata,
Kishore Kumar Ravindran text). Same mechanism as import_2026-10-18.py (admin API + astro endpoint).
  python3 import_2026-10-18b.py [--apply]
"""
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

NEW = [
 dict(firstName="Lokesh", lastName="G", gender="M", dob="1996-01-01", age="30", casteId=1, subcasteId=2, mobile="9841430471", pin=pin("9841430471"),
      location="Chennai", placeOfBirth="Chennai", star="Bharani", moonSign="Mesham", birthTime="08:15 AM", degree="ENGINEER",
      educationInDetail="B.E Aeronautical", occupation="IT professional", employedAt="PRIVATE", annualIncome="720000", height="174 cm",
      fatherName="Govindaraji V", fatherOccupation="Business", motherName="Poongavanam G", noOfBrothers="1", brothersMarried="0",
      currentAddress="Ambattur, Chennai", userStatus="APPROVED",
      about="Hi, I'm Lokesh G, working in IT. Gotram: Jambu Maharishi. Looking forward to a happy future together.", _lagnam="Magaram"),
 dict(firstName="Kishore Kumar", lastName="Ravindran", gender="M", dob="1992-01-08", age="34", casteId=1, mobile="9840282705", pin=pin("9840282705"),
      location="Chennai", placeOfBirth="Chennai", star="Avittam", moonSign="Magaram", dosham="Sarpa Dosham", birthTime="06:00 AM", degree="PG",
      educationInDetail="B.E (Electrical & Electronics), M.E (Power Systems Engineering)", occupation="Senior Electronics Engineer",
      jobPlace="Valeo India Pvt Ltd, Navalur, Chennai", employedAt="PRIVATE", annualIncome="1200000", height="5'7\"", weight="79",
      physicalStatus="Normal", fatherName="Ravindran N", fatherOccupation="IAF (Retd), M.E.S Tambaram", motherName="Indra Ravindran R",
      motherOccupation="Housewife", noOfBrothers="0", noOfSisters="0", nativePlace="Chennai",
      currentAddress="No 2A, Ponnu Swamy Nagar, Kolathur, Chennai - 600099", userStatus="APPROVED",
      about="Hi, I'm Kishore Kumar Ravindran, working as Senior Electronics Engineer at Valeo India. Gotram: Jambu Maharishi. Blood group AB+. Looking forward to a happy future together.",
      _lagnam="Dhanusu"),
]
UPD = {
 961: dict(mobile="9345621776", pin=pin("9345621776"), location="Chennai", placeOfBirth="Chennai", star="Thiruvathirai", moonSign="Mithunam",
           birthTime="04:23 AM", degree="ENGINEER", educationInDetail="B.E Civil", occupation="Project Manager", jobPlace="PSU Homes Pvt Ltd",
           employedAt="PRIVATE", height="5'10\"", weight="70", fatherName="Santhana Krishnan D", fatherOccupation="Flat Promoter",
           motherName="Mythilisivagami S", motherOccupation="Housewife", noOfSisters="1", sistersMarried="1", noOfBrothers="0",
           about="Hi, I'm Karthik S, working as Project Manager at PSU Homes Pvt Ltd. Looking forward to a happy future together."),
 964: dict(location="Chennai", placeOfBirth="Ambattur, Chennai", birthTime="04:20 PM", height="5'8\"", occupation="Grant Thornton Bharat LLP",
           fatherName="Anandhan G", fatherOccupation="PWD Working Inspector", motherName="Thangam A", brothersMarried="1", sistersMarried="1",
           currentAddress="No.46, 5th Street, Muthamizh Nagar, Kallikuppam, Ambattur, Chennai - 600053",
           about="Hi, I'm Madhan A, working at Grant Thornton Bharat LLP. Looking forward to a happy future together."),
}
apply = "--apply" in sys.argv
log = {"created": [], "updated": [], "lagnam": [], "errors": []}
def astro(m, uid):
    a = call("/user-details/update-astrology-info", "POST", dict(userId=b64(uid), star=m.get("star",""), moonSign=m.get("moonSign",""),
             dosham=m.get("dosham",""), birthTime=m.get("birthTime",""), lagnam=m["_lagnam"]))
    print("   lagnam ->", a.get("code"), a.get("message")); log["lagnam"].append({"userId": uid, "code": a.get("code")})
for m in NEW:
    dto = {k: v for k, v in m.items() if not k.startswith("_")}
    print("CREATE", json.dumps(dto, ensure_ascii=False))
    if not apply: continue
    r = call("/admin/users", "POST", dto); uid = r.get("data"); print("   ->", r.get("code"), r.get("message"), uid)
    if r.get("code") in (200, 201) and uid:
        log["created"].append({"userId": uid, "name": f"{m['firstName']} {m['lastName']}"}); astro(m, uid)
    else: log["errors"].append({"create": m["firstName"], "resp": r})
for uid, m in UPD.items():
    dto = {k: v for k, v in m.items() if not k.startswith("_")}
    print("UPDATE", uid, json.dumps(dto, ensure_ascii=False))
    if not apply: continue
    r = call(f"/admin/users/{uid}", "PUT", dto); print("   ->", r.get("code"), r.get("message"))
    if r.get("code") == 200: log["updated"].append(uid)
    else: log["errors"].append({"update": uid, "resp": r})
if apply:
    json.dump(log, open(os.path.join(HERE, "import_2026-10-18b.log.json"), "w"), indent=1)
    print("\ncreated", len(log["created"]), "updated", len(log["updated"]), "errors", len(log["errors"]))
