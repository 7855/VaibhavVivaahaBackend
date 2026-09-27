-- Cashfree Payment Gateway — 2026-09-15
-- Hibernate ddl-auto=update creates the two tables (and, for NEW tables, usually the
-- declared unique constraints) but this project has seen it skip indexes before, so
-- apply this by hand in every environment after the first boot of the new jar and
-- verify with the SELECTs at the bottom. Every statement is idempotent-safe to re-run
-- only if you check the verification output first (MySQL has no CREATE INDEX IF NOT EXISTS).

ALTER TABLE payment_orders
  ADD UNIQUE KEY uk_po_order (orderId),
  ADD KEY idx_po_user (userId, createdAt),
  ADD KEY idx_po_status (orderStatus);

ALTER TABLE payment_webhook_events
  ADD UNIQUE KEY uk_pwe_idem (idempotencyKey),
  ADD KEY idx_pwe_order (orderId);

-- Verify
SELECT TABLE_NAME, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols, NON_UNIQUE
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('payment_orders', 'payment_webhook_events')
GROUP BY TABLE_NAME, INDEX_NAME, NON_UNIQUE
ORDER BY TABLE_NAME, INDEX_NAME;
