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
package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

import java.util.Date;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.commn.dao.DxresearchDAO;
import io.github.carlos_emr.carlos.commn.model.Dxresearch;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Optional clinical write triggered from {@code billing/CA/ON/ViewBillingONReview}:
 * if the user checked "add this dx to the patient's disease registry", insert
 * a {@link Dxresearch} row attributed to the logged-in provider before the
 * review-page assembler runs.
 *
 * <p>Lives outside {@link BillingONReviewDataAssembler} so the assembler
 * remains a pure read of request state into a view model. Callers must run
 * {@link #persistIfRequested} <em>before</em> assembling the view model so
 * any failure (audit-trail violation, malformed input) propagates through
 * the action's standard error path rather than rendering a misleadingly
 * "successful" review page.</p>
 *
 * @since 2026-04-25
 */
public final class BillingONReviewDxPersister {

    private final DxresearchDAO dxresearchDAO;

    public BillingONReviewDxPersister() {
        this(SpringUtils.getBean(DxresearchDAO.class));
    }

    BillingONReviewDxPersister(DxresearchDAO dxresearchDAO) {
        this.dxresearchDAO = dxresearchDAO;
    }

    /**
     * Inserts a {@link Dxresearch} row when the request opted in via the
     * {@code addToPatientDx=yes} hidden field. No-op for any other request
     * shape. Throws {@link IllegalArgumentException} if the request opted in
     * but {@code demographic_no} is non-numeric — silent drops on a clinical
     * write are an audit-trail gap.
     *
     * @param request the current request
     * @param userNo  the logged-in provider number to attribute the insert to
     */
    public void persistIfRequested(HttpServletRequest request, String userNo) {
        if (!"yes".equals(request.getParameter("addToPatientDx"))) {
            return;
        }
        String demoNo = nullToEmpty(request.getParameter("demographic_no"));
        if (demoNo.isEmpty()) {
            return;
        }
        String dxCode = nullToEmpty(request.getParameter("dxCode"));
        String dxCodeMatch = nullToEmpty(request.getParameter("codeMatchToPatientDx"));
        String dxCodeAdd = dxCodeMatch.isEmpty() ? dxCode : dxCodeMatch;
        if (dxCodeAdd.isEmpty()) {
            return;
        }

        Integer demoNoInt;
        try {
            demoNoInt = Integer.valueOf(demoNo);
        } catch (NumberFormatException nfe) {
            String sanitized = LogSanitizer.sanitize(demoNo);
            MiscUtils.getLogger().error(
                    "addToPatientDx requested with non-numeric demographic_no: {}", sanitized);
            throw new IllegalArgumentException(
                    "addToPatientDx requested with non-numeric demographic_no: " + sanitized, nfe);
        }
        Date now = new Date();
        Dxresearch dx = new Dxresearch(
                demoNoInt,
                now,
                now,
                'A',
                dxCodeAdd,
                "icd9",
                (byte) 0,
                userNo);
        dxresearchDAO.save(dx);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
