package ro.utcn.homewave.Dao;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.ResultSetExtractor;

@Repository("ro.utcn.homewave.Dao.HouseDao")
public class HouseDataAccessService implements HouseDao {
    public final JdbcTemplate jdbcTemplate;
    @Autowired
    public HouseDataAccessService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    @Override
    public String addNewHouse(String houseName,String uuid) {
        String iduser = getIdUser(uuid);
        if(iduser!=null) {
            jdbcTemplate.update("INSERT INTO homewave.public.houses(house_name, iduser) VALUES(?,?)", houseName, Integer.valueOf(iduser));
            return "Success";
        }
        else{
            return "Eroare:User inexistent.";
        }
    }

    @Override
    public String deleteHouse(String houseid) {
        try {
            jdbcTemplate.execute("START TRANSACTION");

            Integer houseCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM homewave.public.houses WHERE id = ?",
                    Integer.class,Integer.valueOf(houseid)
            );

            if (houseCount == null || houseCount == 0) {
                jdbcTemplate.execute("ROLLBACK");
                return "Error: House does not exist.";
            }

            // Check if there are rooms associated with the house
            Integer roomCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM homewave.public.rooms WHERE houseid = ?",
                    Integer.class,
                    Integer.valueOf(houseid)

            );

            if (roomCount != null && roomCount > 0) {

                Integer lightControlCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM homewave.public.light_control WHERE room_id IN (SELECT id FROM homewave.public.rooms WHERE houseid = ?)",
                        Integer.class,
                        Integer.valueOf(houseid)

                );
                if (lightControlCount != null && lightControlCount > 0) {
                    jdbcTemplate.update("DELETE FROM homewave.public.light_control WHERE room_id IN (SELECT id FROM homewave.public.rooms WHERE houseid = ?)", Integer.valueOf(houseid));
                }

                // Check and delete thermostat controls associated with the rooms in the house
                Integer thermostatControlCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM homewave.public.thermostat WHERE room_id IN (SELECT id FROM rooms WHERE houseid = ?)",
                        Integer.class,
                        Integer.valueOf(houseid)
                );
                if (thermostatControlCount != null && thermostatControlCount > 0) {
                    jdbcTemplate.update("DELETE FROM thermostat WHERE room_id IN (SELECT id FROM rooms WHERE houseid = ?)", Integer.valueOf(houseid));
                }

                // Check and delete lock controls associated with the rooms in the house
                Integer lockControlCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM lock_control WHERE room_id IN (SELECT id FROM rooms WHERE houseid = ?)",
                        Integer.class,
                        Integer.valueOf(houseid)
                );
                if (lockControlCount != null && lockControlCount > 0) {
                    jdbcTemplate.update("DELETE FROM lock_control WHERE room_id IN (SELECT id FROM rooms WHERE houseid = ?)", Integer.valueOf(houseid));
                }

                // Finally, delete rooms associated with the house
                jdbcTemplate.update("DELETE FROM rooms WHERE houseid = ?", Integer.valueOf(houseid));
            }

            // Delete the house itself
            jdbcTemplate.update("DELETE FROM houses WHERE id = ?", Integer.valueOf(houseid));

            // Commit the transaction
            jdbcTemplate.execute("COMMIT");

            return "Success";
        } catch (Exception e) {
            e.printStackTrace();
            jdbcTemplate.execute("ROLLBACK");
            return "Error: House does not exist or could not be deleted.";
        }
    }



    @Override
    public JSONObject getHouses(String uuid) {
        String idUser=getIdUser(uuid);
        System.out.println(idUser+" "+getHouseData(idUser));
        return getHouseData(idUser);
    }
    public String getIdUser(String uuid){
        if(Objects.equals(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM uuids WHERE uuid = ?", Integer.class, uuid), 0)) {
            return null;
        }
        return String.valueOf(jdbcTemplate.queryForObject("SELECT iduser FROM uuids WHERE uuid = ?", Integer.class, uuid));
    }
    public JSONObject getHouseData(String userId) {
        // SQL query to fetch data, including IDs for houses and rooms
        String query = """
    SELECT h.id AS house_id,
                                  h.house_name,
                                  r.id AS room_id,
                                  r.room_name,
                                  STRING_AGG(DISTINCT th.ip_address, ',') AS thermostat_ips,
                                  STRING_AGG(DISTINCT lc.ip_address, ',') AS light_ips,
                                  STRING_AGG(DISTINCT lck.ip_address, ',') AS lock_ips
                           FROM houses h
                           LEFT JOIN rooms r ON h.id = r.houseid
                           LEFT JOIN light_control lc ON r.id = lc.room_id
                           LEFT JOIN lock_control lck ON r.id = lck.room_id
                           LEFT JOIN thermostat th ON r.id = th.room_id
                           WHERE h.iduser = CAST(? AS INTEGER)
                           GROUP BY h.id, h.house_name, r.id, r.room_name
                           ORDER BY h.house_name, r.room_name;
""";

        // Initialize the main object and homesArray outside the RowMapper
        JSONObject mainObject = new JSONObject();
        JSONArray homesArray = new JSONArray();

        // Custom class to hold the current house and room information
        class HouseAccumulator {
            JSONObject currentHouse = null;
            JSONArray roomsArray = null;
            String currentHouseName = "";

            // Finalize the current house by adding it to homesArray
            void finalizeHouse() {
                if (currentHouse != null) {
                    homesArray.add(currentHouse);
                }
            }
        }

        // Create an instance of HouseAccumulator to track the current house
        HouseAccumulator accumulator = new HouseAccumulator();

        // Execute the query and map the result set
        jdbcTemplate.query(query, new Object[]{userId}, (ResultSetExtractor<Void>) rs -> {
    while (rs.next()) {
        String houseName = rs.getString("house_name");
        String roomName = rs.getString("room_name");
        Long houseId = rs.getLong("house_id");
        Long roomId = rs.getLong("room_id");
        String lightIPs = rs.getString("light_ips");
        String lockIPs = rs.getString("lock_ips");
        String thermostatIPs = rs.getString("thermostat_ips");

        if (!houseName.equals(accumulator.currentHouseName)) {
            accumulator.finalizeHouse();
            accumulator.currentHouseName = houseName;
            accumulator.currentHouse = new JSONObject();
            accumulator.currentHouse.put("houseId", houseId);
            accumulator.currentHouse.put("houseName", accumulator.currentHouseName);
            accumulator.roomsArray = new JSONArray();
            accumulator.currentHouse.put("rooms", accumulator.roomsArray);
        }

        if (roomId != null && roomId != 0 && roomName != null) {
            JSONObject currentRoom = new JSONObject();
            currentRoom.put("roomId", roomId);
            currentRoom.put("roomName", roomName);

            if (lightIPs != null) {
                JSONArray lightControllers = new JSONArray();
                for (String ip : lightIPs.split(",")) {
                    lightControllers.add(ip.trim());
                }
                currentRoom.put("lightControllers", lightControllers);
            }

            if (lockIPs != null) {
                JSONArray lockControllers = new JSONArray();
                for (String ip : lockIPs.split(",")) {
                    lockControllers.add(ip.trim());
                }
                currentRoom.put("lockControllers", lockControllers);
            }

            if (thermostatIPs != null) {
                JSONArray thermostatControllers = new JSONArray();
                for (String ip : thermostatIPs.split(",")) {
                    thermostatControllers.add(ip.trim());
                }
                currentRoom.put("thermostatControllers", thermostatControllers);
            }

            if (accumulator.roomsArray != null) {
                accumulator.roomsArray.add(currentRoom);
            }
        }
    }
    return null;
});

        // Finalize the last processed house
        accumulator.finalizeHouse();

        // Add the homes array to the main object
        mainObject.put("homes", homesArray);

        // Return the main object, even if empty
        return mainObject;
    }



}
