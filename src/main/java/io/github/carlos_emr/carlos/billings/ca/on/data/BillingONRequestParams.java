/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.data;

/**
 * Small parsing helpers shared by the Ontario billing assemblers
 * ({@link BillingONFormDataAssembler}, {@link BillingONReviewDataAssembler},
 * and the shortcut variant). Extracted so the same parsing isn't
 * reimplemented inconsistently — passing the unstripped
 * {@code "providerNo|ohipNo"} pair to {@code ProviderDao.getProvider(...)}
 * silently returns null.
 *
 * @since 2026-04-25
 */
public final class BillingONRequestParams {

    private BillingONRequestParams() {
        // utility
    }

    /**
     * Extracts a provider number from the {@code xml_provider} /
     * {@code providerview} pair posted by {@code billingON.jsp}.
     *
     * <p>{@code xml_provider} carries a {@code "providerNo|ohipNo"} pair when
     * the provider was selected from the picker; the {@code providerview}
     * fallback is the older single-value param. Either one may be {@code null}
     * or empty. The returned string is the provider number alone (everything
     * before the {@code |}, or the whole value if no pipe is present), and is
     * never {@code null} — empty string when both inputs are missing.</p>
     *
     * <p>Pure function; no side effects on the request.</p>
     */
    public static String extractProviderNo(String xmlProvider, String providerView) {
        String s = xmlProvider != null && !xmlProvider.isEmpty()
                ? xmlProvider
                : (providerView != null ? providerView : "");
        int pipe = s.indexOf('|');
        return pipe >= 0 ? s.substring(0, pipe) : s;
    }
}
