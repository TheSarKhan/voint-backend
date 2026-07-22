# ai/ qovluğu

Bu qovluq AI səs agentinin prompt və şablon fayllarını saxlayır. Ayrıca repo DEYİL —
backend ilə birlikdə versiyalanır və Docker image-ə birlikdə kopyalanır (bax: `Dockerfile`).

## Struktur

```
ai/
├── prompts/
│   ├── system-prompt.md   # agent persona + davranış qaydaları
│   └── boundaries.md      # agentin edə BİLMƏDİKLƏRİ (qadağalar)
├── templates/
│   ├── ces-rental.md      # CES (texnika icarəsi) RAG şablonu
│   ├── clinic.md          # klinika şablonu (placeholder)
│   └── restaurant.md      # restoran rezervasiya şablonu (placeholder)
└── README.md
```

## Backend bu qovluğu necə yükləyir

- Qovluğun yeri `application.yml`-dəki `voint.ai.folder` parametri ilə müəyyən olunur
  (default: `./ai`, env var: `VOINT_AI_FOLDER`).
- **Bootstrap mərhələsində** fayllar hələ oxunmur — `VoiceWebhookService.buildPrompt()`
  stub-dur. Növbəti mərhələdə prompt qurulanda `system-prompt.md` + `boundaries.md`
  oxunub tenant konfiqurasiyası (salamlama, iş saatları) və RAG nəticələri ilə
  birləşdirilərək Gemini Flash-a göndəriləcək.
- `templates/` faylları yeni müştəri (tenant) qoşulanda RAG datasının hansı strukturda
  toplanacağını təsvir edir — onlar runtime-da yox, onboarding prosesində istifadə olunur.
