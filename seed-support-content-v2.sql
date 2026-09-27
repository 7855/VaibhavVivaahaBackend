-- ══════════════════════════════════════════════════════════════════════
-- SUPPORT_CONTENT v2 — Help & Support screen copy
--
-- Adds the fields the redesigned HelpSupportPage understands on top of the
-- original row seeded by seed-content-keyvalues.sql:
--   whatsapp, whatsappNote, hoursNote, quickHelp[]
-- The original keys (email/emailNote/phone/phoneNote/hoursWeekday/hoursWeekend)
-- are unchanged; `chatNote` is dropped — the app now maps it onto whatsappNote
-- for backwards compatibility, so the old row still renders if this is not run.
--
-- keyValue has ONLY id / keyColumn / valueColumn, and keyColumn has NO unique
-- index — ON DUPLICATE KEY UPDATE would silently insert a duplicate row, so
-- DELETE-then-INSERT is used to stay idempotent. valueColumn is a json column.
-- Every field is optional: omit one and the app falls back to its bundled copy.
-- ══════════════════════════════════════════════════════════════════════

START TRANSACTION;

DELETE FROM keyValue WHERE keyColumn = 'SUPPORT_CONTENT';
INSERT INTO keyValue (keyColumn, valueColumn)
VALUES ('SUPPORT_CONTENT', '{"email":"support@vaibhavvivaahamatrimony.com","emailNote":"Replies within 24 hours","phone":"+91 79045 47565","phoneNote":"Mon - Sat, 10 AM - 8 PM","whatsapp":"+91 79045 47565","whatsappNote":"Quickest way to reach us","hoursWeekday":"Mon - Sat  ·  10 AM - 8 PM","hoursWeekend":"Sunday  ·  Closed","hoursNote":"Messages sent outside these hours are answered the next working day.","quickHelp":[{"title":"Profile not showing in search?","text":"Complete your profile and make sure it has been verified by our team."},{"title":"Trouble with a payment?","text":"Check your payment method and try again, or request a callback from a relationship manager."},{"title":"Can''t access your account?","text":"Reset your PIN from the login screen, or write to us with your registered mobile number."}]}');

COMMIT;

-- verify: expect exactly one row
SELECT keyColumn, COUNT(*) AS rows_, CHAR_LENGTH(valueColumn) AS len
FROM keyValue WHERE keyColumn = 'SUPPORT_CONTENT'
GROUP BY keyColumn, valueColumn;
