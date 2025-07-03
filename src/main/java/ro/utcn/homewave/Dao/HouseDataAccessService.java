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
    public String addNewHouse(String houseName, String uuid) {
        String iduser = getIdUser(uuid);
        if (iduser != null) {
            jdbcTemplate.update("INSERT INTO public.houses(house_name) VALUES (?)", houseName);
            Integer houseId = jdbcTemplate.queryForObject("SELECT currval(pg_get_serial_sequence('houses','id'))", Integer.class);
            jdbcTemplate.update("INSERT INTO public.user_houses(user_id, house_id) VALUES (?, ?)", Integer.valueOf(iduser), houseId);
            return "Success";
        } else {
            return "Error: User does not exist.";
        }
    }


    @Override
    public String deleteHouse(String houseid) {
        try {
            jdbcTemplate.execute("START TRANSACTION");

            Integer houseCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM public.houses WHERE id = ?",
                    Integer.class,Integer.valueOf(houseid)
            );

            if (houseCount == null || houseCount == 0) {
                jdbcTemplate.execute("ROLLBACK");
                return "Error: House does not exist.";
            }

            Integer roomCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM public.rooms WHERE houseid = ?",
                    Integer.class,
                    Integer.valueOf(houseid)

            );

            if (roomCount != null && roomCount > 0) {

                Integer lightControlCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM public.light_control WHERE room_id IN (SELECT id FROM public.rooms WHERE houseid = ?)",
                        Integer.class,
                        Integer.valueOf(houseid)

                );
                if (lightControlCount != null && lightControlCount > 0) {
                    jdbcTemplate.update("DELETE FROM public.light_control WHERE room_id IN (SELECT id FROM public.rooms WHERE houseid = ?)", Integer.valueOf(houseid));
                }

                Integer thermostatControlCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM public.thermostat WHERE room_id IN (SELECT id FROM rooms WHERE houseid = ?)",
                        Integer.class,
                        Integer.valueOf(houseid)
                );
                if (thermostatControlCount != null && thermostatControlCount > 0) {
                    jdbcTemplate.update("DELETE FROM thermostat WHERE room_id IN (SELECT id FROM rooms WHERE houseid = ?)", Integer.valueOf(houseid));
                }

                Integer lockControlCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM lock_control WHERE room_id IN (SELECT id FROM rooms WHERE houseid = ?)",
                        Integer.class,
                        Integer.valueOf(houseid)
                );
                if (lockControlCount != null && lockControlCount > 0) {
                    jdbcTemplate.update("DELETE FROM lock_control WHERE room_id IN (SELECT id FROM rooms WHERE houseid = ?)", Integer.valueOf(houseid));
                }
                jdbcTemplate.update("DELETE FROM rooms WHERE houseid = ?", Integer.valueOf(houseid));
            }

            jdbcTemplate.update("DELETE FROM houses WHERE id = ?", Integer.valueOf(houseid));

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
        String query = """
    SELECT h.id AS house_id,
         h.house_name,
         r.id AS room_id,
         r.room_name,
         STRING_AGG(DISTINCT th.ip_address, ',') AS thermostat_ips,
         STRING_AGG(DISTINCT lc.ip_address, ',') AS light_ips,
         STRING_AGG(DISTINCT lck.ip_address, ',') AS lock_ips
    FROM houses h
    JOIN user_houses uh ON h.id = uh.house_id
    LEFT JOIN rooms r ON h.id = r.houseid
    LEFT JOIN light_control lc ON r.id = lc.room_id
    LEFT JOIN lock_control lck ON r.id = lck.room_id
    LEFT JOIN thermostat th ON r.id = th.room_id
    WHERE uh.user_id = CAST(? AS INTEGER)
    GROUP BY h.id, h.house_name, r.id, r.room_name
    ORDER BY h.house_name, r.room_name
                                                              
""";

        JSONObject mainObject = new JSONObject();
        JSONArray homesArray = new JSONArray();

        class HouseAccumulator {
            JSONObject currentHouse = null;
            JSONArray roomsArray = null;
            String currentHouseName = "";

            void finalizeHouse() {
                if (currentHouse != null) {
                    homesArray.add(currentHouse);
                }
            }
        }

        HouseAccumulator accumulator = new HouseAccumulator();

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

        accumulator.finalizeHouse();

        mainObject.put("homes", homesArray);

        return mainObject;
    }



}
