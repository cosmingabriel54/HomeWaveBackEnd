package ro.utcn.homewave.Service;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Service;

@Service
public class MqttService {

    private final MqttClient mqttClient;

    public MqttService() throws MqttException {
        mqttClient = new MqttClient("tcp://broker.hivemq.com:1883", MqttClient.generateClientId());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        mqttClient.connect(options);
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
}

