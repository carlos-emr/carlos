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
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.time.LocalDate;
import java.util.Set;
import java.util.function.Supplier;

/** Administrator-only historical reader; credentials remain in the CARLOS web process. */
public final class PortalEmailFooterAuditService {
    private final PortalStaffContextResolver resolver;
    private final Supplier<PatientPortalService> portal;

    public PortalEmailFooterAuditService(SecurityInfoManager security) {
        this(security, () -> SpringUtils.getBean(PatientPortalService.class));
    }

    PortalEmailFooterAuditService(SecurityInfoManager security, Supplier<PatientPortalService> portal) {
        this.resolver = new PortalStaffContextResolver(security);
        this.portal = portal;
    }

    public PortalEmailFooterAuditPage read(LoggedInInfo user, LocalDate date, String before) {
        // Authorization precedes client/settings lookup and signed request construction.
        PatientPortalStaffContext staff = resolver.resolve(user,
                Set.of(PortalStaffContextResolver.OBJECT_EMAIL_AUDIT));
        return portal.get().listEmailFooterAttempts(date, 50, before, staff);
    }
}
