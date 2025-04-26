package ro.utcn.homewave.Dao;

import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

public interface LightDao {
    boolean getLightStatus(String ipaddress);
    void turnOffLight(String ipaddress);
    void turnOnLight(String ipaddress);
    String registerDevice(String ipaddress,String mac_address,String uuid,String roomid);
    String registerToQueue(String device_hash, String ipaddress, String mac_address);
    Map<String,Object> getQueuedDevice(String device_hash);
    List<Map<String, Object>> getFullDeviceStructure(String userId);
}
