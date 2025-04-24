package ro.utcn.homewave.Dao;

import org.json.simple.JSONObject;

public interface HouseDao {
    public String addNewHouse(String houseName,String uuid);
    public String deleteHouse(String houseId);
    public JSONObject getHouses(String uuid);
}
