/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.waitinglist.util;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.ProviderPreferenceDao;
import io.github.carlos_emr.carlos.commn.dao.WaitingListNameDao;
import io.github.carlos_emr.carlos.commn.model.ProviderPreference;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/** Checks current waiting-list availability at submission, independently of rendered/session state. */
public final class WaitingListAccess {
    private WaitingListAccess() { }

    /**
     * Reads the provider's current group and the active lists in that group.
     *
     * @param login authenticated provider context
     * @param submittedId submitted waiting-list identifier
     * @return whether the enabled list is currently available to this provider
     */
    public static boolean isAvailable(LoggedInInfo login, String submittedId) {
        if (login == null || login.getLoggedInProviderNo() == null || submittedId == null
                || !CarlosProperties.getInstance().getBooleanProperty("DEMOGRAPHIC_WAITING_LIST", "true")) {
            return false;
        }
        final int id;
        try {
            id = Integer.parseInt(submittedId.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (id <= 0) return false;
        ProviderPreference preference = SpringUtils.getBean(ProviderPreferenceDao.class)
                .find(login.getLoggedInProviderNo());
        if (preference == null || preference.getMyGroupNo() == null || preference.getMyGroupNo().isBlank()) {
            return false;
        }
        return SpringUtils.getBean(WaitingListNameDao.class).findCurrentByGroup(preference.getMyGroupNo())
                .stream().anyMatch(list -> Integer.valueOf(id).equals(list.getId()));
    }
}
