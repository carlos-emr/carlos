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
package io.github.carlos_emr.carlos.webserv.rest.conversion.summary;

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.managers.PrescriptionManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.SummaryItemTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.SummaryTo1;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.carlos_emr.carlos.util.StringUtils;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsLegacyProjectionService;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsMedicationCompatibilityException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsLegacyProjectionException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Set;


@Component
public class RxSummary implements Summary {

    private final PrescriptionManager rxManager;
    private final CdsLegacyProjectionService projection;

    @Autowired
    public RxSummary(PrescriptionManager rxManager, CdsLegacyProjectionService projection) {
        this.rxManager = java.util.Objects.requireNonNull(rxManager);
        this.projection = java.util.Objects.requireNonNull(projection);
    }

    protected static final String ELLIPSES = "...";
    protected static final int MAX_LEN_TITLE = 48;
    protected static final int CROP_LEN_TITLE = 45;
    protected static final int MAX_LEN_KEY = 12;
    protected static final int CROP_LEN_KEY = 9;

    public SummaryTo1 getSummary(LoggedInInfo loggedInInfo, Integer demographicNo, String summaryCode) {
        try { projection.requireSupported(demographicNo, Set.of("MedicationsAndTreatments")); }
        catch (CdsLegacyProjectionException unsupported) { throw importedMedicationUnsupported(demographicNo); }

        SummaryTo1 summary = new SummaryTo1("Meds", 0, SummaryTo1.MEDICATIONS_CODE);

        List<SummaryItemTo1> list = summary.getSummaryItem();
        int count = 0;

        fillRx(loggedInInfo, list, demographicNo, count);

        return summary;
    }

    private void fillRx(LoggedInInfo loggedInInfo, List<SummaryItemTo1> list, Integer demographicNo, int count) {

        List<Drug> drugList = rxManager.getUniqueDrugsByPatient(loggedInInfo, demographicNo);

        long now = System.currentTimeMillis();
        long month = 1000L * 60L * 60L * 24L * 30L;
        for (Drug drug : drugList) {
            // Defend the legacy Date-only shape even if a damaged chart has lost source metadata.
            try { CdsMedicationCompatibilityException.requireLegacyCompatible(drug); }
            catch (CdsMedicationCompatibilityException unsupported) {
                throw importedMedicationUnsupported(demographicNo);
            }
            if (drug.isArchived())
                continue;
            if (drug.isHideFromDrugProfile()) {
                continue;
            }

            String styleColor = "";
            if (drug.isCurrent() && drug.getEndDate() != null && (drug.getEndDate().getTime() - now <= month)) {
                styleColor = "style=\"color:orange;font-weight:bold;\"";
            } else if (drug.isCurrent()) {
                styleColor = "style=\"color:blue;\"";
            } else if (drug.isLongTerm()) {
                styleColor = "style=\"color:grey;\"";
            } else
                continue;

            String tmp = "";
            if (drug.getFullOutLine() != null) tmp = drug.getFullOutLine().replaceAll(";", " ");
            String strTitle = StringUtils.maxLenString(tmp, MAX_LEN_TITLE, CROP_LEN_TITLE, ELLIPSES);

            SummaryItemTo1 summaryItem = new SummaryItemTo1(drug.getId(), strTitle, "action", "rx");
            summaryItem.setDate(drug.getRxDate());
            summaryItem.setAction("../rx/choosePatient?demographicNo=" + demographicNo);  // for now, open the Rx module if the user clicks on any meds.

            list.add(summaryItem);
            count++;
        }
    }

    private static WebApplicationException importedMedicationUnsupported(Integer demographicNo) {
        return new WebApplicationException(Response.status(Response.Status.CONFLICT)
                .type(MediaType.TEXT_PLAIN_TYPE).entity(new CdsMedicationCompatibilityException().getMessage()
                        + " (/form/cdsSourceFields?demographicNo=" + demographicNo
                        + "&category=MedicationsAndTreatments)")
                .header("Cache-Control", "no-store").build());
    }
}
