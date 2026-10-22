# -*- coding: utf-8 -*-
"""Build the member import (2026-10-08) from members.csv.

Writes:
  preview.tsv  - one normalised row per member, for review before anything touches the DB
  import.sql   - one transaction: back up + deactivate every existing member, clear the one
                 clashing mobile, then insert users + user_details + Free subscription per row
                 (signup parity with UserService.createUser).
Decisions (client): unknown castes -> FREE CASTE BAR (original caste kept in About);
PIN = last 4 digits of mobile; members without a phone imported as profiles only.
"""
import csv, json, re, base64, datetime, io, os

HERE = os.path.dirname(os.path.abspath(__file__))
TODAY = datetime.date(2026, 10, 8)
TAG = 'import-2026-10-08'
FIRST_MEMBER_NO = 105          # memberId continues after UMR000104

CASTE = {'vanniyar': 1, 'naidu': 2, 'adidravidar': 3, 'mudaliyar': 4}
FCB = 5
SUBCASTE = {('naidu', 'gajulu balija'): 7, ('naidu', 'kamavar'): 6}   # Balija, Kamma

RASI = {'meenam': 'Meenam', 'kumbam': 'Kumbam', 'medunam': 'Mithunam', 'methunam': 'Mithunam',
        'mithunam': 'Mithunam', 'viruchigam': 'Viruchigam', 'viruchagam': 'Viruchigam',
        'kadagam': 'Kadagam', 'kanya': 'Kanni', 'kanni': 'Kanni', 'dhanush': 'Dhanusu',
        'dhanur': 'Dhanusu', 'simmam': 'Simmam', 'magaram': 'Magaram', 'mesham': 'Mesham',
        'reshabam': 'Rishabam', 'kumabam': 'Kumbam', 'virisikkam': 'Viruchigam'}
STAR = {'revathi': 'Revathi', 'uthratathi': 'Uthirattathi', 'uttaratadhi': 'Uthirattathi',
        'puratathi': 'Poorattathi', 'thiruvadirai': 'Thiruvathirai', 'thiruvathiral': 'Thiruvathirai',
        'anusham': 'Anusham', 'chitra': 'Chithirai', 'uthiradam': 'Uthiradam', 'magam': 'Magam',
        'shatabhisha': 'Sathayam', 'sathayam': 'Sathayam', 'punarpusam': 'Punarpusam',
        'ashwini': 'Aswini', 'karthikai': 'Krithikai', 'kottai': 'Kettai', 'uthiram': 'Uthiram',
        'pusam': 'Poosam', 'mirugasirisham': 'Mirugasiridham', 'mula': 'Moolam', 'ayilyam': 'Ayilyam'}
DISTRICT = {'chennai': 'Chennai', 'erode': 'Erode', 'katpadi,vellore': 'Vellore',
            'coimbatore': 'Coimbatore', 'kancheepuram': 'Kanchipuram', 'trichu': 'Tiruchirappalli',
            'thiruchirapalli': 'Tiruchirappalli', 'kerala': 'Other', 'vilupuram': 'Villupuram',
            'krishnagiri': 'Krishnagiri', 'thiruvallur': 'Tiruvallur', 'viruthunagar': 'Virudhunagar',
            'thiruvannamalai': 'Tiruvannamalai'}
GOVT_HINTS = ('canara bank', 'gst', 'govt', 'army')


def clean(s):
    return re.sub(r'\s+', ' ', (s or '').strip())


def title(s):
    s = clean(s)
    return ' '.join(w[:1].upper() + w[1:] for w in s.split(' ')) if s else ''


def split_name(raw):
    s = clean(raw).replace(' .', '.').replace('. ', '.')
    m = re.match(r'^(.*?)[.]\s*([A-Za-z.]+)$', s)          # "Raja.R", "Dhunisha.K.C"
    if m and len(m.group(2).replace('.', '')) <= 3:
        return title(m.group(1)), m.group(2).upper().strip('.')
    parts = s.split(' ')
    if len(parts) >= 2 and len(parts[-1]) <= 2:              # "Santhosh P"
        return title(' '.join(parts[:-1])), parts[-1].upper()
    return title(s), ''


def phones(raw, extra=''):
    nums = re.findall(r'\d+', (raw or '') + ' ' + (extra or ''))
    good = [n for n in nums if len(n) == 10 and n[0] in '6789']
    bad = [n for n in nums if n not in good]
    return good, bad


def birth_time(raw):
    s = clean(raw).lower()
    m = re.match(r'^(\d{1,2})[.:](\d{2})\s*(am|pm)', s)
    if not m:
        return ''
    return f"{int(m.group(1)):02d}:{m.group(2)} {m.group(3).upper()}"


def income(raw):
    s = clean(raw).lower().replace(',', '')
    if not s:
        return None
    m = re.match(r'^([\d.]+)\s*-\s*[\d.]+\s*lpa$', s)        # "5-10lpa" -> lower bound
    if m:
        return int(float(m.group(1)) * 100000)
    m = re.match(r'^([\d.]+)\s*lpa$', s)
    if m:
        v = float(m.group(1))
        return int(v if v >= 100000 else v * 100000)          # "1300000 lpa" is already rupees
    if re.match(r'^\d+$', s):
        v = int(s)
        return v * 12 if v < 300000 else v                    # below 3L read as a MONTHLY salary
    return None


def degree(raw):
    s = clean(raw).lower()
    if not s:
        return None
    if 'bds' in s or 'mbbs' in s:
        return 'DOCTOR'
    if re.search(r'\b(mba|m\.?tech|m\.?sc|mcom|m\.com|m\.?pharm|mpa|m\.?e)\b', s) or 'm.pharmacy' in s:
        return 'PG'
    if re.search(r'\b(b\.?e|be|b\.?tech)\b', s):
        return 'ENGINEER'
    if re.search(r'\b(b\.?a|bcom|b\.?com|bsc|b\.?sc|bba|bca)\b', s):
        return 'UG'
    return 'OTHERS'                                            # Diploma, DME, DCE, ITI, ...


def count_people(text, word):
    """'2 sisters', '1(married)', 'Vani (married)', 'Deepak' -> (count, married)."""
    t = clean(text).lower()
    if not t or t == '`':
        return '', ''
    m = re.search(r'(\d+)\s*' + word, t)
    if m:
        n = int(m.group(1))
    elif word in t:
        n = 1
    else:
        nums = re.findall(r'\d+', t)
        n = int(nums[0]) if nums else 1                        # a bare name counts as one
    married = n if 'married' in t else 0
    return str(n), str(married) if 'married' in t else ''


def sql(v):
    if v is None:
        return 'NULL'
    if isinstance(v, (int, float)):
        return str(v)
    return "'" + str(v).replace('\\', '\\\\').replace("'", "''") + "'"


rows = list(csv.reader(io.open(os.path.join(HERE, 'members.csv'), encoding='utf-8')))[1:]
members, notes = [], []
for i, r in enumerate(rows, 1):
    r = (r + [''] * 30)[:30]
    name, _, gender, caste, subc, dob, tob, rasi, lag, star, edu, occ, jobp, inc, fa, mo, bro, sis, addr, place, phone, email = r[:22]
    first, last = split_name(name)
    caste_key = clean(caste).lower()
    caste_id = CASTE.get(caste_key, FCB)
    original_caste = clean(caste) or clean(subc)
    sub_id = SUBCASTE.get((caste_key, clean(subc).lower()))
    d = datetime.datetime.strptime(clean(dob), '%d.%m.%Y').date()
    age = TODAY.year - d.year - ((TODAY.month, TODAY.day) < (d.month, d.day))
    good, bad = phones(phone, email if '@' not in email else '')
    mail = clean(email).lower() if '@' in email else None
    star_raw = clean(star) or (clean(lag) if clean(lag).lower() in STAR else '')
    star_v = STAR.get(star_raw.lower(), '')
    rasi_v = RASI.get(clean(rasi).lower(), '')
    place_v = DISTRICT.get(clean(place).lower())
    if not place_v and 'arani' in addr.lower():
        place_v = 'Tiruvannamalai'
    nb, bm = count_people(bro + (' ' + sis if 'brother' in sis.lower() else ''), 'brother')
    if 'brother' not in bro.lower() and bro and 'sister' in bro.lower():
        nb, bm = '', ''
    ns, sm = count_people(sis + (' ' + bro if 'sister' in bro.lower() else ''), 'sister')
    if 'sister' not in (sis + bro).lower() and not clean(sis):
        ns, sm = '', ''
    if clean(bro) and 'sister' in bro.lower() and 'brother' not in bro.lower():
        nb, bm = '', ''
    occ_v, jobp_v = clean(occ)[:60] or None, clean(jobp)[:100] or None
    blob = (occ + ' ' + jobp).lower()
    employed = 'GOVT' if any(h in blob for h in GOVT_HINTS) else ('PRIVATE' if clean(occ + jobp) else None)
    about = f"Hi, I'm {first}{(' ' + last) if last else ''}" + (f", working as {occ_v}" if occ_v else '') + \
            '. Looking forward to a happy future together.'
    extras = []
    if caste_id == FCB and original_caste:
        extras.append(f"Community: {title(original_caste)}.")
    if 'christian' in subc.lower():
        extras.append('Religion: Christian.')
    lag_v = RASI.get(clean(lag).lower())
    if lag_v:                                   # 'ayilyam' in Pandiyan's Lagnam column is his star
        extras.append(f"Lagnam: {lag_v}.")
    about = (about + ' ' + ' '.join(extras)).strip()[:255]
    m = dict(no=i, first=first, last=last, gender='F' if gender.lower().startswith('f') else 'M',
             caste_id=caste_id, original_caste=original_caste, sub_id=sub_id, dob=d.isoformat(), age=age,
             mobile=good[0] if good else None, alt=good[1:], bad_numbers=bad, email=mail,
             pin=base64.b64encode(good[0][-4:].encode()).decode() if good else None,
             birth_time=birth_time(tob), rasi=rasi_v, star=star_v, degree=degree(edu),
             edu_detail=clean(edu)[:200] or None, occ=occ_v, jobp=jobp_v, employed=employed,
             income=income(inc), father=title(fa), mother=title(mo), nb=nb, bm=bm, ns=ns, sm=sm,
             address=clean(addr)[:200] or None, location=place_v or 'Other', about=about)
    members.append(m)
    if not good:
        notes.append(f"#{i} {first} {last}: no phone - profile only, cannot log in")
    if bad:
        notes.append(f"#{i} {first} {last}: dropped invalid number(s) {bad}")
    if clean(rasi) and not rasi_v:
        notes.append(f"#{i}: unknown rasi {rasi!r}")
    if star_raw and not star_v:
        notes.append(f"#{i}: unknown star {star_raw!r}")
    if not place_v:
        notes.append(f"#{i} {first} {last}: no place given - location set to 'Other'")

# ---- preview
with io.open(os.path.join(HERE, 'preview.tsv'), 'w', encoding='utf-8') as f:
    cols = ['no', 'first', 'last', 'gender', 'age', 'caste_id', 'original_caste', 'sub_id', 'dob', 'mobile',
            'alt', 'email', 'birth_time', 'rasi', 'star', 'degree', 'occ', 'employed', 'income', 'location',
            'nb', 'bm', 'ns', 'sm', 'about']
    f.write('\t'.join(cols) + '\n')
    for m in members:
        f.write('\t'.join('' if m[c] is None else str(m[c]) for c in cols) + '\n')

# ---- SQL
mobiles = [m['mobile'] for m in members if m['mobile']]
# The backup is created BEFORE the transaction: CREATE TABLE causes an implicit commit in MySQL,
# which would otherwise end the transaction and let every later statement autocommit on its own.
out = ["SET NAMES utf8mb4;",
       "CREATE TABLE _bak_users_import_20261008 AS SELECT userId, isActive, mobile FROM users;",
       "START TRANSACTION;",
       "UPDATE users SET isActive='N' WHERE isActive='Y';",
       # an old test row (PENDING, user 129) holds one imported member's number; login looks members
       # up by mobile and needs exactly one match, so the old row gives the number up.
       "UPDATE users SET mobile=NULL WHERE mobile IN (" + ','.join(sql(x) for x in mobiles) + ");"]
for k, m in enumerate(members):
    member_id = f"UMR{FIRST_MEMBER_NO + k:06d}"
    astro = [{"star": m['star'], "dosham": "", "moon_sign": m['rasi'], "birth_time": m['birth_time']}]
    basic = {"marital_status": "", "place_of_birth": "", "mother_language": "", "physical_status": ""}
    fam = [{"house": "", "father": m['father'], "mother": m['mother'], "familyType": "", "no_of_sister": m['ns'],
            "family_status": "", "no_of_brother": m['nb'], "sister_married": m['sm'], "brother_married": m['bm'],
            "father_occupation": "", "mother_occupation": ""}]
    out.append(
        "INSERT INTO users (createdAt,createdBy,isActive,updatedAt,age,casteId,dob,email,firstName,gender,isBlocked,"
        "isOnline,isUser,lastName,location,memberId,mobile,pin,userStatus,view_count,subcasteId) VALUES ("
        f"NOW(6),'{TAG}','Y',NOW(6),{sql(str(m['age']))},{m['caste_id']},{sql(m['dob'])},{sql(m['email'])},"
        f"{sql(m['first'])},{sql(m['gender'])},'N',0,'FA',{sql(m['last'])},{sql(m['location'])},{sql(member_id)},"
        f"{sql(m['mobile'])},{sql(m['pin'])},'APPROVED',0,{sql(m['sub_id'])});")
    out.append("SET @u = LAST_INSERT_ID();")
    out.append(
        "INSERT INTO user_details (createdAt,createdBy,isActive,updatedAt,about,annualIncome,astroInfo,basicInfo,"
        "degree,educationInDetail,employedAt,familyInfo,jobPlace,occupation,presentAddress,userId) VALUES ("
        f"NOW(6),'{TAG}','Y',NOW(6),{sql(m['about'])},{sql(str(m['income']) if m['income'] else None)},"
        f"{sql(json.dumps(astro, ensure_ascii=False))},{sql(json.dumps(basic))},{sql(m['degree'])},{sql(m['edu_detail'])},"
        f"{sql(m['employed'])},{sql(json.dumps(fam, ensure_ascii=False))},{sql(m['jobp'])},{sql(m['occ'])},"
        f"{sql(m['address'])},@u);")
    out.append(
        "INSERT INTO user_subscriptions (createdAt,createdBy,isActive,updatedAt,autoRenew,boost_credits,endDate,"
        "paymentReference,startDate,status,subscriptionPlanId,userId) VALUES ("
        f"NOW(6),'{TAG}','Y',NOW(6),'N',0,NULL,NULL,CURDATE(),'ACTIVE',1,@u);")
out.append("COMMIT;")
io.open(os.path.join(HERE, 'import.sql'), 'w', encoding='utf-8').write('\n'.join(out) + '\n')

print(f"members: {len(members)}  (M {sum(m['gender']=='M' for m in members)}, F {sum(m['gender']=='F' for m in members)})")
print(f"with phone/login: {len(mobiles)}  profile-only: {len(members)-len(mobiles)}  emails: {sum(1 for m in members if m['email'])}")
print("caste ids:", {c: sum(m['caste_id'] == c for m in members) for c in (1, 2, 3, 4, 5)})
print("duplicate mobiles inside sheet:", len(mobiles) - len(set(mobiles)))
print('\n'.join(notes))
