# Restores subscription_plans price/isActive/isPopular from plans_backup_20260929.json.
# Sends all three fields on every call: the update endpoint resets isActive/isPopular to
# their entity defaults when they are left out.
import json
from api import call
for p in json.load(open('plans_backup_20260929.json'))['data']:
    r = call(f"/subscriptionPlans/updatePlan/{p['id']}", "PUT",
             {"price": p['price'], "isActive": p['isActive'], "isPopular": p['isPopular']})
    print(p['id'], p['title'], r.get('code'), (r.get('data') or {}).get('price'))
