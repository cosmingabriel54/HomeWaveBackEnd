package ro.utcn.homewave.Dao;
import java.net.InetAddress;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;
import ro.utcn.homewave.Service.MqttService;

import java.net.UnknownHostException;
import java.sql.ResultSet;
import java.time.LocalDateTime;
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
    public Map<String, Object> getLightStatus(String mac_address) {
        String sql = "SELECT status FROM light_control WHERE mac_address = ?";
        Integer status = jdbcTemplate.queryForObject(sql, Integer.class, mac_address);
        sql="SELECT power_saving_mode FROM light_control WHERE mac_address=?";
        Boolean powerSavingMode=jdbcTemplate.queryForObject(sql,Boolean.class,mac_address);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "turn_on/"+status);
        result.put("power_saving_mode", powerSavingMode);
        return result;
    }

    @Override
    public Map<String, Boolean> getDevices(String mac_address) {
        Map<String, Boolean> result = new HashMap<>();
        if(!Objects.equals(jdbcTemplate.queryForObject("select count(*) from light_control where mac_address=?",Integer.class,mac_address),0)){
            result.put("light",true);
        }
        else{
            result.put("light",false);
        }
        if(!Objects.equals(jdbcTemplate.queryForObject("select count(*) from lock_control where mac_address=?",Integer.class,mac_address),0)){
            result.put("lock",true);
        }
        else{
            result.put("lock",false);
        }
        if(!Objects.equals(jdbcTemplate.queryForObject("select count(*) from thermostat where mac_address=?",Integer.class,mac_address),0)){
            result.put("thermostat",true);
        }
        else{
            result.put("thermostat",false);
        }
        return result;
    }


    @Override
    public Map<String, Object> getLockStatus(String mac_address) {
        String sql = "SELECT status FROM lock_control WHERE mac_address = ?";
        Boolean status = jdbcTemplate.queryForObject(sql, Boolean.class, mac_address);
        sql="SELECT power_saving_mode FROM lock_control WHERE mac_address=?";
        Boolean powerSavingMode=jdbcTemplate.queryForObject(sql,Boolean.class,mac_address);
        Map<String, Object> result = new HashMap<>();
        result.put("status",status);
        result.put("power_saving_mode", powerSavingMode);
        return result;
    }
    @Override
    public String getThermostatStatus(String mac_address) {
        return jdbcTemplate.queryForObject("SELECT cast(status as varchar) FROM thermostat WHERE mac_address = ?", String.class, mac_address);
    }
    @Override
    public void changeThermostatTarget(String temp,String mac_address){
        jdbcTemplate.update("update thermostat set status=? where mac_address=?",Double.valueOf(temp),mac_address);
    }

    @Override
    public void setSmartActions(String mac_address, String deviceType, String action, String time,boolean permanent) {
        LocalDateTime parsedEventTime = LocalDateTime.parse(time);
        String sql = "INSERT INTO smart_actions (mac_address, device_type,action,activation_time,permanent) VALUES (?, ?,?,?,?)";
        jdbcTemplate.update(sql, mac_address,deviceType,action, parsedEventTime,permanent);
    }

    @Override
    public List<Map<String, Object>> getSmartActions(String mac_address) {
        String sql = "SELECT id, mac_address, device_type, action, activation_time, permanent FROM smart_actions where mac_address=?";
        return jdbcTemplate.queryForList(sql, mac_address);
    }
    @Override
    public void setTemperature(String mac_address, String temperature,String humidity) {
        jdbcTemplate.update("update thermostat set temperature=?,humidity=? where mac_address=?", Double.valueOf(temperature),Double.valueOf(humidity),mac_address);
    }

    @Override
    public void turnOffLight(String mac_address) {
        jdbcTemplate.update("update light_control set status=0 where mac_address=?",mac_address);
        mqttService.sendCommand(mac_address, "turn_off");
    }

    @Override
    public void turnOnLight(String mac_address,int percentage) {
        jdbcTemplate.update("update light_control set status=? where mac_address=?",percentage,mac_address);
        mqttService.sendCommand(mac_address, "turn_on/"+percentage);
    }

    @Override
    public void lockDoor(String mac_address) {
        if(!Objects.equals(jdbcTemplate.queryForObject("select count(*) from lock_control where mac_address=?",Integer.class,mac_address),0)) {
            if(Objects.equals(jdbcTemplate.queryForObject("select status from lock_control where mac_address=?",Boolean.class,mac_address),false))
            {
                jdbcTemplate.update("update lock_control set status=true where mac_address=?", mac_address);
                mqttService.sendCommand(mac_address, "lock_door");
            }
        }
    }

    @Override
    public void unlockDoor(String mac_address) {
        if(!Objects.equals(jdbcTemplate.queryForObject("select count(*) from lock_control where mac_address=?",Integer.class,mac_address),0)) {
            if(Objects.equals(jdbcTemplate.queryForObject("select status from lock_control where mac_address=?",Boolean.class,mac_address),true)) {
                jdbcTemplate.update("update lock_control set status=false where mac_address=?", mac_address);
                mqttService.sendCommand(mac_address, "unlock_door");
            }
        }
    }

    @Override
    public String registerDevice(String ipaddress,String mac_address, String uuid,String roomid,String deviceType) {
        String sql= """
            SELECT COUNT(*)
            FROM uuids u
            JOIN user_houses uh ON uh.user_id = u.iduser
            JOIN houses h ON h.id = uh.house_id
            JOIN rooms r ON r.houseid = h.id
            WHERE u.uuid = ? AND r.id = ?
        """;
        if(Objects.equals(jdbcTemplate.queryForObject("select count(*) from provisioning_queue where mac_address=?",Integer.class,mac_address),1)){
            jdbcTemplate.update("delete from provisioning_queue where mac_address=?",mac_address);
        }
        boolean power_saving_mode = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT CASE WHEN EXISTS (" +
                        "  SELECT 1 FROM light_control " +
                        "  WHERE mac_address = ? AND power_saving_mode = true " +
                        "  UNION ALL " +
                        "  SELECT 1 FROM lock_control " +
                        "  WHERE mac_address = ? AND power_saving_mode = true " +
                        "  UNION ALL " +
                        "  SELECT 1 FROM thermostat " +
                        "  WHERE mac_address = ? AND power_saving_mode = true " +
                        "  LIMIT 1 " +
                        ") THEN true ELSE false END",
                Boolean.class,
                mac_address, mac_address,mac_address));
        if(!Objects.equals(jdbcTemplate.queryForObject(sql, Integer.class, uuid, Integer.valueOf(roomid)), 0)) {
            switch (deviceType) {
                case "light_control" -> {
                    if (Objects.equals(jdbcTemplate.queryForObject("SELECT count(*) from light_control where ip_address=?", Integer.class, ipaddress), 0)) {
                        try {
                            jdbcTemplate.update("INSERT INTO light_control (ip_address,room_id,mac_address,power_saving_mode) VALUES (?,?,?,?)", ipaddress, Integer.valueOf(roomid), mac_address, power_saving_mode);
                            return "Succes";
                        } catch (DuplicateKeyException e) {
                            return "Device already registered";
                        }

                    }
                }
                case "lock_control" -> {
                    if (Objects.equals(jdbcTemplate.queryForObject("SELECT count(*) from lock_control where ip_address=?", Integer.class, ipaddress), 0)) {
                        try {
                            jdbcTemplate.update("INSERT INTO lock_control (ip_address,room_id,mac_address,power_saving_mode) VALUES (?,?,?,?)", ipaddress, Integer.valueOf(roomid), mac_address, power_saving_mode);
                            return "Succes";
                        } catch (DuplicateKeyException e) {
                            return "Device already registered";
                        }
                    }
                }
                case "thermostat" -> {
                    if (Objects.equals(jdbcTemplate.queryForObject("select count(*) from thermostat where ip_address=?", Integer.class, ipaddress), 0)) {
                        try {
                            jdbcTemplate.update("INSERT INTO thermostat(ip_address,room_id,mac_address,power_saving_mode) values(?,?,?,?)", ipaddress, Integer.valueOf(roomid), mac_address, power_saving_mode);
                            return "Succes";
                        } catch (DuplicateKeyException e) {
                            return "Device already registered";
                        }
                    }
                }
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
        d.status AS status,
        'light_control' AS device_type
    FROM houses h
    JOIN user_houses uh ON uh.house_id = h.id
    JOIN rooms r ON r.houseid = h.id
    LEFT JOIN light_control d ON d.room_id = r.id
    WHERE uh.user_id = CAST(? AS INTEGER)

    UNION ALL

    SELECT
        h.id AS house_id,
        h.house_name,
        r.id AS room_id,
        r.room_name,
        l.id AS device_id,
        l.ip_address,
        l.mac_address,
        CAST(l.status AS INTEGER) AS status,
        'lock_control' AS device_type
    FROM houses h
    JOIN user_houses uh ON uh.house_id = h.id
    JOIN rooms r ON r.houseid = h.id
    LEFT JOIN lock_control l ON l.room_id = r.id
    WHERE uh.user_id = CAST(? AS INTEGER)

    UNION ALL

    SELECT
        h.id AS house_id,
        h.house_name,
        r.id AS room_id,
        r.room_name,
        t.id AS device_id,
        t.ip_address,
        t.mac_address,
        CAST(t.status AS INTEGER) AS status,
        'thermostat' AS device_type
    FROM houses h
    JOIN user_houses uh ON uh.house_id = h.id
    JOIN rooms r ON r.houseid = h.id
    LEFT JOIN thermostat t ON t.room_id = r.id
    WHERE uh.user_id = CAST(? AS INTEGER)

    ORDER BY house_id, room_id, device_id
""";

        List<Map<String, Object>> flatList = jdbcTemplate.queryForList(sql, userId, userId,userId);
        Map<Integer, Map<String, Object>> houseMap = new LinkedHashMap<>();

        for (Map<String, Object> row : flatList) {
            int houseId = (int) row.get("house_id");
            int roomId = (int) row.get("room_id");

            Map<String, Object> house = houseMap.computeIfAbsent(houseId, id -> {
                Map<String, Object> h = new HashMap<>();
                h.put("house_id", houseId);
                h.put("house_name", row.get("house_name"));
                h.put("rooms", new ArrayList<Map<String, Object>>());
                return h;
            });

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> roomList = (List<Map<String, Object>>) house.get("rooms");

            Map<String, Object> room = roomList.stream()
                    .filter(r -> ((int) r.get("room_id")) == roomId)
                    .findFirst()
                    .orElseGet(() -> {
                        Map<String, Object> r = new HashMap<>();
                        r.put("room_id", roomId);
                        r.put("room_name", row.get("room_name"));
                        r.put("devices", new ArrayList<Map<String, Object>>());
                        roomList.add(r);
                        return r;
                    });

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> devices = (List<Map<String, Object>>) room.get("devices");

            if (row.get("device_id") != null) {
                Map<String, Object> device = new HashMap<>();
                device.put("device_id", row.get("device_id"));
                device.put("ip_address", row.get("ip_address"));
                device.put("mac_address", row.get("mac_address"));
                device.put("device_type", row.get("device_type"));
                device.put("status", row.get("status"));
                devices.add(device);
            }

        }

        List<Map<String, Object>> finalResult = new ArrayList<>();
        for (Map<String, Object> house : houseMap.values()) {
            Map<String, Object> resultHouse = new LinkedHashMap<>();
            resultHouse.put("house_id", house.get("house_id"));
            resultHouse.put("house_name", house.get("house_name"));
            resultHouse.put("rooms", house.get("rooms"));
            finalResult.add(resultHouse);
        }

        return finalResult;
    }


    @Override
    public int removeDevice(String mac_address,String deviceType) {
        jdbcTemplate.update("delete from provisioning_queue where mac_address=?",mac_address);
        if(deviceType.equals("light_control")) {
             jdbcTemplate.update("delete from light_control where mac_address=?", mac_address);
        }
        else if (deviceType.equals("lock_control")) {
             jdbcTemplate.update("delete from lock_control where mac_address=?",mac_address);
        }
        else if(deviceType.equals("thermostat")){
            jdbcTemplate.update("delete from thermostat where mac_address=?",mac_address);
        }
        Integer stillExists = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM (
                    SELECT mac_address FROM light_control WHERE mac_address = ?
                    UNION
                    SELECT mac_address FROM lock_control WHERE mac_address = ?
                    UNION
                    SELECT mac_address FROM thermostat WHERE mac_address = ?
                ) AS combined
                """, Integer.class, mac_address, mac_address,mac_address);

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
            jdbcTemplate.update("update thermostat set power_saving_mode = true where mac_address = ?", mac_address);
        }else if(toggle.equals("disable_power_saving")){
            jdbcTemplate.update("update light_control set power_saving_mode = false where mac_address = ?", mac_address);
            jdbcTemplate.update("update lock_control set power_saving_mode = false where mac_address = ?", mac_address);
            jdbcTemplate.update("update thermostat set power_saving_mode = false where mac_address = ?", mac_address);
        }
        mqttService.sendCommand(mac_address, toggle);
    }

    @Override
    public int getPowerSavingMode(String mac_address) {
        String sql = """
        SELECT power_saving_mode FROM (
            SELECT power_saving_mode FROM light_control WHERE mac_address = ?
            UNION
            SELECT power_saving_mode FROM lock_control WHERE mac_address = ?
            UNION
            SELECT power_saving_mode FROM thermostat WHERE mac_address = ?
        ) AS combined
        LIMIT 1
    """;
        Boolean result = jdbcTemplate.queryForObject(sql, Boolean.class, mac_address, mac_address, mac_address);
        return Boolean.TRUE.equals(result) ? 1 : 0;
    }


    @Override
    public String registerToQueue(String device_hash, String ipaddress, String mac_address) {
        final Pattern MAC_ADDRESS_PATTERN = Pattern.compile("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$");
        try {
            InetAddress.getByName(ipaddress);
            if(MAC_ADDRESS_PATTERN.matcher(mac_address).matches()){
                if(Objects.equals(jdbcTemplate.queryForObject("SELECT count(*) from light_control where ip_address=?", Integer.class, ipaddress), 0)||
                        Objects.equals(jdbcTemplate.queryForObject("SELECT count(*) from lock_control where ip_address=?", Integer.class, ipaddress), 0)||
                        Objects.equals(jdbcTemplate.queryForObject("SELECT count(*) from thermostat where ip_address=?", Integer.class, ipaddress), 0)) {
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
    public JSONArray getDutyCyclesByMac(String macAddress) {
        String sql = "SELECT id, mac_address, duty_cycle, duration_seconds, recorded_at FROM lightbulb_power_events WHERE mac_address = ?";

        List<JSONObject> results = jdbcTemplate.query(sql, (ResultSet rs, int rowNum) -> {
            JSONObject obj = new JSONObject();
            obj.put("id", rs.getInt("id"));
            obj.put("mac_address", rs.getString("mac_address"));
            obj.put("duty_cycle", rs.getInt("duty_cycle"));
            obj.put("duration_seconds", rs.getInt("duration_seconds"));
            obj.put("recorded_at", rs.getTimestamp("recorded_at").toInstant().toString());
            return obj;
        },macAddress);

        JSONArray array = new JSONArray();
        array.addAll(results);
        return array;
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
