-- Payment Mode Toggle — seed data
-- Run this once after deploying the new CallbackRequest entity (Hibernate will create the table on startup).
--
-- PAYMENT_MODE values: 'QR' or 'CONTACT'
--   - 'CONTACT' = show "Talk to a Relationship Manager" view (Play Store-safe default)
--   - 'QR'      = show existing UPI QR + screenshot upload flow
--
-- Flip the value via:
--   UPDATE keyValue SET valueColumn = '{"mode":"QR"}' WHERE keyColumn = 'PAYMENT_MODE';

INSERT INTO keyValue (keyColumn, valueColumn) VALUES
  ('PAYMENT_MODE',  '{"mode":"CONTACT"}'),
  ('ADMIN_CONTACT', '{"phone":"+917904547565","whatsapp":"+917904547565","rmName":"Priya","rmTitle":"Senior Relationship Manager","rmPhotoUrl":"","callbackHours":"10 AM – 8 PM (Mon–Sat)"}')
ON DUPLICATE KEY UPDATE valueColumn = VALUES(valueColumn);
