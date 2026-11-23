package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.yumigram.util.YumiLocale;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import java.util.List;

/**
 * 3D-style Wheel / Carousel Picker BottomSheet.
 * The selected item is prominent, bold, and centered.
 * Items above and below scale down and fade with distance.
 */
public class YumiWheelPickerDialog extends BottomSheet {

    public interface OnItemSelectedListener {
        void onSelected(int index, String value);
    }

    private final List<String> items;
    private int selectedIndex;
    private final OnItemSelectedListener listener;

    private RecyclerView recyclerView;
    private WheelAdapter adapter;
    private LinearLayoutManager layoutManager;

    public YumiWheelPickerDialog(
            Context context,
            String title,
            List<String> items,
            int initialIndex,
            OnItemSelectedListener listener
    ) {
        super(context, false);
        this.items = items;
        this.selectedIndex = Math.max(0, Math.min(initialIndex, items.size() - 1));
        this.listener = listener;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(12), 0, dp(16));
        root.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));

        // Drag pill handle
        View pill = new View(context);
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setCornerRadius(dp(3));
        pillBg.setColor(Theme.getColor(Theme.key_sheet_scrollUp));
        pill.setBackground(pillBg);
        root.addView(pill, LayoutHelper.createLinear(36, 4, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 12));

        // Header Title
        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        titleView.setGravity(Gravity.CENTER);
        root.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 0, 16, 16));

        // Wheel Container with center highlight line
        FrameLayout wheelContainer = new FrameLayout(context);

        // Center selection bar
        View selectionBar = new View(context);
        GradientDrawable barBg = new GradientDrawable();
        barBg.setCornerRadius(dp(12));
        barBg.setColor(Theme.getColor(Theme.key_featuredStickers_addButton) & 0x1FFFFFFF);
        barBg.setStroke(dp(1), Theme.getColor(Theme.key_featuredStickers_addButton));
        selectionBar.setBackground(barBg);
        wheelContainer.addView(selectionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 46, Gravity.CENTER, 20, 0, 20, 0));

        // Recycler List
        recyclerView = new RecyclerView(context);
        layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setClipToPadding(false);
        recyclerView.setPadding(0, dp(64), 0, dp(64));

        LinearSnapHelper snapHelper = new LinearSnapHelper();
        snapHelper.attachToRecyclerView(recyclerView);

        adapter = new WheelAdapter();
        recyclerView.setAdapter(adapter);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                updateVisualScales();
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    View centerView = snapHelper.findSnapView(layoutManager);
                    if (centerView != null) {
                        int pos = layoutManager.getPosition(centerView);
                        if (pos >= 0 && pos < items.size()) {
                            selectedIndex = pos;
                        }
                    }
                    updateVisualScales();
                }
            }
        });

        wheelContainer.addView(recyclerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 174));
        root.addView(wheelContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 174, 0, 0, 0, 16));

        // Confirm Button
        TextView confirmButton = new TextView(context);
        confirmButton.setText(YumiLocale.getString(R.string.OK));
        confirmButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        confirmButton.setTypeface(AndroidUtilities.bold());
        confirmButton.setTextColor(Color.WHITE);
        confirmButton.setGravity(Gravity.CENTER);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setCornerRadius(dp(10));
        btnBg.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        confirmButton.setBackground(btnBg);
        confirmButton.setPadding(0, dp(12), 0, dp(12));

        confirmButton.setOnClickListener(v -> {
            if (listener != null && selectedIndex >= 0 && selectedIndex < items.size()) {
                listener.onSelected(selectedIndex, items.get(selectedIndex));
            }
            dismiss();
        });

        root.addView(confirmButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 0, 20, 8));

        setCustomView(root);

        recyclerView.post(() -> {
            if (selectedIndex >= 0 && selectedIndex < items.size()) {
                recyclerView.scrollToPosition(selectedIndex);
                recyclerView.post(this::updateVisualScales);
            }
        });
    }

    private void updateVisualScales() {
        int midY = recyclerView.getHeight() / 2;
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            int childMidY = (child.getTop() + child.getBottom()) / 2;
            int dist = Math.abs(midY - childMidY);
            float maxDist = dp(75);
            float factor = Math.max(0.0f, Math.min(1.0f, dist / maxDist));

            float scale = 1.0f - (factor * 0.18f); // 1.0 -> 0.82
            float alpha = 1.0f - (factor * 0.55f); // 1.0 -> 0.45

            child.setScaleX(scale);
            child.setScaleY(scale);
            child.setAlpha(alpha);
        }
    }

    private class WheelAdapter extends RecyclerView.Adapter<WheelAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout frame = new FrameLayout(parent.getContext());
            frame.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, dp(46)));

            TextView text = new TextView(parent.getContext());
            text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            text.setGravity(Gravity.CENTER);
            text.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            text.setTypeface(AndroidUtilities.bold());
            frame.addView(text, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            return new ViewHolder(frame, text);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.textView.setText(items.get(position));
            holder.itemView.setOnClickListener(v -> {
                selectedIndex = position;
                recyclerView.smoothScrollToPosition(position);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView textView;

            ViewHolder(View itemView, TextView textView) {
                super(itemView);
                this.textView = textView;
            }
        }
    }
}
