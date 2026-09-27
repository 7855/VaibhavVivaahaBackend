-- Seed data for the `subcastes` table (added 2026-08-09, see CLAUDE.md section 18).
--
-- Subcaste names supplied by the client on 2026-08-09. These are REAL community names — do not
-- edit, guess at, or extend this list without the client confirming the additions.
--
-- casteId values come from the `castes` table:
--   1 = VANNIYAR   2 = NAIDU   3 = AADITRAVIDAR   4 = MUDALIAR   5 = FREECASTEBAR
-- FREECASTEBAR (5) deliberately has NO subcastes — it is the "no caste preference" option.
--
-- Safe to re-run: the NOT EXISTS guard makes each row idempotent, so this can be applied to
-- every environment (local / staging / production) without creating duplicates.
-- Hibernate's ddl-auto=update does NOT seed data, so this must be run by hand per environment.

INSERT INTO subcastes (casteId, subcasteName, isActive, createdAt, createdBy)
SELECT * FROM (
    SELECT 1 AS casteId, 'Padayachi'                 AS subcasteName, 'Y' AS isActive, NOW(6) AS createdAt, 'seed' AS createdBy UNION ALL
    SELECT 1, 'Gounder / Naicker',            'Y', NOW(6), 'seed' UNION ALL
    SELECT 1, 'Palli',                        'Y', NOW(6), 'seed' UNION ALL
    SELECT 1, 'Kandar',                       'Y', NOW(6), 'seed' UNION ALL
    SELECT 1, 'Mazhavarayar / Kachirayar',    'Y', NOW(6), 'seed' UNION ALL

    SELECT 2, 'Kamma',                        'Y', NOW(6), 'seed' UNION ALL
    SELECT 2, 'Balija',                       'Y', NOW(6), 'seed' UNION ALL
    SELECT 2, 'Gavara',                       'Y', NOW(6), 'seed' UNION ALL
    SELECT 2, 'Tholuva',                      'Y', NOW(6), 'seed' UNION ALL
    SELECT 2, 'Kapu',                         'Y', NOW(6), 'seed' UNION ALL
    SELECT 2, 'Kambalathu',                   'Y', NOW(6), 'seed' UNION ALL

    SELECT 3, 'Valluva / Valluvan',           'Y', NOW(6), 'seed' UNION ALL
    SELECT 3, 'Thoti / Vettiyan',             'Y', NOW(6), 'seed' UNION ALL
    SELECT 3, 'Kavalkaran',                   'Y', NOW(6), 'seed' UNION ALL
    SELECT 3, 'Samban / Sambavar',            'Y', NOW(6), 'seed' UNION ALL
    SELECT 3, 'Tangalam & Goliya',            'Y', NOW(6), 'seed' UNION ALL

    SELECT 4, 'Thuluva Vellalar',             'Y', NOW(6), 'seed' UNION ALL
    SELECT 4, 'Thondaimandala Vellalar',      'Y', NOW(6), 'seed' UNION ALL
    SELECT 4, 'Saiva Vellalar',               'Y', NOW(6), 'seed' UNION ALL
    SELECT 4, 'Poonamallee Vellalar',         'Y', NOW(6), 'seed' UNION ALL
    SELECT 4, 'Sengunthar Kaikolar',          'Y', NOW(6), 'seed' UNION ALL
    SELECT 4, 'Agamudaiya',                   'Y', NOW(6), 'seed' UNION ALL
    SELECT 4, 'Isai Vellala',                 'Y', NOW(6), 'seed'
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
