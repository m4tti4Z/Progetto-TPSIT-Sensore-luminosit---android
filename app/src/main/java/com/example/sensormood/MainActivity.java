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
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
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
import android.widget.Toast;

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
    private Button toggleTorchButton;
    private Button changeBackgroundButton;
    private int[] backgroundColors = {
            Color.RED,
            Color.BLUE,
            Color.MAGENTA,
            Color.CYAN,
            Color.parseColor("#FF6600") // Arancione
    };
    private int currentColorIndex = 0;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Trova le View dal layout
        luxTextView = findViewById(R.id.luxTextView);
        toggleTorchButton = findViewById(R.id.toggleTorchButton); // <-- NUOVO: Trova il pulsante!
        changeBackgroundButton = findViewById(R.id.changeBackgroundButton);
        // Otteniamo il SensorManager dal sistema
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        // Setup Camera Manager per la torcia (necessario per toggleTorch)
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            // Ottieni l'ID della fotocamera posteriore (solitamente la prima)
            cameraId = cameraManager.getCameraIdList()[0];
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        // --- COLLEGAMENTO MANUALE DEL PULSANTE TORCIA ---
        // Questo permette all'utente di ACCENDERE/SPEGNERE la torcia manualmente
        toggleTorchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Se la torcia è spenta (false), chiamiamo toggleTorch(true) per accenderla, e viceversa.
                toggleTorch(!isTorchOn);
            }


        });
        changeBackgroundButton.setOnClickListener(v -> changeBackgroundManually());

        // Nota: Il sensore viene registrato in onResume, non qui in onCreate.
        // L'hai gestito correttamente nei tuoi metodi onResume e onPause!
    }

    // Listener che viene chiamato ogni volta che il sensore cambia valore
    private final SensorEventListener lightListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            float lux = event.values[0]; // valore del sensore
            String currentMode = "";
            LinearLayout mainLayout = findViewById(R.id.rootLayout);

            // Soglie (Mantenute così come sono, con OFF > ON)
            final float TORCH_ON_THRESHOLD = 40f;
            final float TORCH_OFF_THRESHOLD = 60f;

            // --- GESTIONE TORCIA (CENTRALE) ---
            // 1. Accendi la torcia se il Lux è basso E la torcia è spenta
            if (lux < TORCH_ON_THRESHOLD && !isTorchOn) {
                toggleTorch(true);
            }
            // 2. Spegni la torcia se il Lux è abbastanza alto E la torcia è accesa
            else if (lux > TORCH_OFF_THRESHOLD && isTorchOn) {
                toggleTorch(false);
            }

            // --- GESTIONE DELLE MODALITÀ SCHERMO (Separata dalla Torcia) ---

            // Modalità Notte
            if (lux < 50) {
                currentMode = "Night";
                setScreenBrightness(0.12f);
                mainLayout.setBackgroundColor(Color.parseColor("#001F3F")); // blu scuro
            }
            // Modalità Sole
            else if (lux > 10000) {
                currentMode = "Sun";
                setScreenBrightness(1.0f);
                mainLayout.setBackgroundColor(Color.parseColor("#FFDC00")); // giallo
            }
            // Modalità Focus
            else {
                currentMode = "Focus";
                setScreenBrightness(0.5f);
                mainLayout.setBackgroundColor(Color.parseColor("#2ECC40")); // verde
            }

            // Aggiorna TextView con modalità e valore lux
            luxTextView.setText("Modalità: " + currentMode + "\nLuminosità: " + lux + " lux" + (isTorchOn ? "\nTORCIA ACCESA" : ""));
        }


        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Non usiamo questa funzione per ora
        }
    };private void changeBackgroundManually() {
        LinearLayout mainLayout = findViewById(R.id.rootLayout);

        // 1. Attiva la Modalità Manuale
        boolean isManualMode = true;

        // 2. Passa al colore successivo
        currentColorIndex = (currentColorIndex + 1) % backgroundColors.length;
        int nextColor = backgroundColors[currentColorIndex];

        // 3. Applica il nuovo colore
        mainLayout.setBackgroundColor(nextColor);

        Toast.makeText(this, "Modalità Manuale Attivata", Toast.LENGTH_SHORT).show();
    }
    
    

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
