package com.example.sensormood;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Random;

public class MainActivity extends AppCompatActivity {

    // --- Costanti e Codici di Richiesta ---
    private static final int REQUEST_CODE_WRITE_SETTINGS = 100;
    private final float DIAGNOSIS_TOLERANCE = 5.0f;

    // --- Componenti UI ---
    private LinearLayout mainLayout;
    private LinearLayout diagnosisContainer;
    private TextView luxTextView;
    private TextView diagnosisTextView;
    private Button toggleTorchButton;
    private Button requestPermissionButton;
    private Button goToGameButton;
    private Spinner scenarioSpinner;

    // --- Sensori ---
    private SensorManager sensorManager;
    private Sensor lightSensor;

    // --- Stato ---
    private Scenario currentScenario = Scenario.LETTURA_RELAX;
    private boolean canWriteSettings = false;
    private boolean isTorchOn = false;
    private float lastLuxDiagnosed = -1;

    // --- Torcia ---
    private CameraManager cameraManager;
    private String cameraId = null;

    // --- Enum Scenario ---
    private enum Scenario {
        STUDIO_INTENSO(450, 750, "Studio Intenso/Lavoro al PC"),
        LETTURA_RELAX(250, 400, "Lettura e Attività Generali"),
        CORRIDOIO(80, 150, "Corridoi e Illuminazione di Passaggio"),
        LAVORI_DETTAGLIATI(350, 550, "Cucina o Lavori di Precisione"),
        RIPOSO_NOTTURNO(0, 50, "Riposo Notturno o Ambienti BUI"),
        NESSUN_SCENARIO(0, 100000, "Misurazione Standard");

        public final int minLux;
        public final int maxLux;
        public final String name;

        Scenario(int minLux, int maxLux, String name) {
            this.minLux = minLux;
            this.maxLux = maxLux;
            this.name = name;
        }
    }

    // -------------------------
    // 1. Ciclo di vita - onCreate
    // -------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeUI();
        initializeSensors();
        initializeTorch();
        setupSpinner();
        setupListeners();

        canWriteSettings = checkWriteSettingsPermission();
        updatePermissionUI();
    }

    // -------------------------
    // 2. Inizializzazione UI
    // -------------------------
    private void initializeUI() {
        mainLayout = findViewById(R.id.rootLayout);
        luxTextView = findViewById(R.id.luxTextView);
        diagnosisTextView = findViewById(R.id.diagnosisTextView);
        diagnosisContainer = findViewById(R.id.diagnosisContainer);
        toggleTorchButton = findViewById(R.id.toggleTorchButton);
        requestPermissionButton = findViewById(R.id.requestPermissionButton);
        goToGameButton = findViewById(R.id.goToGameButton);
        scenarioSpinner = findViewById(R.id.scenarioSpinner);
    }

    // -------------------------
    // 3. Inizializzazione Sensori
    // -------------------------
    private void initializeSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (lightSensor == null) {
            Toast.makeText(this, "Sensore di Luce non disponibile.", Toast.LENGTH_LONG).show();
            luxTextView.setText("Errore: Sensore non trovato.");
            toggleTorchButton.setEnabled(false);
            requestPermissionButton.setEnabled(false);
            goToGameButton.setEnabled(false);
        }
    }

    // -------------------------
    // 4. Inizializzazione Torcia
    // -------------------------
    private void initializeTorch() {
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIds = cameraManager.getCameraIdList();
            if (cameraIds.length > 0) {
                cameraId = cameraIds[0];
            } else {
                throw new CameraAccessException(CameraAccessException.CAMERA_DISABLED, "Nessuna fotocamera trovata.");
            }
        } catch (CameraAccessException e) {
            Toast.makeText(this, "Torcia non supportata o accessibile.", Toast.LENGTH_SHORT).show();
            toggleTorchButton.setEnabled(false);
        }
    }

    // -------------------------
    // 5. Setup Spinner
    // -------------------------
    private void setupSpinner() {
        String[] scenarioNames = new String[Scenario.values().length];
        for (int i = 0; i < Scenario.values().length; i++) {
            scenarioNames[i] = Scenario.values()[i].name;
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                R.layout.spinner_item_white,
                scenarioNames);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_white);
        scenarioSpinner.setAdapter(adapter);

        scenarioSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentScenario = Scenario.values()[position];
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // -------------------------
    // 6. Setup Listeners Bottoni
    // -------------------------
    private void setupListeners() {
        toggleTorchButton.setOnClickListener(v -> toggleTorch(!isTorchOn));
        requestPermissionButton.setOnClickListener(v -> requestWriteSettingsPermission());
        goToGameButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, GameActivity.class);
            startActivity(intent);
        });
    }

    // -------------------------
    // 7. SensorEventListener
    // -------------------------
    private final SensorEventListener lightListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            float lux = event.values[0];

            // Sfondo dinamico
            final float MAX_LUX_TRANSITION = 2000f;
            float factor = Math.min(1f, lux / MAX_LUX_TRANSITION);
            int blueComponent = (int) (30 + factor * 150);
            int grayComponent = (int) (18 + factor * 40);
            int dynamicColor = Color.rgb(grayComponent, grayComponent, blueComponent);
            mainLayout.setBackgroundColor(dynamicColor);

            luxTextView.setText(String.format("Luminosità Attuale: %.2f lux", lux));

            if (lastLuxDiagnosed != -1 && Math.abs(lux - lastLuxDiagnosed) < DIAGNOSIS_TOLERANCE) {
                if (canWriteSettings) setScreenBrightness(Math.max(0.1f, Math.min(1.0f, lux / 1000f)));
                return;
            }
            lastLuxDiagnosed = lux;

            String diagnosis = performLightDiagnosis(lux);

            if (canWriteSettings) setScreenBrightness(Math.max(0.1f, Math.min(1.0f, lux / 1000f)));

            String torchStatus = isTorchOn ? "✅ ON" : "❌ OFF";
            String autoBrightStatus = canWriteSettings ? "🔆 ON" : "🚫 OFF";
            diagnosisTextView.setText(diagnosis + "\n\nStato Servizi: Torcia: " + torchStatus + " | Auto-Luminosità: " + autoBrightStatus);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    // -------------------------
    // 8. Diagnosi Luce
    // -------------------------
    private String performLightDiagnosis(float lux) {
        String baseMsg = "Scenario: " + currentScenario.name + "\n";
        Random random = new Random();
        String recommendation;
        int stateColor;

        if (lux >= currentScenario.minLux && lux <= currentScenario.maxLux) {
            String[] ottime = {"🌟 Qualità della luce eccellente!","✅ Ambiente visivo confortevole","💡 Illuminazione equilibrata"};
            recommendation = ottime[random.nextInt(ottime.length)];
            stateColor = Color.parseColor("#4CAF50");
        } else if (lux < currentScenario.minLux) {
            float deficit = currentScenario.minLux - lux;
            String[] suggerimenti = {"Luce insufficiente! Mancano " + String.format("%.0f", deficit) + " lux.","Ambiente troppo buio","Serve più illuminazione"};
            recommendation = suggerimenti[random.nextInt(suggerimenti.length)] + "\n\nSuggerimento: Avvicina la luce o accendi la torcia 🔦.";
            stateColor = Color.parseColor("#FF9800");
        } else {
            float excess = lux - currentScenario.maxLux;
            String[] eccessive = {"Troppa luce! Superi " + String.format("%.0f", excess) + " lux","Eccesso di luminosità","Potenziale abbagliamento"};
            recommendation = eccessive[random.nextInt(eccessive.length)] + "\n\nSuggerimento: Riduci la luce.";
            stateColor = Color.parseColor("#F44336");
        }

        diagnosisContainer.setBackgroundColor(stateColor);
        return baseMsg + "\n" + recommendation;
    }

    // -------------------------
    // 9. Permessi WRITE_SETTINGS
    // -------------------------
    private boolean checkWriteSettingsPermission() {
        return Settings.System.canWrite(this);
    }

    private void requestWriteSettingsPermission() {
        if (!checkWriteSettingsPermission()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_CODE_WRITE_SETTINGS);
        } else {
            Toast.makeText(this, "Permesso già attivo.", Toast.LENGTH_SHORT).show();
        }
    }

    private void updatePermissionUI() {
        requestPermissionButton.setVisibility(canWriteSettings ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_WRITE_SETTINGS) {
            canWriteSettings = Settings.System.canWrite(this);
            updatePermissionUI();
            Toast.makeText(this, canWriteSettings ? "Permesso Concesso." : "Permesso Negato.", Toast.LENGTH_LONG).show();
        }
    }

    // -------------------------
    // 10. Metodi Helper
    // -------------------------
    private void setScreenBrightness(float brightness) {
        if (!canWriteSettings) return;
        try {
            WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
            layoutParams.screenBrightness = brightness;
            getWindow().setAttributes(layoutParams);
        } catch (Exception e) {
            Toast.makeText(this, "Errore: Impossibile cambiare luminosità.", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleTorch(boolean state) {
        if (cameraId == null) {
            Toast.makeText(this, "Errore: Torcia non supportata.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            cameraManager.setTorchMode(cameraId, state);
            isTorchOn = state;
            toggleTorchButton.setText(isTorchOn ? "Torcia OFF" : "Torcia ON");
        } catch (CameraAccessException e) {
            e.printStackTrace();
            Toast.makeText(this, "Errore Torcia: Riprovare.", Toast.LENGTH_SHORT).show();
        }
    }

    // -------------------------
    // 11. Ciclo di vita Activity
    // -------------------------
    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && lightSensor != null) {
            sensorManager.registerListener(lightListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        canWriteSettings = checkWriteSettingsPermission();
        updatePermissionUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null && lightSensor != null) {
            sensorManager.unregisterListener(lightListener);
        }
        if (isTorchOn) toggleTorch(false);
    }
}
