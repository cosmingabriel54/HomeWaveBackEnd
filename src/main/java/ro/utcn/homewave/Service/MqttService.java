package ro.utcn.homewave.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ro.utcn.homewave.Dao.DeviceDao;
import ro.utcn.homewave.Dao.SensorDataCallback;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class MqttService {

    private final MqttClient mqttClient;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();;
    @Autowired
    public MqttService(JdbcTemplate jdbcTemplate) throws MqttException {
        this.jdbcTemplate = jdbcTemplate;
        MqttClientPersistence persistence = new MemoryPersistence();
        this.mqttClient = new MqttClient("tcp://broker.hivemq.com:1883", MqttClient.generateClientId(),persistence);
    }
    @PostConstruct
    public void init() {
        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            mqttClient.connect(options);

            subscribeToPowerData();
        } catch (MqttException e) {
            throw new RuntimeException("Failed to connect MQTT client", e);
        }
    }
    private void subscribeToPowerData() throws MqttException {
        String topic = "/homewave/data/power_consumption";
        mqttClient.subscribe(topic, (t, message) -> {
            try {
                String payload = new String(message.getPayload()).trim();
                System.out.println("[MQTT] Received payload: " + payload);
                JsonNode json = objectMapper.readTree(payload);

                String mac = json.get("mac").asText();
                int duration = json.get("duration").asInt();
                int dutyCycle = json.get("duty_cycle").asInt();

                insertPowerEvent(mac, duration, dutyCycle);

            } catch (Exception e) {
                System.err.println("[MQTT] Failed to parse/insert payload: " + e.getMessage());
                e.printStackTrace();
            }
        });

        System.out.println("[MQTT] Subscribed to: " + topic);
    }
    private void insertPowerEvent(String mac, int durationSeconds, int dutyCycle) {
        String sql = """
                INSERT INTO lightbulb_power_events(mac_address, duration_seconds, duty_cycle)
                VALUES (?, ?, ?)
                """;

        jdbcTemplate.update(sql, mac, durationSeconds, dutyCycle);
        System.out.printf("[DB] Inserted event: MAC=%s, Duration=%ds, DutyCycle=%d%%\n", mac, durationSeconds, dutyCycle);
    }

    public void sendCommand(String deviceId, String command) {
        try {
            String topic = "/homewave/devices/" + deviceId.replace(":", "") + "/command";
            byte[] payload = command.getBytes();
            int qos = 1;
            boolean retained = !command.equals("wipe");
            mqttClient.publish(topic, payload, qos, retained);
            if(command.equals("wipe")){
                mqttClient.publish(topic, new byte[0], qos, false);
            }
            System.out.println("[MQTT] Published '" + command + "' to topic: " + topic);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
    public JSONObject getDeviceSensors(String mac_address) {
        if(Boolean.TRUE.equals(jdbcTemplate.queryForObject("select power_saving_mode from thermostat where REPLACE(mac_address, ':', '')=?", Boolean.class, mac_address))){
            JSONObject json = new JSONObject();
            json.putAll(getSensorsPowerSavingMode(mac_address));
            return json;
        }
        String topic = "/homewave/data/" + mac_address.replace(":", "") + "/sensor";
        CompletableFuture<JSONObject> future = new CompletableFuture<>();

        try {
            MqttClientPersistence persistence = new MemoryPersistence();
            MqttClient tempClient = new MqttClient("tcp://broker.hivemq.com:1883", MqttClient.generateClientId(),persistence);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(false);
            options.setCleanSession(true);
            tempClient.connect(options);

            tempClient.subscribe(topic, 1, (t, message) -> {
                String payload = new String(message.getPayload()).trim();
                String[] parts = payload.split(";");

                if (parts.length >= 2) {
                    JSONObject data = new JSONObject();
                    data.put("temperature", parts[0]);
                    data.put("humidity", parts[1]);
                    future.complete(data);
                } else {
                    future.completeExceptionally(new RuntimeException("Invalid payload: " + payload));
                }
            });
            String target = jdbcTemplate.queryForObject(
                    "SELECT CAST(status AS varchar) FROM thermostat WHERE REPLACE(mac_address, ':', '') = ?",
                    String.class,
                    mac_address
            );
            sendCommand(mac_address, "getdata:"+target);
            JSONObject result = future.get(10, TimeUnit.SECONDS);
            tempClient.unsubscribe(topic);
            tempClient.disconnect();
            tempClient.close();

            return result;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    public Map<String, Object> getSensorsPowerSavingMode(String mac_address) {
        String sql = "select temperature, humidity, status from thermostat where REPLACE(mac_address, ':', '')=?";

        Map<String, Object> result = Optional.of(
                jdbcTemplate.queryForMap(sql, mac_address)
        ).orElse(Collections.emptyMap());

        Double temperature = Optional.ofNullable((Double) result.get("temperature")).orElse(0.0d);
        Double humidity = Optional.ofNullable((Double) result.get("humidity")).orElse(0.0d);

        Map<String, Object> response = new HashMap<>();
        response.put("temperature", temperature);
        response.put("humidity", humidity);

        return response;
    }


}

