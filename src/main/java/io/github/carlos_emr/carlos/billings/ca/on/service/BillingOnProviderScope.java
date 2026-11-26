/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.billings.ca.on.dto.ProviderDropdownEntry;
import io.github.carlos_emr.carlos.commn.SiteChoice;
import io.github.carlos_emr.carlos.commn.dao.ProviderDataDao;
import io.github.carlos_emr.carlos.commn.model.ProviderData;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Provider selection and historical file access for the Ontario OHIP forms. */
public record BillingOnProviderScope(boolean teamBillingOnly, boolean siteAccessPrivacy, boolean teamAccessPrivacy) {

    public static BillingOnProviderScope forLogin(SecurityInfoManager security, LoggedInInfo login) {
        return new BillingOnProviderScope(
                login != null && security.hasPrivilege(login, "_team_billing_only", SecurityInfoManager.READ, null),
                SiteChoice.isSiteAccessRestricted(security, login),
                login != null && security.hasPrivilege(login, "_team_access_privacy", SecurityInfoManager.READ, null));
    }

    public boolean restricted() {
        return teamBillingOnly || siteAccessPrivacy || teamAccessPrivacy;
    }

    /** OSCAR's provider picker gives either team restriction precedence over the site restriction. */
    public List<ProviderDropdownEntry> selectableProviders(BillingReviewLoader reviews, String providerNo) {
        List<ProviderDropdownEntry> options;
        if (teamBillingOnly || teamAccessPrivacy) {
            options = reviews.getTeamProviderBillingStr(providerNo);
        } else if (siteAccessPrivacy) {
            options = reviews.getSiteProviderBillingStr(providerNo);
        } else {
            options = reviews.getProviderBillingStr();
        }
        return options == null ? List.of() : options;
    }

    /** Unrestricted submissions retain their established population and ordering. */
    public Optional<Set<String>> selectableProviderNumbers(BillingReviewLoader reviews, String providerNo) {
        if (!restricted()) return Optional.empty();
        return Optional.of(selectableProviders(reviews, providerNo).stream()
                .map(ProviderDropdownEntry::providerNo).collect(Collectors.toUnmodifiableSet()));
    }

    /**
     * An absent result means clinic-wide access. An empty set means the restricted login
     * has no matching providers. History includes inactive providers. Full team-access
     * privacy historically takes precedence, while billing-only access must still respect
     * an effective site restriction on previously generated files.
     */
    public Optional<Set<String>> historyProviderNumbers(ProviderDataDao providers, String providerNo) {
        if (!restricted()) {
            return Optional.empty();
        }
        if (teamAccessPrivacy || teamBillingOnly) {
            Set<String> team = providerNumbers(providers.findByProviderTeam(providerNo));
            if (teamBillingOnly && !teamAccessPrivacy && providerNo != null && !providerNo.isBlank()) {
                // A billing-only login may select its own provider even without a named team.
                team.add(providerNo);
            }
            if (siteAccessPrivacy && !teamAccessPrivacy) {
                team.retainAll(providerNumbers(providers.findByProviderSite(providerNo)));
            }
            return Optional.of(Set.copyOf(team));
        }
        return Optional.of(Set.copyOf(providerNumbers(providers.findByProviderSite(providerNo))));
    }

    private static Set<String> providerNumbers(List<ProviderData> providers) {
        return providers.stream().map(ProviderData::getId).collect(Collectors.toCollection(HashSet::new));
    }
}
