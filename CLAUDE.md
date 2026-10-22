# CLAUDE.md — VaibhavVivaahaBackend

> **READ FIRST.** This is the source of truth for this project. Every Claude Code session must read this file before making changes and **update it before ending**. Stale CLAUDE.md = future session breaks.
>
> Sister docs:
> - `/Users/prodian/Documents/Personal/VaibhavVivaahaApp/CLAUDE.md` (mobile)
> - `/Users/prodian/Documents/Personal/adminpanel/CLAUDE.md` (admin panel)

---

## 1. Purpose
Spring Boot REST backend powering the **Vaibhav Vivaaha Matrimony** mobile app and admin panel. Owns user accounts, profiles, search/matching, chat, payments, subscriptions, push notifications, and the plan-feature gating logic.

## 2. Tech stack
- **Java**: 17+
- **Spring Boot** (Spring MVC, Spring Data JPA, Spring Security)
- **MySQL 8** — DB name `vvm_db`, hosted at `localhost:3306`
- **Hibernate** — `ddl-auto=update` so new entity columns/tables auto-create on restart
- **JJWT** — 15 min access token, 60 day refresh token (`utils/JwtUtil.java`)
- **Lombok** — `@Data` everywhere
- **AWS SDK** — S3 uploads (`uravugal` bucket, `ap-south-1`)
- **Firebase Admin SDK** — push notifications (FCM)
- **Cashfree Payment Gateway** — online payments via plain `java.net.http` calls (no SDK); web redirect checkout + HMAC-verified webhooks (`services/CashfreeService.java`). The Razorpay SDK + stub were removed 2026-09-15.
- **Zeptomail** — transactional email (OTPs, approval/rejection)
- **STOMP / SockJS / WebSocket** — chat + presence (`config/WebSocketConfig.java`, `UserWebSocketHandler.java`)
- **Maven** — `mvnw` wrapper at repo root

## 3. Repo layout
```
src/main/java/com/uravugal/matrimony/
├── MatrimonyApplication.java        # Spring Boot entry point
├── config/                          # Cross-cutting config beans
│   ├── AwsConfig.java               # S3 client bean
│   ├── GlobalExceptionHandler.java  # @ControllerAdvice
│   ├── JwtAuthenticationFilter.java # request → JWT validation
│   ├── PresenceTracker.java         # online-user map for WebSocket
│   ├── RateLimiter.java             # per-IP rate limit
│   ├── SecurityConfig.java          # Spring Security chain
│   ├── UserWebSocketHandler.java    # raw WS handler (chat/presence)
│   └── WebSocketConfig.java         # STOMP endpoint registry
├── controllers/                     # REST endpoints (one per resource)
├── dtos/                            # request/response DTOs
├── enums/                           # status / type enums
├── models/                          # JPA @Entity classes
├── repositories/                    # Spring Data JPA repositories
├── services/                        # business logic
└── utils/                           # JwtUtil, EncryptionUtils, RasiLordMapper, StarInfo
src/main/resources/
└── application.properties           # config — see section 4
```

## 4. Build & run
```bash
# install + run dev
./mvnw spring-boot:run

# package jar
./mvnw clean package

# run jar
java -jar target/uravugalmatrimony-*.jar
```

**`application.properties` keys** (`src/main/resources/application.properties`):
- `spring.application.name=uravugalmatrimony`
- `spring.jpa.hibernate.ddl-auto=update` ← **important**: new entity fields auto-add columns
- `spring.datasource.url=jdbc:mysql://localhost:3306/vvm_db?...&serverTimezone=Asia/Kolkata`
- `spring.datasource.username=root`, `spring.datasource.password=Krish@820`
- `server.address=0.0.0.0`, `server.port=9100`
- `spring.servlet.multipart.max-file-size=50MB`
- AWS S3: `aws.access.key.id=...`, `aws.region=ap-south-1`, `aws.s3.bucket=uravugal`
- Cashfree: `cashfree.enabled` (env `CASHFREE_ENABLED`, default false), `cashfree.env` sandbox|production, `cashfree.base.url` (`https://sandbox.cashfree.com/pg` | `https://api.cashfree.com/pg`), `cashfree.client.id` / `cashfree.client.secret` (env `CASHFREE_CLIENT_ID` / `CASHFREE_CLIENT_SECRET` — the secret is also the webhook HMAC key), `cashfree.api.version=2025-01-01`, `cashfree.return.url` (website `/payment/return?order_id={order_id}`), `cashfree.notify.url` (**`https://vaibhavvivaahamatrimony.com/BE/payments/cashfree/webhook`**), `cashfree.order.expiry.minutes=30`, `cashfree.webhook.max.skew.minutes=10`, `payments.boost.price=149`
- Zeptomail: `zeptomail.enabled=true`, templates `otp-registration`, `otp-reset`, `approval`, `rejection`
- JWT: hardcoded default `VaibhavVivaahaMatrimonySecretKey2024VVMAppSecureToken`, override via `jwt.secret`. Access expiry `jwt.expiration=900000` (15 min), refresh `jwt.refresh.expiration=5184000000` (60 days)
- App link: `app.link=https://vaibhavvivaahamatrimony.com/`

## 5. Architecture overview
```
Mobile App ─┐                                          ┌─→ MySQL (vvm_db)
            ├─→ axiosClient (Bearer token) ─→ Spring  ─┼─→ S3 (profile images, gallery)
Admin Panel ┘     │                            Boot    ├─→ Firebase FCM (push)
                  │                                    ├─→ Zeptomail (email)
            JwtAuthenticationFilter                    └─→ Razorpay (payments)
            (extracts userId from token)
```
- **Authentication**: `/user/login` (mobile + base64 PIN) returns user payload directly. Refresh handled by `/auth/refresh` (issues JWT). Token storage on backend = `UserEntity.refreshToken` column.
- **Plan gating**: every gated feature looks up `Features` by code → checks `PlanFeatures` row for the user's `subscriptionPlanId`. Free is hardcoded as `subscriptionPlanId == 1`.
- **Encoded user IDs**: most path params take `encodedUserId` = `Base64.encode(String.valueOf(userId))`. Backend decodes via `Base64.getDecoder().decode(...)`.

## 6. Conventions
- **Package naming**: `com.uravugal.matrimony.{config,controllers,dtos,enums,models,repositories,services,utils}`
- **Entity column names**: **camelCase**, NOT snake_case. e.g. `createdAt`, `isActive`, `featureId`, `subscriptionPlanId`. The `planFeatures` table is camelCase too, NOT `plan_features`.
- **Audit base**: every entity extends `GenericEntity` which adds `createdAt`, `createdBy`, `isActive` (`'Y'`/`'N'` enum), `updatedAt`, `updatedBy`.
- **`isActive` is `enum('Y','N')`**, not boolean. Use `ActiveStatus.Y` / `ActiveStatus.N`.
- **Response shape**: every endpoint returns `ResultResponse` (`code`, `status`, `message`, `data`) or `PaginatedResultResponse` (adds `paginationData`).
- **Error handling**: services catch `Exception`, set `code=500`, return; controllers usually delegate. Plan-gate failures use `code=403`, `message="PLAN_UPGRADE_REQUIRED"`.
- **Logging**: mostly `System.out.println`. No structured logger configured.

## 7. Domain model

### 7.1 Subscription plans (`subscription_plans` table)
**Verified against live `vvm_db` 2026-07-09** — the price column below was stale (Starter was documented at ₹199; the DB has ₹499). Re-verify this table directly against the DB (`SELECT id, title, price, duration_days, tagline FROM subscription_plans;`) before trusting it again if plans are re-seeded — this table drifted from reality once already.

| id | title    | price ₹  | duration_days | tagline (Tamil)              |
|----|----------|----------|---------------|-------------------------------|
| 1  | Free     | 0        | 0 (lifetime)  | உங்கள் பயணம் தொடங்குகிறது      |
| 2  | Starter  | 499      | 30            | முதல் அடி எடுங்கள்             |
| 3  | Classic  | 999      | 90            | தெளிவான தேர்வு                |
| 4  | Silver   | 2499     | 90            | இதயம் திறக்கும் நேரம்          |
| 5  | Gold     | 4999     | 180           | தங்க வாழ்க்கை தொடர்புகள்       |
| 6  | Platinum | 9999     | 9999 (until marriage) | திருமணம் வரை நம்மோட உதவி |

`subscription_plans` columns: `id`, `createdAt`, `createdBy`, `isActive`, `updatedAt`, `updatedBy`, `discount`, `duration_days`, `is_popular`, `original_price`, `period`, `price`, `savings`, `title`, **`tagline`** (added this session), **`plan_description`** (added this session).

### 7.2 Feature codes (`features` table — 32 entries, ids 1–33 with id 29 currently unused)
**Corrected 2026-07-09 against live `vvm_db`** — the previous version of this table (27 entries) was missing 5 real rows (28, 30–33) and had several tier values wrong (SEND_REQUEST/REQ_LIMITED numbers swapped between Starter and Classic, MESSAGE's starting tier wrong, PROFILE_VIEW_LIMIT's Classic value invented). Re-verify with `SELECT id, code FROM features ORDER BY id;` joined against `planFeatures` before trusting again.

| id | code                  | tier introduced |
|----|-----------------------|-----------------|
| 1  | BASIC_SEARCH          | Free (all plans have this row — not actually a differentiator) |
| 2  | ADV_SEARCH            | Classic+        |
| 3  | VIEW_PROFILE_DETAILS  | Free (LIMITED) / Starter+ (FULL) |
| 4  | SEND_REQUEST          | Free (3) / Starter (25) / **Classic (60 as of 2026-07-10, local `vvm_db` only — was 15, a lower-than-Starter downgrade trap confirmed with product; live `uravugal` RDS still has the old value of 15, not yet promoted)** / Silver+ (unlimited) |
| 5  | REQ_LIMITED           | Starter (25) / Classic (15 — **left unchanged**, see 2026-07-10 changelog entry: verify whether this or SEND_REQUEST is the field `UserFeatureUsageService` actually enforces before assuming it also needs bumping) — companion counter to SEND_REQUEST |
| 6  | REQ_UNLIMITED         | Silver+         |
| 7  | MESSAGE               | **Free: blocked entirely (hardcoded `planId == 1` check, not a missing row) / Starter (5 conversations, limited) / Classic+ (unlimited)** — NOT "Silver+" as previously documented; see `ChatService.sendChatMessage` |
| 8  | VIEW_PERSONAL_INFO    | Classic (40 reveals via `revealContact`, quota) / Silver+ (unconditional) |
| 9  | SHORTLIST             | Starter+        |
| 10 | WHO_VIEWED            | Starter (5, limited) / Classic (20, limited) / Silver+ (unlimited) |
| 11 | VERIFY_BADGE          | Silver+ (DB row exists; **no backend or frontend code references this code — currently decorative**) |
| 12 | HIGH_VISIBILITY       | Silver+ (**not enforced anywhere in code**) |
| 13 | NOTIFICATION_ALERT    | Starter+ (DB row starts at Starter, not Classic) |
| 14 | WHATSAPP_SHARE        | Gold+ (**frontend-only** `planTitle` check in `ProfileDetail.tsx`, no backend gate) |
| 15 | SPEAK_FAMILY          | Platinum (via `ServiceRequestService` generic plan-feature check — real gate) |
| 16 | HOROSCOPE_VIEW        | Classic+ (also requires the specific interest between viewer↔profile to be `APPROVED` — plan alone is not sufficient, see `getProfileDetailWithIntractionStatus`) |
| 17 | STAR_MATCH            | Classic (BASIC) / Silver+ (FULL) — DB row exists; **no backend code references `STAR_MATCH` by code, likely frontend-only via `entitlements.starMatch`** |
| 18 | SECURE_CONNECT        | Silver+         |
| 19 | VOICE_CALL            | Silver+ (via `ServiceRequestService` — real gate. Frontend CTA in `ProfileDetail.tsx` over-shows this for Starter/Classic too — see app CLAUDE.md landmines) |
| 20 | VIDEO_PROFILE         | Gold+ (via `ServiceRequestService`) |
| 21 | FAMILY_LOGIN          | Gold+ (via `ServiceRequestService`) |
| 22 | WHO_SHORTLISTED_YOU   | Gold+         |
| 23 | PRIORITY_SEARCH       | Gold+ (**not enforced anywhere in code** — no search-ranking boost implemented) |
| 24 | PRIME_VERIFIED        | Silver+ (**not enforced anywhere in code**) |
| 25 | DEDICATED_RM          | Platinum (via `ServiceRequestService`) |
| 26 | FAMILY_ASSISTED_MATCH | Platinum (via `ServiceRequestService`) |
| 27 | PROFILE_VIEW_LIMIT    | Free (0) / Starter (0) / Classic (no row) / Silver+ (no row, explicit) — all six plans currently allow unlimited profile viewing. The gate only enforces when `limit > 0`, so `0` reads as "no limit." **This is treated as intended, not a bug** — matrimony platforms generally let free users browse profiles freely and paywall the valuable actions (contact, chat, horoscope) instead; blocking browsing at Free would hurt conversion, not help it. The stale in-code comment describing a blocking design ("Free=0, Starter=20, Classic=100") does not reflect actual product intent and should be removed rather than the code "fixed." |
| 28 | DAILY_MATCH_ALERT     | Classic+        |
| 30 | INCOME_VERIFIED_BADGE | Gold+ (**not enforced in code** — badges render off the profile's own verification status fields, not the viewer's plan) |
| 31 | EDUCATION_VERIFIED_BADGE | Silver+ (**not enforced in code**, same as above) |
| 32 | ID_VERIFIED_BADGE     | Silver+ (**not enforced in code**, same as above) |
| 33 | PROFILE_BOOST         | Classic (0) / Silver (0) / Gold (2/month) / Platinum (5/month) — **`BoostService` uses a separate hardcoded `Map<Long,Integer>` (`PLAN_BOOST_CREDITS`), not this table.** Values currently match by coincidence; changing this DB row alone will NOT change actual boost credits granted. |

### 7.3 `planFeatures` table (camelCase!)
Columns: `id`, `createdAt`, `createdBy`, `isActive`, `updatedAt`, `updatedBy`, **`featureId`**, **`limit_value`** (varchar), **`subscriptionPlanId`**, **`limit_period`** ('DAY','MONTH','PLAN_DURATION').

Per-tier feature assignments (the canonical 6-tier × 23-feature matrix is materialized as rows here — see DB seed in section 12).

### 7.4 Plan × Feature matrix (source of truth)
**Corrected 2026-07-09** — Send Requests, Direct Messages, Who Viewed You, Notifications, and Profile Views were all wrong in the previous version (see 7.2 for the row-by-row DB verification). Cross-check cell values against 7.2 + the live `planFeatures` table, not this matrix alone, since this is a hand-maintained flattening of that data and will drift again if someone edits one without the other.

| Feature | Free ₹0 | Starter ₹499 | Classic ₹999 | Silver ₹2.5k | Gold ₹5k | Platinum ₹10k |
|---|---|---|---|---|---|---|
| Validity | Lifetime | 30d | 3mo | 3mo | 6mo | Till Marriage |
| Profile Views | Unlimited¹ | Unlimited¹ | Unlimited² | ∞ | ∞ | ∞ |
| Basic Search | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| Advanced Filters | ✖ | ✖ | ✔ | ✔ | ✔ | ✔ |
| Horoscope/Jathagam³ | ✖ | ✖ | ✔ | ✔ | ✔ | ✔ |
| Star Match Score | ✖ | ✖ | Basic | Full | Full | Full |
| Send Requests | 3 | 25 | 60⁴ | ∞ | ∞ | ∞ |
| Direct Messages | ✖ | 5 conversations | ∞ | ∞ | ∞ | ∞ |
| Who Viewed You | ✖ | 5 | 20 | ∞ | ∞ | ∞ |
| In-App Voice Call | ✖ | ✖ | ✖ | ✔ (req) | ✔ (req) | ✔ (req) |
| SecureConnect (mask phone) | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ |
| Shortlist | ✖ | ✔ | ✔ | ✔ | ✔ | ✔ |
| Daily Match Alert | ✖ | ✖ | ✔ | ✔ | ✔ | ✔ |
| Notifications | ✖ | ✔ | ✔ | ✔ | ✔ | ✔ |
| Verification Badge⁵ | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ |
| Prime Verified⁵ | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ |
| High Visibility⁵ | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ |
| Profile Boost/month⁶ | 0 | 0 | 0 | 0 | 2 | 5 |
| Video Profile | ✖ | ✖ | ✖ | ✖ | ✔ (req) | ✔ (req) |
| Who Shortlisted You | ✖ | ✖ | ✖ | ✖ | ✔ | ✔ |
| Priority Search⁵ | ✖ | ✖ | ✖ | ✖ | ✔ | ✔ |
| Family / Parent Login | ✖ | ✖ | ✖ | ✖ | ✔ | ✔ |
| WhatsApp Profile Share⁷ | ✖ | ✖ | ✖ | ✖ | ✔ | ✔ |
| Speak With Families | ✖ | ✖ | ✖ | ✖ | ✖ | ✔ (req) |
| Dedicated RM | ✖ | ✖ | ✖ | ✖ | ✖ | ✔ (req) |
| Family Assisted Matchmaking | ✖ | ✖ | ✖ | ✖ | ✖ | ✔ (req) |

"req" = service-request flow (admin fulfilled). See section 11.

¹ Unlimited by the `if (limit > 0)` guard reading a DB value of `0` as "no limit." Not treated as a bug — matches how free-tier browsing typically works elsewhere in this vertical (see 7.2 #27 and the 2026-07-09 changelog entry for the full reasoning).
² Classic has no `PROFILE_VIEW_LIMIT` row at all — unlimited by absence, same practical effect as ¹.
³ Also requires the specific interest between viewer↔profile to be `APPROVED`, independent of plan.
⁴ Fixed 2026-07-10 in local `vvm_db` (was 15, lower than Starter's 25 — confirmed a real downgrade trap, not intentional). **Live `uravugal` RDS has not been updated yet** — see the 2026-07-10 changelog entry for the exact `UPDATE` statement to promote.
⁵ DB row exists; no backend or frontend code currently reads this feature code. Cosmetic until wired up.
⁶ `BoostService` uses a hardcoded map, not this table — see 7.2 #33.
⁷ Frontend-only gate (`planTitle` string check), no backend enforcement.

## 8. File-by-file inventory

### 8.1 `models/` (entities)
| File | Table | Key fields | Notes |
|---|---|---|---|
| `GenericEntity.java` | (mapped superclass) | createdAt, createdBy, isActive ('Y'/'N'), updatedAt, updatedBy | base for almost everything |
| `UserEntity.java` | `users` | userId, mobile, email, pin (base64!), refreshToken, refreshTokenExpiry, resetToken, resetTokenExpiry, otp, otpCreatedAt, firstName, lastName, gender, dob, profileImage, casteId, location, isUser, isOnline, lastSeen, profileCreated, userStatus, rejectionReason | core profile |
| `UserDetailEntity.java` | `user_detail` | userId, height, weight, languages, degree, occupation, employedAt, annualIncome, presentAddress, permanentAddress ⭐, jobPlace, educationInDetail, horoscope, basicInfo (json), astronomicInfo (json), familyInfo (json), educationInfo (json) | extended profile. **`permanentAddress`** was a dormant, never-written column until 2026-07-14 — now repurposed to store "Native Place" (see that changelog entry) instead of adding a new column. |
| `UserLead.java` | `user_leads` | email, otp, otpCreatedAt, emailVerified, verificationToken, verificationTokenExpiry | pre-signup OTP holder |
| `SubscriptionPlan.java` | `subscription_plans` | id, title, period, price, originalPrice, discount, savings, durationDays, isPopular, **tagline**, **planDescription** | added 2 fields this session |
| `UserSubscriptions.java` | `user_subscriptions` | id, userId, subscriptionPlanId, status (SubscriptionStatus), startDate, endDate | one-to-many user→subscriptions |
| `Features.java` | `features` | id, code (UNIQUE), name, description | 27 rows |
| `PlanFeatures.java` | `planFeatures` | id, featureId, subscriptionPlanId, limit_value, limit_period | junction with limit values |
| `UserFeatureUsage.java` | `user_feature_usage` | id, userId, subscriptionId, featureId, used_count, last_reset_date | counts SEND_REQUEST + PROFILE_VIEW_LIMIT consumption |
| `ShortlistedProfile.java` | `shortlisted_profiles` | id, shortlistedBy, shortlistedUserId, note | |
| `InterestRequest.java` | `interest_requests` | senderId, receiverId, acceptStatus, message | |
| `RestrictedFieldRequest.java` | `restricted_field_requests` | requestedBy, requestedTo, fieldType, status | mobile/image/horoscope unlock requests |
| `ChatEntity.java` | `chat_messages` | conversationId, senderId, receiverId, message, sentAt, deliveredAt, seenAt | |
| `Conversation.java` | `conversations` | id, user1Id, user2Id, lastMessage, lastMessageAt | |
| `Notification.java` | `notifications` | senderId, receiverId, title, message, notificationCategory, read | |
| `UserDeviceInformation.java` | `user_devices` | userId, deviceId, fcmToken, platform | for push notifications |
| `ViewedProfile.java` | `viewed_profiles` | viewerId, viewedUserId, viewedAt | feeds WHO_VIEWED |
| `HiddenFieldEntity.java` | `hidden_fields` | userId, fieldName | profile field privacy |
| `BlockedUser.java` | `blocked_users` | blockerId, blockedUserId | mutual hide |
| `HappyStoryEntity.java` | `happy_stories` | title, content, image, isPublished | testimonials |
| `PaymentRequestEntity.java` | `payment_requests` | userId, planId, amount, status (PaymentRequestStatus), screenshot, txnId, adminNote | manual payment approval flow |
| `SavedSearchesEntity.java` + `SavedSearchFilterEntity.java` | `saved_searches` + `saved_search_filters` | search name + filter rows | |
| `KeyValue.java` | `key_value` | k, v | system config / feature flags |
| `UserConnectionEntity.java` | `user_connections` | followerId, followingId, status | follow relationships |
| `UserLikes.java` | `user_likes` | liker, likedUser | |
| `UserReport.java` | `user_reports` | reporterId, reportedUserId, reason | |
| `CasteEntity.java` | `caste` | id, name, communityId | master |
| `GalleryEntity.java` | `gallery` | userId, imageUrl, isActive | profile photos |
| `PremiumFeature.java` | `premium_features` | (legacy table — superseded by Features/PlanFeatures) | DO NOT use for gating |
| `AuditLog.java` | `audit_logs` | actor, action, entityType, entityId, before, after | |
| `CmsPage.java` | `cms_pages` | slug, title, content, isPublished | About/Privacy/Terms |
| `SupportTicket.java` + `TicketMessage.java` | `support_tickets`, `ticket_messages` | userId, subject, status, priority + threaded messages | |
| `ServiceRequest.java` ⭐ NEW | `service_requests` | id, userId, requestType (VOICE_CALL/VIDEO_PROFILE/FAMILY_LOGIN/SPEAK_FAMILY/DEDICATED_RM/FAMILY_ASSISTED_MATCH), status (PENDING/IN_PROGRESS/DONE/REJECTED), note, assignedAdminId | premium concierge requests |
| `FamilyLogin.java` ⭐ NEW | `family_logins` | id, primaryUserId, parentName, relationship, mobile (UNIQUE), email, pin (base64), status (ACTIVE/REVOKED), lastLoginAt | parent secondary login |
| `PaymentOrder.java` ⭐ NEW 2026-09-15 | `payment_orders` | orderId (UNIQUE, `VVM-<userId>-<base36 ms>-<8 rand>`), cfOrderId, paymentSessionId, userId, planId (null for add-ons), purpose (SUBSCRIPTION/BOOST_PURCHASE), amount (from plan, never client), orderStatus (ACTIVE/PAID/EXPIRED/TERMINATED), cfPaymentId, paymentStatus, paymentGroup, bankReference, paymentTime, failureReason, expiresAt, **fulfilledAt** (idempotency guard), fulfilledBy (WEBHOOK/VERIFY/ADMIN), subscriptionId, serviceCharge/serviceTax/settlementAmount | one Cashfree order; see §10 + 2026-09-15 changelog |
| `PaymentWebhookEvent.java` ⭐ NEW 2026-09-15 | `payment_webhook_events` | idempotencyKey (UNIQUE = `x-idempotency-key`), eventType, orderId, cfPaymentId, webhookVersion, attempt, signatureValid Y/N, processed Y/N, processingError, rawPayload (LONGTEXT) | every delivery incl. rejected ones (forensics) |
| `ResultResponse.java` | (not @Entity, legacy DTO in models/) | | should live in dtos/ |

### 8.2 `repositories/`
All extend `JpaRepository<Entity, Long>`. Custom methods worth noting:
- **`UserRepository`**: `findByMobile`, `findByEmail`, `findByRefreshToken`, `findByResetToken`, `advanceFilter(...)`, `normalFilter(...)` — both LEFT JOIN `subscription_plans` and project `subscriptionPlanId` + `subscriptionTitle` into `FilteredUserPlanView`.
- **`UserSubscriptionsRepository`**: `findByUserIdAndStatus(userId, SubscriptionStatus)`, `findTopByUserIdOrderByCreatedAtDesc(userId)` ← used widely as the "current subscription" lookup.
- **`FeaturesRepository`**: `findByCode(String code)`.
- **`PlanFeaturesRepository`**: `findByFeatureIdAndSubscriptionPlanId(Long featureId, Long planId)`.
- **`UserFeatureUsageRepository`**: `findByUserIdAndSubscriptionIdAndFeatureId(...)`.
- **`ShortlistedProfileRepository`**: `findByShortlistedByAndIsActive`, `findByShortlistedUserIdAndIsActive`, `findByShortlistedByAndShortlistedUserIdAndIsActive`, `existsByShortlistedByAndShortlistedUserIdAndIsActive`.
- **`InterestRequestRepository`**: `findBetweenUsers(viewerId, profileId)`.
- **`ServiceRequestRepository`** ⭐ NEW: `findByUserIdOrderByIdDesc`, `findByStatusOrderByIdDesc`, `findByRequestTypeOrderByIdDesc`, `findAllByOrderByIdDesc`.
- **`FamilyLoginRepository`** ⭐ NEW: `findByMobileAndStatus(mobile, status)`, `findByPrimaryUserIdAndStatusOrderByIdDesc`, `findAllByOrderByIdDesc`.

### 8.3 `services/`
| File | Public methods (highlights) |
|---|---|
| `AuthService.java` | `sendOtp(email, purpose, hint)`, `verifyOtp(email, otp, purpose)`, `refreshAccessToken(refreshToken)`, `logout(refreshToken)`, `resetPassword(resetToken, newPassword)` — uses BCrypt for resets |
| `UserService.java` ⭐ | `login(UserEntity)` — falls through to `FamilyLoginService.tryLogin` on miss/PIN mismatch · `createStarterProfile` · `getProfileDetailByUserId(Long)` (overload) · `getProfileDetailByUserId(viewedUserId, requesterId)` — VIEW_PERSONAL_INFO + SECURE_CONNECT mask · `getProfileDetailWithIntractionStatus(viewerId, profileUserId)` — PROFILE_VIEW_LIMIT increment + horoscope mask + secure connect mask · `filterUsers(UserFilterRequest)` — ADV_SEARCH gate (uses `advanceFilter` repo or `normalFilter`) · `getUserPaidStatus` · `updateLastSeen` |
| `ChatService.java` ⭐ | `sendChatMessage(request)` — MESSAGE feature gate: Free hardcoded-blocked (`planId == 1`), Starter limited to 5 conversations (numeric `planFeatures` value), Classic+ unlimited. **Corrected 2026-07-09** — previously documented as "Free/Starter/Classic blocked," which was wrong for both Starter and Classic; plus all chat CRUD |
| `ConversationService.java` | conversation list, mark seen, etc. |
| `ViewedProfileService.java` ⭐ | `getAllViewers(userId)` — Gold+ gate via WHO_VIEWED feature, plus `subList(0, viewerLimit)` cap |
| `ShortlistedProfileService.java` ⭐ | `insertShortlistedProfile(ShortlistedProfile)` — SHORTLIST gate (Free blocked) · `getShortlistedProfiles(encodedId, page, size)` · `getWhoShortlistedMe(encodedId, page, size)` ⭐ NEW — WHO_SHORTLISTED_YOU gate (Gold+) · `deleteShortlistedProfile`, `deleteShortlistedProfileByUsers`, `checkIfShortlisted` |
| `InterestRequestService.java` | send/accept/reject/list interest requests; uses `validateAndIncrementUsage` for SEND_REQUEST limit |
| `RestrictedFieldRequestService.java` | mobile/image/horoscope unlock request flow |
| `PushNotificationService.java` ⭐ | `sendPushNotificationToUser(userId, title, body)` — NOTIFICATION_ALERT gate (Free + Starter silenced); `updateFcmTokenForUser(map)` |
| `NotificationService.java` | DB-side notification CRUD |
| `EmailService.java` | Zeptomail wrapper; OTP, approval, rejection emails |
| `S3FileUploadService.java` + `S3BucketMapping.java` + `S3Config.java` | profile/gallery/document upload |
| `GalleryService.java` | per-user gallery CRUD |
| `HiddenFieldService.java` | per-user field privacy |
| `BlockedUserService.java` | block/unblock |
| `HappyStoryService.java` | testimonials CRUD (admin) |
| `SaveSearchService.java` | named saved searches with filters |
| `KeyValueService.java` | key/value config store |
| `UserConnectionService.java` | follow / unfollow / followers / following |
| `UserDetailService.java` | user_detail CRUD · `updatePersonalInfo` ⭐ now also persists `currentAddress`→`presentAddress` and `nativePlace`→`permanentAddress` (2026-07-14) · `updateEducationInfo` ⭐ now also persists `jobPlace`/`educationInDetail`, and its `EmploymentType.valueOf(employedAt)` conversion is null-guarded instead of throwing (2026-07-14) · `calculateProfileCompletion` ⭐ tracks 9 more fields (jobPlace, educationInDetail, annualIncome, currentAddress, nativePlace, fatherName/Occupation, motherName/Occupation) — see 2026-07-14 changelog |
| `UserLikesService.java` | likes |
| `UserReportService.java` | abuse reports |
| `UserFeatureUsageService.java` | `validateAndIncrementUsage(userId, subscriptionId, featureId)` — throws on FREE_PLAN_RESTRICTED / FEATURE_NOT_AVAILABLE / INTEREST_LIMIT_EXCEEDED. Used for SEND_REQUEST. |
| `SubscriptionPlanService.java` | list active plans; the `getAllActivePlans` returns the new tagline + planDescription too |
| `UserSubscriptionsService.java` | activate/expire/list user subscriptions |
| `SubscriptionSchedulerService.java` | nightly job: expire subscriptions whose endDate < now |
| `PaymentService.java` + `PaymentRequestService.java` | Razorpay + manual upload approval |
| `PoruthamService.java` | Star Match: name resolution + consent gate only; the calculation is the pure `utils/PoruthamEngine` (Tamil 10-porutham, 2026-09-27) over `utils/StarInfo` tables |
| `CasteService.java` | master caste list |
| `PremiumFeatureService.java` | LEGACY — premium_features table; do NOT extend, use Features/PlanFeatures |
| `AdminService.java` | admin-only user list, approve/reject profile, dashboard counts |
| `ServiceRequestService.java` ⭐ NEW | `createRequest(encodedUserId, requestType, note)` — plan-feature gate; `getMyRequests(encodedUserId, page, size)`; `adminListRequests(status, requestType, page, size)`; `adminUpdateStatus(id, newStatus, adminId)` |
| `FamilyLoginService.java` ⭐ NEW | `createFamilyLogin(encodedPrimaryUserId, FamilyLogin)` — Gold+ gate via FAMILY_LOGIN feature; `listMine(encodedPrimaryUserId)`; `revoke(encodedPrimaryUserId, familyLoginId)` (ownership check); `tryLogin(mobile, plainPin)` — returns child's payload + `role: "PARENT"` + `familyLoginId` + parent meta; `adminList()`; `adminRevoke(id)` |
| `CashfreeService.java` ⭐ NEW 2026-09-15 | `createOrder(userId, planId, purpose)` — Cashfree Create Order, amount from `subscription_plans.price`, reuses an open unexpired order · `getOrderStatus(orderId, requesterIdOrNull)` — Get Order fast-path, fulfils if PAID · `handleWebhook(rawBytes, sig, ts, idemKey, version, attempt)` → HTTP code (200/401/500/503) · private `fulfil()` = `markPaid` claim + `activate()` · `adminListOrders`, `adminReverify`, `adminListEvents` |
| `SubscriptionActivationService.java` ⭐ NEW 2026-09-15 | `activatePlan(user, plan, paymentReference, notificationCategory)` — THE one place an ACTIVE `user_subscriptions` row is created + push (Direct) + in-app + email; `addBoostCredit(userId)`. Used by `AdminService.approvePayment`, `AdminService.assignPlanToUser` and `CashfreeService` (the two admin copies were de-duplicated onto it) |
| ~~`PaymentService.java`~~ | REMOVED 2026-09-15 — Razorpay stub that trusted the amount from the URL; nothing called it |

### 8.4 `controllers/`
| Controller | Base path | Notable endpoints |
|---|---|---|
| `UserController` | `/user` | `POST /login`, `POST /sign-up`, `GET /getUserByUserId/{id}`, `GET /getProfileDetailByUserId/{id}?requesterId=...`, `POST /filterUsers`, `GET /getUserConnectionCounts/{id}`, `POST /lastSeen/{id}`, `GET /getDailyShuffledUsersByCaste/{casteId}/{gender}`, `GET /getTop30NewUsers/{casteId}/{gender}`, `GET /getTenShuffledUsers/{gender}/{casteId}`, profile CRUD |
| `AuthController` | `/auth` | `POST /sendOtp`, `POST /verifyOtp`, `POST /refresh`, `POST /logout`, `POST /resetPassword` |
| `MailboxController` ⭐ | `/mailbox` | `GET /received/{encodedId}`, `GET /sent/{encodedId}`, `GET /restricted-field-requests/{encodedId}`, `GET /shortlisted/{encodedId}`, `POST /insertShortlistedProfile`, `GET /pending-received/{encodedId}`, `GET /accepted-received/{encodedId}`, `GET /rejected/{encodedId}`, `GET /updateInterestRequestStatus/{id}/{status}`, `DELETE /deleteInterestRequest/{id}`, `DELETE /deleteShortlistedProfile/{id}`, `DELETE /deleteShortlistedProfileByUsers/{encodedId}/{userId}`, `GET /checkShortlisted/{encodedId}/{userId}`, **`GET /whoShortlistedMe/{encodedId}` ⭐ NEW** |
| `ChatController` | `/chat` | sendChatMessage, fetch messages, mark seen |
| `ConversationController` | `/conversation` | `GET /chatlist/{userId}` |
| `InterestRequestController` | `/interestRequest` | sendInterest, accept, reject |
| `RestrictedFieldRequestController` | `/restrictedFieldRequest` | request mobile/image/horoscope |
| `SubscriptionPlanController` | `/subscriptionPlan` | `GET /getAllActivePlans`, CRUD |
| `UserSubscriptionsController` | `/userSubscriptions` | activate, list, expire |
| `PaymentController` | `/payment` | Razorpay checkout |
| `PaymentRequestController` | `/paymentRequest` | manual upload + admin approve |
| `NotificationController` | `/notification` | list / mark read |
| `PushNotificationController` | `/pushNotification` | `POST /updateFcmToken` |
| `GalleryController` | `/gallery` | `GET /getAllImagesByUserId/{id}`, `PUT /changeImageActiveStatus/{id}`, upload |
| `HiddenFieldController` | `/hiddenField` | get/set |
| `BlockedUserController` | `/blockedUser` | block/unblock |
| `HappyStoryController` | `/happyStory` | list/CRUD |
| `KeyValueController` | `/keyValue` | get/set config |
| `SaveSearchController` | `/saveSearch` | save/list/delete |
| `UserConnectionController` | `/userConnection` | follow, unfollow, followers, following |
| `UserDetailController` | `/userDetail` | extended profile CRUD |
| `UserFeatureUsageController` | `/userFeatureUsage` | get usage counts |
| `UserLikesController` | `/userLikes` | like |
| `UserReportController` | `/userReport` | report user |
| `ViewedProfileController` ⭐ | `/viewedProfile` | log view, **`GET /getAllViewers/{userId}`** (Silver+ gated, capped) |
| `MatchingController` | `/matching` | porutham endpoints |
| `CasteController` | `/caste` | master list |
| `PremiumFeatureController` | `/premiumFeature` | LEGACY |
| `PresenceController` | `/presence` | online status |
| `AdminController` | `/admin` | dashboard counts, user list, approve/reject |
| `ServiceRequestController` ⭐ NEW | `/service-request` | `POST /create/{encodedUserId}` body `{requestType, note}`, `GET /my/{encodedUserId}?page&size`, `GET /admin/list?status&requestType&page&size`, `PATCH /admin/{id}/status` body `{status, adminId}` |
| `FamilyLoginController` ⭐ NEW | `/family-login` | `POST /create/{encodedUserId}` body `FamilyLogin`, `GET /mine/{encodedUserId}`, `DELETE /{encodedUserId}/{familyLoginId}`, `GET /admin/list`, `DELETE /admin/{id}` |
| `CallbackRequestController` ⭐ NEW | `/callback-request` | `POST /create/{encodedUserId}` body `{name, mobile, email?, planInterested, note?, bestTimeToCall?}` (encodedUserId can be `'guest'`); `GET /my/{encodedUserId}?page&size`; `GET /admin/list?status&page&size`; `POST /admin/{id}/status` body `{status, adminId}`. Supports Play Store-safe payment fallback flow. |
| `CashfreeController` ⭐ NEW 2026-09-15 | `/payments/cashfree` | `POST /createOrder/{encodedUserId}` body `{planId, purpose?}` → `{orderId, paymentSessionId, amount, env, expiresAt}`; `GET /order/{orderId}/status?requesterId=<encoded, optional>` (anonymous allowed — website return page runs in a Custom Tab with no login; payload carries no personal data, `paymentSessionId` only while ACTIVE); **`POST /webhook`** (PUBLIC, raw `byte[]` body, HMAC via `x-webhook-signature`/`x-webhook-timestamp`); `GET /admin/orders?status&userId&page&size`; `POST /admin/orders/{orderId}/reverify`; `GET /admin/orders/{orderId}/events`. ~~`PaymentController` `/payments/createOrder/{amount}`~~ removed. |

### 8.5 `dtos/`
- **`ResultResponse`** — standard `{code, status, message, data}`
- **`PaginatedResultResponse`** extends ResultResponse with `paginationData`
- **`PaginationData`** — `{totalPages, totalElements, currentPage, pageSize}`
- **`FilteredUserPlanView`** — search result row that includes `subscriptionPlanId` + `subscriptionTitle` (drives plan badges in app + admin)
- **`MailboxUserDetail`** — flattened user fields used in mailbox listings
- **`UserFilterRequest`** — search filter inputs
- Plus: AuthOtpRequest, AuthVerifyOtpRequest, ChangePinRequest, ChatListResponse, EducationInfoRequest, FamilyInfoRequest, FilteredUserResponse, ForgotPasswordRequest, MatchRequestDto, MatchResponseDto, NotificationResponse, OtpVerificationRequest, PaymentRequestResponseDTO, PersonalInfoRequest, PoruthamResultDto, ProfileCompletionResponse, RefreshTokenRequest, ReportUserRequest, ResetPasswordRequest, SendPushNotificationRequestDTO, UserConnectionResponse, UserDetailDTO, UserDetailUpdateRequest, UserLikesRequest, UserProfileRequest, ViewedProfileDetailDTO, BlockUserRequest, AdminApprovePaymentRequestDTO, AdminUserDetailDTO, AdminUserListDTO, AstrologyInfoRequest

### 8.6 `enums/`
- `ActiveStatus` — `Y`, `N`
- `ApprovalStatus` — `PENDING`, `APPROVED`, `REJECTED`
- `ChatStatus` — `SENT`, `DELIVERED`, `SEEN`
- `ConnectionStatus` — `PENDING`, `ACCEPTED`, `BLOCKED`
- `EmploymentType`
- `FeatureType`
- `Gender` — `M`, `F`
- `IsUser` — `PU` (paid), `FA` (free), etc.
- `PaymentRequestStatus` — `PENDING`, `APPROVED`, `REJECTED`
- `Planet`
- `ResponseStatus` — `SUCCESS`, `FAILURE`
- `S3BucketMap`
- `SubscriptionStatus` — `ACTIVE`, `PENDING`, `EXPIRED`
- `UserKnowStatus`
- `UserStatus` — `PENDING`, `APPROVED`, `REJECTED`
- `caste`

### 8.7 `utils/`
- `JwtUtil` — `generateToken(userId, mobile, role)`, `generateRefreshToken(userId)`, `extractAllClaims`, `extractUserId`, `extractRole`, `isTokenValid`. HS256 with secret from `jwt.secret` property.
- `EncryptionUtils` — generic crypt helpers
- `JsonMap` — JSON↔Map utility
- `PoruthamEngine` (pure, static, testable via `verify-porutham/`) + `StarInfo` (tables + every star/rasi spelling → id) — porutham helpers. `RasiLordMapper` was deleted 2026-09-27 (lords live in the engine).

### 8.8 `config/`
- **`SecurityConfig`** — Spring Security filter chain, CORS, public endpoint allowlist
- **`JwtAuthenticationFilter`** — extracts Bearer token, sets `SecurityContext` userId
- **`WebSocketConfig`** — STOMP `/ws` endpoint, message broker `/topic`/`/queue`
- **`UserWebSocketHandler`** — raw WS handler for chat & presence
- **`PresenceTracker`** — in-memory map of online userIds
- **`RateLimiter`** — per-IP token bucket
- **`AwsConfig`** — S3 client bean from `aws.*` properties
- **`GlobalExceptionHandler`** — `@ControllerAdvice`, maps exceptions to `ResultResponse`

## 9. API surface (canonical endpoint list)
See section 8.4 — that table is the complete endpoint inventory. Cross-reference with `VaibhavVivaahaApp/CLAUDE.md` section 9 (frontend `userApi.js`) and `adminpanel/CLAUDE.md` section 8 (admin panel consumers).

## 10. Plan gating implementation map
| Feature | Where | Failure mode |
|---|---|---|
| **PROFILE_VIEW_LIMIT** | `UserService.getProfileDetailWithIntractionStatus` — attempts to increment `UserFeatureUsage` for plans 1/2/3, unlimited for plans 4/5/6, but the `if (limit > 0)` guard means a `0` value (Free/Starter's actual DB rows) never triggers the block — all plans are effectively unlimited today, treated as intended (see 7.2 #27) | `403 PROFILE_VIEW_LIMIT_EXCEEDED` only if a plan's row is ever seeded to a real positive number. `PROFILE_VIEW_BLURRED` referenced in the mobile app's error handling is never actually sent by this method — dead message, don't rely on it |
| **VIEW_PERSONAL_INFO** | `UserService.getProfileDetailWithIntractionStatus` AND `getProfileDetailByUserId(viewedUserId, requesterId)` | Silently null `mobile` + `email` |
| **SECURE_CONNECT** | Same two methods | Replace `mobile` with `•••• •••• 1234` (last-4 mask) for Silver+ |
| **HOROSCOPE_VIEW** | `UserService.getProfileDetailWithIntractionStatus` | Null `userDetail.horoscope` for non-Classic+ viewers |
| **ADV_SEARCH** | `UserService.filterUsers` | Falls back to `userRepository.normalFilter` instead of `advanceFilter` (silent degrade) |
| **MESSAGE** | `ChatService.sendChatMessage` | `403 PLAN_UPGRADE_REQUIRED` |
| **SHORTLIST** | `ShortlistedProfileService.insertShortlistedProfile` | `403 PLAN_UPGRADE_REQUIRED` |
| **WHO_VIEWED** | `ViewedProfileService.getAllViewers` | `403 PLAN_UPGRADE_REQUIRED` for Free; results capped to `limit_value` for limited tiers |
| **WHO_SHORTLISTED_YOU** | `ShortlistedProfileService.getWhoShortlistedMe` ⭐ NEW | `403 PLAN_UPGRADE_REQUIRED` |
| **NOTIFICATION_ALERT** | `PushNotificationService.sendPushNotificationToUser` | Silent skip — returns success but does not send (Free + Starter) |
| **SEND_REQUEST** | `UserFeatureUsageService.validateAndIncrementUsage` (called by `InterestRequestService`) | Throws `INTEREST_LIMIT_EXCEEDED` |
| **FAMILY_LOGIN** | `FamilyLoginService.createFamilyLogin` ⭐ NEW | `403 PLAN_UPGRADE_REQUIRED` |
| **VOICE_CALL / VIDEO_PROFILE / SPEAK_FAMILY / DEDICATED_RM / FAMILY_ASSISTED_MATCH** | `ServiceRequestService.createRequest` ⭐ NEW | `403 PLAN_UPGRADE_REQUIRED` |

**Payment fulfilment (not a gate)**: `CashfreeService.fulfil()` → `PaymentOrderRepository.markPaid()` (`UPDATE … WHERE orderId=? AND fulfilledAt IS NULL`, exactly one winner) → `SubscriptionActivationService.activatePlan()`. Triggered by webhook `PAYMENT_SUCCESS_WEBHOOK`, by `getOrderStatus` when Get Order says PAID, or by admin reverify. Never activate a plan for a gateway order any other way.

**Canonical gate snippet** (copy this when adding a new gate):
```java
UserSubscriptions sub = userSubscriptionsRepository.findTopByUserIdOrderByCreatedAtDesc(userId);
if (sub == null || sub.getSubscriptionPlanId() == 1L) {
    response.setCode(403);
    response.setMessage("PLAN_UPGRADE_REQUIRED");
    return response;
}
Features feature = featuresRepository.findByCode("FEATURE_CODE");
if (feature != null) {
    PlanFeatures pf = planFeaturesRepository.findByFeatureIdAndSubscriptionPlanId(
            feature.getId(), sub.getSubscriptionPlanId());
    if (pf == null) {
        response.setCode(403);
        response.setMessage("PLAN_UPGRADE_REQUIRED");
        return response;
    }
}
```

## 11. Auth & roles
### 11.1 Login flow
1. App POSTs `/user/login` with `{mobile, pin}` (PIN is plaintext in body).
2. `UserService.login` looks up `users` by mobile.
3. If found → decode stored `pin` (`Base64.decode(userentity.getPin())`) → compare equals → generate JWT access + refresh token via `JwtUtil`, persist `refreshToken` + `refreshTokenExpiry` on `UserEntity`, return payload (`userId` base64-encoded, basic profile fields, `isUser`, **`token`**, **`refreshToken`**).
4. If user **not found** → fall through to `FamilyLoginService.tryLogin(mobile, pin)`.
5. If primary user found but **PIN mismatch** → also fall through to `FamilyLoginService.tryLogin`.
6. `tryLogin`: looks up `family_logins` by `(mobile, status='ACTIVE')`, base64-decodes stored PIN, compares, loads the **child's** UserEntity, **issues JWT access + refresh tokens scoped to the child's `userId`** (persists refresh on child's `UserEntity`), returns the SAME payload shape as a user login but with extra fields: `role: "PARENT"`, `familyLoginId`, `parentName`, `relationship`, plus `token` + `refreshToken`. Updates `lastLoginAt`.
7. App receives identical payload shape, persists `userRole` from response.

### 11.2 Token / refresh flow
- JWT issued via `AuthService.refreshAccessToken(refreshToken)` — claims: `userId`, `mobile`, `role`, `type`. Subject = `String.valueOf(userId)`.
- Access token = 15 min, Refresh = 60 days.
- Refresh tokens are persisted on `UserEntity.refreshToken` so logout can revoke.
- `JwtAuthenticationFilter` reads `Authorization: Bearer <token>` on every request.

### 11.3 Roles
- **USER** — normal logged-in member (default)
- **PARENT** — logged in via `family_logins` table; same `userId` as the child; app hides edit/payment screens
- **ADMIN** — admin panel users; auth handled via separate `admin_token` localStorage on the admin client; backend role check is currently lax (TODO)

### 11.4 Inconsistencies (landmines)
- **PIN can be stored in TWO formats**: legacy **base64** (set via signup/changePin) OR **BCrypt** (set via `AuthService.resetPassword`). `UserService.login` now detects the format: if `pin.startsWith("$2")` → BCrypt match via `BCryptPasswordEncoder.matches`, else base64 decode + equals. Wrapped in try/catch so a malformed value can't crash login.
- ~~After password reset, login would throw `Illegal base64 character`~~ **FIXED 2026-04-09** — dual-format PIN check.
- ~~`/user/login` does NOT issue a JWT~~ **FIXED 2026-04-09** — now issues access + refresh tokens via `JwtUtil`, persisted on `UserEntity`. `FamilyLoginService.tryLogin` also issues tokens scoped to the child.

## 12. DB schema (vvm_db)
Hibernate `ddl-auto=update` keeps schema in sync with `@Entity`. For tables created this session, run the SQL below if `ddl-auto` is ever set to `validate`/`none`:

### `service_requests` ⭐ NEW
```sql
CREATE TABLE service_requests (
  id BIGINT NOT NULL AUTO_INCREMENT,
  createdAt DATETIME(6) DEFAULT NULL,
  createdBy VARCHAR(255) DEFAULT NULL,
  isActive ENUM('Y','N') DEFAULT 'Y',
  updatedAt DATETIME(6) DEFAULT NULL,
  updatedBy VARCHAR(255) DEFAULT NULL,
  userId BIGINT NOT NULL,
  request_type VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  note VARCHAR(1000) DEFAULT NULL,
  assigned_admin_id BIGINT DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_sr_user (userId),
  KEY idx_sr_status (status),
  KEY idx_sr_type (request_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

### `subcastes` ⭐ NEW (2026-08-09)
Each subcaste belongs to exactly one caste. Seeded 2026-08-09 with 23 client-supplied rows via `seed-subcastes.sql` (idempotent — run per environment); FREECASTEBAR has none by design. See section 17.
```sql
CREATE TABLE subcastes (
  id BIGINT NOT NULL AUTO_INCREMENT,
  createdAt DATETIME(6) DEFAULT NULL,
  createdBy VARCHAR(255) DEFAULT NULL,
  isActive ENUM('Y','N') DEFAULT 'Y',
  updatedAt DATETIME(6) DEFAULT NULL,
  updatedBy VARCHAR(255) DEFAULT NULL,
  casteId BIGINT NOT NULL,
  subcasteName VARCHAR(100) NOT NULL,   -- plain VARCHAR, deliberately NOT an enum (unlike castes.casteCode)
  PRIMARY KEY (id),
  KEY idx_subcaste_caste (casteId, isActive),
  CONSTRAINT fk_subcaste_caste FOREIGN KEY (casteId) REFERENCES castes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- and on users:
ALTER TABLE users ADD COLUMN subcasteId BIGINT NULL;
ALTER TABLE users ADD INDEX idx_users_subcaste (casteId, subcasteId);
```

### `family_logins` ⭐ NEW
```sql
CREATE TABLE family_logins (
  id BIGINT NOT NULL AUTO_INCREMENT,
  createdAt DATETIME(6) DEFAULT NULL,
  createdBy VARCHAR(255) DEFAULT NULL,
  isActive ENUM('Y','N') DEFAULT 'Y',
  updatedAt DATETIME(6) DEFAULT NULL,
  updatedBy VARCHAR(255) DEFAULT NULL,
  primaryUserId BIGINT NOT NULL,
  parentName VARCHAR(120) NOT NULL,
  relationship VARCHAR(50) DEFAULT NULL,
  mobile VARCHAR(20) NOT NULL,
  email VARCHAR(120) DEFAULT NULL,
  pin VARCHAR(255) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  last_login_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_family_mobile (mobile),
  KEY idx_family_primary_user (primaryUserId),
  KEY idx_family_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

### `payment_orders` + `payment_webhook_events` ⭐ NEW (2026-09-15)
Created by `ddl-auto=update`. Indexes/uniques are in `migration-2026-09-15-cashfree.sql` — run it per environment and check the verification SELECT it ends with (Hibernate has skipped indexes in this project before).
```sql
ALTER TABLE payment_orders ADD UNIQUE KEY uk_po_order (orderId), ADD KEY idx_po_user (userId, createdAt), ADD KEY idx_po_status (orderStatus);
ALTER TABLE payment_webhook_events ADD UNIQUE KEY uk_pwe_idem (idempotencyKey), ADD KEY idx_pwe_order (orderId);
```
Column inventory: see §8.1. Enable the app's online checkout with `UPDATE keyValue SET valueColumn='{"mode":"GATEWAY"}' WHERE keyColumn='PAYMENT_MODE';` (see the app CLAUDE.md for the Play-policy caveat before doing that).

### Additional columns added to `subscription_plans` this session
```sql
ALTER TABLE subscription_plans
  ADD COLUMN tagline VARCHAR(255) DEFAULT NULL,
  ADD COLUMN plan_description TEXT DEFAULT NULL;
```

### Reseed of plans + features + planFeatures
The full reseed SQL (used to reset the matrix from scratch) lives in this session's plan history. Re-run it only if rebuilding from empty.

## 13. State management
N/A — backend is stateless except for `PresenceTracker` (in-memory online users) and the JWT refresh token persisted on `UserEntity`.

## 14. localStorage / persistence keys
N/A — see app/admin CLAUDE.md.

## 15. Navigation
N/A.

## 16. Cross-repo contracts
| Backend endpoint | Mobile app caller | Admin panel caller |
|---|---|---|
| `POST /user/login` | `LoginScreen` via `userApi.login` | `(auth)/login` (admin uses separate auth path) |
| `GET /user/getProfileDetailByUserId/{id}?requesterId=...` | `ProfileDetail.tsx` via `userApi.getProfileDetailByUserId` | `users/[id]/page.tsx` |
| `POST /user/filterUsers` | `SearchTabs.tsx` via `userApi.filterUsers` | `users/page.tsx` |
| `GET /mailbox/whoShortlistedMe/{encodedId}` ⭐ | `mailBox.tsx` Gold+ tab via `userApi.getWhoShortlistedMe` | — |
| `POST /service-request/create/{encodedUserId}` ⭐ | `ProfileDetail.tsx` Voice Call CTA, `FamilyAccessScreen` (future), `MyRequestsScreen` | — |
| `GET /service-request/my/{encodedUserId}` ⭐ | `MyRequestsScreen` | — |
| `GET /service-request/admin/list` ⭐ | — | `(dashboard)/requests/service/page.tsx` |
| `PATCH /service-request/admin/{id}/status` ⭐ | — | `(dashboard)/requests/service/page.tsx` |
| `POST /family-login/create/{encodedUserId}` ⭐ | `FamilyAccessScreen.tsx` | — |
| `GET /family-login/mine/{encodedUserId}` ⭐ | `FamilyAccessScreen.tsx` | — |
| `DELETE /family-login/{encodedUserId}/{id}` ⭐ | `FamilyAccessScreen.tsx` | — |
| `GET /family-login/admin/list` ⭐ | — | `(dashboard)/users/family-logins/page.tsx` |
| `DELETE /family-login/admin/{id}` ⭐ | — | `(dashboard)/users/family-logins/page.tsx` |
| `GET /keyValue/getKeyValueByKey/PAYMENT_MODE` ⭐ | `PaymentScreen.tsx` | (admin sets via keyValue editor) |
| `GET /keyValue/getKeyValueByKey/ADMIN_CONTACT` ⭐ | `RelationshipManagerView.tsx` | (admin sets via keyValue editor) |
| `POST /callback-request/create/{encodedUserId}` ⭐ | `RelationshipManagerView.tsx` | — |
| `GET /callback-request/my/{encodedUserId}` ⭐ | (future "My Requests" tab) | — |
| `GET /callback-request/admin/list` ⭐ | — | (admin lead inbox — future) |
| `POST /callback-request/admin/{id}/status` ⭐ | — | (admin lead inbox — future) |
| `POST /payments/cashfree/createOrder/{encodedUserId}` ⭐ 2026-09-15 | `components/GatewayCheckoutView.tsx` via `userApi.createCashfreeOrder` (PaymentScreen + AddOnPaymentScreen in `GATEWAY` mode) | website `PackagesSection.jsx` (logged-in members) |
| `GET /payments/cashfree/order/{orderId}/status` ⭐ | `screens/PaymentReturn.tsx` via `userApi.getCashfreeOrderStatus` | website `/pay/:orderId` (reads `paymentSessionId`) + `/payment/return` (polls) |
| `POST /payments/cashfree/webhook` ⭐ | — (Cashfree → us) | — |
| `GET /payments/cashfree/admin/orders`, `POST …/reverify`, `GET …/events` ⭐ | — | admin panel Payments → Online (not built yet) |

## 17. Known landmines

- **🔴 `ddl-auto=update` does NOT add a value to an existing MySQL `ENUM` column — it adds COLUMNS and TABLES only.** `conversations.status` is `enum('PENDING','ACCEPTED','BLOCKED')`. Adding `DISCONNECTED` to the Java `ChatStatus` enum compiled, deployed and ran fine, then every write of the new value failed at runtime with **`Data truncated for column 'status' at row 1`** — and because `removeConnection` deletes the interest rows BEFORE updating the conversation, and is not transactional, the member was left unfriended with the chat still open. Fixed in production 2026-09-19 by `ALTER TABLE conversations MODIFY status enum('PENDING','ACCEPTED','BLOCKED','DISCONNECTED') NOT NULL;` (backup `_bak_conversations_status_20260919`). **Before adding a value to ANY `@Enumerated(EnumType.STRING)` enum, check the live column with `SHOW COLUMNS` — if it is an `ENUM`, the ALTER is a hand-applied migration like the indexes (§ DB indexes landmine), and staging/new environments need it too.** Enum columns in this schema: check with `SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND DATA_TYPE='enum';`
- **Never `repo.save()` a `PaymentOrder` you loaded BEFORE `markPaid` ran on it.** `markPaid` is a bulk JPQL UPDATE; a stale instance still has `fulfilledAt = null`, and `save()` merges every field back — re-opening the order for a second activation. Reload by `orderId` after any claim (see `CashfreeService.activate`). Same trap for any future bulk-update repository method.
- **The Cashfree webhook body must stay `byte[]`** in `CashfreeController.webhook`. The HMAC is over the exact bytes; a DTO/`Map` parameter would let Jackson re-serialise `4999.00` as `4999` and every signature would fail with no obvious error. And when the JWT filter is re-enabled, `/payments/cashfree/webhook` and `/payments/cashfree/order/*/status` must be on the permit-all list (Cashfree sends no Bearer; the website return page runs in a Custom Tab with no login).
- **Cloudflare sits in front of `/BE/`** — add a WAF/bot-fight skip rule for `/BE/payments/cashfree/webhook` before going live or deliveries get a JS challenge and silently fail (Cashfree retries 3× then gives up; the dashboard's Webhooks → Logs shows the non-200s).
- **DB indexes are hand-applied and NOT in version control.** `ddl-auto=update` does not create indexes and there is no Flyway/Liquibase — verified empirically by letting Hibernate build a schema from scratch: it created the `subcastes` table and `users.subcasteId` column but **0 of the 16 indexes and no foreign key**. Applied 2026-08-10 to local `vvm_db` AND to production `vaibhavVivaaha` (verified: 16/16 indexes with correct columns, FK present, 27 subcaste rows). **Any NEW environment still needs them** — run `migration-indexes-simple.sql` (plain ALTERs, GUI-client safe) or the full `migration-2026-08-09-production.sql`, and check with `verify-production.sql`.
- **`subcastes` holds ONLY client-supplied names** — seeded 2026-08-09 with 23 rows via `seed-subcastes.sql` (idempotent; run it per environment — production/staging still need it). FREECASTEBAR (caste 5) deliberately has none. Never invent or "helpfully" add subcaste names; additions must come from the client. Code must still tolerate a caste with zero rows (the app hides subcaste UI in that state).
- **A retired plan (`subscription_plans.isActive='N'`) still fully entitles its existing subscribers.** Entitlements come from `planFeatures` keyed by plan id; `isActive` only controls whether the plan can be *sold*. Never use `isActive` as an entitlement check — it would instantly strip access from paying members (as of 2026-08-09 that is 2 Starter + 10 Classic).
- **camelCase column names everywhere** — `createdAt`, `isActive`, `featureId`, `subscriptionPlanId`, `planFeatures` (the table itself!), `primaryUserId`, etc. NOT snake_case. Easy to break with naive migration scripts.
- **`isActive` is `enum('Y','N')`**, not boolean. Always use `ActiveStatus.Y` / `ActiveStatus.N`.
- **Hardcoded `subscriptionPlanId == 1`** as the Free check across multiple services. Search for `== 1L` before refactoring plan IDs.
- **PIN stored base64-encoded** in `UserEntity.pin` AND `FamilyLogin.pin`. NOT bcrypt for normal login.
- **Inconsistent password hashing**: `AuthService.resetPassword` uses BCrypt, but `UserService.login` compares base64. After a reset the user can't log in until this is reconciled.
- **Legacy `CONTACT_VIEW` feature code does NOT exist** in the `features` table. Canonical code is `VIEW_PERSONAL_INFO`. Old code that referenced `CONTACT_VIEW` was silently granting access — fixed this session.
- **Two `ResultResponse` classes** — one in `dtos/` (canonical), one in `models/` (legacy). Use `dtos/ResultResponse`.
- **`PremiumFeature` entity is legacy** — superseded by `Features` + `PlanFeatures`. Don't extend it.
- **`SubscriptionPlan.tagline` + `plan_description`** were added to the entity AND the DB this session. Old jars will throw if pointed at a DB without those columns and `ddl-auto != update`.
- **`UserSubscriptionsRepository.findTopByUserIdOrderByCreatedAtDesc`** is what most services use as "current subscription" — but it returns the most-recently-created sub, NOT the active one. Most code is OK because it then checks `subscriptionPlanId == 1`, but if a user has an EXPIRED Gold + an ACTIVE Free, this returns the Gold.
- **Plain text DB credentials** are committed in `application.properties`.
- **No admin role guard on `*/admin/*` endpoints yet** — admin panel relies on Bearer token from `localStorage.admin_token` but backend does not currently verify role. **TODO.**
- **WebSocket presence is in-memory only** — multi-instance deploys will desync `PresenceTracker`.
- **605 of 657 users (as of 2026-07-09) have zero `user_subscriptions` rows** — bulk test-data seeded directly into `users`/`user_details`, bypassing the real signup API (the only place that auto-assigns a Free-plan row). Any code that assumes every user has a subscription row will break on these accounts — use `UserSubscriptionsService.ensureActiveSubscription(userId)` instead of `userSubscriptionsRepository.findByUserIdAndStatus(...)` directly if a missing row would cause a bug (quota checks, entitlement checks, etc.) — see 2026-07-09 changelog entry for the exact bug this caused (frozen/unenforced request quota).
- **`PROFILE_VIEW_LIMIT` gate treats a DB value of `0` as "no limit," not "zero allowed"** — `UserService.getProfileDetailWithIntractionStatus` only runs the block/increment logic `if (limit > 0)`. Free and Starter both have `limit_value = '0'` in `planFeatures`, so all six plans currently allow unlimited profile viewing. **Resolved as intended, not a bug** (2026-07-09): matrimony platforms typically let free users browse freely and paywall the valuable actions (contact reveal, chat, horoscope) instead — blocking profile *browsing* at Free would hurt conversion. The in-code comment ("Free=0, Starter=20, Classic=100") describes a blocking design that doesn't match this and should be corrected/removed rather than the guard "fixed."
- **`UserDetailEntity.permanentAddress` is NOT "permanent address"** — as of 2026-07-14 it's repurposed to store "Native Place" (see that changelog entry), because the column existed but was never read/written by any code path, and adding a real "Native Place" column would have needed a migration. `presentAddress` = "Current Address" (unchanged meaning). Don't assume the column name matches its current UI meaning.
- **`UserEntity.age` is a frozen string set once at signup, never recalculated from `dob`** — every other service (`InterestRequestService`, `ShortlistedProfileService`, `ViewedProfileService`, `UserConnectionService`, `UserLikesService`, `UserService.java:~1601`) computes live age via `Period.between(user.getDob(), LocalDate.now()).getYears()`. `UserRepository.advanceFilter`/`normalFilter` were the sole holdouts still comparing `CAST(u.age AS UNSIGNED)` — fixed 2026-07-15 to `TIMESTAMPDIFF(YEAR, u.dob, CURDATE())` instead. If you add a new age-range query anywhere, compute it from `dob`, never trust the stored `age` column (it silently drifts — a user seeded at age 25 is still "25" in that column years later; confirmed live on `vvm_db` where a real user's stored age was 9 years stale). `AdminService.java:376-399` also updates `dob`/`age` independently with no recompute step tying them together — same risk if either is edited without the other.
- **Reporting no longer automatically blocks** (fixed 2026-07-20) — `UserReportService.reportUser` now only creates a `BlockedUser` row when the caller sends `blockUser: true` (null defaults to `true` for old clients, but the mobile app always sends this explicitly now). If you add a new caller of `POST /userReport/reportUser`, remember to pass this field deliberately — omitting it silently blocks, which is the exact bug that was fixed.
- **`UserReport.reportType` distinguishes PROFILE vs MESSAGE reports in the same table** — the admin's Profile Reports and Message Reports pages both read `GET /userReport/getAllReports` and filter client-side by this field. A legacy row with `reportType == null` is treated as `"PROFILE"` (the `@PrePersist` default), so old data still shows up on the profiles page correctly.
- **`Features.code` must never change after creation** — the admin `PUT /features/updateFeature/{id}` (added 2026-07-17) deliberately excludes `code` from what it can update, because every plan-gate check in the codebase (`featuresRepository.findByCode("VIEW_PERSONAL_INFO")` etc.) hardcodes that string. Renaming a code via direct SQL would silently break every check reading it, with no error anywhere.
- **Many `planFeatures` rows have no corresponding backend or frontend check at all** — `VERIFY_BADGE`, `HIGH_VISIBILITY`, `PRIME_VERIFIED`, `PRIORITY_SEARCH`, `STAR_MATCH`, `ID_VERIFIED_BADGE`, `EDUCATION_VERIFIED_BADGE`, `INCOME_VERIFIED_BADGE`. These are safe to seed/edit freely since nothing reads them yet, but don't assume seeding a row here changes app behavior — check section 7.2 for which codes are actually wired up before promising a client a gate works. Note: `POST /matching/porutham` still has NO backend plan-tier check at all (`STAR_MATCH` is one of the un-wired codes above) — the only gate is the frontend's `isPremiumValue` check in `ProfileDetail.tsx`/`StarMatch.tsx`, trivially bypassable by calling the endpoint directly. The interest-approval gate added 2026-07-15 (see changelog) doesn't address this — it's a separate concern (consent vs. plan tier).
- **Two different id-encoding conventions coexist on `RestrictedFieldRequest` status updates** — the mobile-facing `PUT /restrictedFieldRequest/updateStatus/{id}` expects a **base64-encoded** request id (mirrors how `userId` is encoded everywhere else), while the admin-facing `POST /restrictedFieldRequest/admin/{id}/status` (added 2026-07-17) takes a **plain numeric** id, matching `ServiceRequestController`'s admin endpoints. Before wiring up any new admin panel action against a mobile-facing endpoint, check whether it expects a base64 id — this exact mismatch is why the admin's Mobile Requests page silently 500'd on every approve/reject for who knows how long (see 2026-07-17 changelog).
- **Star Match is the Tamil Nadu 10-porutham system since 2026-09-27, counted INCLUSIVELY from the girl's star/rasi (her own = 1), graded Uthamam 1 / Madhimam ½ / Adhamam 0, headline = points out of 10.** The calculation is `utils/PoruthamEngine` (pure static) over `utils/StarInfo`; `PoruthamService` only resolves names and enforces the consent gate. Rajju and Vedhai are disqualifying; Dina/Gana/Yoni/Rasi/Rajju are the five essentials. ⚠️ **Any change to either file must pass `verify-porutham/PoruthamCheck.java` (71 rule checks) — a wrong table produces a plausible number and nothing else catches it.** ⚠️ **Do not reintroduce exclusive counting (`g - b`)** — that was the 2026-07-17 engine's mistake and it shifted Dina and Mahendra by one for every pair. ⚠️ Nadi is NOT one of the ten Tamil poruthams; it is returned in `extras` with `counted=false` and must never be added back into the score. The 2026-09-20 note that the old tables were 'verified correct' was wrong about Vedhai (its pairs were `{1,18},{2,17}…{9,10},{19,27}…` — a mirrored guess, not the almanac list) and did not check the counting convention at all.

## 18. Recent changes log (rolling, newest first)

### 2026-10-20 — "Profile approved" now reaches every approved member (push was plan-gated; edit-page approval was silent)
Client: the approval message is important. `AdminService.approveUser` already called email + WhatsApp + push, with two holes:
- 🔴 **The push went through `sendPushNotificationToUser`, which skips Free/Starter** — and a member being approved is always on Free, so the approval push reached nobody. Now `sendPushNotificationToUserDirect`.
- 🔴 **Approving through the admin edit page** (`updateUserFromAdmin` with `userStatus=APPROVED`) changed the status and sent NOTHING. It now fires the same message when the status moves INTO Approved (not on every save of an already-approved member), and clears the rejection reason / resubmission note like the Approve button does.
- New private **`notifyProfileApproved(user)`** used by both paths: email (only if the account has one), WhatsApp template `profile-approved` (`name`, `member_id`), DIRECT push, and a new in-app `Notification` row (category **`PROFILE_APPROVED`**; the app's NotificationScreen opens Home on tap). Each channel is try/caught on its own.
- Members imported straight as APPROVED (`POST /admin/users`) are deliberately NOT messaged — batch imports would otherwise send a burst of WhatsApp messages.
- ⚠️ WhatsApp only goes out when `ZOHO_CPAAS_ENABLED` is true on the server and the template stays approved in Zoho; email only when the member gave one (most imported members have none). Push only reaches a member who has opened the app since installing. Jar **21:41**, needs deploy.

### 2026-10-20 — Member import: 6 from biodata images + PDF (production, admin API)
`imports/import_2026-10-20a.py` (+ `.log.json`), same pattern as the 10-18/10-19 batches: dry-run CHECK by mobile (all six new), `POST /admin/users` (APPROVED, Free, PIN = last 4 digits of the mobile), `update-astrology-info` for star / moon sign / lagnam / birth time, photo via `/user/updateProfileImage`, then read-back + login check — all six log in (200 + token).
| userId | memberId | name | caste | district | notes |
|---|---|---|---|---|---|
| 1014 | UMR000177 | Jeyavani R (F, 1996) | Vanniyar / Gounder | Cuddalore | Avittam·Kumbam·Rishabam lagnam, 03:25 PM, B.Tech CSE, photo cropped from the biodata |
| 1015 | UMR000178 | Madhumitha S (F, 1999) | Vanniyar | Cuddalore | Magam·Simmam·Simmam, 07:24 PM, B.E ECE, CTS ₹50k/month |
| 1016 | UMR000179 | Vidhurasarani S (F, 1997) | Vanniyar | Salem | no star/rasi on the sheet; 09:20 AM; M.A English |
| 1017 | UMR000180 | Rajkumar R (M, 1986) | Adi Dravidar (3) | Mayiladuthurai | Punarpusam·Mithunam·Rishabam, D.ECE, panchayat president + business, ₹75k/month |
| 1018 | UMR000181 | Nathiya R (F, 1993) | Vanniyar (Arasu) | Kallakurichi | Pooradam·Dhanusu·Kumbam, M.Com (CA) |
| 1019 | UMR000182 | Sathish Kumar E (M, 1995) | Vanniyar | Chennai | Hastham·Kanni·Rishabam (from the chart), 11:20 AM, B.E Mech, CEVA Logistics |
Second phone numbers (Jeyavani 6382958882, Rajkumar 9750090826 / 9524195921, Nathiya 6381193699) are NOT stored — no field. Vidhurasarani's and Nathiya's sheets list no job → "Not working" / UNEMPLOYED (assumption). Subcaste left empty except Jeyavani (Gounder). Monthly salaries ×12 into `annualIncome`.

### 2026-10-20 — `change-email`: members replace their email after an OTP to the new address
- `AuthService.isLeadPurpose` accepts **`change-email`** (send + verify run through the same lead-table OTP/token path as `add-email`; a new address that belongs to a real account → 409 on send). `EmailService` uses the registration OTP template for it.
- `UserService.addEmail` → thin wrapper; new **`changeEmail`**; both call `saveVerifiedEmail(encodedUserId, email, token, replace)`. `replace=true` skips `EMAIL_ALREADY_SET`, returns 400 **`SAME_EMAIL`** when the address equals the current one, then the same token check (`EMAIL_NOT_VERIFIED`), uniqueness re-check (409) and token spend.
- New **`POST /user/changeEmail/{encodedUserId}`** body `{email, verificationToken}` (`UserController`). Same identity model as `addEmail` — no auth header (see AUDIT_REPORT.md); the OTP to the NEW address is the only proof required. The old address is not notified. Jar 19:51.

### 2026-10-19 (later) — Registration PIN chosen at start; resume by number + PIN; 🔴 login leaked name/rejection reason on a wrong PIN; PIN lockout (cross-repo)
Client reshaped the app flow (PIN on the number page, 3 steps, resume only by signing in — app CLAUDE.md same date). Server side:
- **`RegistrationService.start`** requires `pin` (`^\d{4}$` → 400 "Choose a 4-digit PIN") and stores it Base64 on the INCOMPLETE row (the form `createUser` writes). **`complete`** decodes that stored PIN into the `UserProfileRequest` when the body carries none (400 if neither exists).
- **`AuthService.sendMobileOtp`** purpose `registration`/`add-mobile`: an INCOMPLETE row now answers **409 "This number is already registered. Please sign in to complete your profile."** — Sign Up cannot restart a registration; purpose `start` is unchanged.
- **`UserService.login`** INCOMPLETE branch: the typed PIN is checked (`pinMatchesStored`); a match returns `403 REGISTRATION_INCOMPLETE` + `resumePayload(…, mint=true)` (draft + fresh registration token) so the app reopens the pending step on any phone; a mismatch is a plain `401 Login Failed`.
- 🔴 **Leak fixed:** the PIN comparison ran AFTER the BANNED / SUSPENDED / PENDING / REJECTED replies, so any caller who knew a number got that member's first + last name, base64 id and the admin's `rejectionReason` back with a WRONG PIN — and "Pin Not Setted" (204) confirmed an account existed. The PIN check (BCrypt or legacy Base64, family-login fallback kept) now runs before every status gate; a wrong PIN is `401` whatever the state; the 204 reply is gone (no app code read it).
- **Brute-force guard:** 5 wrong PINs per identifier (mobile/email, lower-cased) → `429 "Too many wrong attempts. Please try again in N minutes."` for 15 minutes; cleared on a correct PIN or family login; WhatsApp-code logins (`otpVerified`) are neither counted nor blocked. In-memory `ConcurrentHashMap` — resets on restart, per instance; fine for one jar.
- Verified on a local jar against `vvm_db`: wrong PIN on PENDING → 401 no data; right PIN → the 403 under-review payload; 5th wrong → 429; start without pin → 400; PIN login on an incomplete row → draft + token; send-otp on it → 409.
- ⚠️ Residual (unchanged): an unknown number still gets `404 User Not Found` while a wrong PIN gets `401` — a caller can still tell whether a number has an account.
- **Deployed 2026-10-19 ~23:45 IST (23:41 jar).** The client then hit the one case the rule cannot cover: Latha Manohar (user 1012, 9840134542) started under the OLD jar, so her INCOMPLETE row has **no PIN** — PIN login is 401 and, with the strict 409, Sign Up is blocked too. A 23:51 jar briefly let PIN-less rows re-register (send-otp allowed, `register/start` resumes the row and stores the PIN); the client wants the strict rule, so the **23:57 jar** answers 409 for EVERY incomplete row. ⚠️ Her row must be cleared by hand before that number can register (clear `mobile` on 1012 or delete the row — it holds no profile); any other pre-deploy incomplete row needs the same. ⚠️ `pinMatchesStored` returns false for a null stored PIN, so such rows can never log in.

### 2026-10-19 — New app registration API: account row at WhatsApp verification, resumable anywhere, admin "Incomplete Registrations" (cross-repo)
Client approved the phase-2 plan. **Separate API, nothing in `/user/createUser` or the old sign-up changed**: `RegistrationService` + `RegistrationController` at **`/auth/register/start | step | complete`**.
- **`start`** `{firstName, lastName, profileFor, mobile, mobileVerificationToken}` — strict WhatsApp-token check, ban list, 409 if the number is a real account; otherwise creates (or reuses a website-lead row) with **`users.registrationStage='INCOMPLETE'`**, `userStatus` NULL (hidden like a lead), `leadVerified='Y'`, mints **`registrationToken`** (UUID). Returns `{userId(b64), registrationToken, firstName, lastName, mobile, profileFor, draft}`.
- **`step`** `{userId, registrationToken, draft:{…}, doneSteps:[…]}` — merges into **`users.registrationDraft`** (TEXT JSON); names/profileFor in the draft keep the row in sync. 403 `REGISTRATION_TOKEN_INVALID` on a stale token.
- **`complete`** `{userId, registrationToken, pin, gender, dateOfBirth, age, casteId, subcasteId, location, education, occupation, employingIn, …}` — builds the EXACT `UserProfileRequest` the old page sends and calls **`UserService.createUser`** (unchanged): the row is CLAIMED (userStatus NULL → PENDING, memberId, user_details, Free plan, welcome WhatsApp). createUser's own WhatsApp-token check is satisfied by a token minted SERVER-SIDE for that call (never sent to the client). Then stage → `COMPLETE`, token + draft cleared.
- **Resume:** `verifyMobileOtp` (purposes `registration` / `start`) returns **`data.registration`** (= start's payload, token RE-MINTED → older phones' tokens die) when the row is INCOMPLETE. **`login`** (PIN or OTP) answers **403 `REGISTRATION_INCOMPLETE`** for such a row — the OTP path includes the resume payload with the CURRENT token (not re-minted, so it matches what verify just issued); the PIN path sends no data (no PIN was ever set, so a typed number must not expose the draft). New columns: `registrationStage`(20), `registrationToken`(64), `registrationDraft`(TEXT) — ddl-auto.
- **Admin:** `GET /admin/incomplete-registrations?page&size&search` → `{userId, name, mobile, profileFor, stepsDone, stepsTotal:4, gender, district, createdAt, updatedAt}`; `findWebsiteLeads` now EXCLUDES INCOMPLETE rows (a website lead who starts the app flow moves tabs).
- **Verified locally (log-only):** send → verify → start (row 760 INCOMPLETE) → step → PIN login 403 no data → re-verify on "another phone" returns the draft + new token, old token 403 → login-with-otp 403 with the SAME token → complete 200 → `UMR000005`, PENDING, COMPLETE, user_details 1, Free plan, welcome WhatsApp logged → PIN login = the normal "under review" 403 → admin list empty. Test rows removed. Jar packaged; **deploy with the app 1.0.6 build** (the app's new register screen calls these; an old jar answers 404 and the screen falls back to `createUser` — so nothing breaks either way).

### 2026-10-18 — Second member import: 8 new + 3 enriched, through the ADMIN API (no SQL)
Client supplied a typed note, 8 sheet rows, `biodata-2.pdf` and a WhatsApp biodata image. **3306 is not reachable from this machine** (the 8 Oct import used a `.env` that is no longer here), so this run went through production's own `POST /admin/users` / `PUT /admin/users/{id}` (signup parity: Base64 PIN, Free `user_subscriptions` row, memberId), `POST /user-details/update-astrology-info` for lagnam (the admin DTO has no lagnam field), and `POST /user/updateProfileImage` (multipart) for the two photos. Generator + log: `imports/import_2026-10-18.py` (`--apply`) → `imports/import_2026-10-18.log.json`.
- **Created userIds 976–983 / UMR000143–UMR000150** (Arumugam · Sriram S · Sathish Velan R · Gnanadevan R · Priyadharshini (F) · Yelumalai A · Prakash · Maharajan K). All APPROVED, active, Free, `location='Other'` (no place given), **no mobile → profile-only, cannot log in**. ⚠️ **Priyadharshini, Prakash and Maharajan have no DOB** — `filterUsers` always applies the age filter and NULL dob fails it, so those three are invisible in search until a DOB is added. Unknown subcastes kept as About notes (Sozhiya Vellalar, Devendrakulam); Gnanadevan → Sengunthar Kaikolar (21). Monthly salaries ×12 (18000→216000 …); Sriram's 250000 read as annual.
- **Enriched (already imported 8 Oct):** **946 Pandiyan B** = the Tamil biodata image — mobile **6381196352 + PIN 6352 (login verified 200)**, subcaste Gavara (8), mother, parents' occupations, Tiruvannamalai (place of birth + location), address, lagnam Mithunam, **photo**; second number 8489412171 not stored (no field). **947 Amar Raj** = `biodata-2.pdf` (same mobile 9345679364) — father spelling per his own biodata, Pondicherry, Kallakurichi (was Other), height 5'8", Single, Tamil, Apollo Main Hospital, address, **photo**. **950 Sundaramoorthy A** = the typed note — only height 5'5" and the fuller address were new.
- Photos were cropped from the documents with `sips` (no PIL here) and uploaded to R2 by the app's own endpoint — `profile-images/user_946/…`, `user_947/…`.
- **Verified live:** 11/11 via `/admin/users/{id}` + `getUserByUserId` (`astronomicInfo` is the key there, not `astroInfo`): memberIds, Free plan, star/rasi/lagnam, photos, active flags. Nothing deactivated this time.
- **Batch 2 (same evening, `imports/import_2026-10-18b.py`):** four more biodatas. **Created 984 Lokesh G (UMR000151, 9841430471/PIN 0471, Vanniyar Gounder, Bharani/Mesham/lagnam Magaram) and 985 Kishore Kumar Ravindran (UMR000152, 9840282705/PIN 2705, PG, Avittam/Magaram/lagnam Dhanusu, Rahu-Ketu → `Sarpa Dosham` per the 2026-08-17 mapping)**; **enriched 961 Karthik S** (= the Karthik S imported 8 Oct without a phone: mobile **9345621776/PIN 1776**, Chennai, Thiruvathirai/Mithunam, 04:23 AM, B.E Civil, PSU Homes, parents, sister married) and **964 Madhan A** (same mobile: birth time, Ambattur, height, address, siblings married). All four logins verified 200. ⚠️ The admin create answers `data: {memberId, userId}` — the first run passed that whole object to the astro endpoint (which still said 200); the lagnam for 984/985 was re-sent with the real ids and verified in `astronomicInfo`. Second phone numbers (Karthik 6369105143, Madhan 9789047819) not stored — no field.
- **Batch 3 (`imports/import_2026-10-18c.py`):** **Manojkumar S** (Tamil jathaga kurippu) — Vanniyar, b. 22-04-1983 Vellore, Magam/Simmam/lagnam Thulam, 07:25 PM, B.Com, Typist (Permanent) at the Principal District Court Vellore (GOVT, ₹4.8 L/yr), 2 sisters married, mobile **9566908331/PIN 8331** (second number 9363586905 not stored). userId/memberId in its `.log.json`.
- **Batch 4 (`imports/import_2026-10-18d.py`):** **Sneha Varadhan (F)** — **987 / UMR000154**, Vanniyar (Gounder/Naicker), b. 20-01-2002, Revathi/Meenam/lagnam Rishabam, 03:24 PM, 5'6", `degree=LAWYER` (BA LLB Hons SRM + LLM Ambedkar Law College), Chengalpattu (Madurantakam), 1 elder sister married, mobile **9443990389/PIN 0389** (login verified; second number 9344475471 not stored).
- **Batch 5 (`imports/import_2026-10-18e.py`), two brides:** **988 Ezhilarasi N / UMR000155** — Vanniyar Gounder, b. 19-01-1996 Puducherry (`location='Other'`: Puducherry is not a TN district in the `districts` list), Pooradam/Dhanusu/lagnam Kumbam, 09:07 AM, B.Arch (`degree=UG`), Architect (private), father late (Asst Agri Officer retd), mother JE PWD Puducherry, 1 brother, mobile **9487062584/PIN 2584**. **989 Seema S / UMR000156** — Vanniyar, b. 06-11-2000 Krishnagiri, Sathayam/Kumbam/lagnam Rishabam, 07:45 PM, M.Sc B.Ed (PG), no job (`employedAt=UNEMPLOYED`), 5'5", 1 brother, "Hosur groom only" kept in About, **photo** (cropped from the Chanakkian Matrimony sheet, their watermark trimmed), mobile **9942398495/PIN 8495**. Both logins verified; second numbers (7708241173, 9750636887) not stored.
- **Batch 6 (`imports/import_2026-10-18f.py`), two doctor brides, both with photos:** **990 Dr. Archana Natarajan / UMR000157** — Vanniyar, b. 19-10-1993 Kanpur, Avadi Chennai, native Tiruvannamalai, Kettai/Viruchigam (no lagnam on the sheet), 08:12 AM, `degree=DOCTOR` MBBS MD Anaesthesia, 5'5"/58 kg, father retd IAF + VAO, mother Asst Headmistress, sister married; photo is only ~120 px (the source was a small PDF render) — replace if a better one arrives; mobile **9943780940/PIN 0940**. **991 Dr. Priyadarshini V / UMR000158** — Vanniyar, b. 19-04-1995, Trichy (Uyyakondanthirumalai), Kettai/Viruchigam, 02:52 AM, MBBS, Government Medical Officer (GOVT, ₹9.6 L/yr), 164 cm, 1 brother, photo cropped from the Thirumanancheri sheet; mobile **9489830888/PIN 0888**. ⚠️ Not the same person as 980 Priyadharshini (Adi Dravidar, sheet row). Both logins verified; Archana's second number 9786855970 not stored.
- **Batch 7 (`imports/import_2026-10-18g.py`):** **992 Priyanka S (F) / UMR000159** — Vanniyar (sheet says subcaste "Pandhal", not in our list → About note), b. 30-08-1995, Salem, Chithirai/Kanni, 12:45 AM, B.E ECE, Software Engineer, salary "120000" read as MONTHLY → ₹14.4 L/yr (the <3L rule), 154 cm/55 kg, 1 younger brother, 2 sisters married, **photo** (Vanniyars Matrimony sheet), mobile **9942699153/PIN 9153**. **993 Gokul B (M) / UMR000160** — Vanniyar, b. 26-02-1998 Thirukoilur, Chennai (St Thomas Mount), Sathayam/Kumbam (no birth time or lagnam on the sheet), B.E Mech, Project Manager at Grupo Cunado India (sheet spells "CRUPO CUNADO"), ₹12 L/yr, 5'11", father SI of Police, 1 brother unmarried, mobile **9500037875/PIN 7875** (second 9498131254 not stored). Both logins verified.
- **Batch 8 (`imports/import_2026-10-18h.py`):** **994 Murali M / UMR000161** (Vanniyar, b. 04-07-1989 Puducherry → `location='Other'`, Punarpusam/Mithunam/lagnam Kadagam, 07:25 AM, B.Tech Mech, Manager at Built-up Engineering Goa, ₹12 L/yr, 5'7", 1 brother, **photo**, mobile 9843897668/PIN 7668); **996 Gokul V / UMR000162** (b. 18-06-1999, Kanchipuram, Magam/Simmam/lagnam Thulam, B.E+MBA (PG), paver-block business SELF, min ₹90k/mo → ₹10.8 L, O+, 1 younger brother, 7708464977/PIN 4977 — NOT the same person as 993 Gokul B); **997 Sugavaneswari P V (F) / UMR000163** (name read from a faint Tamil scan — `P.V. சுகவனேஸ்வரி`, confirm the spelling with the client; b. 02-07-2002 Salem, Kanchipuram, Uthirattathi/Meenam/lagnam Kadagam, **`dosham='Chevvai Dosham'`** (sheet: Chevvai 2,4,12, parihara done), 07:25 AM, B.Sc Physics + B.Ed, father Co-optex, 1 younger brother, 7200145966/PIN 5966); **998 Deepan Chakravarthi S / UMR000164** (b. 21-05-1997, Kallakurichi (Sirulapattu, Ulundurpet), Visagam/Thulam/lagnam Kadagam, 09:50 AM, B.E Civil CIT, Assistant Engineer Fisheries Dept Cuddalore GOVT ₹9 L/yr, elder sister married, 9514718166/PIN 8166, second 9486223589 not stored). ⚠️ **`user_details.about` is VARCHAR(255)** — Deepan's first create (a 330-char About) failed with "Transaction silently rolled back" and **burned userId 995** (no row; the sequence skipped it). The admin API does not truncate; keep About ≤ 255. All four logins verified.
- **Batch 9 (`imports/import_2026-10-18i.py`):** **999 Harikrishnan M / UMR000165** (Vanniyar, b. 05-02-1993, Kanchipuram (Thimmasamudram), Punarpusam/Mithunam/lagnam Magaram, 05:30 AM, BCA+DMLT, Lab Technician Blood Bank Govt HQ Hospital Kanchipuram GOVT ₹3.6 L, 5'8", youngest of 9 — 2 brothers + 6 sisters all married, **photo**, 9842351773/PIN 1773); **1000 Murugan P / UMR000166** (from a screenshot of his profile on ANOTHER matrimony app — **no DOB and no caste on it**: stored `age='35'`, `casteId=5` FREE CASTE BAR pending the client's answer; B.E+M.E Anna Univ (PG), Software Professional HCL Chennai ₹20 L, 5'8"/65 kg, Villupuram (Elavathadi, Melmalayanur), 9003581703/PIN 1703 — **cannot log in until a DOB is set**, see bug below); **1001 Aravindhan A / UMR000167** (Vanniyar, b. 21-09-1996, Cuddalore (Thethampattu, Srimushnam), Pooradam/Dhanusu/lagnam Dhanusu, B.Sc Agri, agri marketing, 5'8", father Cane Officer MRK Co-op Sugar Mill, sister married, 6379652587/PIN 2587, second 8248911912 not stored). Harikrishnan + Aravindhan logins verified.
- 🔴 **Bug found by Murugan's login test and FIXED in code: `POST /user/login` threw 500 (`getDob()` is null → `.toString()`) for any member without a DOB** — `UserService.login` line ~1705 now null-safe. Affects every DOB-less row (980/982/983 have no mobile anyway; 1000 has one). **Jar repackaged — deploy this one** (supersedes the 17:42 jar; nothing else changed). Until deployed, Murugan's login answers 500.
- **Batch 10 (`imports/import_2026-10-18j.py`), two Ranipet brides:** **Revathi V** — Vanniyar, b. 28-06-1997 Walajapet, Revathi/Meenam/lagnam Kumbam, 10:50 PM, M.Sc M.Phil Maths (PG), CBSE-school lecturer, 153 cm, **`maritalStatus='Divorced'`** (sheet: remarriage, divorced within a week — stated plainly in About), father late, elder sister married + younger brother, 9025647465/PIN 7465; **Dr. Varsha J** — Vanniyar ("Reddiyar" subcaste not in our list → About note), b. 14-02-2000, Rohini/Rishabam (no lagnam on sheet), 04:55 AM, MBBS MD Microbiology (DOCTOR), 157 cm, father Advocate, younger sister + brother, Arakkonam, **photo**, 9865526547/PIN 6547 (second 9003527218 not stored). userIds in the `.log.json`.
- **Batch 11 (`imports/import_2026-10-18k.py`), both with photos cropped from phone screenshots (small — ~160–200 px wide):** **Kokila A (F)** — Vanniyar, b. 08-06-1997 Tiruvannamalai, Punarpusam/Kadagam/lagnam Kanni, 02:00 PM, B.Sc Agri (UG, no job stated), father retired teacher, 2 younger brothers, 17 acres, 9443913134/PIN 3134. **Venkatesan P (M)** — Vanniyar, b. 10-04-1992 Thiruvengaranai, Kanchipuram, 10:00 PM (**no star/rasi/education on the sheet**), Foxconn Hai Oragadam ₹15k/mo → ₹1.8 L, 165 cm, youngest of four (all siblings married), 9787276920/PIN 6920 (second 8056541621 not stored). userIds in the `.log.json`.
- **Batch 12 (`imports/import_2026-10-18l.py`), four grooms:** **Vijay Anand G** (Vanniyar, b. 01-09-1991 Kanchipuram, Puducherry → `location='Other'`, Krithikai/Rishabam/lagnam Kanni, 08:35 AM, B.E Civil, Senior Engineer Leo Trading Maldives ₹14.5 L, 1 younger sister, 9940807224/PIN 7224); **Satheeshkumar B** (b. 04-03-1990, Cuddalore (Usuppur, Chidambaram), Rohini/Rishabam/lagnam Kumbam, 06:32 AM, BBA, A/C Mechanic Supervisor in Singapore ₹12 L, 165 cm, father late, brother + sister married, **photo**, 8098111139/PIN 1139, second 9345512748 not stored); **Satheeshkumar R L** (b. 11-09-1987 Kalpakkam, Cheyyar → Tiruvannamalai, Aswini/Mesham/lagnam Dhanusu, 02:30 PM, D.EEE+B.E, Production Engineer SADC Die Casting Chennai ₹4.8 L, 5'6", elder sister married, **photo**, 9894994425/PIN 4425, second 9790130033 not stored); **Ilansooriyan M** (b. 15-03-1994, Cuddalore (Konur, Thittakudi), Aswini/Mesham/lagnam Thulam, 08:30 PM, B.Tech Mech, Junior Cinematographer SICA, 173 cm, 2 sisters married, 9443186060/PIN 6060). Two different Satheeshkumars — distinct DOBs/mobiles, not duplicates. userIds in the `.log.json`.
- **Batch 13 (2026-10-19, `imports/import_2026-10-19a.py`):** **1010 Hariharan I / UMR000176** — **Kulalar (MBC) → `casteId=5` FREE CASTE BAR**, "Community: Kulalar" in About (8 Oct convention), b. 26-10-1999 Puducherry → `location='Other'`, Bharani/Mesham (no time/lagnam on sheet), M.Com (PG), Photographer at Jothi Studio Saram, ₹25k/mo+incentive → ₹3 L, 5'3", father Driver, mother JIPMER, younger sister married. ⚠️ The sheet lists only the MOTHER's number (9629788102) and the sister's — the mother's is stored as his mobile/login (PIN 8102), said so in About; swap in his own number when known. Login verified. No photo (empty frame on the sheet).
- **Undo:** the creates have no `createdBy` tag — delete by id (`user_subscriptions`, `user_details`, `users` WHERE userId BETWEEN 976 AND 983); the three updates are field-level (previous values in the 8 Oct preview/`import.sql`).

### 2026-10-16 (later) — Phone-first login: purpose `start`, `POST /user/login-with-otp`, `profileFor` (app 1.0.6 / code 13)
- **`AuthService.sendMobileOtp` purpose `start`** accepts any number (lead rows included) and answers `exists` (real account or not); `verifyMobileOtp` returns `verificationToken` + `exists` + `lead{firstName, profileFor}` when a website lead row exists. The app decides: exists → OTP login, else → sign-up with the token.
- **`UserService.loginWithOtp(mobile, verificationToken)`** → `checkMobileVerificationStrict` → `login(probe, true)` → `consumeMobileVerification` (the token is single-use; a replay answers 403 `MOBILE_NOT_VERIFIED`). `login(UserEntity)` became `login(user, otpVerified)`: with `otpVerified` the PIN check, the "Pin Not Setted" check and the family-login fall-through are skipped; lead rows (`userStatus IS NULL`) are "no account" (404) on both paths. The existing `POST /user/login` is byte-for-byte the old behaviour (`login(user, false)`).
- **`POST /user/login-with-otp {mobile, verificationToken}`** (UserController). Same response shape as `/user/login` (token, refreshToken, userId…), same 403 PENDING/REJECTED payload.
- **`UserProfileRequest.profileFor`** → `users.profileCreated` at `createUser` (Myself/Son/Daughter/…); email is now optional at registration — `createUser` already tolerated a null email; the app sends `email: null` + `verificationToken: null` when blank.
- Verified locally (log-only): registered number start→verify→login-with-otp 200; replay 403; unknown number exists=false, login-with-otp 404; PIN login unchanged. Jar **17:42**. Deploy before app code 13.

### 2026-10-16 — Forgot PIN on WhatsApp; website leads verified, listed for the advisor, and claimed by sign-up (cross-repo)
- **Forgot PIN:** `/auth/send-mobile-otp` purpose **`reset`** (number must belong to a real account, else 404) sends template `vvm_forgot_password_otp` (`36cd31e0-c950-11f1-85c1-8e9a6c33ddc2`, `zoho.cpaas.template.forgot-pin`); `/auth/verify-mobile-otp` with `reset` mints the SAME `users.resetToken` the email path does, so `/auth/reset-password` is untouched. The email reset stays for older app builds.
- **Website leads:** `createStarterProfile` now (a) rejects an invalid number, (b) **never creates a second row for a number** — a real account answers 409 "sign in to the app", an earlier lead row is updated — (c) accepts `mobileVerificationToken` from the website's new WhatsApp step (purpose **`lead`**) and stamps `users.leadVerified='Y'`. New `users` columns (ddl-auto): `leadStatus` (NEW | CONTACTED | CONVERTED | DROPPED), `leadVerified`, `leadNote`. A lead = a users row with `userStatus IS NULL` (`AuthService.isStarter`).
- **Lead → sign-up:** `send-mobile-otp` for registration/add-mobile/lead no longer 409s on a lead row; `verify-mobile-otp` returns `data.lead {firstName, profileFor}` for prefill; **`createUser` claims the lead row** (same userId, `leadStatus=CONVERTED`) instead of 401 — ⚠️ load-bearing: `findByMobile` returns ONE row, a second row for the number would break login.
- **Admin:** `GET /admin/website-leads?page&size&status&search` (paginated rows: userId, name, mobile, profileFor, verified, status, note, createdAt) and `PUT /admin/website-leads/{userId}` `{status, note}`; admin page Verification → **Website Requests**.
Verified locally in log-only mode: reset send/verify → resetToken; unknown number 404; lead code → verify → create (verified Y) → repeat create updates the same row; admin list; the lead can start app sign-up and gets its name back; a registered number cannot become a lead. Jar rebuilt 17:30.

### 2026-10-15 — Seven WhatsApp member notifications (Zoho CPaaS templates) beside the existing push + email
Client picked these from the suggested list; templates written here, approved in Zoho, keys in `application.properties` (`zoho.cpaas.template.*`, env-overridable; blank a key to switch that message off). `WhatsAppService.sendTemplate()` is the generic sender; notifications run on a 2-thread daemon pool (`notify()`) so an admin action or checkout never waits on Zoho — the OTP stays synchronous. A member with no valid 10-digit mobile is silently skipped.
| message | hook | merge keys |
|---|---|---|
| welcome | `UserService.createUser` success | name, member_id |
| profile approved / rejected | `AdminService.approveUser` / `rejectUser`, after the email | name, member_id (+ reason) |
| plan activated | `SubscriptionActivationService.activatePlan`, after the email (admin assign, Cashfree, direct create) | name, plan, valid_until ("until 12 Apr 2027" / "until marriage") |
| payment receipt | `CashfreeService.activate()` (plan) and the boost branch ("Profile Boost") | name, amount (Indian grouping), plan, order_id, paid_on |
| permission request → owner | `RestrictedFieldRequestService.sendRestrictedFieldRequest` | name, requester (plan-masked via `nameForRecipient`; "Someone" → "A member"), field |
| permission decision → requester | `updateRestrictedFieldStatus` | name, owner, decision (accepted/declined), field, **"1"** = owner's base64 id for the template's dynamic URL button `/profile/{{1}}` |
Buttons: request → `https://vaibhavvivaahamatrimony.com/open-app?to=requests` (web page translates to `vaibhavvivaaha://requests/permissions`); decision → `/profile/{{1}}`. Verified by sending the welcome template by hand (queued, MSG_101). Jar rebuilt.

### 2026-10-14 — Mobile number verified on WhatsApp (Zoho CPaaS) at sign-up and on the profile page (cross-repo)
Client: verify the mobile like the email, with an OTP; members with no mobile add one from the profile page. SMS needs DLT registration; WhatsApp does not.
- **`WhatsAppService`** sends Zoho CPaaS template `3fa7ab80-c6fb-11f1-a810-525400c92439` ("{{code}} is your verification code…", Copy Code button) from `+917200858854`. ⚠️ **INDIA endpoint only** — `cpaas.zoho.in`; `cpaas.zoho.com` answers 401 "Invalid API Token" for this account. The key is the same `Zoho-enczapikey` as ZeptoMail (`zoho.cpaas.api.key` defaults to `zeptomail.api.key`). Every send is a paid message; "LE_102 Credit exhausted" (429) means the CPaaS wallet is empty — top up in Zoho, no code change.
- **`mobile_otps`** (`MobileOtp`, ddl-auto creates it): one row per number — 6-digit code, 5-minute expiry, 5 wrong attempts then a new code, then a 30-minute `verificationToken`. `POST /auth/send-mobile-otp {mobile, purpose}` (rate limit 3 per number per 10 min) and `POST /auth/verify-mobile-otp {mobile, otp, purpose}`; purposes `registration` | `add-mobile`, both refuse a number already on an account (409) or banned (403).
- **`createUser`** takes `mobileVerificationToken`. ⚠️ Enforced only when `auth.require-mobile-verification=true` (env `AUTH_REQUIRE_MOBILE_VERIFICATION`, default false): older APKs send no token and must keep registering until the new build is on Play. A token that IS sent is always checked. Flip it on after the rollout.
- **`POST /user/addMobile/{encodedUserId}` {mobile, verificationToken}** — the mobile twin of `addEmail`: only for an account whose `users.mobile` is blank (imported profiles), never changes an existing number.
Verified: compiles; the CPaaS call was exercised by hand (accepted at `.in`, refused only for credit). Needs jar deploy + the app build.

### 2026-10-12 — Members with no email can add one from the app, OTP-verified (`add-email` purpose + `POST /user/addEmail`)
37 of the 38 imported members (2026-10-08) have `users.email = NULL`. Nothing breaks for them (every mail sender already guards a blank address; login is mobile + PIN), but the ONLY forgot-PIN path is email OTP, so they could never recover a PIN themselves, and the app's Email field is read-only. Client chose the self-service fix over an SMS gateway.
- **`AuthService`**: new purpose **`add-email`** on `/auth/send-otp` + `/auth/verify-otp`, identical to `registration` (`isLeadPurpose()`): banned-identifier check, **409 if the address already belongs to an account**, OTP on the `user_leads` row, `verificationToken` (30 min) on verify. `EmailService.sendOtpEmail` uses the registration ("verify your email") template for it, not the PIN-reset one.
- **New `POST /user/addEmail/{encodedUserId}`** body `{email, verificationToken}` → `UserService.addEmail`: 404 no user · **400 `EMAIL_ALREADY_SET`** (changing an existing email stays a support action — it is a login identity) · 400 invalid address · **403 `EMAIL_NOT_VERIFIED`** (no lead / not verified / token mismatch / expired) · 409 taken by another account (re-checked at write time) · 200 `{email}` — stored trimmed + lowercased, the lead's token spent. ⚠️ The lead row is keyed on the address as TYPED (not lowercased) — looked up by the typed value first, then lowercased.
- Nothing else changes: approval / plan / receipt mails simply start arriving once the address exists; an earlier receipt (`EMAIL_NO_ADDRESS`) can be re-sent with the admin resend endpoint.
Compile + package clean. **Needs deploy** — until then the app's Verify button gets "Invalid purpose" from the old jar (surfaced as "Could not send code"). Not exercised against a running server this session (local MySQL was down); logic mirrors the registration path line for line.

### 2026-10-08 (later 3) — `GET /admin/stats/overview`: per-day activity for the redesigned admin dashboard
New `AdminService.getActivityOverview(from, to)` + `AdminController` `GET /admin/stats/overview?from=YYYY-MM-DD&to=YYYY-MM-DD` (or `?days=N`, N days ending today IST; capped at 370 days). One response: `totals` and `previous` (the window of the same length immediately before — for "vs previous 7 days"), `daily[]` (one row per IST calendar day, zero-filled), `snapshot` (live counts) and `planMix`.
- Metrics (native SQL via `EntityManager`, grouped by `DATE(CONVERT_TZ(col,'+00:00','+05:30'))` because the production JVM/MySQL run UTC): `registrations` (+Male/Female, `users.createdAt`, admins excluded), `interestsSent` (`interestRequest.createdAt`), `interestsAccepted` (`updatedAt` where APPROVED — approximate), `messages` (`chats`), `profileViews` (`viewedProfile`), `likes`, `shortlists`, `onlinePayments`+`Amount` (`payment_orders` PAID by `DATE(fulfilledAt)` — ⚠ **no CONVERT_TZ: `payment_orders` timestamps are written as IST wall-clock by `CashfreeService.istNow()`**), `manualPayments`+`Amount` (`PAYMENT_REQUESTS` APPROVED by `COALESCE(verified_at,created_at)`), `paidPlans` (`user_subscriptions` with plan ≠ 1), `boosts`, `reports` (`reportedAt`), `supportTickets` (`created_at`), `callbacks`, `serviceRequests`; derived `payments` = online+manual and `revenue` = both amounts.
- `snapshot`: totalMembers / activeMembers / approvedMembers / pendingApprovals / rejectedMembers / blockedMembers / activeMale / activeFemale / withPhoto / onlineNow / pendingManualPayments / openReports (status PENDING) / openTickets (OPEN, IN_PROGRESS) / newCallbacks (NEW) / pendingServiceRequests / activeBoosts / activeConversations (ACCEPTED) / lifetimeRevenue (Cashfree PAID + manual APPROVED) / paidMembers. `planMix` = active members by latest ACTIVE unexpired plan (same subquery as `findAllUsersForAdmin`), `Free` = activeMembers − paid.
- Also fixed in passing: `getDashboardStats.totalRevenue` now includes Cashfree PAID orders (it only summed `PAYMENT_REQUESTS` before, i.e. ₹0 for every online payment).
- Verified on the new jar against the production DB: `?days=7` and `?from=2026-09-20&to=2026-09-30` (17 payments, ₹36,335 — matches the 20-order ledger minus the three ₹1 tests outside the range). No audit log is written for approvals (`logAdminAction` has zero callers), so approvals-per-day cannot be charted — the snapshot's `pendingApprovals` is the signal instead. Jar `target/app.jar` 16:43 — **deploy with the admin build of the same date.**
### 2026-10-08 (later 2) — Star, Moon Sign & Lagnam are now a paid benefit on another member's profile
Client: Lagnam should be premium-only "like star" — but Star was in fact visible to everyone; client chose to lock all three for Free viewers. **Backend** (`getProfileDetailWithIntractionStatus`): `astroLocked = !isSelf && (viewerPlanId == null || viewerPlanId == 1)`; when true the viewed member's `astroInfo[0].star / moon_sign / lagnam` are blanked via new `stripAstroKeys()` and the response carries **`astroLocked: true`**. ⚠️ The entity is **`entityManager.detach`ed before blanking** — the request shares one persistence context (open-in-view), so any later save in the same request could otherwise flush the blanked copy to the DB. (The older horoscope / birth_time strip right above does NOT detach — same latent risk, left as is.) **App** (`ProfileDetail`): reads `astroLocked`, passes it to `InlineProfileTabs`, and renders ONE full-width `PremiumLock` ("Upgrade to … to see Star, Moon Sign & Lagnam", via `upgradeMessage(…, 'Starter')`) in place of the three rows. Trial and every paid plan see the values. ⚠️ Only this endpoint masks: search / home shelves / `getUserByUserId` still return astroInfo in their JSON (none of their cards display it) — same class as the `hidden_fields` note in §17. ⚠️ Deploy order: a jar deployed BEFORE the new app makes the old app show "-" for those three fields to Free viewers (no lock, no error). Jar 16:07; AAB code 10 rebuilt with it.

### 2026-10-08 (later) — Lagnam (ascendant) added: profile, profile detail, edit page, completion (cross-repo)
Lagnam is a DIFFERENT fact from Moon sign/Rasi (the rising sign at birth vs the Moon's sign) and is not used by the 10-porutham match, so it gets its own field: **`astroInfo[0].lagnam`** (JSON — no schema change).
- **Options come from keyValue `rasi`** (client: "get the data from keyvalue, keep the Tanglish names"): `[{id,name}]` × 12 — Mesham, Rishabam, Mithunam, Kadagam, Simmam, Kanni, Thulam, Viruchigam, Dhanusu, Magaram, Kumbam, Meenam (the spellings the stored data already uses). Every keyValue row reaches `masterData` via `getAllKeyValues`, so no backend endpoint was needed; `EditProfileSection`'s master-data guard now also requires `rasi`.
- **Moon Sign now reads the same `rasi` row.** Its old hardcoded list spelled three signs differently from the data (Vrichigam / Dhanushu / Makaram), so a saved Viruchigam/Dhanusu/Magaram never pre-selected. Bundled `rasiFallback` (same spellings) only if the row is missing. The single stored "Makaram" was normalised to Magaram (backup `_bak_user_details_lagnam_20261008`).
- **App:** edit page Religious → Lagnam picker after Moon Sign with hint "Rising sign at birth — not the same as Moon sign" (picker fields now show `hint` like text/time fields); payload `lagnam`; profile tab Religious card row (subtitle "8 fields"); ProfileDetail Religious Details row (not masked — like star/moon sign); `profileSections` maps `lagnam` → ReligiousDetail/Lagnam so the completion hub and nudges open the right field.
- **Backend:** `AstrologyInfoRequest.lagnam` (null = older app, stored value left alone); `UserDetailService` saves it; `calculateProfileCompletion` counts it → **37 fields** (was 36). Admin edits keep it (they merge into the existing astro map). Jar 15:59.
- **Data:** the 17 imported members whose sheet had a real Lagnam now have it in `astroInfo[0].lagnam` (Pandiyan's "ayilyam" is his star, not a lagnam); the "Lagnam: X." text was removed from their About. Earlier I said 23 — the sheet has 17 valid values.
- Needs **backend deploy + app build (code 10)**. Until the jar is deployed, saving Lagnam from the new app is silently ignored (old DTO drops the unknown field).

### 2026-10-08 — First real member import: 38 members live, every earlier member deactivated (cross-repo)
Client sheet "Caste report.csv" (38 members, 31 M / 7 F; 1 email). Imported straight into production `vaibhavVivaaha` in ONE transaction, with signup parity (users + user_details + Free `user_subscriptions`, `isOnline=0`, `view_count=0`, `autoRenew='N'`, `isBlocked='N'`, `isUser='FA'`, APPROVED). Rows tagged `createdBy='import-2026-10-08'`; userIds **938–975**, memberIds **UMR000105–UMR000142**. Generator (no personal data): `VaibhavVivaahaBackend/imports/build_import_2026-10-08.py`.
- **Before the insert, all 238 previously active members were set `isActive='N'`** (nothing deleted). Backup table **`_bak_users_import_20261008`** (userId, isActive, mobile for all 757 rows, created OUTSIDE the transaction — CREATE TABLE implicitly commits). ⚠️ `isActive='N'` only hides a member from discovery/search; **login does not check it**, so the Play reviewer account 706, test accounts 935–937 (937 is Gold with paid orders) and all old test rows can still sign in.
- **Client decisions:** castes not in the app (Kanvundar, Pillai, BC, Velalar, SC, "No caste bar") → **FREE CASTE BAR (5)**, original caste written into About as "Community: …"; **PIN = last 4 digits of the member's mobile** (Base64, as login expects); 3 members with no phone (Chandra Mohan V, Pandiyan B, Karthik S) imported as **profiles only** — they appear in search but cannot log in until a mobile + PIN is added.
- **Normalised to the app's vocabularies** so search finds them: stars to the `star` keyValue spellings (Uthratathi→Uthirattathi, shatabhisha→Sathayam, Ashwini→Aswini, kottai→Kettai, Pusam→Poosam, Chitra→Chithirai…), rasi to stored moon_sign spellings, Lagnam (no field) into About, education → degree category (UG/PG/ENGINEER/DOCTOR/OTHERS) + raw text in `educationInDetail`, birth time → `hh:mm A`, place → district (15 with no place → `Other`), subcaste Gajulu Balija→Balija(7), Kamavar→Kamma(6). **Income: values under 3L read as MONTHLY ×12**, ≥3L as annual, "x lpa" as lakhs. Extra phone numbers were NOT stored (no field; About is visible to members). Dropped Santhosh Kumar R's invalid 9-digit second number.
- **Mobile clash fixed:** Moorthy S's 9003884410 was on old test row **129** (PENDING); its mobile was cleared (in the backup), because `findByMobile` must return one row or login fails for that number.
- **Verified live:** 38/38/38 rows; only the 38 are active; Raja (9941354495/4495), Dhunisha, Moorthy log in (200), wrong PIN 401, 706 still logs in; `filterUsers` returns exactly the imported members per gender+caste (F Vanniyar 5, F Adi Dravidar 2, M Vanniyar 13, M Naidu 8, M Free Caste Bar 6).
- **Undo:** `DELETE FROM user_subscriptions/user_details/users WHERE createdBy='import-2026-10-08'` (children first), then `UPDATE users u JOIN _bak_users_import_20261008 b ON b.userId=u.userId SET u.isActive=b.isActive, u.mobile=b.mobile;`.

### 2026-09-27 (later 5) — Three gallery endpoints so a photo can exist before the member does
The admin panel's new picker (Clothing-style: upload a file OR paste a URL) needs every image to be a URL before anything is attached to a member — a photo can be chosen while creating someone who has no id yet. Nothing in `GalleryController` did that: every existing endpoint binds a file to a userId in one step.
- **`POST /gallery/uploadImage`** — multipart `file` (+ optional `folder`) → stores it via `S3FileUploadService` and returns the URL. No member, no DB row. Staging folder `adminUploads/` when no folder is given.
- **`POST /gallery/addGalleryImageByUrl/{userId}`** — `{imageUrl}` → a `GalleryEntity` row pointing at an already-hosted image.
- **`POST /gallery/setImageUrl/{userId}?horoscope=true|false`** — points `users.profileImage` or `user_details.horoscope` at a hosted image.
⚠️ All three take the **RAW numeric** userId, matching `uploadGalleryImage`, NOT the Base64 one `getAllImagesByUserId`/`setAsProfileImage` take. That split is pre-existing and is exactly why the id encoding is spelled out in each javadoc. 🔴 `addGalleryImageByUrl` now enforces `MAX_GALLERY_PHOTOS` (3) like `uploadGalleryImage` — without it the admin panel could add photos past the cap the member's own app shows and enforces. Jar 17:58, clean, no stubs. **Needs deploy** — until then the admin picker 404s on upload (and says so).

### 2026-09-29 (later) — Payment window 30 → 15 minutes
`cashfree.order.expiry.minutes=15` (client). ⚠️ 15 is Cashfree's MINIMUM for `order_expiry_time`, measured when Cashfree receives the request — sent as exactly now+15m, latency plus the seconds truncation would land just under it and every order could be refused. So `createOrder` adds a 30s cushion at the floor (Cashfree sees 15m30s) and clamps anything lower to 15. Retries inside the Cashfree page work for those 15 minutes; after that a new tap makes a new order. Jar 17:54, needs deploy — create one sandbox order afterwards to confirm Cashfree accepts it.

### 2026-09-29 — Wallets removed from checkout (client decision)
Cashfree now offers UPI · cards · net banking only. Reasons: little use at ₹2,499–₹9,999, wallet brands are reachable through UPI anyway, and the wallet page was the flakiest in testing (22 Sep: stranded-page bug, pre-filled phone rejected). Server: `cashfree.payment.methods` default `upi,dc,cc,nb` (properties AND the `@Value` fallback in CashfreeService). ⚠️ If the server sets `CASHFREE_PAYMENT_METHODS`, that env var wins — it must drop `app` too. App: `CFPaymentModes.WALLET` removed from the Drop builder; PaymentReturn's no-UPI-app copy says "card or net banking". Website: Subscription Policy and `/pay` no longer mention wallets (Refund + Privacy wording kept — it still covers past wallet payments). Needs jar deploy + web deploy; the app change rides the next APK (older APKs are fine — the order decides what renders).

### 2026-09-28 (admin, later) — WEBHOOK / CALLBACK trace lines for every Cashfree call
Client: log every webhook and callback, named WEBHOOK and CALLBACK, with System.out too. `CashfreeController` now writes one line per call to loggers named `WEBHOOK` / `CALLBACK` AND stdout (timestamped), at the CONTROLLER so no early return in the service is missed:
- `WEBHOOK in type=… order=… paymentStatus=… amount=… cfPaymentId=… attempt=… bytes=… ip=…` then `WEBHOOK out order=… http=200 (accepted) | 401 (signature rejected) | 500 (failed, Cashfree will retry)`. The body is parsed for the log line only; the service still verifies the signature over the raw bytes.
- `CALLBACK order=… requester=… code=… orderStatus=… paymentStatus=…` for every `GET /order/{orderId}/status`, which is the server side of the app's PaymentReturn screen (after the SDK callback) and the website's /payment/return page. The SDK callback itself runs on the phone and never reaches the server.
Only ids and statuses are printed, never name/phone/email. On the server: `grep -E "WEBHOOK|CALLBACK"` in the app's stdout log. ⚠️ The confirm screen polls every 2.5s for up to 40s, so one payment can print ~16 CALLBACK lines. Jar 18:21, needs deploy.

### 2026-09-28 (admin) — User Management's Premium/Free tabs now follow the REAL subscription; newest first
Client: the Premium Users tab listed Free members. The tabs sent `userType` PU/FA and `findAllUsersForAdmin` filtered on **`users.isUser`**, a stale flag — production had 56 PU-flagged members on Free or with no plan, and 3 paying members still flagged FA. Now PU/FA are derived from the member's **latest ACTIVE, unexpired subscription** (same rule as `UserSubscriptionsRepository.findTopByUserIdOrderByCreatedAtDesc`, which the app gates on); only ADM still reads `isUser`, and admins are excluded from both. The Plan column and the plan filter use the same join, so an expired member now shows **Free** (the old join showed the newest row regardless of status). The plan filter uses `COALESCE(sp.title,'Free')`, so filtering by Free also finds members with no subscription row. Order is `createdAt DESC, userId DESC`: 757 users share only 27 distinct `createdAt` values, and without the tiebreaker, rows could move between pages. The page query and countQuery were changed together. Verified read-only on production: PU = 27 (Gold 11, Platinum 10, Silver 3, Trial 3), FA = 729, ADM = 1. Jar 17:33, **needs deploy**; no admin-panel change.

### 2026-09-28 (later) — `seed-terms-content.sql`: app Terms generated from the website's source (no code change)
New **generated** seed file — do not hand-edit it. Produced by `VaibhavVivaahaWeb/scripts/export-app-terms.mjs` from `VaibhavVivaahaWeb/src/content/legal.js`, the same text the website's Terms page renders. DELETE-then-INSERT on `TERMS_CONTENT`, same pattern as `seed-support-content-v2.sql` (keyColumn has no unique index). It replaces the app's bundled Terms, which claimed memberships "automatically renew" and can be "cancelled through account settings" — `autoRenew` is hardcoded `N` in every creation path and there is no cancel endpoint. Installed apps pick it up within 6 hours, no release. 15 sections, all-or-nothing (a partial row would publish a truncated legal page — the script refuses to write one). ⚠️ **Not yet applied to any database** — it is published legal text and needs the client's review first. No Java change; no deploy.
✅ **APPLIED to production 2026-09-28** (client instruction). Backup: table **`_bak_keyvalue_terms_20260928`** (1 row, the previous value) — restore with `UPDATE keyValue k JOIN _bak_keyvalue_terms_20260928 b ON b.id=k.id SET k.valueColumn=b.valueColumn`. Verified by reading the row back: parses to content **identical** to `legal.js`, 15 sections, no mojibake; and `GET /keyValue/getKeyValueByKey/TERMS_CONTENT` serves it. Installed apps switch within 6h (cache).
🔴 **Correction to what this entry first said:** members were NOT seeing the bundled Terms with the false auto-renewal §5 — a **12-section remote `TERMS_CONTENT` row was already live**, and it was mostly sound (no auto-renew, non-refundable except where law requires, 21 men / 18 women). Its real defects were **four literal placeholders shown to every member** — `«LEGAL ENTITY NAME»`, `«REGISTERED OFFICE ADDRESS»`, `«CITY»`, `«GRIEVANCE OFFICER NAME»` — and a §5 describing manual, team-verified payment with no mention of Cashfree. 💡 Before claiming what a keyValue-driven screen shows, read the row — the bundled fallback only renders when the row is absent.
⚠️ **Two traps hit applying it:** (1) the `mysql` client defaults to **latin1** — reading the row that way failed to decode, and writing the seed that way would have turned every "—" and "’" into mojibake; always pass `--default-character-set=utf8mb4`. (2) `valueColumn` is a MySQL **JSON** column, which re-serialises with spaces after `:` and `,` — so the stored `CHAR_LENGTH` (6,764) differs from the generated string (6,687) with identical content. Compare parsed JSON, never lengths.


### 2026-09-28 — createOrder no longer REUSES orders: every tap mints a fresh Cashfree order + session (client decision)
Client, after measuring it on production: five deliberate "Continue with this plan" → back → retry attempts produced **two** `payment_orders` rows, 28m10s apart. The 28-minute dedupe window (`cashfree.order.expiry.minutes − 2`) returned the EXISTING order for the same user+purpose+plan+amount+channel, and with it the **original `paymentSessionId`** — a session is minted once, at order creation, so a member whose first session could not open a payment page was handed that same dead session on every retry until the window lapsed, then it "suddenly worked". Worse, the reuse path returned BEFORE the `order_created` local log row, so attempts 2–4 wrote **nothing** to either table and the trail understated what the member did.

The reuse block is deleted from `CashfreeService.createOrder` (the `findByUserIdAndPurposeAndOrderStatusOrderByIdDesc` finder stays in the repository, now unused). Double-submit protection lives on the button instead, where the intent is known: `PremiumTab` now guards with a ref (a real mutex — the old state read was a stale closure) and `GatewayCheckoutView` already had `busy`/`disabled`. Residual exposure if a duplicate ever slips through is two ACTIVE orders, which is harmless — an unpaid order just expires; a double CHARGE would need the member to complete payment twice. ⚠️ **Do not reintroduce reuse here**; if duplicates ever matter, dedupe on a per-tap idempotency key the client sends, never by handing back an unchecked session. 💡 Side effect: abandoned checkouts now leave one ACTIVE row each until the nightly job expires them (it calls `getOrderStatus` per stale row first, so slightly more Cashfree traffic at 00:15). `./mvnw compile` clean; jar rebuilt. **Deploy the jar.**

⚠️ Recorded the same day, unrelated to this change: production sandbox returned **HTTP 400 `payment_gateway_inactive` — "payment gateway product is not activated for your account"** on `POST /pg/orders` at 12:13:07 IST, then accepted the identical call 50s later; both orders that day show `paymentStatus: null` (no payment ever attempted). That is a Cashfree account state, fixed in their dashboard — the code, jar, `return_url` suppression and `channel` field were all verified correct against five PAID orders from 27 Sep. `GET /payments/cashfree/admin/log?day=` and `…/admin/orders/{id}/log` are where the real Cashfree bodies live; `admin/orders` rows do not expose `channel`.

### 2026-09-27 (later 5) — Star Match rebuilt as the Tamil Nadu 10-porutham system (cross-repo)
Client: "search and collect original information about star match, find the exact logics which suit Tamil Nadu, implement it, and keep only the required fields on the UI." The previous engine was a hybrid — the ten Tamil poruthams **plus Nadi at 30 % weight**, a North-Indian percentage model, exclusive star counting, and a Vedhai table that matched no source.

**Sources** (all 2026-09-27): findyourfate 10porutham, hindu-blog + astroved (rajju zones), epanchang (vedha pairs, vasya table), astrologyatoz + priestservices (dina/stree dheerga/rasi grading, same-star rule), dheivegam. Where two sources differed the classical Tamil-almanac form was taken and is commented beside the table.

**New `utils/PoruthamEngine.java`** (pure, static — no Spring, no DB, so it can be run from a plain `main`) and rewritten **`utils/StarInfo.java`**:
- **Counting is inclusive, Tamil style**: from the girl's star to the boy's, her own star = 1 (`countStars`). The old engine used `g - b` (girl = 0), so Dina and Mahendra were **off by one for every pair** — girl Aswini / boy Rohini is the 4th star and a Mahendra match; the old code called it the 3rd and failed it. The 2026-07-17 "Dina inversion fix" had actually inverted a rule that was right under exclusive counting.
- **Dina**: count mod 9 ∈ {0,2,4,6,8} → Uthamam; same star → Uthamam for Rohini/Thiruvathirai/Poosam/Magam/Hastham/Visagam/Uthiradam/Thiruvonam/Revathi, Madhimam for Aswini/Bharani/Krithikai/Mirugasiridham/Punarpusam/Uthiram/Chithirai/Anusham, else Adhamam. **Gana** 9/9/9 split; same = U, Deva-girl/Manushya-boy = U, Manushya-girl/Deva-boy = M, Manushya/Rakshasa = M, Deva/Rakshasa either way = A. **Mahendra** counts 4,7,…,25. **Stree Dheerga** >13 U, >7 M. **Yoni** 14 animals with sex per star; enemy pair = A, same animal opposite sex = U, same sex = M, other = U. **Rasi** (from the girl's): same or 7/9/10/11/12 = U, 3/4 = M, 2/5/6/8 = A. **Rasi Adhipathi** Naisargika Maitri: same/mutual friends = U, neutral = M, enemy either side = A. **Vasiya** classical rasi table: boy in girl's vasya list = U, girl in boy's = M, same rasi = M. **Rajju** five body zones (Pada 1·9·10·18·19·27, Kati 2·8·11·17·20·26, Nabhi 3·7·12·16·21·25, Kanta 4·6·13·15·22·24, Siro 5·14·23); same zone = Adhamam, same star on the exempt list = Madhimam. **Vedhai** the almanac pairs 1-18, 2-17, 3-16, 4-15, 6-22, 7-21, 8-20, 9-19, 10-27, 11-26, 12-25, 13-24 and the mutual triple 5-14-23 — **the old table (`{5,14},{6,13},{7,12}…`) was wrong for 11 of the 15 pairs.**
- **Verdict**: Rajju or Vedhai Adhamam → NOT RECOMMENDED regardless; ≥7.5 points with all 5 essentials → EXCELLENT; ≥5 with all essentials → GOOD; ≥5 otherwise → FAIR; else NOT RECOMMENDED. Nadi is computed but returned in `extras` with `counted=false`.
- **Names**: `StarInfo.starId()/rasiId()` accept every spelling in play (the `star` keyValue vocabulary, the edit page's `Vrichigam`/`Dhanushu`/`Makaram`, the old Star Match list's `Viruchagam`, Sanskrit/English). `getStarId` no longer reads the `star` keyValue row on every call. A missing/unknown star is a 400 with the offending value, not a silent all-UNKNOWN result.

**Response** (`MatchResponseDto` / `PoruthamResultDto`) is additive: `poruthamPoints`, `totalPoruthams`, `uthamamCount/madhimamCount/adhamamCount`, `essentialsPassed/Total`, `doshams[]`, `verdictNote`, `extras[]`, canonical `brideStar/groomStar/brideRasi/groomRasi`; per row `grade`, `points`, `essential`, `counted`. `score`/`percentage` = points × 10 and `result` PASS/PARTIAL/FAIL stay so the 22 Sep APK still renders. `dob/timeOfBirth/place` on the request are accepted and ignored — the method does not use them. `RasiLordMapper` deleted.

**Verification**: new `verify-porutham/PoruthamCheck.java` — 71 checks (table cardinalities, symmetry, every alias, every grading branch, two full evaluations, unknown-rasi path), **all pass**; README explains how to run it. ⚠️ **Production was already answering with this engine at 16:1x** (probed: `doshams`, `extras`, inclusive Mahendra) although I did not deploy and `origin/main` is a year old — the 16:08 `target/app.jar` was deployed by hand by someone else. It is the intended build. App side in the app CLAUDE.md same date.

### 2026-09-27 (later 4) — All Matches (`getUserDetailByCasteId`) orders boosted members first
Audit of where a paid boost applies: `advanceFilter`/`normalFilter` (Search) and `findInterestMatches` already `ORDER BY … hasActiveBoost DESC`; the All Matches endpoint — home shelf + full list — returned `findAllByCasteIdAndGender…` unordered, so a boost did nothing there. `getUserDetailByCaste` now loads running boosts (`ProfileBoostRepository.findAllByStatusAndExpiresAtAfter("ACTIVE", now)`, new), stamps a new **`@Transient Boolean hasActiveBoost`** on `UserEntity` (null → absent from JSON on every other endpoint) and stable-sorts boosted first; wrapped in its own try so a boost-table hiccup never fails the list. 💡 The three SQL joins use `pb.expiresAt > NOW()` — IST digits vs a UTC clock — but the 10-minute expiry job flips `status` first, which the joins also check, so the overshoot is ≤10 min. Jar 13:51, clean. **Needs deploy** (with the boost-credit carry-forward, e-mail wording, subscriptions sort and member search).

### 2026-09-27 (later 3) — 🔴 Paid boost credits were lost on every plan change
Client: "profile boost clicked and started the timer from 18 hrs — check and fix." Two separate things behind it.
- **The 18h30m countdown** is the app parsing zone-less UTC digits as IST (fixed in the app, same date). The server's `expiresAt` is `LocalDateTime.toString()` with no zone — consistent with every other timestamp it sends.
- **🔴 The boost credit vanished.** `boost_credits` is a column on `user_subscriptions`, i.e. per subscription ROW, and `addBoostCredit` / `startBoost` only touch the ACTIVE row. `activatePlan`'s supersede step closed the old row **without moving its credits**, so any boost bought and not yet used was stranded the moment the member changed plan. Reproduced on user 933: ₹149 boost credited to row 291 (Platinum) at 12:33:40, plan replaced by admin-assign at 12:37:19 → row 291 EXPIRED with `boost_credits = 1`, active row 0, `/boost/status` says `remainingCredits: 0`. **`activatePlan` now sums the closing rows' credits, zeroes them there, and writes the total onto the new row** — moved, not duplicated. Jar rebuilt clean. The stranded credit on 933 was moved by hand to the active row (295) the same day.
- 💡 The first boost (row 2) did not expire early by itself: `adminRevokeBoost` — the only path that writes `status=EXPIRED` + `expiresAt=now` — ran at 12:33:10. Someone pressed Revoke in the admin panel. ⚠️ Neither `profile_boosts.updatedBy` nor admin-assigned subscriptions' `createdBy` records WHICH admin acted; both are NULL. Worth wiring the admin identity in when auth returns.

### 2026-09-27 (later 2) — Activation e-mail says "valid until marriage" for Platinum
`sendPlanAssignedEmail` received `endDate.toString()`, so a Platinum member was told their plan was "valid until 2054-02-…" — the 9999-day sentinel printed as a date. New `SubscriptionActivationService.validityText(plan, sub)`: `durationDays >= 9999` → "until marriage", otherwise "until 26 Dec 2026" (`d MMM yyyy`, English locale so the month never localises to the server's language). The e-mail sentence now reads "It is valid <b>{validity}</b>." Jar rebuilt clean. **Needs deploy** (with the newest-first subscriptions sort and the server-side member search from earlier today).

### 2026-09-27 (later) — `/admin/subscriptions/users` sorted newest first
`getAllUserSubscriptions` used an unsorted `findAll(pageable)`, which came back oldest-first — the admin page fetches one page of 50 out of 228, so a subscription bought today sat at row ~228 and was never displayed. Now `Sort.by(DESC, "createdAt")`. 💡 Noted while investigating: `activatePlan`'s supersede step stamps the replaced row `EXPIRED` + `endDate = today`, destroying the paid-through date; a `SUPERSEDED` enum value (hand-applied ENUM `ALTER`) would keep history truthful — proposed, not done. Also: admin-assigned rows carry `createdBy = NULL`, so there is no audit of which admin changed a member's plan.

### 2026-09-27 — `GET /admin/users` gains `search` and `plan`, applied across ALL members
The admin list filtered client-side over the page it had already fetched, so a member beyond page 1 could not be found by name. `findAllUsersForAdmin` now takes `plan` (exact `subscription_plans.title` of the member's LATEST subscription) and `search` (LIKE over firstName, lastName, `CONCAT(firstName,' ',lastName)`, email, mobile, memberId); blank/absent → no filter. ⚠️ **The `countQuery` repeats the latest-subscription join and every predicate** — a count over a different set than the rows gives the pager a wrong total. Validated on production before building: 755 members, "Krish" → 26 across all pages, mobile/full-name/e-mail fragments resolve. The old 4-arg `getAllUsersForAdmin` overload delegates with nulls. Jar 11:41, clean. **Needs deploy** (also carries the 2026-09-26 daily-match fix if that was not deployed yet).

### 2026-09-26 — 8 AM Daily Match push named the same profiles: both a thin pool AND no memory; a quarter of picks were unapproved
Client: the 8 o'clock cron "sometimes sends the same profile — lack of profiles or a bug?" **Both, measured on production** (`notifications`, category `DAILY_MATCH`, last 14 days — the job itself fires reliably, 23 rows every morning = 27 eligible subscribers minus the 4 whose caste pool is empty):

| receiver | caste pool | sends | distinct bodies |
|---|---|---|---|
| 716, 718 | 3 | 14 | **3** |
| 717, 719, 730 | 12 | 14 | 7 |
| 728, 740–743, 753, 755 | 11–12 | 14 | 8–9 |

- **Pool:** castes 2, 3, 4, 5 have 3–12 opposite-gender candidates; castes 3♀ and 5♂ have **zero**. No code can invent profiles there.
- **Bug:** `DailyMatchScheduler` reshuffled the whole pool from scratch every morning (`Random(epochDay + userId)`) and remembered nothing, so even with 12 candidates the one name in the push repeated on half the days, and the 5 picks overlapped heavily with yesterday's.
- **🔴 Worse, found on the way: 59 of the 243 candidates are `userStatus = PENDING`.** `isActive` defaults to `Y` at registration (`GenericEntity`), the pool filtered on `isActive` only, and the home tab does the same — so a quarter of the profiles being *named in a push* were unvetted. It also never excluded blocked pairs or members with an interest row (connected, pending or declined) — none of which is a "new match".

**Fix.** New `daily_match_log` (`DailyMatchLog`: receiverId, suggestedUserId, sentOn, headline; indexed on receiverId+sentOn; ddl-auto creates it; trimmed to 180 days by the job). The picker now: filters to **APPROVED**, drops block-adjacent ids (`findBlockAdjacentUserIds`, the home tab's own finder) and every interest partner (new `InterestRequestRepository.findPartnerIds`, either direction, any status); then orders **never-suggested first, then least-recently-suggested**, ties broken by the per-member daily shuffle — so nobody repeats until the whole pool has had a turn. The **headline** (the one name the member reads) is rotated separately by least-recently-named, so even a pool of 3 does not read identically on consecutive days. Copy says "new" only when every pick is genuinely new; otherwise "Today's picks… worth another look". `runNow(boolean force)`: the cron skips a member already alerted today (before this, the admin QA button on the same day sent a **second** push), the admin endpoint passes `force=true`.

**Post-fix pool sizes (read-only query):** 716/718 fall from 3 to **1** — two of their three "matches" were PENDING — so those two members will get the same single name daily, now honestly worded. Caste-1 members drop from 55→41–44 and 126→77–81 once connections and blocks are removed. Summary map gains `recycled` and `alreadySentToday`.

⚠️ **Not runtime-tested.** Free RAM on the build machine was 0.0 GB and Docker was off, so the throwaway-DB run used for the admin work was not possible; the change is compiled (Boot 3.2.2 / Hibernate 6.4), JPQL is plain, the `MAX(sentOn)` projection cast is defensive (`asLocalDate`) because a `ClassCastException` there would be swallowed by the per-member catch and silently alert **nobody**. A malformed `@Query` fails at **startup**, loudly — so after deploying, confirm the app booted; the next cron is 8 AM IST, and `POST /admin/runDailyMatchNow` (now `force=true`) exercises it on demand. ⚠️ `LocalDate.now()` is deliberately zone-less (§17 IST double-shift).

`./mvnw -q clean -DskipTests package` clean, `target/app.jar` 22:19, no stubs. **Needs deploy.**

### 2026-09-26 (later) — 🔴 SEND_REQUEST quota is now enforced by the server, not the app
QA found (see app CLAUDE.md same date) that `POST /interestRequest/sendInterestRequest/{sender}/{receiver}` never looked at the member's allowance: the only counting was the APP's own `getRequestQuota` → `updateUsedCount` → send sequence, so a direct call sent unlimited interests with the counter stuck at 25/25 (proved on a Trial account), and a network blip between the app's two calls could charge a request that was never sent — or send one free.
- **`InterestRequestService.createInterestRequest`** now calls `userFeatureUsageService.checkSendRequestQuota(sendId)` AFTER the duplicate-400 guard (a repeat tap is refused without touching the count) and BEFORE any write; exhausted → **`403 REQUEST_LIMIT_EXCEEDED`** with `data {limit, used}` (same shape as `CONTACT_VIEW_LIMIT_EXCEEDED`). The charge (`chargeSendRequest`) runs only AFTER the interest row is saved, best-effort in its own try/catch — a counter hiccup never turns a created interest into an error. All three success paths charge (fresh, resend-after-reject, mutual auto-accept), which is exactly what the app used to charge.
- **`UserFeatureUsageService`** gained `SendRequestQuota` + `checkSendRequestQuota()` (peek — resolves the allowance exactly as `getRequestQuota` does, incl. `ensureActiveSubscription` self-heal) and `chargeSendRequest()`. ⚠️ **`updateUsedCount` is now PEEK-ONLY for the SEND_REQUEST feature** (looked up by code, not the literal id 4): it answers the same 200 / 401 the app branches on but no longer increments. This is what stops every APK in the field — which still calls it right before sending — from double-charging against the server's new charge. Every other feature id through that endpoint behaves exactly as before. No app change is required for correctness; the app only gained a friendlier message for the new 403.
- 💡 A second endpoint, `sendInterestWithLimit` (`InterestRequestController` line ~130 → `validateAndIncrementUsage`), already enforced a limit server-side — but against **`REQ_LIMITED` (feature 5)**, a different counter from the `SEND_REQUEST` (feature 4) the app displays, and it throws `FREE_PLAN_RESTRICTED` for plan 1. The app never calls it. Left untouched; it is a half-built alternative, not the live path.
- **Verify after deploy:** `python3 verify-send-quota.py` (hidden test accounts only, cleans up) — direct send decrements · `updateUsedCount(SEND_REQUEST)` does not · duplicate = 400 without charge · Free member's 4th send = 403. Compile clean; `target/app.jar` rebuilt. **Needs deploy.**

### 2026-09-26 — `getProfileCompletion`: `nextAction` now carries its `key`
`UserDetailService.calculateProfileCompletion` builds `nextActions` as a `LinkedHashMap<String,Object>` keyed `PROFILE_IMAGE` / `HOROSCOPE` / `HEIGHT_WEIGHT` / `HEIGHT` / `WEIGHT` / `WORK_INCOME` / `ADDRESS` / `FAMILY_DETAILS` / `HOBBIES` (or the field name upper-cased from `createDefaultNextAction`), but the response only ever sent `entry.getValue()` — the key was dropped, so the app had to guess the destination from the human-readable `title`. The chosen action is now copied and `key` added alongside `title`/`description`/`boostPercentage`/`route` (additive; old APKs ignore it). The app (`utils/profileSections.ts`, same date) routes on it: section editor at the missing field, or the profile tab's photo/horoscope/interests control. ⚠️ **If a new grouped action is added here, add it to the app's `ACTION_FIELDS` too** — an unknown key falls back to title keywords, which may not match. Compile clean; needs deploy.

### 2026-09-25 (later 3) — End-to-end tested on a throwaway DB, and it found two more defects
Client asked for the admin create/edit flow to be actually exercised, not just compiled. Run against a **disposable MySQL in Docker** with the new jar on port 9111 — no production writes, container destroyed afterwards. 💡 Worth repeating for any admin-API change: `docker run mysql:8.0`, let `ddl-auto` build the schema, seed a caste + subcaste + a Free plan, then POST/GET/PUT. Only env vars and a port are needed.

**🔴 Nine fields silently did not save on CREATE.** `createUserFromAdmin` is a separate method from the update path, and it never set `height`, `about`, `hobbies`, `presentAddress` or `permanentAddress`, while its `basicInfo` and `astroInfo` blocks wrote **hardcoded empty strings** for `marital_status`, `physical_status` and `moon_sign` (and had no `birth_time` at all). A member added from the panel lost all nine until somebody re-saved them from the edit screen. First run: 33/42 correct.

**🔴 The familyInfo destruction bug was only half fixed.** There were **TWO copies** of that logic, create and update, and the first fix landed on the create one — so the update path still wrote a JSON OBJECT over the array. The test proved it: after a PUT the row was `OBJECT` with **4 keys left of 13**, `house`, `brother_sister` and every sibling count gone. Both paths now call one **`writeFamilyInfo(...)`** helper, so a fix cannot land on only one again.

**Final run, all green:** CREATE 42/42 fields · UPDATE 42/42 fields · `familyInfo` stays `ARRAY` with **13/13 keys**, app-only keys (`house`, `brother_sister`) preserved while the admin's edits apply · `no_of_siblings` re-derived (2+1 → 1+3 = 4) · Tamil names round-trip (`முருகன் இராகவன்`) · PIN stored Base64 (`MTIzNA==`) · exactly one Free `user_subscriptions` row · duplicate **mobile** → 409, duplicate **email** with a fresh mobile → 409 · `astroInfo` holds star/dosham/moon_sign/birth_time, `basicInfo` holds marital/physical/place/language.

⚠️ `/BE` is an **nginx** prefix — locally the paths have no prefix. A `curl -o /dev/null` readiness check passes on a 404, so check `%{http_code}` instead; mine "succeeded" against a 404 for several minutes.

Jar rebuilt 14:17, clean, no stubs. **This is the one to deploy** — the 12:33 and 13:28 jars still destroy family data on every admin save.

### 2026-09-25 (later 2) — `subcasteId` was readable but never writable from admin
`updateUserFromAdmin` set `casteId` and ignored `subcasteId` entirely, and `createUserFromAdmin` never set it either — so a member filed under the wrong subcaste could not be corrected from anywhere, by anyone. Both paths now write it. ⚠️ `UserEntity.subcasteId` is a **Long** while the DTO field is an `Integer`, so the assignment converts explicitly rather than relying on autoboxing. The cross-caste guard in the caste service still rejects a subcaste that does not belong to the chosen caste (400). Jar rebuilt 13:28, clean.

### 2026-09-25 (later) — 🔴 Admin save destroyed familyInfo; two JSON key mismatches; 17 fields added to the admin user API
Driven by an admin-panel field audit (see its CLAUDE.md same date). Three defects in `AdminService`, all silent.

**🔴 `updateUserFromAdmin` wrote `familyInfo` as a JSON OBJECT — the column holds a JSON ARRAY.** It read with `mapper.readValue(json, Map.class)`, which throws on `[{...}]` and was caught-and-ignored, then wrote `{"fatherName":…,"father":…}` over the whole thing. Saving any member with a father or mother name therefore **erased family type, family status, both parents' occupations and every sibling count**. Production holds **702 ARRAY rows and 10 OBJECT rows** — the 10 are members already damaged. It now parses either shape, preserves every existing key, writes the array back, and uses the app's snake_case keys (`father`, `mother`, `father_occupation`, `no_of_brother`, …), including the derived `no_of_siblings`.

**🔴 `findUserDetailForAdmin` read two keys that do not exist.** `JSON_EXTRACT(ud.familyInfo,'$.fatherName')` on an array is always NULL, and `basicInfo.$.placeOfBirth` is written as `place_of_birth` by this very service and by the app. Both are now `COALESCE(<real key>, <old key>)` so pre-existing object-shaped rows still read. Verified against production: `$[0].father` → `TestFather` where the old path returned NULL.

**17 fields added** to `AdminUserDetailDTO`, the query and the writer, so the panel can edit everything a member can: `birthTime`, `moonSign` (astroInfo[0]); `physicalStatus`, `maritalStatus` (basicInfo); `currentAddress`/`nativePlace` (the `presentAddress`/`permanentAddress` columns — ⚠ "Native Place" is `permanentAddress`, §17); `familyType`, `familyStatus`, `fatherOccupation`, `motherOccupation`, `noOfBrothers`, `brothersMarried`, `noOfSisters`, `sistersMarried` (familyInfo[0]); `subcasteId`/`subcasteName` (new `LEFT JOIN subcastes`); plus `hobbies`, `about`, `height` from `user_details`.

⚠️ **Every new column is APPENDED to the end of the select.** `getUserDetailForAdmin` maps by POSITION, so a column added in the middle silently shifts every mapping after it. Reads go through a `str(row, i)` helper that tolerates a shorter row.
⚠️ `hobbies` is passed through as the raw JSON array string the app writes (`["food","music"]`) — codes, never labels.

`./mvnw -q clean -DskipTests package` clean, `target/app.jar` 12:33, no "Unresolved compilation" stubs. **Needs deploy.** 💡 The 10 damaged `familyInfo` rows are not repaired by this change — their lost keys are gone; only the shape can be corrected.

### 2026-09-22 — Cashfree pre-launch fix pack: all six reported defects
Six issues raised against the gateway before `PAYMENT_MODE=GATEWAY` goes live. All fixed; backend compiles, app tsc 55.

**1. 🔴 Order reuse handed members a dead session.** `createOrder` reused an open order gated on `expiresAt.isAfter(LocalDateTime.now())` — but `expiresAt` is written as **IST wall-clock** (`OffsetDateTime.now(IST).toLocalDateTime()`) while `LocalDateTime.now()` is the **JVM's** zone. On a UTC server a 30-minute order looked valid for **6 hours**, so a member retrying later got a long-dead session and "This payment has expired". Now gated on the order's real **age** from `createdAt`, which is a `java.util.Date` — an instant — so the arithmetic is identical in every zone and cannot drift again. The 2-minute margin is kept.

**2. 🔴 Wallet success stranded the member.** Every order carried `return_url`, including native-SDK checkouts. The SDK's in-app browser navigates to the website return page, which tries to open `vaibhavvivaaha://` — a scheme that browser cannot handle. Money taken, credit granted, member left on "Webpage not available" → "Exit payment?" → checkout again for an **already-paid order**. `createOrder` now takes a **`channel`** (`SDK` | `WEB`, default `WEB` for back-compat) and omits `return_url` for `SDK`; `utils/cashfreeCheckout.ts` sends `channel: 'SDK'`. The SDK callback already drives the app to PaymentReturn, and fulfilment never depended on the redirect — the webhook does that.

**3. 🔴 Three clocks in one row.** `fulfilledAt` came from JPQL `CURRENT_TIMESTAMP` (the DATABASE clock) while `expiresAt`/`paymentTime` are written from Java in IST and `createdAt` from the JVM — a paid order could read as fulfilled **5h30m before it was created**. `markPaid` now takes `fulfilledAt` as a parameter and `CashfreeService.istNow()` is the single clock for the table. This is the root of issue 1.

**4. Confirm screen polled on after a failure.** `PaymentReturn.tsx` set `'failed'` with **no `return`** — every other branch has one — so it polled the full 40s and each poll made the server call Cashfree: ~29 Get Order calls and 59 log rows for ONE failed payment. `return` added. Server-side, `getOrderStatus` gated only on `orderStatus`, which Cashfree leaves **ACTIVE after a failed attempt**; it now also skips the call once `fulfilledAt` is set or `paymentStatus` is terminal (`isTerminalPaymentStatus`). A genuine retry on the same order still settles by webhook.

**5. Old memberships were never closed.** Every purchase inserted a row and left the previous one ACTIVE (the test account reached **four** live plans). Because entitlements resolve from the newest ACTIVE unexpired row, the day the new plan lapsed the member silently dropped onto an older plan they no longer paid for. `activatePlan` now closes every open row before inserting, wrapped so housekeeping can never block a paid activation. ⚠️ **Uses the existing `EXPIRED`, not a new `SUPERSEDED`** — `SubscriptionStatus` has only PENDING/ACTIVE/EXPIRED/CANCELLED, and adding a value risks the exact 2026-09-19 `Data truncated for column 'status'` incident if the live column is a MySQL ENUM (unverifiable from here, `ddl-auto` does not extend enums). Behaviour is correct either way; a distinct label needs a column check + ALTER first.

**6. Rejected webhooks retried forever.** The skew check ran **before** the signature check and answered 401. Cashfree retries a 401, each retry carries the **original** timestamp, so the skew only grows — the same event rejected in a loop. Order swapped: signature first; a genuine-but-late delivery is recorded and **acknowledged with 200** without being acted on (acting on it is unsafe — it may be a replay); only a real signature failure still returns 401.

⚠️ Needs the backend deploy + the next APK. Still unapplied from 2026-09-21: the presence reaper, and both index migrations.

### 2026-09-22 — Payment RECEIPT PDF e-mailed after every successful payment (new `payment_invoices` table)
Client: send an invoice by e-mail when a member buys a plan or a boost. **Built as a "Payment Receipt", deliberately not a "Tax Invoice"**: the business has no GSTIN, and an unregistered supplier may not issue a tax invoice, charge/show GST or print HSN/SAC lines. The PDF says so in words ("not registered under GST; no GST has been charged"). When a GSTIN exists, set `invoice.business.gstin` and the header prints it — tax lines would be a separate change.
- **Trigger = `CashfreeService.fulfil()`, after `activate()`** — the ONE place a payment is confirmed for all three paths (webhook / status-poll VERIFY / nightly reconcile). `markPaid` wins once per order and `payment_invoices.paymentOrderId` is UNIQUE, so duplicate webhooks (Cashfree sends ~3 per payment) can never produce two receipts. Best-effort in its own try/catch — a mail failure never turns a paid order into an error.
- **New `InvoiceService`** (OpenPDF 2.0.3, pure Java): receipt no `VVM-RCPT-000012` derived from the row id; business block from `invoice.business.*` properties (**⚠️ `invoice.business.address` is EMPTY — fill the postal address before go-live**); member name / member id / e-mail / mobile; description (`Platinum Membership` with validity "22 Sep 2026 to 21 Mar 2027" or "…until marriage"; `Profile Boost credit (1)`); amount in Indian grouping + words (lakh/crore); payment mode from `paymentGroup`; Cashfree payment id as reference; terms (digital service, non-transferable, refunds per website policy, Cashfree processes payments, computer-generated). One A4 page, brand maroon/gold. The PDF is NOT stored — rebuilt from the row on every send, so a resend reproduces the same document. "Rs." not "₹": the base PDF fonts have no rupee glyph.
- **`EmailService.sendHtmlWithPdf(...)`** — ZeptoMail plain-send with an inline base64 attachment, one retry, returns accepted/not; the row records `emailStatus` SENT / FAILED / NO_EMAIL, `emailedTo`, `emailedAt`, `sendAttempts`.
- **Endpoints:** `POST /payments/cashfree/admin/orders/{orderId}/receipt/resend` (support: "member says no mail") and `GET /payments/cashfree/order/{orderId}/receipt.pdf?requesterId=<base64 id>` (member download, owner-checked). Nothing in the app calls the download yet.
- ddl-auto creates `payment_invoices` on deploy. Members who paid BEFORE this deploy have no receipt row; the resend endpoint issues one on demand (it accepts any PAID order).
`./mvnw -q clean -DskipTests package` clean, no compilation stubs. **Needs deploy.** Verified offline by rendering a sample PDF from the real class (1 page, 2.9 KB); a live send was not exercised — after deploy, `curl -X POST …/admin/orders/<a paid orderId>/receipt/resend` and check the inbox + `payment_invoices.emailStatus`.

**Same day, first live send — two things it exposed.** (a) The first resend went to the member's OLD e-mail: user 926's address had been changed via `PUT /admin/users/926`, verified in the DB, and then **reverted within a minute while the APP WAS RUNNING logged in as that member**. With the app force-stopped the same change held for 60s+. ⚠️ Something the app calls re-saves the user entity carrying a stale e-mail — worth finding, because an admin e-mail correction silently undoes itself while the member has the app open. (b) 🔴 **`LocalDateTime.now(IST)` DOUBLE-SHIFTS on this server**: the JVM default zone is UTC while the machine's clock already holds IST wall time, so a receipt issued 22 Sep 21:53 was stamped **23 Sep 03:23** (`createdAt` from `new Date()` said 21:53 on the same row; MySQL `NOW()` said 16:27, i.e. true UTC). `issuedAt`/`emailedAt` now use plain `LocalDateTime.now()`, matching `GenericEntity.createdAt`. ⚠️ The receipt's "Payment Date" prints `payment_orders.paymentTime`, which CashfreeService writes with the same `atZoneSameInstant(IST)` double shift — it stays wrong until the payment_orders timezone bug is fixed. **Never use `now(IST)` in this codebase; use `LocalDateTime.now()`.** Jar rebuilt 21:59 — needs redeploy.


### 2026-09-21 (later) — 🔴 Like notifications were silent for free members AND gated on the wrong column; profile tables had no indexes at all
**A. "I liked Arun Kumar from Bala Kumar V and no notification arrived."** Two independent faults in `UserLikesService`, either one enough to silence it:
- The whole notify block sat behind a plan gate, so a **Free recipient got nothing** — no push and no in-app row either.
- 🔴 The gate read **`userSubscriptions.get(0).getId()`** — the `user_subscriptions` ROW's primary key — and compared it to `1L`, meaning to test "is Free". It never looked at `getSubscriptionPlanId()`. So whether ANY member got a like notification depended on an arbitrary row id. Same class as the `img.id` vs `galleryId` bug: a real field, wrong one.

Per the client's rule — **everyone is notified; the NAME is what the plan buys** — the gate is gone. Paid recipients see "Bala Kumar V liked your profile", free recipients "Someone liked your profile! ...", via the existing `nameForRecipient` (fails CLOSED: a lookup error yields the anonymous form). Push switched from `sendPushNotificationToUser` to **`...Direct`** — the gated variant skips Free/Starter and returns SUCCESS while sending nothing, which is the other half of why it was silent.

⚠️ **Masking the push text alone would have leaked it anyway.** `NotificationService.getAllNotificationsForUser` ships `userName`, `avatar`, `userAge` and `userLocation` as SEPARATE fields for every row, so a free member would have read "Someone liked your profile" with the liker's name and photo rendered right beside it. Now masked there too — `userName` → "Someone", `avatar` → null — for **discovery** categories only (`LIKE`, `PROFILE_VIEW`, `SHORTLIST`, `DAILY_MATCH`), resolved with ONE plan lookup per page, not per row. 💡 Essential categories (MESSAGE, INTEREST, INTEREST_ACCEPTED, CALL_REQUEST, permission decisions) keep the real name on every plan — a member must know who is actually contacting them. Verified no bean cycle: `PushNotificationService` does not inject `NotificationService`.

⚠️ Deliberately NOT changed: `PROFILE_VIEW` / `SHORTLIST` / `DAILY_MATCH` are still paid-only at the SENDING end (the client asked about likes). If they should also reach free members as "Someone", that is a one-line switch to `...Direct` in each — the masking half is already done.

**B. `migration-2026-09-21-profile-indexes.sql` — the profile page had NO indexes.** The 2026-08-10 batch covered users/interestRequest/shortlisted/viewedProfile/conversations/chats and **none** of the tables a profile read touches. Worst by far: 🔴 **`user_details.userId` is not the primary key** — the entity has its own `@GeneratedValue id` — so every `findByUserId(...)` is a FULL TABLE SCAN, and that call is on every profile read, every save and every search assembly. `UserService.getInterestMatches` (Home's Interest-Based Matches shelf) calls it **inside a loop over every candidate**: ~600 profiles ⇒ 600 scans of a 600-row table ≈ **360,000 row reads for one shelf**. Added: `user_details(userId)` **UNIQUE** (one detail row per member is the real rule; if it errors 1062 there are duplicates to clean, do NOT downgrade it to a plain index), `gallery(userId,isActive)`, `userLikes(likedBy,likedTo)` + `(likedTo)`, `restricted_field_requests(requestedBy,requestedTo,fieldType)` + `(requestedTo,status)`, `user_feature_usage(userId,featureId)`.

💡 **An index does not fix the N+1** — 600 scans become 600 seeks, still 600 round trips. The real fix is `findByUserIdIn(Collection<Long>)` + a Map lookup in the loop. Flagged in the migration; not done here so the file stays a pure reversible schema change.

`./mvnw compile` clean. ⚠️ **Neither migration is applied** — 3306 is filtered from outside the app host, so both must be run on the server.

### 2026-09-21 — 🔴 A force-killed app stayed "Online" to its chat partner forever
Client: person A closes the app; person B, sitting in A's chat, keeps seeing Online. Reproduced by reading the presence path, and the cause is a gap no amount of client work can close.

**Every piece of the disconnect path was already correct** — `afterConnectionClosed` removes the session, calls `updateLastSeen(userId, false)` and `broadcastPresence(userId, false)` to every conversation partner. **It simply never fires on a force-kill.** Swiping an app away does not close its TCP socket gracefully: no FIN reaches the server, so Spring never raises the close event, the entry stays in `userSessions`, and `isUserOnline()` goes on answering true until the OS TCP timeout — potentially hours.

⚠️ **The client heartbeat cannot fix this, and it is worth understanding why.** `webSocketService` pings every 25s and force-reconnects on a missing pong — but that only lets the **CLIENT notice a dead SERVER**. Nothing was watching the other direction. The server answered every ping with a pong and **never recorded that a ping had arrived**, so it had no way to tell a live client from a dead socket.

Fixed in `UserWebSocketHandler` by having the server time clients out itself:
- `touch(session)` stamps `lastHeardFromMs` into the session attributes on connect and on **every** inbound frame (the 25s ping is what normally keeps a session fresh).
- New `@Scheduled(fixedDelay = 30s) reapStaleSessions()` closes any session silent for more than **`STALE_AFTER_MS` = 75s** (three missed pings). Worst-case detection is ~105s.
- 💡 **The reaper only calls `session.close()`** — that fires the existing `afterConnectionClosed`, which already does the removal, the DB write and the broadcast. One cleanup path, not two that drift apart. The only place it cleans up directly is a session found already `!isOpen()` but still mapped, where the close event can no longer fire.
- ⚠️ 75s is deliberate, not arbitrary: anything under ~3 missed pings flaps a member offline on a tunnel or a network switch, which the client's own reconnect already handles. Tune `STALE_AFTER_MS` if presence needs to be tighter, but keep it above 2× the client's `PING_EVERY_MS`.

**Checked and NOT a second bug:** the app calls `userApi.lastSeen()` when it backgrounds, and `UserConnectionService.updateLastSeen` used to force `setOnline(true)` there — already corrected earlier; it now only stamps the timestamp. Backgrounding does not mark you online.

`./mvnw compile` clean. **Needs the backend deploy** — this is entirely server-side, no app change and no APK.

### 2026-09-21 — Chat indexes written for slow conversation loading (NOT YET APPLIED)
Client: "loading conversation is taking too much time." `chats` has exactly ONE index — `idx_chats_conv (conversationId, isActive, isRead)` — and **every** chat query sorts or ranges on a column it does not contain, so the index only answers "which rows belong to this conversation" and the expensive part is still a full read + sort of the conversation. New `migration-2026-09-21-chat-indexes.sql` adds four, each matched to a real query shape:
- **`(conversationId, createdAt)`** — the big one. `getUserChatList` runs **four** correlated subqueries PER CONVERSATION, all `WHERE conversationId = c.id ORDER BY createdAt DESC LIMIT 1` (last message, its time, its read flag, its sender). With no `createdAt` in any index each one reads every message in that conversation and sorts it: 20 conversations × 100 messages ≈ 8,000 row reads and 80 sorts to draw one list. Becomes a backward seek — read one row, stop.
- **`(conversationId, isActive, Id)`** — `findVisibleAfter`, which its own javadoc calls "the app's single way of getting messages" (on open, every socket event, reconnect, foreground), so it is the hottest query in the app. ⚠️ The existing index is effectively `(conversationId, isActive, isRead, Id)` because InnoDB appends the PK — and **`isRead` sitting between the equality columns and `Id` is what stops `id > ?` being a range scan**. Moving `Id` up makes it a true range, already in ORDER BY order, so the filesort goes too.
- **`(conversationId, isRead, senderId)`** — the unread badges, which run for every conversation on the list screen.
- **`(conversationId, senderId, isRead)`** — tick high-water marks; lowest value of the four, marked skippable.

⚠️ **Every ALTER carries `ALGORITHM=INPLACE, LOCK=NONE` deliberately** — `chats` is the highest-write table and a plain `ADD INDEX` that falls back to a table copy would stall every member's chat. With these clauses MySQL **errors instead of locking**; do not strip them to "make it work". 💡 The PK column is literally **`Id`** (`@Column(name="Id")` on ChatEntity) — MySQL is case-insensitive for column names so `id` also works, but the migration matches the schema. `isActive` is correct and NOT `is_active`: `spring.jpa.hibernate.naming.physical-strategy=PhysicalNamingStrategyStandardImpl` keeps field names verbatim (confirmed by the already-applied `idx_chats_conv`).

**NOT APPLIED — no access from here.** Port 3306 is filtered on both the app host and the documented IP (correct for a production DB), so this must be run ON the server. The file is self-contained: step 0 sizes the table (if it is small, indexes are NOT the bottleneck — suspect the 10s axios timeout or the synchronous email sends that pin the Hikari pool, 2026-09-04), step 1 captures EXPLAIN before, step 2 adds the indexes, step 3 re-runs EXPLAIN to prove `Using filesort` is gone. Rollback lines included; indexes drop instantly. The old `idx_chats_conv` is left in place on purpose — it is now largely redundant but dropping an index deserves its own measurement, not a bundle.

### 2026-09-21 (evening) — Cashfree methods widened to `upi,dc,cc,nb,app`
`cashfree.payment.methods` default changed from `upi` (client ask): UPI, debit + credit cards, net banking, wallets. Excluded on purpose: pay-later, all EMI codes, bank transfer, PayPal, and corporate/prepaid cards (`ccc`,`ppc`, highest fees). Env `CASHFREE_PAYMENT_METHODS` + restart overrides it with no rebuild. Outcome handling needed no change — nothing in `CashfreeService` branches on the method. ⚠️ Refund / dispute / chargeback webhooks are still acknowledged and ignored; with cards enabled that gap is now reachable. Jar rebuilt clean 17:35, 0 stub classes.

### 2026-09-21 — UPI-only orders; expiry now reconciles with Cashfree first
- **`order_meta.payment_methods`** is sent on every order from `cashfree.payment.methods` (`CASHFREE_PAYMENT_METHODS`, default `upi`; blank = whatever the dashboard allows). On the ORDER, not in the client SDK, so every surface inherits it and a modified client cannot widen it. Restart to change, no rebuild.
- **🔴 `expirePaymentOrders` (00:15) used to expire purely on the clock** — a paid order whose webhook was missed, from a member who had closed the app, was stamped EXPIRED and then looked exactly like an abandoned checkout. It now runs `getOrderStatus` (re-checks Cashfree, fulfils a PAID order) for each `findStaleActive` row first, each isolated in its own try/catch, and only then calls `expireStale`. `CashfreeService` is injected `@Lazy` to avoid a service-layer cycle; boots clean.
- Matrimony still has NO periodic reconciler (the saree-shop backend runs one every 10 min) — the nightly pass is the backstop, the webhook and the app's poll are the fast paths. The app now uses Cashfree's native SDK with the browser flow as fallback; **nothing on the server changed for that**. Jar rebuilt clean 2026-09-21 12:27.

### 2026-09-20 — Porutham reference tables verified correct; the July 2026 accuracy audit is closed
Asked whether peyar porutham (name matching) could be added, which prompted a check of the tables already in use. **All six items the July 2026 audit flagged are fixed and now match the classical sources** — verified cell by cell, not from the earlier notes:
- **Nadi** — `nadiCycle {1,2,3,3,2,1}` over `(i-1) % 6` reproduces the classical Aadi/Madhya/Antya split for **all 27 stars** (checked against the canonical sets programmatically: zero mismatches). This was the worst of them — it carries the largest single weight and used to assign 21 of 27 stars through a lazy loop.
- **Yoni** — the correct **14** animals, correctly assigned across all 27 stars. The three that do not exist in the system ("Crow", "Eagle", "Pig") are gone.
- **Gana** — matches the standard Deva / Manushya / Rakshasa grouping.
- **Vasya** — a real five-group implementation (Chatushpada, Manava, Jalachara, Vanachara, Keeta) with the correct cross-group pairs; no longer the two-hardcoded-pair stub.
- **Rasi Adhipathi** — matches the standard Naisargika Maitri friend/enemy table exactly (the Mercury/Moon, Mars/Venus and Jupiter/Saturn inversions were corrected 2026-07-17 per the comment in the method).
- **Stree Dirga** — traditional threshold `diff >= 13`, not the old 7.

⚠️ **R7 in the app's `BRD.md` said four of these were unverified — that entry was stale and is now marked Resolved.** 💡 If a twelfth factor is ever added, the lesson from the original audit stands: a wrong table does not look broken, it produces a plausible percentage and a confident verdict, so nothing catches it. Any new factor needs a reference the client names AND a set of known-correct pairs to test against.

**Peyar porutham was NOT built.** It comes in two unrelated forms: the name-star (namakshatra) method derives a star from the name's first syllable and then reuses this same engine — a fallback only useful when the birth star is unknown, and a live sample showed 13 of 15 profiles already carry one; the numerology method is a separate calculation whose letter-value system practitioners do not agree on, and which is sensitive to English spelling of Tamil names. Needs the client to pick a method and supply a reference before it can be written responsibly.

### 2026-09-20 (later) — 🔴 Presence was write-only: `PUT /user/lastseen` forced online=true
The endpoint itself is fine (200 in ~0.4s, 15/15; it 500s only on a non-base64 id). The bug was its semantics: `UserConnectionService.updateLastSeen` set `setOnline(true)`, but the app calls it when the member LEAVES (background, logout, socket close). **20 production members were shown "Online" with real last-seen values days to weeks old.** Now it writes only the timestamp — **online/offline belongs to `UserWebSocketHandler` alone** (connect → `updateLastSeen(id,true)`, close → `false`), which was already right. ⚠️ Never re-add an online write to a REST endpoint that cannot tell arrival from departure.
`getUserOnlineStatus` now answers from the **live session map** (`UserWebSocketHandler.isUserOnline`) and heals a disagreeing row: a restart force-closes sockets during context shutdown, so `afterConnectionClosed` cannot persist offline (it logs "Skipped … during shutdown") and the flag would stick forever otherwise.
⚠️ **`broadcastPresence` now sends `Instant.now()`, not `LocalDateTime.now()`.** Both were unmarked, but REST returns UTC while LocalDateTime is server-local — two values 5h30m apart down one field, so no client parser could be right for both. Instant ends in `Z`. Production data cleared (backup `_bak_users_isonline_20260920`); **re-run `UPDATE users SET isOnline=0 WHERE isOnline=1;` after the deploy**, since the live jar keeps re-flagging until then. Verified offline→online→offline on the new jar. Jar rebuilt clean 12:01 — **supersedes the 11:27 jar; includes plan_changed.**

### 2026-09-20 — `plan_changed` socket signal from every path that changes a plan (cross-repo)
The app only learned its plan at Home-load, so an admin assign / approved payment / gateway payment needed an app restart to take effect (app CLAUDE.md same date). New **`SubscriptionActivationService.notifyPlanChanged(userId, planTitle)`** pushes `{"type":"plan_changed","data":{userId, planTitle}}` over the member's socket. It is a SIGNAL, not data — the app answers by re-reading `/userSubscriptions/getActiveUserSubscriptionByUserId`, which stays the only source of truth, so a lost event is harmless (the app also refreshes on foreground, reconnect, login, any push and any 403).
Called from: `activatePlan` (covers admin `approvePayment`, admin `assignPlanToUser`, Cashfree webhook — right after the save, BEFORE the slow push/email), the nightly `expireSubscriptions` (→ "Free", so a downgrade is live too) and `UserSubscriptionsService`'s direct-create.
⚠️ **Commit-aware, and it has to be.** `approvePayment` and the expiry job are `@Transactional`, and `activatePlan` spends seconds on push + email after its save. Sent inline, the signal reaches the app long before the row commits, the app re-reads the OLD plan, and nothing prompts it to look again. Inside a transaction the send is registered for `afterCommit`; outside one it goes immediately. **Any new plan-changing path must call this helper, not `sendMessageToUser` directly.**
Verified on the built jar against production: `plan_changed` arrived 1.2s after an admin assign (the HTTP call itself returned at 3.4s) and an immediate re-read showed the new row. Best-effort by contract — never throws into, or rolls back, the write it follows. Jar `target/app.jar` built clean 2026-09-20 11:27 (0 stub classes) — **supersedes the 10:19 jar; includes birth time.**

### 2026-09-20 — Birth time (astro): stored, counted, revealed with the horoscope, hideable
New member field, no schema change — it lives in **`user_details.astroInfo[0].birth_time`**, beside `star`/`moon_sign`/`dosham`, because it is astro data read together with the horoscope. Free text ("10:45 PM"); no format imposed, since members write it many ways and a time picker would force a precision they may not have.
- **`AstrologyInfoRequest.birthTime`** → `UserDetailService.updateAstrologyInfo` writes it **only when the field is non-null**, so an older app build (which omits it) cannot blank a saved value. ⚠️ Verified: a legacy-shaped save left `"10:45 PM"` intact.
- **Profile completion** counts it (`requiredFields` + `checkField(firstAstro,"birth_time","birthTime",…)`) — total 35 → **36**.
- **`revealContact` returns `birthTime`** inside the existing `horoscopeEligible` branch: one tap already reveals contact + horoscope, and birth time is the same astro data under the same consent rule (plan has HOROSCOPE_VIEW **and** the interest is APPROVED).
- **Masked otherwise.** `getProfileDetailWithIntractionStatus`'s horoscope branch now also calls new `stripBirthTime(ud)` (blanks `birth_time` in the JSON on the detached entity, tolerating both the array and object shapes the two signup paths write). New `readBirthTime(detail)` is the single reader. Verified live: Gold viewer with an APPROVED interest → `10:45 PM`; Free viewer with no interest → `''`.
- **Privacy:** `fieldType` is a free String, so `BIRTH_TIME` needs no enum change — only `RestrictedFieldRequestService.fieldLabel()` gained `case "BIRTH_TIME" → "birth time"` so notifications read properly. `hidden_fields.fieldName = 'birthTime'` is written by the app's privacy screen.
Clean build, 0 stub classes, boots in ~13s.

### 2026-09-19 (later 2) — Incremental chat sync: `GET /chat/conversation/{id}?viewerId=&after=`
The app's chat screen now has ONE way of getting messages (see its CLAUDE.md): REST is the source of truth, the WebSocket only says "something changed". This endpoint is that one way.
- **`after` present (even `0`) → OBJECT response** `{ messages, readUpToId, deliveredUpToId, status }` via new `ChatService.syncConversation`. `messages` = rows with `id > after` visible to the viewer (`ChatRepository.findVisibleAfter`, ordered by id — the cursor), so a missed live push can never lose a message: the next sync returns it.
- **`readUpToId` / `deliveredUpToId`** = highest id of the VIEWER's OWN messages that is read / delivered (`maxReadId`, `maxDeliveredId`). Reads and deliveries are applied in bulk, so "everything of mine up to N" is exact — and it is what lets a rows-after-X fetch still update ticks on OLDER messages, which it would otherwise never return.
- **`status`** rides along so the app learns DISCONNECTED without a second call.
- **`after` ABSENT → the legacy full ARRAY, unchanged** — app builds already in the field keep working. A missing `viewerId` is treated as `-1` on the sync path (hidden rows dropped for anonymous callers).
Verified on a locally booted clean jar against conversation 105: legacy array 13 rows · `after=0` 13 rows · `after=67` exactly the 3 newer · `after=103` zero rows with tick marks still present · each member gets their OWN marks (926 → 66, 925 → 67). Jar rebuilt 21:43.

### 2026-09-19 (later) — 🔴 Live chat delivery: reconnecting phones were flagged OFFLINE; heartbeat; delivered ticks; presence push; block answers
Client: "both online, I'm inside the chat, the message doesn't appear — but the notification does." Two server-side causes (the app had a third, see its CLAUDE.md):
- **🔴 `UserWebSocketHandler.afterConnectionClosed` did `userSessions.remove(userId)` — by KEY.** Sessions are one-per-user; a phone that reconnects (app resumed, network switch) opens its NEW socket before the old one finishes closing, so the old socket's late close event deleted the NEW session's entry and ran `updateLastSeen(userId,false)`. The member sat in an open chat flagged offline, and `sendMessageToUser` had nowhere to push — push notifications still arrived via Expo, which is exactly the symptom. Now `remove(userId, session)` (only the mapped session removes itself; a stale close is ignored and does NOT mark offline), and a replaced session is closed explicitly on connect. **Verified with a real socket pair** on a locally booted jar: second connection → first closed 1000 → log `disconnected (current=false, total: 1)` → second still served.
- **No heartbeat** → a half-open socket looked OPEN forever. New `handleTextMessage`: `{"type":"ping"}` → `{"type":"pong"}` (the app pings every 25s and re-dials after 60s of silence). All sends are now `synchronized(session)` — `WebSocketSession` is not safe for concurrent sends and four kinds of push can now target one member at once.
- **WhatsApp ticks** — new nullable `chats.isDelivered` (ddl-auto adds it; verified created). Set at send time when the recipient's socket is live (`/chat/send` now returns `data:{id, delivered}`), otherwise by new `ChatService.onUserConnected(userId)` the moment they connect (`findUndeliveredForReceiver` → `markDeliveredForReceiver`, then a `message_delivered` WS event to each online sender). `updateMessagesAsRead` sets `isDelivered=1` too. Legacy rows are NULL — the app treats read as implying delivered, so no backfill.
- **Presence push** — `ChatService.broadcastPresence(userId, online)` on connect/disconnect sends `{"type":"presence","data":{userId,isOnline,lastSeen}}` to every online conversation partner (`ConversationRepository.findPartnerIds`). The app keeps its 15s poll only as a fallback.
- **Block answers (client decision — reverses the silent block of 09-17):** `/chat/send` refuses BOTH directions and stores nothing: sender is the blocker → **`USER_BLOCKED`**, sender is the blocked member → **`BLOCKED_BY_USER`**. `chats.hidden_from_receiver` is no longer written; the column and its read-side filters stay so rows suppressed between 09-17 and now remain hidden. ⚠️ A test APK built 09-17..09-19 has no handler for `BLOCKED_BY_USER` and shows its generic upgrade prompt; the Play Store build (09-09) blocks client-side and never reaches this path.
Clean build, 0 compiler-stub classes, boots in 14s. Jar rebuilt 21:13.

### 2026-09-19 — 🔴 Remove-connection threw `Data truncated for column 'status'`: the new DISCONNECTED value was never added to the live ENUM
Client: unfriending showed "Something went wrong" with `could not execute statement [Data truncated for column 'status' at row 1] update conversations set …` — **yet the unfriend partly happened**. Cause: `conversations.status` is a MySQL `enum('PENDING','ACCEPTED','BLOCKED')` and `ddl-auto=update` never extends an existing enum (see the new §17 landmine), so the `DISCONNECTED` value added on 2026-09-17 could not be written. `disconnectPair` deletes BOTH interest rows first and the method is not transactional, so the deletes committed and only the status update failed — leaving the pair unfriended with the conversation still `ACCEPTED`, i.e. **the chat stayed open and writable**, which is exactly the state the feature exists to prevent.
Fixed on production (no code change, no redeploy): backup `_bak_conversations_status_20260919` (15 rows) → `ALTER TABLE conversations MODIFY status enum('PENDING','ACCEPTED','BLOCKED','DISCONNECTED') NOT NULL;` → repaired the 2 conversations left stuck (105, 107 — both had real messages, so marked `DISCONNECTED` with history kept, never deleted). Verified live afterwards: `POST /chat/send` to 105 → **403 `NOT_CONNECTED`**, both threads still listed for both members, all 12 messages still readable. ⚠️ Staging/any new environment needs the same ALTER.

### 2026-09-17 (evening) — 🔴 Production 502: a jar packaged from VS Code's error-stub class files. ALWAYS `clean` before `package`.
The 17:52 jar crashed on boot with `NoClassDefFoundError: ActiveStatus` (Spring Data reading `ChatRepository`'s method signatures). The SOURCE was fine — `javap` on the jar's `ChatRepository.class` showed an Eclipse-compiler stub: *"Unresolved compilation problems: The import com.uravugal.matrimony.enums.ActiveStatus cannot be resolved"* with the parameter type emitted as a default-package `ActiveStatus`. **VS Code's Java extension (JDT) writes its own compiled classes into `target/classes`** and had done so at 17:51 with a stale index; `./mvnw package` without `clean` then saw those class files as newer than the sources, skipped compiling, and shipped them. Diagnosed by booting the jar locally against the production DB (`SPRING_DATASOURCE_*` env + `--server.port=9199`) — the only way to see the stack trace without server access.
**Rule: build with `./mvnw -q clean -DskipTests package`**, and before deploying scan `grep -rla "Unresolved compilation" BOOT-INF/classes` on an unzipped copy — 0 hits or don't deploy. Rebuilt clean at 18:04, verified it boots (15 s) and the new endpoints answer.

### 2026-09-17 — VOICE_CALL service request now notifies the target member
`ServiceRequestService.createRequest` saved the row and notified nobody — only the admin queue saw call requests, so the member being called never knew. New `notifyCallTarget(requesterId, targetUserId)` runs after the save for `VOICE_CALL` + non-null `targetUserId`: `sendPushNotificationToUserDirect` (essential — not the plan-gated variant) with the name via `nameForRecipient`, plus a `Notification` row with category **`CALL_REQUEST`** (new value; the app maps it to a phone icon). Own try/catch so a notification failure cannot fail an already-saved request. Two new `@Autowired` beans on the service (`PushNotificationService`, `NotificationRepository`). Compile clean. Needs deploy.

### 2026-09-17 (later) — Interest/chat: mutual sync, disconnect, silent block, free-member masking
- **`InterestRequestService.updateInterestRequestStatus` APPROVED → the reverse-direction row (if any, any status) is set APPROVED too.** `createInterestRequest`: if the reverse row is PENDING the new row is saved APPROVED and the reverse is approved through the same method (conversation ACCEPTED + "Interest request approved" chat line + Interest Accepted push to the first asker); no "received an interest" push in that case. Response 201, `data.acceptStatus = APPROVED`.
- **New `disconnectPair(a, b)`** — deletes BOTH interest rows; conversation deleted when only system lines exist, otherwise marked **`ChatStatus.DISCONNECTED`** (new enum value; `@Enumerated(STRING)`, safe to add). Used by `deleteInterestRequest(id)` (⚠️ behaviour change: the reverse row is removed too) and by new **`DELETE /mailbox/removeConnection/{encodedUserId}/{otherUserId}`** (404 when no row in either direction). `createInterestRequest` already flips an existing conversation back to PENDING, approval to ACCEPTED, so a disconnected pair can reconnect.
- **`ChatService.sendChatMessage`**: (a) connection gate — status must be ACCEPTED, else 403 `NOT_CONNECTED` (DISCONNECTED) / `REQUEST_PENDING`; (b) block gate split: sender blocked the other → 403 `USER_BLOCKED` as before; **other blocked the sender → message saved with new `chats.hidden_from_receiver = 1`, 200 returned, NO notification/push/WebSocket, and only the sender's own deleted-flag is cleared** (a suppressed message must not resurface the thread for the blocker). `ChatRepository`: unread counts, `updateMessagesAsRead` (so the tick never turns blue) and new `findVisibleByConversationForViewer` all exclude hidden rows; `GET /chat/conversation/{id}?viewerId=` uses it (no param = legacy full list). `ConversationRepository.getUserChatList`: the three last-message subqueries exclude rows hidden from `:userId`, plus a `lastMessageSenderId` column APPENDED LAST (row[12]).
- **`ChatService.canReadMessages(userId)`** (ACTIVE sub, plan ≠ 1, MESSAGE planFeature present; fails closed). `ConversationService` masks `lastMessage` to "🔒 New message — upgrade to read" for such viewers unless it is a system line or their own; the MESSAGE `Notification` row omits the text for them.
Compile clean; jar rebuilt. ddl-auto adds the column on deploy.

### 2026-09-17 — Login 403 (PENDING/REJECTED) payload now includes `lastName`
The app's ProfileUnderVerificationScreen shows the member's full name. One-line addition to the `pendingData` map in `UserService` login. (Also verified live: mobile+PIN login works server-side — the reported failure was an app-side 9-digit registration, fixed in the app.)

### 2026-09-15 — Cashfree Payment Gateway: redirect checkout + webhooks (cross-repo); Razorpay stub removed
**Why:** client asked to integrate Cashfree's web redirect checkout and payment webhooks (docs read: `payments/online/web/redirect`, `api-reference/…/webhooks#version-2026-01-01`, `webhooks/overview`). Full design in `CASHFREE_INTEGRATION_PLAN.md` (repo root) — read that first.

**Webhook URL to register in Cashfree (Developers → Webhooks, version 2026-01-01, Payment group, sandbox AND production separately):** `https://vaibhavvivaahamatrimony.com/BE/payments/cashfree/webhook`. The endpoint must be live (deployed) before saving — Cashfree pings it on save.

**Backend**
- New `models/PaymentOrder` (`payment_orders`), `models/PaymentWebhookEvent` (`payment_webhook_events`), repositories, `services/CashfreeService`, `controllers/CashfreeController` (`/payments/cashfree/**`, see §8.4). Amount is ALWAYS `subscription_plans.price` (or `payments.boost.price` for BOOST_PURCHASE) — the client sends only `planId`/`purpose`. Free plan / retired plan / unapproved profile → 400/403.
- **Fulfilment is idempotent by construction**: `PaymentOrderRepository.markPaid` is a single conditional UPDATE (`… WHERE fulfilledAt IS NULL`); whoever gets `1` activates. Duplicate webhooks (Cashfree is at-least-once, retries 2/10/30 min) and the webhook-vs-return-URL race are both handled by that one statement. Events are also stored by `x-idempotency-key`; a processed key short-circuits to 200.
- **Webhook**: raw bytes, `Base64(HMAC-SHA256(timestamp + rawBody, clientSecret))` compared with `MessageDigest.isEqual`, ±10 min timestamp skew guard, then `PAYMENT_SUCCESS_WEBHOOK` (amount + currency must equal the stored order, else `AMOUNT_MISMATCH` flagged and NOT fulfilled), `PAYMENT_FAILED_WEBHOOK`/`PAYMENT_USER_DROPPED_WEBHOOK` (recorded; order stays ACTIVE so the member can retry within the 30-min expiry), `PAYMENT_CHARGES_WEBHOOK` (settlement figures). Processing exception → 500 so Cashfree retries; the event row is reused on retry. Rejected signatures are stored with `signatureValid=N`.
- **`SubscriptionActivationService` extracted** from the two duplicated blocks in `AdminService` (`approvePayment`, `assignPlanToUser`) — both now delegate; behaviour change: approvePayment now ALSO sends the plan email (it previously didn't) and the push title is the shared "Membership Activated! 🎉".
- `SubscriptionSchedulerService.expirePaymentOrders` (00:15 daily) flips stale ACTIVE orders to EXPIRED locally.
- **Removed** `PaymentController`/`PaymentService` (Razorpay stub, trusted the amount from the URL, never called) + `razorpay-java` + the committed test keys. ⚠ `commons-lang3` was only on the classpath transitively through that SDK (`UserDetailService` imports it) — now declared explicitly in `pom.xml`.
- Config: `cashfree.*` block in `application.properties`, secrets from env (`CASHFREE_CLIENT_ID`, `CASHFREE_CLIENT_SECRET`, `CASHFREE_ENABLED`, `CASHFREE_ENV`, `CASHFREE_BASE_URL`). `cashfree.enabled=false` by default → createOrder returns 503 `PAYMENT_GATEWAY_DISABLED` and the clients fall back to the existing contact flow.

**Website** (`VaibhavVivaahaWeb`): `/pay/:orderId` (loads Cashfree JS SDK v3, shows the REAL plan/amount from the backend, "Pay securely" → `checkout({paymentSessionId, redirectTarget:'_self'})`) and `/payment/return` (polls status ~40 s, deep-links `vaibhavvivaaha://screens/PaymentReturn?order_id=…` on mobile). `PackagesSection` sends logged-in members through createOrder → `/pay`; falls back to the old "call our team" prompt otherwise. ⚠ The section's hardcoded prices (Silver ₹1,499 …) don't match the DB (₹2,499 …) — `/pay` shows the DB amount before charging, but that copy should be fixed.

**App** (`VaibhavVivaahaApp`): `PAYMENT_MODE` gains `'GATEWAY'`; `components/GatewayCheckoutView.tsx` (createOrder → `expo-web-browser.openAuthSessionAsync(website /pay/:orderId, 'vaibhavvivaaha://screens/PaymentReturn')` — system browser, NOT a WebView, so UPI intent apps work) rendered by `PaymentScreen` + `AddOnPaymentScreen`; new `screens/PaymentReturn.tsx` polls status. Dead `userApi.createOrder` replaced by `createCashfreeOrder`/`getCashfreeOrderStatus`.

**Verified (same day, later)**: `./mvnw -q -o compile` clean, jar built; app `npx tsc --noEmit` held at the 56-error baseline (none in touched files); website `npm run build` clean. **Exercised end-to-end locally against the Cashfree SANDBOX** (jar run with the sandbox keys + local `vvm_db`, test user 751): real Create Order → session id; order reuse on double-tap; anonymous / right / wrong-requester status (200/200/403); webhook rejections (tampered sig, stale timestamp, altered body, no headers → 401, all stored `signatureValid=N`); FAILED then SUCCESS then duplicate-key redelivery then NEW-key redelivery then late FAILED → exactly ONE `user_subscriptions` row (`paymentReference CF_9900011`), `fulfilledBy=WEBHOOK`; CHARGES webhook stored settlement figures; AMOUNT_MISMATCH webhook on a second order → 200, flagged, NOT fulfilled; Free/retired plan/unknown user guards; admin list/events/reverify. Hibernate DID create all 5 indexes/uniques on these two new tables locally — still run `migration-2026-09-15-cashfree.sql`'s verification SELECT per environment. The sandbox App ID/secret live only in the merchant dashboard + the server's environment — never in this repo. **Needs deploy.**

### 2026-09-09 — 🔴 Fresh registrants scored 0% profile completion; 250-char reject cap; emails use the full name
- **`calculateProfileCompletion`'s `requiredFields` listed ONLY the 26 post-signup extras**, so a member who had just completed the entire registration form was told their profile was 0% complete. Verified live on user 923 (all 9 registration fields present, all 26 extras missing → 0%). The 9 registration-collected fields — `firstName`, `lastName`, `dateOfBirth`, `gender`, `caste`, `location` (users) and `education`, `occupation`, `employedAt` (user_details) — now count via a `creditIfPresent` helper, taking the total to 35 so a completed signup lands at ~25%. ⚠️ The three user_details credits must sit AFTER `userDetails` is fetched further down the method — the variable is `userDetails`, not `userDetail`, and is not in scope where the user-entity credits run.
- **Rejection reason capped at 250 chars server-side** (`AdminService.rejectUser`) to match the admin panel's new input cap — the member reads it verbatim on the rejection screen and in the email, and it is the same cap as their own resubmission note, so both halves of that exchange are symmetrical. The capped text (not the raw input) is what gets stored and emailed.
- **Outbound email now addresses the member by first AND last name.** New `AdminService.fullName(user)` (falls back through either half to "there", so a greeting never renders "Dear ,") used by approval, rejection and plan-assigned mail; `AuthService`'s reset OTP builds the same from the user row. ⚠️ Registration OTP is the one mail sent before a user row exists, so the full name has to travel in the request — the app now sends `firstName: "First Last"` in the `/auth/send-otp` body.

Compile clean; jar rebuilt (12:00). Needs deploy.

### 2026-09-05 — 🔴 createUser: email-uniqueness gate + @Transactional with manual rollback
See app CLAUDE.md same date (later 13) for the full story. Email gate → 409; `@Transactional` + `TransactionAspectSupport...setRollbackOnly()` in the catch (the method swallows exceptions, so without the mark the early user save still committed → ghost accounts). Same jar.

### 2026-09-05 — app.link → /open-app landing (emails open the app)
`app.link` now points at the website's new `/open-app` page (scheme attempt + Play Store fallback) instead of the homepage — the `{{appLink}}` button in approval/rejection emails opens the installed app. ⚠️ Check the Zoho templates actually use `{{appLink}}` for the button. Same jar.

### 2026-09-05 — deleted_accounts.planTitle + admin list endpoint
`DeletedAccount` gained `planTitle` (latest subscription's title at deletion, native query, "Free" fallback). `GET /admin/deleted-accounts?page&size` (newest first) feeds the new admin Users → Deleted Accounts page. ddl-auto adds the column. Same jar.

### 2026-09-05 — Login routes by identifier shape; changePinWithOld
`UserService.login`: the `mobile` field now carries email OR mobile — `contains('@')` → `findByEmail` (ci collation handles case), else the existing mobile path incl. family-login fall-through (deliberately mobile-only). New `POST /user/changePinWithOld` `{mobileNumber, oldPin, newPin}` → 401 on old-pin mismatch; plain `/changePin` untouched for the email-reset flows. Compile clean; same jar.

### 2026-09-05 — `deleted_accounts` tombstone table (who deleted + why)
New entity/repo `DeletedAccount` (`deleted_accounts`, indexes on userId/mobile): snapshot of the member captured BEFORE `deleteAccount`'s wipe, persisted AFTER it succeeds; reason from new optional `?reason=` query param (capped 500). Deliberately not in the wipe list. A failed insert logs and never fails the deletion. ddl-auto creates it on deploy — same pending jar.

### 2026-09-05 — OTP email: one automatic retry + honest failure (was: "sent successfully" while nothing left the server)
Client: OTP emails slow, sometimes never arrive. The timeout fix (2026-09-04) stops a slow ZeptoMail from freezing the whole API, but delivery itself had a second hole: **`sendTemplate` swallowed every failure (log-only) and `AuthService` returned "OTP sent successfully" regardless** — the member waited for a mail that was never accepted. Now: `sendTemplate` retries ONCE on failure and returns whether any attempt was accepted; the OTP path returns **502 "We couldn't send the verification email right now"** when both attempts fail, so the app shows a real retryable error. Approval/rejection/plan emails get the retry too (their callers stay fire-and-forget by design). ⚠️ `sendTemplateOnce` and `sendHtml` have IDENTICAL tail blocks — an anchored edit on that tail hit the wrong method once; anchor on the method signature, not the tail. ⚠️ Still open (AUDIT_REPORT): the OTP is echoed in the sendOtp response body ("DEV MODE") — remove before real scale. Compile clean; same pending deploy.

### 2026-09-04 (night) — `POST /gallery/replaceGalleryImage` (replace one photo in place)
Multipart `userId` (raw numeric) / `galleryId` / `file` → uploads to R2 and updates the SAME `GalleryEntity` row's URL. Deliberately NOT delete-then-insert: that reorders the gallery and at the 3-photo cap opens a window where a failed upload loses the photo. Ownership-checked like `setAsProfileImage` (403 on someone else's row). Compile clean; same pending deploy.

### 2026-09-04 (night) — 🔴 EmailService had NO timeouts — synchronous sends froze the whole API intermittently
`HttpClient.newHttpClient()` has no connect/request timeout, and every email (OTP, approval, rejection, plan-assigned) is sent synchronously inside the request thread; with open-in-view each hung request pins a Hikari connection (pool=20). Slow ZeptoMail = whole-API stall (Cloudflare 522s / everything timing out at once), then recovery — seen live on launch day. Fixed: `connectTimeout(5s)` + per-request `.timeout(10s)` on both the template and plain senders. Compile clean; same deploy as resubmit/assign-plan.

### 2026-09-04 (later) — Admin assign-plan endpoint + plan-assigned email
**`POST /admin/users/{userId}/assign-plan`** body `{planId, adminId?}` → `AdminService.assignPlanToUser`: creates an ACTIVE `user_subscriptions` row exactly like `approvePayment` (autoRenew N, start today, end +durationDays, `paymentReference=ADMIN_ASSIGN[_BY_id]`; latest-row-wins finder makes it effective immediately), then email + Direct push + in-app notification. ⚠️ **Deliberately allows `isActive='N'` plans** — admin assignment is how invisible packs (Trial) are granted; the isActive guard only protects member-facing purchase. **EmailService gained `sendPlanAssignedEmail` via a new `sendHtml(...)` sender using ZeptoMail's PLAIN send API** (`apiUrl` minus `/template` → `/v1.1/email`, inline `htmlbody`) — works without creating a Zoho template; same enabled/mock guard. Compile clean; needs deploy (same deploy as the resubmit endpoint).

### 2026-09-04 — Resubmit-profile endpoint built (the app had been calling a 404 forever)
`POST /user/resubmit-profile/{encodedUserId}` (optional body `{message}` ≤250) — see the app CLAUDE.md same-date entry for the full story. New `users.resubmission_note` column (250, ddl-auto creates it); note appended LAST to `findAllUsersForAdmin`'s select and mapped as `resubmissionNote`; `approveUser`/`rejectUser` null the note (consumed once acted on). APPROVED → 400, else status → PENDING. Compile clean; **needs deploy**.

### 2026-08-18 — 🔴 `/user/deleteAccount/{id}` is UNAUTHENTICATED — anyone can delete anyone
Proven against live production while verifying the Play Store deletion flow. Two seeded accounts (894, 895) were deleted with a plain `curl -X DELETE` carrying **no `Authorization` header at all**, and a third call for a non-existent id also returned `200 "Account permanently deleted."`

Two independent failures stack:
1. **`SecurityConfig` is `anyRequest().permitAll()`** with the JWT filter commented out (line 38), so no endpoint requires a token.
2. **`deleteAccount(String encodedUserId)` takes the target identity straight from the URL path** and never compares it to the caller. Even with auth switched back on, any authenticated member could delete any other member.

`userId` values are sequential integers and the encoding is plain Base64, so the entire member base is enumerable: `for i in 1..900: DELETE /user/deleteAccount/$(base64 <<< $i)`. This is mass account deletion by anyone who can reach the API.

**Fix needs both parts**: re-enable the JWT filter, and change the method to derive the user from the authenticated principal (or verify the path id matches it and return 403 otherwise) — the same shape as the `deleteConversationForUser(id, requesterId)` fix from 2026-08-13. The web page and app screen are fine; the client-side login gate is cosmetic because the endpoint itself is open.

⚠️ **Also confirmed the same day: the deployed jar does NOT contain the verification/support-table additions to the delete cascade.** A test `id_verifications` row and a `support_tickets` row both **survived** a successful account deletion (users row gone, those rows still present). So in production today an Aadhaar/PAN document record outlives the account it belongs to — which contradicts the published privacy policy and Google Play's deletion requirement. **A redeploy is required.**

### 2026-08-18 (later) — 🔴 `deleteAccount` left VERIFICATION DOCUMENTS behind; R2 objects still orphaned
A third-party review of the privacy policy asked, correctly, whether identity documents are actually deleted when an account is deleted. They were not. Six tables holding personal data had **no** `DELETE` in the cascade, so a member who "permanently deleted" their account left behind:

`id_verifications` · `income_verifications` · `education_verifications` (each holds `document_url` + `document_number_last4` for Aadhaar / PAN / Voter ID / licence / passport / income proof / degree certificates), plus `profile_boosts`, `callback_requests` (name, mobile, email) and `support_tickets` + `ticket_messages`.

Added 7 statements (`ticket_messages` before `support_tickets`, since it resolves ticket ids by subquery). Compiles clean; all 7 dry-run against production as `SELECT COUNT(*)`. **All three verification tables are currently empty — nobody has used the feature yet, so there is no live exposure — but this had to be fixed before the broker campaign brings ~1,500 members in.**

⚠️ **Storage objects are still NOT deleted, and this is the remaining gap.** `S3FileUploadService` has **no delete method at all**, so removing the DB row only drops the pointer: the uploaded file stays in the bucket forever. Worse, every upload path passes `enablePublicReadAccess = true`, so those objects sit at a **public, unauthenticated URL** — a deleted member's Aadhaar image would remain publicly fetchable by anyone holding the link, indefinitely. Fixing it properly means adding an object-delete call **and** moving documents to a private bucket served by signed URLs, which also touches how the admin panel displays them. Not done here; flagged as the highest-priority privacy item after the auth re-enable.

💡 **"AWS" in this codebase is a misnomer** — `S3Config` points the AWS SDK at `https://…r2.cloudflarestorage.com` with `withPathStyleAccessEnabled(true)`, i.e. **Cloudflare R2 via its S3-compatible API**. There is no AWS account and no second upload path (`grep` for `AmazonS3`/`putObject` returns only `S3FileUploadService` and `S3Config`). The `aws.*` property names and `S3*` class names are cosmetic; renaming them would require editing `application.properties` on the production server in lockstep, so it is deliberately left alone.

### 2026-08-18 — 🔴 `deleteAccount` deleted NOTHING in production; every call 500'd
Found while adding the deletion page to `VaibhavVivaahaWeb` (Play Store requires a public deletion URL). A probe against live production returned `code 500 — "Transaction silently rolled back because it has been marked as rollback-only"`. Two compounding causes:

**1. Ten of the 23 column references were wrong** — verified against the live `information_schema`, every table existed but these columns did not: `interestRequest.interestReceive` (real: `interestReceived`), `chats.receiverId` (**no such column at all** — a chat is addressed by its conversation), `conversations.userOne`/`userTwo` (real: `user_one_id`/`user_two_id`), `viewedProfile.viewerUserId`/`viewedUserId` (real: `viewedBy`/`userIdValue`), `userLikes.likedUserId` (real: `likedTo`), and four snake_case ones — `savedSearchFilters.saved_search_id`, `savedSearches.user_id`, `PAYMENT_REQUESTS.user_id`.

**2. The per-statement `try/catch` is what hid it, and also what made it total.** Catching a JDBC exception does **not** un-poison the surrounding transaction — Spring has already flagged it rollback-only, so the commit throws and every delete that *did* succeed is undone. The method logged 13 cheerful `deleteAccount [n] …` lines and then rolled the lot back. **A swallowed exception inside a `@Transactional` method is not resilience; it is a silent total failure.** The catch is gone: the cascade is now atomic on purpose, because a partial wipe would leave orphaned chats and interests pointing at a deleted user row.

`chats` needed two statements rather than one — the user's own messages by `senderId`, then whatever remains in any conversation they were part of (the other participant's side) via a subquery — and both must run **before** `conversations`, which that subquery reads.

**Verified**: `./mvnw compile` clean, and all 24 rewritten statements dry-run against production as `SELECT COUNT(*)` (nothing deleted) — every one parses and resolves. For test user 702 they would remove 1 subscription, 5 interests, 23 chats, 4 conversations, 31 notifications, 9 saved-search filters, and so on, ending with the `users` row.

⚠️ **Needs a redeploy.** Until then, account deletion is broken on **both** the app (Settings → Delete Account) and the new web page — an App Store *and* Play Store compliance failure, not just a bug. Test it on a throwaway account after deploying: this is the one endpoint that cannot be re-tested on real data.

### 2026-08-10 — Contact consent gate + SUBCASTE_SEARCH premium gate + search "Others" ranking
**Consent gate (`UserService.revealContact`)**: now requires an APPROVED interest between viewer and profile (`interestRequestRepository.findBetweenUsers` + `ApprovalStatus.APPROVED`), checked BEFORE quota so blocked attempts don't burn reveals; returns `403 INTEREST_NOT_APPROVED`. Closes the "buy Classic, harvest 40 phone numbers of strangers" path. NOTE: `getProfileDetailWithIntractionStatus` already masked contact without approval (last-4 mobile, null email) and horoscope was already gated — revealContact was the only hole. Verified live both ways.

**SUBCASTE_SEARCH (Silver+)**: new `features` row + `planFeatures` for plans 4/5/6. `UserService.filterUsers` has its own `hasSubcasteSearch` check (deliberately NOT reusing `hasAdvSearch` — different tier floor); non-entitled callers get `subcasteIds` silently nulled (`effectiveSubcasteIds`), matching ADV_SEARCH's silent degrade. Surfaces to the app automatically as `entitlements.subcasteSearch` via the generic entitlements builder. **Production needs the seed insert + deploy.**

**Search "Others" behaviour (both `advanceFilter` and `normalFilter`)**: an explicit subcaste filter also admits members whose subcaste is the caste's "Others" row (NOT members with NULL subcaste — that would no-op the filter on legacy data), ranked LAST via a scalar `:subcasteFilterApplied` flag as the primary ORDER BY key (boost/plan-tier order preserved WITHIN relevance groups). Two landmines documented in the repository Javadoc: (1) an IN-list parameter referenced >2× corrupts Hibernate native-query binding with a misleading "No argument for named parameter ':occupation'" error — hence the scalar flag; (2) `--` comments inside native query strings register phantom parameters (Hibernate scans raw SQL without skipping comments) and fail startup — never put SQL comments in these queries. `SubCasteRepository` list queries sort "Others" to the bottom of dropdowns. Registration requires subcaste ONLY when the caste has subcastes (`countActiveByCasteId` guard) — FREE CASTE BAR stays exempt.

### 2026-08-09 — Subcaste feature, inactive-plan guard, perf indexes + Hikari/gzip (cross-repo)
**Why:** Three client asks: (1) app navigation felt slow, (2) Starter + Classic plans are being retired and the app must follow a DB flag rather than hardcoded lists, (3) add a subcaste dimension mapped to each caste. See the mobile repo's CLAUDE.md for the matching app-side entry.

**A. Subcaste (new, additive — every field is nullable/optional, no breaking change)**
- **New table `subcastes`** — `id`, `casteId` (FK → `castes.id`), `subcasteName VARCHAR(100)`, plus the `GenericEntity` audit columns; `KEY idx_subcaste_caste (casteId, isActive)`. Deliberately a **plain VARCHAR name, NOT an enum** — `castes.casteCode` is an enum and needs a schema ALTER to add a caste, which is a known landmine; do not repeat it here.
- **`users.subcasteId BIGINT NULL`** + `INDEX idx_users_subcaste (casteId, subcasteId)`.
- **Seeded 2026-08-09 (later same day)** with 23 client-supplied names via `seed-subcastes.sql` — Vanniyar 5, Naidu 6, Aadi Dravidar 5, Mudaliar 7; FREECASTEBAR deliberately 0. App UI still degrades to hidden when a caste has zero subcastes. Admin-panel CRUD for this table is still not built (see section 19).
- New `models/SubCasteEntity.java`, `repositories/SubCasteRepository.java`.
- New endpoints on the **existing** `CasteController` (so they inherit the same open `/caste/**` access as `getAllActiveCaste`): `GET /caste/getAllActiveSubcastes` and `GET /caste/getSubcastesByCasteId/{casteId}` — standard `ResultResponse`, `data: [{id, casteId, subcasteName, isActive}]`.
- `UserEntity` gained `subcasteId` (persisted) and `subcasteName` (`@Transient`, resolved on read by `UserService.resolveSubcasteName()` so the app renders it without a second lookup). Populated in `getUserById`, `getProfileDetailByUserId`, `getProfileDetailWithIntractionStatus`.
- `UserProfileRequest.subcasteId` (registration) and `PersonalInfoRequest.subcasteId` (post-signup edit). **Both validate that the subcaste actually belongs to the submitted/current caste** and return `400 "Subcaste does not belong to the selected caste"` otherwise — otherwise a profile could carry a subcaste from an unrelated caste.
- `UserFilterRequest.subcasteIds` (**`List<Long>` — multi-select**, matching how `degree`/`star`/`dosham` already work) + one condition line (`AND (COALESCE(:subcasteIds) IS NULL OR u.subcasteId IN (:subcasteIds))`) in **both** native queries in `UserRepository` — `advanceFilter` AND `normalFilter` — so basic and advanced search both support it. Null/empty = no filter, so every existing caller is unaffected. Verified live: `[1]`→2 rows, `[3]`→2, `[1,3]`→4, `[1,3,4]`→7 (exactly additive union), `null` and omitted both → unfiltered.

**B. Inactive-plan guard (Starter id 2 / Classic id 3 are being retired)**
`subscription_plans.isActive` already existed and `getAllActivePlans` already filtered on it, but **nothing stopped a NEW subscription being created on a retired plan**. Added that check in the two places a paid subscription is born:
- `AdminService` payment-approval path — rejects with `400 SUBSCRIPTION_PLAN_INACTIVE: '<title>' is no longer offered...` before creating the `UserSubscriptions` row (catches a payment request raised *before* the plan was pulled).
- `UserSubscriptionsService.createUserSubscription` — same guard, plus a `404` when the plan id doesn't exist at all.
**Deliberately NOT touched: any code path that reads entitlements for an existing subscription.** Entitlements resolve from `planFeatures` by plan id, never from `isActive`, so the 2 active Starter and 10 active Classic members keep everything until their `endDate`. Verified live: user 700 (Classic) still returns full entitlements after the flip.
**Verified end-to-end on a running server**: 6 plans → flip → `[Free, Silver, Gold, Platinum]`; buying Classic → `400 SUBSCRIPTION_PLAN_INACTIVE`; buying Silver → `200 SUCCESS`. Fully symmetric — re-activating is the inverse UPDATE, no code change.

**C. Performance — indexes (this was the single biggest backend win)**
Before this, `chats`, `notifications`, `viewedProfile`, `user_subscriptions`, `shortlisted_profiles`, `blockedUsers` had **only PRIMARY keys**, so every hot query was a full table scan. 13 indexes added (see the mobile repo's `AUDIT_REPORT.md` DB-1 for the exact DDL). Measured with `EXPLAIN` before/after:
`chats WHERE conversationId` cost 26 → **0.7** · `notifications WHERE receiverId ORDER BY createdAt` 28.6 → **3.85** (filesort eliminated entirely — reverse index scan) · `interestRequest WHERE interestReceived` 16.6 → **0.35** · `user_subscriptions WHERE userId+status` 7.1 → **0.35**.
⚠️ **These indexes are NOT reproducible by Hibernate.** `ddl-auto=update` never creates indexes, and there is no Flyway/Liquibase in this project — so **this DDL must be applied by hand to every environment** (it was applied to the local `vvm_db` on 2026-08-09; production/staging still need it).

**D. `application.properties` — Hikari + gzip**
Added `spring.datasource.hikari.*` (max-pool-size 20, min-idle 5, connection-timeout 5000, max-lifetime/idle-timeout) — the defaults were 10 connections / 30s timeout, which the S3-upload and synchronous-email request paths can exhaust. Added `server.compression.enabled=true` for JSON (verified: `Content-Encoding: gzip`).
**`spring.jpa.open-in-view` was deliberately left at its default (true)** with an explanatory comment in the file — list endpoints still serialize the lazily-loaded `UserEntity.userDetail` collection, so turning OSIV off would throw `LazyInitializationException` until those are converted to DTO projections. Do not flip it without doing that first.

### 2026-08-04 — Closed login approval gate: PENDING/REJECTED could log in and get a real token (cross-repo)
**Why:** A new marketing website (`VaibhavVivaahaWeb`) was being wired up to register and log in against this same backend. Before writing its login screen, the question was asked directly: if a user registers (from the app OR the website) and hasn't been approved by an admin yet, can they still log in anywhere? Investigation found the answer was yes — a real, previously-unenforced gap, not just a hypothetical.

**Root cause**: `UserService.login()` only ever checked for `BANNED`/`SUSPENDED` before verifying the PIN and issuing tokens. `PENDING` (the default status for every new signup, set at `UserService.createUser()`) and `REJECTED` were never checked at all — login succeeded, HTTP 200, with a fully valid, usable access + refresh token pair. The mobile app's `ProfileUnderVerificationScreen` (see app CLAUDE.md) only ever ran *after* that successful login, reading `userStatus` from the response and redirecting client-side — a courtesy UX detour, not an actual gate. Any client that didn't reimplement that same client-side check (like the new website, or any future client) would let unapproved users straight in.

**Fixed — `services/UserService.java`, `login()`** (right alongside the existing `BANNED`/`SUSPENDED` checks, before PIN verification): added a block for `PENDING` and `REJECTED`, returning HTTP `403` with `{userId (base64), firstName, userStatus, rejectionReason}` in the response `data` — enough for a client to show a meaningful "still under review" / "not approved: {reason}" message — but no token is issued either way.

**Also fixed — `services/AuthService.java`, `refreshAccessToken()`**: already required `userStatus == APPROVED` before rotating tokens (blocks BANNED/SUSPENDED/PENDING/REJECTED alike), closing the companion gap where an account approved at login time but later banned/suspended/rejected could otherwise keep silently minting fresh access tokens off its still-valid refresh token, since refresh previously never re-checked status after the initial login.

**Cross-repo impact**:
- `VaibhavVivaahaApp`'s `LoginScreen.tsx` — its existing `code === 403` branch (previously written only for BANNED/SUSPENDED, showing "Account Blocked") now also receives PENDING/REJECTED as 403s. **Fixed** (same session) — it now checks `response.data.data?.userStatus` and routes PENDING/REJECTED to `ProfileUnderVerificationScreen` (using the `userId`/`firstName`/`userStatus`/`rejectionReason` this endpoint now includes in the 403 body), preserving the old resubmit/contact-support UX; BANNED/SUSPENDED still get the generic "Account Blocked" popup. See app CLAUDE.md's matching 2026-08-04 changelog entry.
- `VaibhavVivaahaWeb`'s `LoginModal.jsx` was built already knowing about this 403 shape and handles it correctly (see its own CLAUDE.md).

**Verification**: `./mvnw -q -o compile` — clean, no errors.

### 2026-07-20 — Report/block flow overhaul: optional block-on-report, message-level reporting, block-gate coverage gap closed
**Why:** User asked whether report/block matched standard matrimonial-platform behavior. Audit found: (1) `UserReportService.reportUser` unconditionally force-blocked the reported user on every report with no opt-out and no disclosure to the reporter; (2) there was no way to report a specific chat message, only whole profiles; (3) `UserService.getProfileDetailByUserId` (unlike its sibling `getProfileDetailWithIntractionStatus`) had zero block-gate check; (4) the admin's report-listing endpoint returned raw `UserReport` rows with no reporter/reported names, and the Message Reports admin page called a nonexistent endpoint entirely (see adminpanel's own `CLAUDE.md`).

**`models/UserReport.java`** — added `reportType` (`"PROFILE"` default | `"MESSAGE"`), `reportedMessageId`, `messageContent` (snapshot of the message text, survives the message later being edited/deleted).

**`dtos/ReportUserRequest.java`** — added `blockUser` (Boolean, null treated as `true` for backward compatibility with any client predating this field), `reportedMessageId`, `messageContent`.

**`services/UserReportService.java`**:
- `reportUser` — blocking is now conditional on `blockUser` instead of automatic; added a check against `BlockedUserRepository.findByBlockedByUserIdAndBlockedUserId` before inserting a block row (previously absent — reporting an already-blocked user could throw on the unique constraint and surface as a generic 500); sets `reportType`/`reportedMessageId`/`messageContent` when a message id is present; response now includes `{report, blocked}` so the caller knows what actually happened instead of assuming.
- `getAllReports` — now enriches every row with `reporterName`/`reportedName` (looked up via `userRepository.findById`), same "return everything, enrich" pattern used by every other admin-list endpoint added this session. Previously returned bare `UserReport` entities with only numeric ids.

**`services/UserService.java`** — `getProfileDetailByUserId(Long, Long)` gained the same BLOCK GATE (`BlockedUserRepository.findByUsersEitherDirection`, 403 `USER_BLOCKED`) that `getProfileDetailWithIntractionStatus` already had. Low real-world impact today (every current caller only self-fetches through this overload), but closes the gap for any future caller that passes a real cross-user `requesterId`.

**Verification**: `./mvnw -q -o compile` clean.

### 2026-07-17 — New Features/PlanFeatures admin CRUD API
**Why:** User asked to build an admin UI for editing which features each subscription plan includes. Investigation found this was impossible — `Features` and `PlanFeatures` (the tables every plan-gate check in the codebase reads via `featuresRepository.findByCode(...)` + `planFeaturesRepository.findByFeatureIdAndSubscriptionPlanId(...)`) had **no controller or service at all**, admin-facing or otherwise. Only `SubscriptionPlan` (price/duration) had CRUD.

**New `services/FeaturesService.java` + `controllers/FeaturesController.java`** (`/features`): `getAllFeatures`, `createFeature` (rejects a duplicate `code`, uppercases it), `updateFeature` (name/description only — **`code` is deliberately not editable after creation**, since it's the hardcoded string every `findByCode("...")` call across the backend depends on; renaming it would silently break every feature check with no compile error), `deleteFeature` (refuses if the feature is still attached to any plan, to avoid orphaned `planFeatures` rows).

**New `services/PlanFeaturesService.java` + `controllers/PlanFeaturesController.java`** (`/planFeatures`): `GET /matrix` returns `{plans, features, cells}` in one call (all `SubscriptionPlan`s, all `Features`, all `PlanFeatures` rows) — the admin UI pivots this into a features-x-plans grid client-side, same "return everything, enrich, let the client pivot" shape used by `ServiceRequestService.adminListRequests` etc. `POST /upsert` (body: `{subscriptionPlanId, featureId, limitValue, limitPeriod}`) attaches or updates a cell — always an upsert via the existing `findByFeatureIdAndSubscriptionPlanId` lookup, since a plan+feature pair is unique. `DELETE /{id}` detaches a feature from a plan entirely.

**Not changed**: no existing plan-gating code was touched — `UserService`/`ServiceRequestService`/etc. still read `PlanFeatures` exactly as before; this only adds a way to see and edit those rows instead of only being able to seed them via SQL.

**Verification**: `./mvnw -q -o compile` clean. See adminpanel's own `CLAUDE.md` for the matrix UI this powers.

### 2026-07-17 — Added admin "Boost Now" direct-activate endpoint
**Why:** Admin panel had a fully-built boost admin API (`BoostController`/`BoostService`: `adminList`, `adminGrantCredits`, `adminRevokeBoost`) but no UI at all consumed it (see adminpanel's own `CLAUDE.md`). While building that UI, a real gap surfaced: `adminGrantCredits` only tops up `UserSubscriptions.boostCredits` — the user still has to self-activate via `POST /boost/start/{encodedUserId}`. There was no way for admin to instantly boost a specific profile regardless of plan/credits (support gesture, campaign push).

**`services/BoostService.java`** — added `adminActivateBoost(Long userId)`: expires any existing active `ProfileBoost` for the user (same as `startBoost`'s own stale-boost cleanup), then creates a new one directly with `source="ADMIN_GRANT"`, `status="ACTIVE"`, `expiresAt=now+24h` — entirely bypassing the `UserSubscriptions`/credit check `startBoost` normally requires.

**`controllers/BoostController.java`** — added `POST /boost/admin/activate` (body: `{userId}`).

**Verification**: `./mvnw -q -o compile` clean.

### 2026-07-17 — Admin panel's Mobile/Image/Horoscope request pages fixed (were calling a nonexistent endpoint); Family Logins list enriched
**Why:** User reported "we have an image request on db but not showing" in the admin panel. Investigation found `adminpanel`'s `/requests/mobile`, `/requests/images`, `/requests/horoscopes` pages all called `apiGet('/admin/requests', {...})` — **an endpoint that doesn't exist anywhere in this backend**. All three pages' approve/reject buttons also only updated local React state and never called any backend endpoint at all (`/requests/mobile` came closest — it called `PUT /restrictedFieldRequest/updateStatus/{id}` on click, but that endpoint expects a **base64-encoded** id, `Long.parseLong(new String(Base64.getDecoder().decode(encodedId)))`, while the admin page passed the plain numeric id — would 500 server-side, silently masked by the page's "optimistic update anyway" fallback in its catch block). Net effect: these 3 admin pages have likely never worked since they were added — nothing they showed or did ever reached the database.

**`repositories/RestrictedFieldRequestRepository.java`** — added `findByFieldTypeAndStatus`, `findByStatus`, `findAllByOrderByIdDesc` (derived query methods, mirrors `HiddenFieldRepository`/`ServiceRequestRepository`'s existing admin-listing patterns).

**`services/RestrictedFieldRequestService.java`** — added `adminListRequests(fieldType, status, page, size)` (paginated, enriches each row with `requesterName/requesterMobile/requesterProfileImage` and `targetName/targetMobile/targetProfileImage` via `userRepository.findById`, same shape as `ServiceRequestService.adminListRequests`) and `adminUpdateStatus(Long id, String status)` (plain numeric id, unlike the mobile-facing base64 `updateRestrictedFieldStatus`). Extracted the shared notification/push logic from `updateRestrictedFieldStatus` into a private `applyStatusUpdate(...)` helper used by both the mobile (base64 id) and admin (plain id) status-update paths, so there's exactly one place that sends the "Permission Accepted/Declined" notification.

**`controllers/RestrictedFieldRequestController.java`** — added `GET /restrictedFieldRequest/admin/list` (params: `fieldType`, `status`, `page`, `size`) and `POST /restrictedFieldRequest/admin/{id}/status` (body: `{status}`) — same endpoint shape as `ServiceRequestController`'s existing `/service-request/admin/list` + `/service-request/admin/{id}/status`.

**`services/FamilyLoginService.java`** — `adminList()` now returns enriched rows (`primaryUserName`, `primaryUserMobile` looked up via `userRepository.findById(primaryUserId)`) instead of the raw `FamilyLogin` entity, so admins aren't just looking at a bare `primaryUserId` number. Also explicitly surfaces `updatedAt` in the row — this was already being set correctly (bumped by `GenericEntity`'s `@PreUpdate` hook on every save, including the `revoke()`/`adminRevoke()` status flip to `"REVOKED"`), just never read/displayed anywhere — it doubles as "revoked at" for REVOKED rows with no new column needed.

**Confirmed, no change needed:** a revoked family login genuinely cannot sign in afterward — `FamilyLoginService.tryLogin()` looks up via `findByMobileAndStatus(mobile, "ACTIVE")`, so a `"REVOKED"` status (set by either the primary user's own revoke or admin's `adminRevoke`) simply stops matching and login fails, regardless of which path revoked it.

**Not changed**: the mobile app's own `getRestrictedRequestsById`/`getRestrictedRequestsToId`/`updateRestrictedFieldStatus` methods (used by `mailBox.tsx`'s Permissions tab) were left untouched — they already return rich requester/target details (name, age, location, income, occupation, verified badges) and work correctly; this fix only added new admin-facing methods alongside them.

**Verification**: `./mvnw -q -o compile` clean. Admin panel `npx tsc --noEmit` — exit 0, no errors. See adminpanel's own `CLAUDE.md` for the frontend side of this fix.

### 2026-07-17 — Porutham calculation fixed: missing Vedhai, inverted Dina, Nadi/Yoni/Rajju data tables, Rasi name aliases, mandatory disqualifying gates
**Why:** Following the same-day audit (see entry directly below), user said "fix it." This was a full pass across `PoruthamService.java`, `utils/StarInfo.java`, and `utils/RasiLordMapper.java` — not a one-line patch.

**`utils/StarInfo.java`** — rewrote `NADI` (correct mirrored 6-star cycle: Adi/Madhya/Antya/Antya/Madhya/Adi, repeating — replaces the old "first 6 explicit, rest = Antya" bug) and `RAJJU` (correct mirrored 9-star cycle: Pada/Kati/Nabhi/Kantha/Shira/Kantha/Nabhi/Kati/Pada, repeating 3× across all 27 stars — replaces the old simple mod-5 pattern). Rebuilt `YONI` with the real per-nakshatra classical animal assignment (14 distinct animals, no more fabricated "Crow"/"Eagle"/"Pig"). Fixed 3 wrong `GANA` entries (star indices 4, 6, 10). Added two new maps: `VEDHA` (13 mutual-enemy star pairs; Dhanishta/23 has none) and `YONI_ENEMY` (7 enemy-animal pairs — same shape the user's own reference example named: Snake/Mongoose, Cat/Rat).

**`utils/RasiLordMapper.java`** — added 4 missing rasi-name aliases (`kadagam`, `simmam`, `viruchagam`, `makaram`) that `StarMatch.tsx`'s `rasiData` list actually sends for Cancer/Leo/Scorpio/Capricorn instead of the Sanskrit-transliteration spellings this map only had before (`katagam`, `simham`, `vrischikam`, `magaram`). Without these, `getLordFromRasi` silently returned `null` for roughly a third of real submissions, zeroing out Rasi Adhipathi Porutham for those users. Same aliases added to `PoruthamService.mapRasiNameToIndex`'s switch (also fed Rasi and Vasya Porutham, same gap).

**`services/PoruthamService.java`**:
- Added `checkVedhai` (new, using the new `StarInfo.VEDHA` map) and wired it into `evaluate()`; added `"vedhai" → 10` to `WEIGHTS`, which now correctly sums to 100 (was 90).
- Fixed `checkDina`'s inverted pass condition (now passes on remainder 0/2/4/6/8, was passing on 1/3/5/7).
- Rewrote `checkYoni` to check the `YONI_ENEMY` pairing instead of requiring an exact same-animal match — same animal or any non-enemy animal now passes, only true enemy pairs fail.
- Rewrote `checkRasi` to use a directional bride→groom rasi *position* (1st/7th/9th/10th/11th/12th = good, 2nd–6th and 8th = bad) instead of an undirected `diff==6||diff==8` check that let 2nd–5th positions pass.
- Fixed `checkRasiAdhipathi`'s planet friend/enemy table against the standard Vedic Naisargika Maitri table (previous version had Mercury/Moon's friendship and enmity swapped, plus wrong Mars/Venus and Jupiter/Saturn enmities).
- Fixed `checkStreeDirga`'s threshold (`diff >= 7` → `diff >= 13`).
- Rewrote `checkVasya` from a 2-hardcoded-pair stub into a real group-based check (`Chatushpada`/`Manava`/`Jalachara`/`Vanachara`/`Keeta`, with the Chatushpada+Vanachara and Manava+Jalachara cross-group compatibilities) — see the section 17 landmine for the one simplification this still makes (Simha/Dhanusu/Makaram aren't split by decan, since this endpoint has no birth-degree data).
- `evaluate()` now overrides the verdict string to `"NOT RECOMMENDED (Rajju Dosha)"` / `"...(Vedhai Dosha)"` / both, whenever Rajju or Vedhai fails — **regardless of the numeric percentage** — matching the traditional rule that these two are mandatory disqualifying filters, not ordinary weighted inputs. The percentage/score themselves are unchanged (still reflect the full weighted total) so the raw breakdown stays visible; only the verdict text now reflects disqualification.

**Frontend**: no changes needed — `StarMatchResult.tsx` renders `result.results` generically (a `.map()`), so the new Vedhai entry (and the corrected verdict string) render automatically with no code changes on that side.

**Verification**: `./mvnw -q -o compile` clean. Manually reasoned through several star/rasi pairs by hand (documented in the conversation, not committed as an automated test) — e.g. same-star same-rasi now correctly disqualifies via Rajju/Nadi dosha (matches the real-world convention against marrying someone born under an identical star); Punarvasu(7)/Pushya(8) now correctly resolve to different Nadis (Adi/Madhya), where the old table forced both to "Antya" and wrongly failed the pair. **No automated test suite exists for this calculation** — if precision matters before this ships to real users, get a real astrologer or a second independent reference to check a batch of known-answer pairs, especially for `RAJJU` and `VASYA_GROUP` (see the section 17 landmine's residual-confidence note).

### 2026-07-17 — Porutham calculation audit sharpened against a user-supplied reference (no code changes)
User asked to verify the 2026-07-15 porutham audit against a standard 10-Porutham reference they'd found. Cross-checking confirmed the earlier findings and added several new, higher-confidence ones — see the section 17 landmine (now superseded by the fix above) for what was found. Headline additions since the 2026-07-15 entry below: **Vedhai Porutham (a mandatory 10th porutham) is completely unimplemented**, which also explains why `WEIGHTS` sums to 90 instead of the 100 its own comment claims; **Rajju and Vedhai should be pass/fail disqualifying gates on the final verdict, not weighted percentage inputs** (a same-Rajju pair can currently still score "GOOD MATCH" if everything else is strong, which is backwards); and **`checkDina`'s pass/fail condition is fully inverted** relative to the correct rule (should pass on remainder 0/2/4/6/8, currently only passes on 1/3/5/7). `checkMahendra` was newly confirmed **correct**. Fixed the same day — see entry above.

### 2026-07-15 — Star Match now requires mutual interest approval (cross-repo) + porutham calculation audit
**Why:** User asked whether Star Match should be usable against another member without mutual interest approval — confirmed this was a genuine gap: `ProfileDetail.tsx`'s `handleStarMatch` only checked plan tier, never `interestStatus`, unlike `handleRequestCall` (Voice Call) right next to it, which already required `interestStatus === 'APPROVED'`.

**`dtos/MatchRequestDto.java`** — added two optional top-level fields: `requesterUserId` (String, base64-encoded — same convention as every other endpoint's `encodedUserId`) and `viewedUserId` (String, plain numeric — matches `ServiceRequestService`'s `targetUserId` convention). Both are `null` for the standalone/manual Star Match flow (no other member being checked), present only for the profile-initiated flow.

**`services/PoruthamService.java`** — `evaluate()` now runs a gate at the very top, **only when both `requesterUserId` and `viewedUserId` are present** in the request: decodes `requesterUserId` from base64, parses `viewedUserId`, calls `InterestRequestRepository.findBetweenUsers(requesterUserId, viewedUserId)`, and returns `403`/`"INTEREST_NOT_APPROVED"` unless an `APPROVED` `InterestRequest` exists between them — same shape as `ServiceRequestService.createRequest`'s existing `VOICE_CALL` gate. Added `InterestRequestRepository` as a new `@Autowired` dependency; imports `ApprovalStatus`, `InterestRequest`, `Base64`.

**Frontend changes** (`VaibhavVivaahaApp` — see its own CLAUDE.md for full detail): `ProfileDetail.tsx`'s `handleStarMatch` gains the same `interestStatus !== 'APPROVED'` check as `handleRequestCall`, plus a matching greyed-out button state; `StarMatch.tsx` forwards `requesterUserId`/`viewedUserId` in its submit payload only when it received a `viewedUserId` route param to begin with; `StarMatchResult.tsx` now explicitly checks for the `INTEREST_NOT_APPROVED` response (previously any non-success body left the screen stuck on its loading spinner forever, since nothing else caught that case).

**Separate finding surfaced during this investigation, NOT fixed this session:** the porutham calculation itself has real data-accuracy bugs across most of its weighted factors — see the new section 17 landmine above for the full breakdown (Nadi table, Yoni table, Gana table, Rasi Adhipathi planet-friendship table, Vasya stub, Stree Dirga threshold). Well over half of the 100-point weight is affected. Left for the user to prioritize before touching — this needs verified classical reference data, not a guess-and-check patch.

**Verification:** `./mvnw -q -o compile` — clean, no errors. `npx tsc --noEmit` in the app repo — held at the existing 171-error baseline, no new errors. Manually confirmed via code read (not yet a live device test) that the standalone Star Match flow (`settingsPage.tsx`/`QuickAccessFAB.tsx`, no target member) sends no `requesterUserId`/`viewedUserId` at all, so the new gate never triggers for it.

### 2026-07-15 — Fixed two basic-search filter bugs: stale age column, and Job Sector silently doing nothing
**Why:** A real user testing the app on the Free/Starter ("basic filter") tier reported two bugs: (1) filtering Age 18-60 + Job Sector "Government" still returned Private-sector profiles, and (2) filtering Age 21-29 returned a profile ("Bhavani") whose real age looked way off. Verified both against live `vvm_db` data before fixing (not assumed):
```
userId  firstName  dob         stale `age` column  live age (TIMESTAMPDIFF)
24      Divya       1997-06-06  25                  29
28      Bhavani     1991-10-10  25                  34
```

**Bug 1 — age range used a frozen `users.age` string column, not a DOB computation.** `Bhavani`'s stored `age` was `25` (set once at signup) while her real, current age is `34` — 9 years stale — so she incorrectly passed an "Age 21-29" filter. Both `UserRepository.advanceFilter` and `.normalFilter` (`UserRepository.java`) compared `CAST(u.age AS UNSIGNED)` against the requested range. **Fixed**: both now use `TIMESTAMPDIFF(YEAR, u.dob, CURDATE())` instead — matches the pattern every other service in the codebase already uses (`Period.between(dob, now).getYears()`), except these two native queries were the last holdouts still trusting the cached column. See new section 17 landmine.

**Bug 2 — Job Sector filter was a complete no-op on the basic (non-ADV_SEARCH) tier.** `UserService.filterUsers` branches on `hasAdvSearch` (Classic+ plans): premium users hit `advanceFilter`, which already had a correct `employedAt` clause; everyone else hits `normalFilter`, which **had no `employedAt` parameter at all** — the frontend (`SearchTabs.tsx`) sends Job Sector unconditionally (unlike Education/City, which the frontend explicitly premium-gates with a 🔒 + upgrade modal), so a Free/Starter user picking "Government" got it silently dropped server-side, not degraded — every sector passed. `Divya` (a genuinely `PRIVATE`-sector profile) is exactly what leaked through. **Fixed**: added `employedAt` param + `AND (COALESCE(:employedAt) IS NULL OR ud.employedAt IN (:employedAt))` clause to `normalFilter` (mirroring `advanceFilter`'s existing clause), and updated `UserService.filterUsers`'s `normalFilter(...)` call site to pass `filterRequest.getEmployedAt()`. Chose to add the clause here rather than premium-gate the frontend control, since the UI was never gated — Job Sector was clearly intended as a basic-tier filter, just never wired up server-side for that tier.

**Verified fix** against the same live data — after the change, the exact query `Age 21-29 + Government` for these two profiles now correctly returns zero rows (both `SearchTabs.tsx`'s existing `SearchResult.tsx` card list would exclude them), confirmed via direct `TIMESTAMPDIFF`/`employedAt IN (...)` query against `vvm_db` before considering this done.

**Not changed:** `normalFilter` still doesn't support Occupation/Education/Star/Dosham/Horoscope filters (those remain advanced-tier only, matching the frontend's existing premium gates for those specific fields) — only Job Sector was added, since only Job Sector had the frontend/backend gating mismatch.


### 2026-07-14 — Moved 9 fields out of required signup into post-signup profile completion
**Why:** Mobile app's `sign-up.tsx` asked for 23 fields across 2 pages before a user could even see their profile — flagged as too much friction for initial registration. Agreed to keep only core identity fields (name, DOB, gender, caste, mobile, age, email+verify, education, occupation, employment status, city, PIN) required at signup, and move Father's/Mother's Name+Occupation, Job Place, Current Address, Native Place, Annual Income, and Education in Detail to the existing post-signup profile-completion flow instead. See the mobile app's `CLAUDE.md` section 18 (same date) for the frontend side of this change.

**Backend investigation confirmed this was low-risk:**
- `UserProfileRequest` (signup DTO) already had **no validation annotations** on any of these 9 fields, and `UserController.createUser` doesn't even use `@Valid` — the only real "required" gate was the frontend's own client-side check.
- None of the relevant `UserDetailEntity`/`UserEntity` columns are `nullable = false`, and there's no Flyway/Liquibase in this repo (`ddl-auto=update` only) — no DB migration needed anywhere for this change.
- 5 of the 9 fields (Father's/Mother's Name+Occupation via `FamilyInfoRequest`/`updateFamilyInfo`, Annual Income via `EducationInfoRequest`/`updateEducationInfo`) **already had a complete, working post-signup update path** — zero backend change needed for those.

**Modified — `dtos/EducationInfoRequest.java`**: added `jobPlace`, `educationInDetail` fields (both already existed as `UserDetailEntity` columns, set only at signup until now — no migration).

**Modified — `services/UserDetailService.java` → `updateEducationInfo(...)`**: now also calls `userDetail.setJobPlace(...)` / `setEducationInDetail(...)`. Also **null-guarded** the `EmploymentType.valueOf(request.getEmployedAt().toUpperCase())` conversion (previously would NPE/throw on blank) — since Annual Income/Job Place can now be saved independently of signup, a caller might resave this section without re-sending Employment Status.

**Modified — `dtos/PersonalInfoRequest.java`**: added `currentAddress`, `nativePlace` fields.

**Modified — `services/UserDetailService.java` → `updatePersonalInfo(...)`**: now also calls `userDetail.setPresentAddress(request.getCurrentAddress())` and `userDetail.setPermanentAddress(request.getNativePlace())`. **`permanentAddress` was a completely dormant column** (never read or written anywhere in the codebase before this) — reused it for Native Place instead of adding a new column. See section 17 landmine.

**Modified — `services/UserService.java` → `createUser(...)`**: same null-guard added around `EmploymentType.valueOf(request.getEmployingIn()...)` (line ~923) as a defensive hardening — `employingIn` stays required at signup so this wasn't actively broken, but it's the same unguarded pattern that was already flagged as a landmine, fixed while touching adjacent code.

**Modified — `services/UserDetailService.java` → `calculateProfileCompletion(...)`**: added `jobPlace`, `educationInDetail`, `annualIncome`, `currentAddress` (→`presentAddress`), `nativePlace` (→`permanentAddress`), `fatherName`, `fatherOccupation`, `motherName`, `motherOccupation` to the tracked `requiredFields` list, with matching completion checks (new `hasText(String)` helper for the 5 plain-column fields, reusing the existing `checkField(JsonNode,...)` helper for the 4 family-JSON fields) and two new grouped `nextActions` entries (`WORK_INCOME`, `ADDRESS`) alongside the existing `FAMILY_DETAILS` entry — so the mobile app's completion % and "add this next" nudge now actually points users at the fields moved out of signup.

**Known pre-existing quirk, not introduced by this change:** `UserService.createUser` writes `familyInfo` as a plain JSON **object** (`{"father":"",...}`), while `UserDetailService.updateFamilyInfo` always writes it back as a JSON **array** (`[{"father":"",...}]`) and `calculateProfileCompletion` only reads the array form (`familyInfo.isArray()`). This means father/mother/family-type/etc. fields are silently skipped by the completion calculator until a user's *first* Family Detail save converts the blob to array form — harmless today (they correctly stay counted as "missing" either way), but worth fixing properly if `calculateProfileCompletion` is ever refactored.

**Not changed:** no DTO validation annotations removed (none existed), no DB migration, no new endpoints — everything reuses `update-personal-info`/`update-education-info`.

### 2026-07-10 — Fixed Classic's SEND_REQUEST regression (product review, local DB only — live not yet updated)
**Why:** User asked for a product-manager-style review of the plan tiers. Confirmed the bug flagged in the 2026-07-09 entry below is real and worse than it first looked: normalized per day, Starter gives 25 requests / 30 days (0.83/day) while Classic gave 15 requests / 90 days (0.17/day) — a paying-more-for-less "downgrade trap" a customer would immediately notice (this user did). Per-day *price* is fine (Classic is actually cheaper per day, ₹11.10 vs ₹16.63), so the fix is scoped to the quota only, not pricing.

**Fixed:** `planFeatures` row `id=23` (`featureId=4` SEND_REQUEST, `subscriptionPlanId=3` Classic) — `limit_value` changed from `'15'` to `'60'` (≈20/month over 90 days, clearly above Starter's 25/month rate, still well below Silver's unlimited so the ladder stays meaningful). **Applied to local `vvm_db` only** — user explicitly chose local-only scope; live `uravugal` RDS still has the old value of `15`. To promote when ready:
```sql
UPDATE planFeatures
SET limit_value = '60', updatedAt = NOW(), updatedBy = '<your-name>'
WHERE id = 23 AND featureId = 4 AND subscriptionPlanId = 3;
```
(Row `id=24`, `featureId=5` REQ_LIMITED — the companion counter — was intentionally left untouched at `15`; verify whether `UserFeatureUsageService` reads `SEND_REQUEST` or `REQ_LIMITED` as the actual enforced cap before assuming the companion row also needs bumping — see section 7.3 note on the two features tracking the same quota.)

**Not changed:** `VIEW_PERSONAL_INFO`'s 40-reveal quota for Classic — reviewed and judged reasonable (Starter gets zero contact reveals at all, so 40 over 90 days is a genuine, generous unlock, not stingy).

### 2026-07-09 — Documentation correction pass: sections 7.1/7.2/7.4/8.3 were stale against the live DB
**Why:** Asked to build a QA test plan cross-referencing every plan's gated features against the live DB. Query results didn't match this file in several places — not just cosmetic drift, one was a wrong statement given to the user earlier in the same session (claimed MESSAGE required Silver+ and blocked Classic; the DB and `ChatService` code both show Classic has always had unlimited messaging, and Starter has a 5-conversation allowance — only Free is actually blocked).

**Corrected:**
- 7.1: Starter price was documented at ₹199, DB has ₹499
- 7.2: table claimed 27 feature codes; the `features` table actually has 32 (ids 1–33, id 29 unused) — added the 5 missing rows (`DAILY_MATCH_ALERT`, `PROFILE_BOOST`, `INCOME_VERIFIED_BADGE`, `EDUCATION_VERIFIED_BADGE`, `ID_VERIFIED_BADGE`) and fixed wrong tier values on `SEND_REQUEST`/`REQ_LIMITED` (Starter/Classic numbers were swapped relative to reality — Classic's real quota of 15 is lower than Starter's 25), `MESSAGE` (see above), `PROFILE_VIEW_LIMIT` (Classic's "100" was invented; the real row doesn't exist), `WHO_VIEWED`, and `NOTIFICATION_ALERT` (starts at Starter, not Classic)
- 7.4: the flattened matrix inherited the same wrong numbers on Send Requests, Direct Messages, Who Viewed You, and Notifications — corrected and added footnotes explaining the two known bugs/quirks inline
- 8.3: `ChatService.java`'s one-line description repeated the wrong "Free/Starter/Classic blocked" claim

**New finding, resolved as intended, not a bug** (see section 17): `PROFILE_VIEW_LIMIT`'s `if (limit > 0)` guard means a DB value of `0` (Free and Starter) is read as "unlimited" rather than "blocked" — the opposite of the code's own comment. Discussed with the user: unlimited profile browsing at every tier matches how free/paid tiers typically work in this vertical (browse freely, pay for contact/chat/horoscope), so the code stays as-is — the stale comment describing a blocking design is what should eventually be corrected, not the guard.

**Not changed:** the `STAR_MATCH` BASIC/FULL distinction, `WHATSAPP_SHARE`, and the four badge/visibility features remain DB-configured but functionally unenforced — documented as such rather than fixed, since implementing them wasn't in scope of a documentation pass.

### 2026-07-09 — Self-healing UserSubscriptions: fixes frozen/unenforced request quota for accounts with no subscription row
**Why:** A user reported sending 5 interest requests on a Free account while the "Requests remaining" count stayed frozen at 3 (never decremented) — and the 4th/5th sends weren't even blocked despite the Free plan's `SEND_REQUEST` limit being 3. Traced via direct DB query: `user_697` had **zero** rows in `user_subscriptions`. Broader query found this affects 605 of 657 total users — all bulk-inserted directly into `users`/`user_details` by a test-data seed script (names like `VannFree`, `NaiduFree`, all sharing the identical timestamp `2026-05-21 16:31:24`), bypassing the real `/user/signup` API entirely — which is the ONLY place that auto-assigns a Free-plan `UserSubscriptions` row (added 2026-04-25). Without a subscription row, `getRequestQuota` couldn't resolve a `subId` and defaulted `used=0` forever (frozen count), while the mobile app's send-interest flow also couldn't resolve a `subId` and fell to its "send without quota tracking" fallback (no enforcement).

**Fixed — self-healing instead of one-off data backfill** (so this can't recur from any future bulk seed/import, not just this one):
- `services/UserSubscriptionsService.java` — new `ensureActiveSubscription(Long userId)`: returns the existing ACTIVE subscription, or creates+saves a Free-plan (planId=1) row if none exists (mirrors the exact fields `UserService`'s signup auto-assign sets: `status=ACTIVE`, `startDate=now`, `autoRenew=N`)
- `getActiveUserSubscriptionUserId` now calls `ensureActiveSubscription` instead of returning 404 when no active row exists
- `controllers/UserFeatureUsageController.java` `getRequestQuota` — replaced its manual `subs.isEmpty()` → `findTopByUserIdOrderByCreatedAtDesc` fallback (which didn't handle a user with ZERO subscription rows ever, only an inactive one) with a call to the same `ensureActiveSubscription` helper

**Not done:** no backfill SQL was run for the 605 already-affected accounts — the self-healing kicks in lazily the next time each account's quota is checked or a subscription is looked up (via `getRequestQuota` or `getActiveUserSubscriptionByUserId`), so they'll self-correct on next use without needing a migration.

### 2026-07-09 — Fixed conversation duplicate-key crash on re-sending after a cancel
**Why:** The 2026-07-08 re-send fix below only reused the existing `Conversation` row when the prior `InterestRequest` was REJECTED. But canceling a sent request (`deleteInterestRequest`, called from the mobile app's "Sent By You" tab) deletes only the `InterestRequest` row — the `Conversation` between the pair is left behind. Re-sending after a *cancel* then had no `InterestRequest` row to find, took the "fresh insert" branch, and tried to INSERT a second `Conversation` for the same `(userOne, userTwo)` pair → `SqlExceptionHelper: Duplicate entry '696-705' for key 'conversations.UKkfsvmcclev9k2goead259k5hm'` (500).

**Fixed:** `InterestRequestService.createInterestRequest` now checks `conversationRepository.findByUserOneAndUserTwo(...)` directly — regardless of whether the `InterestRequest` path taken was fresh-insert or REJECTED-reuse — and only inserts a new `Conversation` if none exists; otherwise resets the existing one to `PENDING` and adds a new `ChatEntity` message. The `isResend` flag from the 2026-07-08 fix is now only used for the response message, not the conversation branch.

**Lesson:** a `Conversation` row's lifecycle is decoupled from its `InterestRequest` row's lifecycle — `InterestRequest` rows can be deleted (cancel) or reset (re-send after reject), but nothing ever deletes the `Conversation`. Any future code path that creates an `InterestRequest` must check for an existing `Conversation` independently, not infer it from the `InterestRequest`'s own state.

### 2026-07-08 — Allow re-sending an interest request after a decline
**Why:** `InterestRequestService.createInterestRequest`'s duplicate check (`existsByInterestSendAndInterestReceived`) blocked a new interest request between two users if ANY row already existed between them — including REJECTED ones — permanently. Product decision: allow re-sending after a decline (no cooldown for now).

**Modified:**
- `repositories/InterestRequestRepository.java` — added `findByInterestSendAndInterestReceived(Long, Long)` (returns `Optional<InterestRequest>`, needed to find the existing row to reuse)
- `services/InterestRequestService.java` `createInterestRequest` — now looks up the existing row instead of just checking existence. If it's PENDING or APPROVED, still blocks with `code 400 "Interest request already exists"`. If it's REJECTED, resets that same row back to `PENDING` and reuses it — **a DB-level unique constraint on `(interestSend, interestReceived)`** (see `InterestRequest.java`'s `@UniqueConstraint`) means a second row for the same pair can never be inserted, so a genuine INSERT is not an option for the re-send path. The existing `Conversation` between the pair is also reused (status reset to PENDING) with a new `ChatEntity` message, rather than creating a duplicate conversation.

**Not changed:** `sendInterestWithLimit` (the `/interestRequest/send/{senderId}/{receiverId}` endpoint) has the same `existsByInterestSendAndInterestReceived` duplicate-check gap, but the mobile app doesn't currently call that endpoint (it uses `/interestRequest/sendInterestRequest/{senderId}/{receiverId}` → `createInterestRequest`) — left as-is since it's dead code from the app's perspective today. Apply the same fix there if it's ever wired up.

### 2026-05-06 — CallbackRequest entity + Payment mode toggle (Play Store gating)
**Why:** Google Play Store may reject the app due to QR/UPI payment screen being seen as off-platform billing for digital subscriptions. Strategy: gate the QR view via a `PAYMENT_MODE` keyValue flag. When flag is `CONTACT` (default for Play Store submission), mobile shows a "Talk to a Relationship Manager" view with a callback request form. Once Play Store approves, flip the flag to `QR` via admin panel.

**New files:**
- `models/CallbackRequest.java` — table `callback_requests` with fields `id, userId (nullable), name, mobile, email, planInterested, note, bestTimeToCall, status (NEW/CONTACTED/CONVERTED/CLOSED), assignedAdminId` + GenericEntity timestamps
- `repositories/CallbackRequestRepository.java` — `findByUserIdOrderByIdDesc`, `findByStatusOrderByIdDesc`, `findFirstByUserIdAndPlanInterestedAndStatus` (dedup)
- `services/CallbackRequestService.java` — `createRequest` (validates name + mobile regex `^\+?\d{10,15}$`, dedups NEW requests by user+plan, tolerates `'guest'` encodedUserId), `getMyRequests`, `adminListRequests`, `adminUpdateStatus`
- `controllers/CallbackRequestController.java` — endpoints listed in section 8.4
- `seed-payment-mode.sql` — inserts default `PAYMENT_MODE` (CONTACT) + `ADMIN_CONTACT` rows into keyValue

**No existing files modified.** All wiring is via the existing `KeyValueController.getKeyValueByKey({key})` endpoint — frontend reads `PAYMENT_MODE` and `ADMIN_CONTACT` keys.

**Deploy:**
1. Hibernate auto-creates `callback_requests` table on startup (`ddl-auto=update`)
2. Run `seed-payment-mode.sql` to insert default keyValue rows
3. Frontend mobile build picks up the new endpoints automatically

### 2026-04-09 — PIN dual-format login (base64 + BCrypt)
- `UserService.login` now detects PIN format: strings starting with `$2` are BCrypt-matched via `BCryptPasswordEncoder.matches`, others are base64-decoded. Resolves `Illegal base64 character 24` crash on accounts whose PIN was reset via `AuthService.resetPassword`.
- Base64 decode is wrapped in try/catch so malformed values fall through to family login fall-through instead of 500.

### 2026-04-09 — `/user/login` + family login now issue JWTs
- `UserService.login`: after successful PIN match, generates JWT access token + refresh token via `JwtUtil`, persists `refreshToken` + `refreshTokenExpiry` on `UserEntity`, returns `token` + `refreshToken` in the payload.
- `FamilyLoginService.tryLogin`: same — issues tokens scoped to the child's `userId` (primary user), persists to the child's `UserEntity`, returns `token` + `refreshToken` so parent sessions also authenticate against JWT-protected endpoints.
- `LoginScreen.tsx` already handled `response.data.data.token` / `refreshToken` persistence so no frontend change needed.
- **Resolves**: every `/user/*`, `/user-details/*`, `/userSubscriptions/*`, `/happyStory/*`, `/notifications/*` endpoint was returning 403 because the app had no `authToken` after fresh login.

### 2026-04-08 — Subscription matrix + Service Requests + Family Login (Option A)
**New entities & tables**:
- `models/ServiceRequest.java` + `service_requests` table
- `models/FamilyLogin.java` + `family_logins` table
- `models/SubscriptionPlan.java` — added `tagline` + `planDescription` columns

**New repositories**:
- `repositories/ServiceRequestRepository.java`
- `repositories/FamilyLoginRepository.java`

**New services**:
- `services/ServiceRequestService.java` — feature-gated request flow
- `services/FamilyLoginService.java` — create / list / revoke / `tryLogin` / admin

**New controllers**:
- `controllers/ServiceRequestController.java`
- `controllers/FamilyLoginController.java`

**Modified services**:
- `UserService.login` — falls through to `FamilyLoginService.tryLogin` on miss/PIN mismatch
- `UserService.getProfileDetailWithIntractionStatus` — added PROFILE_VIEW_LIMIT increment, HOROSCOPE_VIEW + SECURE_CONNECT masking; fixed CONTACT_VIEW → VIEW_PERSONAL_INFO bug
- `UserService.getProfileDetailByUserId(viewedUserId, requesterId)` — same masking + new `requesterId` overload
- `UserService.filterUsers` — replaced `subscriptionPlanId != 1` shortcut with proper ADV_SEARCH plan-feature lookup
- `ChatService.sendChatMessage` — MESSAGE plan gate
- `ViewedProfileService.getAllViewers` — WHO_VIEWED gate + viewerLimit subList
- `ShortlistedProfileService.insertShortlistedProfile` — SHORTLIST plan gate (Free blocked)
- `ShortlistedProfileService.getWhoShortlistedMe` ⭐ NEW — Gold+ gate
- `PushNotificationService.sendPushNotificationToUser` — NOTIFICATION_ALERT gate (silent skip for Free + Starter)

**Modified controllers**:
- `MailboxController` — added `GET /whoShortlistedMe/{encodedId}`
- `UserController.getProfileDetailByUserId` — added optional `requesterId` query param

**DB seed**:
- Wiped + reseeded `subscription_plans`, `features`, `planFeatures` from scratch (6 plans, 27 features, full plan_features matrix per tier)

**Latent bug fix**:
- Replaced all `featuresRepository.findByCode("CONTACT_VIEW")` with `findByCode("VIEW_PERSONAL_INFO")` — old code silently granted access because the feature row didn't exist.

## 19. Deferred / not implemented
- **Cashfree refunds** — no refund API call or REFUND webhook handling; refunds are manual in the Cashfree dashboard for now.
- **Admin panel page for `GET /payments/cashfree/admin/orders` (+ reverify / events)** — endpoints exist, no UI.
- **Google Play alternative-billing enrolment + privacy-policy wording** before flipping the app to `PAYMENT_MODE=GATEWAY` — see `CASHFREE_INTEGRATION_PLAN.md` §8.
- **Real in-app voice call** — gated as `ServiceRequest VOICE_CALL`, admin-fulfilled. No WebRTC / Agora / Twilio.
- **Real video profile recording** — gated as `ServiceRequest VIDEO_PROFILE`, admin-fulfilled.
- **Speak with families / Dedicated RM / Family Assisted Matchmaking** — all `ServiceRequest` Platinum-only flows.
- **Admin role check on `/admin/*` and `*/admin/*` endpoints** — currently no enforcement.
- **PIN hashing reconciliation** — `login` (base64) vs `resetPassword` (BCrypt) mismatch.
- **Multi-instance presence** — needs Redis if scaled.

## 20. Session protocol
1. **READ this CLAUDE.md FIRST.** It is the source of truth for this project.
2. **READ the matching CLAUDE.md in sibling repos** when your change crosses the frontend/backend/admin boundary:
   - `/Users/prodian/Documents/Personal/VaibhavVivaahaApp/CLAUDE.md`
   - `/Users/prodian/Documents/Personal/VaibhavVivaahaBackend/CLAUDE.md` (this file)
   - `/Users/prodian/Documents/Personal/adminpanel/CLAUDE.md`
3. **Make your changes.**
4. **UPDATE this file before ending the session** — add new endpoints (section 8.4), new entities (section 8.1), new feature codes (section 7.2), plan IDs (section 7.1), DB tables (section 12), or gating points (section 10). Append a new dated entry to **section 18** describing what changed and why.
5. **If you add a new feature gated by a plan**:
   - Add the feature code to `features` table + section 7.2
   - Add `planFeatures` rows for the relevant tiers
   - Document the gate location in section 10
   - Update the cross-repo contract table in section 16 if a new endpoint is added
   - Mirror the change in the matching CLAUDE.md of the consuming repo (app/admin)
6. **Stale CLAUDE.md = future session breaks.** Don't skip the update.