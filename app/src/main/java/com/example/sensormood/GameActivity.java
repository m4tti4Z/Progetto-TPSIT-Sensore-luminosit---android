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

    // --- UI Componenti ---
    private TextView targetLuxTextView;
    private TextView currentLuxTextView;
    private TextView timerTextView;
    private TextView feedbackTextView;
    private Button startButton;
    private LinearLayout rootLayout;

    // --- Variabili di Gioco ---
    private int targetLux;
    private final int MIN_LUX = 50;
    private final int MAX_LUX = 1000;
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

    // --- Sensor Listener ---
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
            float hue = 60 * (1 - proximityFactor);
            int color = Color.HSVToColor(new float[]{hue, 1f, 0.7f});
            rootLayout.setBackgroundColor(color);

            // 3. Feedback testuale
            if (diff < 50) {
                feedbackTextView.setText("Sei quasi lì! Molto vicino all'obiettivo!");
            } else if (diff < 200) {
                feedbackTextView.setText("Avanti! Sei nella zona calda!");
            } else {
                feedbackTextView.setText("Cambiando l'illuminazione esterna per raggiungere il target...");
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    // --- Ciclo di vita ---
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        initViews();
        setupSensor();
        setupStartButton();
        targetLuxTextView.setText("Target: -- lux");
    }

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

    // --- Inizializzazioni ---
    private void initViews() {
        targetLuxTextView = findViewById(R.id.targetLuxTextView);
        currentLuxTextView = findViewById(R.id.currentLuxTextView);
        timerTextView = findViewById(R.id.timerTextView);
        feedbackTextView = findViewById(R.id.feedbackTextView);
        startButton = findViewById(R.id.startButton);
        rootLayout = findViewById(R.id.rootLayout);
    }

    private void setupSensor() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        if (lightSensor == null) {
            Toast.makeText(this, "Sensore di Luce non disponibile. Impossibile giocare.", Toast.LENGTH_LONG).show();
            startButton.setEnabled(false);
        }
    }

    private void setupStartButton() {
        startButton.setOnClickListener(v -> startGame());
    }

    // --- Logica Gioco ---
    private void startGame() {
        if (isGameActive) {
            stopGame(false);
        }

        Random random = new Random();
        targetLux = random.nextInt(MAX_LUX - MIN_LUX + 1) + MIN_LUX;

        startTime = System.currentTimeMillis();
        timerHandler.post(timerRunnable);

        isGameActive = true;
        targetLuxTextView.setText("TARGET: " + targetLux + " lux");
        feedbackTextView.setText("Avvicinati al valore Lux nell'ambiente! (Tolleranza ±" + (int)TOLERANCE + ")");
        startButton.setText("IN CORSO...");
        startButton.setEnabled(false);
        rootLayout.setBackgroundColor(Color.parseColor("#303030"));
    }

    private void stopGame(boolean win) {
        isGameActive = false;
        timerHandler.removeCallbacks(timerRunnable);
        startButton.setEnabled(true);
        startButton.setText("NUOVO ROUND");

        if (win) {
            long finalTimeMillis = System.currentTimeMillis() - startTime;
            float finalSeconds = finalTimeMillis / 1000.0f;
            Toast.makeText(this, "VITTORIA! Tempo: " + String.format("%.2fs", finalSeconds), Toast.LENGTH_LONG).show();
            feedbackTextView.setText("🏆 VITTORIA! Tempo finale: " + String.format("%.2fs", finalSeconds) + "\nProva a battere il tuo record!");
            rootLayout.setBackgroundColor(Color.parseColor("#4CAF50"));
        } else {
            rootLayout.setBackgroundColor(Color.parseColor("#303030"));
            feedbackTextView.setText("Round interrotto. Premi START per iniziare!");
        }
    }
}
