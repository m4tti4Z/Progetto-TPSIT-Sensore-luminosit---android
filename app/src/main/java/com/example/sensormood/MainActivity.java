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

    // --- Componenti Android e Interfaccia Utente ---
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private TextView luxTextView;
    private TextView diagnosisTextView;
    private LinearLayout mainLayout;
    private LinearLayout diagnosisContainer; // Nuovo contenitore per il bordo di stato
    private Button toggleTorchButton;
    private Button requestPermissionButton;
    private Button goToGameButton;
    private Spinner scenarioSpinner;

    // --- Gestione Stato ---
    private Scenario currentScenario = Scenario.LETTURA_RELAX;
    private boolean canWriteSettings = false;
    private boolean isTorchOn = false;

    // **Variabili per Stabilità/Debouncing (Anti-Flickering)**
    private float lastLuxDiagnosed = -1;
    private final float DIAGNOSIS_TOLERANCE = 5.0f;

    // --- Gestione Torcia ---
    private CameraManager cameraManager;
    private String cameraId = null;

    // Enum per definire gli scenari e i target Lux
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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Inizializzazione delle View
        luxTextView = findViewById(R.id.luxTextView);
        diagnosisTextView = findViewById(R.id.diagnosisTextView);
        diagnosisContainer = findViewById(R.id.diagnosisContainer); // Inizializzazione del contenitore
        toggleTorchButton = findViewById(R.id.toggleTorchButton);
        requestPermissionButton = findViewById(R.id.requestPermissionButton);
        goToGameButton = findViewById(R.id.goToGameButton);
        scenarioSpinner = findViewById(R.id.scenarioSpinner);
        mainLayout = findViewById(R.id.rootLayout);

        // 2. Setup Sensore
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        if (lightSensor == null) {
            Toast.makeText(this, "Sensore di Luce non disponibile.", Toast.LENGTH_LONG).show();
            luxTextView.setText("Errore: Sensore non trovato.");
            toggleTorchButton.setEnabled(false);
            requestPermissionButton.setEnabled(false);
            goToGameButton.setEnabled(false);
        }

        // 3. Setup Torcia
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

        // 4. Setup Spinner e Listeners
        setupSpinner();
        setupListeners();

        // 5. Check iniziale permessi
        canWriteSettings = checkWriteSettingsPermission();
        updatePermissionUI();
    }

    private void setupSpinner() {
        String[] scenarioNames = new String[Scenario.values().length];
        for (int i = 0; i < Scenario.values().length; i++) {
            scenarioNames[i] = Scenario.values()[i].name;
        }

        // 🌟 AGGIORNAMENTO: Usiamo il layout personalizzato per il testo Bianco
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                R.layout.spinner_item_white, // Layout per l'elemento visualizzato (chiuso)
                scenarioNames);

        // Layout personalizzato per la lista a discesa (dropdown)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_white);

        scenarioSpinner.setAdapter(adapter);

        scenarioSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentScenario = Scenario.values()[position];
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private void setupListeners() {
        toggleTorchButton.setOnClickListener(v -> toggleTorch(!isTorchOn));
        requestPermissionButton.setOnClickListener(v -> requestWriteSettingsPermission());

        // Listener per la Navigazione al Gioco
        goToGameButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, GameActivity.class);
                startActivity(intent);
            }
        });
    }

    // --- Sensor Listener con Soglia di Aggiornamento ---
    // --- Sensor Listener con Soglia di Aggiornamento ---
    private final SensorEventListener lightListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            float lux = event.values[0];

            // 🌟🌟🌟 1. LOGICA SFONDO DINAMICO (Dark/Tech Look) 🌟🌟🌟
            // Normalizziamo il Lux: La transizione massima avviene fino a 2000 Lux
            final float MAX_LUX_TRANSITION = 2000f;
            float factor = Math.min(1f, lux / MAX_LUX_TRANSITION); // Fattore da 0.0 a 1.0

            // Calcola il componente Blu/Grigio per un effetto Dark/Blue Technology
            int blueComponent = (int) (30 + factor * 150); // Base 30, max 180
            int grayComponent = (int) (18 + factor * 40);  // Base 18, max 58 (Base scura)

            // Crea il colore RGB
            int dynamicColor = Color.rgb(grayComponent, grayComponent, blueComponent);

            // Applica il colore allo sfondo principale (aggiornamento fluido)
            mainLayout.setBackgroundColor(dynamicColor);


            // 2. Aggiorna la TextView Lux in tempo reale
            luxTextView.setText(String.format("Luminosità Attuale: %.2f lux", lux));


            // 3. Controllo di Stabilità (Debouncing - Anti-Flickering)
            // Se Lux non è cambiato abbastanza (5 lux), evitiamo il costoso ricalcolo della diagnosi
            if (lastLuxDiagnosed != -1 && Math.abs(lux - lastLuxDiagnosed) < DIAGNOSIS_TOLERANCE) {
                if (canWriteSettings) {
                    float screenBrightness = Math.max(0.1f, Math.min(1.0f, lux / 1000f));
                    setScreenBrightness(screenBrightness);
                }
                return;
            }
            // Aggiorna il valore di riferimento per la prossima volta
            lastLuxDiagnosed = lux;


            // 4. Esegue la diagnosi dell'ambiente (solo se necessario)
            String diagnosis = performLightDiagnosis(lux); // Questo metodo contiene il random

            // 5. Regola la luminosità dello schermo (se i permessi sono OK)
            if (canWriteSettings) {
                float screenBrightness = Math.max(0.1f, Math.min(1.0f, lux / 1000f));
                setScreenBrightness(screenBrightness);
            }

            // 6. Aggiorna UI
            String torchStatus = isTorchOn ? "✅ ON" : "❌ OFF";
            String autoBrightStatus = canWriteSettings ? "🔆 ON" : "🚫 OFF";

            diagnosisTextView.setText(diagnosis);

            // Aggiorna l'interfaccia con gli stati dei servizi
            String servicesStatus = "Torcia: " + torchStatus + " | Auto-Luminosità: " + autoBrightStatus;
            diagnosisTextView.append("\n\nStato Servizi: " + servicesStatus);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    // Logica di Diagnosi dell'Ambiente (con varietà di testo e colore dinamico sul contenitore)
    private String performLightDiagnosis(float lux) {
        String baseMsg = "Scenario: " + currentScenario.name + "\n";
        String recommendation;
        Random random = new Random();
        int stateColor; // Colore per il bordo/highlight

        if (lux >= currentScenario.minLux && lux <= currentScenario.maxLux) {
            // Caso OTTIMO
            String[] ottime = {
                    "🌟 Qualità della luce **eccellente**! Perfetta per la concentrazione.",
                    "✅ Sei nell'intervallo ideale. **Ambiente visivo confortevole**.",
                    "💡 Illuminazione equilibrata. La tua vista ti ringrazierà!"
            };
            recommendation = ottime[random.nextInt(ottime.length)];
            stateColor = Color.parseColor("#4CAF50"); // Verde Scuro
        }
        else if (lux < currentScenario.minLux) {
            // Caso INSUFFICIENTE
            float deficit = currentScenario.minLux - lux;
            String[] suggerimenti = {
                    "Luce non sufficiente! Ti mancano **" + String.format("%.0f", deficit) + " lux**.",
                    "L'ambiente è troppo buio. Rischio di affaticamento visivo (**astenopia**).",
                    "Ti servono fonti di luce aggiuntive. **Migliora subito** l'illuminazione!"
            };
            recommendation = suggerimenti[random.nextInt(suggerimenti.length)];
            recommendation += "\n\nSuggerimento: Avvicina la fonte di luce o accendi la torcia ausiliaria 🔦.";
            stateColor = Color.parseColor("#FF9800"); // Arancione
        }
        else {
            // Caso ECCESSIVA
            float excess = lux - currentScenario.maxLux;
            String[] eccessive = {
                    "Luce troppo forte! Superi il target di **" + String.format("%.0f", excess) + " lux**.",
                    "C'è un **eccesso di luminosità**. Potrebbe causare abbagliamento o riflessi.",
                    "Attenzione! Troppa luce diretta. Potrebbe non essere confortevole."
            };
            recommendation = eccessive[random.nextInt(eccessive.length)];
            recommendation += "\n\nSuggerimento: Chiudi le tende, sposta la postazione o riduci l'intensità delle lampade.";
            stateColor = Color.parseColor("#F44336"); // Rosso
        }

        // **APPLICAZIONE DEI COLORI AL BORDO ESTERNO (Container)**
        diagnosisContainer.setBackgroundColor(stateColor);

        return baseMsg + "\n" + recommendation;
    }

    // --- Gestione Permesso WRITE_SETTINGS ---

    private boolean checkWriteSettingsPermission() {
        return Settings.System.canWrite(this);
    }

    private void requestWriteSettingsPermission() {
        if (!checkWriteSettingsPermission()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_CODE_WRITE_SETTINGS);
        } else {
            Toast.makeText(this, "Permesso Modifica Impostazioni già attivo.", Toast.LENGTH_SHORT).show();
        }
    }

    private void updatePermissionUI() {
        if (canWriteSettings) {
            requestPermissionButton.setVisibility(View.GONE);
        } else {
            requestPermissionButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_WRITE_SETTINGS) {
            canWriteSettings = Settings.System.canWrite(this);
            updatePermissionUI();
            if (canWriteSettings) {
                Toast.makeText(this, "Permesso Modifica Impostazioni Concesso.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permesso Negato. Luminosità automatica è disattivata.", Toast.LENGTH_LONG).show();
            }
        }
    }

    // --- Metodi Helper ---

    private void setScreenBrightness(float brightness) {
        if (!canWriteSettings) return;
        try {
            WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
            layoutParams.screenBrightness = brightness;
            getWindow().setAttributes(layoutParams);
        } catch (Exception e) {
            Toast.makeText(this, "Errore: Impossibile cambiare luminosità schermo.", Toast.LENGTH_SHORT).show();
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
            toggleTorchButton.setText(isTorchOn ? "Torcia Ausiliaria OFF" : "Torcia Ausiliaria ON");
        } catch (CameraAccessException e) {
            e.printStackTrace();
            Toast.makeText(this, "Errore Torcia: Riprovare.", Toast.LENGTH_SHORT).show();
        }
    }

    // --- Gestione Ciclo di Vita ---

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null && lightSensor != null) {
            sensorManager.unregisterListener(lightListener);
        }
        if (isTorchOn) {
            toggleTorch(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && lightSensor != null) {
            sensorManager.registerListener(lightListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        canWriteSettings = checkWriteSettingsPermission();
        updatePermissionUI();
    }
}