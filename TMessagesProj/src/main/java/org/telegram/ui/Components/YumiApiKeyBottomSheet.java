package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.yumigram.YumiConfig;
import org.telegram.messenger.yumigram.util.YumiLocale;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

/**
 * Modern, beautiful Gemini API Key input BottomSheet.
 * Includes direct Paste from clipboard, Cancel, Save buttons,
 * and a direct link to Google AI Studio.
 */
public class YumiApiKeyBottomSheet extends BottomSheet {

    public interface OnKeySavedListener {
        void onKeySaved(String key);
    }

    private final EditText inputField;

    public YumiApiKeyBottomSheet(Context context, String currentKey, OnKeySavedListener listener) {
        super(context, true);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(20));
        root.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));

        // Top drag pill
        View pill = new View(context);
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setCornerRadius(dp(3));
        pillBg.setColor(Theme.getColor(Theme.key_sheet_scrollUp));
        pill.setBackground(pillBg);
        root.addView(pill, LayoutHelper.createLinear(36, 4, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 14));

        // Title
        TextView titleView = new TextView(context);
        titleView.setText("🔑 Google Gemini API Key");
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        titleView.setGravity(Gravity.CENTER);
        root.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        // Subtitle
        TextView descView = new TextView(context);
        descView.setText("Enter your Gemini API key to activate Cloud models (3.5 / 3.1 Flash Lite).");
        descView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        descView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        descView.setGravity(Gravity.CENTER);
        root.addView(descView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // Input container with Paste button
        LinearLayout inputContainer = new LinearLayout(context);
        inputContainer.setOrientation(LinearLayout.HORIZONTAL);
        inputContainer.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setCornerRadius(dp(12));
        inputBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        inputBg.setStroke(dp(1), Theme.getColor(Theme.key_divider));
        inputContainer.setBackground(inputBg);
        inputContainer.setPadding(dp(14), dp(4), dp(6), dp(4));

        inputField = new EditText(context);
        inputField.setHint("AIzaSy...");
        inputField.setText(currentKey != null ? currentKey : "");
        inputField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        inputField.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        inputField.setHintTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        inputField.setBackground(null);
        inputField.setSingleLine(true);
        inputContainer.addView(inputField, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        // Paste button
        TextView pasteButton = new TextView(context);
        pasteButton.setText("📋 Paste");
        pasteButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        pasteButton.setTypeface(AndroidUtilities.bold());
        pasteButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        pasteButton.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable pasteBg = new GradientDrawable();
        pasteBg.setCornerRadius(dp(8));
        pasteBg.setColor(Theme.getColor(Theme.key_featuredStickers_addButton) & 0x1FFFFFFF);
        pasteButton.setBackground(pasteBg);
        pasteButton.setOnClickListener(v -> {
            try {
                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
                    CharSequence clip = cm.getPrimaryClip().getItemAt(0).getText();
                    if (!TextUtils.isEmpty(clip)) {
                        inputField.setText(clip.toString().trim());
                        inputField.setSelection(inputField.getText().length());
                    }
                }
            } catch (Exception ignored) {}
        });
        inputContainer.addView(pasteButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        root.addView(inputContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // Direct link to get API Key
        TextView getKeyLink = new TextView(context);
        getKeyLink.setText("🔗 Get free key at Google AI Studio (aistudio.google.com)");
        getKeyLink.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        getKeyLink.setTextColor(Theme.getColor(Theme.key_chat_messageLinkIn));
        getKeyLink.setGravity(Gravity.CENTER);
        getKeyLink.setPadding(0, dp(4), 0, dp(16));
        getKeyLink.setOnClickListener(v -> {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"));
                context.startActivity(browserIntent);
            } catch (Exception ignored) {}
        });
        root.addView(getKeyLink, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Bottom action buttons: Cancel and Save
        LinearLayout buttonsRow = new LinearLayout(context);
        buttonsRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView cancelBtn = new TextView(context);
        cancelBtn.setText(YumiLocale.getString(R.string.Cancel));
        cancelBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        cancelBtn.setTypeface(AndroidUtilities.bold());
        cancelBtn.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        cancelBtn.setGravity(Gravity.CENTER);
        GradientDrawable cancelBg = new GradientDrawable();
        cancelBg.setCornerRadius(dp(10));
        cancelBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        cancelBtn.setBackground(cancelBg);
        cancelBtn.setPadding(0, dp(12), 0, dp(12));
        cancelBtn.setOnClickListener(v -> dismiss());
        buttonsRow.addView(cancelBtn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 0, 0, 8, 0));

        TextView saveBtn = new TextView(context);
        saveBtn.setText(YumiLocale.getString(R.string.Save));
        saveBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        saveBtn.setTypeface(AndroidUtilities.bold());
        saveBtn.setTextColor(Color.WHITE);
        saveBtn.setGravity(Gravity.CENTER);
        GradientDrawable saveBg = new GradientDrawable();
        saveBg.setCornerRadius(dp(10));
        saveBg.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        saveBtn.setBackground(saveBg);
        saveBtn.setPadding(0, dp(12), 0, dp(12));
        saveBtn.setOnClickListener(v -> {
            String key = inputField.getText().toString().trim();
            YumiConfig.setAiApiKey(key);
            if (listener != null) {
                listener.onKeySaved(key);
            }
            dismiss();
        });
        buttonsRow.addView(saveBtn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 8, 0, 0, 0));

        root.addView(buttonsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        setCustomView(root);
    }
}
