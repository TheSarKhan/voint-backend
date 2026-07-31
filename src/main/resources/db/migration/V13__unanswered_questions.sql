-- Agentin cavablaya bilmədiyi suallar — bilik bazasındakı boşluqların qeydiyyatı.
--
-- Problem: zəngdə müştəri nəsə soruşur, bilik bazasında cavab yoxdur, agent operatora yönləndirir
-- və məsələ orada bitir. Növbəti müştəri eyni sualı verir, eyni yerə çırpılır. Boşluq heç yerdə
-- yazılmadığı üçün heç kim onu bağlamır.
--
-- Nə üçün ayrıca cədvəl, call_transcripts-də bir sütun yox: bir zəngdə bir neçə cavabsız sual ola
-- bilər və hər biri AYRI-AYRI həll olunur (biri bilik bazasına əlavə edilir, digəri "bu bizim işimiz
-- deyil" deyə bağlanır). Bir mətn sütunu bu vəziyyəti saxlaya bilməz.

CREATE TABLE unanswered_questions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    call_id         UUID NOT NULL REFERENCES calls (id) ON DELETE CASCADE,

    -- Sualın özü — transkriptdəki xam ifadə deyil, təhlil onu aydın bir suala çevirir
    -- ("depozit nə qədərdir?"), çünki bilik bazasına yazılan cavab da bu formaya cavab verir.
    question        TEXT NOT NULL,
    -- Operator söhbətin nədən getdiyini anlasın deyə qısa kontekst: hansı məqamda soruşulub.
    context         TEXT,

    status          VARCHAR(16) NOT NULL DEFAULT 'OPEN'
                    CHECK (status IN ('OPEN', 'ANSWERED', 'DISMISSED')),

    -- Cavab bilik bazasına əlavə ediləndə hansı sənədə çevrildiyi. Sənəd sonradan silinsə
    -- sual "cavablanmış" olaraq qalır, amma izi itmir - SET NULL, CASCADE yox.
    rag_document_id UUID REFERENCES rag_documents (id) ON DELETE SET NULL,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at     TIMESTAMPTZ,
    resolved_by     UUID REFERENCES panel_users (id) ON DELETE SET NULL
);

-- Panelin əsas sorğusu: "bu müəssisədə açıq qalan suallar".
CREATE INDEX idx_unanswered_tenant_status ON unanswered_questions (tenant_id, status);
-- Zəng cədvəlində işarələmə və zəng detalı üçün.
CREATE INDEX idx_unanswered_call ON unanswered_questions (call_id);

-- Təhlilin işlədiyini bilmək üçün. NULL = bu zəng hələ təhlil olunmayıb (köhnə zənglər, və ya
-- Gemini əlçatmaz olduğu üçün buraxılanlar) - geriyə dönük doldurma məhz bunlara baxır.
-- Cavabsız sual TAPILMAYAN zəngdə də doldurulur: "təhlil olunub, boşluq yoxdur" ilə
-- "heç baxılmayıb" fərqli şeylərdir.
ALTER TABLE call_transcripts ADD COLUMN analyzed_at TIMESTAMPTZ;
