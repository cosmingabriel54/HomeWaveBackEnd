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

@Repository("ro.utcn.homewave.Dao.RoomDao")
public class RoomDataAccessService implements RoomDao {
    private final JdbcTemplate jdbcTemplate;
    @Autowired
    public RoomDataAccessService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    @Override
    public String addNewRoom(String roomName, String houseId) {
        try {
            if (houseId == null || houseId.isEmpty()) {
                return "Eroare: houseId cannot be null or empty.";
            }

            // Parse houseId to integer (if houseId is supposed to be an integer)
            int houseIdInt = Integer.parseInt(houseId);  // Ensure houseId is a valid integer

            // Execute the query
            jdbcTemplate.update("INSERT INTO rooms(room_name, houseid) VALUES(?,?)", roomName, houseIdInt);
            return "Success";
        } catch (NumberFormatException e) {
            // Catch if houseId is not a valid integer
            e.printStackTrace();
            return "Eroare: Invalid houseId format.";
        } catch (Exception e) {
            e.printStackTrace();
            return "Eroare: Camera existenta.";
        }
    }


    @Override
    public String deleteRoom(String roomid) {
        try {
            // Start a transaction
            jdbcTemplate.execute("START TRANSACTION");

            // Check if the room exists
            Integer roomCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM rooms WHERE id = ?",
                    Integer.class,
                    Integer.valueOf(roomid)
            );

            if (roomCount == null || roomCount == 0) {
                jdbcTemplate.execute("ROLLBACK");
                return "Error: Room does not exist.";
            }

            // Check and delete light controls associated with the room
            Integer lightControlCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM light_control WHERE room_id = ?",
                    Integer.class,
                    Integer.valueOf(roomid)
            );
            if (lightControlCount != null && lightControlCount > 0) {
                jdbcTemplate.update("DELETE FROM light_control WHERE room_id = ?", Integer.valueOf(roomid));
            }

            // Check and delete thermostat controls associated with the room
            Integer thermostatControlCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM thermostat WHERE room_id = ?",
                    Integer.class,
                    Integer.valueOf(roomid)
            );
            if (thermostatControlCount != null && thermostatControlCount > 0) {
                jdbcTemplate.update("DELETE FROM thermostat WHERE room_id = ?", Integer.valueOf(roomid));
            }

            // Check and delete lock controls associated with the room
            Integer lockControlCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM lock_control WHERE room_id = ?",
                    Integer.class,
                    Integer.valueOf(roomid)
            );
            if (lockControlCount != null && lockControlCount > 0) {
                jdbcTemplate.update("DELETE FROM lock_control WHERE room_id = ?", Integer.valueOf(roomid));
            }

            // Delete the room itself
            jdbcTemplate.update("DELETE FROM rooms WHERE id = ?", Integer.valueOf(roomid));

            // Commit the transaction
            jdbcTemplate.execute("COMMIT");

            return "Success";
        } catch (Exception e) {
            e.printStackTrace();
            // Rollback the transaction if something goes wrong
            jdbcTemplate.execute("ROLLBACK");
            return "Error: Room does not exist or could not be deleted.";
        }
    }



    @Override
    public JSONObject getRooms(String homeid) {
        return getHouseDataById(Integer.parseInt(homeid));
    }

    @Override
    public String addNewLightControl(String ip_address, String roomid) {
        try{
            jdbcTemplate.update("INSERT INTO light_control(ip_address, room_id) VALUES(?,?)", ip_address, Integer.valueOf(roomid));
            return "Success";
        }catch (Exception e){
            e.printStackTrace();
            return "Eroare:Camera inexistenta.";
        }
    }

    @Override
    public String addNewThermostat(String ip_address, String roomid) {
        try{
            jdbcTemplate.update("INSERT INTO thermostat(ip_address, room_id) VALUES(?,?)", ip_address, Integer.valueOf(roomid));
            return "Success";
        }catch (Exception e){
            e.printStackTrace();
            return "Eroare:Camera inexistenta.";
        }
    }

    @Override
    public String addNewLockControl(String ip_address, String roomid) {
        try {
            jdbcTemplate.update("INSERT INTO lock_control(ip_address, room_id) VALUES(?,?)", ip_address, Integer.valueOf(roomid));
            return "Success";
        } catch (Exception e) {
            e.printStackTrace();
            return "Eroare:Camera inexistenta.";
        }
    }
    public JSONObject getHouseDataById(int homeId) {
        // SQL query to fetch data for a specific house and its associated devices
        String query = """
        SELECT h.house_name, r.id AS room_id, r.room_name,
               th.ip_address AS thermostat_ip,
               lc.ip_address AS light_ip,
               lck.ip_address AS lock_ip
        FROM houses h
        LEFT JOIN rooms r ON h.id = r.houseid
        LEFT JOIN light_control lc ON r.id = lc.room_id
        LEFT JOIN lock_control lck ON r.id = lck.room_id
        LEFT JOIN thermostat th ON r.id = th.room_id
        WHERE h.id = ?
        ORDER BY r.room_name;
    """;

        // Execute the query and map the results
        List<JSONObject> results = jdbcTemplate.query(query, new Object[]{homeId}, new HouseRowMapper());

        // If no results, return a default response
        if (results.isEmpty()) {
            return createDefaultResponse();
        } else {
            return results.get(0);
        }
    }

    // RowMapper to map each row from the ResultSet to a JSON object
    private static class HouseRowMapper implements RowMapper<JSONObject> {
        private JSONObject mainObject = new JSONObject();
        private JSONArray roomsArray = new JSONArray();

        @Override
        public JSONObject mapRow(ResultSet rs, int rowNum) throws SQLException {
            String houseName = rs.getString("house_name");
            Integer roomId = rs.getInt("room_id");
            String roomName = rs.getString("room_name");
            String lightIP = rs.getString("light_ip");
            String lockIP = rs.getString("lock_ip");
            String thermostatIP = rs.getString("thermostat_ip");

            // Initialize the main object only once
            if (rowNum == 0) {
                mainObject.put("houseName", houseName);
                mainObject.put("rooms", roomsArray);
            }

            // Create room object only if roomName is not null
            if (roomName != null) {
                JSONObject roomObject = new JSONObject();
                roomObject.put("roomId", roomId);
                roomObject.put("roomName", roomName);
                roomObject.put("lightControllers", ipToJSONArray(lightIP));
                roomObject.put("lockControllers", ipToJSONArray(lockIP));
                roomObject.put("thermostatControllers", ipToJSONArray(thermostatIP));
                roomsArray.add(roomObject);
            }

            return mainObject;
        }

        // Helper function to convert a comma-separated IP string to JSONArray
        private JSONArray ipToJSONArray(String ipAddresses) {
            JSONArray ipArray = new JSONArray();
            if (ipAddresses != null) {
                for (String ip : ipAddresses.split(",")) {
                    ipArray.add(ip.trim());
                }
            }
            return ipArray;
        }
    }

    // Method to create a default response if no data is found
    private JSONObject createDefaultResponse() {
        JSONObject defaultResponse = new JSONObject();
        JSONObject houseObject = new JSONObject();
        houseObject.put("houseName", "Unknown House");
        houseObject.put("rooms", new JSONArray());
        defaultResponse.put("house", houseObject);
        return defaultResponse;
    }

}

