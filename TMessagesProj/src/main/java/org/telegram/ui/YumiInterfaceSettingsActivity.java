package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.R;
import org.telegram.messenger.yumigram.YumiConfig;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * Dedicated settings screen for Yumigram Interface & Spotify Player (Інтерфейс та Spotify плеєр).
 */
public class YumiInterfaceSettingsActivity extends UniversalFragment {

    private static final int ID_SPOTIFY_PLAYER = 1;
    private static final int ID_SPOTIFY_DYNAMIC_COLOR = 2;
    private static final int ID_ENHANCED_PLAYER = 3;
    private static final int ID_EQUALIZER = 4;

    private static final int ID_HIDE_STORIES = 10;
    private static final int ID_HIDE_SPONSORED = 11;
    private static final int ID_CONFIRM_VOICE_CALL = 12;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.YumiMenuInterface);
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
        // --- 1. Spotify Player Section ---
        items.add(UItem.asHeader(getString(R.string.YumiSpotifyPlayer)));
        items.add(UItem.asCheck(ID_SPOTIFY_PLAYER, getString(R.string.YumiSpotifyPlayer)).setChecked(YumiConfig.isSpotifyPlayerEnabled()));
        items.add(UItem.asCheck(ID_SPOTIFY_DYNAMIC_COLOR, getString(R.string.YumiSpotifyDynamicColor)).setChecked(YumiConfig.isSpotifyDynamicColorEnabled()));
        items.add(UItem.asCheck(ID_ENHANCED_PLAYER, getString(R.string.YumiEnhancedPlayer)).setChecked(YumiConfig.isEnhancedPlayerEnabled()));
        items.add(UItem.asCheck(ID_EQUALIZER, getString(R.string.YumiEqualizer)).setChecked(YumiConfig.isEqualizerEnabled()));
        items.add(UItem.asShadow(getString(R.string.YumiSpotifyPlayerInfo)));

        // --- 2. Interface Customization ---
        items.add(UItem.asHeader(getString(R.string.YumiSectionUI)));
        items.add(UItem.asCheck(ID_HIDE_STORIES, getString(R.string.YumiHideStories)).setChecked(YumiConfig.isHideStoriesEnabled()));
        items.add(UItem.asCheck(ID_HIDE_SPONSORED, getString(R.string.YumiHideSponsored)).setChecked(YumiConfig.isHideSponsoredEnabled()));
        items.add(UItem.asCheck(ID_CONFIRM_VOICE_CALL, getString(R.string.YumiConfirmVoiceCall)).setChecked(YumiConfig.isConfirmVoiceCallEnabled()));
        items.add(UItem.asShadow(getString(R.string.YumiHideStoriesInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_SPOTIFY_PLAYER:
                YumiConfig.setSpotifyPlayerEnabled(!YumiConfig.isSpotifyPlayerEnabled());
                updateList();
                break;
            case ID_SPOTIFY_DYNAMIC_COLOR:
                YumiConfig.setSpotifyDynamicColorEnabled(!YumiConfig.isSpotifyDynamicColorEnabled());
                updateList();
                break;
            case ID_ENHANCED_PLAYER:
                YumiConfig.setEnhancedPlayerEnabled(!YumiConfig.isEnhancedPlayerEnabled());
                updateList();
                break;
            case ID_EQUALIZER:
                YumiConfig.setEqualizerEnabled(!YumiConfig.isEqualizerEnabled());
                updateList();
                break;
            case ID_HIDE_STORIES:
                YumiConfig.setHideStoriesEnabled(!YumiConfig.isHideStoriesEnabled());
                updateList();
                break;
            case ID_HIDE_SPONSORED:
                YumiConfig.setHideSponsoredEnabled(!YumiConfig.isHideSponsoredEnabled());
                updateList();
                break;
            case ID_CONFIRM_VOICE_CALL:
                YumiConfig.setConfirmVoiceCallEnabled(!YumiConfig.isConfirmVoiceCallEnabled());
                updateList();
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
