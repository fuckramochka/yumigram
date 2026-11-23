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
 * Dedicated settings screen for Yumigram Ghost Mode & Privacy.
 * Features dynamic expanding/collapsing: sub-options are smoothly hidden when master features are disabled.
 */
public class YumiGhostSettingsActivity extends UniversalFragment {

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

    @Override
    protected CharSequence getTitle() {
        return YumiLocale.getString(R.string.YumiMenuGhost);
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
        // --- 1. Master Ghost Mode ---
        boolean ghostEnabled = YumiConfig.isGhostModeEnabled();
        items.add(UItem.asHeader(YumiLocale.getString(R.string.YumiSectionGhost)));
        items.add(UItem.asCheck(ID_GHOST_MODE, YumiLocale.getString(R.string.YumiGhostMode)).setChecked(ghostEnabled));

        if (ghostEnabled) {
            items.add(UItem.asCheck(ID_HIDE_READ, YumiLocale.getString(R.string.YumiHideRead)).setChecked(YumiConfig.isHideReadEnabled()));
            items.add(UItem.asCheck(ID_HIDE_TYPING, YumiLocale.getString(R.string.YumiHideTyping)).setChecked(YumiConfig.isHideTypingEnabled()));
            items.add(UItem.asCheck(ID_HIDE_STORIES_SEEN, YumiLocale.getString(R.string.YumiHideStoriesSeen)).setChecked(YumiConfig.isHideStoriesSeenEnabled()));
            items.add(UItem.asCheck(ID_HIDE_VOICE_LISTEN, YumiLocale.getString(R.string.YumiHideVoiceListen)).setChecked(YumiConfig.isHideVoiceListenEnabled()));
            items.add(UItem.asCheck(ID_STAY_OFFLINE, YumiLocale.getString(R.string.YumiStayOffline)).setChecked(YumiConfig.isStayOfflineEnabled()));
        }
        items.add(UItem.asShadow(YumiLocale.getString(R.string.YumiGhostModeInfo)));

        // --- 2. Anti-Delete ---
        boolean antiDeleteEnabled = YumiConfig.isAntiDeleteEnabled();
        items.add(UItem.asHeader(YumiLocale.getString(R.string.YumiSectionAntiDelete)));
        items.add(UItem.asCheck(ID_ANTI_DELETE, YumiLocale.getString(R.string.YumiAntiDelete)).setChecked(antiDeleteEnabled));
        if (antiDeleteEnabled) {
            items.add(UItem.asCheck(ID_SAVE_DELETED_MEDIA, YumiLocale.getString(R.string.YumiSaveDeletedMedia)).setChecked(YumiConfig.isSaveDeletedMediaEnabled()));
        }
        items.add(UItem.asShadow(YumiLocale.getString(R.string.YumiSaveDeletedMediaInfo)));

        // --- 3. View-Once (Disappearing) Media ---
        boolean secretMediaEnabled = YumiConfig.isSaveSecretMediaEnabled();
        items.add(UItem.asHeader(YumiLocale.getString(R.string.YumiSectionSecretMedia)));
        items.add(UItem.asCheck(ID_SAVE_SECRET_MEDIA, YumiLocale.getString(R.string.YumiSaveSecretMedia)).setChecked(secretMediaEnabled));
        if (secretMediaEnabled) {
            items.add(UItem.asCheck(ID_SEND_SECRET_MEDIA_READ, YumiLocale.getString(R.string.YumiSendSecretMediaRead)).setChecked(YumiConfig.shouldSendSecretMediaRead()));
            items.add(UItem.asCheck(ID_ALLOW_SECRET_SCREENSHOTS, YumiLocale.getString(R.string.YumiAllowSecretScreenshots)).setChecked(YumiConfig.isAllowScreenshotsInSecretMedia()));
        }
        items.add(UItem.asShadow(YumiLocale.getString(R.string.YumiSendSecretMediaReadInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_GHOST_MODE:
                boolean ghost = !YumiConfig.isGhostModeEnabled();
                YumiConfig.setGhostModeEnabled(ghost);
                if (ghost) {
                    YumiConfig.setHideReadEnabled(true);
                    YumiConfig.setHideTypingEnabled(true);
                    YumiConfig.setHideStoriesSeenEnabled(true);
                    YumiConfig.setHideVoiceListenEnabled(true);
                    YumiConfig.setStayOfflineEnabled(true);
                }
                updateList();
                break;
            case ID_HIDE_READ:
                YumiConfig.setHideReadEnabled(!YumiConfig.isHideReadEnabled());
                updateList();
                break;
            case ID_HIDE_TYPING:
                YumiConfig.setHideTypingEnabled(!YumiConfig.isHideTypingEnabled());
                updateList();
                break;
            case ID_HIDE_STORIES_SEEN:
                YumiConfig.setHideStoriesSeenEnabled(!YumiConfig.isHideStoriesSeenEnabled());
                updateList();
                break;
            case ID_HIDE_VOICE_LISTEN:
                YumiConfig.setHideVoiceListenEnabled(!YumiConfig.isHideVoiceListenEnabled());
                updateList();
                break;
            case ID_STAY_OFFLINE:
                YumiConfig.setStayOfflineEnabled(!YumiConfig.isStayOfflineEnabled());
                updateList();
                break;
            case ID_ANTI_DELETE:
                YumiConfig.setAntiDeleteEnabled(!YumiConfig.isAntiDeleteEnabled());
                updateList();
                break;
            case ID_SAVE_DELETED_MEDIA:
                YumiConfig.setSaveDeletedMediaEnabled(!YumiConfig.isSaveDeletedMediaEnabled());
                updateList();
                break;
            case ID_SAVE_SECRET_MEDIA:
                YumiConfig.setSaveSecretMediaEnabled(!YumiConfig.isSaveSecretMediaEnabled());
                updateList();
                break;
            case ID_SEND_SECRET_MEDIA_READ:
                YumiConfig.setSendSecretMediaRead(!YumiConfig.shouldSendSecretMediaRead());
                updateList();
                break;
            case ID_ALLOW_SECRET_SCREENSHOTS:
                YumiConfig.setAllowScreenshotsInSecretMedia(!YumiConfig.isAllowScreenshotsInSecretMedia());
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
