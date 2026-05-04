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

import io.github.carlos_emr.carlos.billings.ca.on.BillingDates;
import io.github.carlos_emr.carlos.billings.ca.on.BillingMoney;
import io.github.carlos_emr.carlos.billings.ca.on.OhipScheduleDates;
import io.github.carlos_emr.carlos.billings.ca.on.dto.FeeScheduleAppliedChange;
import io.github.carlos_emr.carlos.billings.ca.on.dto.FeeScheduleApplyResult;
import io.github.carlos_emr.carlos.billings.ca.on.dto.FeeScheduleChange;
import io.github.carlos_emr.carlos.billings.ca.on.dto.FeeScheduleImportRequest;
import io.github.carlos_emr.carlos.billings.ca.on.dto.FeeScheduleImportResult;
import io.github.carlos_emr.carlos.billings.ca.on.dto.FeeScheduleSelectedChange;
import io.github.carlos_emr.carlos.billings.ca.on.dto.FeeScheduleValidationError;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
/**
 * Preview/apply service for Ontario Schedule of Benefits fee uploads.
 *
 * <p>The workflow is deliberately two-phase: preview parses the fixed-width
 * ministry file and computes deltas without writing, then apply persists only
 * the operator-approved changes back into {@code billing_service}.</p>
 */
@Service
public class FeeScheduleImportService {
    private static final String REGION_ON = "ON";

    private final BillingServiceDao billingServiceDao;
    private final Clock clock;

    @Autowired
    public FeeScheduleImportService(BillingServiceDao billingServiceDao) {
        this(billingServiceDao, Clock.systemDefaultZone());
    }

    FeeScheduleImportService(BillingServiceDao billingServiceDao, Clock clock) {
        this.billingServiceDao = Objects.requireNonNull(billingServiceDao, "billingServiceDao");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Parse a fixed-width Schedule of Benefits file and compute the change set
     * the operator would apply, without writing to the database.
     */
    @Transactional(readOnly = true)
    public FeeScheduleImportResult preview(InputStream stream, FeeScheduleImportRequest request) {
        List<FeeScheduleChange> changes = new ArrayList<>();
        List<FeeScheduleValidationError> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String rawLine;
            int lineNumber = 0;
            while ((rawLine = reader.readLine()) != null) {
                lineNumber++;
                FeeScheduleLine line = parseLine(rawLine, lineNumber, errors);
                if (line == null) {
                    continue;
                }
                changes.addAll(previewLine(line, request, errors));
            }
        } catch (IOException e) {
            MiscUtils.getLogger().error("SOB Upload error", e);
            errors.add(new FeeScheduleValidationError(0, "", "stream", e.getMessage()));
        }

        return new FeeScheduleImportResult(changes, errors, request.forceUpdate());
    }

    /** Persist every previewed change in order, returning the rows that were applied. */
    @Transactional
    public FeeScheduleApplyResult applyAll(List<FeeScheduleChange> changes) {
        List<FeeScheduleAppliedChange> applied = new ArrayList<>();
        for (FeeScheduleChange change : changes) {
            persistBillingCode(change.feeCode(), change.newPrice(), change.effectiveDate(), change.terminationDate(),
                    change.description());
            applied.add(new FeeScheduleAppliedChange(change.feeCode(), change.newPrice()));
        }
        return new FeeScheduleApplyResult(applied, List.of());
    }

    /** Persist only the subset of preview rows the operator selected on the review page. */
    @Transactional
    public FeeScheduleApplyResult applySelected(List<FeeScheduleSelectedChange> changes) {
        List<FeeScheduleAppliedChange> applied = new ArrayList<>();
        for (FeeScheduleSelectedChange change : changes) {
            persistBillingCode(change.feeCode(), change.newPrice(), change.effectiveDate(), change.terminationDate(),
                    change.description());
            applied.add(new FeeScheduleAppliedChange(change.feeCode(), change.newPrice()));
        }
        return new FeeScheduleApplyResult(applied, List.of());
    }

    private List<FeeScheduleChange> previewLine(FeeScheduleLine line, FeeScheduleImportRequest request,
                                                List<FeeScheduleValidationError> errors) {
        List<FeeScheduleChange> changes = new ArrayList<>();
        String morePrices = line.pricesSummary();
        String defaultDescription = billingServiceDao.searchDescBillingCode(line.feeCode(), REGION_ON);

        BigDecimal newPrice = firstNonZero(line.gpFee(), line.specialistFee(), line.assistantCompFee());
        addChange(changes, compareBillingCode(line, "A", newPrice, morePrices, defaultDescription, request, errors));

        if (BillingMoney.isNonZero(request.updateAssistantFeesValue())) {
            addChange(changes, compareBillingCode(line, "B", request.updateAssistantFeesValue(), morePrices,
                    defaultDescription, request, errors));
        }

        if (BillingMoney.isNonZero(request.updateAnaesthetistFeesValue())) {
            addChange(changes, compareBillingCode(line, "C", request.updateAnaesthetistFeesValue(), morePrices,
                    defaultDescription, request, errors));
        }

        return changes;
    }

    private FeeScheduleChange compareBillingCode(FeeScheduleLine line, String feeType, BigDecimal fee,
                                                 String morePrices, String defaultDescription,
                                                 FeeScheduleImportRequest request,
                                                 List<FeeScheduleValidationError> errors) {
        String serviceCode = line.feeCode() + feeType;
        List<BillingService> existingServices = billingServiceDao.findMostRecentByServiceCode(serviceCode);
        BillingService existing = latest(existingServices);
        boolean feeNonZero = fee.compareTo(BigDecimal.ZERO) != 0;

        if (existing == null && !request.forceUpdate() && !request.addNewCodes()) {
            return null;
        }
        if (existing == null && (request.addNewCodes() || request.forceUpdate()) && !feeNonZero) {
            return null;
        }
        if (existing != null && !request.forceUpdate() && !request.addChangedCodes()) {
            return null;
        }

        BigDecimal oldPrice = existing == null ? null : parseExistingPrice(existing, serviceCode, line, errors);
        if (existing != null && oldPrice == null) {
            return null;
        }
        boolean feeChanged = oldPrice == null || oldPrice.compareTo(fee) != 0;
        if (existing != null && request.addChangedCodes() && !feeChanged) {
            return null;
        }
        if (existing != null && BillingDates.toOhipDate(existing.getBillingserviceDate()).equals(line.effectiveDate())) {
            return null;
        }

        boolean newCode = existing == null;
        BigDecimal diff = newCode ? null : fee.subtract(oldPrice);
        String description = newCode || isEmptyDescription(existing.getDescription())
                ? nullToEmpty(defaultDescription)
                : existing.getDescription();

        return new FeeScheduleChange(serviceCode, oldPrice, fee, diff, morePrices, line.effectiveDate(),
                line.terminationDate(), description, existingServices.size(), newCode);
    }

    private BigDecimal parseExistingPrice(BillingService existing, String serviceCode, FeeScheduleLine line,
                                          List<FeeScheduleValidationError> errors) {
        try {
            return BillingMoney.parseNonNegativeAmount(existing.getValue(), "existingValue");
        } catch (io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException e) {
            errors.add(new FeeScheduleValidationError(line.lineNumber(), line.rawLine(), "existingValue",
                    "Existing billing_service value for " + serviceCode + " is malformed"));
            return null;
        }
    }

    private void persistBillingCode(String code, BigDecimal value, String effectiveDate, String terminationDate,
                                    String description) {
        LocalDate today = LocalDate.now(clock);
        String serviceDate = BillingDates.ohipEffectiveDate(effectiveDate, today);
        String termDate = OhipScheduleDates.terminationDate(terminationDate);

        BillingService billingService = new BillingService();
        billingService.setServiceCompositecode("");
        billingService.setServiceCode(code);
        billingService.setDescription(description);
        billingService.setValue(BillingMoney.format(value));
        billingService.setPercentage("");
        billingService.setBillingserviceDate(BillingDates.serviceDate(serviceDate));
        billingService.setSpecialty("");
        billingService.setRegion(REGION_ON);
        billingService.setAnaesthesia("00");
        billingService.setTerminationDate(BillingDates.serviceDate(termDate));
        billingService.setGstFlag(false);
        billingService.setSliFlag(false);
        billingServiceDao.persist(billingService);
    }

    private FeeScheduleLine parseLine(String rawLine, int lineNumber, List<FeeScheduleValidationError> errors) {
        if (rawLine == null || rawLine.length() != 75) {
            errors.add(new FeeScheduleValidationError(lineNumber, rawLine, "line",
                    "Expected fixed-width fee schedule line with 75 characters"));
            return null;
        }

        try {
            return new FeeScheduleLine(
                    lineNumber,
                    rawLine,
                    rawLine.substring(0, 4),
                    rawLine.substring(4, 12),
                    rawLine.substring(12, 20),
                    BillingMoney.ohipFeeAmountLegacyBigDecimal(rawLine.substring(20, 31)),
                    BillingMoney.ohipFeeAmountLegacyBigDecimal(rawLine.substring(31, 42)),
                    BillingMoney.ohipFeeAmountLegacyBigDecimal(rawLine.substring(42, 53)),
                    BillingMoney.ohipFeeAmountLegacyBigDecimal(rawLine.substring(53, 64)),
                    BillingMoney.ohipFeeAmountLegacyBigDecimal(rawLine.substring(64, 75)));
        } catch (RuntimeException e) {
            // The expected case is NumberFormatException from BillingMoney.ohipFeeAmountLegacyBigDecimal;
            // the catch is intentionally broad in case BillingMoney's contract drifts.
            // Log the line number + exception type so unknown failure modes remain
            // debuggable instead of disappearing into the validation-error bag.
            MiscUtils.getLogger().warn("Schedule of Benefits line {} failed to parse ({})",
                    lineNumber, e.getClass().getSimpleName(), e);
            errors.add(new FeeScheduleValidationError(lineNumber, rawLine, "line", e.getMessage()));
            return null;
        }
    }

    private BigDecimal firstNonZero(BigDecimal first, BigDecimal second, BigDecimal third) {
        if (first.compareTo(BigDecimal.ZERO) != 0) {
            return first;
        }
        if (second.compareTo(BigDecimal.ZERO) != 0) {
            return second;
        }
        return third;
    }

    private BillingService latest(List<BillingService> services) {
        return services == null || services.isEmpty() ? null : services.get(services.size() - 1);
    }

    private void addChange(List<FeeScheduleChange> changes, FeeScheduleChange change) {
        if (change != null) {
            changes.add(change);
        }
    }

    private boolean isEmptyDescription(String description) {
        return description == null || description.trim().isEmpty() || "----".equals(description.trim());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record FeeScheduleLine(
            int lineNumber,
            String rawLine,
            String feeCode,
            String effectiveDate,
            String terminationDate,
            BigDecimal gpFee,
            BigDecimal assistantCompFee,
            BigDecimal specialistFee,
            BigDecimal anaesthetistFee,
            BigDecimal nonAnaesthetistFee) {

        private String pricesSummary() {
            return "(gp.:" + gpFee +
                    ")  (asst.:" + assistantCompFee +
                    ")  (spec.:" + specialistFee +
                    ")  (anaes:" + anaesthetistFee +
                    ")  (non-a:" + nonAnaesthetistFee + ")";
        }
    }
}
