package ro.utcn.homewave.Dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Repository;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

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

        String sql = """
        SELECT u.username, u.email,u.verification_status, u.id,
               pp.profile_picture, pp.profile_picture_mime_type, pp.profile_picture_size
        FROM uuids d
        JOIN users u ON d.iduser = u.id\s
        LEFT JOIN profile_pictures pp ON u.id = pp.iduser
        WHERE d.uuid = ?
   \s""";

        try {
            Map<String, Object> result = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("username", rs.getString("username"));
                userInfo.put("email", rs.getString("email"));
                userInfo.put("id", rs.getInt("id"));
                userInfo.put("verification_status",rs.getBoolean("verification_status"));

                byte[] profilePictureData = rs.getBytes("profile_picture");
                if (profilePictureData != null) {
                    String base64Image = Base64.getEncoder().encodeToString(profilePictureData);
                    userInfo.put("profilePicture", base64Image);
                    userInfo.put("profilePictureMimeType", rs.getString("profile_picture_mime_type"));
                    userInfo.put("profilePictureSize", rs.getInt("profile_picture_size"));
                } else {
                    userInfo.put("profilePicture", null);
                    userInfo.put("profilePictureMimeType", null);
                    userInfo.put("profilePictureSize", null);
                }

                return userInfo;
            }, uuid);

            return objectMapper.writeValueAsString(result);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Error converting to JSON", e);
        }
    }

    @Override
    public String passwordResetEmail(String email) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
        System.out.println(email);
        if (count == null || count == 0) {
            return "Eroare: User inexistent";
        }

        String userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", String.class, email);
        assert userId != null;

        String uuid = jdbcTemplate.queryForObject(
                "SELECT uuid FROM uuids WHERE iduser = ?", String.class, Integer.valueOf(userId));

        String code = String.valueOf(100000 + new SecureRandom().nextInt(900000));

        String secret = System.getenv("RESET_CODE_SECRET");
        String codeHash = hmacSHA256(secret, code);

        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(15);
        jdbcTemplate.update(
                "INSERT INTO password_reset_tokens(user_id, token, expires_at) VALUES (?, ?, ?)",
                Integer.valueOf(userId), codeHash, Timestamp.valueOf(expiryTime));

        String template;
        try {
            template = new String(
                    new ClassPathResource("templates/ForgotPasswordTemplate.html")
                            .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            return "Eroare: Nu se poate citi template-ul";
        }

        String htmlBody = template.replace("${code}", code);
        if (emailDao.sendMessage(email, "Password reset code", htmlBody).contains("Failed")) {
            return "Eroare: Nu s-a putut trimite email-ul";
        }

        return uuid;
    }


    @Override
    public String verifyCode(String code, String uuid) {
        Integer uuidCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM uuids WHERE uuid = ?", Integer.class, uuid);
        if (uuidCount == null || uuidCount == 0) {
            return "Eroare: User inexistent";
        }

        String userId = jdbcTemplate.queryForObject(
                "SELECT iduser FROM uuids WHERE uuid = ?", String.class, uuid);
        assert userId != null;

        String secret = System.getenv("RESET_CODE_SECRET");
        String providedHash = hmacSHA256(secret, code);

        Integer valid = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ? AND token = ? AND expires_at > NOW()",
                Integer.class, Integer.valueOf(userId), providedHash);

        if (valid != null && valid > 0) {
            jdbcTemplate.update(
                    "DELETE FROM password_reset_tokens WHERE user_id = ? AND token = ?",
                    Integer.valueOf(userId), providedHash);

            return "Success";
        }

        return "Eroare: Cod incorect sau expirat";
    }


    @Override
    public String resetPassword(String code, String newPassword) {
        String userid;
        if(!Objects.equals(jdbcTemplate.queryForObject("select count(*) from password_reset_tokens where token=?",Integer.class,code),0)) {
            userid = String.valueOf(jdbcTemplate.queryForObject("select user_id from password_reset_tokens where token=?", Integer.class, code));
        }else{
            return "Eroare: Cod inexistent";
        }
        jdbcTemplate.update("update users set sha_password=? where id=?", BCrypt.hashpw(newPassword, BCrypt.gensalt(12)),userid);
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

    @Override
    public String generateAddUserCode(String houseid) {
        String code = String.valueOf(100000 + new SecureRandom().nextInt(900000));

        String secret = System.getenv("RESET_CODE_SECRET");
        String codeHash = hmacSHA256(secret, code);

        Timestamp expiresAt = Timestamp.from(Instant.now().plus(Duration.ofMinutes(15)));

        jdbcTemplate.update(
                "INSERT INTO add_user_codes(houseid, code, expires_at) VALUES (?, ?, ?)",
                houseid, codeHash, expiresAt
        );

        return code;
    }


    @Override
    public String addUserWithCode(String uuid, String code) {
        String userId = getIdUser(uuid);
        if (userId == null) return "Error: Invalid user.";

        String secret = System.getenv("RESET_CODE_SECRET");
        String codeHash = hmacSHA256(secret, code);

        Integer codeExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM add_user_codes WHERE code = ?",
                Integer.class,
                codeHash
        );

        if (codeExists == null || codeExists == 0) {
            return "Error: Invalid code.";
        }

        String houseId = jdbcTemplate.queryForObject(
                "SELECT houseid FROM add_user_codes WHERE code = ?",
                String.class,
                codeHash
        );

        Integer alreadyAdded = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_houses WHERE user_id = ? AND house_id = ?",
                Integer.class,
                Integer.valueOf(userId),
                Integer.valueOf(houseId)
        );

        if (alreadyAdded != null && alreadyAdded == 0) {
            jdbcTemplate.update(
                    "INSERT INTO user_houses (user_id, house_id) VALUES (?, ?)",
                    Integer.valueOf(userId),
                    Integer.valueOf(houseId)
            );
            jdbcTemplate.update("DELETE FROM add_user_codes WHERE code = ?", codeHash);
            return "Success: Access granted.";
        } else {
            return "Error: User already has access.";
        }
    }


    @Override
    public String getUsers(String houseid) {
        String query = """
    SELECT u.id, u.username, u.email, 
           pp.profile_picture, pp.profile_picture_mime_type, pp.profile_picture_size
    FROM users u
    JOIN user_houses uh ON u.id = uh.user_id
    LEFT JOIN profile_pictures pp ON u.id = pp.iduser
    WHERE uh.house_id = ?
""";

        List<JSONObject> users = jdbcTemplate.query(query, new Object[]{Integer.parseInt(houseid)}, (rs, rowNum) -> {
            JSONObject user = new JSONObject();
            user.put("id", rs.getInt("id"));
            user.put("username", rs.getString("username"));
            user.put("email", rs.getString("email"));

            byte[] profilePictureData = rs.getBytes("profile_picture");
            if (profilePictureData != null) {
                String base64Image = Base64.getEncoder().encodeToString(profilePictureData);
                user.put("profilePicture", base64Image);
                user.put("profilePictureMimeType", rs.getString("profile_picture_mime_type"));
                user.put("profilePictureSize", rs.getInt("profile_picture_size"));
            } else {
                user.put("profilePicture", null);
                user.put("profilePictureMimeType", null);
                user.put("profilePictureSize", null);
            }

            return user;
        });

        JSONArray resultArray = new JSONArray();
        resultArray.addAll(users);

        return resultArray.toJSONString();
    }

    @Override
    public String uploadProfilePicture(MultipartFile file, String uuid) {
        String iduser = getIdUser(uuid);
        if(iduser == null){
            return "Error:User inexistent";
        }

        try {
            if (file.isEmpty()) {
                return "Error:File is empty";
            }

            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return "Error:File must be an image";
            }

            byte[] imageData = file.getBytes();
            int userId = Integer.parseInt(iduser);

            String checkSql = "SELECT COUNT(*) FROM profile_pictures WHERE iduser = ?";
            int count = jdbcTemplate.queryForObject(checkSql, Integer.class, userId);

            if (count > 0) {
                String updateSql = "UPDATE profile_pictures SET profile_picture = ?, profile_picture_mime_type = ?, profile_picture_size = ? WHERE iduser = ?";
                jdbcTemplate.update(updateSql, imageData, contentType, (int) file.getSize(), userId);
            } else {
                String insertSql = "INSERT INTO profile_pictures (iduser, profile_picture, profile_picture_mime_type, profile_picture_size) VALUES (?, ?, ?, ?)";
                jdbcTemplate.update(insertSql, userId, imageData, contentType, (int) file.getSize());
            }

            return "Success: Profile picture uploaded successfully";

        } catch (Exception e) {
            return "Error: Upload failed: " + e.getMessage();
        }
    }

    @Override
    public String sendEmailVerificationCode(String email) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
        System.out.println(email);
        if (count == null || count == 0) {
            return "Eroare: User inexistent";
        }

        String userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", String.class, email);
        assert userId != null;

        String uuid = jdbcTemplate.queryForObject(
                "SELECT uuid FROM uuids WHERE iduser = ?", String.class, Integer.valueOf(userId));

        String code = String.valueOf(100000 + new SecureRandom().nextInt(900000));

        String secret = System.getenv("RESET_CODE_SECRET");
        String codeHash = hmacSHA256(secret, code);

        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(15);
        jdbcTemplate.update(
                "INSERT INTO email_verification_codes(user_id, code, expires_at) VALUES (?, ?, ?)",
                Integer.valueOf(userId), codeHash, Timestamp.valueOf(expiryTime));

        String template;
        try {
            template = new String(
                    new ClassPathResource("templates/VerifyEmailTemplate.html")
                            .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            return "Eroare: Nu se poate citi template-ul";
        }

        String htmlBody = template.replace("${code}", code);
        if (emailDao.sendMessage(email, "Verify Email", htmlBody).contains("Failed")) {
            return "Eroare: Nu s-a putut trimite email-ul";
        }

        return uuid;
    }

    @Override
    public String verfiyEmail(String uuid, String code) {
        Integer uuidCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM uuids WHERE uuid = ?", Integer.class, uuid);
        if (uuidCount == null || uuidCount == 0) {
            return "Eroare: User inexistent";
        }

        String userId = jdbcTemplate.queryForObject(
                "SELECT iduser FROM uuids WHERE uuid = ?", String.class, uuid);
        assert userId != null;

        String secret = System.getenv("RESET_CODE_SECRET");
        String providedHash = hmacSHA256(secret, code);

        Integer valid = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_verification_codes WHERE user_id = ? AND code = ? AND expires_at > NOW()",
                Integer.class, Integer.valueOf(userId), providedHash);

        if (valid != null && valid > 0) {
            jdbcTemplate.update(
                    "DELETE FROM email_verification_codes WHERE user_id = ? AND code = ?",
                    Integer.valueOf(userId), providedHash);
            jdbcTemplate.update(
                    "update users set verification_status=true where id = ?",
                    Integer.valueOf(userId));
            return "Success";
        }

        return "Eroare: Cod incorect sau expirat";
    }



    public String getIdUser(String uuid){
        if(Objects.equals(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM uuids WHERE uuid = ?", Integer.class, uuid), 0)) {
            return null;
        }
        return String.valueOf(jdbcTemplate.queryForObject("SELECT iduser FROM uuids WHERE uuid = ?", Integer.class, uuid));
    }
    private String hmacSHA256(String secretKey, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate HMAC", e);
        }
    }

}
