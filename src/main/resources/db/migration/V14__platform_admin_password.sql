-- platform@voint.az üçün şifrəni təyin edir.
--
-- V3-də bu hesabın şifrəsi qəsdən commit edilməmişdi ("real cross-tenant access, out of band
-- paylaşılıb") və o dəyər itdi - hash-dən geri qaytarmaq mümkün deyil. Ona görə burada yenisi
-- təyin olunur ki, admin panelinə giriş bərpa olunsun.
--
-- DİQQƏT: bu şifrə artıq git tarixçəsindədir. İlk girişdən sonra panel üzərindən dəyişdirin -
-- repoya çıxışı olan hər kəs bu hesabın sabit şifrəsi olduğunu bilir.
--
-- Hash tətbiqin öz BCryptPasswordEncoder(10) ilə yaradılıb və matches() ilə yoxlanılıb.
-- Yalnız hash HƏLƏ DƏ seed dəyəri olarsa yenilənir: kimsə şifrəni artıq dəyişibsə, bu
-- migration onu geri qaytarıb üstündən yazmamalıdır.

UPDATE panel_users
SET password_hash = '$2a$10$/WoKgFvglYCIx9mmBkdI5edqOfWFhIiOQTyHAF9Qug4Nbj6agPz2y'
WHERE email = 'platform@voint.az'
  AND password_hash = '$2a$10$EeV4KuQHgrYzKFAZgUJ6C..Nc7VUmLUT8WoHqOGamzLnZ8qnkFVMe';
