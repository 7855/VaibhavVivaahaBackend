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
| id | title    | price ₹  | duration_days | tagline (Tamil)              |
|----|----------|----------|---------------|-------------------------------|
| 1  | Free     | 0        | 0 (lifetime)  | உங்கள் பயணம் தொடங்குகிறது      |
| 2  | Starter  | 199      | 30            | முதல் அடி எடுங்கள்             |
| 3  | Classic  | 999      | 90            | தெளிவான தேர்வு                |
| 4  | Silver   | 2499     | 90            | இதயம் திறக்கும் நேரம்          |
| 5  | Gold     | 4999     | 180           | தங்க வாழ்க்கை தொடர்புகள்       |
| 6  | Platinum | 9999     | 9999 (until marriage) | திருமணம் வரை நம்மோட உதவி |

`subscription_plans` columns: `id`, `createdAt`, `createdBy`, `isActive`, `updatedAt`, `updatedBy`, `discount`, `duration_days`, `is_popular`, `original_price`, `period`, `price`, `savings`, `title`, **`tagline`** (added this session), **`plan_description`** (added this session).

### 7.2 Feature codes (`features` table — 27 entries)
| id | code                  | tier introduced |
|----|-----------------------|-----------------|
| 1  | BASIC_SEARCH          | Free            |
| 2  | ADV_SEARCH            | Classic         |
| 3  | VIEW_PROFILE_DETAILS  | Free (LIMITED) / Starter+ (FULL) |
| 4  | SEND_REQUEST          | Free (3) / Starter (15) / Classic (50) / Silver+ (∞) |
| 5  | REQ_LIMITED           | Starter (15) / Classic (50) |
| 6  | REQ_UNLIMITED         | Silver+         |
| 7  | MESSAGE               | Silver+         |
| 8  | VIEW_PERSONAL_INFO    | Classic+        |
| 9  | SHORTLIST             | Starter+        |
| 10 | WHO_VIEWED            | Silver+         |
| 11 | VERIFY_BADGE          | Silver+         |
| 12 | HIGH_VISIBILITY       | Silver+         |
| 13 | NOTIFICATION_ALERT    | Classic+        |
| 14 | WHATSAPP_SHARE        | Gold+           |
| 15 | SPEAK_FAMILY          | Platinum        |
| 16 | HOROSCOPE_VIEW        | Classic+        |
| 17 | STAR_MATCH            | Classic (BASIC) / Silver+ (FULL) |
| 18 | SECURE_CONNECT        | Silver+         |
| 19 | VOICE_CALL            | Silver+         |
| 20 | VIDEO_PROFILE         | Gold+           |
| 21 | FAMILY_LOGIN          | Gold+           |
| 22 | WHO_SHORTLISTED_YOU   | Gold+           |
| 23 | PRIORITY_SEARCH       | Gold+           |
| 24 | PRIME_VERIFIED        | Silver+         |
| 25 | DEDICATED_RM          | Platinum        |
| 26 | FAMILY_ASSISTED_MATCH | Platinum        |
| 27 | PROFILE_VIEW_LIMIT    | Free (0) / Starter (20) / Classic (100) / Silver+ (∞) |

### 7.3 `planFeatures` table (camelCase!)
Columns: `id`, `createdAt`, `createdBy`, `isActive`, `updatedAt`, `updatedBy`, **`featureId`**, **`limit_value`** (varchar), **`subscriptionPlanId`**, **`limit_period`** ('DAY','MONTH','PLAN_DURATION').

Per-tier feature assignments (the canonical 6-tier × 23-feature matrix is materialized as rows here — see DB seed in section 12).

### 7.4 Plan × Feature matrix (source of truth)
| Feature | Free | Starter ₹199 | Classic ₹999 | Silver ₹2.5k | Gold ₹5k | Platinum ₹10k |
|---|---|---|---|---|---|---|
| Validity | Lifetime | 30d | 3mo | 3mo | 6mo | Till Marriage |
| Profile Views | Blurred | 20 full | 100 | ∞ | ∞ | ∞ |
| Basic Search | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| Advanced Filters | ✖ | ✖ | ✔ | ✔ | ✔ | ✔ |
| Horoscope/Jathagam | ✖ | ✖ | ✔ | ✔ | ✔ | ✔ |
| Star Match Score | ✖ | ✖ | Basic | Full | Full | Full |
| Send Requests | 3 | 15 | 50 | ∞ | ∞ | ∞ |
| Direct Messages | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ |
| In-App Voice Call | ✖ | ✖ | ✖ | ✔ (req) | ✔ (req) | ✔ (req) |
| SecureConnect (mask phone) | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ |
| Shortlist | ✖ | ✔ | ✔ | ✔ | ✔ | ✔ |
| Who Viewed You | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ |
| Verification Badge | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ |
| Prime Verified | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ |
| High Visibility | ✖ | ✖ | ✖ | ✔ | ✔ | ✔ |
| Notifications | ✖ | ✖ | ✔ | ✔ | ✔ | ✔ |
| Video Profile | ✖ | ✖ | ✖ | ✖ | ✔ (req) | ✔ (req) |
| Who Shortlisted You | ✖ | ✖ | ✖ | ✖ | ✔ | ✔ |
| Priority Search | ✖ | ✖ | ✖ | ✖ | ✔ | ✔ |
| Family / Parent Login | ✖ | ✖ | ✖ | ✖ | ✔ | ✔ |
| WhatsApp Profile Share | ✖ | ✖ | ✖ | ✖ | ✔ | ✔ |
| Speak With Families | ✖ | ✖ | ✖ | ✖ | ✖ | ✔ (req) |
| Dedicated RM | ✖ | ✖ | ✖ | ✖ | ✖ | ✔ (req) |
| Family Assisted Matchmaking | ✖ | ✖ | ✖ | ✖ | ✖ | ✔ (req) |

"req" = service-request flow (admin fulfilled). See section 11.

## 8. File-by-file inventory

### 8.1 `models/` (entities)
| File | Table | Key fields | Notes |
|---|---|---|---|
| `GenericEntity.java` | (mapped superclass) | createdAt, createdBy, isActive ('Y'/'N'), updatedAt, updatedBy | base for almost everything |
| `UserEntity.java` | `users` | userId, mobile, email, pin (base64!), refreshToken, refreshTokenExpiry, resetToken, resetTokenExpiry, otp, otpCreatedAt, firstName, lastName, gender, dob, profileImage, casteId, location, isUser, isOnline, lastSeen, profileCreated, userStatus, rejectionReason | core profile |
| `UserDetailEntity.java` | `user_detail` | userId, height, weight, languages, degree, occupation, employedAt, annualIncome, presentAddress, horoscope, basicInfo (json), astronomicInfo (json), familyInfo (json), educationInfo (json) | extended profile |
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
| `ChatService.java` ⭐ | `sendChatMessage(request)` — MESSAGE feature gate (Free/Starter/Classic blocked); plus all chat CRUD |
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
| `UserDetailService.java` | user_detail CRUD |
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
| **PROFILE_VIEW_LIMIT** | `UserService.getProfileDetailWithIntractionStatus` — increments `UserFeatureUsage` for plans 1/2/3, unlimited for plans 4/5/6 | `403 PROFILE_VIEW_BLURRED` (limit=0) or `403 PROFILE_VIEW_LIMIT_EXCEEDED` |
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

## 18. Recent changes log (rolling, newest first)
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