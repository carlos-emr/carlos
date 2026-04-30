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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingEdtObecOutputSpecificationRecordDto;
import io.github.carlos_emr.carlos.commn.dao.BatchEligibilityDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.commn.model.BatchEligibility;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicCust;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Applies a parsed OBEC output-specification report to the demographic
 * graph atomically: per row, flips matching demographics' {@code ver}
 * to {@code "##"} (HIN-flagged-invalid) and appends a reason note to
 * the patient's {@code DemographicCust.alert} string.
 *
 * <p>Pre-fix this loop ran inline in
 * {@link io.github.carlos_emr.carlos.billings.ca.on.web.BillingDocumentErrorReportUpload2Action#generateReportR}
 * with no transactional boundary. A mid-loop failure (DAO timeout,
 * concurrent edit, NPE on a malformed row) left half the patients in the
 * file with HIN flagged invalid (ver={@code "##"}) and the other half
 * untouched — corrupting eligibility data with no operator signal. The
 * action saw {@code verdict=true} from the parser regardless.</p>
 *
 * <p>Lifted into a {@code @Transactional} service so a mid-loop throw
 * rolls back every prior {@code setVer("##")} + alert append.</p>
 *
 * @since 2026-04-30
 */
@Service
@Transactional
public class BillingObecOutputApplyService {

    private final BatchEligibilityDao batchEligibilityDao;
    private final DemographicCustDao demographicCustDao;
    private final DemographicManager demographicManager;

    public BillingObecOutputApplyService(BatchEligibilityDao batchEligibilityDao,
                                         DemographicCustDao demographicCustDao,
                                         DemographicManager demographicManager) {
        this.batchEligibilityDao = batchEligibilityDao;
        this.demographicCustDao = demographicCustDao;
        this.demographicManager = demographicManager;
    }

    /**
     * Atomically apply the parsed OBEC output-spec records. Any throw
     * (DAO failure, concurrent edit, malformed row that isn't otherwise
     * skip-able) rolls back the entire batch.
     */
    public void applyOutputSpec(LoggedInInfo loggedInInfo,
                                List<BillingEdtObecOutputSpecificationRecordDto> outputSpecVector) {
        for (BillingEdtObecOutputSpecificationRecordDto bean : outputSpecVector) {
            String hin = bean.getHealthNo();
            String responseCode = bean.getResponseCode();
            int responseCodeNum;
            try {
                responseCodeNum = Integer.parseInt(responseCode);
            } catch (NumberFormatException e) {
                // Skip unparseable rows: the legacy code fell through to a
                // second unguarded Integer.parseInt(responseCode) which
                // would crash the whole batch. Skipping here keeps the
                // rest of the file intact; the operator sees the malformed
                // row in the server log.
                MiscUtils.getLogger().warn(
                        "Skipping OBEC output-spec row with unparseable response code {} for hin {}",
                        LogSanitizer.sanitize(responseCode),
                        LogSanitizer.sanitize(hin), e);
                continue;
            }

            if (responseCodeNum < 50 || responseCodeNum > 59) {
                BatchEligibility batchEligibility = batchEligibilityDao.find(responseCodeNum);
                List<Demographic> ds = demographicManager.searchByHealthCard(loggedInInfo, hin);

                if (!ds.isEmpty()) {
                    Demographic d = ds.get(0);
                    if (d.getVer().trim().compareTo(bean.getVersion().trim()) == 0) {
                        for (Demographic demographic : ds) {
                            demographic.setVer("##");
                            demographicManager.updateDemographic(loggedInInfo, demographic);
                        }
                        DemographicCust demographicCust = demographicCustDao.find(d.getDemographicNo());
                        if (demographicCust != null && batchEligibility != null) {
                            String newAlert = demographicCust.getAlert() + "\n" + "Invalid old version code: "
                                    + bean.getVersion() + "\nReason: " + batchEligibility.getMOHResponse() + "- "
                                    + batchEligibility.getReason() + "\nResponse Code: " + responseCode;
                            demographicCust.setAlert(newAlert);
                            demographicCustDao.merge(demographicCust);
                        }
                    }
                }
            }
        }
    }
}
