package org.telegram.messenger.yumigram;

import org.telegram.messenger.FileLog;

/**
 * Controller managing Ghost Mode (Режим призрака) operations.
 * Intercepts read receipts, typing status, and story views.
 */
public class YumiGhostController {

    private static final String TAG = "YumiGhostController";

    /**
     * Determines whether reading history in a chat should be suppressed from the server.
     * @param dialogId dialog ID being viewed
     * @return true if the read receipt should NOT be sent to Telegram servers
     */
    public static boolean shouldSuppressRead(long dialogId) {
        boolean suppress = YumiConfig.isHideReadEnabled();
        if (suppress) {
            FileLog.d(TAG + ": Suppressed read receipt for dialogId=" + dialogId);
        }
        return suppress;
    }

    /**
     * Determines whether typing status ("typing...", "uploading photo...") should be suppressed.
     * @param dialogId target dialog ID
     * @return true if typing status should NOT be sent to Telegram servers
     */
    public static boolean shouldSuppressTyping(long dialogId) {
        boolean suppress = YumiConfig.isHideTypingEnabled();
        if (suppress) {
            FileLog.d(TAG + ": Suppressed typing status for dialogId=" + dialogId);
        }
        return suppress;
    }

    /**
     * Determines whether viewing a story should mark it as viewed on the server.
     * @param dialogId user who posted the story
     * @param storyId ID of the story
     * @return true if story view receipt should NOT be sent to Telegram servers
     */
    public static boolean shouldSuppressStoryView(long dialogId, int storyId) {
        boolean suppress = YumiConfig.isHideStoriesSeenEnabled();
        if (suppress) {
            FileLog.d(TAG + ": Suppressed story view for dialogId=" + dialogId + ", storyId=" + storyId);
        }
        return suppress;
    }
}
