package ro.utcn.homewave.Dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Repository("ro.utcn.homewave.Dao.LoginDao")
public class LoginDataAccessService implements LoginDao {
    public final JdbcTemplate jdbcTemplate;
    public final EmailDao emailDao;
    @Autowired
    public LoginDataAccessService(JdbcTemplate jdbcTemplate, EmailDao emailDao) {
        this.jdbcTemplate = jdbcTemplate;
        this.emailDao = emailDao;
    }
    @Override
    public String login(String usernameOrEmail, String password) {
        String userQuery = "SELECT id, sha_password FROM users WHERE username = ? OR email = ?";
        String userId;
        String hashedPassword;

        try {
            Map<String, Object> result = jdbcTemplate.queryForMap(userQuery, usernameOrEmail, usernameOrEmail);
            userId = String.valueOf(result.get("id"));
            hashedPassword = (String) result.get("sha_password");
            if (!BCrypt.checkpw(password, hashedPassword)) {
                return "Eroare: Parolă greșită";
            }

        } catch (EmptyResultDataAccessException e) {
            return "Eroare: User inexistent";
        }

        Integer uuidCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM uuids WHERE iduser = ?", Integer.class, Integer.valueOf(userId));

        if (Objects.equals(uuidCount,0)) {
            String uuid = generateUID32();
            jdbcTemplate.update("INSERT INTO uuids(iduser, uuid) VALUES (?, ?)", Integer.valueOf(userId), uuid);
            return uuid;
        }

        return jdbcTemplate.queryForObject(
                "SELECT uuid FROM uuids WHERE iduser = ?", String.class, Integer.valueOf(userId));
    }

    @Override
    public String register(String username, String password, String email) {
        String uuid = generateUID32();
        if (username == null || username.trim().isEmpty() ||
                email == null || email.trim().isEmpty() ||
                password == null || password.trim().isEmpty()) {
            return "Eroare: Invalid input. All fields are required.";
        }
        Integer existingUserCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ? OR email=?", Integer.class, username,email);
        if (existingUserCount != null && existingUserCount > 0) {
            return "Eroare: User existent";
        }
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt(12));
        Integer userId = jdbcTemplate.queryForObject(
                "INSERT INTO users(username, sha_password, email) VALUES (?, ?, ?) RETURNING id",
                Integer.class, username, hashedPassword, email
        );
        if (userId == null) {
            return "Eroare: Înregistrare eșuată";
        }
        jdbcTemplate.update("INSERT INTO uuids(uuid, iduser) VALUES (?, ?)", uuid, userId);

        return uuid;
    }


    @Override
    public String twofacodeEmail(String email) {
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
                    new ClassPathResource("templates/TwoFATemplate.html").getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            e.printStackTrace();
            return "Eroare: Nu se poate citi template-ul";
        }
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(15);
        jdbcTemplate.update("insert into twofa_tokens(user_id, token, expires_at) values (?,?,?)",Integer.valueOf(userId),code, Timestamp.valueOf(expiryTime));
        String htmlBody = template.replace("${code}", code);
        if(emailDao.sendMessage(email,"Verification Code",htmlBody).contains("Failed")){
            return "Eroare: Nu s-a putut trimite email ul";
        }
        return uuid;
    }


    @Override
    public String logout(String uuid) {
        if(Objects.equals(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM uuids WHERE uuid = ?", Integer.class, uuid), 0)) {
        jdbcTemplate.update("DELETE FROM uuids WHERE uuid = ?", uuid);
        return "Userul a fost deconectat";
        }
        return "Eroare: Userul nu a fost deconectat";
    }
    @Override
    public String regenerateToken(String olduuid) {
        String newuuid = generateUID32();
        if(!Objects.equals(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM uuids WHERE uuid = ?", Integer.class, newuuid), 0)) {
            jdbcTemplate.update("UPDATE uuids SET uuid = ? WHERE uuid = ?", newuuid, olduuid);
            return newuuid;
        }
        return "Eroare:User inexistent";

    }

    @Override
    public String existingUsername(String username) {
        if(Objects.equals(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, username), 0)) {
            return "Userul nu exista";
        }
        return "Eroare: User existent";
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
        if(!Objects.equals(jdbcTemplate.queryForObject("select count(*) from twofa_tokens where user_id=? and token=?",Integer.class,Integer.valueOf(userId),code),0)){
            jdbcTemplate.update("delete from twofa_tokens where user_id=? and token=?",Integer.valueOf(userId),code);
            return "Success";
        }
        return "Eroare: Cod incorect";
    }
    public static String generateUID32() {
        UUID uuid = UUID.randomUUID();
        ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[16]);
        byteBuffer.putLong(uuid.getMostSignificantBits());
        byteBuffer.putLong(uuid.getLeastSignificantBits());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(byteBuffer.array());
    }
}
