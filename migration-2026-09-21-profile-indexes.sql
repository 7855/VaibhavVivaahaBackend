-- ─────────────────────────────────────────────────────────────────────────────
--  Profile / discovery indexes — 2026-09-21
--
--  The 2026-08-10 batch indexed users, interestRequest, shortlisted_profiles,
--  viewedProfile, conversations and chats. It indexed NONE of the tables a
--  profile page actually reads. Every one below is hit by lookups that are
--  currently FULL TABLE SCANS.
--
--  Run alongside migration-2026-09-21-chat-indexes.sql. Same rules: online,
--  reversible, safe to re-run (ERROR 1061 = already there).
-- ─────────────────────────────────────────────────────────────────────────────

-- STEP 0 — sizes, so you can tell whether this is your bottleneck.
SELECT 'user_details' AS tbl, COUNT(*) AS rows_now FROM user_details
UNION ALL SELECT 'gallery', COUNT(*) FROM gallery
UNION ALL SELECT 'userLikes', COUNT(*) FROM userLikes
UNION ALL SELECT 'restricted_field_requests', COUNT(*) FROM restricted_field_requests
UNION ALL SELECT 'user_feature_usage', COUNT(*) FROM user_feature_usage;


-- ─────────────────────────────────────────────────────────────────────────────
-- 1. user_details.userId — THE IMPORTANT ONE. Do this even if you skip the rest.
--
-- 🔴 `userId` is NOT the primary key. UserDetailEntity has its own
-- @GeneratedValue `id`, and `userId` is a plain unindexed column. So every
-- `userDetailRepository.findByUserId(...)` scans the whole table — and that
-- call sits on essentially every profile read, every profile save, and every
-- search result assembly.
--
-- Worse, UserService.getInterestMatches (Home's "Interest-Based Matches" shelf)
-- calls it INSIDE A LOOP over every candidate:
--     for (UserEntity u : allUsers) { userDetailRepository.findByUserId(...) }
-- With ~600 profiles that is 600 scans of a 600-row table ≈ 360,000 row reads
-- to draw one shelf. This index turns each scan into a single seek.
--
-- UNIQUE is deliberate: one detail row per member is the real rule, and the
-- constraint stops a duplicate ever being written. If this errors with 1062,
-- duplicates already exist — find them with the query below, fix them, then
-- re-run. Do NOT downgrade it to a plain INDEX to make the error go away.
--     SELECT userId, COUNT(*) c FROM user_details GROUP BY userId HAVING c > 1;
ALTER TABLE `user_details`
  ADD UNIQUE INDEX `uq_user_details_user` (`userId`),
  ALGORITHM=INPLACE, LOCK=NONE;


-- 2. gallery.userId — the photo grid on every profile, own and other.
ALTER TABLE `gallery`
  ADD INDEX `idx_gallery_user` (`userId`, `isActive`),
  ALGORITHM=INPLACE, LOCK=NONE;


-- 3. userLikes — "has this viewer liked this profile" runs on every profile
-- open; "who liked me" is a Starter+ feature screen.
ALTER TABLE `userLikes`
  ADD INDEX `idx_likes_by_to` (`likedBy`, `likedTo`),
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `userLikes`
  ADD INDEX `idx_likes_to` (`likedTo`),
  ALGORITHM=INPLACE, LOCK=NONE;


-- 4. restricted_field_requests — the profile screen asks, for every hidden
-- field (mobile, photo, horoscope), whether this viewer already requested it.
ALTER TABLE `restricted_field_requests`
  ADD INDEX `idx_rfr_by_to` (`requestedBy`, `requestedTo`, `fieldType`),
  ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE `restricted_field_requests`
  ADD INDEX `idx_rfr_to_status` (`requestedTo`, `status`),
  ALGORITHM=INPLACE, LOCK=NONE;


-- 5. user_feature_usage — read AND written on every quota-gated action
-- (send interest, reveal contact, profile view). A scan here is on the write
-- path, so it slows the action itself, not just a list.
ALTER TABLE `user_feature_usage`
  ADD INDEX `idx_ufu_user_feature` (`userId`, `featureId`),
  ALGORITHM=INPLACE, LOCK=NONE;


-- ─────────────────────────────────────────────────────────────────────────────
-- VERIFY
-- ─────────────────────────────────────────────────────────────────────────────
EXPLAIN SELECT * FROM user_details WHERE userId = 706;
--   before: type=ALL, rows≈<table size>     after: type=const/ref, rows=1
SHOW INDEX FROM `user_details`;


-- ─────────────────────────────────────────────────────────────────────────────
-- ROLLBACK
-- ─────────────────────────────────────────────────────────────────────────────
-- ALTER TABLE `user_details`              DROP INDEX `uq_user_details_user`;
-- ALTER TABLE `gallery`                   DROP INDEX `idx_gallery_user`;
-- ALTER TABLE `userLikes`                 DROP INDEX `idx_likes_by_to`;
-- ALTER TABLE `userLikes`                 DROP INDEX `idx_likes_to`;
-- ALTER TABLE `restricted_field_requests` DROP INDEX `idx_rfr_by_to`;
-- ALTER TABLE `restricted_field_requests` DROP INDEX `idx_rfr_to_status`;
-- ALTER TABLE `user_feature_usage`        DROP INDEX `idx_ufu_user_feature`;


-- ─────────────────────────────────────────────────────────────────────────────
-- ⚠️ AN INDEX DOES NOT FIX THE N+1 IN getInterestMatches.
--
-- It turns 600 scans into 600 seeks — far faster, but still 600 round trips to
-- the database to draw one shelf. The real fix is to fetch the detail rows in
-- ONE query:
--     List<UserDetailEntity> findByUserIdIn(Collection<Long> userIds);
-- then build a Map<Long, UserDetailEntity> and look up inside the loop. That is
-- a code change, not a migration — raised separately so this file stays a pure,
-- reversible schema change.
-- ─────────────────────────────────────────────────────────────────────────────
