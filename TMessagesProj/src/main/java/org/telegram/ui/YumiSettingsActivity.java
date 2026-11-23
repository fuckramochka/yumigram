package org.telegram.ui;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.R;
import org.telegram.messenger.yumigram.update.YumiUpdateManager;
import org.telegram.messenger.yumigram.util.YumiLocale;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * Clean & Minimalist Yumigram Hub.
 * Displays only sleek, clear category buttons without cluttered subtitles:
 * 1. Ghost Mode & Privacy
 * 2. Yumi AI Agent
 * 3. Spotify Music Player & Interface
 * 4. Auto-Updates & Releases
 */
public class YumiSettingsActivity extends UniversalFragment {

    private static final int ID_MENU_GHOST = 1;
    private static final int ID_MENU_AI = 2;
    private static final int ID_MENU_INTERFACE = 3;
    private static final int ID_MENU_UPDATE = 4;

    @Override
    protected CharSequence getTitle() {
        return YumiLocale.getString(R.string.YumiSettings);
    }

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        fragmentView = super.createView(context);
        listView.setSections();
        listView.setSectionsDrawBackground(true);
        return fragmentView;
    }

    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        return listView;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(YumiLocale.getString(R.string.YumiSettings)));

        // 1. Ghost Mode & Privacy
        items.add(UItem.asButton(ID_MENU_GHOST, R.drawable.settings_privacy, YumiLocale.getString(R.string.YumiMenuGhost)));

        // 2. Yumi AI Agent
        items.add(UItem.asButton(ID_MENU_AI, R.drawable.msg_bot, YumiLocale.getString(R.string.YumiMenuAI)));

        // 3. Spotify Music Player & Interface
        items.add(UItem.asButton(ID_MENU_INTERFACE, R.drawable.player_new_repeatall, YumiLocale.getString(R.string.YumiMenuInterface)));

        // 4. Auto-Updates & Releases
        items.add(UItem.asButton(ID_MENU_UPDATE, R.drawable.msg_download_settings, YumiLocale.getString(R.string.YumiMenuUpdate)));

        items.add(UItem.asShadow("Yumigram " + YumiUpdateManager.CURRENT_VERSION + " (arm64-v8a)"));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_MENU_GHOST:
                presentFragment(new YumiGhostSettingsActivity());
                break;
            case ID_MENU_AI:
                presentFragment(new YumiAiSettingsActivity());
                break;
            case ID_MENU_INTERFACE:
                presentFragment(new YumiInterfaceSettingsActivity());
                break;
            case ID_MENU_UPDATE:
                presentFragment(new YumiUpdateActivity());
                break;
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
