package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.R;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * Main Yumigram Hub (Головне меню налаштувань Юмиграм).
 * Contains dedicated entry buttons for:
 * 1. Ghost Mode & Privacy (Стелс, анти-видалення, одноразові медіа)
 * 2. Yumi AI Agent (ШІ Агент, доступ до Telegram, моделі)
 * 3. Interface & Spotify Player (Spotify плеєр, інтерфейс)
 */
public class YumiSettingsActivity extends UniversalFragment {

    private static final int ID_MENU_GHOST = 1;
    private static final int ID_MENU_AI = 2;
    private static final int ID_MENU_INTERFACE = 3;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.YumiSettings);
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
        items.add(UItem.asHeader(getString(R.string.YumiSettings)));

        // 1. Ghost Mode & Privacy Hub
        items.add(UItem.asButton(ID_MENU_GHOST, R.drawable.settings_privacy, getString(R.string.YumiMenuGhost), getString(R.string.YumiMenuGhostSubtitle)));

        // 2. Yumi AI Agent Hub
        items.add(UItem.asButton(ID_MENU_AI, R.drawable.msg_bot, getString(R.string.YumiMenuAI), getString(R.string.YumiMenuAISubtitle)));

        // 3. Interface & Spotify Player Hub
        items.add(UItem.asButton(ID_MENU_INTERFACE, R.drawable.player_new_repeatall, getString(R.string.YumiMenuInterface), getString(R.string.YumiMenuInterfaceSubtitle)));

        items.add(UItem.asShadow("Yumigram v13.2.2 • " + getString(R.string.YumiSettingsInfo)));
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
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private void updateList() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }
}
