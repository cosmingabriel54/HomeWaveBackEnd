package ro.utcn.homewave.Dao;
import java.net.InetAddress;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;
import ro.utcn.homewave.Service.MqttService;

import java.net.UnknownHostException;
import java.util.*;
import java.util.regex.Pattern;

@Repository("ro.utcn.homewave.LightDao")
public class DeviceDataAccessService implements DeviceDao {
    private final RestTemplate restTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final MqttService mqttService;

    @Autowired
    public DeviceDataAccessService(RestTemplate restTemplate, JdbcTemplate jdbcTemplate, MqttService mqttService) {
        this.restTemplate = restTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.mqttService = mqttService;
    }

    @Override
    public boolean getLightStatus(String mac_address) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT status FROM light_control WHERE mac_address = ?", Boolean.class, mac_address));
    }

    @Override
    public boolean getLockStatus(String mac_address) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT status FROM lock_control WHERE mac_address = ?", Boolean.class, mac_address));
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
    public void lockDoor(String mac_address) {
        mqttService.sendCommand(mac_address,"lock_door");
    }

    @Override
    public void unlockDoor(String mac_address) {
        mqttService.sendCommand(mac_address,"unlock_door");
    }

    @Override
    public String registerDevice(String ipaddress,String mac_address, String uuid,String roomid,String deviceType) {
        String sql="SELECT count(*) FROM uuids u JOIN houses h ON u.iduser = h.iduser JOIN rooms r ON h.id = r.houseid WHERE u.uuid = ? AND r.id = ?";
        boolean power_saving_mode = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT CASE WHEN EXISTS (" +
                        "  SELECT 1 FROM light_control " +
                        "  WHERE mac_address = ? AND power_saving_mode = true " +
                        "  UNION ALL " +
                        "  SELECT 1 FROM lock_control " +
                        "  WHERE mac_address = ? AND power_saving_mode = true " +
                        "  LIMIT 1 " +
                        ") THEN true ELSE false END",
                Boolean.class,
                mac_address, mac_address));
        if(!Objects.equals(jdbcTemplate.queryForObject(sql, Integer.class, uuid, Integer.valueOf(roomid)), 0)) {
            if(deviceType.equals("light_control")) {
                if (Objects.equals(jdbcTemplate.queryForObject("SELECT count(*) from light_control where ip_address=?", Integer.class, ipaddress), 0)) {
                    jdbcTemplate.update("INSERT INTO light_control (ip_address,room_id,mac_address,power_saving_mode) VALUES (?,?,?,?)", ipaddress, Integer.valueOf(roomid), mac_address,power_saving_mode);
                    return "Succes";
                }
            }else if(deviceType.equals("lock_control")){
                if (Objects.equals(jdbcTemplate.queryForObject("SELECT count(*) from lock_control where ip_address=?", Integer.class, ipaddress), 0)) {
                    jdbcTemplate.update("INSERT INTO lock_control (ip_address,room_id,mac_address,power_saving_mode) VALUES (?,?,?,?)", ipaddress, Integer.valueOf(roomid), mac_address,power_saving_mode);
                    return "Succes";
                }
            }else{
                return "Eroare:Device type incorrect";
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
        d.mac_address,
        'light_control' AS device_type
    FROM houses h
    JOIN rooms r ON r.houseid = h.id
    JOIN light_control d ON d.room_id = r.id
    WHERE h.iduser = CAST(? AS INTEGER)

    UNION ALL

    SELECT
        h.id AS house_id,
        h.house_name,
        r.id AS room_id,
        r.room_name,
        l.id AS device_id,
        l.ip_address,
        l.mac_address,
        'lock_control' AS device_type
    FROM houses h
    JOIN rooms r ON r.houseid = h.id
    JOIN lock_control l ON l.room_id = r.id
    WHERE h.iduser = CAST(? AS INTEGER)

    ORDER BY house_id, room_id, device_id
""";

        List<Map<String, Object>> flatList = jdbcTemplate.queryForList(sql, userId, userId);

        Map<Integer, Map<String, Object>> houseMap = new LinkedHashMap<>();

        for (Map<String, Object> row : flatList) {
            int houseId = (int) row.get("house_id");
            int roomId = (int) row.get("room_id");

            Map<String, Object> house = houseMap.computeIfAbsent(houseId, id -> {
                Map<String, Object> h = new HashMap<>();
                h.put("house_id", houseId);
                h.put("house_name", row.get("house_name"));
                h.put("rooms", new LinkedHashMap<Integer, Map<String, Object>>());
                return h;
            });

            @SuppressWarnings("unchecked")
            Map<Integer, Map<String, Object>> roomMap = (Map<Integer, Map<String, Object>>) house.get("rooms");

            Map<String, Object> room = roomMap.computeIfAbsent(roomId, id -> {
                Map<String, Object> r = new HashMap<>();
                r.put("room_id", roomId);
                r.put("room_name", row.get("room_name"));
                r.put("devices", new ArrayList<Map<String, Object>>());
                return r;
            });

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> devices = (List<Map<String, Object>>) room.get("devices");

            Map<String, Object> device = new HashMap<>();
            device.put("device_id", row.get("device_id"));
            device.put("ip_address", row.get("ip_address"));
            device.put("mac_address", row.get("mac_address"));
            device.put("device_type", row.get("device_type"));

            devices.add(device);
        }
        List<Map<String, Object>> finalResult = new ArrayList<>();
        for (Map<String, Object> house : houseMap.values()) {
            Map<String, Object> resultHouse = new LinkedHashMap<>();
            resultHouse.put("house_id", house.get("house_id"));
            resultHouse.put("house_name", house.get("house_name"));

            @SuppressWarnings("unchecked")
            Map<Integer, Map<String, Object>> roomMap = (Map<Integer, Map<String, Object>>) house.get("rooms");
            List<Map<String, Object>> roomList = new ArrayList<>(roomMap.values());
            resultHouse.put("rooms", roomList);

            finalResult.add(resultHouse);
        }
        return finalResult;
        
    }


    @Override
    public int removeDevice(String mac_address,String deviceType) {
        jdbcTemplate.update("delete from provisioning_queue where mac_address=?",mac_address);
        if(deviceType.equals("light_control")) {
            return jdbcTemplate.update("delete from light_control where mac_address=?", mac_address);
        }
        else if (deviceType.equals("lock_control")) {
            return jdbcTemplate.update("delete from lock_control where mac_address=?",mac_address);
        }
        Integer stillExists = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM (
                    SELECT mac_address FROM light_control WHERE mac_address = ?
                    UNION
                    SELECT mac_address FROM lock_control WHERE mac_address = ?
                ) AS combined
                """, Integer.class, mac_address, mac_address);

        // Step 3: If no remaining roles, send wipe + delete from provisioning queue
        if (Objects.equals(stillExists,0)) {
            mqttService.sendCommand(mac_address, "wipe");
        }
        return 0;
    }

    @Override
    public void togglePowerSavingMode(String mac_address, String toggle) {
        if(toggle.equals("enable_power_saving")){
            jdbcTemplate.update("update light_control set power_saving_mode = true where mac_address = ?", mac_address);
            jdbcTemplate.update("update lock_control set power_saving_mode = true where mac_address = ?", mac_address);
        }else if(toggle.equals("disable_power_saving")){
            jdbcTemplate.update("update light_control set power_saving_mode = false where mac_address = ?", mac_address);
            jdbcTemplate.update("update lock_control set power_saving_mode = false where mac_address = ?", mac_address);
        }
        mqttService.sendCommand(mac_address, toggle);
    }

    @Override
    public int getPowerSavingMode(String mac_address) {
        if(!Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT power_saving_mode FROM light_control WHERE mac_address = ?", Boolean.class, mac_address)))
            return 0;
        return 1;
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
