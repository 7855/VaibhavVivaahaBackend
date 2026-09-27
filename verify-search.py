#!/usr/bin/env python3
"""
verify-search.py — prove the search API returns the right profiles.

Checking filter combinations by hand doesn't scale: some fields are set, some are null, and
"looks about right" hides off-by-one and silently-ignored filters. This runs each combination
twice — once through the live API, once as SQL built independently from the same inputs — and
diffs the returned userId sets. A mismatch names the exact profiles that differ.

The SQL here is written from the FILTER CONTRACT (what each field is meant to do), not copied
from UserRepository. That's deliberate: copying the query would make both sides agree even when
the query is wrong. This is how the annual-income bug was caught — the API returned 0 and the
independent SQL returned 210.

Usage
    python3 verify-search.py                 # run the built-in combination suite
    python3 verify-search.py --show 5        # also print the first 5 ids of each result

Requires: mysql client on PATH.
"""

import json
import subprocess
import sys
import urllib.request

API = "https://vaibhavvivaahamatrimony.com/BE/user/filterUsers"
DB = ["mysql", "-h", "200.141.11.79", "-u", "vaibhav", "-pPassword@123",
      "vaibhavVivaaha", "--connect-timeout=15", "-N", "-B", "-e"]

SHOW = 0


def sql(query):
    out = subprocess.run(DB + [query], capture_output=True, text=True)
    rows = [r for r in out.stdout.strip().split("\n") if r]
    if out.returncode != 0:
        raise RuntimeError(out.stderr.strip()[:400])
    return rows


def quote_list(values):
    return ",".join("'" + str(v).replace("'", "''") + "'" for v in values)


SNAPSHOT = None


def load_snapshot():
    """
    Pull every searchable profile ONCE, then evaluate all combinations in memory.

    The first version ran one `mysql` subprocess per combination. At 575 combinations that
    took 3 hours and the server eventually refused connections (ERROR 2003) — hundreds of
    fresh remote connections in a tight loop. One query up front removes both problems and
    leaves the run bounded only by API latency.

    Age is computed here (from dob, as the backend does) so the comparison never depends on
    the stale users.age varchar column.
    """
    q = """
    SELECT u.userId,
           COALESCE(u.gender,''), COALESCE(u.casteId,0), COALESCE(u.subcasteId,0),
           COALESCE(TIMESTAMPDIFF(YEAR,u.dob,CURDATE()),-1),
           CASE WHEN ud.annualIncome REGEXP '^[0-9]+$'
                THEN CAST(ud.annualIncome AS UNSIGNED)
                ELSE CAST(REGEXP_SUBSTR(ud.annualIncome,'^[0-9]+') AS UNSIGNED)*100000 END,
           COALESCE(u.location,''), COALESCE(ud.occupation,''),
           COALESCE(ud.employedAt,''), COALESCE(ud.degree,''),
           COALESCE(JSON_UNQUOTE(JSON_EXTRACT(ud.astroInfo,'$[0].star')),''),
           COALESCE(JSON_UNQUOTE(JSON_EXTRACT(ud.astroInfo,'$[0].dosham')),''),
           CASE WHEN u.profileImage IS NOT NULL THEN 1 ELSE 0 END,
           CASE WHEN ud.horoscope   IS NOT NULL THEN 1 ELSE 0 END
    FROM users u JOIN user_details ud ON ud.userId = u.userId
    WHERE u.isActive='Y' AND u.isUser <> 'ADM'
    """
    rows = []
    for line in sql(q):
        c = line.split("\t")
        rows.append({
            "userId": int(c[0]), "gender": c[1], "casteId": int(c[2]),
            "subcasteId": int(c[3]), "age": int(c[4]),
            "income": int(c[5]) if c[5] not in ("", "NULL") else None,
            "location": c[6], "occupation": c[7], "employedAt": c[8], "degree": c[9],
            "star": c[10], "dosham": c[11],
            "hasPhoto": c[12] == "1", "hasHoroscope": c[13] == "1",
        })
    others = set(int(x) for x in sql(
        "SELECT id FROM subcastes WHERE subcasteName='Others'"))
    return rows, others


def expected_ids(f):
    """Evaluate one filter combination against the snapshot. Mirrors the CONTRACT, not the code."""
    rows, others = SNAPSHOT
    out = set()
    for r in rows:
        if f.get("gender") and r["gender"] != f["gender"]:
            continue
        if f.get("casteId") and r["casteId"] != int(f["casteId"]):
            continue
        # subcaste: selected ids, PLUS anyone whose subcaste is literally 'Others'
        if f.get("subcasteIds"):
            picked = {int(x) for x in f["subcasteIds"]}
            if r["subcasteId"] not in picked and r["subcasteId"] not in others:
                continue
        # A NULL dob (snapshotted as -1) must FAIL any age filter, matching SQL semantics:
        # NULL <= 25 is unknown, so the backend excludes the row. Without this the harness
        # wrongly expected 5 dob-less profiles to pass a max-age search.
        if (f.get("minAge") or f.get("maxAge")) and r["age"] < 0:
            continue
        if f.get("minAge") and r["age"] < int(f["minAge"]):
            continue
        if f.get("maxAge") and r["age"] > int(f["maxAge"]):
            continue
        if f.get("minAnnualIncome"):
            if r["income"] is None or r["income"] < int(f["minAnnualIncome"]):
                continue
        if f.get("maxAnnualIncome"):
            if r["income"] is None or r["income"] > int(f["maxAnnualIncome"]):
                continue
        if f.get("location") and r["location"] != f["location"]:
            continue
        if f.get("occupation") and r["occupation"] not in set(f["occupation"]):
            continue
        if f.get("employedAt") and r["employedAt"] not in set(f["employedAt"]):
            continue
        if f.get("degree") and r["degree"] not in set(f["degree"]):
            continue
        if f.get("star") and r["star"] not in set(f["star"]):
            continue
        if f.get("dosham") and r["dosham"] not in set(f["dosham"]):
            continue
        # 'N' means NO filter — the query only narrows on 'Y'
        if f.get("profileImageStatus") == "Y" and not r["hasPhoto"]:
            continue
        if f.get("profilesWithHoroscope") == "Y" and not r["hasHoroscope"]:
            continue
        out.add(r["userId"])
    return out


def actual_ids(f):
    body = json.dumps(f).encode()
    # A browser-ish User-Agent is required: the default "Python-urllib/3.x" is rejected
    # upstream with 403 before the request ever reaches Spring (curl with the same body works).
    req = urllib.request.Request(API, data=body, headers={
        "Content-Type": "application/json",
        "User-Agent": "Mozilla/5.0 (VVM search verifier)",
        "Accept": "application/json",
    })
    with urllib.request.urlopen(req, timeout=40) as r:
        d = json.loads(r.read())
    if d.get("code") not in (200, 404):
        raise RuntimeError(f"API code={d.get('code')} msg={d.get('message')}")
    rows = d.get("data") or []
    ids = set()
    for row in rows:
        for key in ("userId", "id", "user_id"):
            if isinstance(row, dict) and row.get(key) is not None:
                ids.add(int(row[key]))
                break
    return ids


def run(name, filters):
    try:
        exp, act = expected_ids(filters), actual_ids(filters)
    except Exception as e:
        print(f"  ERROR  {name}\n         {e}")
        return False

    if exp == act:
        print(f"  PASS   {name}  ({len(act)} profiles)")
        if SHOW:
            print(f"         ids: {sorted(act)[:SHOW]}")
        return True

    missing, extra = sorted(exp - act), sorted(act - exp)
    print(f"  FAIL   {name}")
    print(f"         expected {len(exp)}, api returned {len(act)}")
    if missing:
        print(f"         API is MISSING {len(missing)}: {missing[:10]}")
    if extra:
        print(f"         API returned EXTRA {len(extra)}: {extra[:10]}")
    return False


# Each case sets some fields and leaves others null — that null-vs-set mix is exactly where
# filters get silently ignored, so it is the thing worth covering.
# CALLER_ID must be a real member — filterUsers uses it for the entitlement lookup and
# returns 403 without it. Use a Silver+ account to exercise the subcaste filter.
CALLER_ID = "706"  # live demo account (Krish Selva). 702 was deleted 2026-08-18.

BASE = {"gender": "F", "casteId": 1, "profileImageStatus": "N",
        "profilesWithHoroscope": "N", "userId": CALLER_ID}

CASES = [
    ("no filters at all",            {"userId": CALLER_ID}),
    ("gender only",                  {"userId": CALLER_ID, "gender": "F"}),
    ("caste only",                   {"userId": CALLER_ID, "casteId": 1}),
    ("age 18-30",                    {**BASE, "minAge": 18, "maxAge": 30}),
    ("age 18-60",                    {**BASE, "minAge": 18, "maxAge": 60}),
    ("min age only",                 {**BASE, "minAge": 40}),
    ("max age only",                 {**BASE, "maxAge": 25}),
    ("income 3L-10L",                {**BASE, "minAnnualIncome": 300000, "maxAnnualIncome": 1000000}),
    ("min income only",              {**BASE, "minAnnualIncome": 1500000}),
    ("max income only",              {**BASE, "maxAnnualIncome": 300000}),
    ("photo=Y",                      {**BASE, "profileImageStatus": "Y"}),
    ("horoscope=Y",                  {**BASE, "profilesWithHoroscope": "Y"}),
    ("photo=Y + horoscope=Y",        {**BASE, "profileImageStatus": "Y", "profilesWithHoroscope": "Y"}),
    ("subcaste Gounder/Naicker",     {**BASE, "subcasteIds": [2]}),
    ("dosham No Dosham",                  {**BASE, "dosham": ["No Dosham"]}),
    ("star Avittam",                 {**BASE, "star": ["Avittam"]}),
    ("age + income",                 {**BASE, "minAge": 18, "maxAge": 60,
                                      "minAnnualIncome": 300000, "maxAnnualIncome": 1000000}),
    ("age + income + photo",         {**BASE, "minAge": 18, "maxAge": 60,
                                      "minAnnualIncome": 300000, "maxAnnualIncome": 1000000,
                                      "profileImageStatus": "Y"}),
    ("everything at once",           {**BASE, "minAge": 18, "maxAge": 60,
                                      "minAnnualIncome": 200000, "maxAnnualIncome": 2000000,
                                      "subcasteIds": [2], "dosham": ["No Dosham"],
                                      "profileImageStatus": "Y", "profilesWithHoroscope": "Y"}),
]



# ─────────────────────────────────────────────────────────────────────────────
#  Generated coverage (--all)
#
#  The CASES list above is a fixed suite — useful, but it only proves the 19
#  combinations someone thought to write down. --all generates coverage instead:
#  every filter on its own, then every PAIR of filters together.
#
#  Pairwise is the standard technique here: exhaustive coverage of 15 filters is
#  combinatorially impossible, but the overwhelming majority of real filter bugs
#  show up either in a single filter or in the interaction of two — which is
#  exactly the shape of the income bug (income alone) and the subcaste-vs-Others
#  behaviour (subcaste + anything).
# ─────────────────────────────────────────────────────────────────────────────
FILTER_POOL = {
    "minAge":               18,
    "maxAge":               45,
    "minAnnualIncome":      300000,
    "maxAnnualIncome":      1000000,
    "subcasteIds":          [2],
    "dosham":               ["No Dosham"],
    "star":                 ["Avittam"],
    "degree":               ["B.E"],
    "occupation":           ["Engineer"],
    "employedAt":           ["PRIVATE"],
    "location":             "Chennai",
    "profileImageStatus":   "Y",
    "profilesWithHoroscope": "Y",
    "casteId":              1,
    "gender":               "F",
}


def generated_cases(depth=2, sample=0, seed=7):
    """
    depth=1  every filter on its own                       15 cases
    depth=2  + every PAIR                                 120 cases   (~2 min)
    depth=3  + every TRIPLE                               575 cases   (~10 min)
    sample=N N random combinations of any size            N cases

    Why not truly exhaustive: 15 filters on/off is 2**15 = 32,768 combinations. At roughly
    half a second each (one API call + one DB query) that is over four hours, and it buys
    almost nothing — filter bugs are either in a single filter or in the interaction of two.
    Both real bugs found here fit that: income alone, and subcaste-plus-anything. Use
    --sample for a random spot-check of deeper combinations.
    """
    import itertools
    import random
    base = {"userId": CALLER_ID, "gender": "F", "casteId": 1,
            "profileImageStatus": "N", "profilesWithHoroscope": "N"}
    keys = list(FILTER_POOL)
    cases = []

    if sample:
        rnd = random.Random(seed)          # seeded so a failure is reproducible
        for i in range(sample):
            n = rnd.randint(1, min(6, len(keys)))
            picked = rnd.sample(keys, n)
            f = {**base}
            for k in picked:
                f[k] = FILTER_POOL[k]
            cases.append((f"random[{i}]: " + " + ".join(sorted(picked)), f))
        return cases

    for size in range(1, depth + 1):
        for combo in itertools.combinations(keys, size):
            f = {**base}
            for k in combo:
                f[k] = FILTER_POOL[k]
            tag = {1: "single", 2: "pair", 3: "triple"}.get(size, f"{size}-way")
            cases.append((f"{tag}: " + " + ".join(combo), f))
    return cases


# ─────────────────────────────────────────────────────────────────────────────
#  --real : enumerate the app's ACTUAL search contract
#
#  The app always sends: userId, gender (opposite of searcher), casteId (the
#  searcher's own caste), and an age range from the slider. Income is sent when
#  the slider is touched. The five multi-select fields — subcaste, education,
#  job sector, star, dosham — are each either absent, one value, or several.
#  Photo / horoscope are Y ("only profiles that have one") or N (no filter).
#
#  That makes the structural space:  3^5 multi-fields × 2 income × 2 photo
#  × 2 horoscope = 1,944 combinations — every one enumerated here.
#
#  Values are chosen to EXIST in the data (verified against production), so a
#  pass means "the right rows came back", not "0 == 0".
# ─────────────────────────────────────────────────────────────────────────────
MULTI_FIELDS = {
    "subcasteIds": ([2],            [2, 3]),
    "degree":      (["B.Tech"],     ["B.Tech", "MBA", "M.Sc"]),
    "employedAt":  (["PRIVATE"],    ["PRIVATE", "GOVT"]),
    "star":        (["Aswini"],     ["Aswini", "Rohini", "Avittam"]),
    "dosham":      (["No Dosham"],  ["No Dosham", "Rahu Dosham"]),
}
INCOME_STATES = (None, (200000, 2000000))


def real_cases():
    import itertools
    cases = []
    keys = list(MULTI_FIELDS)
    # 0 = absent, 1 = single value, 2 = multiple values
    for states in itertools.product((0, 1, 2), repeat=len(keys)):
        for income in INCOME_STATES:
            for photo in ("N", "Y"):
                for horo in ("N", "Y"):
                    f = {"userId": CALLER_ID, "gender": "F", "casteId": 1,
                         "minAge": 18, "maxAge": 60,
                         "profileImageStatus": photo,
                         "profilesWithHoroscope": horo}
                    tags = []
                    for k, st in zip(keys, states):
                        if st:
                            f[k] = MULTI_FIELDS[k][st - 1]
                            tags.append(f"{k}{'+' if st == 2 else ''}")
                    if income:
                        f["minAnnualIncome"], f["maxAnnualIncome"] = income
                        tags.append("income")
                    tags.append(f"photo={photo}")
                    tags.append(f"horo={horo}")
                    cases.append((" ".join(tags) or "base", f))
    return cases


if __name__ == "__main__":
    if "--show" in sys.argv:
        SHOW = int(sys.argv[sys.argv.index("--show") + 1])
    def arg(flag, default=None):
        return sys.argv[sys.argv.index(flag) + 1] if flag in sys.argv else default

    if "--real" in sys.argv:
        cases = real_cases()
        label = "REAL contract: 3^5 multi-fields x income x photo x horoscope = 1,944"
    elif "--sample" in sys.argv:
        cases = generated_cases(sample=int(arg("--sample", 50)))
        label = "random combinations"
    elif "--deep" in sys.argv:
        cases = generated_cases(depth=3)
        label = "every filter alone + every pair + every triple"
    elif "--all" in sys.argv:
        cases = generated_cases(depth=2)
        label = "every filter alone + every pair"
    else:
        cases = CASES
        label = "fixed suite (use --all / --deep / --sample N for generated coverage)"
    print("\nloading profile snapshot from the database…")
    SNAPSHOT = load_snapshot()
    print(f"  {len(SNAPSHOT[0])} searchable profiles loaded\n")

    print(f"verifying {len(cases)} filter combinations — {label}\n{API}\n")
    results = [run(n, f) for n, f in cases]
    ok, total = sum(results), len(results)
    print(f"\n  {ok}/{total} passed" + ("" if ok == total else "  ← investigate the FAIL rows above"))
    sys.exit(0 if ok == total else 1)
