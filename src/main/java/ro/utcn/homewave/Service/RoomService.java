package ro.utcn.homewave.Service;

import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ro.utcn.homewave.Dao.RoomDao;

@Service
public class RoomService {
    public final RoomDao roomDao;
    @Autowired
    public RoomService(RoomDao roomDao) {
        this.roomDao = roomDao;
    }
    //implement all the methods from roomdao
    public JSONObject getRooms(String houseid) {
        return roomDao.getRooms(houseid);
    }

    public String addNewRoom(String roomName, String houseId) {
        return roomDao.addNewRoom(roomName,houseId);
    }

    public String deleteRoom(String roomid) {
        return roomDao.deleteRoom(roomid);
    }

    public String addNewLightControl(String ip_address, String roomid) {
        return roomDao.addNewLightControl(ip_address,roomid);
    }

    public String addNewThermostat(String ip_address, String roomid) {
        return roomDao.addNewThermostat(ip_address,roomid);
    }

    public String addNewLockControl(String ip_address, String roomid) {
        return roomDao.addNewLockControl(ip_address,roomid);
    }
}
