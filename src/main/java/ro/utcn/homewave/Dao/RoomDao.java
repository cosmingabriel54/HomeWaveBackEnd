package ro.utcn.homewave.Dao;

import org.json.simple.JSONObject;

public interface RoomDao {
    public String addNewRoom(String roomName,String houseId);
    public String deleteRoom(String roomid);
    public JSONObject getRooms(String houseid);
    public String addNewLightControl(String ip_address,String roomid);
    public String addNewThermostat(String ip_address,String roomid);
    public String addNewLockControl(String ip_address,String roomid);
}
