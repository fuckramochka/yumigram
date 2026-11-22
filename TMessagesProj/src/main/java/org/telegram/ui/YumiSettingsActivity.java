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
 * Dedicated settings screen for Yumigram (Юмиграм) modifications.
 */
public class YumiSettingsActivity extends UniversalFragment {

    private static final int ID_GHOST_MODE = 1;
    private static final int ID_HIDE_READ = 2;
    private static final int ID_HIDE_TYPING = 3;
    private static final int ID_HIDE_STORIES_SEEN = 4;
    private static final int ID_HIDE_VOICE_LISTEN = 5;
    private static final int ID_STAY_OFFLINE = 6;

    private static final int ID_ANTI_DELETE = 10;
    private static final int ID_SAVE_DELETED_MEDIA = 11;

    private static final int ID_SAVE_SECRET_MEDIA = 15;
    private static final int ID_SEND_SECRET_MEDIA_READ = 16;
    private static final int ID_ALLOW_SECRET_SCREENSHOTS = 17;

    private static final int ID_AI_ENABLED = 20;

    private static final int ID_ENHANCED_PLAYER = 30;
    private static final int ID_EQUALIZER = 31;

    private static final int ID_HIDE_SPONSORED = 40;

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
        // --- Ghost Mode ---
        items.add(UItem.asHeader(getString(R.string.YumiSectionGhost)));
        items.add(UItem.asCheck(ID_GHOST_MODE, getString(R.string.YumiGhostMode)).setChecked(YumiConfig.isGhostModeEnabled()));
        items.add(UItem.asCheck(ID_HIDE_READ, getString(R.string.YumiHideRead)).setChecked(YumiConfig.isHideReadEnabled()));
        items.add(UItem.asCheck(ID_HIDE_TYPING, getString(R.string.YumiHideTyping)).setChecked(YumiConfig.isHideTypingEnabled()));
        items.add(UItem.asCheck(ID_HIDE_STORIES_SEEN, getString(R.string.YumiHideStoriesSeen)).setChecked(YumiConfig.isHideStoriesSeenEnabled()));
        items.add(UItem.asCheck(ID_HIDE_VOICE_LISTEN, getString(R.string.YumiHideVoiceListen)).setChecked(YumiConfig.isHideVoiceListenEnabled()));
        items.add(UItem.asCheck(ID_STAY_OFFLINE, getString(R.string.YumiStayOffline)).setChecked(YumiConfig.isStayOfflineEnabled()));
        items.add(UItem.asShadow(getString(R.string.YumiGhostModeInfo)));

        // --- Anti-Delete ---
        items.add(UItem.asHeader(getString(R.string.YumiSectionAntiDelete)));
        items.add(UItem.asCheck(ID_ANTI_DELETE, getString(R.string.YumiAntiDelete)).setChecked(YumiConfig.isAntiDeleteEnabled()));
        items.add(UItem.asCheck(ID_SAVE_DELETED_MEDIA, getString(R.string.YumiSaveDeletedMedia)).setChecked(YumiConfig.isSaveDeletedMediaEnabled()));
        items.add(UItem.asShadow(getString(R.string.YumiSaveDeletedMediaInfo)));

        // --- View-Once Media ---
        items.add(UItem.asHeader(getString(R.string.YumiSectionSecretMedia)));
        items.add(UItem.asCheck(ID_SAVE_SECRET_MEDIA, getString(R.string.YumiSaveSecretMedia)).setChecked(YumiConfig.isSaveSecretMediaEnabled()));
        items.add(UItem.asCheck(ID_SEND_SECRET_MEDIA_READ, getString(R.string.YumiSendSecretMediaRead)).setChecked(YumiConfig.shouldSendSecretMediaRead()));
        items.add(UItem.asCheck(ID_ALLOW_SECRET_SCREENSHOTS, getString(R.string.YumiAllowSecretScreenshots)).setChecked(YumiConfig.isAllowScreenshotsInSecretMedia()));
        items.add(UItem.asShadow(getString(R.string.YumiSendSecretMediaReadInfo)));

        // --- AI Assistant ---
        items.add(UItem.asHeader(getString(R.string.YumiSectionAI)));
        items.add(UItem.asCheck(ID_AI_ENABLED, getString(R.string.YumiAiEnabled)).setChecked(YumiConfig.isAiEnabled()));
        items.add(UItem.asShadow(getString(R.string.YumiAiEnabledInfo)));

        // --- Media Player ---
        items.add(UItem.asHeader(getString(R.string.YumiSectionPlayer)));
        items.add(UItem.asCheck(ID_ENHANCED_PLAYER, getString(R.string.YumiEnhancedPlayer)).setChecked(YumiConfig.isEnhancedPlayerEnabled()));
        items.add(UItem.asCheck(ID_EQUALIZER, getString(R.string.YumiEqualizer)).setChecked(YumiConfig.isEqualizerEnabled()));
        items.add(UItem.asShadow(getString(R.string.YumiEnhancedPlayerInfo)));

        // --- Interface ---
        items.add(UItem.asHeader(getString(R.string.YumiSectionUI)));
        items.add(UItem.asCheck(ID_HIDE_SPONSORED, getString(R.string.YumiHideSponsored)).setChecked(YumiConfig.isHideSponsoredEnabled()));
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_GHOST_MODE:
                YumiConfig.setGhostModeEnabled(!YumiConfig.isGhostModeEnabled());
                if (adapter != null) adapter.update(true);
                break;
            case ID_HIDE_READ:
                YumiConfig.setHideReadEnabled(!YumiConfig.isHideReadEnabled());
                if (adapter != null) adapter.update(true);
                break;
            case ID_HIDE_TYPING:
                YumiConfig.setHideTypingEnabled(!YumiConfig.isHideTypingEnabled());
                if (adapter != null) adapter.update(true);
                break;
            case ID_HIDE_STORIES_SEEN:
                YumiConfig.setHideStoriesSeenEnabled(!YumiConfig.isHideStoriesSeenEnabled());
                if (adapter != null) adapter.update(true);
                break;
            case ID_HIDE_VOICE_LISTEN:
                YumiConfig.setHideVoiceListenEnabled(!YumiConfig.isHideVoiceListenEnabled());
                if (adapter != null) adapter.update(true);
                break;
            case ID_STAY_OFFLINE:
                YumiConfig.setStayOfflineEnabled(!YumiConfig.isStayOfflineEnabled());
                if (adapter != null) adapter.update(true);
                break;
            case ID_ANTI_DELETE:
                YumiConfig.setAntiDeleteEnabled(!YumiConfig.isAntiDeleteEnabled());
                if (adapter != null) adapter.update(true);
                break;
            case ID_SAVE_DELETED_MEDIA:
                YumiConfig.setSaveDeletedMediaEnabled(!YumiConfig.isSaveDeletedMediaEnabled());
                if (adapter != null) adapter.update(true);
                break;
            case ID_SAVE_SECRET_MEDIA:
                YumiConfig.setSaveSecretMediaEnabled(!YumiConfig.isSaveSecretMediaEnabled());
                if (adapter != null) adapter.update(true);
                break;
            case ID_SEND_SECRET_MEDIA_READ:
                YumiConfig.setSendSecretMediaRead(!YumiConfig.shouldSendSecretMediaRead());
                if (adapter != null) adapter.update(true);
                break;
            case ID_ALLOW_SECRET_SCREENSHOTS:
                YumiConfig.setAllowScreenshotsInSecretMedia(!YumiConfig.isAllowScreenshotsInSecretMedia());
                if (adapter != null) adapter.update(true);
                break;
            case ID_AI_ENABLED:
                YumiConfig.setAiEnabled(!YumiConfig.isAiEnabled());
                if (adapter != null) adapter.update(true);
                break;
            case ID_ENHANCED_PLAYER:
                YumiConfig.setEnhancedPlayerEnabled(!YumiConfig.isEnhancedPlayerEnabled());
                if (adapter != null) adapter.update(true);
                break;
            case ID_EQUALIZER:
                YumiConfig.setEqualizerEnabled(!YumiConfig.isEqualizerEnabled());
                if (adapter != null) adapter.update(true);
                break;
            case ID_HIDE_SPONSORED:
                YumiConfig.setHideSponsoredEnabled(!YumiConfig.isHideSponsoredEnabled());
                if (adapter != null) adapter.update(true);
                break;
        }
    }
}
