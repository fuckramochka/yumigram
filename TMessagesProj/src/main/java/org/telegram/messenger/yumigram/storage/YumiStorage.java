package org.telegram.messenger.yumigram.storage;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.messenger.FileLog;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Storage manager for Yumigram (Юмиграм) persistent features like Anti-Delete.
 */
public class YumiStorage {

    private static final String TAG = "YumiStorage";
    private static final Set<String> deletedCache = Collections.synchronizedSet(new HashSet<>());

    /**
     * Initializes Yumigram custom tables inside the existing SQLite database.
     */
    public static void initDatabase(SQLiteDatabase database) {
        if (database == null) return;
        try {
            database.executeFast("CREATE TABLE IF NOT EXISTS yumi_deleted_messages(mid INTEGER, uid INTEGER, deleted_date INTEGER, PRIMARY KEY(mid, uid))").stepThis().dispose();
        } catch (Exception e) {
            FileLog.e(TAG, e);
        }
    }

    /**
     * Marks a message as deleted by the other party without purging its contents.
     */
    public static void saveDeletedMessage(SQLiteDatabase database, long uid, int mid) {
        if (database == null) return;
        try {
            long now = System.currentTimeMillis() / 1000;
            database.executeFast(String.format(Locale.US, "INSERT OR REPLACE INTO yumi_deleted_messages VALUES(%d, %d, %d)", mid, uid, now)).stepThis().dispose();
            deletedCache.add(buildKey(uid, mid));
        } catch (Exception e) {
            FileLog.e(TAG, e);
        }
    }

    /**
     * Checks whether a message was flagged as deleted.
     */
    public static boolean isDeleted(long uid, int mid) {
        return deletedCache.contains(buildKey(uid, mid));
    }

    private static String buildKey(long uid, int mid) {
        return uid + "_" + mid;
    }
}
