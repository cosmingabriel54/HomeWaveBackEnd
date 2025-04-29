package ro.utcn.homewave.Schedule;

import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;

@CrossOrigin(origins = {"http://localhost:3000","http://localhost:5173"})
@RestController
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
        return jdbcTemplate.update(sql);
    }
}
