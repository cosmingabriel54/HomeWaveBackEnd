package ro.utcn.homewave.Dao;
import java.net.InetAddress;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;
import ro.utcn.homewave.Service.MqttService;

import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Repository("ro.utcn.homewave.LightDao")
public class LightDataAccessService implements LightDao {
    private final RestTemplate restTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final MqttService mqttService;

    @Autowired
    public LightDataAccessService(RestTemplate restTemplate, JdbcTemplate jdbcTemplate, MqttService mqttService) {
        this.restTemplate = restTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.mqttService = mqttService;
    }

    @Override
    public boolean getLightStatus(String mac_address) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT status FROM light_control WHERE mac_address = ?", Boolean.class, mac_address));
    }

    @Override
    public void turnOffLight(String mac_address) {
        mqttService.sendCommand(mac_address, "turn_off");
    }

    @Override
    public void turnOnLight(String mac_address) {
        mqttService.sendCommand(mac_address, "turn_on");
    }

    @Override
    public String registerDevice(String ipaddress,String mac_address, String uuid,String roomid) {
        String sql="SELECT count(*) FROM uuids u JOIN houses h ON u.iduser = h.iduser JOIN rooms r ON h.id = r.houseid WHERE u.uuid = ? AND r.id = ?";
        if(!Objects.equals(jdbcTemplate.queryForObject(sql, Integer.class, uuid, Integer.valueOf(roomid)), 0)) {
            if(Objects.equals(jdbcTemplate.queryForObject("SELECT count(*) from light_control where ip_address=?", Integer.class, ipaddress), 0)) {
                jdbcTemplate.update("INSERT INTO light_control (ip_address,room_id,mac_address) VALUES (?,?,?)", ipaddress,roomid,mac_address);
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
            d.ip_address,
            d.mac_address
        FROM houses h
        LEFT JOIN rooms r ON r.houseid = h.id
        LEFT JOIN light_control d ON d.room_id = r.id
        WHERE h.iduser = ?
        ORDER BY h.id, r.id, d.id
   \s""";

        return jdbcTemplate.queryForList(sql, userId);
    }

    @Override
    public String registerToQueue(String device_hash, String ipaddress, String mac_address) {
        final Pattern MAC_ADDRESS_PATTERN = Pattern.compile("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$");
        try {
            InetAddress.getByName(ipaddress);
            if(MAC_ADDRESS_PATTERN.matcher(mac_address).matches()){
                if(Objects.equals(jdbcTemplate.queryForObject("SELECT count(*) from light_control where ip_address=?", Integer.class, ipaddress), 0)) {
                        jdbcTemplate.update("INSERT INTO provisioning_queue (device_hash,ip_address,mac_address,delete_time) VALUES (?,?,?,NOW() + INTERVAL '2 minutes')", device_hash,ipaddress,mac_address);
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
                "SELECT COUNT(*) FROM provisioning_queue WHERE device_hash = ?",
                Integer.class, device_hash
        );

        if (count == null || count == 0) {
            return null;
        }

        return jdbcTemplate.queryForMap(
                "SELECT ip_address, mac_address FROM provisioning_queue WHERE device_hash = ?",
                device_hash
        );
    }


}
