package org.telegram.messenger.yumigram.ai;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Account-scoped local persistence for the AI conversation list and transcript. */
public final class YumiAgentConversationStore {
    public static final int ROLE_USER = 0;
    public static final int ROLE_ASSISTANT = 1;
    private static final String TAG = "YumiAgentConversationStore";
    public static final String DEFAULT_TITLE = "__yumi_ai_new_chat__";
    private static final String LEGACY_CONVERSATION_ID = "legacy-import-v1";
    private static final String LEGACY_MIGRATION_KEY = "legacy_history_v1";

    public static final class Conversation {
        public final String id;
        public final String title;
        public final long createdAt;
        public final long updatedAt;
        public final String preview;

        public Conversation(String id, String title, long createdAt, long updatedAt, String preview) {
            this.id = id;
            this.title = title;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.preview = preview;
        }
    }

    public static final class AgentMessage {
        public final String id;
        public final String conversationId;
        public final int role;
        public final String content;
        public final long createdAt;
        public final String attachmentName;
        public final String attachmentMime;

        public AgentMessage(String id, String conversationId, int role, String content, long createdAt, String attachmentName, String attachmentMime) {
            this.id = id;
            this.conversationId = conversationId;
            this.role = role;
            this.content = content;
            this.createdAt = createdAt;
            this.attachmentName = attachmentName;
            this.attachmentMime = attachmentMime;
        }
    }

    public interface Callback<T> {
        void onResult(T value);
        void onError(String error);
    }

    private interface DatabaseAction<T> {
        T run(SQLiteDatabase database) throws Exception;
    }

    private YumiAgentConversationStore() {}

    public static void createConversation(int account, Callback<Conversation> callback) {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        run(account, callback, database -> {
            execute(database, "INSERT INTO yumi_ai_conversations(id,title,created_at,updated_at,preview) VALUES(?,?,?,?,?)",
                    id, DEFAULT_TITLE, now, now, "");
            return new Conversation(id, DEFAULT_TITLE, now, now, "");
        });
    }

    public static void listConversations(int account, Callback<List<Conversation>> callback) {
        run(account, callback, database -> {
            ArrayList<Conversation> rows = new ArrayList<>();
            SQLiteCursor cursor = database.queryFinalized("SELECT id,title,created_at,updated_at,preview FROM yumi_ai_conversations ORDER BY updated_at DESC");
            try {
                while (cursor.next()) rows.add(new Conversation(cursor.stringValue(0), cursor.stringValue(1), cursor.longValue(2), cursor.longValue(3), cursor.stringValue(4)));
            } finally {
                cursor.dispose();
            }
            return rows;
        });
    }

    public static void searchConversations(int account, String query, Callback<List<Conversation>> callback) {
        String needle = query == null ? "" : query.trim();
        run(account, callback, database -> {
            ArrayList<Conversation> rows = new ArrayList<>();
            SQLiteCursor cursor;
            if (needle.isEmpty()) {
                cursor = database.queryFinalized("SELECT id,title,created_at,updated_at,preview FROM yumi_ai_conversations ORDER BY updated_at DESC");
            } else {
                cursor = database.queryFinalized(
                        "SELECT c.id,c.title,c.created_at,c.updated_at,c.preview FROM yumi_ai_conversations c " +
                                "WHERE instr(lower(c.title),lower(?))>0 OR instr(lower(c.preview),lower(?))>0 " +
                                "OR EXISTS (SELECT 1 FROM yumi_ai_messages m WHERE m.conversation_id=c.id AND instr(lower(m.content),lower(?))>0) " +
                                "ORDER BY c.updated_at DESC",
                        new Object[]{needle, needle, needle});
            }
            try {
                while (cursor.next()) rows.add(new Conversation(cursor.stringValue(0), cursor.stringValue(1), cursor.longValue(2), cursor.longValue(3), cursor.stringValue(4)));
            } finally {
                cursor.dispose();
            }
            return rows;
        });
    }

    public static void loadMessages(int account, String conversationId, Callback<List<AgentMessage>> callback) {
        run(account, callback, database -> {
            ArrayList<AgentMessage> rows = new ArrayList<>();
            SQLiteCursor cursor = database.queryFinalized(
                    "SELECT id,conversation_id,role,content,created_at,attachment_name,attachment_mime FROM yumi_ai_messages WHERE conversation_id=? ORDER BY created_at,id",
                    new Object[]{conversationId});
            try {
                while (cursor.next()) rows.add(new AgentMessage(cursor.stringValue(0), cursor.stringValue(1), cursor.intValue(2), cursor.stringValue(3), cursor.longValue(4), cursor.stringValue(5), cursor.stringValue(6)));
            } finally {
                cursor.dispose();
            }
            return rows;
        });
    }

    public static void saveMessage(int account, String conversationId, String messageId, int role, String content, String attachmentName, String attachmentMime, Callback<Boolean> callback) {
        if (TextUtils.isEmpty(conversationId) || TextUtils.isEmpty(messageId) || TextUtils.isEmpty(content) || (role != ROLE_USER && role != ROLE_ASSISTANT)) {
            if (callback != null) callback.onError("Не удалось сохранить сообщение AI.");
            return;
        }
        long now = System.currentTimeMillis();
        String id = messageId;
        String preview = content.replace('\n', ' ').trim();
        if (preview.length() > 96) preview = preview.substring(0, 93) + "…";
        String titleCandidate = content.replace('\n', ' ').trim();
        if (titleCandidate.length() > 42) titleCandidate = titleCandidate.substring(0, 39) + "…";
        final String shortPreview = preview;
        final String title = titleCandidate;
        run(account, callback, database -> {
            execute(database, "INSERT OR REPLACE INTO yumi_ai_messages(id,conversation_id,role,content,created_at,attachment_name,attachment_mime) VALUES(?,?,?,?,?,?,?)",
                    id, conversationId, role, content, now, attachmentName, attachmentMime);
            execute(database, "UPDATE yumi_ai_conversations SET title=CASE WHEN title=? AND ?=? THEN ? ELSE title END, updated_at=?, preview=? WHERE id=?",
                    DEFAULT_TITLE, role, ROLE_USER, title, now, shortPreview, conversationId);
            return true;
        });
    }

    public static void renameConversation(int account, String conversationId, String title, Callback<Boolean> callback) {
        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(title.trim())) {
            if (callback != null) callback.onError("Название чата не может быть пустым.");
            return;
        }
        String safeTitle = title.trim();
        if (safeTitle.length() > 80) safeTitle = safeTitle.substring(0, 80);
        final String finalTitle = safeTitle;
        run(account, callback, database -> execute(database,
                "UPDATE yumi_ai_conversations SET title=?, updated_at=? WHERE id=?", finalTitle, System.currentTimeMillis(), conversationId) >= 0);
    }

    public static void deleteConversation(int account, String conversationId, Callback<Boolean> callback) {
        run(account, callback, database -> {
            execute(database, "DELETE FROM yumi_ai_messages WHERE conversation_id=?", conversationId);
            execute(database, "DELETE FROM yumi_ai_conversations WHERE id=?", conversationId);
            return true;
        });
    }

    public static void clearConversation(int account, String conversationId, Callback<Boolean> callback) {
        long now = System.currentTimeMillis();
        run(account, callback, database -> {
            execute(database, "DELETE FROM yumi_ai_messages WHERE conversation_id=?", conversationId);
            execute(database, "UPDATE yumi_ai_conversations SET preview='',updated_at=? WHERE id=?", now, conversationId);
            return true;
        });
    }

    /** Imports the previous single JSON transcript once, into the currently selected account's DB. */
    public static void importLegacyHistory(int account, String serializedHistory, Callback<String> callback) {
        run(account, callback, database -> {
            Integer migrated = database.executeInt("SELECT 1 FROM yumi_ai_meta WHERE key=?", LEGACY_MIGRATION_KEY);
            if (migrated != null) return null;
            JSONArray legacy;
            try {
                legacy = new JSONArray(serializedHistory == null ? "[]" : serializedHistory);
            } catch (Exception malformedHistory) {
                // Keep the original legacy file intact, but let the app move on with a clean local history.
                FileLog.e(TAG, malformedHistory);
                legacy = new JSONArray();
            }
            long now = System.currentTimeMillis();
            String preview = "";
            String title = DEFAULT_TITLE;
            for (int i = 0; i < legacy.length(); i++) {
                JSONObject row = legacy.optJSONObject(i);
                if (row == null) continue;
                String text = row.optString("text", "");
                if (TextUtils.isEmpty(text)) continue;
                int role = row.optBoolean("isAi") ? ROLE_ASSISTANT : ROLE_USER;
                if (title.equals(DEFAULT_TITLE) && role == ROLE_USER) {
                    title = text.replace('\n', ' ').trim();
                    if (title.length() > 42) title = title.substring(0, 39) + "…";
                }
                preview = text.replace('\n', ' ').trim();
                if (preview.length() > 96) preview = preview.substring(0, 93) + "…";
            }
            if (!TextUtils.isEmpty(preview)) {
                execute(database, "INSERT OR IGNORE INTO yumi_ai_conversations(id,title,created_at,updated_at,preview) VALUES(?,?,?,?,?)",
                        LEGACY_CONVERSATION_ID, title, now, now, preview);
                for (int i = 0; i < legacy.length(); i++) {
                    JSONObject row = legacy.optJSONObject(i);
                    if (row == null) continue;
                    String text = row.optString("text", "");
                    if (TextUtils.isEmpty(text)) continue;
                    int role = row.optBoolean("isAi") ? ROLE_ASSISTANT : ROLE_USER;
                    execute(database, "INSERT OR IGNORE INTO yumi_ai_messages(id,conversation_id,role,content,created_at) VALUES(?,?,?,?,?)",
                            "legacy-" + i, LEGACY_CONVERSATION_ID, role, text, now + i);
                }
            }
            execute(database, "INSERT OR REPLACE INTO yumi_ai_meta(key,value) VALUES(?,?)", LEGACY_MIGRATION_KEY, "1");
            return TextUtils.isEmpty(preview) ? null : LEGACY_CONVERSATION_ID;
        });
    }

    private static <T> void run(int account, Callback<T> callback, DatabaseAction<T> action) {
        if (account < 0 || account >= org.telegram.messenger.UserConfig.MAX_ACCOUNT_COUNT) {
            if (callback != null) callback.onError("Аккаунт Telegram недоступен.");
            return;
        }
        MessagesStorage storage = MessagesStorage.getInstance(account);
        storage.getStorageQueue().postRunnable(() -> {
            T result = null;
            String error = null;
            try {
                SQLiteDatabase database = storage.getDatabase();
                if (database == null) throw new IllegalStateException("Локальная база ещё не открыта");
                ensureSchema(database);
                result = action.run(database);
            } catch (Throwable e) {
                FileLog.e(TAG, e);
                error = e.getLocalizedMessage();
            }
            T callbackResult = result;
            String callbackError = error;
            AndroidUtilities.runOnUIThread(() -> {
                if (callback == null) return;
                if (callbackError != null) callback.onError("Ошибка локальной истории AI: " + callbackError);
                else callback.onResult(callbackResult);
            });
        });
    }

    private static void ensureSchema(SQLiteDatabase database) throws Exception {
        execute(database, "CREATE TABLE IF NOT EXISTS yumi_ai_conversations(id TEXT PRIMARY KEY, title TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, preview TEXT NOT NULL DEFAULT '')");
        execute(database, "CREATE INDEX IF NOT EXISTS yumi_ai_conversations_updated ON yumi_ai_conversations(updated_at DESC)");
        execute(database, "CREATE TABLE IF NOT EXISTS yumi_ai_messages(id TEXT PRIMARY KEY, conversation_id TEXT NOT NULL, role INTEGER NOT NULL, content TEXT NOT NULL, created_at INTEGER NOT NULL, attachment_name TEXT, attachment_mime TEXT)");
        execute(database, "CREATE INDEX IF NOT EXISTS yumi_ai_messages_conversation ON yumi_ai_messages(conversation_id, created_at)");
        execute(database, "CREATE TABLE IF NOT EXISTS yumi_ai_meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)");
    }

    private static int execute(SQLiteDatabase database, String sql, Object... values) throws Exception {
        SQLitePreparedStatement statement = database.executeFast(sql);
        try {
            for (int i = 0; i < values.length; i++) {
                Object value = values[i];
                if (value == null) statement.bindNull(i + 1);
                else if (value instanceof String) statement.bindString(i + 1, (String) value);
                else if (value instanceof Integer) statement.bindInteger(i + 1, (Integer) value);
                else if (value instanceof Long) statement.bindLong(i + 1, (Long) value);
                else throw new IllegalArgumentException("Unsupported SQLite value type");
            }
            return statement.step();
        } finally {
            statement.dispose();
        }
    }
}
