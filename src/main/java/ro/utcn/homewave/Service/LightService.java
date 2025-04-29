package ro.utcn.homewave.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ro.utcn.homewave.Dao.LightDao;

import java.util.*;

@Service
public class LightService {
    public final LightDao lightDao;

    @Autowired
    public LightService(LightDao lightDao) {
        this.lightDao = lightDao;
    }
    public boolean getLightStatus(String mac_address){
        return lightDao.getLightStatus(mac_address);
    }
    public void turnOffLight(String mac_address)
    {
        lightDao.turnOffLight(mac_address);
    }
    public void turnOnLight(String mac_address){
        lightDao.turnOnLight(mac_address);
    }
    public String registerDevice(String ipaddress,String mac_address,String uuid,String roomid){
        return lightDao.registerDevice(ipaddress,mac_address,uuid,roomid);
    }
    public String registerToQueue(String device_hash, String ipaddress, String mac_address){
        return lightDao.registerToQueue(device_hash,ipaddress,mac_address);
    }
    public Map<String,Object> getQueuedDevice(String device_hash){
        return lightDao.getQueuedDevice( device_hash);
    }
    public List<Map<String, Object>> buildUserDeviceTree(String userId) {
        List<Map<String, Object>> flatRows = lightDao.getFullDeviceStructure(userId);
        System.out.println(flatRows);
        Map<Integer, Map<String, Object>> houses = new LinkedHashMap<>();

        for (Map<String, Object> row : flatRows) {
            Integer houseId = (Integer) row.get("house_id");
            Integer roomId = (Integer) row.get("room_id");

            houses.putIfAbsent(houseId, new HashMap<>(Map.of(
                    "house_id", houseId,
                    "house_name", row.get("house_name"),
                    "rooms", new LinkedHashMap<Integer, Map<String, Object>>()
            )));

            Map<Integer, Map<String, Object>> rooms = (Map<Integer, Map<String, Object>>) houses.get(houseId).get("rooms");

            if (roomId != null) {
                rooms.putIfAbsent(roomId, new HashMap<>(Map.of(
                        "room_id", roomId,
                        "room_name", row.get("room_name"),
                        "devices", new ArrayList<Map<String, Object>>()
                )));

                List<Map<String, Object>> devices = (List<Map<String, Object>>) rooms.get(roomId).get("devices");

                if (row.get("device_id") != null) {
                    devices.add(Map.of(
                            "device_id", row.get("device_id"),
                            "ip_address", row.get("ip_address"),
                            "mac_address", row.get("mac_address")
                    ));
                }
            }
        }

        // Flatten to a list
        return houses.values().stream().peek(h -> {
            List<Object> roomsList = (List<Object>) ((Map<?, ?>) h.get("rooms")).values().stream().toList();
            h.put("rooms", roomsList);
        }).toList();
    }
}
