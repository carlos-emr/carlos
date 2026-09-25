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
package io.github.carlos_emr.carlos.prescript.pageUtil;

import java.util.List;
import java.util.Objects;

import io.github.carlos_emr.carlos.commn.dao.PrescriptionDao;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/** A request-local saved prescription, independent of mutable session staging/reprint state. */
public record RxPreviewSnapshot(RxSessionBean bean, String scriptId, String comment) {
    public static final String REQUEST_ATTRIBUTE = RxPreviewSnapshot.class.getName();

    /**
     * Loads exactly the saved prescription requested for an already authorised patient.
     * Invalid identifiers are refused, and missing/foreign prescriptions return null without
     * reading their drugs or notes. This method never registers or changes a session workspace.
     */
    public static RxPreviewSnapshot load(int demographicNo, String scriptId) {
        if (scriptId == null || !scriptId.matches("[0-9]{1,10}")) {
            throw new IllegalArgumentException("Invalid prescription identifier");
        }
        int id;
        try {
            id = Integer.parseInt(scriptId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid prescription identifier");
        }
        if (id <= 0 || demographicNo <= 0) {
            throw new IllegalArgumentException("Invalid prescription identifier");
        }
        Prescription header = SpringUtils.getBean(PrescriptionDao.class).find(id);
        if (header == null || !Objects.equals(header.getDemographicId(), demographicNo)) {
            return null;
        }
        List<RxPrescriptionData.Prescription> drugs = new RxPrescriptionData()
                .getPrescriptionsByScriptNo(id, demographicNo);
        if (drugs.isEmpty()) {
            return null;
        }
        RxSessionBean display = new RxSessionBean();
        display.setDemographicNo(demographicNo);
        display.setProviderNo(header.getProviderNo());
        // Preserve every saved row, including duplicate drugs; staging's addStashItem dedupes
        // and loads interaction warnings, neither of which belongs in a read-only print view.
        display.getStashList().addAll(drugs);
        return new RxPreviewSnapshot(display, Integer.toString(id), header.getComments());
    }
}
