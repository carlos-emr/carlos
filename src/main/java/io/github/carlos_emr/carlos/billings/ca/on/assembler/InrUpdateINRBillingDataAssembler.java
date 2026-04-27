/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.MyDateFormat;
import io.github.carlos_emr.carlos.billings.ca.on.data.InrUpdateINRBillingViewModel;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Assembles the {@link InrUpdateINRBillingViewModel} from request
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
@org.springframework.context.annotation.Lazy
public class InrUpdateINRBillingDataAssembler {

    private final DemographicDao demographicDao;

    /** Production constructor — resolves the DAO from the Spring context. */
    public InrUpdateINRBillingDataAssembler() {
        this(SpringUtils.getBean(DemographicDao.class));
    }

    /** Test-friendly constructor — takes the DAO mock directly. */
    InrUpdateINRBillingDataAssembler(DemographicDao demographicDao) {
        this.demographicDao = demographicDao;
    }

    public InrUpdateINRBillingViewModel assemble(HttpServletRequest request) {
        String demoNo = request.getParameter("demono");
        String demoName = request.getParameter("demo_name");
        String billingInrNo = request.getParameter("billinginr_no");
        String serviceCode = request.getParameter("servicecode");
        String dxCode = request.getParameter("dxcode");

        String demoHin = "";
        String demoDob = "";
        if (demoNo != null && !demoNo.isEmpty()) {
            try {
                Demographic d = demographicDao.getDemographicById(Integer.parseInt(demoNo));
                if (d != null) {
                    demoDob = MyDateFormat.getStandardDate(
                            Integer.parseInt(d.getYearOfBirth()),
                            Integer.parseInt(d.getMonthOfBirth()),
                            Integer.parseInt(d.getDateOfBirth()));
                    String ver = d.getVer() == null ? "" : d.getVer().toUpperCase();
                    demoHin = (d.getHin() == null ? "" : d.getHin()) + ver;
                }
            } catch (NumberFormatException ignore) {
                // demono was non-numeric — leave HIN/DOB blank.
            }
        }

        return InrUpdateINRBillingViewModel.builder()
                .demoNo(demoNo)
                .billingInrNo(billingInrNo)
                .demoName(demoName)
                .demoHin(demoHin)
                .demoDob(demoDob)
                .serviceCode(serviceCode)
                .dxCode(dxCode)
                .build();
    }
}
