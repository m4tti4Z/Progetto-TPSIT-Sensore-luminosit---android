package com.example.sensormood;

import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Random;

public class GameActivity extends AppCompatActivity {

    // --- Sensori ---
    private SensorManager sensorManager;
    private Sensor lightSensor;

    // 🌟🌟🌟 Aggiunte le dichiarazioni mancanti che causavano l'errore "Cannot resolve symbol" 🌟🌟🌟
    // --- UI Componenti ---
    private TextView targetLuxTextView;
    private TextView currentLuxTextView;
    private TextView timerTextView;
    private TextView feedbackTextView;
    private Button startButton;
    private LinearLayout rootLayout;
    // 🌟🌟🌟 Fine aggiunte 🌟🌟🌟

    // --- Variabili di Gioco ---
    private int targetLux;
    private final int MIN_LUX = 50;
    private final int MAX_LUX = 2000;
    private final float TOLERANCE = 15f;
    private boolean isGameActive = false;
    private long startTime;
    private final Handler timerHandler = new Handler();

    // --- Logica Cronometro ---
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isGameActive) {
                long millis = System.currentTimeMillis() - startTime;
                float seconds = millis / 1000.0f;
                timerTextView.setText(String.format("Tempo: %.1fs", seconds));
                timerHandler.postDelayed(this, 100);
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        // 1. Inizializzazione delle View
        targetLuxTextView = findViewById(R.id.targetLuxTextView);
        currentLuxTextView = findViewById(R.id.currentLuxTextView);
        timerTextView = findViewById(R.id.timerTextView);
        feedbackTextView = findViewById(R.id.feedbackTextView);
        startButton = findViewById(R.id.startButton);
        rootLayout = findViewById(R.id.rootLayout);

        // 2. Setup Sensore
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        if (lightSensor == null) {
            Toast.makeText(this, "Sensore di Luce non disponibile. Impossibile giocare.", Toast.LENGTH_LONG).show();
            startButton.setEnabled(false);
            return;
        }

        // 3. Listener del pulsante START
        startButton.setOnClickListener(v -> startGame());

        targetLuxTextView.setText("Target: -- lux");
    }

    private void startGame() {
        if (isGameActive) {
            stopGame(false);
        }

        // 1. Genera un nuovo target Lux
        Random random = new Random();
        targetLux = random.nextInt(MAX_LUX - MIN_LUX + 1) + MIN_LUX;

        // 2. Resetta e avvia il Cronometro
        startTime = System.currentTimeMillis();
        timerHandler.post(timerRunnable);

        // 3. Aggiorna l'UI
        isGameActive = true;
        targetLuxTextView.setText("TARGET: " + targetLux + " lux");
        feedbackTextView.setText("Avvicinati al valore Lux nell'ambiente! (Tolleranza ±" + (int)TOLERANCE + ")");
        startButton.setText("IN CORSO...");
        startButton.setEnabled(false);
        rootLayout.setBackgroundColor(Color.parseColor("#303030")); // Resetta colore di sfondo
    }

    private void stopGame(boolean win) {
        isGameActive = false;
        timerHandler.removeCallbacks(timerRunnable);
        startButton.setEnabled(true);
        startButton.setText("NUOVO ROUND");

        if (win) {
            long finalTimeMillis = System.currentTimeMillis() - startTime;//tempo impiegato
            float finalSeconds = finalTimeMillis / 1000.0f;
            Toast.makeText(this, "VITTORIA! Tempo: " + String.format("%.2fs", finalSeconds), Toast.LENGTH_LONG).show();
            feedbackTextView.setText("🏆 VITTORIA! Tempo finale: " + String.format("%.2fs", finalSeconds) + "\nProva a battere il tuo record!");
            rootLayout.setBackgroundColor(Color.parseColor("#4CAF50")); // Verde Vittoria
        } else {
            rootLayout.setBackgroundColor(Color.parseColor("#303030"));
            feedbackTextView.setText("Round interrotto. Premi START per iniziare!");
        }
    }

    private final SensorEventListener lightListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            float currentLux = event.values[0];
            currentLuxTextView.setText(String.format("Lettura: %.1f lux", currentLux));

            if (!isGameActive) return;

            float diff = Math.abs(currentLux - targetLux);

            // 1. Condizione di Vittoria
            if (diff <= TOLERANCE) {
                stopGame(true);
                return;
            }

            // 2. Feedback Visivo (colore)
            float maxRange = MAX_LUX;
            float proximityFactor = Math.min(1.0f, diff / maxRange);

            // Passaggio da Rosso (Lontano) a Giallo (Vicino)
            float hue = 60 * (1 - proximityFactor);
            int color = Color.HSVToColor(new float[]{hue, 1f, 0.7f});

            rootLayout.setBackgroundColor(color);
            if (diff < 50) {
                feedbackTextView.setText("Sei quasi lì! Molto vicino all'obiettivo!");
            } else if (diff < 200) {
                feedbackTextView.setText("Avanti! Sei nella zona calda!");
            } else {
                // Messaggio di base
                feedbackTextView.setText("Cambiando l'illuminazione esterna per raggiungere il target...");
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };


    // --- Gestione Ciclo di Vita (Cruciale per i sensori) ---
    @Override
    protected void onResume() {
        super.onResume();
        if (lightSensor != null) {
            sensorManager.registerListener(lightListener, lightSensor, SensorManager.SENSOR_DELAY_FASTEST);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (lightSensor != null) {
            sensorManager.unregisterListener(lightListener);
        }
        if (isGameActive) {
            stopGame(false);
            feedbackTextView.setText("Gioco in pausa.");
        }
        timerHandler.removeCallbacks(timerRunnable);
    }
}