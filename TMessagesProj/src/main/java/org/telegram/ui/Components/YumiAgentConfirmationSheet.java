package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

/** Consistent Telegram-themed confirmation surface for AI-initiated actions. */
public final class YumiAgentConfirmationSheet {
    private YumiAgentConfirmationSheet() {}

    public static void show(Context context, String title, String message, String cancelLabel, String confirmLabel,
                            boolean destructive, Runnable onCancel, Runnable onConfirm) {
        if (context == null) return;
        int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        int hintColor = Theme.getColor(Theme.key_windowBackgroundWhiteHintText);
        int surfaceColor = Theme.getColor(Theme.key_windowBackgroundWhite);
        int accentColor = Theme.getColor(destructive ? Theme.key_color_red : Theme.key_chat_messagePanelSend);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(4), AndroidUtilities.dp(20), AndroidUtilities.dp(20));

        TextView description = new TextView(context);
        description.setText(message == null ? "" : message);
        description.setTextSize(15);
        description.setTextColor(textColor);
        description.setLineSpacing(AndroidUtilities.dp(3), 1f);
        description.setGravity(Gravity.START);
        description.setPadding(0, 0, AndroidUtilities.dp(4), AndroidUtilities.dp(18));
        ScrollView messageScroll = new ScrollView(context);
        messageScroll.setFillViewport(false);
        messageScroll.setClipToPadding(false);
        messageScroll.addView(description, new ScrollView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        int maxMessageHeight = Math.min(AndroidUtilities.dp(320), (int) (context.getResources().getDisplayMetrics().heightPixels * .38f));
        content.addView(messageScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, maxMessageHeight));

        TextView confirm = action(context, confirmLabel, 0xffffffff, accentColor);
        content.addView(confirm, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 0, 0, 8));
        TextView cancel = action(context, cancelLabel, textColor, surfaceColor);
        cancel.setTextColor(hintColor);
        content.addView(cancel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 46));

        BottomSheet sheet = new BottomSheet.Builder(context, false)
                .setTitle(title, true)
                .setCustomView(content)
                .create();
        final boolean[] handled = {false};
        sheet.setOnDismissListener(d -> {
            if (!handled[0] && onCancel != null) onCancel.run();
        });
        confirm.setOnClickListener(v -> {
            handled[0] = true;
            sheet.dismiss();
            if (onConfirm != null) onConfirm.run();
        });
        cancel.setOnClickListener(v -> {
            handled[0] = true;
            sheet.dismiss();
            if (onCancel != null) onCancel.run();
        });
        sheet.show();
    }

    private static TextView action(Context context, String label, int textColor, int backgroundColor) {
        TextView view = new TextView(context);
        view.setText(label == null ? "Продолжить" : label);
        view.setTextColor(textColor);
        view.setTextSize(15);
        view.setGravity(Gravity.CENTER);
        GradientDrawable background = new GradientDrawable();
        background.setColor(backgroundColor);
        background.setCornerRadius(AndroidUtilities.dp(16));
        view.setBackground(background);
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }
}
