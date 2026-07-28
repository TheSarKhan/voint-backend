-- Dörd gözlə baxılan əməliyyatlar: bir müəssisədə hər dəyişdirmə və silmə təsdiqdən keçir.
--
-- Sorğu İCRA OLUNMUR, saxlanılır. Təsdiqlənəndə eyni sorğu daxildən yenidən göndərilir - yəni
-- biznes məntiqi bir dəfə yazılır və burada təkrarlanmır. Rədd edilsə heç nə baş vermir.
--
-- Nə üçün sorğunun özü saxlanılır, "müştəri X silinsin" kimi bir niyyət yox: niyyəti icra etmək
-- üçün hər əməliyyatın ikinci bir icraçısını yazmaq lazım gələrdi, və onlar əsl endpoint-dən
-- yavaş-yavaş fərqlənməyə başlayardı. Sorğunun surəti isə həmişə eyni yolu gedir.

CREATE TABLE approval_requests (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,

    requested_by       UUID REFERENCES panel_users (id) ON DELETE SET NULL,
    -- Hesab sonradan silinsə və ya adı dəyişsə də, kimin istədiyi qeyd olaraq qalmalıdır.
    requested_by_email VARCHAR(255) NOT NULL,

    method             VARCHAR(10)  NOT NULL,
    path               VARCHAR(512) NOT NULL,
    query_string       VARCHAR(1024),
    body               TEXT,

    -- İcazə matrisinin öz lüğəti ilə: ekran "hansı modul" deyə süzgəcləyə bilsin.
    resource           VARCHAR(40)  NOT NULL,
    action             VARCHAR(10)  NOT NULL,
    -- Təsdiqləyən adama göstərilən cümlə. Sorğunun yolunu oxutmaq olmaz.
    summary            VARCHAR(500) NOT NULL,

    status             VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    decided_at         TIMESTAMPTZ,
    decided_by         UUID REFERENCES panel_users (id) ON DELETE SET NULL,
    decided_by_email   VARCHAR(255),
    decision_note      VARCHAR(500),

    -- Təsdiqlənmiş sorğunun daxili təkrar göndərilməsi üçün birdəfəlik açar. İstifadə olunandan
    -- sonra silinir: eyni təsdiq iki dəfə icra oluna bilməz.
    replay_nonce       VARCHAR(64),
    failure_detail     VARCHAR(1000),

    CONSTRAINT approval_status_known
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'FAILED'))
);

-- Panel həmişə "bu müəssisədə gözləyənlər" sorğusunu verir; siyahı yeni-dən köhnəyə sıralanır.
CREATE INDEX idx_approvals_tenant_status ON approval_requests (tenant_id, status, created_at DESC);

-- Birdəfəlik açarla təkrar göndəriş hər dəfə bu sütuna baxır.
CREATE UNIQUE INDEX idx_approvals_nonce ON approval_requests (replay_nonce)
    WHERE replay_nonce IS NOT NULL;

-- Yeni modul: kim növbəni görür (READ) və kim qərar verir (UPDATE).
-- Platforma admininə hər ikisi verilir - onsuz da bütün icazələri var.
INSERT INTO role_permissions (role_id, resource, action)
SELECT id, 'APPROVAL', a
FROM roles, unnest(ARRAY['READ', 'UPDATE']) AS a
WHERE id = 'a0000000-0000-0000-0000-000000000001'
ON CONFLICT DO NOTHING;

-- Müəssisə sahibi öz müəssisəsində təsdiq verən şəxsdir. Şablona da, artıq yaradılmış
-- nüsxələrə də verilir - əks halda mövcud müəssisələrdə heç kim təsdiq edə bilməz və
-- bu miqrasiya işlədiyi an bütün silmə əməliyyatları donar.
INSERT INTO role_permissions (role_id, resource, action)
SELECT id, 'APPROVAL', a
FROM roles, unnest(ARRAY['READ', 'UPDATE']) AS a
WHERE name = 'Sahib'
ON CONFLICT DO NOTHING;

-- Operator növbəni görür, amma qərar vermir: təsdiq mexanizmi öz-özünü təsdiqləyən
-- rol yaradarsa mənasını itirir.
INSERT INTO role_permissions (role_id, resource, action)
SELECT id, 'APPROVAL', 'READ'
FROM roles
WHERE name = 'Operator'
ON CONFLICT DO NOTHING;
