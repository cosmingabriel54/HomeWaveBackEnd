package ro.utcn.homewave.Dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
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
        String hashedPassword = sha256(password);
        // Query to fetch user ID based on either username or email
        String userIdQuery = "SELECT id FROM users WHERE username = ? AND sha_password = ? OR email = ? AND sha_password = ?";
        String userId;
        System.out.println(usernameOrEmail+" "+hashedPassword);
        // Hash the password once
        try {
            // Check if user exists by username or email
            userId = jdbcTemplate.queryForObject(userIdQuery, String.class, usernameOrEmail, hashedPassword,usernameOrEmail,hashedPassword);
        } catch (EmptyResultDataAccessException e) {
            return "Eroare: User inexistent"; // User not found
        }
        System.out.println(userId);
        assert userId != null;
        if(Objects.equals(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM uuids WHERE iduser = ?", Integer.class, Integer.valueOf(userId)),0)){
            String uuid=generateUID32();
            jdbcTemplate.update("INSERT INTO uuids(iduser, uuid) VALUES (?,?)",Integer.valueOf(userId),uuid);
            return uuid;
        }
        return jdbcTemplate.queryForObject("SELECT uuid FROM uuids where iduser=?",String.class,Integer.valueOf(userId) );
    }



    @Override
    public String register(String username, String password, String email) {
        String uuid = generateUID32();

        // Ensure that all fields are provided
        if (username == null || username.trim().isEmpty() || email == null || email.trim().isEmpty()) {
            return "Eroare: Invalid input. All fields are required.";
        }

        // Check if the username already exists
        if (Objects.equals(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, username), 0)) {
            // Insert new user with hashed password
            String iduser = String.valueOf(jdbcTemplate.queryForObject(
                    "INSERT INTO users(username, sha_password, email) VALUES (?,?,?) RETURNING id",
                    Integer.class,
                    username, sha256(password), email
            ));
            jdbcTemplate.update("INSERT INTO uuids(uuid, iduser) VALUES(?,?)", uuid, Integer.valueOf(iduser));
            return uuid; // Return UUID on successful registration
        } else {
            return "Eroare: User existent"; // Username already exists
        }
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
            return "Success";
        }
        return "Eroare: Cod incorect";
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
    public static String generateUID32() {
        UUID uuid = UUID.randomUUID();
        ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[16]);
        byteBuffer.putLong(uuid.getMostSignificantBits());
        byteBuffer.putLong(uuid.getLeastSignificantBits());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(byteBuffer.array());
    }
}
