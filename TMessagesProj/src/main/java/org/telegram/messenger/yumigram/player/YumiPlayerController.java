package org.telegram.messenger.yumigram.player;

import android.media.audiofx.Equalizer;
import android.os.Handler;
import android.os.Looper;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.yumigram.YumiConfig;

/**
 * Controller for Yumigram (Юмиграм) enhanced media player features.
 */
public class YumiPlayerController {

    private static final String TAG = "YumiPlayerController";
    private static Equalizer equalizer;
    private static int lastAudioSessionId = 0;

    private static final Handler sleepTimerHandler = new Handler(Looper.getMainLooper());
    private static Runnable sleepTimerRunnable;

    /**
     * Attaches or updates the audio equalizer for the current audio session.
     */
    public static void attachEqualizer(int audioSessionId) {
        if (!YumiConfig.isEqualizerEnabled() || audioSessionId <= 0) {
            releaseEqualizer();
            return;
        }

        if (equalizer != null && lastAudioSessionId == audioSessionId) {
            return;
        }

        releaseEqualizer();
        try {
            lastAudioSessionId = audioSessionId;
            equalizer = new Equalizer(0, audioSessionId);
            equalizer.setEnabled(true);
            FileLog.d(TAG + ": Equalizer attached to session " + audioSessionId);
        } catch (Exception e) {
            FileLog.e(TAG, e);
        }
    }

    /**
     * Releases active equalizer instance.
     */
    public static void releaseEqualizer() {
        if (equalizer != null) {
            try {
                equalizer.release();
            } catch (Exception e) {
                FileLog.e(TAG, e);
            }
            equalizer = null;
            lastAudioSessionId = 0;
        }
    }

    /**
     * Starts a sleep timer to pause playback after specified minutes.
     */
    public static void startSleepTimer(int minutes) {
        cancelSleepTimer();
        if (minutes <= 0) return;

        sleepTimerRunnable = () -> {
            MediaController.getInstance().pauseMessage(MediaController.getInstance().getPlayingMessageObject());
            FileLog.d(TAG + ": Sleep timer triggered, paused playback.");
            sleepTimerRunnable = null;
        };

        sleepTimerHandler.postDelayed(sleepTimerRunnable, minutes * 60 * 1000L);
    }

    /**
     * Cancels active sleep timer.
     */
    public static void cancelSleepTimer() {
        if (sleepTimerRunnable != null) {
            sleepTimerHandler.removeCallbacks(sleepTimerRunnable);
            sleepTimerRunnable = null;
        }
    }

    /**
     * Checks if sleep timer is running.
     */
    public static boolean isSleepTimerActive() {
        return sleepTimerRunnable != null;
    }
}
