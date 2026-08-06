-- Platforma admininin giriş e-poçtunu real gələn qutusuna keçirir.
--
-- Səbəb: hesabın e-poçtu platform@voint.az idi, amma voint.az-ın MX qeydi yoxdur - yəni o ünvan
-- poçt QƏBUL ETMİR. "Şifrəmi unutdum" linki oraya getsə heç kimə çatmazdı. serxan.babayev.06@gmail.com
-- həm sahibin ünvanı, həm də sistemin SMTP göndərən ünvanıdır (Gmail-dən Gmail-ə, zəmanətli çatma) -
-- ona görə sıfırlama e-poçtu buraya gedəndə mütləq gəlir.
--
-- Şifrə burada təyin OLUNMUR: admin "Şifrəmi unutdum" ilə özü təyin edir (bax V15 + PasswordReset*).
-- Bu, şifrənin git tarixçəsinə düşməməsi deməkdir.
--
-- Yalnız köhnə seed e-poçtu hələ dursa dəyişir: kimsə artıq başqa ünvana keçiribsə toxunmuruq.
-- Hədəf ünvan artıq varsa (məsələn CES admini kimi) heç nə etmirik - e-poçt platforma boyu
-- unikaldır və konflikt login-i sındırardı.

UPDATE panel_users
SET email = 'serxan.babayev.06@gmail.com'
WHERE email = 'platform@voint.az'
  AND role = 'SUPER_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM panel_users existing
      WHERE lower(existing.email) = 'serxan.babayev.06@gmail.com'
  );
