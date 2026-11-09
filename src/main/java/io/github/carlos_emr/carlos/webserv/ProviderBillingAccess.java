/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/** Shared disclosure rule for the four billing identifiers; provider visibility checks still apply. */
public final class ProviderBillingAccess {
    private ProviderBillingAccess() {}

    public static boolean canRead(LoggedInInfo actor) {
        return actor != null && canRead(actor, SpringUtils.getBean(SecurityInfoManager.class));
    }

    public static boolean canRead(LoggedInInfo actor, SecurityInfoManager permissions) {
        return actor != null && permissions.hasPrivilege(actor, "_billing", "r", null);
    }
}
