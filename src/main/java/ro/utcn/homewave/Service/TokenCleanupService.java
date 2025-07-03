package ro.utcn.homewave.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TokenCleanupService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Scheduled(fixedRate = 60_000)
    public void deleteExpiredTokens() {
        jdbcTemplate.update("DELETE FROM add_user_codes WHERE expires_at < NOW()");
        jdbcTemplate.update("DELETE FROM password_reset_tokens WHERE expires_at < NOW()");
        jdbcTemplate.update("DELETE FROM email_verification_codes WHERE expires_at < NOW()");
    }
}

