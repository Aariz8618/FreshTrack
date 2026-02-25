package com.aariz.freshtrack;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CookingModeActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    // Constants
    private static final int RECORD_AUDIO_PERMISSION_CODE = 101;
    private static final int SPEECH_REQUEST_CODE          = 100;

    // Data
    private ArrayList<String> instructions;
    private ArrayList<String> times;
    private int currentStep = 0;

    // Views
    private TextView    stepText;
    private TextView    stepTime;
    private TextView    timerText;
    private TextView    timerStatus;
    private TextView    progressText;
    private ProgressBar progressBar;
    private LinearLayout nextButton;
    private LinearLayout prevButton;
    private LinearLayout pauseButton;
    private LinearLayout resetButton;
    private MaterialButton voiceButton;
    private MaterialButton exitButton;
    private ImageView   pauseIcon;
    private CardView    cardTimer;
    private CardView    voiceIndicatorCard;
    private TextView    voiceIndicatorText;

    // Timer
    private CountDownTimer countDownTimer    = null;
    private long           timeLeftInMillis  = 0L;
    private boolean        isTimerRunning    = false;
    private boolean        isTimerPaused     = false;

    // Voice / TTS
    private TextToSpeech textToSpeech       = null;
    private boolean      isTtsReady         = false;
    private boolean      isVoiceEnabled     = false;
    private boolean      isListening        = false;
    private boolean      audioPermissionGranted = false;

    // ------------------------------------------------------------------ //
    //  Lifecycle
    // ------------------------------------------------------------------ //

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_cooking_mode);

        checkAudioPermission();

        // Keep screen awake during cooking
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsExtensions.applyHeaderInsets(findViewById(R.id.header_section));
        WindowInsetsExtensions.applyBottomNavInsets(findViewById(R.id.bottom_bar));

        initializeViews();
        loadData();
        setupButtons();
        initializeTextToSpeech();

        showStep();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Pause timer when app goes to background
        if (isTimerRunning && !isTimerPaused) {
            toggleTimer();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) countDownTimer.cancel();
        isVoiceEnabled = false;
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // ------------------------------------------------------------------ //
    //  Init
    // ------------------------------------------------------------------ //

    private void initializeViews() {
        stepText           = findViewById(R.id.text_step);
        stepTime           = findViewById(R.id.text_step_time);
        timerText          = findViewById(R.id.text_timer);
        timerStatus        = findViewById(R.id.text_timer_status);
        progressText       = findViewById(R.id.text_progress);
        progressBar        = findViewById(R.id.progress_bar);
        nextButton         = findViewById(R.id.button_next_step);
        prevButton         = findViewById(R.id.button_prev_step);
        pauseButton        = findViewById(R.id.button_pause_timer);
        resetButton        = findViewById(R.id.button_reset_timer);
        voiceButton        = findViewById(R.id.button_voice);
        exitButton         = findViewById(R.id.button_exit);
        pauseIcon          = findViewById(R.id.icon_pause_timer);
        cardTimer          = findViewById(R.id.card_timer);
        voiceIndicatorCard = findViewById(R.id.card_voice_indicator);
        voiceIndicatorText = findViewById(R.id.text_voice_indicator);
    }

    private void loadData() {
        instructions = getIntent().getStringArrayListExtra("instructions");
        times        = getIntent().getStringArrayListExtra("times");
        if (instructions == null) instructions = new ArrayList<>();
        if (times == null)        times        = new ArrayList<>();

        if (instructions.isEmpty()) {
            Toast.makeText(this, "No instructions available", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    // ------------------------------------------------------------------ //
    //  Buttons
    // ------------------------------------------------------------------ //

    private void setupButtons() {
        nextButton.setOnClickListener(v -> {
            if (currentStep < instructions.size() - 1) {
                currentStep++;
                showStep();
                speakInstruction();
            } else {
                showCompletionDialog();
            }
        });

        prevButton.setOnClickListener(v -> {
            if (currentStep > 0) {
                currentStep--;
                showStep();
                speakInstruction();
            }
        });

        pauseButton.setOnClickListener(v -> toggleTimer());

        resetButton.setOnClickListener(v -> resetTimer());

        voiceButton.setOnClickListener(v -> {
            if (!audioPermissionGranted) {
                Toast.makeText(this, "Audio permission required", Toast.LENGTH_SHORT).show();
                return;
            }
            toggleVoiceMode();
        });

        exitButton.setOnClickListener(v -> showExitConfirmation());
    }

    // ------------------------------------------------------------------ //
    //  Steps
    // ------------------------------------------------------------------ //

    private void showStep() {
        if (countDownTimer != null) countDownTimer.cancel();
        isTimerRunning = false;
        isTimerPaused  = false;
        updatePauseIcon();

        stepText.setText(instructions.get(currentStep));

        String stepTimeText = (currentStep < times.size()) ? times.get(currentStep) : "N/A";
        stepTime.setText("Estimated: " + stepTimeText);

        int progress = (int) (((currentStep + 1f) / instructions.size()) * 100);
        progressBar.setProgress(progress);
        progressText.setText("Step " + (currentStep + 1) + " of " + instructions.size());

        prevButton.setAlpha(currentStep > 0 ? 1.0f : 0.5f);
        prevButton.setEnabled(currentStep > 0);

        String nextLabel = (currentStep == instructions.size() - 1) ? "Finish" : "Next";
        ((TextView) nextButton.getChildAt(0)).setText(nextLabel);

        setupStepTimer(stepTimeText);
    }

    // ------------------------------------------------------------------ //
    //  Timer
    // ------------------------------------------------------------------ //

    private void setupStepTimer(String stepTimeText) {
        String[] parts  = stepTimeText.split(" ");
        int      minutes = 0;
        if (parts.length > 0) {
            try { minutes = Integer.parseInt(parts[0]); } catch (NumberFormatException ignored) {}
        }

        if (minutes > 0) {
            timeLeftInMillis = (long) minutes * 60 * 1000;
            cardTimer.setVisibility(View.VISIBLE);
            timerStatus.setText("Ready to start");
            updateTimerDisplay();
            startTimer();
        } else {
            cardTimer.setVisibility(View.GONE);
        }
    }

    private void startTimer() {
        if (timeLeftInMillis <= 0 || isTimerRunning) return;

        countDownTimer = new CountDownTimer(timeLeftInMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftInMillis = millisUntilFinished;
                updateTimerDisplay();
            }

            @Override
            public void onFinish() {
                onTimerComplete();
            }
        }.start();

        isTimerRunning = true;
        isTimerPaused  = false;
        timerStatus.setText("Counting down...");
        updatePauseIcon();
    }

    private void toggleTimer() {
        if (isTimerRunning && !isTimerPaused) {
            if (countDownTimer != null) countDownTimer.cancel();
            isTimerRunning = false;
            isTimerPaused  = true;
            timerStatus.setText("Paused");
            updatePauseIcon();
        } else if (isTimerPaused) {
            startTimer();
            timerStatus.setText("Counting down...");
        }
    }

    private void resetTimer() {
        if (countDownTimer != null) countDownTimer.cancel();

        String stepTimeText = (currentStep < times.size()) ? times.get(currentStep) : "N/A";
        String[] parts      = stepTimeText.split(" ");
        int      minutes    = 0;
        if (parts.length > 0) {
            try { minutes = Integer.parseInt(parts[0]); } catch (NumberFormatException ignored) {}
        }

        timeLeftInMillis = (long) minutes * 60 * 1000;
        updateTimerDisplay();

        isTimerRunning = false;
        isTimerPaused  = false;
        timerStatus.setText("Reset - Ready to start");
        updatePauseIcon();

        // Auto-start after reset
        startTimer();
    }

    private void updateTimerDisplay() {
        long minutes = (timeLeftInMillis / 1000) / 60;
        long seconds = (timeLeftInMillis / 1000) % 60;
        timerText.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
    }

    private void updatePauseIcon() {
        if (isTimerRunning && !isTimerPaused) {
            pauseIcon.setImageResource(R.drawable.ic_pause);
        } else {
            pauseIcon.setImageResource(R.drawable.ic_play);
        }
    }

    private void onTimerComplete() {
        isTimerRunning = false;
        timerText.setText("00:00");
        timerStatus.setText("✔ Step Complete!");
        timerText.setTextColor(getColor(R.color.green_primary));
        speak("Timer complete. Step finished.");
    }

    // ------------------------------------------------------------------ //
    //  Text-to-Speech
    // ------------------------------------------------------------------ //

    private void initializeTextToSpeech() {
        textToSpeech = new TextToSpeech(this, this);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = textToSpeech.setLanguage(Locale.US);
            isTtsReady = (result != TextToSpeech.LANG_MISSING_DATA
                    && result != TextToSpeech.LANG_NOT_SUPPORTED);
            if (isTtsReady) {
                speakInstruction();
            }
        }
    }

    private void speakInstruction() {
        if (isTtsReady) {
            speak("Step " + (currentStep + 1) + ". " + instructions.get(currentStep));
        }
    }

    private void speak(String text) {
        if (textToSpeech != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    // ------------------------------------------------------------------ //
    //  Voice recognition
    // ------------------------------------------------------------------ //

    private void toggleVoiceMode() {
        if (isVoiceEnabled) {
            isVoiceEnabled = false;
            voiceButton.setAlpha(0.5f);
            voiceIndicatorCard.setVisibility(View.GONE);
            speak("Voice commands disabled");
            Toast.makeText(this, "Voice commands disabled", Toast.LENGTH_SHORT).show();
        } else {
            isVoiceEnabled = true;
            voiceButton.setAlpha(1.0f);
            speak("Voice commands enabled. Say next, previous, repeat, or pause");
            startListening();
        }
    }

    private void startListening() {
        if (!isVoiceEnabled || isListening) return;

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                "Say 'next', 'previous', 'repeat', or 'pause'");

        try {
            isListening = true;
            voiceIndicatorCard.setVisibility(View.VISIBLE);
            startActivityForResult(intent, SPEECH_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(this, "Voice recognition not available", Toast.LENGTH_SHORT).show();
            voiceIndicatorCard.setVisibility(View.GONE);
            isListening = false;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SPEECH_REQUEST_CODE) {
            voiceIndicatorCard.setVisibility(View.GONE);

            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> results =
                        data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (results != null && !results.isEmpty()) {
                    String spokenText = results.get(0).toLowerCase(Locale.getDefault());
                    handleVoiceCommand(spokenText);
                }
            }

            isListening = false;

            if (isVoiceEnabled) {
                startListening();
            }
        }
    }

    private void handleVoiceCommand(String command) {
        if (command.contains("next") || command.contains("continue")) {
            speak("Going to next step");
            nextButton.performClick();
        } else if (command.contains("previous") || command.contains("back")) {
            speak("Going to previous step");
            prevButton.performClick();
        } else if (command.contains("repeat") || command.contains("again")) {
            speak("Repeating step");
            speakInstruction();
        } else if (command.contains("pause") || command.contains("stop")) {
            speak("Pausing timer");
            if (isTimerRunning) {
                pauseButton.performClick();
            }
        } else if (command.contains("reset")) {
            speak("Resetting timer");
            resetButton.performClick();
        } else {
            speak("Command not recognized. Try saying next, previous, repeat, or pause");
        }
    }

    // ------------------------------------------------------------------ //
    //  Permissions
    // ------------------------------------------------------------------ //

    private void checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{ Manifest.permission.RECORD_AUDIO },
                    RECORD_AUDIO_PERMISSION_CODE
            );
        } else {
            audioPermissionGranted = true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                audioPermissionGranted = true;
                Toast.makeText(this,
                        "Voice commands available - tap mic icon to enable",
                        Toast.LENGTH_SHORT).show();
            } else {
                audioPermissionGranted = false;
                Toast.makeText(this,
                        "Voice commands disabled - permission denied",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  Dialogs
    // ------------------------------------------------------------------ //

    private void showCompletionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Recipe Complete! 🎉")
                .setMessage("Congratulations! You've finished cooking. Enjoy your meal!")
                .setPositiveButton("Done", (dialog, which) -> finish())
                .setNegativeButton("Review Steps", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    private void showExitConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Exit Cooking Mode?")
                .setMessage("Are you sure you want to exit? Your progress will not be saved.")
                .setPositiveButton("Yes, Exit", (dialog, which) -> finish())
                .setNegativeButton("Cancel",    (dialog, which) -> dialog.dismiss())
                .show();
    }
}