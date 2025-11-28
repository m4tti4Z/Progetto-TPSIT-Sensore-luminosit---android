package com.example.sensormood;

import android.graphics.Color;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;



import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    // Manager che ci permette di accedere ai sensori del telefono
    private SensorManager sensorManager;

    // Sensore di luce
    private Sensor lightSensor;

    // TextView dove mostriamo il valore della luminosità
    private TextView luxTextView;

    private CameraManager cameraManager;
    private String cameraId;
    private boolean isTorchOn = false; // stato della torcia


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Collego l'activity al layout XML
        setContentView(R.layout.activity_main);

        // Trovo il TextView dal layout
        luxTextView = findViewById(R.id.luxTextView);

        // Otteniamo il SensorManager dal sistema
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        // Prendiamo il sensore di luce dal telefono
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        if (lightSensor != null) {
            // Se il sensore esiste, registriamo il listener per leggere i valori
            sensorManager.registerListener(lightListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            // Se il telefono non ha il sensore di luce, mostriamo un messaggio
            luxTextView.setText("Sensore di luce non disponibile!");
        }
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = cameraManager.getCameraIdList()[0]; // prendiamo la prima camera
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // Listener che viene chiamato ogni volta che il sensore cambia valore
    private final SensorEventListener lightListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            float lux = event.values[0]; // valore del sensore
            String currentMode = "";

            LinearLayout mainLayout = findViewById(R.id.mainLayout);

            // Soglie per accendere/spegnere la torcia (hysteresis)
            final float TORCH_ON_THRESHOLD = 40f;
            final float TORCH_OFF_THRESHOLD = 60f;

            // Modalità Notte
            if (lux < 50) {
                currentMode = "Night";
                setScreenBrightness(0.12f); // luminosità minima
                mainLayout.setBackgroundColor(Color.parseColor("#001F3F")); // blu scuro

                // Torcia stabile
                if (lux < TORCH_ON_THRESHOLD && !isTorchOn) {
                    toggleTorch(true);
                }

                // Modalità Sole
            } else if (lux > 10000) {
                currentMode = "Sun";
                setScreenBrightness(1.0f); // luminosità massima
                mainLayout.setBackgroundColor(Color.parseColor("#FFDC00")); // giallo

                // Spegni la torcia se accesa
                if (isTorchOn && lux > TORCH_OFF_THRESHOLD) {
                    toggleTorch(false);
                }

                // Modalità Focus
            } else {
                currentMode = "Focus";
                setScreenBrightness(0.5f); // luminosità media
                mainLayout.setBackgroundColor(Color.parseColor("#2ECC40")); // verde

                // Spegni la torcia se accesa
                if (isTorchOn && lux > TORCH_OFF_THRESHOLD) {
                    toggleTorch(false);
                }
            }

            // Aggiorna TextView con modalità e valore lux
            luxTextView.setText("Modalità: " + currentMode + "\nLuminosità: " + lux + " lux");
        }


        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Non usiamo questa funzione per ora
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        // Quando l'activity va in pausa, fermiamo il listener per risparmiare batteria
        sensorManager.unregisterListener(lightListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Quando l'activity ritorna visibile, riattiviamo il listener
        sensorManager.registerListener(lightListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void setScreenBrightness(float brightness) {
        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
        layoutParams.screenBrightness = brightness; // impostiamo la luminosità
        getWindow().setAttributes(layoutParams); // applichiamo le modifiche
    }
    private void toggleTorch(boolean state) {
        try {
            cameraManager.setTorchMode(cameraId, state);
            isTorchOn = state;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

}
