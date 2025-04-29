package ro.utcn.homewave.Schedule;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;

@RequestMapping({"/scheduler"})
@RestController
@Api(
        tags = {"Scheduler"}
)
@CrossOrigin(origins = {"http://localhost:3000","http://localhost:5173"})
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
