# voint-backend

**Voint (Voice Intelligence)** — bizneslər üçün AI səs agenti + CRM platformasının backend-i.
Vapi telefoniya + STT (Soniox) + TTS (ElevenLabs) işini idarə edir, amma "beyin" bu backend-dir:
Vapi custom-LLM webhook-u bura göndərir → pgvector RAG axtarışı → prompt qurulur → Gemini Flash →
cavab Vapi tərəfindən səsləndirilir.

> **BOOTSTRAP MƏRHƏLƏSİ:** işlək skelet. Health check + Swagger işləyir, webhook **mock cavab**
> qaytarır. Real Gemini/Vapi inteqrasiyası növbəti mərhələlərdədir.

## Texnologiyalar

- Java 21, Spring Boot 3.4.x, Maven (wrapper daxildir: `mvnw` / `mvnw.cmd`)
- PostgreSQL 16 + **pgvector**, Redis 7, Flyway
- springdoc-openapi (Swagger), Lombok, JJWT

## İşə salma

### 1. voint-infra ilə (tövsiyə olunur)

`voint-infra` repo-sundakı docker-compose PostgreSQL (pgvector) + Redis qaldırır.
Default datasource parametrləri `voint-infra`-nın `.env.example`-ı ilə uyğundur:
`localhost:5432/voint`, user `voint`, parol `changeme`.

```bash
# voint-infra qovluğunda:
docker compose up -d postgres redis

# voint-backend qovluğunda (local profil default-dur):
./mvnw spring-boot:run
# Windows:
mvnw.cmd spring-boot:run
```

Server: **http://localhost:8080** (port 8080).

### 2. Docker ilə

```bash
docker build -t voint-backend .
docker run -p 8080:8080 --env SPRING_PROFILES_ACTIVE=docker voint-backend
```

`docker` profili datasource/redis üçün compose network adlarını (`postgres`, `redis`) istifadə edir.

### Profillər və env dəyişənləri

| Dəyişən | Default (local) | Qeyd |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/voint` | docker profilində host `postgres` |
| `SPRING_DATASOURCE_USERNAME` | `voint` | |
| `SPRING_DATASOURCE_PASSWORD` | `changeme` | |
| `SPRING_REDIS_HOST` / `SPRING_REDIS_PORT` | `localhost` / `6379` | docker profilində host `redis` |
| `JWT_SECRET` | dev dəyəri var | prod-da MÜTLƏQ dəyişin (min 32 bayt) |
| `GEMINI_API_KEY` | boş | boşdursa `MockLlmClient` işlədilir; local dev üçün repo-nun kökündəki (git-ignored) `application-local.yml`-də saxlanılır |
| `VAPI_WEBHOOK_SECRET` | boş | hələ yoxlanılmır (TODO) |
| `SONIOX_API_KEY`, `ELEVENLABS_API_KEY` | boş | Vapi tərəfində istifadə olunacaq |

## Swagger

- UI: **http://localhost:8080/swagger-ui.html**
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- Health: http://localhost:8080/actuator/health

## Webhook-u əl ilə test etmək

Vapi custom-LLM formatı OpenAI chat-completions formatıdır. Nümunə curl:

```bash
curl -X POST http://localhost:8080/api/v1/voice/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "model": "voint-agent",
    "messages": [
      {"role": "system", "content": "Sən CES şirkətinin səs agentisən."},
      {"role": "user", "content": "salam, ekskavator icarəsi neçəyədir?"}
    ],
    "stream": false
  }'
```

> **Qeyd (Windows/Git-Bash):** Azərbaycan hərfləri olan mətni inline `-d` ilə göndərmə - curl onu
> korlayır. Əvvəlcə UTF-8 fayla yaz, sonra `--data-binary @file.json` ilə göndər.

**Tenant müəyyənləşdirilməsi (BOOTSTRAP MƏRHƏLƏSİ):** request-ə əlavə edilə bilən top-level
`"tenant_id": "<uuid>"` sahəsi (və ya `call.metadata.tenantId`/`tenant_id`) hansı tenant-a aid
olduğunu bildirir. Heç biri yoxdursa, backend avtomatik seed olunmuş **CES** tenant-a
(`11111111-1111-1111-1111-111111111111`) fallback edir və log-a xəbərdarlıq yazır - real marşrutlama
(məsələn, zəng edilən nömrəyə görə) sonrakı mərhələdə ediləcək TODO-dur.

`GEMINI_API_KEY` təyin olunubsa, cavab RAG-a əsaslanan real Gemini Flash cavabıdır; olmadıqda
`MockLlmClient`-ə fallback edilir və cavab `"mock cavab: {son user mesajı}"` şəklində olur:

```json
{
  "id": "chatcmpl-<uuid>",
  "object": "chat.completion",
  "created": 1753228800,
  "model": "voint-agent",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "mock cavab: salam, ekskavator icarəsi neçəyədir?"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": { "prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0 }
}
```

`stream: true` göndərilsə də, hələlik non-streaming JSON qayıdır (TODO: SSE streaming).

## Seed məlumatları (V2__seed.sql)

- Tenant: **CES** (texnika icarəsi), id: `11111111-1111-1111-1111-111111111111`
- 5 nümunə RAG sənədi (ekskavator qiymətləri, iş saatları, çatdırılma, depozit, icarə şərtləri) —
  embedding-lər hələlik `NULL`
- Panel istifadəçisi: **`admin@ces.az` / `voint123`**

Login nümunəsi:

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "admin@ces.az", "password": "voint123"}'
```

Qaytarılan `accessToken`-i qorunan endpointlərə `Authorization: Bearer <token>` başlığı ilə göndərin
(Swagger UI-da "Authorize" düyməsi).

## Əsas endpointlər

Hamısı `/api/v1` altında (webhook, `auth/login`, `auth/refresh`, Swagger və actuator açıqdır,
qalanları JWT tələb edir):

- `POST /api/v1/voice/webhook` — Vapi custom-LLM webhook (mock)
- `POST /api/v1/tenants`, `GET /api/v1/tenants/{id}`, `PUT /api/v1/tenants/{id}/config`
- `POST|GET /api/v1/tenants/{id}/rag/documents`, `DELETE .../rag/documents/{docId}`
- `GET|POST /api/v1/tenants/{id}/customers`, `GET|PATCH .../customers/{customerId}`
- `GET|POST /api/v1/tenants/{id}/calls`, `GET .../calls/{callId}`
- `GET /api/v1/tenants/{id}/reservations`, `PATCH .../reservations/{resId}`
- `GET /api/v1/tenants/{id}/analytics` — real vaxtda `calls` cədvəlindən hesablanır
  (snapshot cədvəli YOXDUR — bilərəkdən)
- `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `GET /api/v1/auth/me`
- `GET /actuator/health`

## Layihə strukturu

`com.starsoft.voint` altında modullar: `tenant`, `voice`, `rag`, `llm` (LlmClient interfeysi +
MockLlmClient), `crm`, `call`, `reservation`, `analytics`, `auth`, `common`.
`ai/` qovluğu (promptlar + RAG şablonları) haqqında: [ai/README.md](ai/README.md).

## Qeydlər

- `rag_documents.embedding vector(768)` sütunu DB-də var, amma JPA entity-də bilərəkdən map
  olunmayıb — real RAG mərhələsində native SQL ilə yazılıb-oxunacaq (bax: `RagDocument.java`).
- Analytics runtime-da hesablanır, snapshot cədvəli yoxdur (bax: `AnalyticsService.java`).
- Streaming, Vapi secret yoxlanışı — kodda `TODO` kimi işarələnib.
- Gemini Flash (chat + embedding) inteqrasiyası işləkdir: model adları `ListModels` ilə avtomatik
  aşkarlanır (bax: `GeminiApiClient.java`), `voint.gemini.model` / `voint.gemini.embedding-model`
  ilə sabitlənə bilər. `GEMINI_API_KEY` olmadıqda `MockLlmClient`-ə fallback edilir.
