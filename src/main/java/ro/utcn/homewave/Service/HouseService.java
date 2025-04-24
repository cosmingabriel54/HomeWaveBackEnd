package ro.utcn.homewave.Service;

import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ro.utcn.homewave.Dao.HouseDao;

@Service
public class HouseService {
    public final HouseDao houseDao;
    @Autowired
    public HouseService(HouseDao houseDao) {
        this.houseDao = houseDao;
    }
    public JSONObject getHouses(String uuid) {
        return houseDao.getHouses(uuid);
    }
    public String addNewHouse(String houseName,String uuid) {
        return houseDao.addNewHouse(houseName,uuid);
    }
    public String deleteHouse(String houseId) {
        return houseDao.deleteHouse(houseId);
    }

}
