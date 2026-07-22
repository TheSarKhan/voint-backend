-- Seed data for local development / manual testing.
-- Tenant: CES (equipment rental pilot customer)
-- Panel user: admin@ces.az / voint123 (bcrypt hash below; documented in README.md)

INSERT INTO tenants (id, name, phone_number, greeting_text, working_hours, handoff_number, language_config)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'CES',
    '+994120000000',
    'Salam, CES texnika icarəsi şirkətinə xoş gəlmisiniz! Sizə necə kömək edə bilərəm?',
    'B.e-Şənbə 09:00-18:00, Bazar bağlıdır',
    '+994500000000',
    '{"default":"az","supported":["az","ru","en"]}'
);

-- Sample RAG documents (Azerbaijani). Embeddings are NULL at the bootstrap stage -
-- they will be computed by the real RAG pipeline in a later iteration.
INSERT INTO rag_documents (tenant_id, content, category, source)
VALUES
    ('11111111-1111-1111-1111-111111111111',
     'Ekskavator icarəsi qiymətləri: JCB 3CX ekskavator-yükləyici günlük 350 AZN, həftəlik 2100 AZN. CAT 320 paletli ekskavator günlük 550 AZN, həftəlik 3300 AZN. Mini ekskavator (Kubota U27) günlük 220 AZN. Qiymətlərə operator xidməti daxil deyil, operator ilə birlikdə günlük əlavə 80 AZN.',
     'pricing', 'seed'),
    ('11111111-1111-1111-1111-111111111111',
     'İş saatları: Bazar ertəsindən şənbə gününə qədər saat 09:00-dan 18:00-a kimi işləyirik. Bazar günü istirahət günüdür. Texnikanın təhvil-təslimi yalnız iş saatlarında aparılır.',
     'working-hours', 'seed'),
    ('11111111-1111-1111-1111-111111111111',
     'Çatdırılma şərtləri: Bakı şəhəri daxilində texnikanın çatdırılması 100 AZN, Abşeron rayonu üzrə 150 AZN. Regionlara çatdırılma məsafəyə görə hesablanır (hər km üçün 1.5 AZN). Çatdırılma sifarişdən sonra 24 saat ərzində həyata keçirilir.',
     'delivery', 'seed'),
    ('11111111-1111-1111-1111-111111111111',
     'Depozit şərtləri: İcarə müqaviləsi bağlanarkən texnikanın növündən asılı olaraq 500-2000 AZN məbləğində depozit tələb olunur. Depozit texnika zədəsiz qaytarıldıqdan sonra 3 iş günü ərzində geri ödənilir. Ödəniş nağd və ya bank köçürməsi ilə mümkündür.',
     'deposit', 'seed'),
    ('11111111-1111-1111-1111-111111111111',
     'İcarə şərtləri: Minimum icarə müddəti 1 gündür. Uzunmüddətli icarə (1 aydan çox) üçün 15% endirim tətbiq olunur. İcarə üçün şəxsiyyət vəsiqəsi və müqavilə imzalanması tələb olunur. Hüquqi şəxslər üçün VÖEN təqdim edilməlidir.',
     'terms', 'seed');

-- Panel user: admin@ces.az, password "voint123" (bcrypt)
INSERT INTO panel_users (tenant_id, email, password_hash, role)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'admin@ces.az',
    '$2a$10$YEji7lVQVhAUY3infHcEGOZa/Xv21qp0JDG7N6VPys5s2j2hCBl96',
    'ADMIN'
);
