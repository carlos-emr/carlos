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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import jakarta.jws.WebParam;
import jakarta.jws.WebService;

import org.apache.cxf.annotations.GZIP;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.managers.PrescriptionManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.transfer_objects.PrescriptionTransfer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@WebService(targetNamespace = "http://ws.oscarehr.org/")
@Component
@GZIP(threshold = AbstractWs.GZIP_THRESHOLD)
public class PrescriptionWs extends AbstractWs {
    private static final String RX_OBJECT = "_rx";

    @Autowired
    private PrescriptionManager prescriptionManager;

    /**
     * The coarse {@code _rx r} check runs at method entry, before the record is loaded.
     *
     * <p>{@code PrescriptionManagerImpl.getPrescription} performs no privilege check of its own, so
     * without this an unprivileged caller reached a PHI-bearing load, and a missing prescription
     * returned null having been checked not at all -- the difference between a SOAP fault and a
     * null answer enumerated valid prescription IDs. The demographic-scoped re-check below still
     * runs once the record is in hand, because the patient scope is not knowable before the load.</p>
     */
    public PrescriptionTransfer getPrescription(Integer prescriptionId) {
        requirePrivilege(RX_OBJECT, "r");
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        Prescription prescription = prescriptionManager.getPrescription(loggedInInfo, prescriptionId);

        if (prescription != null) {
            requirePrivilege(RX_OBJECT, "r", prescription.getDemographicId() != null ? prescription.getDemographicId().toString() : null);
            List<Drug> drugs = prescriptionManager.getDrugsByScriptNo(loggedInInfo, prescription.getId(), false);
            return (PrescriptionTransfer.toTransfer(prescription, drugs));
        }

        return (null);
    }

    /**
     * Bulk sync, filtered per patient.
     *
     * <p>The manager applies consent filtering only, and {@code getTransfers} then returns every
     * prescription's drug detail, so without this a {@code _rx$<id>} denial was bypassed for the
     * restricted patient.</p>
     */
    public PrescriptionTransfer[] getPrescriptionUpdatedAfterDate(Date updatedAfterThisDateExclusive, int itemsToReturn) {
        requirePrivilege(RX_OBJECT, "r");
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        List<Prescription> prescriptions = prescriptionManager.getPrescriptionUpdatedAfterDate(loggedInInfo, updatedAfterThisDateExclusive, itemsToReturn);
        return (PrescriptionTransfer.getTransfers(loggedInInfo, filterReadablePrescriptions(prescriptions)));
    }

    /**
     * Drops prescriptions whose patient the caller may not read. Filtering rather than throwing, so
     * a restricted patient's presence in the window is not revealed by a fault.
     */
    private List<Prescription> filterReadablePrescriptions(List<Prescription> prescriptions) {
        List<Prescription> readable = new ArrayList<Prescription>();
        if (prescriptions == null) {
            return readable;
        }
        for (Prescription prescription : prescriptions) {
            if (prescription != null && hasPrivilege(RX_OBJECT, "r", prescription.getDemographicId())) {
                readable.add(prescription);
            }
        }
        return readable;
    }

    public PrescriptionTransfer[] getPrescriptionsByProgramProviderDemographicDate(Integer programId, String providerNo, Integer demographicId, Calendar updatedAfterThisDateExclusive, int itemsToReturn) {
        requirePrivilege(RX_OBJECT, "r", demographicId != null ? demographicId.toString() : null);
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        List<Prescription> prescriptions = prescriptionManager.getPrescriptionsByProgramProviderDemographicDate(loggedInInfo, programId, providerNo, demographicId, updatedAfterThisDateExclusive, itemsToReturn);
        return (PrescriptionTransfer.getTransfers(loggedInInfo, prescriptions));
    }

    public PrescriptionTransfer[] getPrescriptionsByDemographicIdAfter(@WebParam(name = "lastUpdate") Calendar lastUpdate, @WebParam(name = "demographicId") Integer demographicId) {
        requirePrivilege(RX_OBJECT, "r", demographicId != null ? demographicId.toString() : null);
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        List<Prescription> prescriptions = prescriptionManager.getPrescriptionByDemographicIdUpdatedAfterDate(loggedInInfo, demographicId, lastUpdate.getTime());
        return (PrescriptionTransfer.getTransfers(loggedInInfo, prescriptions));
    }

}
