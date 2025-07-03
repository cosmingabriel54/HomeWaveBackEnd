package ro.utcn.homewave.Controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import ro.utcn.homewave.Service.DeviceService;
import ro.utcn.homewave.Service.MqttService;
import ro.utcn.homewave.Service.PowerConsumptionService;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
@Api(
        tags = {"Devices"}
)
@RestController
@CrossOrigin(origins = {"*"}, allowCredentials = "false")
public class DeviceController {
    public final DeviceService deviceService;
    private final JdbcTemplate jdbcTemplate;
    private final MqttService mqttService;
    private final PowerConsumptionService powerConsumptionService;

    @Autowired
    public DeviceController(DeviceService deviceService, JdbcTemplate jdbcTemplate, MqttService mqttService, PowerConsumptionService powerConsumptionService) {
        this.deviceService = deviceService;
        this.jdbcTemplate = jdbcTemplate;
        this.mqttService = mqttService;
        this.powerConsumptionService = powerConsumptionService;
    }

    @ApiOperation("Get Light Status")
    @GetMapping("/getlightstatus")
    public ResponseEntity<Map<String, Object>> getLightStatus(@RequestParam String mac_address) {
        return ResponseEntity.status(200).body(deviceService.getLightStatus(mac_address));
    }
    @ApiOperation("Get Devices")
    @GetMapping("/getdevicesbymac")
    public Map<String, Boolean> getDevices(@RequestParam String mac_address){
        return deviceService.getDevices(mac_address);
    }
    @ApiOperation("Get Lock Status")
    @GetMapping("/getlockstatus")
    public ResponseEntity<Map<String, Object>> getLockStatus(@RequestParam String mac_address){
        return ResponseEntity.status(200).body(deviceService.getLockStatus(mac_address));
    }
    @ApiOperation("Get operation data")
    @GetMapping("/getcycledata")
    public ResponseEntity<String> getDutyCyclesByMac(@RequestParam String macAddress,@RequestParam(name = "full", defaultValue = "0") int full){
        boolean isFullReport = (full == 1);
        JSONObject result = powerConsumptionService.getAggregatedConsumption(macAddress, isFullReport);

        if (result == null || !(result.get("saptamani") instanceof JSONArray) || ((JSONArray) result.get("saptamani")).isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(result.toJSONString());
    }
    @ApiOperation("Get operation data")
    @GetMapping("/getsensordata")
    public ResponseEntity<JSONObject> getSensorData(@RequestParam String macAddress){
        return ResponseEntity.status(200).body(deviceService.getDeviceSensors(macAddress));
    }
    @ApiOperation("Get Thermostat Status")
    @GetMapping("/getthermostatstatus")
    public ResponseEntity<String> getThermostatStatus(@RequestParam String mac_address) {
        return ResponseEntity.status(200).body(deviceService.getThermostatStatus(mac_address));
    }
    @PostMapping("/changethermostat")
    public void changeThermostatTarget(@RequestParam String temp,@RequestParam String mac_address){
        deviceService.changeThermostatTarget(temp,mac_address);
    }
    @ApiOperation("Get queued provisioned devices")
    @GetMapping("/getqueueddevice")
    public ResponseEntity<?> getQueuedDevice(@RequestParam String device_hash) {
        try {
            Map<String, Object> device = deviceService.getQueuedDevice(device_hash);

            if (device == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Device not found"));
            }
            System.out.println(ResponseEntity.ok(device));
            return ResponseEntity.ok(device);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Eroare: " + e.getMessage()));
        }
    }
    @ApiOperation("Register light device to provisioning queue")
    @PostMapping("/registertoqueue")
    public ResponseEntity<String> registerToQueue(@RequestBody Map<String, String> payload, HttpServletRequest request) {
        payload.forEach((key, value) -> System.out.println(key + ": " + value));
        String ipaddress = request.getRemoteAddr();
        String device_hash = payload.get("device_hash");
        String mac_address = payload.get("mac_address");
        try{
            String response= deviceService.registerToQueue(device_hash,ipaddress,mac_address);
            if(response.contains("Eroare")){
                return ResponseEntity.status(401).body(response);
            }
            else{
                return ResponseEntity.ok(response);
            }
        }catch (Exception e){
            return ResponseEntity.status(500).body("Eroare: "+e.getMessage());
        }
    }

    @ApiOperation("Register light device")
    @PostMapping("/registerdevice")
    public ResponseEntity<String> registerDevice(@RequestBody Map<String, String> payload) {
        payload.forEach((key, value) -> System.out.println(key + ": " + value));
        String ipAddress = payload.get("ip");
        String uuid = payload.get("provision_token");
        String roomId = payload.get("room_id");
        String mac_address=payload.get("mac_address");
        String deviceType=payload.get("device_type");
        try{

            String response= deviceService.registerDevice(ipAddress,mac_address,uuid,roomId,deviceType);
            if(response.contains("Eroare")){
                return ResponseEntity.status(401).body(response);
            }
            else{
                return ResponseEntity.ok(response);
            }
        }catch (Exception e){
            return ResponseEntity.status(500).body("Eroare: "+e.getMessage());
        }
    }

    @ApiOperation("Set Smart Actions")
    @PostMapping("/setactions")
    public void setSmartActions(@RequestBody Map<String, String> payload){
        String time = payload.get("time");
        String action = payload.get("action");
        String mac_address=payload.get("mac_address");
        String deviceType=payload.get("device_type");
        boolean permanent=Boolean.parseBoolean(payload.get("permanent"));
        deviceService.setSmartActions(mac_address, deviceType, action, time,permanent);
    }
    @ApiOperation("Get Smart Actions")
    @GetMapping("/getactions")
    public List<Map<String, Object>> getSmartActions(@RequestParam String mac_address){
        return deviceService.getSmartActions(mac_address);
    }

    @ApiOperation("Turn On The Light")
    @GetMapping("/turnonlight")
    public void turnOnLight(@RequestParam String mac_address,@RequestParam int percentage) {
        deviceService.turnOnLight(mac_address,percentage);
    }

    @ApiOperation("Turn Off The Light")
    @GetMapping("/turnofflight")
    public void turnOffLight(@RequestParam String mac_address) {
        deviceService.turnOffLight(mac_address);
    }

    @ApiOperation("Lock Door")
    @GetMapping("/lockdoor")
    public void lockDoor(@RequestParam String mac_address) {
        deviceService.lockDoor(mac_address);
    }

    @ApiOperation("Unlock Door")
    @GetMapping("/unlockdoor")
    public void unlockDoor(@RequestParam String mac_address) {
        deviceService.unlockDoor(mac_address);
    }

    @ApiOperation("Toggle Power Saving Mode")
    @PostMapping("/togglepowersavingmode")
    public void togglePowerSavingMode(@RequestParam String mac_address,@RequestParam String toggle) {
        deviceService.togglePowerSavingMode(mac_address,toggle);
    }
    @ApiOperation("Toggle Power Saving Mode")
    @PostMapping("/settemperature")
    public void setTemperature(@RequestParam String mac_address,@RequestParam String temperature,@RequestParam String humidity){
        deviceService.setTemperature(mac_address, temperature,humidity);
    }
    @ApiOperation("Get Power Saving Mode")
    @GetMapping("/getpowersavingmode")
    public ResponseEntity<?> getPowerSavingMode(@RequestParam String mac_address) {
        int response= deviceService.getPowerSavingMode(mac_address);
        return ResponseEntity.status(200).body(response);
    }
    @ApiOperation("Remove Device")
    @DeleteMapping("/removedevice")
    public ResponseEntity<Integer> removeDevice(@RequestParam String mac_address,@RequestParam String device_type) {
        int res= deviceService.removeDevice(mac_address,device_type);
         if(res==0){
             return ResponseEntity.status(400).body(res);
         }
         return ResponseEntity.status(200).body(res);
    }
    @ApiOperation("User Device Tree")
    @GetMapping("/userdevicetree")
    public ResponseEntity<?>  buildUserDeviceTree(@RequestParam String uuid) {
        try{
            List<Map<String, Object>> result = deviceService.buildUserDeviceTree(getUserId(uuid));
            if(result.contains("Eroare")){
                return ResponseEntity.status(401).body(result);
            }
            else{
                return ResponseEntity.ok(result);
            }
        }catch (Exception e){
            return ResponseEntity.status(500).body("Eroare: "+e.getMessage());
        }
    }
    public String getUserId(String uuid){
        return jdbcTemplate.queryForObject("SELECT iduser FROM uuids WHERE uuid = ?", String.class,uuid);
    }

}
