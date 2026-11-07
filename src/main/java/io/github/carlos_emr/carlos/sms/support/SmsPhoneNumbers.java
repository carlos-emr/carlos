package io.github.carlos_emr.carlos.sms.support;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Normalizes recipient phone numbers to {@code +E.164}.
 * <p>
 * NANP-biased: when no country code is given (no leading {@code +}), the default country code is
 * {@code 1} and only 10-digit or {@code 1}-prefixed 11-digit numbers are accepted; other lengths are
 * rejected rather than guessed. International numbers are only accepted when supplied in {@code +E.164}
 * form (or with an explicit non-{@code 1} default country code). This matches the Canadian clinic
 * deployment; revisit the default country code if international SMS is ever required.
 */
public final class SmsPhoneNumbers {
    private static final int MIN_E164_DIGITS = 8;
    private static final int MAX_E164_DIGITS = 15;
    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");
    private static final String ALLOWED_SEPARATORS = "-(). ";

    private SmsPhoneNumbers() {
    }

    public static Optional<String> normalizeToE164(String rawPhoneNumber) {
        return normalizeToE164(rawPhoneNumber, "1");
    }

    public static Optional<String> normalizeToE164(String rawPhoneNumber, String defaultCountryCode) {
        if (rawPhoneNumber == null || rawPhoneNumber.trim().isEmpty()) {
            return Optional.empty();
        }

        ParsedPhone parsed = parse(rawPhoneNumber);
        if (parsed.invalid()) {
            return Optional.empty();
        }

        String digits = parsed.digits();
        if (digits.isEmpty()) {
            return Optional.empty();
        }

        if (parsed.leadingPlus()) {
            return validE164Digits(digits) ? Optional.of("+" + digits) : Optional.empty();
        }

        String countryCode = digitsOnly(defaultCountryCode);
        if (countryCode.isEmpty()) {
            countryCode = "1";
        }

        if ("1".equals(countryCode) && digits.length() == 10) {
            return Optional.of("+1" + digits);
        }

        if ("1".equals(countryCode) && digits.length() == 11 && digits.startsWith("1")) {
            return Optional.of("+" + digits);
        }

        if ("1".equals(countryCode)) {
            return Optional.empty();
        }

        String candidate = countryCode + digits;
        return validE164Digits(candidate) ? Optional.of("+" + candidate) : Optional.empty();
    }

    public static boolean isLikelyE164(String phoneNumber) {
        return phoneNumber != null && E164.matcher(phoneNumber).matches();
    }

    private static ParsedPhone parse(String rawPhoneNumber) {
        StringBuilder digits = new StringBuilder();
        boolean leadingPlus = false;
        boolean invalid = false;
        String trimmed = rawPhoneNumber.trim();

        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (i == 0 && c == '+') {
                leadingPlus = true;
            } else if (isAsciiDigit(c)) {
                digits.append(c);
            } else if (Character.isLetter(c) || !isAllowedSeparator(c)) {
                invalid = true;
            }
        }

        return new ParsedPhone(leadingPlus, digits.toString(), invalid);
    }

    private static boolean validE164Digits(String digits) {
        return digits.length() >= MIN_E164_DIGITS
                && digits.length() <= MAX_E164_DIGITS
                && digits.charAt(0) != '0';
    }

    private static String digitsOnly(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (isAsciiDigit(c)) {
                digits.append(c);
            }
        }
        return digits.toString();
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isAllowedSeparator(char c) {
        return ALLOWED_SEPARATORS.indexOf(c) >= 0;
    }

    private record ParsedPhone(boolean leadingPlus, String digits, boolean invalid) {
    }
}
