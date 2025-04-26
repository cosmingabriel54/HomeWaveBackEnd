package ro.utcn.homewave.Dao;

import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

public interface LightDao {
    boolean getLightStatus(String mac_address);
    void turnOffLight(String mac_address);
    void turnOnLight(String mac_address);
    String registerDevice(String ipaddress,String mac_address,String uuid,String roomid);
    String registerToQueue(String device_hash, String ipaddress, String mac_address);
    Map<String,Object> getQueuedDevice(String device_hash);
    List<Map<String, Object>> getFullDeviceStructure(String userId);
}
