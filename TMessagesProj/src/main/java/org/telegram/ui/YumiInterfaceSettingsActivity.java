package org.telegram.ui;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.R;
import org.telegram.messenger.yumigram.YumiConfig;
import org.telegram.messenger.yumigram.util.YumiLocale;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * Dedicated settings screen for Yumigram Interface & Spotify Player.
 * Features dynamic expanding/collapsing of player options.
 */
public class YumiInterfaceSettingsActivity extends UniversalFragment {

    private static final int ID_SPOTIFY_PLAYER = 1;
    private static final int ID_SPOTIFY_DYNAMIC_COLOR = 2;
    private static final int ID_ENHANCED_PLAYER = 3;
    private static final int ID_EQUALIZER = 4;

    private static final int ID_HIDE_STORIES = 10;
    private static final int ID_HIDE_SPONSORED = 11;
    private static final int ID_CONFIRM_VOICE_CALL = 12;
    private static final int ID_PROFILE_V2 = 13;

    @Override
    protected CharSequence getTitle() {
        return YumiLocale.getString(R.string.YumiMenuInterface);
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
        boolean spotifyEnabled = YumiConfig.isSpotifyPlayerEnabled();
        items.add(UItem.asHeader(YumiLocale.getString(R.string.YumiSpotifyPlayer)));
        items.add(UItem.asCheck(ID_SPOTIFY_PLAYER, YumiLocale.getString(R.string.YumiSpotifyPlayer)).setChecked(spotifyEnabled));

        if (spotifyEnabled) {
            items.add(UItem.asCheck(ID_SPOTIFY_DYNAMIC_COLOR, YumiLocale.getString(R.string.YumiSpotifyDynamicColor)).setChecked(YumiConfig.isSpotifyDynamicColorEnabled()));
            items.add(UItem.asCheck(ID_ENHANCED_PLAYER, YumiLocale.getString(R.string.YumiEnhancedPlayer)).setChecked(YumiConfig.isEnhancedPlayerEnabled()));
            items.add(UItem.asCheck(ID_EQUALIZER, YumiLocale.getString(R.string.YumiEqualizer)).setChecked(YumiConfig.isEqualizerEnabled()));
        }
        items.add(UItem.asShadow(YumiLocale.getString(R.string.YumiSpotifyPlayerInfo)));

        // --- 2. Interface Customization ---
        items.add(UItem.asHeader(YumiLocale.getString(R.string.YumiSectionUI)));
        items.add(UItem.asCheck(ID_HIDE_STORIES, YumiLocale.getString(R.string.YumiHideStories)).setChecked(YumiConfig.isHideStoriesEnabled()));
        items.add(UItem.asCheck(ID_HIDE_SPONSORED, YumiLocale.getString(R.string.YumiHideSponsored)).setChecked(YumiConfig.isHideSponsoredEnabled()));
        items.add(UItem.asCheck(ID_CONFIRM_VOICE_CALL, YumiLocale.getString(R.string.YumiConfirmVoiceCall)).setChecked(YumiConfig.isConfirmVoiceCallEnabled()));
        items.add(UItem.asCheck(ID_PROFILE_V2, YumiLocale.getString(R.string.YumiProfileV2)).setChecked(YumiConfig.isProfileV2Enabled()));
        items.add(UItem.asShadow(YumiLocale.getString(R.string.YumiHideStoriesInfo)));
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
            case ID_PROFILE_V2:
                YumiConfig.setProfileV2Enabled(!YumiConfig.isProfileV2Enabled());
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
