package org.telegram.messenger.yumigram.player;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.palette.graphics.Palette;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.audioinfo.AudioInfo;
import org.telegram.messenger.yumigram.YumiConfig;
import org.telegram.ui.Components.AudioPlayerAlert;
import org.telegram.ui.Components.PlayPauseDrawable;
import org.telegram.ui.Components.SeekBarView;

/**
 * Spotify Player Styler for Yumigram (Юмиграм).
 * Applies Spotify's aesthetic:
 * 1. Prominent Spotify Green (#1DB954) 54x54 circular Play/Pause button with black icon
 * 2. Spotify Green seekbar progress indicator with translucent white track
 * 3. Rounded album artwork container (12dp radius)
 * 4. Dynamic album gradient background or dark Spotify palette (#121212)
 */
public class YumiSpotifyPlayer {

    public static final int SPOTIFY_GREEN = 0xFF1DB954;
    public static final int SPOTIFY_DARK_BG = 0xFF121212;
    public static final int SPOTIFY_ELEVATED_BG = 0xFF181818;

    public static void applySpotifyStyle(
            AudioPlayerAlert alert,
            View containerView,
            FrameLayout playerLayout,
            View coverContainer,
            ImageView playButton,
            SeekBarView seekBarView,
            MessageObject messageObject
    ) {
        if (!YumiConfig.isSpotifyPlayerEnabled()) {
            return;
        }

        try {
            // 1. Style Play/Pause button as iconic Spotify circular button
            if (playButton != null) {
                ViewGroup.LayoutParams lp = playButton.getLayoutParams();
                if (lp != null && (lp.width != dp(54) || lp.height != dp(54))) {
                    lp.width = dp(54);
                    lp.height = dp(54);
                    playButton.setLayoutParams(lp);
                }

                GradientDrawable playBg = new GradientDrawable();
                playBg.setShape(GradientDrawable.OVAL);
                playBg.setColor(SPOTIFY_GREEN);

                RippleDrawable ripple = new RippleDrawable(
                        ColorStateList.valueOf(0x33FFFFFF),
                        playBg,
                        null
                );
                playButton.setBackground(ripple);
                playButton.setElevation(dp(4));

                if (playButton.getDrawable() instanceof PlayPauseDrawable) {
                    ((PlayPauseDrawable) playButton.getDrawable()).setColor(Color.BLACK);
                } else {
                    playButton.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
                }
            }

            // 2. Style SeekBar with Spotify Green
            if (seekBarView != null) {
                seekBarView.setColors(0x33FFFFFF, SPOTIFY_GREEN);
            }

            // 3. Dynamic background gradient from album artwork
            int topColor = SPOTIFY_ELEVATED_BG;
            if (YumiConfig.isSpotifyDynamicColorEnabled()) {
                AudioInfo audioInfo = MediaController.getInstance().getAudioInfo();
                if (audioInfo != null && audioInfo.getCover() != null) {
                    Bitmap coverBitmap = audioInfo.getCover();
                    topColor = extractDominantColor(coverBitmap);
                }
            }

            // Apply Spotify smooth vertical gradient to playerLayout & container
            GradientDrawable backgroundGradient = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] { topColor, SPOTIFY_DARK_BG }
            );
            backgroundGradient.setCornerRadii(new float[] {
                    dp(16), dp(16),
                    dp(16), dp(16),
                    0, 0,
                    0, 0
            });

            if (playerLayout != null) {
                playerLayout.setBackground(backgroundGradient);
            }
            if (containerView != null) {
                containerView.setBackground(backgroundGradient);
            }

            // 4. Style cover artwork container
            if (coverContainer != null) {
                coverContainer.setElevation(dp(6));
            }

        } catch (Throwable e) {
            // Safe fallback
        }
    }

    private static int extractDominantColor(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return SPOTIFY_ELEVATED_BG;
        }
        try {
            Palette palette = Palette.from(bitmap).maximumColorCount(12).generate();
            Palette.Swatch darkVibrant = palette.getDarkVibrantSwatch();
            if (darkVibrant != null) {
                return darkVibrant.getRgb();
            }
            Palette.Swatch muted = palette.getDarkMutedSwatch();
            if (muted != null) {
                return muted.getRgb();
            }
            Palette.Swatch dominant = palette.getDominantSwatch();
            if (dominant != null) {
                int rgb = dominant.getRgb();
                float[] hsv = new float[3];
                Color.colorToHSV(rgb, hsv);
                hsv[2] = Math.min(hsv[2], 0.28f);
                return Color.HSVToColor(hsv);
            }
        } catch (Throwable ignored) {
        }
        return SPOTIFY_ELEVATED_BG;
    }
}
