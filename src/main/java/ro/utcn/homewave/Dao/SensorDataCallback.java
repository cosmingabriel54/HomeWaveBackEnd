package ro.utcn.homewave.Dao;

import org.json.simple.JSONObject;

public interface SensorDataCallback {
    void onSensorData(JSONObject data);
}

