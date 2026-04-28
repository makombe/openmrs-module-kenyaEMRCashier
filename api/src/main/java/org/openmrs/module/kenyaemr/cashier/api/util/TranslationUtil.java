package org.openmrs.module.kenyaemr.cashier.api.util;

import org.openmrs.api.context.Context;

import java.util.Locale;

public final class TranslationUtil {

    private TranslationUtil() {
        // Utility class
    }

    public static String getMessage(String code) {
        return getMessage(code, code);
    }

    public static String getMessage(String code, String defaultMessage) {
        try {
            Locale locale = Context.getLocale();
            return Context.getMessageSourceService().getMessage(code, null, defaultMessage, locale);
        } catch (Exception e) {
            return defaultMessage;
        }
    }
}
