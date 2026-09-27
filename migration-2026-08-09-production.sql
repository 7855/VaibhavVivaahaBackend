-- =====================================================================================
-- VVM PRODUCTION MIGRATION — 2026-08-09 session
-- Target database: vaibhavVivaaha
-- =====================================================================================
--
-- RUNS IN ANY SQL CLIENT. This file contains NO `DELIMITER` command and NO stored
-- procedures — those only work in the `mysql` command-line client, and silently fail in
-- Workbench / DBeaver / phpMyAdmin / hosting consoles (symptom: "PROCEDURE ... does not
-- exist"). Conditional DDL is done with prepared statements instead, which every client
-- supports.
--
-- SAFE TO RE-RUN — every step checks information_schema first and prints "skipped: ..."
-- rather than erroring.
--
-- ─────────────────────────────────────────────────────────────────────────────────────
-- WHICH PARTS DO YOU ACTUALLY NEED?
-- ─────────────────────────────────────────────────────────────────────────────────────
-- Hibernate (`spring.jpa.hibernate.ddl-auto=update`) creates TABLES and COLUMNS from
-- entities on backend startup, but NEVER creates indexes, foreign keys, or data.
--
--   PART A  Seed data (27 subcastes)   -> ALWAYS REQUIRED. Hibernate never inserts data.
--                                         Without this the subcaste dropdowns are empty.
--   PART B  Table + column             -> Only needed if you run this BEFORE deploying the
--                                         backend. If you deploy first, Hibernate already
--                                         made them and this part just prints "skipped".
--   PART C  Indexes + foreign key      -> ALWAYS REQUIRED (Hibernate never creates these).
--                                         Pure performance/integrity — no feature breaks
--                                         without them, but every hot query table-scans.
--
-- So: deploy backend first -> you still need PART A and PART C.
--     run this first       -> it does everything; Hibernate then finds it all in place.
-- Either order works.
--
-- USAGE
--   1. Back up:  mysqldump -u <user> -p vaibhavVivaaha > backup-before-migration.sql
--   2. Run this whole file. No database argument needed — the USE below selects it.
-- =====================================================================================


USE `vaibhavVivaaha`;


-- ─────────────────────────────────────────────────────────────────────────────────────
-- PRE-FLIGHT — confirm the target before anything is changed.
-- `target_database` must read vaibhavvivaaha, and charset must be utf8mb4.
-- The caste ids below MUST be 1=VANNIYAR 2=NAIDU 3=AADITRAVIDAR 4=MUDALIAR, because
-- PART A's seed hardcodes them. If they differ, edit the casteId values in PART A.
-- ─────────────────────────────────────────────────────────────────────────────────────
SELECT DATABASE() AS target_database, VERSION() AS mysql_version,
       @@character_set_database AS charset, @@collation_database AS db_collation;

SELECT id, casteCode, casteName FROM castes ORDER BY id;


-- =====================================================================================
-- PART B — TABLE + COLUMN  (skipped automatically if Hibernate already created them)
-- Placed before PART A because the seed inserts into `subcastes`.
-- =====================================================================================

-- CHARSET pinned to utf8mb4 (needed for Tamil / subcaste text). COLLATION deliberately NOT
-- pinned: utf8mb4_0900_ai_ci exists only on MySQL 8+, so hardcoding it would break a 5.7
-- server. Omitting it inherits the server default, correct on both.
CREATE TABLE IF NOT EXISTS `subcastes` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT,
    `createdAt`    DATETIME(6)   DEFAULT NULL,
    `createdBy`    VARCHAR(255)  DEFAULT NULL,
    `isActive`     ENUM('Y','N') DEFAULT 'Y',
    `updatedAt`    DATETIME(6)   DEFAULT NULL,
    `updatedBy`    VARCHAR(255)  DEFAULT NULL,
    `casteId`      BIGINT        NOT NULL,
    `subcasteName` VARCHAR(100)  NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- users.subcasteId — nullable, so every existing profile is unaffected.
SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'subcasteId'),
    'SELECT ''skipped: users.subcasteId already exists'' AS note',
    'ALTER TABLE `users` ADD COLUMN `subcasteId` BIGINT NULL'));
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;


-- =====================================================================================
-- PART A — SEED DATA (23 client-supplied names + 'Others' for the 4 castes that have
-- subcastes = 27 rows; FREE CASTE BAR deliberately gets none)
-- ALWAYS REQUIRED — Hibernate never inserts data.
-- casteId: 1=VANNIYAR 2=NAIDU 3=AADITRAVIDAR 4=MUDALIAR. FREECASTEBAR (5) has none by
-- design ("no caste preference") apart from its own 'Others'.
-- Re-running inserts nothing (NOT EXISTS guard).
-- =====================================================================================

INSERT INTO subcastes (casteId, subcasteName, isActive, createdAt, createdBy)
SELECT * FROM (
    SELECT 1 AS casteId, 'Padayachi' AS subcasteName, 'Y' AS isActive, NOW(6) AS createdAt, 'seed' AS createdBy UNION ALL
    SELECT 1, 'Gounder / Naicker',         'Y', NOW(6), 'seed' UNION ALL
    SELECT 1, 'Palli',                     'Y', NOW(6), 'seed' UNION ALL
    SELECT 1, 'Kandar',                    'Y', NOW(6), 'seed' UNION ALL
    SELECT 1, 'Mazhavarayar / Kachirayar', 'Y', NOW(6), 'seed' UNION ALL

    SELECT 2, 'Kamma',                     'Y', NOW(6), 'seed' UNION ALL
    SELECT 2, 'Balija',                    'Y', NOW(6), 'seed' UNION ALL
    SELECT 2, 'Gavara',                    'Y', NOW(6), 'seed' UNION ALL
    SELECT 2, 'Tholuva',                   'Y', NOW(6), 'seed' UNION ALL
    SELECT 2, 'Kapu',                      'Y', NOW(6), 'seed' UNION ALL
    SELECT 2, 'Kambalathu',                'Y', NOW(6), 'seed' UNION ALL

    SELECT 3, 'Valluva / Valluvan',        'Y', NOW(6), 'seed' UNION ALL
    SELECT 3, 'Thoti / Vettiyan',          'Y', NOW(6), 'seed' UNION ALL
    SELECT 3, 'Kavalkaran',                'Y', NOW(6), 'seed' UNION ALL
    SELECT 3, 'Samban / Sambavar',         'Y', NOW(6), 'seed' UNION ALL
    SELECT 3, 'Tangalam & Goliya',         'Y', NOW(6), 'seed' UNION ALL

    SELECT 4, 'Thuluva Vellalar',          'Y', NOW(6), 'seed' UNION ALL
    SELECT 4, 'Thondaimandala Vellalar',   'Y', NOW(6), 'seed' UNION ALL
    SELECT 4, 'Saiva Vellalar',            'Y', NOW(6), 'seed' UNION ALL
    SELECT 4, 'Poonamallee Vellalar',      'Y', NOW(6), 'seed' UNION ALL
    SELECT 4, 'Sengunthar Kaikolar',       'Y', NOW(6), 'seed' UNION ALL
    SELECT 4, 'Agamudaiya',                'Y', NOW(6), 'seed' UNION ALL
    SELECT 4, 'Isai Vellala',              'Y', NOW(6), 'seed'
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM subcastes s
    WHERE s.casteId = seed.casteId AND s.subcasteName = seed.subcasteName
);


-- "Others" — added ONLY to castes that actually have named subcastes (Vanniyar, Naidu,
-- Aadi Dravidar, Mudaliar). It is the "I'm not sure / not listed" escape hatch, which is what
-- makes the field safe to REQUIRE at registration: nobody is ever forced to guess.
--
-- Deliberately NOT added to FREE CASTE BAR. That is a caste-LEVEL choice meaning "any caste is
-- fine by me" — it has no subcastes at all, and offering an "Others" subcaste underneath it
-- would just confuse people. Signup handles this by only requiring subcaste when the chosen
-- caste actually has one (see UserService.createUser and sign-up.tsx validateFormData).
--
-- Search treats "Others" specially — see the UserRepository docs: a search for a specific
-- subcaste still RETURNS these members, ranked after the exact matches.
-- Driven off the data, so a caste given subcastes later automatically gets its own "Others".
INSERT INTO subcastes (casteId, subcasteName, isActive, createdAt, createdBy)
SELECT c.id, 'Others', 'Y', NOW(6), 'seed'
FROM castes c
WHERE c.isActive = 'Y'
  AND EXISTS     (SELECT 1 FROM subcastes s2 WHERE s2.casteId = c.id AND s2.subcasteName <> 'Others')
  AND NOT EXISTS (SELECT 1 FROM subcastes s  WHERE s.casteId  = c.id AND s.subcasteName  = 'Others');


-- =====================================================================================
-- PART C — INDEXES + FOREIGN KEY
-- ALWAYS REQUIRED. Hibernate never creates these, so deploying the backend does not
-- bring them across. Before these, chats / notifications / viewedProfile /
-- user_subscriptions / shortlisted_profiles / blockedUsers / conversations had ONLY
-- primary keys — every hot query was a full table scan. Measured locally (EXPLAIN cost):
--   chats WHERE conversationId               26   -> 0.7
--   notifications WHERE receiverId ORDER BY  28.6 -> 3.85  (filesort eliminated)
--   interestRequest WHERE interestReceived   16.6 -> 0.35
--   user_subscriptions WHERE userId+status   7.1  -> 0.35
-- Production has far more rows, so the real gain is larger.
--
-- NOT included: idx_callback_* / idx_sr_* — they pre-date this session (CallbackRequest
-- carries @Index annotations, so Hibernate creates those itself).
-- =====================================================================================

-- chats.idx_chats_conv
SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'chats' AND index_name = 'idx_chats_conv'),
    'SELECT ''skipped: idx_chats_conv already exists'' AS note',
    'ALTER TABLE `chats` ADD INDEX `idx_chats_conv` (`conversationId`, `isActive`, `isRead`)'));
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

-- notifications.idx_notif_receiver
SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'notifications' AND index_name = 'idx_notif_receiver'),
    'SELECT ''skipped: idx_notif_receiver already exists'' AS note',
    'ALTER TABLE `notifications` ADD INDEX `idx_notif_receiver` (`receiverId`, `createdAt`)'));
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

-- users.idx_users_discovery
SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'users' AND index_name = 'idx_users_discovery'),
    'SELECT ''skipped: idx_users_discovery already exists'' AS note',
    'ALTER TABLE `users` ADD INDEX `idx_users_discovery` (`casteId`, `gender`, `isActive`, `userStatus`)'));
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

-- viewedProfile.idx_viewed_user
SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'viewedProfile' AND index_name = 'idx_viewed_user'),
    'SELECT ''skipped: idx_viewed_user already exists'' AS note',
    'ALTER TABLE `viewedProfile` ADD INDEX `idx_viewed_user` (`userIdValue`)'));
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

-- viewedProfile.idx_viewed_by
SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'viewedProfile' AND index_name = 'idx_viewed_by'),
    'SELECT ''skipped: idx_viewed_by already exists'' AS note',
    'ALTER TABLE `viewedProfile` ADD INDEX `idx_viewed_by` (`viewedBy`)'));
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

-- user_subscriptions.idx_usub_user
SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'user_subscriptions' AND index_name = 'idx_usub_user'),
    'SELECT ''skipped: idx_usub_user already exists'' AS note',
    'ALTER TABLE `user_subscriptions` ADD INDEX `idx_usub_user` (`userId`, `status`)'));
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

-- interestRequest.idx_ir_received
SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'interestRequest' AND index_name = 'idx_ir_received'),
    'SELECT ''skipped: idx_ir_received already exists'' AS note',
    'ALTER TABLE `interestRequest` ADD INDEX `idx_ir_received` (`interestReceived`)'));
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

-- interestRequest.idx_ir_send
SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'interestRequest' AND index_name = 'idx_ir_send'),
    'SELECT ''skipped: idx_ir_send already exists'' AS note',
    'ALTER TABLE `interestRequest` ADD INDEX `idx_ir_send` (`interestSend`)'));
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

-- blockedUsers.idx_block_by
SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'blockedUsers' AND index_name = 'idx_block_by'),
    'SELECT ''skipped: idx_block_by already exists'' AS note',
    'ALTER TABLE `blockedUsers` ADD INDEX `idx_block_by` (`blockedByUserId`)'));
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

-- blockedUsers.idx_block_target
SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'blockedUsers' AND index_name = 'idx_block_target'),
    'SELECT ''skipped: idx_block_target already exists'' AS note',
    'ALTER TABLE `blockedUsers` ADD INDEX `idx_block_target` (`blockedUserId`)'));
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

-- shortlisted_profiles.idx_shortlist_by
SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'shortlisted_profiles' AND index_name = 'idx_shortlist_by'),
    'SELECT ''skipped: idx_shortlist_by already exists'' AS note',
    'ALTER TABLE `shortlisted_profiles` ADD INDEX `idx_shortlist_by` (`shortlistedBy`, `isActive`)'));
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

-- shortlisted_profiles.idx_shortlist_target
SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'shortlisted_profiles' AND index_name = 'idx_shortlist_target'),
    'SELECT ''skipped: idx_shortlist_target already exists'' AS note',
    'ALTER TABLE `shortlisted_profiles` ADD INDEX `idx_shortlist_target` (`shortlistedUserId`, `isActive`)'));
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

-- conversations.idx_conv_u1
SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'conversations' AND index_name = 'idx_conv_u1'),
    'SELECT ''skipped: idx_conv_u1 already exists'' AS note',
    'ALTER TABLE `conversations` ADD INDEX `idx_conv_u1` (`user_one_id`)'));
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

-- conversations.idx_conv_u2
SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'conversations' AND index_name = 'idx_conv_u2'),
    'SELECT ''skipped: idx_conv_u2 already exists'' AS note',
    'ALTER TABLE `conversations` ADD INDEX `idx_conv_u2` (`user_two_id`)'));
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

-- subcastes.idx_subcaste_caste
SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'subcastes' AND index_name = 'idx_subcaste_caste'),
    'SELECT ''skipped: idx_subcaste_caste already exists'' AS note',
    'ALTER TABLE `subcastes` ADD INDEX `idx_subcaste_caste` (`casteId`, `isActive`)'));
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

-- users.idx_users_subcaste
SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'users' AND index_name = 'idx_users_subcaste'),
    'SELECT ''skipped: idx_users_subcaste already exists'' AS note',
    'ALTER TABLE `users` ADD INDEX `idx_users_subcaste` (`casteId`, `subcasteId`)'));
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

-- Foreign key subcastes.casteId -> castes.id
SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.table_constraints
           WHERE table_schema = DATABASE() AND table_name = 'subcastes'
             AND constraint_name = 'fk_subcaste_caste'),
    'SELECT ''skipped: fk_subcaste_caste already exists'' AS note',
    'ALTER TABLE `subcastes` ADD CONSTRAINT `fk_subcaste_caste` FOREIGN KEY (`casteId`) REFERENCES `castes` (`id`)'));
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;


-- =====================================================================================
-- VERIFICATION — expect: 27 subcastes (6/7/6/8/0 incl. 'Others'), subcasteId column = 1, 16 index
-- rows all reading OK.
-- =====================================================================================

SELECT c.id, c.casteName, COUNT(s.id) AS subcastes
FROM castes c LEFT JOIN subcastes s ON c.id = s.casteId
GROUP BY c.id, c.casteName ORDER BY c.id;

SELECT COUNT(*) AS total_subcastes FROM subcastes;

SELECT COUNT(*) AS users_subcasteId_column
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'subcasteId';

-- Compares ACTUAL index columns against expected. The guards above match on index NAME
-- only, so an index that already exists under the same name with different columns would
-- be skipped silently — this surfaces that.
SELECT s.table_name, s.index_name,
       GROUP_CONCAT(s.column_name ORDER BY s.seq_in_index) AS actual_columns,
       e.expected_columns,
       CASE WHEN GROUP_CONCAT(s.column_name ORDER BY s.seq_in_index) = e.expected_columns
            THEN 'OK' ELSE '*** MISMATCH - DROP AND RECREATE THIS INDEX ***' END AS status
FROM information_schema.statistics s
JOIN (
    SELECT 'idx_chats_conv' AS idx, 'conversationId,isActive,isRead' AS expected_columns UNION ALL
    SELECT 'idx_notif_receiver',    'receiverId,createdAt'               UNION ALL
    SELECT 'idx_users_discovery',   'casteId,gender,isActive,userStatus' UNION ALL
    SELECT 'idx_viewed_user',       'userIdValue'                        UNION ALL
    SELECT 'idx_viewed_by',         'viewedBy'                           UNION ALL
    SELECT 'idx_usub_user',         'userId,status'                      UNION ALL
    SELECT 'idx_ir_received',       'interestReceived'                   UNION ALL
    SELECT 'idx_ir_send',           'interestSend'                       UNION ALL
    SELECT 'idx_block_by',          'blockedByUserId'                    UNION ALL
    SELECT 'idx_block_target',      'blockedUserId'                      UNION ALL
    SELECT 'idx_shortlist_by',      'shortlistedBy,isActive'             UNION ALL
    SELECT 'idx_shortlist_target',  'shortlistedUserId,isActive'         UNION ALL
    SELECT 'idx_conv_u1',           'user_one_id'                        UNION ALL
    SELECT 'idx_conv_u2',           'user_two_id'                        UNION ALL
    SELECT 'idx_subcaste_caste',    'casteId,isActive'                   UNION ALL
    SELECT 'idx_users_subcaste',    'casteId,subcasteId'
) e ON e.idx = s.index_name
WHERE s.table_schema = DATABASE()
GROUP BY s.table_name, s.index_name, e.expected_columns
ORDER BY s.table_name, s.index_name;


-- =====================================================================================
-- PART D — RETIRE STARTER + CLASSIC PLANS   *** DO NOT RUN YET ***
-- =====================================================================================
-- Left commented on purpose. This is a business change, and ORDER MATTERS: run it only
-- AFTER the new mobile app release is live. Users on the current build still have the old
-- hardcoded plan list, so they would keep requesting plans you no longer sell.
--
-- Existing subscribers are unaffected either way: entitlements resolve from planFeatures
-- by plan id and never read isActive (as of 2026-08-09 that is 2 active Starter and 10
-- active Classic members, who keep everything until endDate). isActive only controls
-- whether a plan can be SOLD; the backend now also rejects new subscriptions on a retired
-- plan with 400 SUBSCRIPTION_PLAN_INACTIVE.
--
-- Check for in-flight payments first:
--   SELECT id, status, planId FROM PAYMENT_REQUESTS WHERE planId IN (2,3) AND status='PENDING';
--
-- Retire:      UPDATE subscription_plans SET isActive='N' WHERE id IN (2,3);
-- Bring back:  UPDATE subscription_plans SET isActive='Y' WHERE id IN (2,3);
--              (fully symmetric - no code change, no app release; the app picks it up
--               within its 30-min plan-catalog cache TTL, or instantly on cold start)
-- =====================================================================================
