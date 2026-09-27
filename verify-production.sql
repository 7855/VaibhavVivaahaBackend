-- =====================================================================================
-- VVM — PRODUCTION VERIFICATION  (read-only, changes nothing, safe any time)
-- =====================================================================================
-- One query. Returns ~20 rows: every index, the new column, the table, the foreign key
-- and the seed data, each with a PASS / FAIL status — followed by a one-line summary.
--
-- WHAT YOU WANT TO SEE
--   Every `status` = 'PASS', and the final summary row = 'ALL CHECKS PASSED'.
--   Anything starting with 'FAIL' tells you exactly what is wrong and what to do.
--
-- USAGE:  point your client at vaibhavVivaaha and run the whole file.
-- =====================================================================================

USE `vaibhavVivaaha`;

SELECT category, item, expected, actual, status FROM (

    -- ── 16 INDEXES: present AND on the right columns ─────────────────────────────────
    SELECT 1 AS sort_order,
           'INDEX'   AS category,
           CONCAT(e.tbl, '.', e.idx) AS item,
           e.expected_columns        AS expected,
           IFNULL((SELECT GROUP_CONCAT(s.column_name ORDER BY s.seq_in_index)
                   FROM information_schema.statistics s
                   WHERE s.table_schema = DATABASE()
                     AND s.table_name = e.tbl AND s.index_name = e.idx),
                  '(not present)')   AS actual,
           CASE
               WHEN NOT EXISTS (SELECT 1 FROM information_schema.statistics s
                                WHERE s.table_schema = DATABASE()
                                  AND s.table_name = e.tbl AND s.index_name = e.idx)
                    THEN 'FAIL - MISSING, run migration-indexes-simple.sql'
               WHEN (SELECT GROUP_CONCAT(s.column_name ORDER BY s.seq_in_index)
                     FROM information_schema.statistics s
                     WHERE s.table_schema = DATABASE()
                       AND s.table_name = e.tbl AND s.index_name = e.idx) = e.expected_columns
                    THEN 'PASS'
               ELSE 'FAIL - WRONG COLUMNS, drop this index then re-create it'
           END AS status
    FROM (
        SELECT 'idx_chats_conv'   AS idx, 'chats'            AS tbl, 'conversationId,isActive,isRead'     AS expected_columns UNION ALL
        SELECT 'idx_notif_receiver',   'notifications',        'receiverId,createdAt'               UNION ALL
        SELECT 'idx_users_discovery',  'users',                'casteId,gender,isActive,userStatus' UNION ALL
        SELECT 'idx_viewed_user',      'viewedProfile',        'userIdValue'                        UNION ALL
        SELECT 'idx_viewed_by',        'viewedProfile',        'viewedBy'                           UNION ALL
        SELECT 'idx_usub_user',        'user_subscriptions',   'userId,status'                      UNION ALL
        SELECT 'idx_ir_received',      'interestRequest',      'interestReceived'                   UNION ALL
        SELECT 'idx_ir_send',          'interestRequest',      'interestSend'                       UNION ALL
        SELECT 'idx_block_by',         'blockedUsers',         'blockedByUserId'                    UNION ALL
        SELECT 'idx_block_target',     'blockedUsers',         'blockedUserId'                      UNION ALL
        SELECT 'idx_shortlist_by',     'shortlisted_profiles', 'shortlistedBy,isActive'             UNION ALL
        SELECT 'idx_shortlist_target', 'shortlisted_profiles', 'shortlistedUserId,isActive'         UNION ALL
        SELECT 'idx_conv_u1',          'conversations',        'user_one_id'                        UNION ALL
        SELECT 'idx_conv_u2',          'conversations',        'user_two_id'                        UNION ALL
        SELECT 'idx_subcaste_caste',   'subcastes',            'casteId,isActive'                   UNION ALL
        SELECT 'idx_users_subcaste',   'users',                'casteId,subcasteId'
    ) e

    UNION ALL

    -- ── COLUMN: users.subcasteId (created by Hibernate on backend start) ─────────────
    SELECT 2, 'COLUMN', 'users.subcasteId', 'bigint, nullable',
           IFNULL((SELECT CONCAT(column_type, ', ',
                                 CASE WHEN is_nullable='YES' THEN 'nullable' ELSE 'NOT NULL' END)
                   FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name='users' AND column_name='subcasteId'),
                  '(not present)'),
           CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns
                             WHERE table_schema = DATABASE() AND table_name='users' AND column_name='subcasteId')
                THEN 'PASS' ELSE 'FAIL - MISSING, start the backend (Hibernate creates it)' END

    UNION ALL

    -- ── TABLE: subcastes ────────────────────────────────────────────────────────────
    SELECT 3, 'TABLE', 'subcastes', 'exists (utf8mb4)',
           IFNULL((SELECT CONCAT('exists (', SUBSTRING_INDEX(table_collation,'_',1), ')')
                   FROM information_schema.tables
                   WHERE table_schema = DATABASE() AND table_name='subcastes'), '(not present)'),
           CASE
               WHEN NOT EXISTS (SELECT 1 FROM information_schema.tables
                                WHERE table_schema = DATABASE() AND table_name='subcastes')
                    THEN 'FAIL - MISSING, start the backend (Hibernate creates it)'
               WHEN (SELECT table_collation FROM information_schema.tables
                     WHERE table_schema = DATABASE() AND table_name='subcastes') NOT LIKE 'utf8mb4%'
                    THEN 'FAIL - NOT utf8mb4, Tamil/subcaste text will be mangled'
               ELSE 'PASS' END

    UNION ALL

    -- ── FOREIGN KEY: subcastes.casteId -> castes.id ─────────────────────────────────
    SELECT 4, 'FOREIGN KEY', 'subcastes.fk_subcaste_caste', 'exists',
           IFNULL((SELECT 'exists' FROM information_schema.table_constraints
                   WHERE table_schema = DATABASE() AND table_name='subcastes'
                     AND constraint_name='fk_subcaste_caste' LIMIT 1), '(not present)'),
           CASE WHEN EXISTS (SELECT 1 FROM information_schema.table_constraints
                             WHERE table_schema = DATABASE() AND table_name='subcastes'
                               AND constraint_name='fk_subcaste_caste')
                THEN 'PASS' ELSE 'FAIL - MISSING, run migration-indexes-simple.sql' END

    UNION ALL

    -- ── DATA: 27 subcaste rows (23 names + 4 "Others") ──────────────────────────────
    SELECT 5, 'DATA', 'subcastes rows', '27',
           CAST((SELECT COUNT(*) FROM subcastes) AS CHAR),
           CASE WHEN (SELECT COUNT(*) FROM subcastes) = 27 THEN 'PASS'
                WHEN (SELECT COUNT(*) FROM subcastes) = 0
                     THEN 'FAIL - EMPTY, subcaste feature will not work. Run the data insert'
                ELSE 'CHECK - unexpected count, see the per-caste breakdown below' END

    UNION ALL

    -- ── DATA: "Others" must exist ONLY for castes that have real subcastes ──────────
    SELECT 6, 'DATA', '"Others" placement', 'only on castes with subcastes',
           CONCAT((SELECT COUNT(*) FROM subcastes WHERE subcasteName='Others'), ' Others row(s)'),
           CASE WHEN EXISTS (
                    SELECT 1 FROM subcastes o
                    WHERE o.subcasteName='Others'
                      AND NOT EXISTS (SELECT 1 FROM subcastes s2
                                      WHERE s2.casteId=o.casteId AND s2.subcasteName<>'Others'))
                THEN 'FAIL - an "Others" sits on a caste with no real subcastes; delete that row'
                WHEN (SELECT COUNT(*) FROM subcastes WHERE subcasteName='Others') = 0
                THEN 'FAIL - no "Others" rows; members who do not know their subcaste cannot register'
                ELSE 'PASS' END

    UNION ALL

    -- ── SUMMARY ─────────────────────────────────────────────────────────────────────
    SELECT 9, 'SUMMARY', 'overall', 'all PASS',
           CONCAT((SELECT COUNT(*) FROM information_schema.statistics s2
                   WHERE s2.table_schema = DATABASE()
                     AND s2.index_name IN ('idx_chats_conv','idx_notif_receiver','idx_users_discovery',
                        'idx_viewed_user','idx_viewed_by','idx_usub_user','idx_ir_received','idx_ir_send',
                        'idx_block_by','idx_block_target','idx_shortlist_by','idx_shortlist_target',
                        'idx_conv_u1','idx_conv_u2','idx_subcaste_caste','idx_users_subcaste')
                     AND s2.seq_in_index = 1), ' of 16 indexes, ',
                  (SELECT COUNT(*) FROM subcastes), ' subcaste rows'),
           CASE WHEN (SELECT COUNT(*) FROM information_schema.statistics s2
                      WHERE s2.table_schema = DATABASE()
                        AND s2.index_name IN ('idx_chats_conv','idx_notif_receiver','idx_users_discovery',
                           'idx_viewed_user','idx_viewed_by','idx_usub_user','idx_ir_received','idx_ir_send',
                           'idx_block_by','idx_block_target','idx_shortlist_by','idx_shortlist_target',
                           'idx_conv_u1','idx_conv_u2','idx_subcaste_caste','idx_users_subcaste')
                        AND s2.seq_in_index = 1) = 16
                     AND (SELECT COUNT(*) FROM subcastes) = 27
                     AND EXISTS (SELECT 1 FROM information_schema.columns
                                 WHERE table_schema = DATABASE() AND table_name='users' AND column_name='subcasteId')
                     AND EXISTS (SELECT 1 FROM information_schema.table_constraints
                                 WHERE table_schema = DATABASE() AND table_name='subcastes'
                                   AND constraint_name='fk_subcaste_caste')
                THEN 'ALL CHECKS PASSED'
                ELSE '*** SOMETHING FAILED - see the FAIL rows above ***' END
) checks
ORDER BY sort_order, item;


-- =====================================================================================
-- Per-caste breakdown — expect 6 / 7 / 6 / 8 / 0, with "Others" on the first four only.
-- FREE CASTE BAR having 0 is CORRECT: it means "any caste is fine", so it has no subcastes.
-- =====================================================================================
SELECT c.id, c.casteName,
       COUNT(s.id) AS subcastes,
       IFNULL(SUM(s.subcasteName = 'Others'), 0) AS has_others
FROM castes c
LEFT JOIN subcastes s ON c.id = s.casteId
GROUP BY c.id, c.casteName
ORDER BY c.id;
