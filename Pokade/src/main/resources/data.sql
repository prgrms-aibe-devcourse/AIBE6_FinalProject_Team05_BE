-- =========================================================
-- 카드 관련 시딩 데이터 (expansions / cards / card_variants / card_prices)
-- 나머지 16개 테이블은 schema.sql로 생성만 하고 데이터는 넣지 않음
-- =========================================================

-- ---------- expansions ----------
INSERT INTO expansions (id, name, series, code, total, language_code, release_date, logo, symbol, synced_at) VALUES ('base1', 'Base', 'Base', NULL, 102, 'EN', '1999-01-09', NULL, NULL, now()) ON CONFLICT (id) DO NOTHING;
INSERT INTO expansions (id, name, series, code, total, language_code, release_date, logo, symbol, synced_at) VALUES ('sv3pt5', '151', 'Scarlet & Violet', NULL, 207, 'EN', '2023-06-16', NULL, NULL, now()) ON CONFLICT (id) DO NOTHING;
INSERT INTO expansions (id, name, series, code, total, language_code, release_date, logo, symbol, synced_at) VALUES ('zsv10pt5', 'Black Bolt', 'Scarlet & Violet', NULL, 172, 'EN', '2025-07-18', NULL, NULL, now()) ON CONFLICT (id) DO NOTHING;
INSERT INTO expansions (id, name, series, code, total, language_code, release_date, logo, symbol, synced_at) VALUES ('sm11', 'Unified Minds', 'Sun & Moon', NULL, 260, 'EN', '2019-08-02', NULL, NULL, now()) ON CONFLICT (id) DO NOTHING;
INSERT INTO expansions (id, name, series, code, total, language_code, release_date, logo, symbol, synced_at) VALUES ('xy7', 'Ancient Origins', 'XY', NULL, 98, 'EN', '2015-08-12', NULL, NULL, now()) ON CONFLICT (id) DO NOTHING;
INSERT INTO expansions (id, name, series, code, total, language_code, release_date, logo, symbol, synced_at) VALUES ('sm3', 'Burning Shadows', 'Sun & Moon', NULL, 168, 'EN', '2017-08-04', NULL, NULL, now()) ON CONFLICT (id) DO NOTHING;
INSERT INTO expansions (id, name, series, code, total, language_code, release_date, logo, symbol, synced_at) VALUES ('me1', 'Mega Evolution', 'Mega Evolution', NULL, 188, 'EN', '2025-09-26', NULL, NULL, now()) ON CONFLICT (id) DO NOTHING;
INSERT INTO expansions (id, name, series, code, total, language_code, release_date, logo, symbol, synced_at) VALUES ('sv10_ja', 'サンダー', 'Scarlet & Violet', NULL, 98, 'JA', '2024-11-01', NULL, NULL, now()) ON CONFLICT (id) DO NOTHING;

-- ---------- cards / card_variants / card_prices ----------

-- base1-4 / Charizard
INSERT INTO cards (external_id, name, set_name, type, rarity, image_url, supertype, subtypes, types, evolves_from, printed_number, rarity_code, artist, national_pokedex_numbers, image_small, image_medium, image_large, expansion_id, expansion_sort_order, language_code, synced_at) VALUES ('base1-4', 'Charizard', 'Base', 'Fire', 'Rare Holo', 'https://images.pokemontcg.io/base1/4.png', 'Pokémon', '{"Stage 2"}', '{"Fire"}', '{"Charmeleon"}', '4/102', '★H', 'Mitsuhiro Arita', '{6}', 'https://images.scrydex.com/pokemon/base1-4/small', 'https://images.scrydex.com/pokemon/base1-4/medium', 'https://images.scrydex.com/pokemon/base1-4/large', 'base1', 4, 'EN', now()) ON CONFLICT (external_id) DO NOTHING;
INSERT INTO card_variants (card_id, variant_name, is_primary, synced_at) VALUES ((SELECT id FROM cards WHERE external_id = 'base1-4'), 'unlimitedHolofoil', TRUE, now()) ON CONFLICT (card_id, variant_name) DO NOTHING;
INSERT INTO card_prices (variant_id, price_type, grade, company, low, mid, high, market, currency, change_1d_pct, change_7d_pct, change_14d_pct, change_30d_pct, change_90d_pct, change_180d_pct, change_7d_amount, updated_at) VALUES ((SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'base1-4') AND variant_name = 'unlimitedHolofoil'), 'graded', '10', 'PSA', 2350.0, 2566.0, 2650.0, 2567.88, 'USD', NULL, 4.55, NULL, -0.42, -5.63, NULL, 111.75, now()) ON CONFLICT (variant_id, price_type, grade, company) DO NOTHING;
INSERT INTO card_prices (variant_id, price_type, grade, company, low, mid, high, market, currency, change_1d_pct, change_7d_pct, change_14d_pct, change_30d_pct, change_90d_pct, change_180d_pct, change_7d_amount, updated_at) VALUES ((SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'base1-4') AND variant_name = 'unlimitedHolofoil'), 'graded', '9', 'PSA', 780.0, 845.0, 910.0, 848.67, 'USD', NULL, 0.62, NULL, -2.54, -4.53, NULL, 5.2, now()) ON CONFLICT (variant_id, price_type, grade, company) DO NOTHING;

-- base1-2 / Blastoise
INSERT INTO cards (external_id, name, set_name, type, rarity, image_url, supertype, subtypes, types, evolves_from, printed_number, rarity_code, artist, national_pokedex_numbers, image_small, image_medium, image_large, expansion_id, expansion_sort_order, language_code, synced_at) VALUES ('base1-2', 'Blastoise', 'Base', 'Water', 'Rare Holo', 'https://images.pokemontcg.io/base1/2.png', 'Pokémon', '{"Stage 2"}', '{"Water"}', '{"Wartortle"}', '2/102', '★H', 'Ken Sugimori', '{9}', 'https://images.scrydex.com/pokemon/base1-2/small', 'https://images.scrydex.com/pokemon/base1-2/medium', 'https://images.scrydex.com/pokemon/base1-2/large', 'base1', 2, 'EN', now()) ON CONFLICT (external_id) DO NOTHING;
INSERT INTO card_variants (card_id, variant_name, is_primary, synced_at) VALUES ((SELECT id FROM cards WHERE external_id = 'base1-2'), 'unlimitedHolofoil', TRUE, now()) ON CONFLICT (card_id, variant_name) DO NOTHING;
INSERT INTO card_prices (variant_id, price_type, grade, company, low, mid, high, market, currency, change_1d_pct, change_7d_pct, change_14d_pct, change_30d_pct, change_90d_pct, change_180d_pct, change_7d_amount, updated_at) VALUES ((SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'base1-2') AND variant_name = 'unlimitedHolofoil'), 'graded', '10', 'PSA', 720.0, 760.0, 810.0, 768.4, 'USD', NULL, 1.08, NULL, -1.91, -3.82, NULL, 8.2, now()) ON CONFLICT (variant_id, price_type, grade, company) DO NOTHING;
INSERT INTO card_prices (variant_id, price_type, grade, company, low, mid, high, market, currency, change_1d_pct, change_7d_pct, change_14d_pct, change_30d_pct, change_90d_pct, change_180d_pct, change_7d_amount, updated_at) VALUES ((SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'base1-2') AND variant_name = 'unlimitedHolofoil'), 'graded', '9', 'PSA', 610.0, 655.0, 700.0, 648.0, 'USD', NULL, -0.49, NULL, 1.9, -2.79, NULL, -3.2, now()) ON CONFLICT (variant_id, price_type, grade, company) DO NOTHING;

-- base1-58 / Pikachu
INSERT INTO cards (external_id, name, set_name, type, rarity, image_url, supertype, subtypes, types, evolves_from, printed_number, rarity_code, artist, national_pokedex_numbers, image_small, image_medium, image_large, expansion_id, expansion_sort_order, language_code, synced_at) VALUES ('base1-58', 'Pikachu', 'Base', 'Lightning', 'Common', 'https://images.pokemontcg.io/base1/58.png', 'Pokémon', '{"Basic"}', '{"Lightning"}', NULL, '58/102', '●', 'Mitsuhiro Arita', '{25}', 'https://images.scrydex.com/pokemon/base1-58/small', 'https://images.scrydex.com/pokemon/base1-58/medium', 'https://images.scrydex.com/pokemon/base1-58/large', 'base1', 58, 'EN', now()) ON CONFLICT (external_id) DO NOTHING;
INSERT INTO card_variants (card_id, variant_name, is_primary, synced_at) VALUES ((SELECT id FROM cards WHERE external_id = 'base1-58'), 'unlimited', TRUE, now()) ON CONFLICT (card_id, variant_name) DO NOTHING;
INSERT INTO card_prices (variant_id, price_type, grade, company, low, mid, high, market, currency, change_1d_pct, change_7d_pct, change_14d_pct, change_30d_pct, change_90d_pct, change_180d_pct, change_7d_amount, updated_at) VALUES ((SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'base1-58') AND variant_name = 'unlimited'), 'graded', '10', 'PSA', 210.0, 235.0, 260.0, 238.4, 'USD', NULL, 1.32, NULL, 4.29, 10.16, NULL, 3.1, now()) ON CONFLICT (variant_id, price_type, grade, company) DO NOTHING;
INSERT INTO card_prices (variant_id, price_type, grade, company, low, mid, high, market, currency, change_1d_pct, change_7d_pct, change_14d_pct, change_30d_pct, change_90d_pct, change_180d_pct, change_7d_amount, updated_at) VALUES ((SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'base1-58') AND variant_name = 'unlimited'), 'graded', '9', 'PSA', 60.0, 68.0, 75.0, 69.2, 'USD', NULL, 1.32, NULL, 3.13, 6.79, NULL, 0.9, now()) ON CONFLICT (variant_id, price_type, grade, company) DO NOTHING;

-- sv3pt5-6 / Charizard ex
INSERT INTO cards (external_id, name, set_name, type, rarity, image_url, supertype, subtypes, types, evolves_from, printed_number, rarity_code, artist, national_pokedex_numbers, image_small, image_medium, image_large, expansion_id, expansion_sort_order, language_code, synced_at) VALUES ('sv3pt5-6', 'Charizard ex', '151', 'Fire', 'Double Rare', 'https://images.pokemontcg.io/sv3pt5/006.png', 'Pokémon', '{"Stage 2","ex"}', '{"Fire"}', '{"Charmeleon"}', '006/165', '◇◇', '5ban Graphics', '{6}', 'https://images.scrydex.com/pokemon/sv3pt5-6/small', 'https://images.scrydex.com/pokemon/sv3pt5-6/medium', 'https://images.scrydex.com/pokemon/sv3pt5-6/large', 'sv3pt5', 6, 'EN', now()) ON CONFLICT (external_id) DO NOTHING;
INSERT INTO card_variants (card_id, variant_name, is_primary, synced_at) VALUES ((SELECT id FROM cards WHERE external_id = 'sv3pt5-6'), 'holofoil', TRUE, now()) ON CONFLICT (card_id, variant_name) DO NOTHING;
INSERT INTO card_prices (variant_id, price_type, grade, company, low, mid, high, market, currency, change_1d_pct, change_7d_pct, change_14d_pct, change_30d_pct, change_90d_pct, change_180d_pct, change_7d_amount, updated_at) VALUES ((SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'sv3pt5-6') AND variant_name = 'holofoil'), 'graded', '10', 'PSA', 320.0, 340.0, 365.0, 342.5, 'USD', NULL, 3.63, NULL, -2.39, -6.83, NULL, 12.0, now()) ON CONFLICT (variant_id, price_type, grade, company) DO NOTHING;
INSERT INTO card_prices (variant_id, price_type, grade, company, low, mid, high, market, currency, change_1d_pct, change_7d_pct, change_14d_pct, change_30d_pct, change_90d_pct, change_180d_pct, change_7d_amount, updated_at) VALUES ((SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'sv3pt5-6') AND variant_name = 'holofoil'), 'graded', '9', 'PSA', 165.0, 178.0, 190.0, 180.2, 'USD', NULL, 1.12, NULL, -2.33, -5.16, NULL, 2.0, now()) ON CONFLICT (variant_id, price_type, grade, company) DO NOTHING;

-- sv3pt5-54 / Blastoise ex
INSERT INTO cards (external_id, name, set_name, type, rarity, image_url, supertype, subtypes, types, evolves_from, printed_number, rarity_code, artist, national_pokedex_numbers, image_small, image_medium, image_large, expansion_id, expansion_sort_order, language_code, synced_at) VALUES ('sv3pt5-54', 'Blastoise ex', '151', 'Water', 'Double Rare', 'https://images.pokemontcg.io/sv3pt5/054.png', 'Pokémon', '{"Stage 2","ex"}', '{"Water"}', '{"Wartortle"}', '054/165', '◇◇', '5ban Graphics', '{9}', 'https://images.scrydex.com/pokemon/sv3pt5-54/small', 'https://images.scrydex.com/pokemon/sv3pt5-54/medium', 'https://images.scrydex.com/pokemon/sv3pt5-54/large', 'sv3pt5', 54, 'EN', now()) ON CONFLICT (external_id) DO NOTHING;
INSERT INTO card_variants (card_id, variant_name, is_primary, synced_at) VALUES ((SELECT id FROM cards WHERE external_id = 'sv3pt5-54'), 'holofoil', TRUE, now()) ON CONFLICT (card_id, variant_name) DO NOTHING;
INSERT INTO card_prices (variant_id, price_type, grade, company, low, mid, high, market, currency, change_1d_pct, change_7d_pct, change_14d_pct, change_30d_pct, change_90d_pct, change_180d_pct, change_7d_amount, updated_at) VALUES ((SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'sv3pt5-54') AND variant_name = 'holofoil'), 'graded', '10', 'PSA', 140.0, 150.0, 162.0, 151.8, 'USD', NULL, 1.2, NULL, -2.06, -4.11, NULL, 1.8, now()) ON CONFLICT (variant_id, price_type, grade, company) DO NOTHING;
INSERT INTO card_prices (variant_id, price_type, grade, company, low, mid, high, market, currency, change_1d_pct, change_7d_pct, change_14d_pct, change_30d_pct, change_90d_pct, change_180d_pct, change_7d_amount, updated_at) VALUES ((SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'sv3pt5-54') AND variant_name = 'holofoil'), 'graded', '9', 'PSA', 70.0, 76.0, 82.0, 77.1, 'USD', NULL, 0.78, NULL, -2.41, -4.22, NULL, 0.6, now()) ON CONFLICT (variant_id, price_type, grade, company) DO NOTHING;

-- sv3pt5-25 / Pikachu
INSERT INTO cards (external_id, name, set_name, type, rarity, image_url, supertype, subtypes, types, evolves_from, printed_number, rarity_code, artist, national_pokedex_numbers, image_small, image_medium, image_large, expansion_id, expansion_sort_order, language_code, synced_at) VALUES ('sv3pt5-25', 'Pikachu', '151', 'Lightning', 'Common', 'https://images.pokemontcg.io/sv3pt5/025.png', 'Pokémon', '{"Basic"}', '{"Lightning"}', NULL, '025/165', '●', 'Naoyo Kimura', '{25}', 'https://images.scrydex.com/pokemon/sv3pt5-25/small', 'https://images.scrydex.com/pokemon/sv3pt5-25/medium', 'https://images.scrydex.com/pokemon/sv3pt5-25/large', 'sv3pt5', 25, 'EN', now()) ON CONFLICT (external_id) DO NOTHING;
INSERT INTO card_variants (card_id, variant_name, is_primary, synced_at) VALUES ((SELECT id FROM cards WHERE external_id = 'sv3pt5-25'), 'normal', TRUE, now()) ON CONFLICT (card_id, variant_name) DO NOTHING;
INSERT INTO card_prices (variant_id, price_type, grade, company, low, mid, high, market, currency, change_1d_pct, change_7d_pct, change_14d_pct, change_30d_pct, change_90d_pct, change_180d_pct, change_7d_amount, updated_at) VALUES ((SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'sv3pt5-25') AND variant_name = 'normal'), 'graded', '10', 'PSA', 22.0, 25.0, 28.0, 25.6, 'USD', NULL, 1.59, NULL, 4.49, 12.28, NULL, 0.4, now()) ON CONFLICT (variant_id, price_type, grade, company) DO NOTHING;
INSERT INTO card_prices (variant_id, price_type, grade, company, low, mid, high, market, currency, change_1d_pct, change_7d_pct, change_14d_pct, change_30d_pct, change_90d_pct, change_180d_pct, change_7d_amount, updated_at) VALUES ((SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'sv3pt5-25') AND variant_name = 'normal'), 'graded', '9', 'PSA', 8.0, 9.2, 10.5, 9.4, 'USD', NULL, 1.08, NULL, 3.3, 8.05, NULL, 0.1, now()) ON CONFLICT (variant_id, price_type, grade, company) DO NOTHING;

-- zsv10pt5-105 / Seismitoad
INSERT INTO cards (external_id, name, set_name, type, rarity, image_url, supertype, subtypes, types, evolves_from, printed_number, rarity_code, artist, national_pokedex_numbers, image_small, image_medium, image_large, expansion_id, expansion_sort_order, language_code, synced_at) VALUES ('zsv10pt5-105', 'Seismitoad', 'Black Bolt', 'Water', 'Illustration Rare', 'https://images.pokemontcg.io/zsv10pt5/105.png', 'Pokémon', '{"Stage 2"}', '{"Water"}', '{"Palpitoad"}', '105/086', '☆1', 'MEGREZ', '{537}', 'https://images.scrydex.com/pokemon/zsv10pt5-105/small', 'https://images.scrydex.com/pokemon/zsv10pt5-105/medium', 'https://images.scrydex.com/pokemon/zsv10pt5-105/large', 'zsv10pt5', 105, 'EN', now()) ON CONFLICT (external_id) DO NOTHING;
INSERT INTO card_variants (card_id, variant_name, is_primary, synced_at) VALUES ((SELECT id FROM cards WHERE external_id = 'zsv10pt5-105'), 'holofoil', TRUE, now()) ON CONFLICT (card_id, variant_name) DO NOTHING;
INSERT INTO card_prices (variant_id, price_type, grade, company, low, mid, high, market, currency, change_1d_pct, change_7d_pct, change_14d_pct, change_30d_pct, change_90d_pct, change_180d_pct, change_7d_amount, updated_at) VALUES ((SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'zsv10pt5-105') AND variant_name = 'holofoil'), 'graded', '10', 'PSA', 2200.0, 2350.0, 2450.0, 2399.0, 'USD', NULL, 3.9, NULL, 11.11, 20.62, NULL, 90.0, now()) ON CONFLICT (variant_id, price_type, grade, company) DO NOTHING;
INSERT INTO card_prices (variant_id, price_type, grade, company, low, mid, high, market, currency, change_1d_pct, change_7d_pct, change_14d_pct, change_30d_pct, change_90d_pct, change_180d_pct, change_7d_amount, updated_at) VALUES ((SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'zsv10pt5-105') AND variant_name = 'holofoil'), 'graded', '9', 'PSA', 780.0, 820.0, 860.0, 822.4, 'USD', NULL, 1.86, NULL, 7.17, 13.53, NULL, 15.0, now()) ON CONFLICT (variant_id, price_type, grade, company) DO NOTHING;

-- sm11-95 / Alakazam GX
INSERT INTO cards (external_id, name, set_name, type, rarity, image_url, supertype, subtypes, types, evolves_from, printed_number, rarity_code, artist, national_pokedex_numbers, image_small, image_medium, image_large, expansion_id, expansion_sort_order, language_code, synced_at) VALUES ('sm11-95', 'Alakazam GX', 'Unified Minds', 'Psychic', 'Rare Holo GX', 'https://images.pokemontcg.io/sm11/95.png', 'Pokémon', '{"Stage 2","GX"}', '{"Psychic"}', '{"Kadabra"}', '95/236', 'GX', 'Ryota Murayama', '{65}', 'https://images.scrydex.com/pokemon/sm11-95/small', 'https://images.scrydex.com/pokemon/sm11-95/medium', 'https://images.scrydex.com/pokemon/sm11-95/large', 'sm11', 95, 'EN', now()) ON CONFLICT (external_id) DO NOTHING;
INSERT INTO card_variants (card_id, variant_name, is_primary, synced_at) VALUES ((SELECT id FROM cards WHERE external_id = 'sm11-95'), 'holofoil', TRUE, now()) ON CONFLICT (card_id, variant_name) DO NOTHING;
INSERT INTO card_prices (variant_id, price_type, grade, company, low, mid, high, market, currency, change_1d_pct, change_7d_pct, change_14d_pct, change_30d_pct, change_90d_pct, change_180d_pct, change_7d_amount, updated_at) VALUES ((SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'sm11-95') AND variant_name = 'holofoil'), 'graded', '10', 'PSA', 24.0, 27.0, 30.0, 27.4, 'USD', NULL, -1.08, NULL, -4.2, -8.66, NULL, -0.3, now()) ON CONFLICT (variant_id, price_type, grade, company) DO NOTHING;
INSERT INTO card_prices (variant_id, price_type, grade, company, low, mid, high, market, currency, change_1d_pct, change_7d_pct, change_14d_pct, change_30d_pct, change_90d_pct, change_180d_pct, change_7d_amount, updated_at) VALUES ((SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'sm11-95') AND variant_name = 'holofoil'), 'graded', '9', 'PSA', 10.0, 11.5, 13.0, 11.8, 'USD', NULL, -0.84, NULL, -4.07, -8.53, NULL, -0.1, now()) ON CONFLICT (variant_id, price_type, grade, company) DO NOTHING;

-- xy7-54 / Gardevoir-EX
INSERT INTO cards (external_id, name, set_name, type, rarity, image_url, supertype, subtypes, types, evolves_from, printed_number, rarity_code, artist, national_pokedex_numbers, image_small, image_medium, image_large, expansion_id, expansion_sort_order, language_code, synced_at) VALUES ('xy7-54', 'Gardevoir-EX', 'Ancient Origins', 'Fairy', 'Rare Holo EX', 'https://images.pokemontcg.io/xy7/54.png', 'Pokémon', '{"Stage 2","EX"}', '{"Fairy"}', '{"Kirlia"}', '54/98', 'EX', '5ban Graphics', '{282}', 'https://images.scrydex.com/pokemon/xy7-54/small', 'https://images.scrydex.com/pokemon/xy7-54/medium', 'https://images.scrydex.com/pokemon/xy7-54/large', 'xy7', 54, 'EN', now()) ON CONFLICT (external_id) DO NOTHING;
INSERT INTO card_variants (card_id, variant_name, is_primary, synced_at) VALUES ((SELECT id FROM cards WHERE external_id = 'xy7-54'), 'holofoil', TRUE, now()) ON CONFLICT (card_id, variant_name) DO NOTHING;
INSERT INTO card_prices (variant_id, price_type, grade, company, low, mid, high, market, currency, change_1d_pct, change_7d_pct, change_14d_pct, change_30d_pct, change_90d_pct, change_180d_pct, change_7d_amount, updated_at) VALUES ((SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'xy7-54') AND variant_name = 'holofoil'), 'graded', '10', 'PSA', 68.0, 74.0, 80.0, 75.2, 'USD', NULL, 1.62, NULL, 4.73, 9.94, NULL, 1.2, now()) ON CONFLICT (variant_id, price_type, grade, company) DO NOTHING;
INSERT INTO card_prices (variant_id, price_type, grade, company, low, mid, high, market, currency, change_1d_pct, change_7d_pct, change_14d_pct, change_30d_pct, change_90d_pct, change_180d_pct, change_7d_amount, updated_at) VALUES ((SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'xy7-54') AND variant_name = 'holofoil'), 'graded', '9', 'PSA', 28.0, 31.0, 34.0, 31.6, 'USD', NULL, 1.28, NULL, 3.61, 7.85, NULL, 0.4, now()) ON CONFLICT (variant_id, price_type, grade, company) DO NOTHING;

-- sm3-20 / Charizard-GX
INSERT INTO cards (external_id, name, set_name, type, rarity, image_url, supertype, subtypes, types, evolves_from, printed_number, rarity_code, artist, national_pokedex_numbers, image_small, image_medium, image_large, expansion_id, expansion_sort_order, language_code, synced_at) VALUES ('sm3-20', 'Charizard-GX', 'Burning Shadows', 'Fire', 'Rare Holo GX', 'https://images.pokemontcg.io/sm3/20.png', 'Pokémon', '{"Stage 2","GX"}', '{"Fire"}', '{"Charmeleon"}', '20/147', 'GX', 'Mitsuhiro Arita', '{6}', 'https://images.scrydex.com/pokemon/sm3-20/small', 'https://images.scrydex.com/pokemon/sm3-20/medium', 'https://images.scrydex.com/pokemon/sm3-20/large', 'sm3', 20, 'EN', now()) ON CONFLICT (external_id) DO NOTHING;
INSERT INTO card_variants (card_id, variant_name, is_primary, synced_at) VALUES ((SELECT id FROM cards WHERE external_id = 'sm3-20'), 'holofoil', TRUE, now()) ON CONFLICT (card_id, variant_name) DO NOTHING;
INSERT INTO card_prices (variant_id, price_type, grade, company, low, mid, high, market, currency, change_1d_pct, change_7d_pct, change_14d_pct, change_30d_pct, change_90d_pct, change_180d_pct, change_7d_amount, updated_at) VALUES ((SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'sm3-20') AND variant_name = 'holofoil'), 'graded', '10', 'PSA', 210.0, 225.0, 240.0, 228.0, 'USD', NULL, 2.24, NULL, -2.77, -7.39, NULL, 5.0, now()) ON CONFLICT (variant_id, price_type, grade, company) DO NOTHING;
INSERT INTO card_prices (variant_id, price_type, grade, company, low, mid, high, market, currency, change_1d_pct, change_7d_pct, change_14d_pct, change_30d_pct, change_90d_pct, change_180d_pct, change_7d_amount, updated_at) VALUES ((SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'sm3-20') AND variant_name = 'holofoil'), 'graded', '9', 'PSA', 88.0, 95.0, 102.0, 96.4, 'USD', NULL, 1.69, NULL, -2.92, -6.86, NULL, 1.6, now()) ON CONFLICT (variant_id, price_type, grade, company) DO NOTHING;

-- me1-12 / Mega Lucario ex
INSERT INTO cards (external_id, name, set_name, type, rarity, image_url, supertype, subtypes, types, evolves_from, printed_number, rarity_code, artist, national_pokedex_numbers, image_small, image_medium, image_large, expansion_id, expansion_sort_order, language_code, synced_at) VALUES ('me1-12', 'Mega Lucario ex', 'Mega Evolution', 'Fighting', 'Double Rare', 'https://images.pokemontcg.io/me1/012.png', 'Pokémon', '{"Mega","ex"}', '{"Fighting"}', '{"Lucario"}', '012/132', '◇◇', '5ban Graphics', '{448}', 'https://images.scrydex.com/pokemon/me1-12/small', 'https://images.scrydex.com/pokemon/me1-12/medium', 'https://images.scrydex.com/pokemon/me1-12/large', 'me1', 12, 'EN', now()) ON CONFLICT (external_id) DO NOTHING;
INSERT INTO card_variants (card_id, variant_name, is_primary, synced_at) VALUES ((SELECT id FROM cards WHERE external_id = 'me1-12'), 'holofoil', TRUE, now()) ON CONFLICT (card_id, variant_name) DO NOTHING;
INSERT INTO card_prices (variant_id, price_type, grade, company, low, mid, high, market, currency, change_1d_pct, change_7d_pct, change_14d_pct, change_30d_pct, change_90d_pct, change_180d_pct, change_7d_amount, updated_at) VALUES ((SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'me1-12') AND variant_name = 'holofoil'), 'graded', '10', 'PSA', 46.0, 50.0, 55.0, 51.2, 'USD', NULL, 8.47, NULL, 23.08, 23.08, NULL, 4.0, now()) ON CONFLICT (variant_id, price_type, grade, company) DO NOTHING;
INSERT INTO card_prices (variant_id, price_type, grade, company, low, mid, high, market, currency, change_1d_pct, change_7d_pct, change_14d_pct, change_30d_pct, change_90d_pct, change_180d_pct, change_7d_amount, updated_at) VALUES ((SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'me1-12') AND variant_name = 'holofoil'), 'graded', '9', 'PSA', 20.0, 22.5, 25.0, 22.9, 'USD', NULL, 7.01, NULL, 19.9, 19.9, NULL, 1.5, now()) ON CONFLICT (variant_id, price_type, grade, company) DO NOTHING;

-- sv10_ja-1 / クヌギダマ
INSERT INTO cards (external_id, name, set_name, type, rarity, image_url, supertype, subtypes, types, evolves_from, printed_number, rarity_code, artist, national_pokedex_numbers, image_small, image_medium, image_large, expansion_id, expansion_sort_order, language_code, synced_at) VALUES ('sv10_ja-1', 'クヌギダマ', 'サンダー', '草', '通常', 'https://images.pokemontcg.io/sv10_ja/001.png', 'ポケモン', '{"たね"}', '{"草"}', NULL, '001/098', '●', 'YASHIRO Nanaco', '{204}', 'https://images.scrydex.com/pokemon/sv10_ja-1/small', 'https://images.scrydex.com/pokemon/sv10_ja-1/medium', 'https://images.scrydex.com/pokemon/sv10_ja-1/large', 'sv10_ja', 1, 'JA', now()) ON CONFLICT (external_id) DO NOTHING;
INSERT INTO card_variants (card_id, variant_name, is_primary, synced_at) VALUES ((SELECT id FROM cards WHERE external_id = 'sv10_ja-1'), 'normal', TRUE, now()) ON CONFLICT (card_id, variant_name) DO NOTHING;
INSERT INTO card_prices (variant_id, price_type, grade, company, low, mid, high, market, currency, change_1d_pct, change_7d_pct, change_14d_pct, change_30d_pct, change_90d_pct, change_180d_pct, change_7d_amount, updated_at) VALUES ((SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'sv10_ja-1') AND variant_name = 'normal'), 'graded', '10', 'PSA', 5.0, 5.6, 6.2, 5.7, 'JPY', NULL, 0.0, NULL, 1.79, 3.64, NULL, 0.0, now()) ON CONFLICT (variant_id, price_type, grade, company) DO NOTHING;
INSERT INTO card_prices (variant_id, price_type, grade, company, low, mid, high, market, currency, change_1d_pct, change_7d_pct, change_14d_pct, change_30d_pct, change_90d_pct, change_180d_pct, change_7d_amount, updated_at) VALUES ((SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'sv10_ja-1') AND variant_name = 'normal'), 'graded', '9', 'PSA', 2.0, 2.3, 2.6, 2.35, 'JPY', NULL, 0.0, NULL, 2.22, 4.44, NULL, 0.0, now()) ON CONFLICT (variant_id, price_type, grade, company) DO NOTHING;


-- =========================================================
-- FR-PRICE-01 검증용 시드 (users / listings / buy_offers)
-- =========================================================

-- 재기동 시 중복 방지 (테스트 데이터만 정리)
-- trades가 listings를 FK로 참조하므로 자식(trades)부터 삭제
DELETE FROM trades;
DELETE FROM buy_offers;
DELETE FROM listings;

-- ---------- users (테스트 유저 2명) ----------
INSERT INTO users (email, password, nickname, provider, role, status, terms_agreed_at, created_at, updated_at)
VALUES ('seller1@test.com', '$2a$10$dummyHashedPasswordForLocalTestOnly', '민준테스트', 'LOCAL', 'USER', 'ACTIVE', now(), now(), now())
    ON CONFLICT (email) DO NOTHING;

INSERT INTO users (email, password, nickname, provider, role, status, terms_agreed_at, created_at, updated_at)
VALUES ('seller2@test.com', '$2a$10$dummyHashedPasswordForLocalTestOnly', '지호테스트', 'LOCAL', 'USER', 'ACTIVE', now(), now(), now())
    ON CONFLICT (email) DO NOTHING;

-- ---------- listings (매도호가) ----------

-- Charizard base1-4 : 매도 2건 → 최저가 2,950,000이 즉시구매가
INSERT INTO listings (card_id, seller_id, variant_id, price, grade, status, created_at, updated_at)
VALUES (
           (SELECT id FROM cards WHERE external_id = 'base1-4'),
           (SELECT id FROM users WHERE email = 'seller1@test.com'),
           (SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'base1-4') AND variant_name = 'unlimitedHolofoil'),
           3200000, 'A', 'ACTIVE', now(), now()
       );
INSERT INTO listings (card_id, seller_id, variant_id, price, grade, status, created_at, updated_at)
VALUES (
           (SELECT id FROM cards WHERE external_id = 'base1-4'),
           (SELECT id FROM users WHERE email = 'seller2@test.com'),
           (SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'base1-4') AND variant_name = 'unlimitedHolofoil'),
           2950000, 'B', 'ACTIVE', now(), now()
       );

-- Charizard base1-4 : CANCELLED 1건 (status 필터링 검증용, 조회에 잡히면 안 됨)
INSERT INTO listings (card_id, seller_id, variant_id, price, grade, status, created_at, updated_at)
VALUES (
           (SELECT id FROM cards WHERE external_id = 'base1-4'),
           (SELECT id FROM users WHERE email = 'seller1@test.com'),
           (SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'base1-4') AND variant_name = 'unlimitedHolofoil'),
           1000000, 'S', 'CANCELLED', now(), now()
       );

-- Pikachu base1-58 : 매도 1건 (단일 매물 케이스)
INSERT INTO listings (card_id, seller_id, variant_id, price, grade, status, created_at, updated_at)
VALUES (
           (SELECT id FROM cards WHERE external_id = 'base1-58'),
           (SELECT id FROM users WHERE email = 'seller1@test.com'),
           (SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'base1-58') AND variant_name = 'unlimited'),
           280000, 'S', 'ACTIVE', now(), now()
       );

-- Blastoise base1-2 : 매도만 있고 매수 없음 (sellPrice 빈 값 검증용)
INSERT INTO listings (card_id, seller_id, variant_id, price, grade, status, created_at, updated_at)
VALUES (
           (SELECT id FROM cards WHERE external_id = 'base1-2'),
           (SELECT id FROM users WHERE email = 'seller2@test.com'),
           (SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'base1-2') AND variant_name = 'unlimitedHolofoil'),
           950000, 'A', 'ACTIVE', now(), now()
       );

-- ---------- buy_offers (매수호가) ----------

-- Charizard base1-4 : 매수 2건 → 최고가 2,850,000이 즉시판매가
INSERT INTO buy_offers (card_id, buyer_id, variant_id, price, grade, status, expires_at, price_updated_at, created_at, updated_at)
VALUES (
           (SELECT id FROM cards WHERE external_id = 'base1-4'),
           (SELECT id FROM users WHERE email = 'seller1@test.com'),
           (SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'base1-4') AND variant_name = 'unlimitedHolofoil'),
           2700000, NULL, 'ACTIVE', now() + interval '30 days', now(), now(), now()
       );
INSERT INTO buy_offers (card_id, buyer_id, variant_id, price, grade, status, expires_at, price_updated_at, created_at, updated_at)
VALUES (
           (SELECT id FROM cards WHERE external_id = 'base1-4'),
           (SELECT id FROM users WHERE email = 'seller2@test.com'),
           (SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'base1-4') AND variant_name = 'unlimitedHolofoil'),
           2850000, NULL, 'ACTIVE', now() + interval '30 days', now(), now(), now()
       );

-- Charizard base1-4 : EXPIRED 1건 (status 필터링 검증용)
INSERT INTO buy_offers (card_id, buyer_id, variant_id, price, grade, status, expires_at, price_updated_at, created_at, updated_at)
VALUES (
           (SELECT id FROM cards WHERE external_id = 'base1-4'),
           (SELECT id FROM users WHERE email = 'seller1@test.com'),
           (SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'base1-4') AND variant_name = 'unlimitedHolofoil'),
           9999000, NULL, 'EXPIRED', now() - interval '1 day', now() - interval '10 days', now() - interval '40 days', now()
       );

-- Pikachu base1-58 : 매수 1건
INSERT INTO buy_offers (card_id, buyer_id, variant_id, price, grade, status, expires_at, price_updated_at, created_at, updated_at)
VALUES (
           (SELECT id FROM cards WHERE external_id = 'base1-58'),
           (SELECT id FROM users WHERE email = 'seller2@test.com'),
           (SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'base1-58') AND variant_name = 'unlimited'),
           260000, NULL, 'ACTIVE', now() + interval '30 days', now(), now(), now()
       );

-- Blastoise ex sv3pt5-54 : 매수만 있고 매도 없음 (buyPrice 빈 값 검증용)
INSERT INTO buy_offers (card_id, buyer_id, variant_id, price, grade, status, expires_at, price_updated_at, created_at, updated_at)
VALUES (
           (SELECT id FROM cards WHERE external_id = 'sv3pt5-54'),
           (SELECT id FROM users WHERE email = 'seller1@test.com'),
           (SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'sv3pt5-54') AND variant_name = 'holofoil'),
           180000, NULL, 'ACTIVE', now() + interval '30 days', now(), now(), now()
       );

-- Charizard ex sv3pt5-6 : 매도·매수 모두 없음 (양쪽 빈 값 검증용, 의도적으로 데이터 없음)

-- =========================================================
-- FR-PRICE-02 검증용 시드 (trades — status='COMPLETED')
-- =========================================================

-- ---------- listings (체결 완료된 매물 3건, 등급별 확인용) ----------

-- Charizard base1-4 : PSA10 등급, 5,000,000원에 체결
INSERT INTO listings (card_id, seller_id, variant_id, price, grade, status, created_at, updated_at)
VALUES (
           (SELECT id FROM cards WHERE external_id = 'base1-4'),
           (SELECT id FROM users WHERE email = 'seller1@test.com'),
           (SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'base1-4') AND variant_name = 'unlimitedHolofoil'),
           5000000, 'PSA10', 'SOLD', now() - interval '1 day', now() - interval '1 day'
       );

-- Charizard base1-4 : 자체 AI등급 S, 3,000,000원에 체결
INSERT INTO listings (card_id, seller_id, variant_id, price, grade, status, created_at, updated_at)
VALUES (
           (SELECT id FROM cards WHERE external_id = 'base1-4'),
           (SELECT id FROM users WHERE email = 'seller2@test.com'),
           (SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'base1-4') AND variant_name = 'unlimitedHolofoil'),
           3000000, 'S', 'SOLD', now() - interval '3 days', now() - interval '3 days'
       );

-- Charizard base1-4 : 등급 없음(RAW), 2,500,000원에 체결
INSERT INTO listings (card_id, seller_id, variant_id, price, grade, status, created_at, updated_at)
VALUES (
           (SELECT id FROM cards WHERE external_id = 'base1-4'),
           (SELECT id FROM users WHERE email = 'seller1@test.com'),
           (SELECT id FROM card_variants WHERE card_id = (SELECT id FROM cards WHERE external_id = 'base1-4') AND variant_name = 'unlimitedHolofoil'),
           2500000, NULL, 'SOLD', now() - interval '10 days', now() - interval '10 days'
       );

-- ---------- trades (체결 완료, 최신순 정렬 검증용으로 confirmed_at 스태거) ----------

-- PSA10 매물 체결 (1일 전 - 가장 최근)
INSERT INTO trades (listing_id, buyer_id, price, status, confirmed_at, settled_at, created_at)
VALUES (
           (SELECT id FROM listings WHERE card_id = (SELECT id FROM cards WHERE external_id = 'base1-4') AND price = 5000000 AND grade = 'PSA10'),
           (SELECT id FROM users WHERE email = 'seller2@test.com'),
           5000000, 'COMPLETED', now() - interval '1 day', now() - interval '1 day', now() - interval '1 day'
       );

-- S등급 매물 체결 (3일 전)
INSERT INTO trades (listing_id, buyer_id, price, status, confirmed_at, settled_at, created_at)
VALUES (
           (SELECT id FROM listings WHERE card_id = (SELECT id FROM cards WHERE external_id = 'base1-4') AND price = 3000000 AND grade = 'S'),
           (SELECT id FROM users WHERE email = 'seller1@test.com'),
           3000000, 'COMPLETED', now() - interval '3 days', now() - interval '3 days', now() - interval '3 days'
       );

-- RAW(등급 없음) 매물 체결 (10일 전 - 가장 오래됨)
INSERT INTO trades (listing_id, buyer_id, price, status, confirmed_at, settled_at, created_at)
VALUES (
           (SELECT id FROM listings WHERE card_id = (SELECT id FROM cards WHERE external_id = 'base1-4') AND price = 2500000 AND grade IS NULL),
           (SELECT id FROM users WHERE email = 'seller2@test.com'),
           2500000, 'COMPLETED', now() - interval '10 days', now() - interval '10 days', now() - interval '10 days'
       );

-- Charizard ex sv3pt5-6 : 체결 이력 없음 (빈 목록 검증용, 의도적으로 데이터 없음)
