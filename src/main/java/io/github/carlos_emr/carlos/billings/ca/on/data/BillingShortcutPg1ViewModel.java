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
 * Immutable view model for {@code billingShortcutPg1.jsp} (the fast-track
 * billing entry, page 1).
 *
 * <p>Built by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingShortcutPg1View2Action}
 * and exposed to the JSP as request attribute {@code shortcutPg1Model}. The
 * shortcut form's prep logic mirrors {@code billingONReview.jsp}'s in shape
 * (demographic + provider lookups, same dx-description hint), so this DTO
 * intentionally tracks the same field set so a future commit can share an
 * assembler if cross-cutting refactors warrant it.</p>
 *
 * <p>This is the initial scaffold. Field set will grow as scriptlet blocks
 * migrate; for now it captures the user identity surface so the JSP top
 * scriptlet can read it without the {@code session.getAttribute("user")}
 * dance and the redundant null-redirect-to-logout.</p>
 *
 * @since 2026-04-24
 */
public final class BillingShortcutPg1ViewModel {

    private final String userProviderNo;
    private final String providerView;

    private BillingShortcutPg1ViewModel(Builder b) {
        this.userProviderNo = b.userProviderNo == null ? "" : b.userProviderNo;
        this.providerView = b.providerView == null ? "" : b.providerView;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getUserProviderNo() { return userProviderNo; }
    public String getProviderView() { return providerView; }

    public static final class Builder {
        private String userProviderNo;
        private String providerView;

        public Builder userProviderNo(String v) { this.userProviderNo = v; return this; }
        public Builder providerView(String v) { this.providerView = v; return this; }

        public BillingShortcutPg1ViewModel build() {
            return new BillingShortcutPg1ViewModel(this);
        }
    }
}
