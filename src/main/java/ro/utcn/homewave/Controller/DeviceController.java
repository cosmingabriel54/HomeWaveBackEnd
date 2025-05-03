package ro.utcn.homewave.Controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import ro.utcn.homewave.Service.DeviceService;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
@Api(
        tags = {"Light"}
)
@RestController
@CrossOrigin(origins = {"http://localhost:3000","http://localhost:5173","https://homewavefrontend.onrender.com"})
public class DeviceController {
    public final DeviceService deviceService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public DeviceController(DeviceService deviceService, JdbcTemplate jdbcTemplate) {
        this.deviceService = deviceService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @ApiOperation("Get Light Status")
    @GetMapping("/getlightstatus")
    public ResponseEntity<Integer> getLightStatus(@RequestParam String mac_address) {
        Integer response= deviceService.getLightStatus(mac_address);
        return ResponseEntity.status(200).body(response);
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
    @ApiOperation("Turn Off The Light")
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
