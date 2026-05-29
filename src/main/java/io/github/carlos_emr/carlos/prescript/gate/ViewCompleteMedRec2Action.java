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
package io.github.carlos_emr.carlos.prescript.gate;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBean;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Mutation gate for {@code rx/completeMedRec.jsp}. Enforces {@code _measurement} w
 * privilege AND POST-only before forwarding to the JSP. GET returns 405.
 *
 * @since 2026-04-13
 */
public final class ViewCompleteMedRec2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private MeasurementDao measurementDao = SpringUtils.getBean(MeasurementDao.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_measurement", "w", null)) {
            throw new SecurityException("missing required sec object (_measurement)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        Integer demographicNo = parsePositiveInteger(request.getParameter("demographicNo"));
        if (demographicNo == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        if (!belongsToSessionPatient(request, demographicNo)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return NONE;
        }

        Measurement measurement = new Measurement();
        measurement.setComments("");
        measurement.setDataField("Yes");
        measurement.setCreateDate(new java.util.Date());
        measurement.setDateObserved(new java.util.Date());
        measurement.setDemographicId(demographicNo);
        measurement.setMeasuringInstruction("");
        measurement.setProviderNo(loggedInInfo.getLoggedInProviderNo());
        measurement.setType("medr");
        measurement.setAppointmentNo(0);
        measurementDao.persist(measurement);

        return SUCCESS;
    }

    private static boolean belongsToSessionPatient(HttpServletRequest request, int demographicNo) {
        Object sessionBean = request.getSession().getAttribute("RxSessionBean");
        return sessionBean instanceof RxSessionBean rxSessionBean
                && demographicNo == rxSessionBean.getDemographicNo();
    }

    private static Integer parsePositiveInteger(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return null;
        }
        try {
            int parsedValue = Integer.parseInt(rawValue.trim());
            return parsedValue > 0 ? parsedValue : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
