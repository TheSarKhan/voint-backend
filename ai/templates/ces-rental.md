# CES — Texnika İcarəsi RAG Şablonu

> **Burada nə olacaq:** CES (tikinti texnikası icarəsi) üçün RAG sənədlərinin strukturu və
> müştərilərin ən çox verdiyi sual kateqoriyaları. Yeni icarə şirkəti qoşulanda bu şablon
> əsasında məlumatlar toplanıb RAG bazasına yüklənəcək.

## Tipik sual kateqoriyaları

- **Qiymətlər** — texnika növünə görə günlük/həftəlik/aylıq icarə qiymətləri, operator xidməti
- **Mövcudluq** — hansı texnika hansı tarixdə boşdur
- **İş saatları** — ofis və təhvil-təslim saatları
- **Çatdırılma** — şəhər daxili / rayonlara çatdırılma qiyməti və müddəti
- **Depozit** — depozit məbləği, geri qaytarılma şərtləri
- **İcarə şərtləri** — minimum müddət, tələb olunan sənədlər, endirimlər

## Sənəd strukturu (hər RAG sənədi üçün)

- `content` — Azərbaycan dilində tam, öz-özünə kifayət edən mətn parçası
- `category` — pricing / availability / working-hours / delivery / deposit / terms

<!-- TODO: tam sual-cavab nümunələri və data toplama forması növbəti mərhələdə -->
