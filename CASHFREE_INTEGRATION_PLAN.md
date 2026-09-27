# Cashfree Payment Gateway — Integration Plan

Planned 2026-09-15 against Cashfree's web **redirect** checkout docs and the payment
**webhook** docs (version `2026-01-01`). No code has been written yet; this is the
blueprint. Sections 1–3 are the parts of the docs that matter; 4 onward is how they map
onto this codebase.

---

## 1. What Cashfree expects (from the docs)

### 1.1 Redirect checkout — three legs
1. **Server** creates an order → gets `payment_session_id`.
2. **Client** opens Cashfree checkout with that session id (JS SDK v3, `redirectTarget: "_self"`).
   Customer pays on Cashfree's page and is redirected to our `return_url`.
3. **Server** confirms the outcome. Docs are explicit: **do not trust the browser redirect**;
   the source of truth is the **webhook**, backed by `GET /pg/orders/{order_id}` on demand.

### 1.2 Create Order API
```
POST {base}/pg/orders
  x-client-id:      <App ID>
  x-client-secret:  <Secret>
  x-api-version:    2025-01-01
  Content-Type:     application/json
```
```json
{
  "order_id": "VVM-<our id>",                 // optional; we set it so we can correlate
  "order_amount": 4999.00,
  "order_currency": "INR",
  "customer_details": {
    "customer_id": "<userId>",
    "customer_name": "<first last>",
    "customer_email": "<email or omitted>",
    "customer_phone": "+91XXXXXXXXXX"
  },
  "order_meta": {
    "return_url": "https://vaibhavvivaahamatrimony.com/payment/return?order_id={order_id}",
    "notify_url": "https://<api-host>/payments/cashfree/webhook"
  },
  "order_expiry_time": "<ISO-8601, ~30 min out>",
  "order_note": "Gold plan (180 days)",
  "order_tags": { "userId": "...", "planId": "...", "purpose": "SUBSCRIPTION" }
}
```
Response carries `payment_session_id`, `cf_order_id`, `order_status` (`ACTIVE`).
`{order_id}` in `return_url` is a literal placeholder Cashfree substitutes.

Base URLs: sandbox `https://sandbox.cashfree.com/pg`, production `https://api.cashfree.com/pg`.
The secret must never leave the backend — the client only ever sees `payment_session_id`.

### 1.3 Client SDK
```html
<script src="https://sdk.cashfree.com/js/v3/cashfree.js"></script>
```
```js
const cashfree = Cashfree({ mode: "sandbox" | "production" });
cashfree.checkout({ paymentSessionId, redirectTarget: "_self" });
```

### 1.4 Verify
`GET {base}/pg/orders/{order_id}` with the same three headers. Paid when `order_status == "PAID"`.

### 1.5 Webhooks (version 2026-01-01)
Events for the **Payment** webhook group:

| `type` | Meaning | We do |
|---|---|---|
| `PAYMENT_SUCCESS_WEBHOOK` | payment captured | **fulfil** (activate plan) |
| `PAYMENT_FAILED_WEBHOOK` | bank/issuer failure for one attempt | record; order stays open for retry until expiry |
| `PAYMENT_USER_DROPPED_WEBHOOK` | customer abandoned | record only |
| `PAYMENT_CHARGES_WEBHOOK` | final TDR/settlement figures | store charges on the order, no user impact |

Headers: `x-webhook-signature`, `x-webhook-timestamp` (Unix **ms**), `x-webhook-version`,
`x-idempotency-key` (unique per payload, 2025-01-01+), `x-webhook-attempt`.

Signature (must use the **raw** body — never re-serialise parsed JSON, `170.00` would become `170`):
```
expected = Base64( HMAC-SHA256( timestamp + rawBody, clientSecret ) )
```
Payload shape: `{ data: { order{order_id, order_amount, order_currency, order_tags, order_note},
payment{cf_payment_id, payment_status, payment_amount, payment_time, bank_reference,
payment_method, payment_group, payment_message}, customer_details{...},
payment_gateway_details{...}, error_details{...}, charges_details{...} }, event_time, type }`.

Delivery: **at-least-once**. Default retry policy is 3 retries at 2 / 10 / 30 minutes until we
return **HTTP 200**. Dashboard → Webhooks → Logs → Batch Resend exists (max 24h window).
Configure under **Developers → Webhooks**, separately for sandbox and production, pick version
`2026-01-01`, subscribe to the Payment group. Saving pings the URL, so it must be live first.

---

## 2. What exists today (and what's wrong with it)

| Piece | State |
|---|---|
| `PaymentController` / `PaymentService` (`POST /payments/createOrder/{amount}`) | Razorpay stub. **Trusts the amount from the URL**, is tied to no user or plan, never verifies anything. Nothing in the app calls it (the `userApi.createOrder` wrapper is dead). Test keys are committed. **Retire it.** |
| `PAYMENT_REQUESTS` + `AdminService.approvePayment` | Manual QR/UTR flow. Admin approval creates the ACTIVE `user_subscriptions` row, sends push + in-app notification. Also handles `note = BOOST_PURCHASE`. |
| `AdminService.assignPlanToUser` | Same activation code duplicated a second time (adds email). |
| `keyValue.PAYMENT_MODE` | `QR` \| `CONTACT` \| `INFO`. Production = `CONTACT`. The app routes every upsell through `resolvePaymentMode()`. |
| `SecurityConfig` | `anyRequest().permitAll()`, JWT filter commented out. |
| App deps | `expo-web-browser`, `expo-linking`, `react-native-webview` all present. Scheme `vaibhavvivaaha://`. |
| Website | Vite + react-router, already has `/open-app`, login with stored `authToken`. |

Activation logic lives in two copies inside `AdminService`. The webhook needs a third caller,
so the first step is to extract it (section 4.4) rather than paste it a third time.

---

## 3. Design decisions

1. **Webhook is the fulfilment trigger. Return URL and polling only *read* status** (and may
   call Get Order to fast-path if the webhook is late), but fulfilment goes through one
   idempotent method regardless of who triggers it.
2. **Amount is computed server-side from `subscription_plans.price`.** The client sends a
   `planId`, never an amount. This fixes the stub's biggest hole.
3. **New table `payment_orders`, not rows in `PAYMENT_REQUESTS`.** The manual flow's columns
   (UTR, screenshot, admin verification) don't fit a gateway order, and mixing them would make
   `getPaymentRequestsByUser` show gateway orders as "pending admin approval". Admin gets its
   own list endpoint.
4. **Checkout page is hosted on the website** (`/pay/:orderId`), used by both the website and
   the mobile app (opened in the system browser via `expo-web-browser`). One integration,
   one return page. Not a WebView: UPI intent apps (GPay/PhonePe) need `upi://` handling that
   Custom Tabs do natively and WebView does not.
5. **New `PAYMENT_MODE` value `GATEWAY`.** Everything ships dark behind the existing flag;
   `CONTACT` stays the default until the store-policy question in section 8 is settled.
6. **Secrets via environment variables** (`${CASHFREE_CLIENT_ID}` etc.), matching how the
   datasource is already configured. No new plaintext secrets in `application.properties`.

---

## 4. Backend work

### 4.1 Configuration (`application.properties`)
```properties
cashfree.enabled=${CASHFREE_ENABLED:false}
cashfree.env=${CASHFREE_ENV:sandbox}                  # sandbox | production
cashfree.base.url=${CASHFREE_BASE_URL:https://sandbox.cashfree.com/pg}
cashfree.client.id=${CASHFREE_CLIENT_ID:}
cashfree.client.secret=${CASHFREE_CLIENT_SECRET:}     # also the webhook HMAC key
cashfree.api.version=2025-01-01
cashfree.return.url=https://vaibhavvivaahamatrimony.com/payment/return?order_id={order_id}
cashfree.notify.url=${CASHFREE_NOTIFY_URL:}           # https://<api-host>/payments/cashfree/webhook
cashfree.order.expiry.minutes=30
cashfree.webhook.max.skew.minutes=10                  # replay guard on x-webhook-timestamp
```
Remove `razorpay.*` keys and the `razorpay-java` dependency once the stub is deleted.

### 4.2 Entities (Hibernate `ddl-auto=update` creates the tables; indexes are hand-applied — see 4.7)

**`models/PaymentOrder.java` → `payment_orders`** (extends `GenericEntity`)

| column | type | notes |
|---|---|---|
| `id` | PK | |
| `orderId` | VARCHAR(45) UNIQUE | our merchant order id, e.g. `VVM-<userId>-<epochMs>-<6 random>` (Cashfree allows alphanumerics, `-`, `_`) |
| `cfOrderId` | VARCHAR(64) | from Create Order |
| `paymentSessionId` | VARCHAR(512) | returned to client; safe to expose |
| `userId` | BIGINT | |
| `planId` | BIGINT | |
| `purpose` | VARCHAR(32) | `SUBSCRIPTION` \| `BOOST_PURCHASE` |
| `amount` | DECIMAL(10,2) | copied from plan at creation |
| `currency` | VARCHAR(8) | `INR` |
| `orderStatus` | VARCHAR(24) | `ACTIVE` → `PAID` \| `EXPIRED` \| `TERMINATED` (mirrors Cashfree) |
| `cfPaymentId` | VARCHAR(64) | set on success/failure |
| `paymentStatus` | VARCHAR(24) | `SUCCESS` \| `FAILED` \| `USER_DROPPED` … |
| `paymentGroup` | VARCHAR(32) | `upi`, `credit_card`, `net_banking`… |
| `bankReference` | VARCHAR(64) | |
| `paymentTime` | DATETIME | |
| `failureReason` | VARCHAR(500) | `error_details.error_description` |
| `expiresAt` | DATETIME | what we sent as `order_expiry_time` |
| `fulfilledAt` | DATETIME | **null until the plan is activated — the idempotency guard** |
| `fulfilledBy` | VARCHAR(16) | `WEBHOOK` \| `VERIFY` \| `ADMIN` |
| `subscriptionId` | BIGINT | the `user_subscriptions` row created |
| `serviceCharge`, `serviceTax`, `settlementAmount` | DECIMAL | from `PAYMENT_CHARGES_WEBHOOK`, nullable |

**`models/PaymentWebhookEvent.java` → `payment_webhook_events`**

| column | notes |
|---|---|
| `idempotencyKey` VARCHAR(128) UNIQUE | `x-idempotency-key`; fallback `type + ":" + cf_payment_id` if header absent |
| `eventType`, `orderId`, `cfPaymentId`, `webhookVersion`, `attempt` | |
| `signatureValid` CHAR(1) | we still store rejected ones for forensics |
| `processed` CHAR(1), `processingError` VARCHAR(1000) | |
| `rawPayload` LONGTEXT | exact bytes received |
| `receivedAt` DATETIME | |

### 4.3 Service — `services/CashfreeService.java`

HTTP: `java.net.http.HttpClient` with `connectTimeout(5s)` and per-request `timeout(10s)`,
exactly the lesson from the 2026-09-04 EmailService stall. Jackson (already on the classpath)
for JSON.

**`createOrder(Long userId, Long planId, String purpose)`**
1. Load user; require `userStatus == APPROVED`.
2. Load plan; require `isActive == Y` (same guard as `createUserSubscription`), 404 if missing.
3. `amount = plan.price` (BOOST_PURCHASE: read the boost price from wherever `AddOnPaymentScreen` gets ₹149 — confirm source before coding; do not hardcode twice).
4. Reuse: if an `ACTIVE`, unexpired `payment_orders` row exists for the same user+plan+purpose, return its `paymentSessionId` instead of creating another order (prevents session spam and double-pay).
5. Build request per 1.2. `customer_phone` = user mobile normalised to `+91…`. `customer_email` only if non-blank.
6. POST; on non-2xx return `502` with Cashfree's `message`. Persist `PaymentOrder` (`orderStatus = ACTIVE`).
7. Return `{orderId, paymentSessionId, cfOrderId, amount, currency, env, expiresAt}`.

**`getOrderStatus(String orderId, Long requesterId)`**
1. Load our row; 404 if unknown; 403 if `userId != requesterId`.
2. If not yet `PAID` and not expired → call `GET /pg/orders/{orderId}`. If Cashfree says `PAID` →
   call `fulfil(order, "VERIFY")` (webhook may simply be late). Mirror `EXPIRED`/`TERMINATED`.
3. Return `{orderId, orderStatus, paymentStatus, planTitle, amount, fulfilled: bool, subscriptionId}`.

**`handleWebhook(String rawBody, HttpHeaders headers)`** — see 4.5.

**`fulfil(PaymentOrder order, String source)`** — the one activation path, `@Transactional`:
```sql
UPDATE payment_orders
   SET orderStatus='PAID', fulfilledAt=NOW(), fulfilledBy=:source
 WHERE orderId=:orderId AND fulfilledAt IS NULL
```
If affected rows == 0 → someone else already fulfilled; return silently. If 1 → we own it:
call `SubscriptionActivationService.activate(...)` (4.4), store the returned `subscriptionId`.
This single UPDATE is what makes webhook-vs-return-URL races and duplicate deliveries safe;
no `SELECT … FOR UPDATE` needed.

### 4.4 Extract `services/SubscriptionActivationService.java`
Pull the duplicated block out of `AdminService.approvePayment` and `assignPlanToUser`:
```java
UserSubscriptions activate(Long userId, SubscriptionPlan plan, String paymentReference, boolean sendEmail)
```
- Creates ACTIVE `UserSubscriptions` (`autoRenew N`, `startDate today`, `endDate +durationDays`, `paymentReference`).
- `pushNotificationService.sendPushNotificationToUserDirect(...)` (bypasses the plan gate, as today).
- In-app `Notification` (`PAYMENT_APPROVED`).
- `emailService.sendPlanAssignedEmail(...)` (retry-once sender already exists).
- Boost credits for `BOOST_PURCHASE` follow the existing `approvePayment` branch.

Payment reference format for gateway orders: `CF_<cf_payment_id>`. Then repoint the two
`AdminService` callers at it — behaviour unchanged, one copy of the code.

Known discrepancy to decide while here: `UserSubscriptionsService.createUserSubscription`
resets the SEND_REQUEST `user_feature_usage` counter on a new plan; `approvePayment` does not.
The extracted method should do whichever is intended, once, for all three callers.

### 4.5 Webhook endpoint — `POST /payments/cashfree/webhook`
Controller signature must be `@RequestBody String rawBody` (a `String`, so Spring hands over
the exact bytes; a DTO or `Map` would break the signature).

Order of operations, every step returning fast:
1. Read `x-webhook-signature`, `x-webhook-timestamp`, `x-idempotency-key`. Missing signature or timestamp → **401**, log.
2. Replay guard: `|now - timestamp| > cashfree.webhook.max.skew.minutes` → 401.
3. Compute `Base64(HmacSHA256(timestamp + rawBody, clientSecret))`; compare with
   `MessageDigest.isEqual` (constant-time). Mismatch → persist event with `signatureValid=N`, **401**.
4. Insert `PaymentWebhookEvent` with the idempotency key. `DataIntegrityViolationException`
   (duplicate key) → already seen → **200** immediately, do nothing else.
5. Parse JSON. Switch on `type`:
   - `PAYMENT_SUCCESS_WEBHOOK`: load our order by `data.order.order_id`. Unknown order → mark
     event `processingError`, still 200 (retrying won't help). **Check
     `data.payment.payment_amount` equals our stored amount and currency is INR**; mismatch →
     do **not** fulfil, flag for admin, 200. Otherwise set `cfPaymentId`, `paymentGroup`,
     `bankReference`, `paymentTime`, `paymentStatus=SUCCESS`, then `fulfil(order, "WEBHOOK")`.
   - `PAYMENT_FAILED_WEBHOOK` / `PAYMENT_USER_DROPPED_WEBHOOK`: record `paymentStatus`,
     `failureReason`; order stays `ACTIVE` (Cashfree allows re-attempts on the same order until expiry).
   - `PAYMENT_CHARGES_WEBHOOK`: copy `charges_details` onto the order.
   - anything else: mark processed, ignore.
6. Mark event `processed=Y`, return **200** with an empty body.

Any exception after step 4 → log with the event id, mark `processingError`, and still return
200? **No** — return 500 so Cashfree retries; but the idempotency row from step 4 would then
block the retry. So: insert the event row **after** successful processing in the same
transaction, and rely on the `fulfilledAt` UPDATE (4.3) for duplicate-safety of the
side-effecting part. Concretely: check-for-existing-key first (read), process, then insert
the event row; a concurrent duplicate at worst re-runs a no-op fulfil.

Security config: when the JWT filter is re-enabled, `/payments/cashfree/webhook` must be in
the permit-all list. Cloudflare sits in front of the API — add a WAF/bot-fight **skip rule**
for this exact path or deliveries will get a JS challenge and fail silently.

### 4.6 Controller — `controllers/CashfreeController.java` (`/payments/cashfree`)

| Method | Path | Body / params | Auth |
|---|---|---|---|
| `POST` | `/createOrder/{encodedUserId}` | `{planId, purpose?}` | member (ownership when JWT on) |
| `GET` | `/order/{orderId}/status` | `?requesterId=<encoded>` | member, must own the order |
| `POST` | `/webhook` | raw JSON | **public**, signature-verified |
| `GET` | `/admin/orders` | `?status&userId&page&size` | admin |
| `POST` | `/admin/orders/{orderId}/reverify` | — | admin: calls Get Order and fulfils if PAID (support tool for a lost webhook) |

Delete `PaymentController`/`PaymentService` (Razorpay) and the dead `userApi.createOrder` in the app.

### 4.7 Hand-applied indexes (ddl-auto never creates these — section 17 landmine)
```sql
ALTER TABLE payment_orders
  ADD UNIQUE KEY uk_po_order (orderId),
  ADD KEY idx_po_user (userId, createdAt),
  ADD KEY idx_po_status (orderStatus);
ALTER TABLE payment_webhook_events
  ADD UNIQUE KEY uk_pwe_idem (idempotencyKey),
  ADD KEY idx_pwe_order (orderId);
```
Add these to a `migration-2026-09-cashfree.sql` and run per environment.

### 4.8 Housekeeping job
Extend `SubscriptionSchedulerService` with a nightly pass:
`payment_orders WHERE orderStatus='ACTIVE' AND expiresAt < NOW()` → `EXPIRED` (local only, no API call).

---

## 5. Website work (`VaibhavVivaahaWeb`)

Two new routes in `App.jsx`:

**`/pay/:orderId`** — checkout launcher. Reads `paymentSessionId` + `env` from
`sessionStorage` (put there by whoever created the order) or, if absent, calls the status
endpoint to refuse gracefully. Loads the SDK script, then:
```js
Cashfree({ mode: env }).checkout({ paymentSessionId, redirectTarget: '_self' });
```
Shows a "Redirecting to secure payment…" state; a manual "Continue" button if the script is
blocked.

**`/payment/return`** — reads `order_id` from the query, polls
`GET /payments/cashfree/order/{orderId}/status` every 2 s for up to 30 s, then renders
PAID / still processing / failed with the plan name. On PAID (or timeout) it also tries
`window.location = 'vaibhavvivaaha://payment/return?order_id=…'` so a mobile user lands
back in the app, same trick `/open-app` already uses. Desktop users just see the result.

`PackagesSection` "Choose plan" → if logged in, `POST createOrder` then navigate to `/pay/:orderId`;
if not, open the existing `LoginModal` first.

---

## 6. Mobile app work (`VaibhavVivaahaApp`)

1. `PaymentMode` union gains `'GATEWAY'` in **both** `upgradeNavigation.ts` and
   `PaymentScreen.tsx` (they drifted once before — import the type, don't redeclare).
2. `resolvePaymentMode()`: `GATEWAY` routes exactly like `QR` (to `PaymentScreen`).
3. `PaymentScreen` in `GATEWAY` mode: replaces the QR/UTR/screenshot UI with plan summary +
   "Pay ₹X securely" button. On tap:
   ```ts
   const { orderId, paymentSessionId, env } = (await userApi.createCashfreeOrder(encodedUserId, planId)).data.data;
   const url = `https://vaibhavvivaahamatrimony.com/pay/${orderId}?session=${paymentSessionId}&env=${env}`;
   const res = await WebBrowser.openAuthSessionAsync(url, 'vaibhavvivaaha://payment/return');
   ```
   (`session` in the query is fine — a session id is not a secret, it's what the SDK needs.)
   Whether `res.type` is `success` or `dismiss`, then call the status endpoint, poll up to
   30 s while `orderStatus === 'ACTIVE'`, and on `fulfilled: true` refresh the user's
   subscription in `UserDataContext` and show the existing success state. `AddOnPaymentScreen`
   gets the same branch with `purpose: 'BOOST_PURCHASE'`.
4. Deep link: `app/payment/return.tsx` (or the existing linking handler) accepts
   `vaibhavvivaaha://payment/return?order_id=` for the case where the browser return page
   deep-links while the auth session was already dismissed.
5. `userApi.js`: add `createCashfreeOrder`, `getCashfreeOrderStatus`; delete `createOrder`.

---

## 7. Cashfree dashboard checklist
- Create sandbox App ID/secret; put in env vars on the dev box; run backend behind ngrok
  (the app's `axiosClient` already defaults to an ngrok URL).
- **Developers → Webhooks → Add endpoint** (sandbox): `https://<ngrok>/payments/cashfree/webhook`,
  version **2026-01-01**, Payment group. It must return 200 on the save-time ping.
- Whitelist `vaibhavvivaahamatrimony.com` under checkout domains before production.
- Repeat both for production with live keys; production `notify_url` and webhook registration
  must point at the real API host.

---

## 8. Store-policy note (decide before flipping the flag)
`CONTACT`/`INFO` exist because Google Play treats in-app purchase of these tiers as digital
content that must use Play Billing. Opening Cashfree from inside the app is alternative
billing. In India Google does allow alternative billing systems, but it requires enrolling
in that program and disclosing it; and every update is re-reviewed against what the server
serves that day. Also the privacy policy currently states "no third-party payment gateway" —
that sentence must change (Cashfree receives name, phone, email, amount) before `GATEWAY`
goes live. The website checkout has no such constraint. Recommendation: ship the website
flow first, enable `GATEWAY` in the app only after the Play enrolment question is answered.

---

## 9. Test plan
| Case | How | Expect |
|---|---|---|
| Happy path (UPI) | sandbox VPA `testsuccess@gocash` | webhook arrives, `fulfilledAt` set once, subscription row created, push + email sent |
| Failure | `testfailure@gocash` | `paymentStatus=FAILED`, order still `ACTIVE`, retry on same session works |
| Webhook late | block webhook path temporarily, complete payment, hit status endpoint | `VERIFY` fulfils; when webhook arrives later → no-op |
| Duplicate delivery | Dashboard → Logs → Resend | second delivery 200, no second subscription |
| Tampered body | replay a real webhook with one digit changed | 401, event stored `signatureValid=N` |
| Amount mismatch | craft a signed payload with wrong `payment_amount` (sandbox secret) | not fulfilled, flagged |
| Expired order | set `cashfree.order.expiry.minutes=15` (Cashfree minimum), wait | status `EXPIRED`, create again reuses nothing |
| Wrong owner | call status with another user's id | 403 |
| Inactive plan | plan `isActive='N'` | 400 `SUBSCRIPTION_PLAN_INACTIVE` |

---

## 10. Rollout order
1. Backend: entities, `SubscriptionActivationService` extraction, `CashfreeService`, controller, config, index SQL. `./mvnw -q compile`.
2. Deploy to production with `cashfree.enabled=false`; run the index SQL; confirm tables exist.
3. Sandbox keys on the server, register sandbox webhook, run section 9 against the deployed API.
4. Website `/pay` and `/payment/return` pages; deploy; end-to-end via website login.
5. App `GATEWAY` branch; internal build; test with sandbox.
6. Live keys, production webhook registration, domain whitelist, Cloudflare skip rule.
7. Website: flip plan CTAs live. App: flip `PAYMENT_MODE` to `GATEWAY` only after section 8.
8. Remove Razorpay stub + dependency + committed test keys. Update `CLAUDE.md` sections 8.1, 8.3, 8.4, 12, 16, 17, 18.
