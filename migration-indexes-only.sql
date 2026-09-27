-- =====================================================================================
-- VVM — INDEXES + FOREIGN KEY ONLY  (no data, no table creation)
-- Target database: vaibhavVivaaha
-- =====================================================================================
--
-- This is PART C of migration-2026-08-09-production.sql, split out on request.
--
-- >>> THIS FILE DOES NOT INSERT THE SUBCASTE DATA. <<<
-- Indexes alone do not make the subcaste feature work — without the 27 rows from
-- PART A the dropdowns are empty in signup, search and profile. Run the full
-- migration file, or run this plus the data insert, not this on its own.
--
-- Hibernate (ddl-auto=update) creates tables and columns from entities but NEVER
-- creates indexes or foreign keys — verified by building a schema from scratch:
-- it produced 0 of the 16 indexes below. That is why this must be run by hand.
--
-- SAFE TO RE-RUN. Each step checks information_schema first and prints "skipped: ..."
-- instead of erroring. No DELIMITER / stored procedures, so it works in the mysql CLI
-- and in Workbench / DBeaver / phpMyAdmin alike.
--
-- ⚠️ TIMING: ALTER TABLE ... ADD INDEX rebuilds the table on older MySQL and can hold
-- a metadata lock on large tables. MySQL 5.6+ does this online for most cases, but on
-- a busy production server prefer a low-traffic window. `chats`, `notifications` and
-- `users` are the biggest tables here.
--
-- USAGE
--   mysqldump -u <user> -p vaibhavVivaaha > backup.sql        <-- back up first
--   mysql    -u <user> -p < migration-indexes-only.sql
-- =====================================================================================


USE `vaibhavVivaaha`;


-- ─────────────────────────────────────────────────────────────────────────────────────
-- PRE-FLIGHT — must read vaibhavvivaaha before anything is altered.
-- ─────────────────────────────────────────────────────────────────────────────────────
SELECT DATABASE() AS target_database, VERSION() AS mysql_version;


-- =====================================================================================
-- 1. PERFORMANCE INDEXES (14)
-- Before these, chats / notifications / viewedProfile / user_subscriptions /
-- shortlisted_profiles / blockedUsers / conversations carried ONLY primary keys, so
-- every hot query was a full table scan. Measured locally with EXPLAIN (cost before ->
-- after): chats 26 -> 0.7 · notifications 28.6 -> 3.85 (filesort eliminated) ·
-- interestRequest 16.6 -> 0.35 · user_subscriptions 7.1 -> 0.35.
--
-- NOT included: idx_callback_* and idx_sr_* — those pre-date this work (CallbackRequest
-- carries @Index annotations, so Hibernate creates them itself).
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


-- =====================================================================================
-- 2. SUBCASTE INDEXES + FOREIGN KEY (2 indexes + 1 FK)
-- The table and column themselves are created by Hibernate on backend startup; these
-- are not, because the entity carries no index/FK metadata.
-- =====================================================================================

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

-- subcastes.fk_subcaste_caste -> castes(id)
SET @sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.table_constraints
           WHERE table_schema = DATABASE() AND table_name = 'subcastes'
             AND constraint_name = 'fk_subcaste_caste'),
    'SELECT ''skipped: fk_subcaste_caste already exists'' AS note',
    'ALTER TABLE `subcastes` ADD CONSTRAINT `fk_subcaste_caste` FOREIGN KEY (`casteId`) REFERENCES `castes` (`id`)'));
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;


-- =====================================================================================
-- 3. INDEX MAPPING / VERIFICATION
-- Expect 16 rows, every `status` reading OK.
--
-- This compares the ACTUAL indexed columns against what is expected. The guards above
-- match on index NAME only, so an index already present under the same name but with
-- different columns would be skipped silently — this surfaces exactly that case.
-- A MISMATCH row means: DROP that index and re-run this file.
-- =====================================================================================

SELECT s.table_name,
       s.index_name,
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

-- Foreign key check — expect 1 row.
SELECT constraint_name, table_name
FROM information_schema.table_constraints
WHERE table_schema = DATABASE() AND table_name = 'subcastes' AND constraint_type = 'FOREIGN KEY';

-- Reminder: this file did NOT insert subcaste data.
SELECT COUNT(*) AS subcaste_rows_present,
       CASE WHEN COUNT(*) = 0
            THEN 'EMPTY - run the data insert (PART A of migration-2026-08-09-production.sql)'
            ELSE 'data present' END AS data_status
FROM subcastes;
