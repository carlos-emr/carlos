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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Additional explicit privilege for CDS results that the source marked as blocked. Default denies. */
@Service
public class CdsLaboratoryReadService {
    private final SecurityInfoManager security;
    @PersistenceContext(unitName="entityManagerFactory") private EntityManager entityManager;
    public CdsLaboratoryReadService(SecurityInfoManager security) { this.security=security; }

    @Transactional(readOnly=true)
    public void requireSourceFieldsReadable(LoggedInInfo actor, int demographicNo) {
        if (actor == null || actor.getLoggedInSecurity() == null
                || !security.hasPrivilegeReadOnly(actor, "_lab", "r", Integer.toString(demographicNo))
                || !security.isAllowedAccessToPatientRecord(actor, demographicNo)) {
            throw new SecurityException("Laboratory access denied");
        }
        Long blocked=entityManager.createQuery("select count(l.id) from CdsLaboratoryResult l "
                + "where l.demographicNo=:patient and l.sourceBlocked='Y'", Long.class)
                .setParameter("patient", demographicNo).getSingleResult();
        if (blocked>0 && !security.hasPrivilegeReadOnly(actor, "_lab_sensitive", "r", Integer.toString(demographicNo))) {
            throw new SecurityException("Restricted laboratory access requires explicit authorization");
        }
    }
}
