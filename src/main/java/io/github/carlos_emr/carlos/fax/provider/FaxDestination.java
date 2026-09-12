/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
package io.github.carlos_emr.carlos.fax.provider;

import io.github.carlos_emr.carlos.commn.model.FaxConfig;

/** Provider-aware validation before files are published or fax jobs are queued. */
public final class FaxDestination {
    private FaxDestination() {}

    /**
     * Returns a compact queue value without losing the international dialing signal.
     * SRFax validation delegates to its transmission normalizer, but expansion to 011 is
     * deferred until transmission so a long international number is not normalized twice.
     * @param rawNumber destination supplied by the user
     * @param providerType selected active account's provider type; null means legacy middleware
     * @return digits, prefixed with '+' for an explicitly international SRFax destination
     * @throws FaxProviderException if the destination fails the selected provider's rules
     */
    public static String forQueue(String rawNumber, FaxConfig.ProviderType providerType) throws FaxProviderException {
        String raw = rawNumber == null ? "" : rawNumber.trim();
        String digits = raw.replaceAll("\\D", "");
        if (providerType == FaxConfig.ProviderType.SRFAX) {
            String dialable = SRFaxProviderClient.normalizeDestinationNumber(raw);
            // Canonical queue values also deduplicate 10-digit/+1 NANP numbers and
            // equivalent +CC/011CC international recipients before any job is persisted.
            return dialable.startsWith("011") ? "+" + dialable.substring(3) : dialable;
        }
        if (digits.length() < 7 || digits.length() > 11) {
            throw new FaxProviderException("The legacy destination must contain seven to eleven digits.");
        }
        return digits;
    }
}
