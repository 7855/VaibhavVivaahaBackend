-- ─────────────────────────────────────────────────────────────────────────────
--  Chat performance indexes — 2026-09-21
--
--  WHY: "loading conversation is slow". Every query the chat screens run hits
--  `chats` filtered by conversationId and then sorted or ranged by a column the
--  ONE existing index does not contain:
--
--      idx_chats_conv (conversationId, isActive, isRead)     <- what exists today
--
--  That index answers "which rows belong to this conversation" and nothing else.
--  The sort and the range still cost a full read of the conversation.
--
--  MEASURE FIRST (step 0). If `chats` is small these indexes change nothing and
--  the delay is elsewhere — the app's 10s HTTP timeout, or the synchronous email
--  sends that pin the connection pool (see CLAUDE.md 2026-09-04).
-- ─────────────────────────────────────────────────────────────────────────────

-- STEP 0 — is this even the problem?
SELECT COUNT(*) AS total_messages FROM chats;
SELECT conversationId, COUNT(*) AS msgs
  FROM chats GROUP BY conversationId ORDER BY msgs DESC LIMIT 5;
-- Under ~50k total rows and ~500 in the biggest conversation, STOP: indexes are
-- not your bottleneck. Profile the endpoint instead.

-- STEP 1 — the plan BEFORE (keep this output to compare against step 3).
-- Replace 1 with a real conversationId from the query above.
EXPLAIN SELECT message FROM chats
 WHERE conversationId = 1
 ORDER BY createdAt DESC LIMIT 1;
EXPLAIN SELECT * FROM chats
 WHERE conversationId = 1 AND isActive = 'Y' AND id > 0
 ORDER BY id ASC;
-- Look for "Using filesort" and a high `rows` count. That is the cost.


-- ─────────────────────────────────────────────────────────────────────────────
-- STEP 2 — the indexes.
--
-- ALGORITHM=INPLACE, LOCK=NONE is deliberate and load-bearing: it keeps the
-- table readable AND writable while the index builds. If the server cannot do
-- it online it ERRORS OUT instead of silently locking `chats` — which on a live
-- app would stall every member's chat. Never drop these clauses to "make it
-- work"; if one errors, run it in a maintenance window instead.
--
-- Re-running is safe: ERROR 1061 "Duplicate key name" just means it is already
-- there. Each statement is independent — a failure does not undo the earlier ones.
-- ─────────────────────────────────────────────────────────────────────────────

-- 2a. CHAT LIST — the big one.
-- getUserChatList runs FOUR correlated subqueries per conversation, all shaped
--   SELECT ... FROM chats WHERE conversationId = c.id ORDER BY createdAt DESC LIMIT 1
-- (last message, its time, its read flag, its sender). With no createdAt in any
-- index each one reads every message in that conversation and sorts it. A member
-- with 20 conversations of 100 messages does ~8,000 row reads and 80 sorts to
-- draw one list. With this index each subquery becomes a backward seek to the
-- newest row: read one, stop.
ALTER TABLE `chats`
  ADD INDEX `idx_chats_conv_created` (`conversationId`, `createdAt`),
  ALGORITHM=INPLACE, LOCK=NONE;

-- 2b. OPENING A CHAT — the hottest query in the app.
-- findVisibleAfter is, per its own javadoc, "the app's single way of getting
-- messages" — on open, on every socket event, on reconnect and on foreground:
--   WHERE conversationId = ? AND isActive = ? AND id > ? ORDER BY id ASC
-- The existing index is (conversationId, isActive, isRead). InnoDB appends the
-- PK, so it is really (conversationId, isActive, isRead, id) — and because
-- `isRead` sits BETWEEN the equality columns and `id`, the `id > ?` cannot be a
-- range scan. MySQL reads the whole conversation and filters. Putting `id`
-- directly after the two equality columns makes it a true range scan that is
-- already in ORDER BY order, so the sort disappears too.
-- NOTE: the PK column is literally `Id` (ChatEntity maps @Column(name="Id")).
-- MySQL matches column names case-insensitively, so `id` would also work — `Id`
-- is used here to match the schema exactly.
ALTER TABLE `chats`
  ADD INDEX `idx_chats_conv_active_id` (`conversationId`, `isActive`, `Id`),
  ALGORITHM=INPLACE, LOCK=NONE;

-- 2c. UNREAD BADGES — runs for every conversation on the list screen.
--   WHERE conversationId IN (...) AND senderId != ? AND isRead = false
-- isRead is the selective column here (most messages are read), so it goes
-- second. senderId last, because `!=` cannot seek — it only filters.
ALTER TABLE `chats`
  ADD INDEX `idx_chats_conv_read_sender` (`conversationId`, `isRead`, `senderId`),
  ALGORITHM=INPLACE, LOCK=NONE;

-- 2d. TICK STATE — maxReadId / maxDeliveredId, twice per chat open.
--   WHERE conversationId = ? AND senderId = ? AND isRead = true
-- Lower value than the three above (it is two queries per open, not per row).
-- Skip this one if you would rather keep write cost down on `chats`.
ALTER TABLE `chats`
  ADD INDEX `idx_chats_conv_sender_read` (`conversationId`, `senderId`, `isRead`),
  ALGORITHM=INPLACE, LOCK=NONE;


-- ─────────────────────────────────────────────────────────────────────────────
-- STEP 3 — confirm it worked. Re-run the two EXPLAINs from step 1.
--   2a should now show key = idx_chats_conv_created and NO "Using filesort".
--   2b should now show key = idx_chats_conv_active_id, "Using index condition",
--      no filesort, and a much smaller `rows`.
-- If a plan did not change, MySQL chose another index — run ANALYZE TABLE chats;
-- and re-check before adding anything else.
-- ─────────────────────────────────────────────────────────────────────────────

SHOW INDEX FROM `chats`;


-- ─────────────────────────────────────────────────────────────────────────────
-- ROLLBACK — indexes are fully reversible and drop instantly.
-- ─────────────────────────────────────────────────────────────────────────────
-- ALTER TABLE `chats` DROP INDEX `idx_chats_conv_created`;
-- ALTER TABLE `chats` DROP INDEX `idx_chats_conv_active_id`;
-- ALTER TABLE `chats` DROP INDEX `idx_chats_conv_read_sender`;
-- ALTER TABLE `chats` DROP INDEX `idx_chats_conv_sender_read`;


-- ─────────────────────────────────────────────────────────────────────────────
-- DELIBERATELY NOT INCLUDED
--
-- `conversations` already has idx_conv_u1 (user_one_id) and idx_conv_u2
-- (user_two_id). The chat-list WHERE is
--     (:userId = user_one_id OR :userId = user_two_id) AND isActive = 'Y'
-- which MySQL answers with an index_merge union of those two. Adding isActive
-- to each would save little: that table has one row per PAIR, so it is small
-- next to `chats`, and it is not where the time goes. Revisit only if step 3
-- shows the subqueries fixed and the list still slow.
--
-- The old idx_chats_conv (conversationId, isActive, isRead) is left in place.
-- It is now largely redundant against 2b and 2c, but dropping an index is a
-- separate decision that wants its own before/after measurement — do not bundle
-- it into a performance fix.
-- ─────────────────────────────────────────────────────────────────────────────
