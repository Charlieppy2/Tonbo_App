package com.example.tonbo_app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TTSManager {
    private static final String TAG = "TTSManager";
    private static TTSManager instance;

    private TextToSpeech textToSpeech;
    private Context context;
    private String currentLanguage = "english";
    private boolean isInitialized = false;
    private boolean isInitializing = false;
    private boolean isSpeaking = false;

    public interface OnSpeechFinishedListener { void onFinished(); }
    private OnSpeechFinishedListener mListener;

    private ConcurrentLinkedQueue<String> speechQueue = new ConcurrentLinkedQueue<>();
    private Handler handler = new Handler(Looper.getMainLooper());

    private TTSManager(Context context) {
        this.context = context.getApplicationContext();
        initTTS();
    }

    public static synchronized TTSManager getInstance(Context context) {
        if (instance == null) instance = new TTSManager(context);
        return instance;
    }

    private void initTTS() {
        if (isInitializing) return;
        isInitializing = true;
        textToSpeech = new TextToSpeech(context, status -> {
            isInitializing = false;
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true;
                textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) { isSpeaking = true; }
                    @Override public void onDone(String utteranceId) {
                        isSpeaking = false;
                        if (mListener != null) {
                            new Handler(Looper.getMainLooper()).post(() -> mListener.onFinished());
                        }
                    }
                    @Override public void onError(String utteranceId) { isSpeaking = false; }
                });
                setLanguage(currentLanguage);
            }
        });
    }

    // 核心 speak 方法，支持监听器回调以修复录音问题
    public void speak(String cantonese, String english, boolean priority, OnSpeechFinishedListener listener) {
        this.mListener = listener;
        String textToSpeak = "english".equals(currentLanguage) ? (english != null ? english : cantonese) : cantonese;
        if (textToSpeak == null || textToSpeak.trim().isEmpty()) return;

        if (textToSpeech != null && isInitialized) {
            int mode = priority ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
            textToSpeech.speak(textToSpeak, mode, null, "TTS_TASK_ID");
        } else {
            handler.postDelayed(() -> speak(cantonese, english, priority, listener), 1000);
        }
    }

    // 补全队友使用的所有方法
    public void speak(String cantonese, String english) { speak(cantonese, english, false, null); }
    public void speak(String cantonese, String english, boolean priority) { speak(cantonese, english, priority, null); }
    public String getCurrentLanguage() { return currentLanguage; }
    public void stopSpeaking() { if (textToSpeech != null) textToSpeech.stop(); speechQueue.clear(); isSpeaking = false; }
    public void stop() { if (textToSpeech != null) textToSpeech.stop(); }
    public void setSpeechRate(float rate) { if (textToSpeech != null) textToSpeech.setSpeechRate(rate); }
    public void setSpeechPitch(float pitch) { if (textToSpeech != null) textToSpeech.setPitch(pitch); }
    public void setSpeechVolume(float volume) { Log.d(TAG, "Volume: " + volume); }
    public void speakPageTitle(String name) { speak("當前頁面：" + name, "Current page: " + name, true); }
    public void speakSuccess(String msg) { speak("成功：" + msg, "Success: " + msg, true); }
    public void speakError(String err) { speak("錯誤：" + err, "Error: " + err, true); }
    public void speakNavigationHint(String hint) { speak("提示：" + hint, "Hint: " + hint, false); }
    public void changeLanguage(String lang) { this.currentLanguage = lang; setLanguage(lang); }
    public void setLanguageSilently(String lang) { this.currentLanguage = lang; setLanguage(lang); }

    private void setLanguage(String lang) {
        if (textToSpeech == null) return;
        if ("english".equals(lang)) textToSpeech.setLanguage(Locale.ENGLISH);
        else if ("mandarin".equals(lang)) textToSpeech.setLanguage(Locale.SIMPLIFIED_CHINESE);
        else textToSpeech.setLanguage(new Locale("zh", "HK"));
    }

    public void forceInitialize() { if (textToSpeech == null) initTTS(); }
    public boolean isSpeaking() { return isSpeaking || (textToSpeech != null && textToSpeech.isSpeaking()); }
    public void pauseSpeaking() { if (textToSpeech != null) textToSpeech.stop(); }
    public void shutdown() { if (textToSpeech != null) textToSpeech.stop(); }
}