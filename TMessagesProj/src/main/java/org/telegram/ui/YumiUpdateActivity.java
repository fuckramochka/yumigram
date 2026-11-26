package org.telegram.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.yumigram.YumiConfig;
import org.telegram.messenger.yumigram.update.YumiUpdateManager;
import org.telegram.messenger.yumigram.util.YumiLocale;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.util.ArrayList;

/**
 * Dedicated Auto-Update & Releases Screen for Yumigram.
 * Shows current version, checks GitHub releases for green builds,
 * downloads arm64-v8a APK with live progress, and triggers system installer.
 */
public class YumiUpdateActivity extends UniversalFragment {

    private static final int ID_AUTO_CHECK = 1;
    private static final int ID_CHECK_NOW = 2;
    private static final int ID_DOWNLOAD_INSTALL = 3;

    private boolean isChecking = false;
    private YumiUpdateManager.UpdateInfo updateInfo;
    private Boolean hasUpdateAvailable = null;
    private String downloadProgress = null;
    private File readyApkFile = null;
    private int lastRenderedProgress = -1;
    private long lastProgressRenderTime;

    @Override
    protected CharSequence getTitle() {
        return YumiLocale.getString(R.string.YumiMenuUpdate);
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

        // Populate with cached update if exists
        updateInfo = YumiUpdateManager.getCachedUpdate();
        if (updateInfo != null) {
            hasUpdateAvailable = updateInfo.isNewerThan(YumiUpdateManager.CURRENT_VERSION);
        }

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
        // 1. Header Top View
        String subtitle = "Current: " + YumiUpdateManager.CURRENT_VERSION + " (arm64-v8a)";
        items.add(UItem.asTopView(YumiLocale.getString(R.string.YumiMenuUpdate), subtitle, "RestrictedEmoji", "🚀"));

        // 2. Settings Section
        items.add(UItem.asHeader(YumiLocale.getString(R.string.YumiMenuUpdate)));
        items.add(UItem.asCheck(ID_AUTO_CHECK, YumiLocale.getString(R.string.YumiUpdateAutoCheck)).setChecked(YumiConfig.isAutoCheckUpdatesEnabled()));
        items.add(UItem.asShadow(YumiLocale.getString(R.string.YumiUpdateAutoCheckInfo)));

        // 3. Action Section: Check button
        String checkTitle = isChecking ? YumiLocale.getString(R.string.YumiUpdateChecking) : YumiLocale.getString(R.string.YumiUpdateCheckButton);
        items.add(UItem.asButton(ID_CHECK_NOW, R.drawable.msg_download_settings, checkTitle));

        // 4. Update status & Release Details
        if (hasUpdateAvailable != null && updateInfo != null) {
            items.add(UItem.asHeader(YumiLocale.getString(R.string.YumiUpdateCardHeader)));

            if (hasUpdateAvailable) {
                String buttonText;
                if (readyApkFile != null && readyApkFile.exists()) {
                    buttonText = YumiLocale.getString(R.string.YumiUpdateInstallNow);
                } else if (downloadProgress != null) {
                    buttonText = downloadProgress;
                } else {
                    String sizeStr = updateInfo.apkSize > 0 ? AndroidUtilities.formatFileSize(updateInfo.apkSize) : "arm64";
                    buttonText = YumiLocale.formatString("YumiUpdateDownloadInstall", R.string.YumiUpdateDownloadInstall, sizeStr);
                }

                items.add(UItem.asButton(ID_DOWNLOAD_INSTALL, R.drawable.msg_download, buttonText, updateInfo.tagName));

                if (!TextUtils.isEmpty(updateInfo.changelog)) {
                    items.add(UItem.asHeader(YumiLocale.formatString("YumiUpdateChangelog", R.string.YumiUpdateChangelog, updateInfo.tagName)));
                    items.add(UItem.asShadow(updateInfo.changelog));
                }
            } else {
                items.add(UItem.asShadow(YumiLocale.formatString("YumiUpdateLatestInfo", R.string.YumiUpdateLatestInfo, YumiUpdateManager.CURRENT_VERSION)));
            }
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_AUTO_CHECK:
                boolean auto = !YumiConfig.isAutoCheckUpdatesEnabled();
                YumiConfig.setAutoCheckUpdatesEnabled(auto);
                updateList();
                break;

            case ID_CHECK_NOW:
                if (isChecking) {
                    return;
                }
                isChecking = true;
                updateList();

                YumiUpdateManager.checkUpdate(true, new YumiUpdateManager.CheckCallback() {
                    @Override
                    public void onSuccess(YumiUpdateManager.UpdateInfo update, boolean hasUpdate) {
                        isChecking = false;
                        updateInfo = update;
                        hasUpdateAvailable = hasUpdate;
                        updateList();

                        if (hasUpdate) {
                            BulletinFactory.of(YumiUpdateActivity.this).createSimpleBulletin(
                                    R.raw.chats_infotip,
                                    YumiLocale.formatString("YumiUpdateAvailableDetails", R.string.YumiUpdateAvailableDetails, update.tagName)
                            ).show();
                        } else {
                            BulletinFactory.of(YumiUpdateActivity.this).createSimpleBulletin(
                                    R.raw.chats_infotip,
                                    YumiLocale.getString(R.string.YumiUpdateLatest)
                            ).show();
                        }
                    }

                    @Override
                    public void onError(String error) {
                        isChecking = false;
                        updateList();

                        BulletinFactory.of(YumiUpdateActivity.this).createSimpleBulletin(
                                R.raw.error,
                                YumiLocale.formatString("YumiUpdateError", R.string.YumiUpdateError, error)
                        ).show();
                    }
                });
                break;

            case ID_DOWNLOAD_INSTALL:
                if (readyApkFile != null && readyApkFile.exists()) {
                    YumiUpdateManager.installApk(getParentActivity(), readyApkFile);
                    return;
                }

                if (updateInfo == null || YumiUpdateManager.isDownloading()) {
                    return;
                }

                downloadProgress = "⏳ 0%...";
                lastRenderedProgress = -1;
                lastProgressRenderTime = 0;
                updateList();

                YumiUpdateManager.downloadApk(updateInfo, new YumiUpdateManager.DownloadCallback() {
                    @Override
                    public void onProgress(int percent, long downloadedBytes, long totalBytes) {
                        long now = android.os.SystemClock.uptimeMillis();
                        if (percent != 100 && percent - lastRenderedProgress < 5 && now - lastProgressRenderTime < 1000) {
                            return;
                        }
                        lastRenderedProgress = percent;
                        lastProgressRenderTime = now;
                        String dlStr = AndroidUtilities.formatFileSize(downloadedBytes);
                        String totStr = AndroidUtilities.formatFileSize(totalBytes);
                        downloadProgress = YumiLocale.formatString("YumiUpdateDownloading", R.string.YumiUpdateDownloading, percent, dlStr, totStr);
                        updateList();
                    }

                    @Override
                    public void onSuccess(File apkFile) {
                        downloadProgress = null;
                        readyApkFile = apkFile;
                        updateList();

                        BulletinFactory.of(YumiUpdateActivity.this).createSimpleBulletin(
                                R.raw.chats_infotip,
                                YumiLocale.getString(R.string.YumiUpdateInstallNow)
                        ).show();

                        YumiUpdateManager.installApk(getParentActivity(), apkFile);
                    }

                    @Override
                    public void onError(String error) {
                        downloadProgress = null;
                        updateList();

                        BulletinFactory.of(YumiUpdateActivity.this).createSimpleBulletin(
                                R.raw.error,
                                YumiLocale.formatString("YumiUpdateError", R.string.YumiUpdateError, error)
                        ).show();
                    }
                });
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
