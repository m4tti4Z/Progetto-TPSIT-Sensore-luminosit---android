package com.example.sensormood; // CAMBIA con il tuo nome package

import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class GameFragment extends Fragment implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor lightSensor;

    private TextView gameStatusTextView;
    private TextView timerTextView;

    private static final float THRESHOLD_LUX = 200f; // Soglia di Lux per attivare il GO

    // Stati del gioco
    private enum GameState { WAITING, READY, REACTION }
    private GameState currentState = GameState.WAITING;

    private long startTime;

    // --- Ciclo di Vita del Fragment ---

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_game, container, false);

        gameStatusTextView = view.findViewById(R.id.gameStatusTextView);
        timerTextView = view.findViewById(R.id.timerTextView);

        // Inizializzazione sensore
        if (getActivity() != null) {
            sensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        }

        // Gestore del click sul box dello stato
        gameStatusTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleTap();
            }
        });

        resetGame();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Registra il listener del sensore
        if (lightSensor != null && sensorManager != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Deregistra il listener quando il Fragment non è visibile
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    // --- Logica del Sensore di Luce ---

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            float currentLux = event.values[0];

            if (currentState == GameState.WAITING) {
                // Se la luce supera la soglia, il gioco inizia!
                if (currentLux > THRESHOLD_LUX) {
                    setGameState(GameState.READY);
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Non necessario
    }

    // --- Logica del Gioco ---

    private void resetGame() {
        setGameState(GameState.WAITING);
        timerTextView.setText("Tempo di reazione: -- ms");
    }

    private void setGameState(GameState state) {
        currentState = state;

        switch (state) {
            case WAITING:
                gameStatusTextView.setText("ATTENDI LUCE");
                gameStatusTextView.setBackgroundColor(Color.parseColor("#CCCCCC"));
                break;
            case READY:
                gameStatusTextView.setText("ORA!");
                gameStatusTextView.setBackgroundColor(Color.RED);
                startTime = System.currentTimeMillis();
                break;
            case REACTION:
                gameStatusTextView.setText("TOCCA PER INIZIARE");
                gameStatusTextView.setBackgroundColor(Color.GREEN);
                break;
        }
    }

    private void handleTap() {
        if (currentState == GameState.READY) {
            // Tocco corretto: calcola il tempo di reazione
            long endTime = System.currentTimeMillis();
            long reactionTime = endTime - startTime;

            timerTextView.setText("Reazione: " + reactionTime + " ms!");
            Toast.makeText(getContext(), "Bravo! Tempo: " + reactionTime + "ms", Toast.LENGTH_LONG).show();

            setGameState(GameState.REACTION); // Passa allo stato di risultato/pronto

        } else if (currentState == GameState.WAITING) {
            // Tocco troppo presto
            Toast.makeText(getContext(), "Non toccare prima del GO!", Toast.LENGTH_SHORT).show();
            resetGame();

        } else if (currentState == GameState.REACTION) {
            // Tocco per ricominciare
            resetGame();
        }
    }
}