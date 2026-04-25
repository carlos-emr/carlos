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

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Immutable view model for {@code billingONCorrection.jsp}.
 *
 * <p>Captures the user-context lookups (provider record, site/team-access flags,
 * provider-access list, multisite list) that the JSP top scriptlet currently
 * builds inline. Bill-record-specific data (the Ch1 invoice + items the
 * correction page actually edits) still flows through
 * {@link io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingCorrection2Action}'s
 * existing state machine and is not duplicated here.</p>
 *
 * <p>Populated by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingCorrection2Action#buildModel}
 * and exposed to the JSP as request attribute {@code correctionModel}. Fields
 * are added incrementally as scriptlet blocks migrate out of the JSP.</p>
 *
 * @since 2026-04-24
 */
public final class BillingONCorrectionViewModel {

    private final String userProviderNo;
    private final String userFirstName;
    private final String userLastName;

    private final boolean siteAccessPrivacy;
    private final boolean teamAccessPrivacy;
    private final boolean teamBillingOnly;
    private final boolean multisites;

    private final Set<String> providerAccessList;
    private final List<String> mgrSites;

    private BillingONCorrectionViewModel(Builder b) {
        this.userProviderNo = b.userProviderNo == null ? "" : b.userProviderNo;
        this.userFirstName = b.userFirstName == null ? "" : b.userFirstName;
        this.userLastName = b.userLastName == null ? "" : b.userLastName;
        this.siteAccessPrivacy = b.siteAccessPrivacy;
        this.teamAccessPrivacy = b.teamAccessPrivacy;
        this.teamBillingOnly = b.teamBillingOnly;
        this.multisites = b.multisites;
        this.providerAccessList = b.providerAccessList == null
                ? Collections.emptySet()
                : Set.copyOf(b.providerAccessList);
        this.mgrSites = b.mgrSites == null
                ? Collections.emptyList()
                : List.copyOf(b.mgrSites);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getUserProviderNo() { return userProviderNo; }
    public String getUserFirstName() { return userFirstName; }
    public String getUserLastName() { return userLastName; }
    public boolean isSiteAccessPrivacy() { return siteAccessPrivacy; }
    public boolean isTeamAccessPrivacy() { return teamAccessPrivacy; }
    public boolean isTeamBillingOnly() { return teamBillingOnly; }
    public boolean isMultisites() { return multisites; }
    public Set<String> getProviderAccessList() { return providerAccessList; }
    public List<String> getMgrSites() { return mgrSites; }

    public static final class Builder {
        private String userProviderNo;
        private String userFirstName;
        private String userLastName;
        private boolean siteAccessPrivacy;
        private boolean teamAccessPrivacy;
        private boolean teamBillingOnly;
        private boolean multisites;
        private Set<String> providerAccessList;
        private List<String> mgrSites;

        public Builder userProviderNo(String v) { this.userProviderNo = v; return this; }
        public Builder userFirstName(String v) { this.userFirstName = v; return this; }
        public Builder userLastName(String v) { this.userLastName = v; return this; }
        public Builder siteAccessPrivacy(boolean v) { this.siteAccessPrivacy = v; return this; }
        public Builder teamAccessPrivacy(boolean v) { this.teamAccessPrivacy = v; return this; }
        public Builder teamBillingOnly(boolean v) { this.teamBillingOnly = v; return this; }
        public Builder multisites(boolean v) { this.multisites = v; return this; }
        public Builder providerAccessList(Set<String> v) { this.providerAccessList = v; return this; }
        public Builder mgrSites(List<String> v) { this.mgrSites = v; return this; }

        public BillingONCorrectionViewModel build() {
            return new BillingONCorrectionViewModel(this);
        }
    }
}
