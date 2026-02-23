package com.example.tonbo_app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FindItemsActivity extends BaseAccessibleActivity {
    private PreviewView cameraPreview;
    private TextView statusText;
    private ExecutorService cameraExecutor;

    // 谷歌原生語音識別器
    private SpeechRecognizer speechRecognizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find_items);
        cameraExecutor = Executors.newSingleThreadExecutor();
        initViews();
        initGoogleSpeechRecognizer(); // 初始化谷歌語音
        checkPermissions();
    }

    private void initViews() {
        cameraPreview = findViewById(R.id.camera_preview);
        statusText = findViewById(R.id.status_text);
        findViewById(R.id.capture_button).setOnClickListener(v -> startRecordingFlow());
    }

    // 初始化谷歌原生語音識別
    private void initGoogleSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {
                    statusText.setText("請說話...");
                }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {
                    statusText.setText("正在識別...");
                }
                @Override public void onError(int error) {
                    statusText.setText("識別失敗 (錯誤碼: " + error + ")");
                    ttsManager.speak("識別失敗，請檢查網絡或重試", "Recognition Failed", true);
                }
                @Override public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String text = matches.get(0);
                        statusText.setText("識別結果: " + text);
                        ttsManager.speak("識別到 " + text, "Recognized " + text, true);
                    }
                }
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });
        } else {
            statusText.setText("當前設備不支持谷歌原生語音識別");
        }
    }

    // 相機初始化
    private void setupCamera() {
        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(() -> {
            try {
                ProcessCameraProvider provider = providerFuture.get();
                Preview preview = new Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_16_9).build();
                preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());
                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview);
            } catch (Exception e) {
                Log.e("FindItems", "Camera Binding Failed: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // 觸發錄音流程
    private void startRecordingFlow() {
        if (speechRecognizer == null) {
            ttsManager.speak("設備不支持語音識別", "Speech recognition not supported", true);
            return;
        }

        // 依然保留了修復好的 TTS 監聽：講完話後再啟動谷歌傾聽
        ttsManager.speak("請在嘀聲後說出名稱", "Say the name after beep", true, () -> {
            vibrationManager.vibrateClick();

            // 必須在主線程啟動谷歌語音識別
            runOnUiThread(() -> {
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                // 默認使用繁體中文(香港)，可以根據需要改為 zh-CN 或 en-US
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-HK");
                intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
                speechRecognizer.startListening(intent);
            });
        });
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 101);
        } else {
            setupCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupCamera();
        }
    }

    @Override protected void announcePageTitle() { ttsManager.speak("物品助手", "Object Assistant", true); }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        cameraExecutor.shutdown();
    }
}