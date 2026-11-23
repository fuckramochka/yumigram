package org.telegram.messenger.yumigram.util;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.annotation.StringRes;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;

import java.util.Locale;

/**
 * Robust Locale Resolver for Yumigram.
 * Guarantees that string resources match Telegram's currently selected language (English, Ukrainian, Russian)
 * independently of Android system locale or remote langpack caches.
 */
public class YumiLocale {

    public static String getString(@StringRes int resId) {
        Context context = ApplicationLoader.applicationContext;
        if (context == null || resId == 0) {
            return "";
        }

        try {
            Locale targetLocale = null;
            if (LocaleController.getInstance() != null) {
                targetLocale = LocaleController.getInstance().currentLocale;
            }
            if (targetLocale == null) {
                targetLocale = Locale.getDefault();
            }

            Configuration config = new Configuration(context.getResources().getConfiguration());
            config.setLocale(targetLocale);
            Context localizedContext = context.createConfigurationContext(config);
            return localizedContext.getResources().getString(resId);
        } catch (Throwable e) {
            // Fallback to standard Telegram resolver
            return LocaleController.getString(resId);
        }
    }

    public static String formatString(String key, @StringRes int resId, Object... args) {
        try {
            String format = getString(resId);
            Locale targetLocale = null;
            if (LocaleController.getInstance() != null) {
                targetLocale = LocaleController.getInstance().currentLocale;
            }
            if (targetLocale != null) {
                return String.format(targetLocale, format, args);
            }
            return String.format(format, args);
        } catch (Throwable e) {
            return LocaleController.formatString(key, resId, args);
        }
    }
}
