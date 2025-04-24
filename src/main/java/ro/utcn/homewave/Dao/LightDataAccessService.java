package ro.utcn.homewave.Dao;
import java.net.InetAddress;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;

import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Repository("ro.utcn.homewave.LightDao")
public class LightDataAccessService implements LightDao {
    private final RestTemplate restTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public LightDataAccessService(RestTemplate restTemplate, JdbcTemplate jdbcTemplate) {
        this.restTemplate = restTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String getLightStatus(String ipaddress) {
        String url = "http://"+ipaddress+":80/health";
        try {
            return restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            return "Eroare: Failed to connect to ESP32: " + e.getMessage();
        }
    }

    @Override
    public String turnOffLight(String ipaddress) {
        String url = "http://"+ipaddress+":80/control?state=0";
        try {
            return restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            return "Eroare: Failed to connect to ESP32: " + e.getMessage();
        }
    }

    @Override
    public String turnOnLight(String ipaddress) {
        String url = "http://"+ipaddress+":80/control?state=1";
        try {
            return restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            return "Eroare: Failed to connect to ESP32: " + e.getMessage();
        }
    }

    @Override
    public String registerDevice(String ipaddress, String uuid,String roomid) {
        String sql="SELECT count(*) FROM uuids u JOIN houses h ON u.iduser = h.iduser JOIN rooms r ON h.id = r.houseid WHERE u.uuid = ? AND r.id = ?";
        if(!Objects.equals(jdbcTemplate.queryForObject(sql, Integer.class, uuid, roomid), 0)) {
            if(Objects.equals(jdbcTemplate.queryForObject("SELECT count(*) from homewave.light_control where ip_address=?", Integer.class, ipaddress), 0)) {
                jdbcTemplate.update("INSERT INTO homewave.light_control (ip_address,room_id) VALUES (?,?)", ipaddress,roomid);
                return "Succes";
            }
            return "Eroare: Device already registered";
        }
        return "Eroare: Room for userid not found";
    }
    @Override
    public List<Map<String, Object>> getFullDeviceStructure(String userId) {
        String sql = """
        SELECT
            h.id AS house_id,
            h.house_name,
            r.id AS room_id,
            r.room_name,
            d.id AS device_id,
            d.ip_address
        FROM houses h
        LEFT JOIN rooms r ON r.houseid = h.id
        LEFT JOIN light_control d ON d.room_id = r.id
        WHERE h.iduser = ?
        ORDER BY h.id, r.id, d.id
    """;

        return jdbcTemplate.queryForList(sql, userId);
    }

    @Override
    public String registerToQueue(String device_hash, String ipaddress, String mac_address) {
        final Pattern MAC_ADDRESS_PATTERN = Pattern.compile("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$");
        try {
            InetAddress.getByName(ipaddress);
            if(MAC_ADDRESS_PATTERN.matcher(mac_address).matches()){
                if(Objects.equals(jdbcTemplate.queryForObject("SELECT count(*) from homewave.light_control where ip_address=?", Integer.class, ipaddress), 0)) {
                        jdbcTemplate.update("INSERT INTO homewave.provisioning_queue (device_hash,ip_address,mac_address,delete_time) VALUES (?,?,?,NOW() + INTERVAL 2 MINUTE)", device_hash,ipaddress,mac_address);
                    return "Succes";
                }
                return "Eroare: Device already registered";
            }
            return "Eroare: Invalid MAC address";
        } catch (UnknownHostException e) {
            return "Eroare: Invalid IP address";
        }


    }

    @Override
    public Map<String, Object> getQueuedDevice(String device_hash) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM homewave.provisioning_queue WHERE device_hash = ?",
                Integer.class, device_hash
        );

        if (count == null || count == 0) {
            return null;
        }

        return jdbcTemplate.queryForMap(
                "SELECT ip_address, mac_address FROM homewave.provisioning_queue WHERE device_hash = ?",
                device_hash
        );
    }


}
