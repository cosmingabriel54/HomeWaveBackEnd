package ro.utcn.homewave.Controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import ro.utcn.homewave.Service.HouseService;

@RequestMapping({"/home"})
@RestController
@Api(
        tags = {"ActualizareCasa"}
)
@CrossOrigin(origins = {"*"}, allowCredentials = "false")
public class HouseController {
    public final HouseService houseService;
    @Autowired
    public HouseController(HouseService houseService) {
        this.houseService = houseService;
    }
    @ApiOperation("AfisareCase")
    @GetMapping("/getHouses")
    public JSONObject getHouses(@RequestParam(name = "uuid")String uuid) {
        System.out.println(uuid);
        return houseService.getHouses(uuid);
    }
    @ApiOperation("AdaugareCasa")
    @PostMapping("/addHouse")
    public String addNewHouse(@RequestParam(name = "houseName") String houseName,@RequestParam(name = "uuid") String uuid) {
        return houseService.addNewHouse(houseName,uuid);
    }
    @ApiOperation("StergereCasa")
    @DeleteMapping("/deleteHouse")
    public String deleteHouse(@RequestParam(name = "houseId") String houseId) {
        return houseService.deleteHouse(houseId);
    }

}
