package ro.utcn.homewave.Dao;

import java.util.List;
import java.util.Map;

public interface DeviceDao {
    boolean getLightStatus(String mac_address);
    boolean getLockStatus(String mac_address);
    void turnOffLight(String mac_address);
    void turnOnLight(String mac_address,int percentage);
    void lockDoor(String mac_address);
    void unlockDoor(String mac_address);
    String registerDevice(String ipaddress,String mac_address,String uuid,String roomid,String device_type);
    String registerToQueue(String device_hash, String ipaddress, String mac_address);
    Map<String,Object> getQueuedDevice(String device_hash);
    List<Map<String, Object>> getFullDeviceStructure(String userId);
    int removeDevice(String mac_address,String deviceType);
    void togglePowerSavingMode(String mac_address,String toggle);
    int getPowerSavingMode(String mac_address);
}
