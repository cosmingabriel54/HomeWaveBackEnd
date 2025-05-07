package ro.utcn.homewave.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ro.utcn.homewave.Dao.SensorDataCallback;

import javax.annotation.PostConstruct;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class MqttService {

    private final MqttClient mqttClient;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public MqttService(JdbcTemplate jdbcTemplate) throws MqttException {
        this.jdbcTemplate = jdbcTemplate;
        this.mqttClient = new MqttClient("tcp://broker.hivemq.com:1883", MqttClient.generateClientId());
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
            MqttMessage message = new MqttMessage(command.getBytes());
            message.setQos(1);
            message.setRetained(true);
            mqttClient.publish(topic, message);
            if(command.equals("wipe")){
                MqttMessage empty = new MqttMessage(new byte[0]);
                empty.setQos(1);
                empty.setRetained(true);
                mqttClient.publish(topic, empty);
            }
            System.out.println("[MQTT] Published '" + command + "' to topic: " + topic);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
    public JSONObject getDeviceSensors(String mac_address) {
        CompletableFuture<JSONObject> future = new CompletableFuture<>();

        sendCommand(mac_address, "getdata");
        String topic = "/homewave/data/" + mac_address.replace(":", "") + "/sensor";
        try {
            mqttClient.subscribe(topic, 1, (t, message) -> {
                String payload = new String(message.getPayload()).trim();
                String[] parts = payload.split(";");

                if (parts.length >= 2) {
                    JSONObject data = new JSONObject();
                    data.put("temperature", parts[0]);
                    data.put("humidity", parts[1]);

                    future.complete(data); // complete the future
                } else {
                    future.completeExceptionally(new RuntimeException("Invalid payload: " + payload));
                }
            });
            return future.get(5, TimeUnit.SECONDS);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}

