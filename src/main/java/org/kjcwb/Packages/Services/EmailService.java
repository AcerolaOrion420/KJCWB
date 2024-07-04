package org.kjcwb.Packages.Services;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

public class EmailService extends AbstractVerticle {
    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final String SMTP_PORT = "587";
    private static final String USERNAME = "22BCAB69@kristujayanti.com";
    private static final String PASSWORD = "klhclbuivcwengmj";
    private static final String VALID_DOMAIN = "kristujayanti.com";

    private final String to;
    private final String subject;
    private final String text;

    public EmailService(String to, String subject, String text) {
        this.to = to;
        this.subject = subject;
        this.text = text;
    }

    public static boolean isValidEmail(String email) {
        return email != null && email.endsWith("@" + VALID_DOMAIN);
    }

    private static Session createSession() {
        Properties properties = new Properties();
        properties.put("mail.smtp.auth", true);
        properties.put("mail.smtp.starttls.enable", true);
        properties.put("mail.smtp.port", SMTP_PORT);
        properties.put("mail.smtp.host", SMTP_HOST);

        return Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(USERNAME, PASSWORD);
            }
        });
    }

    @Override
    public void start(Promise<Void> startPromise) {
        if (!isValidEmail(to)) {
            startPromise.fail("Invalid email domain");
            return;
        }

        Session session = createSession();

        try {
            Message message = new MimeMessage(session);
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setFrom(new InternetAddress(USERNAME));
            message.setSubject(subject);
            message.setText(text);
            Transport.send(message);
            startPromise.complete();
        } catch (Exception e) {
            startPromise.fail(e);
        }
    }

    public static void sendEmailAsync(Vertx vertx, String to, String subject, String text) {
        vertx.deployVerticle(new EmailService(to, subject, text));
    }
}
