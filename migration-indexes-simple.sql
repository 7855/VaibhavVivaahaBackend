-- =====================================================================================
-- VVM — INDEXES + FOREIGN KEY  (plain ALTER statements, GUI-client friendly)
-- Target database: vaibhavVivaaha
-- =====================================================================================
--
-- Use this instead of migration-indexes-only.sql if your SQL client chokes on prepared
-- statements (DBeaver / Workbench / phpMyAdmin often send
-- "PREPARE ...; EXECUTE ...; DEALLOCATE ...;" as ONE statement, which is a syntax error).
-- There is no PREPARE, no DELIMITER and no stored procedure here — just ALTER TABLE.
--
-- ─────────────────────────────────────────────────────────────────────────────────────
-- HOW TO RUN
-- ─────────────────────────────────────────────────────────────────────────────────────
-- Run STEP 1 first. It tells you which indexes are missing. Then run only those ALTERs
-- from STEP 2 — or just run all of STEP 2 and IGNORE any of these harmless errors:
--
--     ERROR 1061  "Duplicate key name 'idx_...'"          -> index already there, fine
--     ERROR 1826 / 1022  "Duplicate foreign key ..."      -> FK already there, fine
--
-- Those errors mean the work was already done. Nothing is damaged by re-running.
-- Any OTHER error (e.g. 1072 "key column doesn't exist") is real — stop and report it.
--
-- ⚠️ TIMING: ADD INDEX can hold a metadata lock while the table rebuilds. `chats`,
-- `notifications` and `users` are the largest here — prefer a low-traffic window, and
-- take a backup first:  mysqldump -u <user> -p vaibhavVivaaha > backup.sql
-- =====================================================================================

USE `vaibhavVivaaha`;


-- =====================================================================================
-- STEP 1 — WHAT IS MISSING?  (read-only, safe to run any time)
-- Run this alone first. Every row that says "MISSING - create it" needs its ALTER
-- from STEP 2. Rows saying "already exists" can be skipped.
-- =====================================================================================

SELECT e.idx AS index_name,
       e.tbl AS table_name,
       e.expected_columns,
       CASE
           WHEN NOT EXISTS (SELECT 1 FROM information_schema.statistics s
                            WHERE s.table_schema = DATABASE()
                              AND s.table_name = e.tbl AND s.index_name = e.idx)
                THEN 'MISSING - create it'
           WHEN (SELECT GROUP_CONCAT(s.column_name ORDER BY s.seq_in_index)
                 FROM information_schema.statistics s
                 WHERE s.table_schema = DATABASE()
                   AND s.table_name = e.tbl AND s.index_name = e.idx) = e.expected_columns
                THEN 'already exists (OK)'
           ELSE '*** WRONG COLUMNS - drop it, then create ***'
       END AS status
FROM (
    SELECT 'idx_chats_conv'       AS idx, 'chats'                AS tbl, 'conversationId,isActive,isRead'     AS expected_columns UNION ALL
    SELECT 'idx_notif_receiver',       'notifications',        'receiverId,createdAt'               UNION ALL
    SELECT 'idx_users_discovery',      'users',                'casteId,gender,isActive,userStatus' UNION ALL
    SELECT 'idx_viewed_user',          'viewedProfile',        'userIdValue'                        UNION ALL
    SELECT 'idx_viewed_by',            'viewedProfile',        'viewedBy'                           UNION ALL
    SELECT 'idx_usub_user',            'user_subscriptions',   'userId,status'                      UNION ALL
    SELECT 'idx_ir_received',          'interestRequest',      'interestReceived'                   UNION ALL
    SELECT 'idx_ir_send',              'interestRequest',      'interestSend'                       UNION ALL
    SELECT 'idx_block_by',             'blockedUsers',         'blockedByUserId'                    UNION ALL
    SELECT 'idx_block_target',         'blockedUsers',         'blockedUserId'                      UNION ALL
    SELECT 'idx_shortlist_by',         'shortlisted_profiles', 'shortlistedBy,isActive'             UNION ALL
    SELECT 'idx_shortlist_target',     'shortlisted_profiles', 'shortlistedUserId,isActive'         UNION ALL
    SELECT 'idx_conv_u1',              'conversations',        'user_one_id'                        UNION ALL
    SELECT 'idx_conv_u2',              'conversations',        'user_two_id'                        UNION ALL
    SELECT 'idx_subcaste_caste',       'subcastes',            'casteId,isActive'                   UNION ALL
    SELECT 'idx_users_subcaste',       'users',                'casteId,subcasteId'
) e
ORDER BY e.tbl, e.idx;


-- =====================================================================================
-- STEP 2 — CREATE THEM  (one statement per line; ignore "Duplicate key name" errors)
-- =====================================================================================

-- --- 14 performance indexes ----------------------------------------------------------
ALTER TABLE `chats`                ADD INDEX `idx_chats_conv`       (`conversationId`, `isActive`, `isRead`);
ALTER TABLE `notifications`        ADD INDEX `idx_notif_receiver`   (`receiverId`, `createdAt`);
ALTER TABLE `users`                ADD INDEX `idx_users_discovery`  (`casteId`, `gender`, `isActive`, `userStatus`);
ALTER TABLE `viewedProfile`        ADD INDEX `idx_viewed_user`      (`userIdValue`);
ALTER TABLE `viewedProfile`        ADD INDEX `idx_viewed_by`        (`viewedBy`);
ALTER TABLE `user_subscriptions`   ADD INDEX `idx_usub_user`        (`userId`, `status`);
ALTER TABLE `interestRequest`      ADD INDEX `idx_ir_received`      (`interestReceived`);
ALTER TABLE `interestRequest`      ADD INDEX `idx_ir_send`          (`interestSend`);
ALTER TABLE `blockedUsers`         ADD INDEX `idx_block_by`         (`blockedByUserId`);
ALTER TABLE `blockedUsers`         ADD INDEX `idx_block_target`     (`blockedUserId`);
ALTER TABLE `shortlisted_profiles` ADD INDEX `idx_shortlist_by`     (`shortlistedBy`, `isActive`);
ALTER TABLE `shortlisted_profiles` ADD INDEX `idx_shortlist_target` (`shortlistedUserId`, `isActive`);
ALTER TABLE `conversations`        ADD INDEX `idx_conv_u1`          (`user_one_id`);
ALTER TABLE `conversations`        ADD INDEX `idx_conv_u2`          (`user_two_id`);

-- --- subcaste indexes + foreign key --------------------------------------------------
ALTER TABLE `subcastes`            ADD INDEX `idx_subcaste_caste`   (`casteId`, `isActive`);
ALTER TABLE `users`                ADD INDEX `idx_users_subcaste`   (`casteId`, `subcasteId`);
ALTER TABLE `subcastes`            ADD CONSTRAINT `fk_subcaste_caste` FOREIGN KEY (`casteId`) REFERENCES `castes` (`id`);


-- =====================================================================================
-- STEP 3 — VERIFY  (re-run STEP 1; every row should now say "already exists (OK)")
-- Plus the foreign key and a reminder about the data.
-- =====================================================================================

SELECT constraint_name, table_name
FROM information_schema.table_constraints
WHERE table_schema = DATABASE() AND table_name = 'subcastes' AND constraint_type = 'FOREIGN KEY';

SELECT COUNT(*) AS subcaste_rows,
       CASE WHEN COUNT(*) = 0 THEN 'EMPTY - subcaste feature will not work, run the data insert'
            ELSE 'data present' END AS data_status
FROM subcastes;
