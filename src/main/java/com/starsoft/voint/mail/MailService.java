package com.starsoft.voint.mail;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.starsoft.voint.settings.PlatformSettingsService;
import com.starsoft.voint.settings.SettingKey;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends the few emails the platform needs, using whatever SMTP account is configured in the
 * admin panel.
 *
 * <p>Mail is treated as optional on purpose. Voint runs today with no SMTP account at all, and a
 * new customer still has to be able to get a login: {@link #isConfigured()} lets the caller fall
 * back to showing the password on screen. The alternative - requiring SMTP before anyone can be
 * onboarded - would have blocked the whole feature on an account nobody has yet.
 *
 * <p>All five settings must be present. A half-configured server is worse than none, because the
 * send fails at the moment it matters and the operator has already told the customer to check
 * their inbox.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final PlatformSettingsService settings;

    public boolean isConfigured() {
        return StringUtils.hasText(settings.get(SettingKey.SMTP_HOST))
                && StringUtils.hasText(settings.get(SettingKey.SMTP_PORT))
                && StringUtils.hasText(settings.get(SettingKey.SMTP_USERNAME))
                && StringUtils.hasText(settings.get(SettingKey.SMTP_PASSWORD))
                && StringUtils.hasText(settings.get(SettingKey.SMTP_FROM));
    }

    /**
     * @throws MailNotConfiguredException when no SMTP account has been set up yet
     * @throws MailException              when the server refuses the message. Never swallowed: the
     *                                    caller has just promised a user an email, so a silent
     *                                    failure would leave them waiting for something that will
     *                                    never arrive.
     */
    public void send(String to, String subject, String htmlBody) {
        if (!isConfigured()) {
            throw new MailNotConfiguredException(
                    "SMTP ayarları tam deyil - Ayarlar bölməsində beş sahəni doldur");
        }
        try {
            JavaMailSenderImpl sender = sender();
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(settings.get(SettingKey.SMTP_FROM));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            sender.send(message);
            log.info("Sent '{}' to {}", subject, to);
        } catch (Exception e) {
            throw new MailException("E-poçt göndərilmədi: " + e.getMessage(), e);
        }
    }

    /**
     * Built per send rather than kept as a bean: the settings can change from the admin panel at
     * any time, and a cached sender would keep using the credentials it was born with.
     */
    private JavaMailSenderImpl sender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(settings.get(SettingKey.SMTP_HOST));
        sender.setPort(Integer.parseInt(settings.get(SettingKey.SMTP_PORT)));
        sender.setUsername(settings.get(SettingKey.SMTP_USERNAME));
        sender.setPassword(settings.get(SettingKey.SMTP_PASSWORD));
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        // Without a timeout a stalled SMTP server holds the request thread until the client
        // gives up, which turns "create a user" into a hang.
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        return sender;
    }

    public static class MailException extends RuntimeException {
        public MailException(String message) {
            super(message);
        }

        public MailException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Kept apart from a send failure on purpose. "No SMTP account yet" is a state we ship in and
     * expect - it must not be logged as an error with a stack trace, and the caller may legitimately
     * decide to carry on without mail (see the show-the-password-once fallback when creating a user).
     */
    public static class MailNotConfiguredException extends MailException {
        public MailNotConfiguredException(String message) {
            super(message);
        }
    }
}
