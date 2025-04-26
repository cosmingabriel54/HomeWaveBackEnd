package ro.utcn.homewave.Controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import ro.utcn.homewave.Service.LightService;

import javax.servlet.http.HttpServletRequest;
import javax.swing.plaf.synth.SynthOptionPaneUI;
import java.net.SocketOption;
import java.sql.SQLOutput;
import java.util.List;
import java.util.Map;
@Api(
        tags = {"Light"}
)
@RestController
@CrossOrigin(origins = {"http://localhost:3000","http://localhost:5173"})
public class LightControl {
    public final LightService lightService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public LightControl(LightService lightService, JdbcTemplate jdbcTemplate) {
        this.lightService = lightService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @ApiOperation("Get Light Status")
    @GetMapping("/getlightstatus")
    public ResponseEntity<?> getLightStatus(@RequestParam String mac_address) {
        boolean response=lightService.getLightStatus(mac_address);
        if(response){
            return ResponseEntity.status(200).body("on");
        }else{
            return ResponseEntity.status(400).body("off");
        }
    }
    @ApiOperation("Get queued provisioned devices")
    @GetMapping("/getqueueddevice")
    public ResponseEntity<?> getQueuedDevice(@RequestParam String device_hash) {
        try {
            Map<String, Object> device = lightService.getQueuedDevice(device_hash);

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
            String response=lightService.registerToQueue(device_hash,ipaddress,mac_address);
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
        try{

            String response=lightService.registerDevice(ipAddress,mac_address,uuid,roomId);
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
    public void turnOnLight(@RequestParam String mac_address) {
        lightService.turnOnLight(mac_address);
    }
    @ApiOperation("Turn Off The Light")
    @GetMapping("/turnofflight")
    public void turnOffLight(@RequestParam String mac_address) {
        lightService.turnOffLight(mac_address);
    }
    @ApiOperation("Turn Off The Light")
    @GetMapping("/userdevicetree")
    public ResponseEntity<?>  buildUserDeviceTree(@RequestParam String uuid) {
        try{
            List<Map<String, Object>> result =lightService.buildUserDeviceTree(getUserId(uuid));
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
