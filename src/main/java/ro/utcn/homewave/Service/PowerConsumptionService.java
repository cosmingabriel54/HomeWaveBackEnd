package ro.utcn.homewave.Service;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PowerConsumptionService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final double MAX_WATTAGE = 5;

    private record PowerEvent(Instant recordedAt, int dutyCycle, int durationSeconds) {}

    public JSONObject getAggregatedConsumption(String macAddress, boolean isFullReport) {
        String sql = "SELECT recorded_at, duty_cycle, duration_seconds FROM lightbulb_power_events WHERE mac_address = ?";

        List<PowerEvent> events = jdbcTemplate.query(sql, (ResultSet rs, int rowNum) ->
                new PowerEvent(
                        rs.getTimestamp("recorded_at").toInstant(),
                        rs.getInt("duty_cycle"),
                        rs.getInt("duration_seconds")
                ), macAddress);

        if (events.isEmpty()) {
            return null;
        }

        Map<LocalDate, Double> dailyConsumption = new TreeMap<>(Collections.reverseOrder());
        for (PowerEvent event : events) {
            LocalDate eventDate = event.recordedAt().atZone(ZoneId.systemDefault()).toLocalDate();
            double energyWh = (event.dutyCycle() / 100.0 * MAX_WATTAGE * event.durationSeconds()) / 3600.0;
            dailyConsumption.merge(eventDate, energyWh, Double::sum);
        }

        Map<String, List<JSONObject>> weeklyData = new TreeMap<>(Collections.reverseOrder());
        DateTimeFormatter weekFormatter = DateTimeFormatter.ofPattern("yyyy-'S'ww");

        for (Map.Entry<LocalDate, Double> entry : dailyConsumption.entrySet()) {
            LocalDate date = entry.getKey();
            String weekKey = date.format(weekFormatter);

            JSONObject dayData = new JSONObject();
            dayData.put("data", date.toString());
            dayData.put("consum", Math.round(entry.getValue() * 10000.0));


            weeklyData.computeIfAbsent(weekKey, k -> new ArrayList<>()).add(dayData);
        }

        JSONArray weeksArray = new JSONArray();
        WeekFields weekFields = WeekFields.of(Locale.getDefault());

        for (Map.Entry<String, List<JSONObject>> entry : weeklyData.entrySet()) {
            JSONObject weekObject = new JSONObject();
            List<JSONObject> days = entry.getValue().stream()
                    .sorted(Comparator.comparing(d -> (String) d.get("data")))
                    .collect(Collectors.toList());

            LocalDate firstDayOfWeek = LocalDate.parse((String) days.get(0).get("data"));

            weekObject.put("saptamana", entry.getKey());
            weekObject.put("inceput", firstDayOfWeek.with(weekFields.dayOfWeek(), 1).toString());
            weekObject.put("sfarsit", firstDayOfWeek.with(weekFields.dayOfWeek(), 7).toString());

            JSONArray daysArray = new JSONArray();
            daysArray.addAll(days);
            weekObject.put("zile", daysArray);

            weeksArray.add(weekObject);
        }

        JSONObject finalResponse = new JSONObject();

        finalResponse.put("unitate", "mWh");

        if (!isFullReport && !weeksArray.isEmpty()) {
            JSONArray singleWeekArray = new JSONArray();
            singleWeekArray.add(weeksArray.get(0));
            finalResponse.put("saptamani", singleWeekArray);
        } else {
            finalResponse.put("saptamani", weeksArray);
        }

        return finalResponse;
    }
}