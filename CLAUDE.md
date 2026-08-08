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
- **Razorpay SDK** — payments
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
- Razorpay: `razorpay.key.id=rzp_test_...`, `razorpay.key.secret=...`
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
| `PoruthamService.java` | Tamil porutham (matching) calc — uses RasiLordMapper, StarInfo |
| `CasteService.java` | master caste list |
| `PremiumFeatureService.java` | LEGACY — premium_features table; do NOT extend, use Features/PlanFeatures |
| `AdminService.java` | admin-only user list, approve/reject profile, dashboard counts |
| `ServiceRequestService.java` ⭐ NEW | `createRequest(encodedUserId, requestType, note)` — plan-feature gate; `getMyRequests(encodedUserId, page, size)`; `adminListRequests(status, requestType, page, size)`; `adminUpdateStatus(id, newStatus, adminId)` |
| `FamilyLoginService.java` ⭐ NEW | `createFamilyLogin(encodedPrimaryUserId, FamilyLogin)` — Gold+ gate via FAMILY_LOGIN feature; `listMine(encodedPrimaryUserId)`; `revoke(encodedPrimaryUserId, familyLoginId)` (ownership check); `tryLogin(mobile, plainPin)` — returns child's payload + `role: "PARENT"` + `familyLoginId` + parent meta; `adminList()`; `adminRevoke(id)` |

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
- `RasiLordMapper` + `StarInfo` — porutham helpers

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

## 17. Known landmines
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
- **`PoruthamService`'s calculation was substantially rewritten 2026-07-17 — see that changelog entry for the full list of what changed.** Historical context: audited 2026-07-15 (user question about calculation correctness), sharpened 2026-07-17 against a user-supplied 10-Porutham reference (found the missing Vedhai porutham, the Dina inversion, and more), then fixed the same day. Residual, lower-confidence items not independently re-verified after the fix (spot-checked via manual reasoning, not a live astrologer/test-suite): the `RAJJU` table's mirrored-9-star-cycle pattern (internally self-consistent against a from-memory reference, but not cross-checked against a second independent source); `VASYA_GROUP`'s simplification of Simha/Dhanusu/Makaram to one category each (the fuller classical system splits these three by decan, which this endpoint can't do without birth-degree data it doesn't collect); the exact Vasya inter-group compatibility rules (Chatushpada+Vanachara, Manava+Jalachara) beyond same-group. If a real astrologer disputes a specific result, these three are the first places to check.

## 18. Recent changes log (rolling, newest first)
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