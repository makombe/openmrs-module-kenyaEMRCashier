package org.openmrs.module.kenyaemr.cashier.api.util;

import org.openmrs.api.context.Context;
import org.openmrs.module.kenyaemr.cashier.ModuleSettings;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

/**
 * Utility class for internationalized currency formatting
 */
public class CurrencyUtil {

    private static final String CURRENCY_SYMBOL_KEY = "openhmis.cashier.currency.symbol";
    private static final String CURRENCY_CODE_KEY = "openhmis.cashier.currency.code";
    private static final String CURRENCY_FORMAT_KEY = "openhmis.cashier.currency.format";
    private static final String DEFAULT_CURRENCY_SYMBOL = "Ksh";
    private static final String DEFAULT_CURRENCY_CODE = "KES";
    private static final String DEFAULT_CURRENCY_FORMAT = "#,##0.00";

    /**
     * Format a BigDecimal amount using the configured currency code and current locale.
     *
     * @param amount the amount to format
     * @return formatted currency string
     */
    public static String formatCurrency(BigDecimal amount) {
        return formatCurrency(amount, getCurrencyCode(), getLocale());
    }

    /**
     * Format a BigDecimal amount using the configured currency code and current locale.
     *
     * @param amount the amount to format
     * @return formatted currency string
     */
    public static String formatCurrency(double amount) {
        return formatCurrency(BigDecimal.valueOf(amount));
    }

    /**
     * Format a BigDecimal amount using an explicit currency code and current locale.
     *
     * @param amount the amount to format
     * @param currencyCode the currency code (e.g. KES, UGS, CDF)
     * @return formatted currency string
     */
    public static String formatCurrency(BigDecimal amount, String currencyCode) {
        return formatCurrency(amount, currencyCode, getLocale());
    }

    /**
     * Format a BigDecimal amount using an explicit locale.
     *
     * @param amount the amount to format
     * @param locale the locale to use for number formatting
     * @return formatted currency string
     */
    public static String formatCurrency(BigDecimal amount, Locale locale) {
        return formatCurrency(amount, getCurrencyCode(), locale);
    }

    /**
     * Format a BigDecimal amount using a currency code and locale.
     *
     * @param amount the amount to format
     * @param currencyCode the currency code to use
     * @param locale the locale to use for formatting
     * @return formatted currency string
     */
    public static String formatCurrency(BigDecimal amount, String currencyCode, Locale locale) {
        if (amount == null) {
            String symbol = getCurrencySymbol();
            return symbol + " " + new DecimalFormat(getCurrencyFormat()).format(0);
        }

        try {
            Locale activeLocale = locale != null ? locale : getLocale();
            NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(activeLocale);
            if (currencyCode != null && !currencyCode.trim().isEmpty()) {
                currencyFormatter.setCurrency(Currency.getInstance(currencyCode.trim()));
            }
            return currencyFormatter.format(amount);
        } catch (Exception e) {
            String symbol = getCurrencySymbol();
            String format = getCurrencyFormat();
            DecimalFormat decimalFormat = new DecimalFormat(format);
            return symbol + " " + decimalFormat.format(amount);
        }
    }

    /**
     * Get the currency symbol from internationalization
     *
     * @return the currency symbol
     */
    public static String getCurrencySymbol() {
        try {
            String symbol = Context.getMessageSourceService().getMessage(CURRENCY_SYMBOL_KEY);
            if (symbol != null && !symbol.isEmpty()) {
                return symbol;
            }
        } catch (Exception ignored) {
            // Fallback if message source is unavailable
        }

        String symbol = Context.getAdministrationService().getGlobalProperty(ModuleSettings.DEFAULT_CURRENCY_SYMBOL);
        return symbol != null && !symbol.isEmpty() ? symbol : DEFAULT_CURRENCY_SYMBOL;
    }

    /**
     * Get the currency format pattern from internationalization
     *
     * @return the currency format pattern
     */
    public static String getCurrencyFormat() {
        try {
            String format = Context.getMessageSourceService().getMessage(CURRENCY_FORMAT_KEY);
            return format != null && !format.isEmpty() ? format : DEFAULT_CURRENCY_FORMAT;
        } catch (Exception e) {
            // Fallback to default if message source is not available
            return DEFAULT_CURRENCY_FORMAT;
        }
    }

    /**
     * Get the currency code from internationalization.
     *
     * @return currency code
     */
    public static String getCurrencyCode() {
        try {
            String code = Context.getMessageSourceService().getMessage(CURRENCY_CODE_KEY);
            return code != null && !code.isEmpty() ? code : DEFAULT_CURRENCY_CODE;
        } catch (Exception e) {
            return DEFAULT_CURRENCY_CODE;
        }
    }

    private static Locale getLocale() {
        try {
            return Context.getLocale();
        } catch (Exception e) {
            return Locale.getDefault();
        }
    }

    /**
     * Get a DecimalFormat instance configured with the internationalized format
     *
     * @return DecimalFormat instance
     */
    public static DecimalFormat getCurrencyDecimalFormat() {
        return new DecimalFormat(getCurrencyFormat());
    }

    /**
     * Format currency using system locale (alternative method)
     *
     * @param amount the amount to format
     * @param locale the locale to use for formatting
     * @return formatted currency string
     */
    public static String formatCurrencyWithLocale(BigDecimal amount, Locale locale) {
        return formatCurrency(amount, getCurrencyCode(), locale);
    }
} 