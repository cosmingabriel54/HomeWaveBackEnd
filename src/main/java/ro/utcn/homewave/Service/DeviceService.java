package ro.utcn.homewave.Service;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ro.utcn.homewave.Dao.DeviceDao;

import java.sql.SQLOutput;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class DeviceService {
    public final JdbcTemplate jdbcTemplate;
    public final DeviceDao deviceDao;
    public final MqttService mqttService;

    @Autowired
    public DeviceService(JdbcTemplate jdbcTemplate, DeviceDao deviceDao, MqttService mqttService) {
        this.jdbcTemplate = jdbcTemplate;
        this.deviceDao = deviceDao;
        this.mqttService = mqttService;
    }
    public Map<String, Object> getLightStatus(String mac_address){
        return deviceDao.getLightStatus(mac_address);
    }
    public Map<String, Boolean> getDevices(String mac_address){
        return deviceDao.getDevices(mac_address);
    }
    public Map<String, Object> getLockStatus(String mac_address){
        return deviceDao.getLockStatus(mac_address);
    }
    public void turnOffLight(String mac_address)
    {
        deviceDao.turnOffLight(mac_address);
    }
    public void turnOnLight(String mac_address,int percentage){
        deviceDao.turnOnLight(mac_address,percentage);
    }
    public void lockDoor(String mac_address){
        deviceDao.lockDoor(mac_address);
    }
    public void unlockDoor(String mac_address){
        deviceDao.unlockDoor(mac_address);
    }
    public JSONArray getDutyCyclesByMac(String macAddress){
        return deviceDao.getDutyCyclesByMac(macAddress);
    }
    public String registerDevice(String ipaddress,String mac_address,String uuid,String roomid,String deviceType){
        return deviceDao.registerDevice(ipaddress,mac_address,uuid,roomid,deviceType);
    }
    public void setSmartActions(String mac_address,String deviceType,String action,String time,boolean permanent){
        deviceDao.setSmartActions(mac_address, deviceType, action, time,permanent);
    }
    public List<Map<String, Object>> getSmartActions(String mac_address){
        return deviceDao.getSmartActions(mac_address);
    }
    public int removeDevice(String mac_address,String deviceType){
        return deviceDao.removeDevice(mac_address,deviceType);
    }
    public void togglePowerSavingMode(String mac_address,String toggle){
        deviceDao.togglePowerSavingMode(mac_address,toggle);
    }
    public int getPowerSavingMode(String mac_address){
        return deviceDao.getPowerSavingMode(mac_address);
    }
    public String registerToQueue(String device_hash, String ipaddress, String mac_address){
        return deviceDao.registerToQueue(device_hash,ipaddress,mac_address);
    }
    public Map<String,Object> getQueuedDevice(String device_hash){
        return deviceDao.getQueuedDevice( device_hash);
    }
    public List<Map<String, Object>> buildUserDeviceTree(String userId) {
        List<Map<String, Object>> flatRows = deviceDao.getFullDeviceStructure(userId);
        System.out.println(flatRows);
        return flatRows;
    }
    public JSONObject getDeviceSensors(String mac_address) {
        return mqttService.getDeviceSensors(mac_address);
    }
    public void setTemperature(String mac_address,String temperature,String humidity){
        deviceDao.setTemperature(mac_address, temperature,humidity);
    }
    public String getThermostatStatus(String mac_address) {
        return deviceDao.getThermostatStatus(mac_address);
    }
    public void changeThermostatTarget(String temp,String mac_address){
        deviceDao.changeThermostatTarget(temp,mac_address);
    }
    @Scheduled(fixedRate = 10000)
    public void performLogicEveryMinuteUsingCron() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Europe/Bucharest")).withSecond(0).withNano(0);
        LocalDateTime startOfMinute = now.withSecond(0).withNano(0);
        LocalDateTime endOfMinute = startOfMinute.plusMinutes(1).minusNanos(1);

        Timestamp from = Timestamp.valueOf(startOfMinute);
        Timestamp to = Timestamp.valueOf(endOfMinute);

        System.out.println("timer a pornit");
        String sql = "SELECT id, mac_address, device_type, action,permanent FROM smart_actions WHERE activation_time BETWEEN ? AND ?";
        List<Map<String, Object>> actions = jdbcTemplate.queryForList(sql, from, to);


        for (Map<String, Object> row : actions) {
            Integer id = (Integer) row.get("id");
            String mac = (String) row.get("mac_address");
            String type = (String) row.get("device_type");
            String action = (String) row.get("action");
            boolean permanent= (boolean) row.get("permanent");
            System.out.println(id);
            try {
                switch (type) {
                    case "lock":
                        if ("lock".equalsIgnoreCase(action)) {
                            deviceDao.lockDoor(mac);
                        } else if ("unlock".equalsIgnoreCase(action)) {
                            deviceDao.unlockDoor(mac);
                        }
                        break;

                    case "light":
                        if (action.startsWith("brightness:")) {
                            int brightness = Integer.parseInt(action.split(":")[1]);
                            deviceDao.turnOnLight(mac, brightness);
                        }
                        break;

                    case "thermostat":
                        if (action.startsWith("temp:")) {
                            String temp = action.split(":")[1];
                            deviceDao.changeThermostatTarget(temp, mac);
                        }
                        break;

                    default:
                        System.err.println("Unknown device type: " + type);
                }
                if(!permanent) {
                    jdbcTemplate.update("DELETE FROM smart_actions WHERE id = ?", id);
                }
            } catch (Exception e) {
                System.err.println("Failed to process smart action (id=" + id + "): " + e.getMessage());
            }
        }
    }
}
