package ro.utcn.homewave.Dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

@Repository("ro.utcn.homewave.Dao.LoginDao")
public class LoginDataAccessService implements LoginDao {
    public final JdbcTemplate jdbcTemplate;
    @Autowired
    public LoginDataAccessService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    @Override
    public String login(String usernameOrEmail, String password) {
        String hashedPassword = sha256(password);
        // Query to fetch user ID based on either username or email
        String userIdQuery = "SELECT id FROM homewave.user WHERE username = ? AND sha_password = ? OR email = ? AND sha_password = ?";
        String userId;
        System.out.println(usernameOrEmail+" "+hashedPassword);
        // Hash the password once
        try {
            // Check if user exists by username or email
            userId = jdbcTemplate.queryForObject(userIdQuery, String.class, usernameOrEmail, hashedPassword,usernameOrEmail,hashedPassword);
        } catch (EmptyResultDataAccessException e) {
            e.printStackTrace();
            return "Eroare: User inexistent"; // User not found
        }
        System.out.println(userId);
        if(Objects.equals(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM homewave.uuids WHERE iduser = ?", Integer.class, userId),0)){
            String uuid=generateUID32();
            jdbcTemplate.update("INSERT INTO homewave.uuids(iduser, uuid) VALUES (?,?)",userId,uuid);
            return uuid;
        }
        return jdbcTemplate.queryForObject("SELECT uuid FROM homewave.uuids where iduser=?",String.class,userId );
    }



    @Override
    public String register(String username, String password, String email, String phonenumber) {
        String uuid = generateUID32();

        // Ensure that all fields are provided
        if (username == null || username.trim().isEmpty() || email == null || email.trim().isEmpty() || phonenumber == null || phonenumber.trim().isEmpty()) {
            return "Eroare: Invalid input. All fields are required.";
        }

        // Check if the username already exists
        if (Objects.equals(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM homewave.user WHERE username = ?", Integer.class, username), 0)) {
            // Insert new user with hashed password
            jdbcTemplate.update("INSERT INTO homewave.user(username, sha_password, email, phone_number) VALUES(?,?,?,?)", username, sha256(password), email, phonenumber);
            String iduser = jdbcTemplate.queryForObject("SELECT last_insert_id()", String.class);
            jdbcTemplate.update("INSERT INTO homewave.uuids(uuid, iduser) VALUES(?,?)", uuid, iduser);
            return uuid; // Return UUID on successful registration
        } else {
            return "Eroare: User existent"; // Username already exists
        }
    }



    @Override
    public String logout(String uuid) {
        if(Objects.equals(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM homewave.uuids WHERE uuid = ?", Integer.class, uuid), 0)) {
        jdbcTemplate.update("DELETE FROM homewave.uuids WHERE uuid = ?", uuid);
        return "Userul a fost deconectat";
        }
        return "Eroare: Userul nu a fost deconectat";
    }
    @Override
    public String regenerateToken(String olduuid) {
        String newuuid = generateUID32();
        if(!Objects.equals(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM homewave.uuids WHERE uuid = ?", Integer.class, newuuid), 0)) {
            jdbcTemplate.update("UPDATE homewave.uuids SET uuid = ? WHERE uuid = ?", newuuid, olduuid);
            return newuuid;
        }
        return "Eroare:User inexistent";

    }

    @Override
    public String existingUsername(String username) {
        if(Objects.equals(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM homewave.user WHERE username = ?", Integer.class, username), 0)) {
            return "Userul nu exista";
        }
        return "Eroare: User existent";
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
