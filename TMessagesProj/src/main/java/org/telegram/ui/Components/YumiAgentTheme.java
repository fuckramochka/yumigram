package org.telegram.ui.Components;

import org.telegram.ui.ActionBar.Theme;

/** Theme tokens for the AI workspace. New visual styles can be added here without changing chat logic. */
public final class YumiAgentTheme {
    public interface Provider {
        Colors resolve();
    }

    public static final class Colors {
        public final int background;
        public final int surface;
        public final int raisedSurface;
        public final int primaryText;
        public final int secondaryText;
        public final int accent;
        public final int accentSoft;
        public final int userBubble;
        public final int userText;
        public final int outline;

        private Colors(int background, int surface, int raisedSurface, int primaryText, int secondaryText,
                       int accent, int accentSoft, int userBubble, int userText, int outline) {
            this.background = background;
            this.surface = surface;
            this.raisedSurface = raisedSurface;
            this.primaryText = primaryText;
            this.secondaryText = secondaryText;
            this.accent = accent;
            this.accentSoft = accentSoft;
            this.userBubble = userBubble;
            this.userText = userText;
            this.outline = outline;
        }
    }

    private static final Provider TELEGRAM = () -> new Colors(
            Theme.getColor(Theme.key_windowBackgroundGray),
            Theme.getColor(Theme.key_windowBackgroundWhite),
            Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2),
            Theme.getColor(Theme.key_windowBackgroundWhiteBlackText),
            Theme.getColor(Theme.key_windowBackgroundWhiteHintText),
            Theme.getColor(Theme.key_chat_messagePanelSend),
            Theme.getColor(Theme.key_featuredStickers_addButton),
            Theme.getColor(Theme.key_chat_outBubble),
            Theme.getColor(Theme.key_chat_messageTextOut),
            Theme.getColor(Theme.key_divider));

    private static Provider provider = TELEGRAM;

    private YumiAgentTheme() {}

    public static Colors colors() {
        return provider.resolve();
    }

    /** Internal extension point; this release uses the active Telegram theme only. */
    public static void setProvider(Provider next) {
        provider = next == null ? TELEGRAM : next;
    }
}
