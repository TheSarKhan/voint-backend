package com.starsoft.voint.mail;

/**
 * The two emails the platform sends.
 *
 * <p>Plain HTML, inline styles, no images and no external stylesheet - mail clients strip most of
 * what a browser would honour, and a logo hosted somewhere gets blocked by default and leaves a
 * broken box at the top of the message.
 *
 * <p>They carry the password itself rather than a reset link. A link needs a token table, an
 * expiry and a public endpoint to consume it; that is the right end state, but sending the
 * password to the address that is about to become the login is the same trust boundary an email
 * link would rely on anyway.
 */
public final class MailTemplates {

    private MailTemplates() {
    }

    private static final String WRAP = """
            <div style="font-family:-apple-system,Segoe UI,Roboto,Arial,sans-serif;
                        max-width:520px;margin:0 auto;padding:32px 24px;color:#111">
              <div style="font-size:22px;font-weight:600;letter-spacing:-.02em;margin-bottom:24px">Voint</div>
              %s
              <div style="margin-top:32px;padding-top:16px;border-top:1px solid #e5e5e5;
                          font-size:12px;color:#777">
                Bu mesaj Voint panelindən avtomatik göndərilib.
                Gözləmirdinizsə, nəzərə almayın.
              </div>
            </div>
            """;

    private static final String CREDENTIALS_BOX = """
            <div style="background:#f5f5f5;border-radius:6px;padding:16px;margin:20px 0;
                        font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:14px">
              <div style="margin-bottom:6px"><span style="color:#777">E-poçt:</span> %s</div>
              <div><span style="color:#777">Şifrə:</span> <strong>%s</strong></div>
            </div>
            """;

    public static String welcome(String tenantName, String panelUrl, String email, String password) {
        String body = """
                <p style="font-size:16px;line-height:1.6;margin:0 0 16px">
                  Salam, <strong>%s</strong> üçün Voint paneli hazırdır.
                </p>
                <p style="font-size:15px;line-height:1.6;color:#444;margin:0">
                  Zənglərinizin qeydləri, müştəri siyahısı və rezervasiya sorğuları buradadır:
                </p>
                %s
                <a href="%s" style="display:inline-block;background:#111;color:#fff;
                   text-decoration:none;padding:11px 20px;border-radius:6px;font-size:14px">
                  Panelə daxil ol
                </a>
                <p style="font-size:13px;line-height:1.6;color:#777;margin:20px 0 0">
                  İlk girişdən sonra şifrəni dəyişməyiniz tövsiyə olunur.
                </p>
                """.formatted(tenantName, CREDENTIALS_BOX.formatted(email, password), panelUrl);
        return WRAP.formatted(body);
    }

    public static String passwordReset(String panelUrl, String email, String password) {
        String body = """
                <p style="font-size:16px;line-height:1.6;margin:0 0 16px">
                  Şifrəniz yeniləndi.
                </p>
                <p style="font-size:15px;line-height:1.6;color:#444;margin:0">
                  Köhnə şifrə artıq işləmir. Yeni məlumatlarınız:
                </p>
                %s
                <a href="%s" style="display:inline-block;background:#111;color:#fff;
                   text-decoration:none;padding:11px 20px;border-radius:6px;font-size:14px">
                  Panelə daxil ol
                </a>
                """.formatted(CREDENTIALS_BOX.formatted(email, password), panelUrl);
        return WRAP.formatted(body);
    }

    public static String test() {
        String body = """
                <p style="font-size:16px;line-height:1.6;margin:0">
                  SMTP ayarları işləyir. Bu mesajı gördünüzsə, panel e-poçt göndərə bilir.
                </p>
                """;
        return WRAP.formatted(body);
    }
}
