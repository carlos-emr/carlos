/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimItemDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimReportFilter;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimReportRow;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingPercLimitDao;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingPercLimit;
import io.github.carlos_emr.carlos.utility.DateRange;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.util.LabelValueBean;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Read-only loader for OHIP claim queries: bill lists by status / provider /
 * date range / location, service-code fee + percentage lookups by date,
 * service-code descriptions, and the dropdown / picker queries used by
 * the claim-entry and review screens. Pairs with
 * {@link BillingOnClaimPersister} on the write side.
 *
 * <p>Web security is enforced at the action layer before invocation.</p>
 */
@org.springframework.stereotype.Service
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public class BillingOnClaimLoader {
    private static final Logger _logger = MiscUtils.getLogger();

    private final ClinicLocationDao clinicLocationDao;
    private final BillingONCHeader1Dao dao;
    private final BillingONExtDao extDao;
    private final BillingONPaymentDao payDao;
    private final BillingServiceDao serviceDao;
    private final BillingOnItemPaymentDao billOnItemPaymentDao;
    private final BillingPercLimitDao percLimitDao;
    private final BillingPaymentTypeDao paymentTypeDao;
    private final ProviderDao providerDao;
    private final BillingONItemDao itemDao;
    private final CtlBillingServiceDao ctlBillingServiceDao;

    public record FeeLookupResult(String value, boolean partial, String message) {
        public static FeeLookupResult found(String value) {
            return new FeeLookupResult(value, false, "");
        }

        public static FeeLookupResult partial(String message) {
            return new FeeLookupResult(null, true, message);
        }
    }

    public record FeeRangeLookupResult(String min, String max, boolean partial, String message) {
        public static FeeRangeLookupResult found(String min, String max) {
            return new FeeRangeLookupResult(min, max, false, "");
        }

        public static FeeRangeLookupResult partial(String message) {
            return new FeeRangeLookupResult("", "", true, message);
        }
    }

    /** Test-friendly constructor — package-private, takes DAO mocks directly. */
    BillingOnClaimLoader(ClinicLocationDao clinicLocationDao,
                               BillingONCHeader1Dao dao,
                               BillingONExtDao extDao,
                               BillingONPaymentDao payDao,
                               BillingServiceDao serviceDao,
                               BillingOnItemPaymentDao billOnItemPaymentDao,
                               BillingPercLimitDao percLimitDao,
                               BillingPaymentTypeDao paymentTypeDao,
                               ProviderDao providerDao,
                               BillingONItemDao itemDao,
                               CtlBillingServiceDao ctlBillingServiceDao) {
        this.clinicLocationDao = clinicLocationDao;
        this.dao = dao;
        this.extDao = extDao;
        this.payDao = payDao;
        this.serviceDao = serviceDao;
        this.billOnItemPaymentDao = billOnItemPaymentDao;
        this.percLimitDao = percLimitDao;
        this.paymentTypeDao = paymentTypeDao;
        this.providerDao = providerDao;
        this.itemDao = itemDao;
        this.ctlBillingServiceDao = ctlBillingServiceDao;
    }

    public String getCodeFee(String val, String billReferalDate) {
        return getCodeFeeResult(val, billReferalDate).value();
    }

    public FeeLookupResult getCodeFeeResult(String val, String billReferalDate) {
        String retval = null;
        try {
            for (BillingService bs : serviceDao.findByServiceCodeAndLatestDate(val, ConversionUtils.fromDateString(billReferalDate))) {
                retval = bs.getValue();

                DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
                Date serviceDate = df.parse(billReferalDate);
                if (bs.getTerminationDate().before(serviceDate)) {
                    retval = BillingONItem.DEFUNCT_FEE;
                }
            }
            return FeeLookupResult.found(retval);

        } catch (Exception e) {
            // Caller treats null as "no fee found"; without context the
            // operator can't distinguish "code is unknown" from "DAO
            // threw" or "date string was unparseable".
            _logger.error("Failed to load fee for service code {} on date {}",
                    LogSanitizer.sanitize(val),
                    LogSanitizer.sanitize(billReferalDate),
                    e);
            return FeeLookupResult.partial("Fee lookup failed for service code " + LogSanitizer.sanitizeForDisplay(val));
        }
    }

    public String getPercFee(String val, String billReferalDate) {
        return getPercFeeResult(val, billReferalDate).value();
    }

    public FeeLookupResult getPercFeeResult(String val, String billReferalDate) {
        String retval = null;
        try {
            for (BillingService bs : serviceDao.findByServiceCodeAndLatestDate(val, ConversionUtils.fromDateString(billReferalDate))) {
                retval = bs.getPercentage();
            }
            return FeeLookupResult.found(retval);
        } catch (Exception e) {
            _logger.error("Failed to load percentage for service code {} on date {}",
                    LogSanitizer.sanitize(val),
                    LogSanitizer.sanitize(billReferalDate),
                    e);
            return FeeLookupResult.partial("Percentage lookup failed for service code " + LogSanitizer.sanitizeForDisplay(val));
        }
    }

    public String[] getPercMinMaxFee(String val, String billReferalDate) {
        FeeRangeLookupResult result = getPercMinMaxFeeResult(val, billReferalDate);
        return new String[] {result.min(), result.max()};
    }

    public FeeRangeLookupResult getPercMinMaxFeeResult(String val, String billReferalDate) {
        String[] retval = {"", ""};

        try {
            for (BillingPercLimit b : percLimitDao.findByServiceCodeAndLatestDate(val, ConversionUtils.fromDateString(billReferalDate))) {
                retval[0] = b.getMin();
                retval[1] = b.getMax();
            }
            return FeeRangeLookupResult.found(retval[0], retval[1]);
        } catch (Exception e) {
            _logger.error("Failed to load percent min/max for service code {} on date {}",
                    LogSanitizer.sanitize(val),
                    LogSanitizer.sanitize(billReferalDate),
                    e);
            return FeeRangeLookupResult.partial("Percentage min/max lookup failed for service code " + LogSanitizer.sanitizeForDisplay(val));
        }
    }

    // invoice report
    public List getBill(String billType, String statusType, String providerNo,
                        String startDate, String endDate, String demoNo) {

        return getBill(billType, statusType, providerNo, startDate, endDate, demoNo, "", "", "");

    }

    // invoice report
    public List getBill(String billType, String statusType, String providerNo,
                        String startDate, String endDate, String demoNo,
                        String serviceCodes, String dx, String visitType) {

        List<BillingClaimHeaderDto> retval = new ArrayList<BillingClaimHeaderDto>();
        BillingClaimHeaderDto ch1Obj = null;

        BillingClaimReportFilter filter = new BillingClaimReportFilter(
                billType, statusType, providerNo, startDate, endDate, demoNo,
                serviceCodes, dx, visitType);
        List<BillingClaimReportRow> bills = dao.findBillingData(filter);
        if (bills != null) {
            // Hoisted out of the loop so dedup spans iterations — the bi×ch1
            // join repeats ch1.paid per item, and a per-iteration null reset
            // would let the dedup arm double-count every multi-item claim.
            String prevId = null;
            for (BillingClaimReportRow b : bills) {
                ch1Obj = new BillingClaimHeaderDto();
                ch1Obj = ch1Obj.withId(b.id());
                ch1Obj = ch1Obj.withPayProgram(b.payProgram());
                ch1Obj = ch1Obj.withDemographicNo(b.demographicNo());
                ch1Obj = ch1Obj.withDemographicName(b.demographicName());
                ch1Obj = ch1Obj.withBillingDate(b.billingDate());
                ch1Obj = ch1Obj.withBillingTime(b.billingTime());
                ch1Obj = ch1Obj.withStatus(b.status());
                ch1Obj = ch1Obj.withProviderNo(b.providerNo());
                ch1Obj = ch1Obj.withProviderOhipNo(b.providerOhipNo());
                ch1Obj = ch1Obj.withUpdateDateTime(b.updateDatetime());
                ch1Obj = ch1Obj.withTotal(b.total());
                ch1Obj = ch1Obj.withClinic(b.clinic());
                ch1Obj = ch1Obj.withServiceNumber(b.serviceCount());
                ch1Obj = ch1Obj.withBillingOnItemId(b.billingOnItemId());

                List<BillingONExt> exts = extDao.findByBillingNoAndKey(Integer.parseInt(b.id()), "payDate");
                for (BillingONExt e : exts) {
                    if (e.getStatus() == '1') {
                        ch1Obj = ch1Obj.withSettleDate(e.getValue());
                    }
                }

                if ("PAT".equals(ch1Obj.payProgram())) {
                    BigDecimal amountPaid = billOnItemPaymentDao.getAmountPaidByItemId(Integer.parseInt(b.billingOnItemId()));
                    ch1Obj = ch1Obj.withPaid(amountPaid.toString());
                } else {
                    // Dedup ch1.paid across the bi×ch1 join: a 3-item claim
                    // returns 3 rows that all carry the same ch1.paid value.
                    // Stamp paid only on the first row of a given ch1; later
                    // rows of the same claim get "0.00" so report totals
                    // don't multiply paid by item count.
                    if (prevId == null || !ch1Obj.getId().equals(prevId)) {
                        ch1Obj = ch1Obj.withPaid(b.paid());
                    } else {
                        ch1Obj = ch1Obj.withPaid("0.00");
                    }
                }
                retval.add(ch1Obj);

                prevId = ch1Obj.getId();
            }

        }

        return retval;
    }


    public List<BillingClaimHeaderDto> getBill(String[] billType, String statusType, String providerNo, String startDate, String endDate, String demoNo, String visitLocation, String paymentStartDate, String paymentEndDate) {
        return getBillWithSorting(billType, statusType, providerNo, startDate, endDate, demoNo, visitLocation, null, null, paymentStartDate, paymentEndDate);
    }

    // invoice report
    public List<BillingClaimHeaderDto> getBillWithSorting(String[] billType, String statusType, String providerNo, String startDate, String endDate, String demoNo, String visitLocation, String sortName, String sortOrder, String paymentStartDate, String paymentEndDate) {
        List<BillingClaimHeaderDto> retval = new ArrayList<BillingClaimHeaderDto>();
        try {
            for (BillingONCHeader1 h : dao.findByMagic(Arrays.asList(billType), statusType, providerNo, ConversionUtils.fromDateString(startDate), ConversionUtils.fromDateString(endDate), ConversionUtils.fromIntString(demoNo), visitLocation, ConversionUtils.fromDateString(paymentStartDate), ConversionUtils.fromDateString(paymentEndDate))) {
                BillingClaimHeaderDto ch1Obj = new BillingClaimHeaderDto();
                ch1Obj = ch1Obj.withId("" + h.getId());
                ch1Obj = ch1Obj.withDemographicNo("" + h.getDemographicNo());
                ch1Obj = ch1Obj.withDemographicName(h.getDemographicName());
                ch1Obj = ch1Obj.withBillingDate(ConversionUtils.toDateString(h.getBillingDate()));
                ch1Obj = ch1Obj.withBillingTime(ConversionUtils.toDateString(h.getBillingTime()));
                ch1Obj = ch1Obj.withStatus(h.getStatus());
                ch1Obj = ch1Obj.withProviderNo(h.getProviderNo());
                ch1Obj = ch1Obj.withProviderOhipNo(h.getProviderOhipNo());
                ch1Obj = ch1Obj.withAppointmentProviderNo(h.getApptProviderNo());
                ch1Obj = ch1Obj.withUpdateDateTime(ConversionUtils.toDateString(h.getTimestamp()));
                ch1Obj = ch1Obj.withTotal(String.valueOf(h.getTotal().doubleValue()));
                ch1Obj = ch1Obj.withPayProgram(h.getPayProgram());
                ch1Obj = ch1Obj.withPaid(String.valueOf(h.getPaid().doubleValue()));
                ch1Obj = ch1Obj.withClinic(h.getClinic());
                for (BillingONExt b : extDao.findByBillingNoAndKey(h.getId(), "payDate")) {
                    ch1Obj = ch1Obj.withSettleDate(b.getValue());
                }

                ch1Obj = ch1Obj.withFacilityNumber(clinicLocationDao.searchVisitLocation(h.getFaciltyNum()));

                retval.add(ch1Obj);
            }
        } catch (Exception e) {
            throw billingLoadFailure("Failed to load billing list", e,
                    "providerNo", providerNo,
                    "demoNo", demoNo,
                    "startDate", startDate,
                    "endDate", endDate,
                    "visitLocation", visitLocation);
        }

        applySort(retval, sortName, sortOrder);
        return retval;
    }

    private void applySort(List<BillingClaimHeaderDto> retval, String sortName, String sortOrder) {
        if (sortOrder == null) {
            sortOrder = "asc";
        }

        if (sortName != null && "ServiceDate".equals(sortName)) {
            Collections.sort(retval, SERVICE_DATE_COMPARATOR);
        }
        if (sortName != null && "DemographicNo".equals(sortName)) {
            Collections.sort(retval, DEMOGRAPHIC_NO_COMPARATOR);
        }
        if (sortName != null && "VisitLocation".equals(sortName)) {
            Collections.sort(retval, VISIT_LOCATION_COMPARATOR);
        }
        if (sortOrder.equals("desc")) {
            Collections.reverse(retval);
        }
    }

    /** Sort sentinel for comparator parse failures — see {@link #parseDateOrSentinel}. */
    private static final Date COMPARATOR_DATE_SENTINEL = new Date(0L);

    /**
     * Parse a yyyy-MM-dd billing_date or return {@link #COMPARATOR_DATE_SENTINEL}.
     * Lifted to a static helper so {@link #SERVICE_DATE_COMPARATOR} and any
     * future date-comparator share one parse + log path. Logs at DEBUG (not
     * WARN) because comparators run O(n log n) and a single corrupt row can
     * fire the log many times per sort — log flooding is worse than the
     * already-visible "row sorted to the front" UX.
     *
     * <p>{@link SimpleDateFormat} is not thread-safe, so a fresh formatter
     * is constructed per call.</p>
     */
    private static Date parseDateOrSentinel(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(s);
        } catch (ParseException | NullPointerException e) {
            _logger.debug("comparator: malformed billing_date {}; sorting to epoch sentinel",
                    LogSanitizer.sanitize(s));
            return COMPARATOR_DATE_SENTINEL;
        }
    }

    /**
     * Parse a demographic_no or return {@code Integer.MIN_VALUE}. Companion
     * to {@link #parseDateOrSentinel} for the demographic comparator.
     */
    private static int parseIntOrSentinel(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException | NullPointerException e) {
            _logger.debug("comparator: malformed demographic_no {}; sorting to MIN sentinel",
                    LogSanitizer.sanitize(s));
            return Integer.MIN_VALUE;
        }
    }

    public static final Comparator<BillingClaimHeaderDto> SERVICE_DATE_COMPARATOR = new Comparator<BillingClaimHeaderDto>() {
        public int compare(BillingClaimHeaderDto arg0, BillingClaimHeaderDto arg1) {
            // Push parse failures to a sentinel epoch so corrupt rows sort
            // to the same end consistently. Returning 0 broke TimSort's
            // transitivity contract — corrupt rows compared-equal to every
            // other row, blowing up Collections.sort with
            // "Comparison method violates its general contract!".
            return parseDateOrSentinel(arg0.billingDate())
                    .compareTo(parseDateOrSentinel(arg1.billingDate()));
        }
    };

    public static final Comparator<BillingClaimHeaderDto> DEMOGRAPHIC_NO_COMPARATOR = new Comparator<BillingClaimHeaderDto>() {
        public int compare(BillingClaimHeaderDto arg0, BillingClaimHeaderDto arg1) {
            return Integer.compare(
                    parseIntOrSentinel(arg0.demographicNo()),
                    parseIntOrSentinel(arg1.demographicNo()));
        }
    };

    public static final Comparator<BillingClaimHeaderDto> VISIT_LOCATION_COMPARATOR = new Comparator<BillingClaimHeaderDto>() {
        public int compare(BillingClaimHeaderDto arg0, BillingClaimHeaderDto arg1) {
            return arg0.facilityNumber().compareTo(arg1.facilityNumber());
        }
    };


    //invoice report
    public List<BillingClaimHeaderDto> getBill(String[] billType, String statusType, String providerNo, String startDate, String endDate, String demoNo, List<String> serviceCodes, String dx, String visitType, String visitLocation, String paymentStartDate, String paymentEndDate) {
        return getBillWithSorting(billType, statusType, providerNo, startDate, endDate, demoNo, serviceCodes, dx, visitType, visitLocation, null, null, paymentStartDate, paymentEndDate, null);
    }

    //invoice report
    public List<BillingClaimHeaderDto> getBillWithSorting(String[] billType, String statusType, String providerNo, String startDate, String endDate, String demoNo, List<String> serviceCodes, String dx, String visitType, String visitLocation, String sortName, String sortOrder, String paymentStartDate, String paymentEndDate, String claimNo) {
        List<BillingClaimHeaderDto> retval = new ArrayList<BillingClaimHeaderDto>();

        try {
            String prevId = null;
            String prevPaid = null;

            Integer CASH_PAYMENT_ID = paymentTypeDao.findIdByName("CASH");
            Integer DEBIT_PAYMENT_ID = paymentTypeDao.findIdByName("DEBIT");

            for (Object[] o : dao.findByMagic2(Arrays.asList(billType), statusType, providerNo, ConversionUtils.fromDateString(startDate), ConversionUtils.fromDateString(endDate), ConversionUtils.fromIntString(demoNo), serviceCodes, dx, visitType, visitLocation, ConversionUtils.fromDateString(paymentStartDate), ConversionUtils.fromDateString(paymentEndDate), claimNo)) {
                BillingONCHeader1 ch1 = (BillingONCHeader1) o[0];
                BillingONItem bi = (BillingONItem) o[1];

                BillingClaimHeaderDto ch1Obj = new BillingClaimHeaderDto();
                ch1Obj = ch1Obj.withId("" + ch1.getId());
                ch1Obj = ch1Obj.withDemographicNo("" + ch1.getDemographicNo());
                ch1Obj = ch1Obj.withDemographicName(ch1.getDemographicName());
                ch1Obj = ch1Obj.withBillingDate(ConversionUtils.toDateString(ch1.getBillingDate()));
                ch1Obj = ch1Obj.withBillingTime(ConversionUtils.toTimeString(ch1.getBillingTime()));
                ch1Obj = ch1Obj.withStatus(ch1.getStatus());
                ch1Obj = ch1Obj.withProviderNo(ch1.getProviderNo());
                ch1Obj = ch1Obj.withProviderOhipNo(ch1.getProviderOhipNo());
                ch1Obj = ch1Obj.withAppointmentProviderNo(ch1.getApptProviderNo());
                ch1Obj = ch1Obj.withUpdateDateTime(ConversionUtils.toTimestampString(ch1.getTimestamp()));
                ch1Obj = ch1Obj.withClinic(ch1.getClinic());
                ch1Obj = ch1Obj.withPayProgram(ch1.getPayProgram());

                if ("PAT".equals(ch1.getPayProgram())) {
                    BigDecimal amountPaid = billOnItemPaymentDao.getAmountPaidByItemId(bi.getId());
                    ch1Obj = ch1Obj.withPaid(amountPaid.toString());
                    ch1Obj = ch1Obj.withBillingOnItemId(bi.getId().toString());
                } else {
                    if (prevId == null && prevPaid == null) {
                        ch1Obj = ch1Obj.withPaid(ch1.getPaid().toString());
                    } else if (prevId != null && prevPaid != null && !ch1Obj.getId().equals(prevId)) {
                        ch1Obj = ch1Obj.withPaid(ch1.getPaid().toString());
                    } else
                        ch1Obj = ch1Obj.withPaid("0.00");
                }
                ch1Obj = ch1Obj.withTotal(bi.getFee());
                ch1Obj = ch1Obj.withRecordId(bi.getDx());
                ch1Obj = ch1Obj.withTransactionId(bi.getServiceCode());

                retval.add(ch1Obj);
                prevId = ch1Obj.getId();
                prevPaid = ch1.getPaid().toString();

                ch1Obj = ch1Obj.withFacilityNumber(clinicLocationDao.searchVisitLocation(ch1.getFaciltyNum()));

                BigDecimal cashTotal = BigDecimal.ZERO;
                BigDecimal debitTotal = BigDecimal.ZERO;

                ch1Obj = ch1Obj.withNumItems(Integer.parseInt(bi.getServiceCount()));

                for (Integer paymentId : payDao.find3rdPartyPayments(Integer.parseInt(ch1Obj.getId()))) {
                    //because private billing changed, we'll check via paymentTypeId in billing_on_payment
                    BillingONPayment paymentObj = payDao.find(paymentId);
                    BillingOnItemPayment boip = billOnItemPaymentDao.findByPaymentIdAndItemId(paymentId, bi.getId());

                    if (boip == null) {
                        MiscUtils.getLogger().warn("boip is null - " + paymentId + "," + bi.getId());
                        //probably means that no payment was applied to this item.
                        continue;
                    }

                    if (paymentObj.getPaymentTypeId() == CASH_PAYMENT_ID) {
                        cashTotal = cashTotal.add(boip.getPaid());
                    } else if (paymentObj.getPaymentTypeId() == DEBIT_PAYMENT_ID) {
                        debitTotal = debitTotal.add(boip.getPaid());
                    }

                }


                ch1Obj = ch1Obj.withCashTotal(cashTotal);
                ch1Obj = ch1Obj.withDebitTotal(debitTotal);

                Provider provider = providerDao.getProvider(ch1Obj.providerNo());
                if (provider != null) {
                    ch1Obj = ch1Obj.withProviderName(provider.getFormattedName());
                }

            }
        } catch (Exception e) {
            throw billingLoadFailure("Failed to load billing list with service filters", e,
                    "providerNo", providerNo,
                    "demoNo", demoNo,
                    "claimNo", claimNo,
                    "startDate", startDate,
                    "endDate", endDate,
                    "serviceCodes", serviceCodes == null ? "" : String.join(",", serviceCodes),
                    "dx", dx,
                    "visitType", visitType,
                    "visitLocation", visitLocation);
        }

        applySort(retval, sortName, sortOrder);

        return retval;
    }

    /**
     * Loads paginated billing history for a demographic, returning an
     * interleaved list of {@link BillingClaimHeaderDto} and
     * {@link BillingClaimItemDto} pairs (positional: even indexes hold
     * headers, odd indexes hold the matching item summary).
     *
     * <p><b>Type-design known issue:</b> the {@code List<Object>} return
     * type with positional casting is a tagged-union encoded as raw list
     * elements. A future cleanup should introduce a
     * {@code record BillingHistoryEntry(BillingClaimHeaderDto header,
     * BillingClaimItemDto item)} and return {@code List<BillingHistoryEntry>}.
     * Migration is non-trivial (10+ caller files iterate by
     * {@code i = i + 2}) so it is tracked as a separate refactor and
     * tracked as a follow-up refactor.</p>
     *
     * @param demoNo    String the demographic number
     * @param iPageSize int max page size
     * @param iOffSet   int offset for pagination
     * @param dateRange DateRange optional date filter
     * @return List interleaved [headerDto, itemDto, headerDto, itemDto, ...]
     */
    public List<Object> getBillingHist(String demoNo, int iPageSize, int iOffSet, DateRange dateRange) {
        List<Object> retval = new ArrayList<Object>();
        int iRow = 0;

        BillingClaimHeaderDto ch1Obj = null;

        try {
            List<BillingONCHeader1> hs;
            if (dateRange == null) {
                hs = dao.findByDemoNo(ConversionUtils.fromIntString(demoNo), iOffSet, iPageSize);
            } else {
                hs = dao.findByDemoNoAndDates(ConversionUtils.fromIntString(demoNo), dateRange, iOffSet, iPageSize);
            }
            for (BillingONCHeader1 h : hs) {
                iRow++;
                if (iRow > iPageSize) {
                    break;
                }
                ch1Obj = new BillingClaimHeaderDto();
                ch1Obj = ch1Obj.withId("" + h.getId());
                ch1Obj = ch1Obj.withBillingDate(ConversionUtils.toDateString(h.getBillingDate()));
                ch1Obj = ch1Obj.withBillingTime(ConversionUtils.toTimeString(h.getBillingTime()));
                ch1Obj = ch1Obj.withStatus(h.getStatus());
                ch1Obj = ch1Obj.withProviderNo(h.getProviderNo());
                ch1Obj = ch1Obj.withAppointmentProviderNo(h.getApptProviderNo());
                ch1Obj = ch1Obj.withUpdateDateTime(ConversionUtils.toDateString(h.getTimestamp()));

                ch1Obj = ch1Obj.withClinic(h.getClinic());
                ch1Obj = ch1Obj.withAppointmentNo("" + h.getAppointmentNo());
                ch1Obj = ch1Obj.withPayProgram(h.getPayProgram());
                ch1Obj = ch1Obj.withVisitType(h.getVisitType());
                ch1Obj = ch1Obj.withAdmissionDate(ConversionUtils.toDateString(h.getAdmissionDate()));
                ch1Obj = ch1Obj.withFacilityNumber(h.getFaciltyNum());
                ch1Obj = ch1Obj.withTotal(h.getTotal().toString());

                Provider provider = providerDao.getProvider(h.getProviderNo());
                ch1Obj = ch1Obj.withLastName(provider.getLastName());
                ch1Obj = ch1Obj.withFirstName(provider.getFirstName());


                retval.add(ch1Obj);

                String dx = "";
                Set<String> serviceCodeSet = new HashSet<String>();

                String strServiceDate = "";
                BigDecimal paid = new BigDecimal("0.00");
                BigDecimal refund = new BigDecimal("0.00");
                BigDecimal discount = new BigDecimal("0.00");


                for (BillingONItem i : itemDao.findByCh1IdAndStatusNotEqual(h.getId(), "D")) {
                    String strService = i.getServiceCode() + " x " + i.getServiceCount() + ", ";
                    dx = i.getDx();
                    strServiceDate = ConversionUtils.toDateString(i.getServiceDate());

                    serviceCodeSet.add(strService);
                }

                BillingClaimItemDto itObj = new BillingClaimItemDto();
                StringBuffer codeBuf = new StringBuffer();
                for (String codeStr : serviceCodeSet) {
                    codeBuf.append(codeStr + ",");
                }
                if (codeBuf.length() > 0) {
                    codeBuf.deleteCharAt(codeBuf.length() - 1);
                }
                itObj = itObj.withServiceCode(codeBuf.toString());
                itObj = itObj.withDx(dx);
                itObj = itObj.withServiceDate(strServiceDate);

                List<BillingONPayment> payment = payDao.find3rdPartyPaymentsByBillingNo(h.getId());
                itObj = itObj.withPaid(payDao.getTotalSumByBillingNoWeb(h.getId().toString()));
                itObj = itObj.withRefund(payDao.getPaymentsRefundByBillingNoWeb(h.getId().toString()));
                BigDecimal discount_total = payDao.getPaymentsDiscountByBillingNo(h.getId());
                if (discount_total == null) {
                    discount_total = new BigDecimal(0);
                }
                NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.US);
                itObj = itObj.withDiscount(currency.format(discount_total));

                retval.add(itObj);
            }
        } catch (Exception e) {
            throw billingLoadFailure("Failed to load billing history", e,
                    "demoNo", demoNo,
                    "pageSize", iPageSize,
                    "offset", iOffSet,
                    "dateRange", dateRange == null ? "" : dateRange.toString());
        }

        return retval;
    }

    public List<LabelValueBean> listBillingForms() {
        List<LabelValueBean> res = new ArrayList<LabelValueBean>();

        try {
            for (io.github.carlos_emr.carlos.billings.ca.on.dto.ServiceTypeRow row :
                    ctlBillingServiceDao.findServiceTypes()) {
                res.add(new LabelValueBean(row.serviceTypeName(), row.serviceType()));
            }
        } catch (Exception ex) {
            throw billingLoadFailure("Failed to load billing forms", ex,
                    "lookup", "billingForms");
        }
        return res;
    }

    public List<String> mergeServiceCodes(String serviceCodes, String billingForm) {

        List<String> serviceCodeList = null;
        if ((serviceCodes != null && serviceCodes.length() > 0) || (billingForm != null && billingForm.length() > 0)) {
            serviceCodeList = new ArrayList<String>();
        }

        if (serviceCodes != null && serviceCodes.length() > 0) {
            String[] serviceArray = serviceCodes.split(",");
            for (int i = 0; i < serviceArray.length; i++) {
                serviceCodeList.add(serviceArray[i].trim());
            }
        }

        if (billingForm != null && billingForm.length() > 0) {
            for (Object code : ctlBillingServiceDao.findServiceCodesByType(billingForm)) {
                serviceCodeList.add(code.toString());
            }
        }

        return serviceCodeList;
    }

    // billing edit page
    public List<Object> getBillingByApptNo(String apptNo) {
        List<Object> retval = new ArrayList<Object>();

        BillingClaimHeaderDto ch1Obj = null;

        try {
            for (BillingONCHeader1 h : dao.findByAppointmentNo(ConversionUtils.fromIntString(apptNo))) {
                ch1Obj = new BillingClaimHeaderDto();
                ch1Obj = ch1Obj.withId("" + h.getId());
                ch1Obj = ch1Obj.withBillingDate(ConversionUtils.toDateString(h.getBillingDate()));
                ch1Obj = ch1Obj.withBillingTime(ConversionUtils.toTimeString(h.getBillingTime()));
                ch1Obj = ch1Obj.withStatus(h.getStatus());
                ch1Obj = ch1Obj.withProviderNo(h.getProviderNo());
                ch1Obj = ch1Obj.withAppointmentNo("" + h.getAppointmentNo());
                ch1Obj = ch1Obj.withAppointmentProviderNo(h.getApptProviderNo());
                ch1Obj = ch1Obj.withAssistantProviderNo(h.getAsstProviderNo());
                ch1Obj = ch1Obj.withManualReview(h.getManReview());
                ch1Obj = ch1Obj.withUpdateDateTime(ConversionUtils.toTimestampString(h.getTimestamp()));
                ch1Obj = ch1Obj.withClinic(h.getClinic());
                ch1Obj = ch1Obj.withPayProgram(h.getPayProgram());
                ch1Obj = ch1Obj.withVisitType(h.getVisitType());
                ch1Obj = ch1Obj.withAdmissionDate(ConversionUtils.toDateString(h.getAdmissionDate()));
                ch1Obj = ch1Obj.withFacilityNumber(h.getFaciltyNum());
                ch1Obj = ch1Obj.withHin(h.getHin());
                ch1Obj = ch1Obj.withVer(h.getVer());
                ch1Obj = ch1Obj.withProvince(h.getProvince());
                ch1Obj = ch1Obj.withDob(h.getDob());
                ch1Obj = ch1Obj.withDemographicName(h.getDemographicName());
                ch1Obj = ch1Obj.withDemographicNo("" + h.getDemographicNo());
                ch1Obj = ch1Obj.withTotal(String.valueOf(h.getTotal().doubleValue()));
                retval.add(ch1Obj);

                String dx = null;
                String dx1 = null;
                String dx2 = null;
                StringBuilder strService = new StringBuilder();
                String strServiceDate = null;

                for (BillingONItem i : itemDao.findByCh1Id(h.getId())) {
                    strService.append(i.getServiceCode()).append(" x ").append(i.getServiceCount()).append(", ");
                    dx = i.getDx();
                    strServiceDate = ConversionUtils.toDateString(i.getServiceDate());
                    dx1 = i.getDx1();
                    dx2 = i.getDx2();
                }

                BillingClaimItemDto itObj = new BillingClaimItemDto();
                itObj = itObj.withServiceCode(strService.toString());
                itObj = itObj.withDx(dx);
                itObj = itObj.withDx1(dx1);
                itObj = itObj.withDx2(dx2);
                itObj = itObj.withServiceDate(strServiceDate);
                retval.add(itObj);

            }
        } catch (Exception e) {
            throw billingLoadFailure("Failed to load billing by appointment", e,
                    "apptNo", apptNo);
        }

        return retval;
    }

    private static BillingDataLoadException billingLoadFailure(String message, Throwable cause, Object... contextPairs) {
        return new BillingDataLoadException(message, cause, BillingDataLoadException.Phase.DAO_QUERY,
                context(contextPairs));
    }

    private static Map<String, String> context(Object... pairs) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            result.put(String.valueOf(pairs[i]), String.valueOf(pairs[i + 1]));
        }
        return result;
    }


    public String getCodeDescription(String val, String billReferalDate) {
        return serviceDao.getCodeDescription(val, billReferalDate);

    }

}
