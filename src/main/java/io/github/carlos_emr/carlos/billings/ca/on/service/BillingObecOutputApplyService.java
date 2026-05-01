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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.util.ArrayList;
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
 * <p>{@code @Transactional} so a mid-loop failure (DAO timeout,
 * concurrent edit, NPE on a malformed row) rolls back every prior
 * {@code setVer("##")} + alert append rather than leaving half the
 * patients in the file with HIN flagged invalid and the other half
 * untouched.</p>
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

    public record ApplyResult(int appliedCount, int skippedCount, List<String> reasons) {
        public ApplyResult {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }

        public int getAppliedCount() {
            return appliedCount;
        }

        public int getSkippedCount() {
            return skippedCount;
        }

        public List<String> getReasons() {
            return reasons;
        }
    }

    /**
     * Atomically apply the parsed OBEC output-spec records. Any throw
     * (DAO failure, concurrent edit, malformed row that isn't otherwise
     * skip-able) rolls back the entire batch.
     */
    public ApplyResult applyOutputSpec(LoggedInInfo loggedInInfo,
                                       List<BillingEdtObecOutputSpecificationRecordDto> outputSpecVector) {
        int appliedCount = 0;
        int skippedCount = 0;
        List<String> reasons = new ArrayList<>();
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
                skippedCount++;
                reasons.add("Skipped HIN " + safeValue(hin) + ": unparseable response code " + safeValue(responseCode));
                continue;
            }

            if (responseCodeNum < 50 || responseCodeNum > 59) {
                BatchEligibility batchEligibility = batchEligibilityDao.find(responseCodeNum);
                List<Demographic> ds = demographicManager.searchByHealthCard(loggedInInfo, hin);

                if (!ds.isEmpty()) {
                    Demographic d = ds.get(0);
                    // Guard null ver / null bean version — a legacy seed row
                    // with a null demographic.ver would NPE here, and (because
                    // this method runs inside @Transactional) abort the whole
                    // batch. Skip the row with a log so other rows in the
                    // file still apply.
                    String dVer = d.getVer();
                    String beanVer = bean.getVersion();
                    if (dVer == null || beanVer == null) {
                        MiscUtils.getLogger().warn(
                                "BillingObecOutputApplyService: skipping row with null ver (hin={}, demographicNo={})",
                                LogSanitizer.sanitize(hin),
                                d.getDemographicNo());
                        skippedCount++;
                        reasons.add("Skipped HIN " + safeValue(hin) + ": missing version");
                        continue;
                    }
                    if (dVer.trim().compareTo(beanVer.trim()) == 0) {
                        for (Demographic demographic : ds) {
                            demographic.setVer("##");
                            demographicManager.updateDemographic(loggedInInfo, demographic);
                            appliedCount++;
                        }
                        DemographicCust demographicCust = demographicCustDao.find(d.getDemographicNo());
                        if (demographicCust != null && batchEligibility != null) {
                            String newAlert = demographicCust.getAlert() + "\n" + "Invalid old version code: "
                                    + bean.getVersion() + "\nReason: " + batchEligibility.getMOHResponse() + "- "
                                    + batchEligibility.getReason() + "\nResponse Code: " + responseCode;
                            demographicCust.setAlert(newAlert);
                            demographicCustDao.merge(demographicCust);
                        }
                    } else {
                        skippedCount++;
                        reasons.add("Skipped HIN " + safeValue(hin) + ": version mismatch");
                    }
                } else {
                    skippedCount++;
                    reasons.add("Skipped HIN " + safeValue(hin) + ": no demographic match");
                }
            } else {
                skippedCount++;
                reasons.add("Skipped HIN " + safeValue(hin) + ": response code " + responseCodeNum
                        + " does not require an update");
            }
        }
        return new ApplyResult(appliedCount, skippedCount, reasons);
    }

    private static String safeValue(String value) {
        return value == null || value.isBlank() ? "(blank)" : value.trim();
    }
}
