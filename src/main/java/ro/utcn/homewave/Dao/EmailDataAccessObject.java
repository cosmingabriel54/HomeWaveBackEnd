package ro.utcn.homewave.Dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;

@Repository("ro.utcn.homewave.Dao.EmailDao")
public class EmailDataAccessObject implements EmailDao {
    private final JavaMailSenderImpl emailSender;

    @Autowired
    public EmailDataAccessObject(JavaMailSenderImpl emailSender) {
        this.emailSender = emailSender;
    }

    public String sendMessage(String to, String subject, String text) {
        this.emailSender.setDefaultEncoding("UTF-8");

        StringBuilder result = new StringBuilder();
        boolean allSuccess = true;
        try {
            MimeMessage message = this.emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(new InternetAddress("smtp.gmail.com", "SmartHome Notifier"));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, true);

            this.emailSender.send(message);
            result.append(" Email sent to: ").append(to).append("\n");

        } catch (MessagingException | UnsupportedEncodingException e) {
            result.append(" Failed to send to: ").append(to).append("\n");
            allSuccess = false;
        }
        return allSuccess
                ? "All emails sent successfully."
                : result.toString().trim();
    }

}
