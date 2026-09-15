/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.webserv;

import java.util.List;

import jakarta.jws.WebService;

import org.apache.cxf.annotations.GZIP;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.managers.FacilityManager;
import io.github.carlos_emr.carlos.webserv.transfer_objects.FacilityTransfer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@WebService(targetNamespace = "http://ws.oscarehr.org/")
@Component
@GZIP(threshold = AbstractWs.GZIP_THRESHOLD)
public class FacilityWs extends AbstractWs {
    @Autowired
    private FacilityManager facilityManager;

    /**
     * @deprecated 2013-03-19 grammatical mistaken in name, this only returns 1 default facility, use getDefaultFacility() instead.
     */
    @Deprecated
    public FacilityTransfer getDefaultFacilities() {
        return (FacilityTransfer.toTransfer(facilityManager.getDefaultFacility(getLoggedInInfo())));
    }

    public FacilityTransfer getDefaultFacility() {
        return (FacilityTransfer.toTransfer(facilityManager.getDefaultFacility(getLoggedInInfo())));
    }

    /**
     * Requires {@code _admin r}.
     *
     * <p><b>Upgrade impact.</b> Both province migration sets grant {@code _admin} to the
     * {@code admin} role only (as {@code x}, which satisfies {@code r}). Integrator / inter-EMR
     * sync accounts provisioned with a provider-type role will start faulting here until an
     * operator grants them the object. See the upgrade checklist in
     * {@code docs/soap-rbac-hardening.md} before deploying.</p>
     *
     * <p>{@link #getDefaultFacility()} and its deprecated alias remain unguarded pending the
     * facility-access policy decision tracked as follow-up work.</p>
     */
    public FacilityTransfer[] getAllFacilities(Boolean active) {
        requirePrivilege("_admin", "r");
        List<Facility> results = facilityManager.getAllFacilities(getLoggedInInfo(), active);
        return (FacilityTransfer.toTransfers(results));
    }
}
