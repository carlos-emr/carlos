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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.MyDateFormat;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.InrBillingUpdateViewModel;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Assembles the {@link InrBillingUpdateViewModel} from request
 * parameters + the {@link DemographicDao} lookup that the legacy
 * {@code updateINRbilling.jsp} did inline as a scriptlet.
 *
 * <p>Pulls {@code demono} from the request, fetches the matching
 * {@link Demographic}, and formats the DOB and HIN as the JSP did
 * (HIN gets the version code uppercased and concatenated; DOB is
 * formatted via {@link MyDateFormat#getStandardDate}). All other
 * fields echo straight through from request parameters.</p>
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
public class InrBillingUpdateViewModelAssembler {

    private final DemographicDao demographicDao;

    /** Production constructor — resolves the DAO from the Spring context. */
    /** Test-friendly constructor — takes the DAO mock directly. */
    public InrBillingUpdateViewModelAssembler(DemographicDao demographicDao) {
        this.demographicDao = demographicDao;
    }

    public InrBillingUpdateViewModel assemble(HttpServletRequest request) {
        String demoNo = request.getParameter("demono");
        String demoName = request.getParameter("demo_name");
        String billingInrNo = request.getParameter("billinginr_no");
        String serviceCode = request.getParameter("servicecode");
        String dxCode = request.getParameter("dxcode");

        String demoHin = "";
        String demoDob = "";
        if (demoNo != null && !demoNo.isEmpty()) {
            try {
                int parsedDemoNo = parseInteger(demoNo, "demono");
                Demographic d = demographicDao.getDemographicById(parsedDemoNo);
                if (d != null) {
                    demoDob = MyDateFormat.getStandardDate(
                            parseInteger(d.getYearOfBirth(), "yearOfBirth"),
                            parseInteger(d.getMonthOfBirth(), "monthOfBirth"),
                            parseInteger(d.getDateOfBirth(), "dateOfBirth"));
                    String ver = d.getVer() == null ? "" : d.getVer().toUpperCase();
                    demoHin = (d.getHin() == null ? "" : d.getHin()) + ver;
                }
            } catch (NumberFormatException nfe) {
                MiscUtils.getLogger().warn("INR billing update: {}; leaving HIN/DOB blank", nfe.getMessage());
            }
        }

        return InrBillingUpdateViewModel.builder()
                .demoNo(demoNo)
                .billingInrNo(billingInrNo)
                .demoName(demoName)
                .demoHin(demoHin)
                .demoDob(demoDob)
                .serviceCode(serviceCode)
                .dxCode(dxCode)
                .build();
    }

    private static int parseInteger(String raw, String fieldName) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException nfe) {
            throw new NumberFormatException("invalid " + fieldName + " ["
                    + LogSafe.sanitize(raw) + "]");
        }
    }
}
