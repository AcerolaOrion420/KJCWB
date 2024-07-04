package org.kjcwb.Packages.Services;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
public class EmailService {
    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final String SMTP_PORT = "587";
    private static final String USERNAME = "22BCAB69@kristujayanti.com";
    private static final String PASSWORD = "klhclbuivcwengmj";
    private static final String VALID_DOMAIN = "kristujayanti.com";

    public static boolean sendEmail(String to, String subject, String text) {
        boolean flag;
        if (!isValidEmail(to)) {
            throw new IllegalArgumentException("Invalid email domain");
        }

        // SMTP properties
        Properties properties = new Properties();
        properties.put("mail.smtp.auth", true);
        properties.put("mail.smtp.starttls.enable", true);
        properties.put("mail.smtp.port", SMTP_PORT);
        properties.put("mail.smtp.host", SMTP_HOST);


        // Session
        Session session = Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(USERNAME,PASSWORD);
            }
        });


        try {
            Message message = new MimeMessage(session);
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setFrom(new InternetAddress(USERNAME));
            message.setSubject(subject);
            message.setText(text);
            Transport.send(message);
            flag = true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return flag;
    }
    public static boolean isValidEmail(String email) {
        return email != null && email.endsWith("@" + VALID_DOMAIN);
    }
}
