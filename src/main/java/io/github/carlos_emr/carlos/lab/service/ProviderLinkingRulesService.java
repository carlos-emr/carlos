/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 *
 * Provider linking rules were first implemented by Deval Italiya in
 * open-osp/Open-O pull request #196 (GPL); this CARLOS implementation is
 * adapted from that work.
 */
package io.github.carlos_emr.carlos.lab.service;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Reads and changes the clinic-wide "Provider Linking Rules" switch.
 *
 * <p>When the switch is on, an HL7 lab or HRM report that is matched to a patient is also routed
 * to that patient's Most Responsible Provider (MRP, {@code demographic.provider_no}); see
 * {@link MrpRoutingService}. The value lives in one global {@code property} row named
 * {@code provider_linking_rules}. No row, or any value other than {@code true}, means off, so a
 * fresh or upgraded database needs no seed data.</p>
 *
 * <p>Reading is deliberately unprivileged: the routing decision is taken inside lab upload and
 * HRM matching, where the acting user may hold {@code _lab} or {@code _hrm} but not
 * {@code _admin}, and an automatic import has no user at all. The routing outcome must not depend
 * on who happens to trigger it. Changing the switch widens who sees results clinic-wide, so it
 * requires {@code _admin} write and is audited.</p>
 *
 * @since 2026-09-26
 */
@Service
public class ProviderLinkingRulesService {

    /** Audit {@code content} value for a change of the switch. */
    public static final String AUDIT_CONTENT = "providerLinkingRules";

    private final PropertyDao propertyDao;
    private final SecurityInfoManager securityInfoManager;

    public ProviderLinkingRulesService(PropertyDao propertyDao, SecurityInfoManager securityInfoManager) {
        this.propertyDao = propertyDao;
        this.securityInfoManager = securityInfoManager;
    }

    /**
     * @return {@code true} only when the global {@code provider_linking_rules} row holds {@code true}
     */
    @Transactional(readOnly = true)
    public boolean isEnabled() {
        List<Property> rows = findGlobalRows();
        return !rows.isEmpty() && "true".equals(StringUtils.trimToEmpty(rows.get(0).getValue()));
    }

    /**
     * Turns the switch on or off, creating the global row on first use.
     *
     * @param loggedInInfo the administrator making the change
     * @param enabled the new state
     * @return the state now stored
     * @throws SecurityException when the user lacks {@code _admin} write
     */
    @Transactional
    public boolean setEnabled(LoggedInInfo loggedInInfo, boolean enabled) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        String value = Boolean.toString(enabled);
        List<Property> rows = findGlobalRows();
        if (rows.isEmpty()) {
            Property property = new Property();
            property.setName(Property.PROPERTY_KEY.provider_linking_rules.name());
            property.setValue(value);
            propertyDao.persist(property);
        } else {
            // Update every global row, not just the first, so a duplicate left by a hand edit
            // cannot make isEnabled() disagree with what the administrator just saved.
            for (Property property : rows) {
                property.setValue(value);
                propertyDao.merge(property);
            }
        }

        LogAction.addLog(loggedInInfo, LogConst.UPDATE, AUDIT_CONTENT, null, null, "enabled=" + value);
        return enabled;
    }

    /**
     * Global rows only. The {@code property.provider_no} column defaults to the empty string, so
     * a row inserted by SQL without a provider is as global as one JPA wrote with NULL; a
     * provider-scoped row of the same name is never read as the clinic setting.
     */
    private List<Property> findGlobalRows() {
        return propertyDao.findByName(Property.PROPERTY_KEY.provider_linking_rules.name()).stream()
                .filter(p -> StringUtils.isBlank(p.getProviderNo()))
                .toList();
    }
}
