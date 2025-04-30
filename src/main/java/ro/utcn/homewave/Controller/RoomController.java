package ro.utcn.homewave.Controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import ro.utcn.homewave.Service.RoomService;

@RestController
@CrossOrigin(origins = {"http://localhost:3000","http://localhost:5173","https://homewavefrontend.onrender.com"})
@Api(
        tags = {"ActualizareCamera"}
)
public class RoomController {
    public final RoomService roomService;
    @Autowired
    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }
    @GetMapping("/getRooms")
    @ApiOperation("Get Rooms")
    public JSONObject getRooms(@RequestParam(name = "houseId") String houseid) {
        System.out.println(roomService.getRooms(houseid));
        return roomService.getRooms(houseid);
    }
    @PostMapping("/addRoom")
    @ApiOperation("Add Room")
    public String addNewRoom(@RequestParam(name = "roomName") String roomName,@RequestParam(name = "houseId") String houseId) {
        return roomService.addNewRoom(roomName,houseId);
    }
    @DeleteMapping("/deleteRoom/{roomid}")
    @ApiOperation("Delete Room")
    public String deleteRoom(@PathVariable String roomid) {
        System.out.println(roomid);
        return roomService.deleteRoom(roomid);
    }
    @PostMapping("/addLightControl")
    @ApiOperation("Add Light Control")
    public String addNewLightControl(@RequestParam(name = "ip_address") String ip_address,@RequestParam(name = "roomid") String roomid) {
        return roomService.addNewLightControl(ip_address,roomid);
    }

    @PostMapping("/addThermostat")
    @ApiOperation("Add Thermostat")
    public String addNewThermostat(@RequestParam(name = "ip_address") String ip_address,@RequestParam(name = "roomid") String roomid) {
        return roomService.addNewThermostat(ip_address,roomid);
    }

    @PostMapping("/addLockControl")
    @ApiOperation("Add Lock Control")
    public String addNewLockControl(@RequestParam(name = "ip_address") String ip_address,@RequestParam(name = "roomid") String roomid) {
        return roomService.addNewLockControl(ip_address,roomid);
    }
}
