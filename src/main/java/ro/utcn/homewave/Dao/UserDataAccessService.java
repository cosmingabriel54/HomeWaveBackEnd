package ro.utcn.homewave.Dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

@Repository("ro.utcn.homewave.Dao.UserDao")
public class UserDataAccessService implements UserDao {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EmailDao emailDao;
    @Autowired
    public UserDataAccessService(JdbcTemplate jdbcTemplate, EmailDao emailDao) {
        this.jdbcTemplate = jdbcTemplate;
        this.emailDao = emailDao;
    }

    @Override
    public String userInfo(String uuid) {
        if(Objects.equals(jdbcTemplate.queryForObject("select count(*) from uuids where uuid=?",Integer.class,uuid),0))
            return null;
        String sql = "SELECT u.username, u.email FROM uuids d JOIN users u ON d.iduser = u.id WHERE d.uuid = ?";

        Map<String, Object> result = jdbcTemplate.queryForMap(sql, uuid);

        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new RuntimeException("Error converting to JSON", e);
        }
    }

    @Override
    public String passwordResetEmail(String email) {
        if(Objects.equals(jdbcTemplate.queryForObject("select count(*) from users where email=?",Integer.class,email),0)){
            return "Eroare: User inexistent";
        }
        String userId=jdbcTemplate.queryForObject("select id from users where email=?",String.class,email);
        String code = String.valueOf(100000 + new SecureRandom().nextInt(900000));
        String template;
        assert userId != null;
        String uuid=jdbcTemplate.queryForObject("select uuid from uuids where iduser=?",String.class,Integer.valueOf(userId));
        try {
            template = new String(
                    new ClassPathResource("templates/ForgotPasswordTemplate.html").getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            e.printStackTrace();
            return "Eroare: Nu se poate citi template-ul";
        }
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(15);
        jdbcTemplate.update("insert into password_reset_tokens(user_id, token, expires_at) values (?,?,?)",Integer.valueOf(userId),code, Timestamp.valueOf(expiryTime));
        String htmlBody = template.replace("${code}", code);
        if(emailDao.sendMessage(email,"Password reset password",htmlBody).contains("Failed")){
            return "Eroare: Nu s-a putut trimite email ul";
        }
        return uuid;
    }

    @Override
    public String verifyCode(String code, String uuid) {
        Integer uuidCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM uuids WHERE uuid = ?",
                Integer.class,
                uuid
        );

        if (uuidCount == null || uuidCount == 0) {
            return "Eroare: User inexistent";
        }
        String userId = jdbcTemplate.queryForObject(
                "SELECT iduser FROM uuids WHERE uuid = ?",
                String.class,
                uuid
        );
        assert userId != null;
        if(!Objects.equals(jdbcTemplate.queryForObject("select count(*) from password_reset_tokens where user_id=? and token=?",Integer.class,Integer.valueOf(userId),code),0)){
            return "Success";
        }
        return "Eroare: Cod incorect";
    }

    @Override
    public String resetPassword(String code, String newPassword) {
        String userid;
        if(!Objects.equals(jdbcTemplate.queryForObject("select count(*) from password_reset_tokens where token=?",Integer.class,code),0)) {
            userid = String.valueOf(jdbcTemplate.queryForObject("select user_id from password_reset_tokens where token=?", Integer.class, code));
        }else{
            return "Eroare: Cod inexistent";
        }
        System.out.println(newPassword);
        jdbcTemplate.update("update users set sha_password=? where id=?",sha256(newPassword),userid);
        return "Actualizare cu succes";
    }

    @Override
    public String changePassword(String uuid, String newPassword, String oldPassword) {
        return "";
    }

    @Override
    public String changeEmail(String uuid, String email) {
        return "";
    }
    public static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
