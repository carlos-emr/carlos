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
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Applies a parsed OBEC output-specification report to the demographic
 * graph atomically: per row, flips matching demographics' {@code ver}
 * to {@code "##"} (HIN-flagged-invalid) and appends a reason note to
 * the patient's {@code DemographicCust.alert} string, when the alert can hold it.
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
        for (int rowIndex = 0; rowIndex < outputSpecVector.size(); rowIndex++) {
            BillingEdtObecOutputSpecificationRecordDto bean = outputSpecVector.get(rowIndex);
            String rowLabel = rowLabel(rowIndex);
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
                        "Skipping OBEC output-spec {} with unparseable response code {}",
                        rowLabel,
                        LogSafe.sanitize(responseCode), e);
                skippedCount++;
                reasons.add("Skipped " + rowLabel + ": unparseable response code " + safeValue(responseCode));
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
                                "BillingObecOutputApplyService: skipping {} with null ver (demographicNo={})",
                                rowLabel,
                                d.getDemographicNo());
                        skippedCount++;
                        reasons.add("Skipped " + rowLabel + ": missing version");
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
                            String existingAlert = demographicCust.getAlert();
                            String newAlert = (existingAlert == null || existingAlert.isEmpty() ? "" : existingAlert + "\n")
                                    + "Invalid old version code: "
                                    + bean.getVersion() + "\nReason: " + batchEligibility.getMOHResponse() + "- "
                                    + batchEligibility.getReason() + "\nResponse Code: " + responseCode;
                            // #6140: a longer alert was cut at the column (empty sql_mode, losing the
                            // newest message) or failed the whole batch (strict). It is measured as the
                            // Master Record form sends it back, each line break as CR LF, so an alert
                            // written here can still be saved there. A message that does not fit is
                            // not written: the row still applies, and the result says so.
                            if (DemographicCust.validateAlertLength(asFormSubmits(newAlert)).isEmpty()) {
                                demographicCust.setAlert(newAlert);
                                demographicCustDao.merge(demographicCust);
                            } else {
                                MiscUtils.getLogger().warn(
                                        "OBEC apply: alert not updated for {}: the message would exceed {} characters",
                                        rowLabel, DemographicCust.ALERT_MAX_LENGTH);
                                reasons.add("Alert not updated for " + rowLabel + ": the eligibility message would make "
                                        + "it longer than " + DemographicCust.ALERT_MAX_LENGTH + " characters");
                            }
                        } else {
                            // #6140: whatever is not written is listed. Without a custom-fields row there is
                            // no alert to add to; a code missing from the eligibility table has no message.
                            reasons.add("Alert not updated for " + rowLabel + ": " + (demographicCust == null
                                    ? "the patient has no custom-fields record to hold the alert"
                                    : "response code " + responseCodeNum + " has no eligibility message"));
                        }
                    } else {
                        skippedCount++;
                        reasons.add("Skipped " + rowLabel + ": version mismatch");
                    }
                } else {
                    skippedCount++;
                    reasons.add("Skipped " + rowLabel + ": no demographic match");
                }
            } else {
                skippedCount++;
                reasons.add("Skipped " + rowLabel + ": response code " + responseCodeNum
                        + " does not require an update");
            }
        }
        return new ApplyResult(appliedCount, skippedCount, reasons);
    }

    /** The text as a textarea submits it: every line break as CR LF. */
    static String asFormSubmits(String text) {
        return text.replace("\r\n", "\n").replace("\n", "\r\n");
    }

    private static String rowLabel(int zeroBasedIndex) {
        return "row " + (zeroBasedIndex + 1);
    }

    private static String safeValue(String value) {
        return value == null || value.isBlank() ? "(blank)" : value.trim();
    }
}
