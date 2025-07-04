package ro.utcn.homewave.Controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RequestMapping({"/scheduler"})
@RestController
@Api(
        tags = {"Scheduler"}
)
@CrossOrigin(origins = {"*"}, allowCredentials = "false")
public class DeleteProvisioning {
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public DeleteProvisioning(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @ApiOperation("Delete expired tokens")
    @DeleteMapping("/deleteexpired")
    public int deleteExpiredTokens() {
        String sql = "DELETE FROM provisioning_queue WHERE delete_time < CURRENT_TIMESTAMP";
        jdbcTemplate.update(sql);
        sql="DELETE FROM twofa_tokens WHERE expires_at < CURRENT_TIMESTAMP";
        jdbcTemplate.update(sql);
        sql="DELETE FROM password_reset_tokens WHERE expires_at < CURRENT_TIMESTAMP";
        return jdbcTemplate.update(sql);
    }
}
