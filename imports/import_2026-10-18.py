# -*- coding: utf-8 -*-
"""Member import 2026-10-18 (client: a typed note, 8 sheet rows, biodata-2.pdf, a WhatsApp biodata image).

Runs through the production ADMIN API (POST/PUT /admin/users), not SQL — that path has signup parity
(Base64 PIN, Free user_subscriptions row, memberId), and 3306 is not reachable from this machine.
  python3 import_2026-10-18.py            -> preview only
  python3 import_2026-10-18.py --apply    -> create 8, update 3 (946 Pandiyan, 947 Amar Raj, 950 Sundaramoorthy),
                                             set lagnam where known, upload 2 photos; ids logged to import_2026-10-18.log.json
Decisions follow the 2026-10-08 import: PIN = last 4 of mobile; monthly salaries x12; unknown subcaste -> note in About;
members with no phone are profiles only; no DOB -> row still created but invisible to the age filter (flagged).
"""
import json, sys, base64, urllib.request, subprocess, os, time

BASE = "https://vaibhavvivaahamatrimony.com/BE"
H = {"User-Agent": "Mozilla/5.0", "Content-Type": "application/json"}
HERE = os.path.dirname(os.path.abspath(__file__))
PHOTOS = "/private/tmp/claude-501/-Users-prodian-Documents-Personal-VaibhavVivaahaApp/07aefd60-f0fa-4197-983a-ac81c3cbdc8d/scratchpad/photos"

def call(path, method="GET", body=None):
    for attempt in range(3):
        try:
            req = urllib.request.Request(BASE + path, data=json.dumps(body).encode() if body is not None else None, headers=H, method=method)
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.loads(r.read().decode(), strict=False)
        except urllib.error.HTTPError as e:
            return json.loads(e.read().decode(), strict=False)
        except Exception as e:
            if attempt == 2: raise
            time.sleep(5)

def pin(mobile): return base64.b64encode(mobile[-4:].encode()).decode()
def b64(uid): return base64.b64encode(str(uid).encode()).decode()

def about(first, last, occ=None, extras=()):
    s = f"Hi, I'm {first}{(' ' + last) if last else ''}" + (f", working as {occ}" if occ else '') + ". Looking forward to a happy future together."
    return (s + ' ' + ' '.join(extras)).strip()[:255]

# ---------- 8 new members (sheet rows; no phones on any of them)
NEW = [
 dict(firstName="Arumugam", gender="M", dob="1991-06-07", casteId=3, moonSign="Meenam", lagnam="Kumbam", degree="PG",
      educationInDetail="M.Sc", occupation="Marketing work", employedAt="PRIVATE", annualIncome="216000",
      fatherName="Subramani A", motherName="Rathnam S", noOfBrothers="1"),
 dict(firstName="Sriram", lastName="S", gender="M", dob="1999-04-11", casteId=4, moonSign="Magaram", lagnam="Mesham", star="Thiruvonam",
      degree="UG", educationInDetail="B.Sc", annualIncome="250000", fatherName="Sekar V", motherName="Vanaga S",
      extras=["Subcaste: Sozhiya Vellalar."]),
 dict(firstName="Sathish Velan", lastName="R", gender="M", dob="1993-05-30", casteId=3, degree="UG", educationInDetail="B.A Economics",
      annualIncome="360000", fatherName="Rasaya S", motherName="Susela R"),
 dict(firstName="Gnanadevan", lastName="R", gender="M", dob="1991-09-01", casteId=4, subcasteId=21, moonSign="Rishabam", lagnam="Kumbam",
      star="Rohini", degree="PG", educationInDetail="M.Tech", occupation="Lecturer", employedAt="PRIVATE", annualIncome="600000",
      fatherName="Ravi S", motherName="Shanthi K"),
 dict(firstName="Priyadharshini", gender="F", casteId=3, moonSign="Mesham", star="Bharani", degree="UG", educationInDetail="B.Sc",
      annualIncome="300000", fatherName="Shiva Kumar", motherName="Kaveri", noOfBrothers="1", extras=["Gotram: Shivakotram."]),
 dict(firstName="Yelumalai", lastName="A", gender="M", dob="2001-11-22", casteId=1, moonSign="Magaram", lagnam="Dhanusu", star="Avittam",
      degree="UG", educationInDetail="B.Com", occupation="Business", employedAt="SELF", fatherName="Annamalai C", motherName="Chandra A"),
 dict(firstName="Prakash", gender="M", casteId=1, degree="OTHERS", educationInDetail="Diploma"),
 dict(firstName="Maharajan", lastName="K", gender="M", casteId=4, degree="OTHERS", educationInDetail="Diploma", annualIncome="180000",
      extras=["Subcaste: Devendrakulam."]),
]
# ---------- 3 existing members (8 Oct import) enriched from the new documents
UPD = {
 946: dict(mobile="6381196352", pin=pin("6381196352"), subcasteId=8, motherName="Ramani N", fatherOccupation="Central Govt (retired), borewell",
           motherOccupation="Homemaker", placeOfBirth="Tiruvannamalai", location="Tiruvannamalai", jobPlace="Pothys, Salem",
           brothersMarried="0", currentAddress="Sengam Road, 2nd Street, Tiruvannamalai - 606601", motherLanguage="Tamil",
           about="Hi, I'm Pandiyan B, working as Manager at Pothys, Salem. Looking for a girl from a good family (10th/12th or any degree). Looking forward to a happy future together.",
           _lagnam="Mithunam", _astro=dict(star="Ayilyam", moonSign="Kadagam", birthTime="10:55 PM"), _photo="pandiyan.jpg"),
 947: dict(fatherName="Ravichandran", fatherOccupation="Business", motherOccupation="Business", placeOfBirth="Pondicherry",
           location="Kallakurichi", height="5'8\"", maritalStatus="Single", motherLanguage="Tamil", educationInDetail="Physician Assistant",
           occupation="Physician Assistant", jobPlace="Apollo Main Hospital, Chennai", noOfBrothers="1",
           currentAddress="131 West Street, Vengaivadi Village, Kallakurichi District - 606206",
           about="Hi, I'm Amar Raj, working as Physician Assistant at Apollo Main Hospital, Chennai. Looking forward to a happy future together.",
           _photo="amar.jpg"),
 950: dict(height="5'5\"", currentAddress="234 Pallathu Street, Munnur Post, Marakanam Taluk, Villupuram"),
}

def new_dto(m):
    first, last = m["firstName"], m.get("lastName", "")
    extras = list(m.get("extras", []))
    dto = dict(firstName=first, lastName=last or None, gender=m["gender"], dob=m.get("dob"), userStatus="APPROVED",
               casteId=m["casteId"], subcasteId=m.get("subcasteId"), location="Other", star=m.get("star"), moonSign=m.get("moonSign"),
               degree=m.get("degree"), educationInDetail=m.get("educationInDetail"), occupation=m.get("occupation"),
               employedAt=m.get("employedAt"), annualIncome=m.get("annualIncome"), fatherName=m.get("fatherName"),
               motherName=m.get("motherName"), noOfBrothers=m.get("noOfBrothers"), about=about(first, last, m.get("occupation"), extras))
    if m.get("dob"):
        y, mo, d = map(int, m["dob"].split('-')); t = time.localtime()
        dto["age"] = str(t.tm_year - y - ((t.tm_mon, t.tm_mday) < (mo, d)))
    return {k: v for k, v in dto.items() if v is not None}

apply = "--apply" in sys.argv
log = {"created": [], "updated": [], "lagnam": [], "photos": [], "errors": []}
for m in NEW:
    dto = new_dto(m)
    print("CREATE", json.dumps(dto, ensure_ascii=False))
    if not apply: continue
    r = call("/admin/users", "POST", dto)
    uid = (r.get("data") or {}).get("userId") if isinstance(r.get("data"), dict) else r.get("data")
    print("   ->", r.get("code"), r.get("message"), uid)
    if r.get("code") in (200, 201) and uid:
        log["created"].append({"userId": uid, "name": f"{m['firstName']} {m.get('lastName','')}".strip()})
        if m.get("lagnam"):
            a = call("/user-details/update-astrology-info", "POST", dict(userId=b64(uid), star=m.get("star") or "", moonSign=m.get("moonSign") or "", dosham="", birthTime="", lagnam=m["lagnam"]))
            print("   lagnam ->", a.get("code"), a.get("message")); log["lagnam"].append({"userId": uid, "code": a.get("code")})
    else:
        log["errors"].append({"create": dto["firstName"], "resp": r})
for uid, m in UPD.items():
    dto = {k: v for k, v in m.items() if not k.startswith("_")}
    print("UPDATE", uid, json.dumps(dto, ensure_ascii=False))
    if not apply: continue
    r = call(f"/admin/users/{uid}", "PUT", dto); print("   ->", r.get("code"), r.get("message"))
    if r.get("code") == 200: log["updated"].append(uid)
    else: log["errors"].append({"update": uid, "resp": r})
    if m.get("_lagnam"):
        a = call("/user-details/update-astrology-info", "POST", dict(userId=b64(uid), dosham="", lagnam=m["_lagnam"], **m["_astro"]))
        print("   lagnam ->", a.get("code"), a.get("message")); log["lagnam"].append({"userId": uid, "code": a.get("code")})
    if m.get("_photo"):
        out = subprocess.run(["curl", "-s", "-m", "120", "-A", "Mozilla/5.0", "-F", f"userId={uid}", "-F", f"file=@{PHOTOS}/{m['_photo']};type=image/jpeg", BASE + "/user/updateProfileImage"], capture_output=True, text=True).stdout
        print("   photo ->", out[:160]); log["photos"].append({"userId": uid, "resp": out[:200]})
if apply:
    json.dump(log, open(os.path.join(HERE, "import_2026-10-18.log.json"), "w"), indent=1)
    print("\nlog written; created", len(log["created"]), "updated", len(log["updated"]), "errors", len(log["errors"]))
