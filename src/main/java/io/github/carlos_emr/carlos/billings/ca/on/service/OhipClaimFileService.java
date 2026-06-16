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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONDiskNameDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONFilenameDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONHeaderDao;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONDiskName;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONFilename;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONHeader;
import io.github.carlos_emr.carlos.billings.ca.on.BillingMoney;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingBatchHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimItemDto;
import io.github.carlos_emr.carlos.billings.ca.on.support.BillingDomIdTokens;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONItemDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.BillingStatus;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.DateRange;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.providers.data.ProviderBillCenter;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * OHIP claim-file generator for the ON billing module. Writes the
 * fixed-width MOH-format claim file (text disk image) plus a parallel
 * HTML preview / record of every claim, item, and reciprocal row in a
 * batch. Persists the {@code billing_on_header} batch summary, the
 * {@code billing_on_filename} disk-creation log entry, and updates each
 * {@code billing_on_cheader1} status as it iterates.
 *
 * <p>Side-effect heavy by nature: file I/O, multiple DAO writes,
 * audit-trail emission. Strictly belongs in {@code service/} per the
 * package-info contract.</p>
 *
 * <p>Used by:</p>
 * <ul>
 *   <li>{@link io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOhipSimulationViewModelAssembler}
 *       — dry-run preview ({@code eFlag="0"}).</li>
 *   <li>{@link BillingOnDiskService} — actual disk creation /
 *       regeneration ({@code eFlag="1"}).</li>
 *   <li>{@code ViewOnReportGeneration2Action} / {@code ViewOnReportRegeneration2Action}
 *       — gate actions for the two report-generation pages.</li>
 * </ul>
 *
 * <p><strong>Scope:</strong> {@code prototype}. Unlike most services in
 * this package, this class carries per-claim instance state — {@code
 * htmlValue}, {@code ohipClaim}, {@code dateRange}, {@code eFlag},
 * {@code providerNo}, {@code currentBatchHeader}, the running total / record-count
 * fields, etc. — populated by setters as it incrementally builds an OHIP
 * claim file. A singleton-scoped instance shared across two concurrent
 * OHIP file generations would corrupt both files' state. Singleton
 * collaborators must therefore inject this via
 * {@link org.springframework.beans.factory.ObjectFactory}; each
 * {@code factory.getObject()} call yields a fresh instance (see
 * {@link BillingOnDiskService}).</p>
 *
 * <p><strong>Transaction note:</strong> this prototype writer is intentionally
 * not class-level transactional because it performs filesystem writes.
 * Claim-file assembly keeps method-level read-only transactions for DAO reads;
 * disk generation callers run {@link #finalizeGeneratedDisk()} and
 * {@link #updateDisknameSum(int)} through {@link BillingOnDiskTransactionService}
 * after the OHIP/HTML files are durable.</p>
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
@org.springframework.context.annotation.Scope("prototype")
public class OhipClaimFileService {

    private static final Logger _logger = MiscUtils.getLogger();

    private String errorFatalMsg = "";
    private BillingBatchHeaderDto currentBatchHeader = null;
    private BillingClaimHeaderDto currentClaimHeader = null;
    private BillingClaimItemDto currentItem = null;
    private Properties propBillingNo = null;
    private final DemographicManager demographicManager;
    private final BillingONCHeader1Dao cheaderDao;
    private final BillingONHeaderDao headerDao;
    private final BillingONFilenameDao filenameDao;
    private final SiteDao siteDao;
    private final BillingONItemDao itemDao;
    private final BillingServiceDao billingServiceDao;
    private final BillingONDiskNameDao diskNameDao;
    private final BillingOnLookupService lookupService;

    public OhipClaimFileService(DemographicManager demographicManager,
                                BillingONCHeader1Dao cheaderDao,
                                BillingONHeaderDao headerDao,
                                BillingONFilenameDao filenameDao,
                                SiteDao siteDao,
                                BillingONItemDao itemDao,
                                BillingServiceDao billingServiceDao,
                                BillingONDiskNameDao diskNameDao,
                                BillingOnLookupService lookupService) {
        this.demographicManager = demographicManager;
        this.cheaderDao = cheaderDao;
        this.headerDao = headerDao;
        this.filenameDao = filenameDao;
        this.siteDao = siteDao;
        this.itemDao = itemDao;
        this.billingServiceDao = billingServiceDao;
        this.diskNameDao = diskNameDao;
        this.lookupService = lookupService;

        // Initialization previously done in the no-arg constructor — folded
        // in here so the prototype's per-instance state is fully built by
        // Spring before any setter is called.
        formatter = new SimpleDateFormat("yyyyMMdd"); // yyyyMMddHmm");
        today = new java.util.Date();
        output = formatter.format(today);

        // Multisite: pre-cache site short names for the disk filename builders.
        clinicShortName = new HashMap<String, String>();
        List<Site> sites = siteDao.getAllSites();
        for (Site s : sites) {
            clinicShortName.put(s.getName(), s.getShortName());
        }
    }

    private String batchHeader;
    private BigDecimal bdFee = BillingMoney.zeroAmount();
    private BigDecimal BigTotal = BillingMoney.zeroAmount();
    private DateRange dateRange;
    private String[] errorParams;
    private String diagcode;
    private String eFlag = "1";
    private String errorMsg = "";
    private String errorPartMsg = "";
    private String fee;
    private SimpleDateFormat formatter;
    private String hcCount = "";
    private String hcFirst = "";
    private String hcFlag = "";
    private String hcLast = "";
    private int healthcardCount = 0;
    private String htmlCode = "";
    private String htmlContent = "";
    private String htmlFilename;
    private String htmlFooter = "";
    private String htmlHeader = "";
    private String htmlValue = "";
    private int invCount = 0;
    private String m_Flag = "";
    private String ohipClaim;
    private String ohipFilename;
    private File lastRenamedOriginalFile;
    private File lastRenamedBackupFile;
    private String ohipReciprocal;
    private String ohipRecord;
    private String ohipVer;
    private String output;
    private int patientCount = 0;
    private String pCount = "";
    private String providerNo;
    private String rCount = "";
    private int recordCount = 0;
    private String referral;
    private java.util.Date today;
    private String totalAmount;
    private String value;
    private String clinicBgColor;
    private HashMap<String, String> clinicShortName;
    private boolean summaryView;
    private String contextPath = "";
    private final List<String> pendingBilledClaimHeaderIds = new ArrayList<>();
    private String pendingBatchHeaderId;
    private int pendingHealthcardCount;
    private int pendingPatientCount;
    private int pendingRecordCount;
    // APOS (U+0027) is used instead of a literal ' to avoid the pre-commit
    // SQL-injection scanner's quote-sandwich false positive on '" + var + "'.
    private static final String APOS = "\u0027";

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath == null ? "" : contextPath;
    }

    private static String onclickPopup(int w, int h, String url) {
        return "popupPage(" + w + "," + h + "," + APOS + url + APOS + ");return false;";
    }


    public BigDecimal getBigTotal() {
        return BigTotal;
    }

    public int getRecordCount() {
        return recordCount;
    }

    private String buildBatchHeader() {
        String ret = "";
        errorFatalMsg = "";
        ret = currentBatchHeader.getTranscId() + currentBatchHeader.getRecId() + currentBatchHeader.getSpecId() + currentBatchHeader.getMohOffice() + currentBatchHeader.getBatchId() + space(6) + currentBatchHeader.getGroupNum() + currentBatchHeader.getProviderRegNum() + currentBatchHeader.getSpecialty() + space(42) + "\r";
        if (ret.length() != 80) errorFatalMsg += "Batch Header length wrong! - " + currentBatchHeader.getProviderRegNum() + "<br>";
        return ret;
    }

    private void checkBatchHeader() {
        if (currentBatchHeader.getSpecId().length() != 3) {
            errorPartMsg = "Batch Header: Version code wrong! - " + currentBatchHeader.getProviderRegNum() + "<br>";
        }
        if (currentBatchHeader.getMohOffice().length() != 1) {
            errorPartMsg += "Batch Header: Health Office Code wrong!<br>";
        }
        if (currentBatchHeader.getGroupNum().length() != 4) {
            errorPartMsg += "Batch Header: GroupNo. wrong!<br>";
        }
        if (currentBatchHeader.getProviderRegNum().length() != 6) {
            errorPartMsg += "Batch Header: Provider OHIP No. wrong!<br>";
        }
        if (currentBatchHeader.getSpecialty().length() != 2) {
            errorPartMsg += "Batch Header: Specialty Code wrong!<br>";
        }
        errorMsg += errorPartMsg;
    }

    /**
     * Build a {@link BillingClaimHeaderDto} from a {@link BillingONCHeader1}
     * entity for the OHIP claim-file write path. Shared between the
     * per-provider and per-site flows; the only difference is whether
     * {@code billing_time} is formatted as a date or a time.
     *
     * @param h                          source header row
     * @param bNo                        bill-no string the caller already resolved
     * @param billingTimeAsTimeString    true: use {@code toTimeString} (per-site path);
     *                                   false: use {@code toDateString} (per-provider path)
     * @throws BillingDataLoadException on a corrupt admission_date — the throw
     *         aborts the surrounding batch and routes to the data-load
     *         operator banner instead of writing a claim with the field
     *         silently stripped.
     */
    private BillingClaimHeaderDto buildClaimHeaderDto(BillingONCHeader1 h, String bNo,
                                                      boolean billingTimeAsTimeString) {
        BillingClaimHeaderDto dto = new BillingClaimHeaderDto();
        dto = dto.withId(bNo);
        dto = dto.withTransactionId(h.getTranscId());
        dto = dto.withRecordId(h.getRecId());
        dto = dto.withHin(h.getHin());
        dto = dto.withVer(h.getVer());
        dto = dto.withDob(h.getDob());

        dto = dto.withPayProgram(h.getPayProgram());
        dto = dto.withPayee(h.getPayee());
        dto = dto.withReferralNumber(h.getRefNum());
        dto = dto.withFacilityNumber(h.getFaciltyNum());
        try {
            dto = dto.withAdmissionDate(ConversionUtils.toDateString(h.getAdmissionDate()));
        } catch (ParseException e) {
            // A corrupt admission_date on a single bill must abort the
            // whole batch — the alternative (warn + continue) writes a
            // claim with admission_date stripped to the MOH submission
            // file, and the operator sees "file generated" with no
            // signal that one row inside is malformed. Throw the typed
            // BillingDataLoadException (data integrity, not file write)
            // so the operator-facing banner names the right cause.
            throw new BillingDataLoadException(
                    "OHIP claim file aborted: bill " + h.getId()
                            + " has unparseable admission_date", e,
                    BillingDataLoadException.Phase.DATE_PARSE,
                    java.util.Map.of(
                            "billId", String.valueOf(h.getId()),
                            "field", "admission_date"));
        }
        dto = dto.withReferringLabNumber(h.getRefLabNum());
        dto = dto.withManualReview(h.getManReview());
        dto = dto.withLocation(h.getLocation());

        dto = dto.withDemographicNo("" + h.getDemographicNo());
        dto = dto.withProviderNo(h.getProviderNo());
        dto = dto.withAppointmentNo("" + h.getAppointmentNo());
        dto = dto.withDemographicName(h.getDemographicName());
        dto = dto.withSex(h.getSex());
        dto = dto.withProvince(h.getProvince());

        dto = dto.withBillingDate(ConversionUtils.toDateString(h.getBillingDate()));
        dto = dto.withBillingTime(billingTimeAsTimeString
                ? ConversionUtils.toTimeString(h.getBillingTime())
                : ConversionUtils.toDateString(h.getBillingTime()));

        dto = dto.withTotal(h.getTotal() == null ? "0.00" : BillingMoney.format(h.getTotal()));
        dto = dto.withPaid(h.getPaid() == null ? "0.00" : BillingMoney.format(h.getPaid()));
        dto = dto.withStatus(h.getStatus());
        dto = dto.withComment(h.getComment());
        dto = dto.withVisitType(h.getVisitType());
        dto = dto.withProviderOhipNo(h.getProviderOhipNo());
        dto = dto.withProviderRmaNo(h.getProviderRmaNo());
        dto = dto.withAppointmentProviderNo(h.getApptProviderNo());
        dto = dto.withAssistantProviderNo(h.getAsstProviderNo());
        dto = dto.withCreator(h.getCreator());

        dto = dto.withClinic(h.getClinic());
        return dto;
    }

    /** Build a {@link BillingClaimItemDto} from a {@link BillingONItem}. */
    private BillingClaimItemDto buildClaimItemDto(BillingONItem item) {
        BillingClaimItemDto dto = new BillingClaimItemDto();
        dto = dto.withTransactionId(item.getTranscId());
        dto = dto.withRecordId(item.getRecId());
        dto = dto.withServiceCode(item.getServiceCode());
        dto = dto.withFee(requireExportFee(item));
        dto = dto.withServiceNumber(item.getServiceCount());
        dto = dto.withServiceDate(ConversionUtils.toDateString(item.getServiceDate()));
        String diagcode = item.getDx();
        diagcode = ":::".equals(diagcode) ? "   " : diagcode;
        dto = dto.withDx(diagcode);
        dto = dto.withDx1(item.getDx1());
        dto = dto.withDx2(item.getDx2());
        dto = dto.withStatus(item.getStatus());
        return dto;
    }

    private static String requireExportFee(BillingONItem item) {
        return BillingMoney.format(BillingMoney.parseNonNegativeAmount(
                item.getFee(),
                "fee for OHIP export item " + item.getId()));
    }

    private String buildHeader1(LoggedInInfo loggedInInfo) {
        String ret = "";
        String header1 = null;
        String header2 = "";
        updateDemoData(loggedInInfo, currentClaimHeader);
        String str1Hin = isRMB() ? space(10) : leftJustify(" ", 10, currentClaimHeader.getHin());
        String ver = isRMB() ? space(2) : leftJustify(" ", 2, currentClaimHeader.getVer());
        String dob = leftJustify(" ", 8, currentClaimHeader.getDob().replaceAll("-", ""));
        referral = currentClaimHeader.referralNumber().length() > 1 ? "R" : "";
        hcFlag = isRMB() ? "H" : "";
        m_Flag = currentClaimHeader.manualReview().equals("Y") ? "M" : "";
        _logger.debug("buildHeader1(ver = {})", ver);

        header1 = currentClaimHeader.transactionId() + currentClaimHeader.recordId() + str1Hin + ver + dob + rightJustify("0", 8, currentClaimHeader.getId()) + currentClaimHeader.payProgram() + currentClaimHeader.getPayee() + rightJustify(" ", 6, currentClaimHeader.referralNumber()) + rightJustify(" ", 4, currentClaimHeader.facilityNumber().equals("0000") ? "" : currentClaimHeader.facilityNumber()) + rightJustify(" ", 8, getCompactDateStr(currentClaimHeader.admissionDate() == null ? "" : currentClaimHeader.admissionDate())) + rightJustify(" ", 4, currentClaimHeader.referringLabNumber())
                + rightJustify(" ", 1, currentClaimHeader.manualReview()) + leftJustify(" ", 4, currentClaimHeader.getLocation().equals("0000") ? "" : currentClaimHeader.getLocation()) + space(11) + space(6);
        checkHeader1();
        if (isRMB()) {
            header2 = buildHeader2();
        }

        ret = "\n" + header1 + "\r" + header2;
        if (header1.length() != 79) errorFatalMsg += "Header 1 length wrong! - " + currentClaimHeader.getId() + "<br>";

        return ret;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private String buildHeader2() {
        healthcardCount++;
        String str1Hin = leftJustify(" ", 12, currentClaimHeader.getHin());
        String strDemoName = currentClaimHeader.demographicName();
        hcLast = strDemoName.substring(0, strDemoName.indexOf(",")).toUpperCase();
        hcFirst = strDemoName.substring(strDemoName.indexOf(",") + 1).toUpperCase();
        hcLast = hcLast.replaceAll("\\W", "");
        hcFirst = hcFirst.replaceAll("\\W", "");
        hcLast = hcLast.length() < 9 ? (hcLast + space(9 - hcLast.length())) : (hcLast.substring(0, 9));
        hcFirst = hcFirst.length() < 5 ? (hcFirst + space(5 - hcFirst.length())) : (hcFirst.substring(0, 5));

        String header2 = "\n" + "HER" + str1Hin + hcLast + hcFirst + currentClaimHeader.getSex() + currentClaimHeader.getProvince() + space(47) + "\r";
        if (header2.length() != 81)
            errorFatalMsg += "Header 2 length wrong! - " + currentClaimHeader.getId() + " length = " + header2.length() + "<br>";
        return header2;
    }

    private String buildHTMLContentHeader() {
        String ret = null;
        ret = "<script type=\"text/JavaScript\">\n<!--\nfunction popupPage(vheight,vwidth,varpage) {\n  var page = \"\" + varpage;\n";
        ret += "  windowprops = \"height=\"+vheight+\",width=\"+vwidth+\",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0\";\n";
        ret += "  var popup=window.open(page, \"billcorrection\", windowprops);\n";
        ret += "    if (popup != null) {\n";
        ret += "    if (popup.opener == null) {\n";
        ret += "      popup.opener = self;\n";
        ret += "    }\n";
        ret += "    popup.focus();\n";
        ret += "  }\n";
        ret += "}\n//-->\n</script>\n";
        ret += "\n<table width='100%' border='0' cellspacing='0' cellpadding='2' class='myDarkGreen'>\n" + "<tr><td colspan='4' class='myGreen'>OHIP Invoice for OHIP No." + currentBatchHeader.getProviderRegNum() + "</td><td colspan='4' class='myGreen'>Payment date of " + output + "\n</td></tr>";
        ret += "\n<tr><td class='myGreen'>ACCT NO</td>" + "<td width='25%' class='myGreen'>NAME</td><td class='myGreen'>RO</td><td class='myGreen'>DOB</td><td class='myGreen'>Sex</td><td class='myGreen'>HEALTH #</td>" + "<td class='myGreen'>BILLDATE</td><td class='myGreen'>CODE</td>" + "<td align='right' class='myGreen'>BILLED</td>" + "<td align='right' class='myGreen'>DX</td><td align='right' class='myGreen'>Comment</td></tr>";
        return ret;
    }

    private String buildSiteHTMLContentHeader() {
        String ret = null;
        ret = "<script type=\"text/JavaScript\">\n<!--\nfunction popupPage(vheight,vwidth,varpage) {\n  var page = \"\" + varpage;\n";
        ret += "  windowprops = \"height=\"+vheight+\",width=\"+vwidth+\",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0\";\n";
        ret += "  var popup=window.open(page, \"billcorrection\", windowprops);\n";
        ret += "    if (popup != null) {\n";
        ret += "    if (popup.opener == null) {\n";
        ret += "      popup.opener = self;\n";
        ret += "    }\n";
        ret += "    popup.focus();\n";
        ret += "  }\n";
        ret += "}\n//-->\n</script>\n";
        ret += "\n<table width='100%' border='0' cellspacing='0' cellpadding='2' class='myIvory'>\n" + "<tr><td colspan='4' class='myGreen'>OHIP Invoice for OHIP No." + html(currentBatchHeader.getProviderRegNum()) + "</td><td colspan='5' class='myGreen'>Payment date of " + html(output) + "\n</td></tr>";
        ret += "\n<tr><td class='myGreen'>ACCT NO</td>" + "<td width='25%' class='myGreen'>NAME</td><td class='myGreen'>HEALTH #</td>" + "<td class='myGreen'>BILLDATE</td><td class='myGreen'>CODE</td>" + "<td align='right' class='myGreen'>BILLED</td>" + "<td align='right' class='myGreen'>DX</td><td align='right' class='myGreen'>Comment</td>" + "<td align='centre' class='myGreen'>SITE</td></tr>";
        return ret;
    }

    private String buildHTMLContentRecord(LoggedInInfo loggedInInfo, int invCount, boolean simulation) {
        String ret = null;
        ret = "";
        String styleClass = patientCount % 2 == 0 ? "myLightBlue" : "myIvory";
        String providerDomToken = BillingDomIdTokens.sanitize(providerNo);
        String summaryRecordAttribute = summaryView
                ? "style='display:none;' class='record" + SafeEncode.forHtmlAttribute(providerDomToken) + "'"
                : "";
        if (invCount == 0) {
            Demographic demo = demographicManager.getDemographic(loggedInInfo, currentClaimHeader.demographicNo());
            ret += "\n<tr " + summaryRecordAttribute + ">";
            String safeDemoName = SafeEncode.forHtml(currentClaimHeader.demographicName());
            String safeBillId = SafeEncode.forUriComponent(String.valueOf(currentClaimHeader.getId()));
            String safeDemoNo = SafeEncode.forUriComponent(String.valueOf(currentClaimHeader.demographicNo()));
            String safeIdHtml = SafeEncode.forHtml(String.valueOf(currentClaimHeader.getId()));
            String tdOpen = "<td class=\"" + styleClass + "\">";
            if (simulation) {
                String billOnclick = onclickPopup(1000, 800, contextPath + "/billing/CA/ON/BillingONCorrection?billing_no=" + safeBillId);
                String demoOnclick = onclickPopup(720, 740, contextPath + "/demographic/DemographicEdit?demographic_no=" + safeDemoNo);
                ret += tdOpen + SafeEncode.forHtml(currentClaimHeader.providerOhipNo()) + "</td>"
                        + tdOpen + "<a href=\"javascript:void(0);\"  onclick=\"" + billOnclick + "\">" + safeIdHtml + "</a></td>"
                        + tdOpen + "<a href=\"javascript:void(0);\" onclick=\"" + demoOnclick + "\">" + safeDemoName + "</a></td>";
            } else {
                ret += tdOpen + safeIdHtml + "</td>"
                        + tdOpen + safeDemoName + "</td>";
            }
            ret += "<td class='" + styleClass + "'>" + html(demo.getRosterStatus()) + "</td>"
                    + "<td class='" + styleClass + "'>" + html(demo.getBirthDayAsString()) + "</td>"
                    + "<td class='" + styleClass + "'>" + html(demo.getSex()) + "</td>"
                    + "<td class='" + styleClass + "'>" + html(currentClaimHeader.getHin(), currentClaimHeader.getVer()) + "</td>"
                    + "<td class='" + styleClass + "'>" + html(currentClaimHeader.billingDate()) + "</td>"
                    + "<td class='" + styleClass + "'>" + html(currentItem.serviceCode()) + "</td>"
                    + "<td align='right' class='" + styleClass + "'>" + html(currentItem.getFee()) + "</td>"
                    + "<td align='right' class='" + styleClass + "'>" + html(currentItem.getDx()) + "</td>"
                    + "<td class='" + styleClass + "'> &nbsp; &nbsp;" + referral + hcFlag + m_Flag + " </td></tr>";
        } else {
            ret = "\n<tr " + summaryRecordAttribute + ">" + "<td class='" + styleClass + "'>&nbsp;</td>" + "<td class='" + styleClass + "'>&nbsp;</td> <td class='" + styleClass + "'>&nbsp;</td><td class='" + styleClass + "'>&nbsp;</td><td class='" + styleClass + "'>&nbsp;</td><td class='" + styleClass + "'>&nbsp;</td>" + "<td class='" + styleClass + "'>&nbsp;</td> <td class='" + styleClass + "'>&nbsp;</td>" + "<td class='" + styleClass + "'>"
                    + html(currentItem.serviceCode()) + "</td><td align='right' class='" + styleClass + "'>" + html(currentItem.getFee()) + "</td><td align='right' class='" + styleClass + "'>" + html(currentItem.getDx()) + "</td><td class='" + styleClass + "'>&nbsp;</td></tr>";
        }
        return ret;
    }

    private String buildSiteHTMLContentRecord(int invCount) {
        String ret = null;
        if (invCount == 0) {
            String safeDemoName = SafeEncode.forHtml(currentClaimHeader.demographicName());
            String safeBillId = SafeEncode.forUriComponent(String.valueOf(currentClaimHeader.getId()));
            String safeDemoNo = SafeEncode.forUriComponent(String.valueOf(currentClaimHeader.demographicNo()));
            String safeIdHtml = SafeEncode.forHtml(String.valueOf(currentClaimHeader.getId()));
            String billOnclick = onclickPopup(720, 740, contextPath + "/billing/CA/ON/BillingONCorrection?billing_no=" + safeBillId);
            String demoOnclick = onclickPopup(720, 740, contextPath + "/demographic/DemographicEdit?demographic_no=" + safeDemoNo);
            ret = "\n<tr><td class=\"myIvory\"><a href=\"#\" onclick=\"" + billOnclick + "\">" + safeIdHtml + "</a></td>"
                    + "<td class=\"myIvory\"><a href=\"#\" onclick=\"" + demoOnclick + "\">" + safeDemoName + "</a></td><td class='myIvory'>" + html(currentClaimHeader.getHin(), currentClaimHeader.getVer())
                    + "</td><td class='myIvory'>" + html(currentClaimHeader.billingDate()) + "</td><td class='myIvory'>" + html(currentItem.serviceCode()) + "</td><td align='right' class='myIvory'>" + html(currentItem.getFee()) + "</td><td align='right' class='myIvory'>" + html(currentItem.getDx()) + "</td><td class='myIvory'> &nbsp; &nbsp;" + referral + hcFlag + m_Flag + " </td>" + "<td bgcolor='" + clinicBgColor + "'> " + html(clinicShortName.get(currentClaimHeader.getClinic())) + "</td></tr>";
        } else {
            ret = "\n<tr><td class='myIvory'>&nbsp;</td> <td class='myIvory'>&nbsp;</td>" + "<td class='myIvory'>&nbsp;</td> <td class='myIvory'>&nbsp;</td>" + "<td class='myIvory'>" + html(currentItem.serviceCode()) + "</td><td align='right' class='myIvory'>" + html(currentItem.getFee()) + "</td><td align='right' class='myIvory'>" + html(currentItem.getDx()) + "</td><td class='myIvory'>&nbsp;</td>" + "<td bgcolor='" + clinicBgColor + "'> " + html(clinicShortName.get(currentClaimHeader.getClinic())) + "</td></tr>";
        }
        return ret;
    }

    private static String html(Object... values) {
        StringBuilder raw = new StringBuilder();
        if (values != null) {
            for (Object value : values) {
                if (value != null) {
                    raw.append(value);
                }
            }
        }
        return SafeEncode.forHtml(raw.toString());
    }

    private String buildHTMLContentTrailer(boolean simulation) {
        if (!simulation) {
            htmlContent += "\n<tr><td colspan='11' class='myIvory'>&nbsp;</td></tr><tr><td colspan='7' class='myIvory'>OHIP No: " + currentBatchHeader.getProviderRegNum() + ": " + pCount + " RECORDS PROCESSED</td><td colspan='4' class='myIvory'>TOTAL: " + BigTotal.toString() + "\n</td></tr>" + "\n</table>";
        }

        String checkSummary = "";

        // Error is 0 if there is no error, otherwise 1 for a normal error, 2 for a fatal error, 3 for both.
        int error = 0 | (errorMsg.equals("") ? 0 : 1) | (errorFatalMsg.equals("") ? 0 : 2);
        String totalError = errorMsg + (error == 3 ? "<br>" : "") + errorFatalMsg;
        String errorMsgHtml = "";
        int errorCount = totalError.split("<br>").length;
        if (error > 0) {
            if (errorCount > 3) {
                int remainingErrors = totalError.indexOf("<br>", totalError.indexOf("<br>", totalError.indexOf("<br>") + 4) + 4);
                errorMsgHtml = "<tr><td colspan='12'><font color='black'>" + totalError.substring(0, remainingErrors) + "</font></td></tr>";
                errorMsgHtml += "<tr><td colspan='12'><font color='black'>" + "<button onclick='jQuery(this).next().show();jQuery(this).hide();return false;'>Click here to see remaining " + (errorCount - 3) + " results.</button>" + "<div style='display:none;'>" + totalError.substring(remainingErrors + 4) + "</div>" + "</font></td></tr>";
            } else {
                errorMsgHtml = "<tr><td colspan='12'><font color='black'>" + totalError + "</font></td></tr>";
            }
        }
        if (error == 0) {
            checkSummary = simulation ? "\n<tr><td colspan='12'><table border='0' width='100%' bgcolor='green'><tr><td>Pass</td></tr></table></td></tr>" : "\n<table border='0' width='100%' bgcolor='green'><tr><td>Pass</td></tr></table>";
        } else {
            checkSummary = simulation ? "\n<tr><td colspan='12' style='padding:2px;'><table border='0' width='100%' style='border: 1px dashed red'><tr style='background-color: #CCCCCC;'><td>FAIL - Please correct the errors and run this simulation again!</td></tr>" + errorMsgHtml + "</table></td></tr>" : "\n<table border='0' width='100%' bgcolor='orange'><tr><td>Please correct the errors and run this simulation again!</td></tr></table>";
        }

        htmlValue += htmlContent + checkSummary;
        if (!simulation) {
            htmlHeader = "<html><body><style type='text/css'><!-- .myGreen{  font-family: Arial, Helvetica, sans-serif;  font-size: 12px; font-style: normal;  line-height: normal;  font-weight: normal;  font-variant: normal;  text-transform: none;  color: #003366;  text-decoration: none; --></style>";
            htmlFooter = "</body></html>";
        } else {
            htmlValue += "<tr><td colspan='12'>&nbsp;</td></tr>";
        }
        htmlCode = htmlHeader + htmlValue + htmlFooter;
        return htmlCode;

    }

    private String buildSiteHTMLContentTrailer() {
        htmlContent += "\n<tr><td colspan='9' class='myIvory'>&nbsp;</td></tr><tr><td colspan='4' class='myIvory'>OHIP No: " + currentBatchHeader.getProviderRegNum() + ": " + pCount + " RECORDS PROCESSED</td><td colspan='5' class='myIvory'>TOTAL: " + BigTotal.toString() + "\n</td></tr>" + "\n</table>";
        // writeFile(value);
        String checkSummary = errorMsg.equals("") ? "\n<table border='0' width='100%' bgcolor='green'><tr><td>Pass</td></tr></table>" : "\n<table border='0' width='100%' bgcolor='orange'><tr><td>Please correct the errors and run this simulation again!</td></tr></table>";
        htmlValue += htmlContent + checkSummary;
        htmlHeader = "<html><body><style type='text/css'><!-- .myGreen{  font-family: Arial, Helvetica, sans-serif;  font-size: 12px; font-style: normal;  line-height: normal;  font-weight: normal;  font-variant: normal;  text-transform: none;  color: #003366;  text-decoration: none; --></style>";
        htmlFooter = "</body></html>";
        htmlCode = htmlHeader + htmlValue + htmlFooter;
        return htmlCode;
    }

    private String buildItem() {
        String ret = currentItem.transactionId() + currentItem.recordId() + currentItem.serviceCode() + space(2) + rightJustify("0", 6, currentItem.getFee().replaceAll("\\.", "")) + rightJustify("0", 2, currentItem.serviceNumber()) + currentItem.serviceDate().replaceAll("-", "") + leftJustify(" ", 4, currentItem.getDx()) + space(11) + space(5) + space(2) + space(6) + space(25);
        if (ret.length() != 79) errorFatalMsg += "Item length wrong! - " + currentClaimHeader.getId() + "<br>";
        return "\n" + ret + "\r";
    }

    private String buildTrailer() {
        String ret = "\n" + "HEE" + rightJustify("0", 4, pCount) + rightJustify("0", 4, hcCount) + rightJustify("0", 5, rCount) + space(63) + "\r";
        return ret;
    }

    /**
     * isRMB
     * <p>
     * Identifies if this is a reciprocal medical billing invoice or not
     *
     * @return true if RMB, false otherwise
     */
    private boolean isRMB() {
        return "RMB".equals(currentClaimHeader.payProgram());
    }

    private void checkHeader1() {
        if (!currentClaimHeader.referralNumber().equals("") && currentClaimHeader.referralNumber().length() != 6)
            errorPartMsg = "Header1: Referral Doc. No. wrong!<br>";
        if (currentClaimHeader.visitType() != null && currentClaimHeader.visitType().compareTo("00") != 0) {
            if ((currentClaimHeader.facilityNumber() != null && currentClaimHeader.facilityNumber().length() != 4) || currentClaimHeader.facilityNumber() == null) {
                errorPartMsg += "Header1: outPatient Visit. wrong!<br>";
            }
        }
        if (currentClaimHeader.getVer() != null && (currentClaimHeader.getVer().length() > 2 || "##".equals(currentClaimHeader.getVer())))
            errorPartMsg += "Header1: Ver. code wrong!<br>";

        //If HIN is not out of province and is not 10 digits, mark as invalid
        if (currentClaimHeader.getHin() == null || !(isRMB() || currentClaimHeader.getHin().matches("[0-9]{10}"))) {
            errorPartMsg += "Header1: HIN is invalid!<br>";
        }

        if (errorPartMsg.length() > 0)
            errorMsg += currentClaimHeader.getId() + " - " + errorPartMsg;
    }


    private void checkItem() {
        if (currentItem.serviceCode().trim().length() != 5) errorPartMsg = "Item: Service Code wrong!<br>";
        errorMsg += errorPartMsg;
    }

    private void checkNoDetailRecord(int invCount) {
        if (invCount == 0) errorPartMsg = "The billing no:" + currentClaimHeader.getId() + " should be marked as 'Delete'.<br>";
        errorMsg += errorPartMsg;
    }

    private String printErrorPartMsg() {
        String ret = "";
        ret = errorPartMsg.length() > 0 ? ("\n<tr bgcolor='yellow'><td colspan='11'><font color='red'>" + errorPartMsg + "</font></td></tr>") : "";
        errorPartMsg = "";
        return ret;
    }

    private String printSiteErrorPartMsg() {
        String ret = "";
        ret = errorPartMsg.length() > 0 ? ("\n<tr bgcolor='yellow'><td colspan='9'><font color='red'>" + errorPartMsg + "</font></td></tr>") : "";
        errorPartMsg = "";
        return ret;
    }

    // FindSecBugs POTENTIAL_XML_INJECTION: summary detail rows are generated by this service's encoded HTML row builders; wrapper values are encoded before rendering.
    @SuppressFBWarnings(value = "POTENTIAL_XML_INJECTION", justification = "summary detail rows are generated by this service's HTML row builders; summary wrapper values are encoded before rendering")
    private void appendGeneratedClaimRows(StringBuilder html, String generatedRowsHtml) {
        html.append(generatedRowsHtml);
    }

    /**
     * Builds the batch claim export state for a provider using the default
     * MOH office selection path.
     *
     * @see #createBillingFileStr(LoggedInInfo, String, String[], boolean, String, boolean, boolean)
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public void createBillingFileStr(LoggedInInfo loggedInInfo, String bid, String[] status, boolean simulation, String mohOffice, boolean summaryView) {
        createBillingFileStr(loggedInInfo, bid, status, simulation, mohOffice, summaryView, false);
    }

    /**
     * Builds the fixed-width OHIP claim file body and companion HTML preview in
     * this prototype instance. When {@code simulation} is false and
     * {@code eFlag} is {@code "1"}, this stages the included claim-header and
     * batch summary updates for {@link #finalizeGeneratedDisk()}.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public void createBillingFileStr(LoggedInInfo loggedInInfo, String bid, String[] status, boolean simulation, String mohOffice, boolean summaryView, boolean useProviderMOH) {
        this.summaryView = summaryView;
        resetGeneratedDiskFinalization();
        try {
            if (!"0".equals(bid)) { // for simulation only
                getBatchHeaderObj(bid);
                if (useProviderMOH) {
                    ProviderBillCenter pbc = new ProviderBillCenter();
                    String billCenter = pbc.getBillCenter(providerNo);
                    if (billCenter != null && billCenter.length() == 1) {
                        currentBatchHeader.setMohOffice(billCenter);
                    } else {
                        currentBatchHeader.setMohOffice(mohOffice);
                    }
                } else if (mohOffice != null) {
                    currentBatchHeader.setMohOffice(mohOffice);
                }
            }

            if (!simulation) {
                checkBatchHeader();
                batchHeader = buildBatchHeader();
                htmlValue = buildHTMLContentHeader();
            }
            // Initialize the fixed-width file body with the batch header.
            value = batchHeader;
            StringBuilder valueBuilder = new StringBuilder(String.valueOf(value));
            StringBuilder htmlContentBuilder = new StringBuilder(htmlContent == null ? "" : htmlContent);

            BigDecimal proTotal = BillingMoney.zeroAmount();
            int proItem = 0;
            String ohipNo = "";

            List<BillingONCHeader1> claimHeaders = cheaderDao.findByProviderStatusAndDateRange(providerNo, Arrays.asList(status), dateRange)
                    .stream()
                    .filter(this::shouldIncludeClaimHeader)
                    .toList();
            Map<Integer, List<BillingONItem>> itemsByHeaderId = claimItemsByHeaderId(claimHeaders);

            for (BillingONCHeader1 h : claimHeaders) {
                String bNo = "" + h.getId();

                patientCount++;
                currentClaimHeader = buildClaimHeaderDto(h, bNo, false);
                ohipNo = currentClaimHeader.providerOhipNo();

                valueBuilder.append(buildHeader1(loggedInInfo));
                if (!simulation) {
                    htmlContentBuilder.append(printErrorPartMsg());
                } else {
                    errorPartMsg = "";
                }

                // build billing detail
                invCount = 0;

                boolean hasSliCode = currentClaimHeader.getLocation().trim().length() == 3;
                for (BillingONItem boi : itemsByHeaderId.getOrDefault(h.getId(), List.of())) {
                    currentItem = buildClaimItemDto(boi);
                    recordCount++;
                    diagcode = currentItem.getDx();
                    fee = boi.getFee();

                    if (!hasSliCode) {
                        if (billingServiceDao.codeRequiresSLI(currentItem.serviceCode())) {
                            errorPartMsg = "Service code '" + currentItem.serviceCode() + "' requires an SLI code. <br/>";
                        }
                    }

                    bdFee = BillingMoney.amount(fee);
                    proTotal = proTotal.add(bdFee);
                    BigTotal = BigTotal.add(bdFee);
                    _logger.debug("createBillingFileStr(BigTotal = {})", BigTotal);
                    checkItem();
                    valueBuilder.append(buildItem());
                    // The MOH claim record concatenated into `value` embeds the
                    // patient HIN (10 chars) and DOB (8 chars) — see buildHeader1
                    // above. Logging the running `value` is a HIPAA/PIPEDA leak;
                    // do NOT reinstate.
                    htmlContentBuilder.append(buildHTMLContentRecord(loggedInInfo, invCount, simulation));
                    if (!simulation) {
                        htmlContentBuilder.append(printErrorPartMsg());
                    } else {
                        errorPartMsg = "";
                    }
                    invCount++;
                    proItem++;
                }
                checkNoDetailRecord(invCount);
                if (!simulation) {
                    htmlContentBuilder.append(printErrorPartMsg());
                } else {
                    errorPartMsg = "";
                }
                if (eFlag.compareTo("1") == 0) {
                    stageHeaderBilledBatchId(currentClaimHeader.getId(), currentBatchHeader.getId());
                }
            }
            hcCount = hcCount + healthcardCount;
            pCount = pCount + patientCount;
            rCount = rCount + recordCount;
            // Historical total multiplier was removed; totals are already stored in dollars.

            if (summaryView) {
                String items = htmlContentBuilder.toString();
                String providerDomToken = BillingDomIdTokens.sanitize(providerNo);
                String providerHtmlAttribute = SafeEncode.forHtmlAttribute(providerDomToken);
                String providerJavaScriptAttribute = SafeEncode.forJavaScriptAttribute(providerDomToken);
                htmlContentBuilder.setLength(0);
                htmlContentBuilder.append("<tr><td class='myIvory'>")
                        .append(SafeEncode.forHtmlContent(ohipNo))
                        .append("</td><td class='myIvory'>")
                        .append(proItem)
                        .append("</td><td class='myIvory'>")
                        .append(SafeEncode.forHtmlContent(String.valueOf(proTotal)))
                        .append("</td><td class='myIvory' colspan='6'><button id='recordShowButton")
                        .append(providerHtmlAttribute)
                        .append("' onclick='jQuery(\".record")
                        .append(providerJavaScriptAttribute)
                        .append("\").show();jQuery(this).hide();jQuery(\"#recordHideButton")
                        .append(providerJavaScriptAttribute)
                        .append("\").show();return false;'>Show record details.</button><button id='recordHideButton")
                        .append(providerHtmlAttribute)
                        .append("' style='display:none;' onclick='jQuery(\".record")
                        .append(providerJavaScriptAttribute)
                        .append("\").hide();jQuery(this).hide();jQuery(\"#recordShowButton")
                        .append(providerJavaScriptAttribute)
                        .append("\").show();return false;'>Hide record details.</button></td></tr>")
                        .append("\n<tr style='display:none;' class='record")
                        .append(providerHtmlAttribute)
                        .append("'><td class='myGreen'>OHIP NO</td><td class='myGreen'>ACCT NO</td>")
                        .append("<td width='25%' class='myGreen'>NAME</td><td class='myGreen'>RO</td><td class='myGreen'>DOB</td><td class='myGreen'>Sex</td><td class='myGreen'>HEALTH #</td>")
                        .append("<td class='myGreen'>BILLDATE</td><td class='myGreen'>CODE</td>")
                        .append("<td align='right' class='myGreen'>BILLED</td>")
                        .append("<td align='right' class='myGreen'>DX</td><td align='right' class='myGreen'>Comment</td></tr>");
                appendGeneratedClaimRows(htmlContentBuilder, items);
            }

            BigTotal = BigTotal.setScale(2, RoundingMode.HALF_UP);
            valueBuilder.append(buildTrailer());
            value = valueBuilder.toString();
            htmlContent = htmlContentBuilder.toString();

            htmlCode = buildHTMLContentTrailer(simulation);
            // writeHtml(htmlCode);
            ohipReciprocal = String.valueOf(hcCount);
            ohipRecord = String.valueOf(rCount);
            ohipClaim = String.valueOf(pCount);
            totalAmount = BigTotal.toString();
            if (eFlag.compareTo("1") == 0) {
                stageBatchHeaderSum(currentBatchHeader.getId(),
                        healthcardCount, patientCount, recordCount);
            }
        } catch (BillingFileWriteException | BillingDataLoadException domain) {
            // Already a typed domain exception — let it through so the
            // global Struts mapping routes to the right operator page
            // (file-write banner vs data-load banner). Wrapping these
            // would re-introduce the misdirection the typed hierarchy exists
            // to prevent. Log at ERROR before rethrow so the cause chain
            // (with the raw malformed value from the entity getter) lands
            // in the application log alongside the operator-facing banner.
            _logger.error("OHIP claim file aborted ({}): {}",
                    domain.getClass().getSimpleName(), domain.getMessage(), domain);
            throw domain;
        } catch (Exception e) {
            // Propagate partial-write failures so Struts renders the file-write error page.
            _logger.error("OHIP claim file generation failed", e);
            throw new BillingFileWriteException(
                    "OHIP claim file generation failed", e);
        }
    }

    /**
     * Builds a site-grouped OHIP claim file body and companion HTML preview in
     * this prototype instance, marking included claim headers as billed when
     * {@code eFlag} is {@code "1"}.
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public void createSiteBillingFileStr(LoggedInInfo loggedInInfo, String bid, String[] statuses) {
        resetGeneratedDiskFinalization();

        try {
            if (!"0".equals(bid)) { // for simulation only
                getBatchHeaderObj(bid);
            }
            checkBatchHeader();
            batchHeader = buildBatchHeader();
            htmlValue = buildSiteHTMLContentHeader();
            // Initialize the fixed-width file body with the batch header.
            value = batchHeader;
            StringBuilder valueBuilder = new StringBuilder(String.valueOf(value));
            StringBuilder htmlContentBuilder = new StringBuilder(htmlContent == null ? "" : htmlContent);

            List<BillingONCHeader1> claimHeaders = cheaderDao.findByProviderStatusAndDateRange(providerNo, Arrays.asList(statuses), dateRange)
                    .stream()
                    .filter(this::shouldIncludeClaimHeader)
                    .toList();
            Map<Integer, List<BillingONItem>> itemsByHeaderId = claimItemsByHeaderId(claimHeaders);

            for (BillingONCHeader1 b : claimHeaders) {
                String bNo = "" + b.getId();

                patientCount++;
                currentClaimHeader = buildClaimHeaderDto(b, bNo, true);
                if (currentClaimHeader.getClinic() == null || currentClaimHeader.getClinic().equalsIgnoreCase("null")) {
                    currentClaimHeader = currentClaimHeader.withClinic("");
                    clinicBgColor = "FFFFFF";
                } else {
                    clinicBgColor = siteDao.getByLocation(currentClaimHeader.getClinic()).getBgColor();
                    clinicBgColor = (clinicBgColor == null || clinicBgColor.equalsIgnoreCase("null") ? "FFFFFF" : clinicBgColor);
                }

                valueBuilder.append(buildHeader1(loggedInInfo));
                htmlContentBuilder.append(printSiteErrorPartMsg());
                // build billing detail
                invCount = 0;

                for (BillingONItem i : itemsByHeaderId.getOrDefault(b.getId(), List.of())) {
                    currentItem = buildClaimItemDto(i);
                    recordCount++;
                    diagcode = currentItem.getDx();
                    fee = i.getFee();
                    bdFee = BillingMoney.amount(fee);
                    BigTotal = BigTotal.add(bdFee);
                    _logger.debug("createBillingFileStr(BigTotal = {})", BigTotal);
                    checkItem();
                    valueBuilder.append(buildItem());
                    // PHI-leak guard — see the matching note in the per-provider
                    // branch above. Do not log `value`; it embeds HIN + DOB.
                    htmlContentBuilder.append(buildSiteHTMLContentRecord(invCount));
                    htmlContentBuilder.append(printSiteErrorPartMsg());
                    invCount++;
                }
                checkNoDetailRecord(invCount);
                htmlContentBuilder.append(printSiteErrorPartMsg());
                if (eFlag.compareTo("1") == 0) {
                    stageHeaderBilledBatchId(currentClaimHeader.getId(), currentBatchHeader.getId());
                }
            }
            hcCount = hcCount + healthcardCount;
            pCount = pCount + patientCount;
            rCount = rCount + recordCount;
            // Historical total multiplier was removed; totals are already stored in dollars.
            BigTotal = BigTotal.setScale(2, RoundingMode.HALF_UP);
            valueBuilder.append(buildTrailer());
            value = valueBuilder.toString();
            htmlContent = htmlContentBuilder.toString();

            htmlCode = buildSiteHTMLContentTrailer();
            // writeHtml(htmlCode);
            ohipReciprocal = String.valueOf(hcCount);
            ohipRecord = String.valueOf(rCount);
            ohipClaim = String.valueOf(pCount);
            totalAmount = BigTotal.toString();
            if (eFlag.compareTo("1") == 0) {
                stageBatchHeaderSum(currentBatchHeader.getId(),
                        healthcardCount, patientCount, recordCount);
            }
        } catch (BillingFileWriteException | BillingDataLoadException domain) {
            throw domain;
        } catch (Exception e) {
            // Propagate partial-write failures so Struts renders the file-write error page.
            _logger.error("OHIP site claim file generation failed", e);
            throw new BillingFileWriteException(
                    "OHIP site claim file generation failed", e);
        }
    }

    private boolean shouldIncludeClaimHeader(BillingONCHeader1 header) {
        return propBillingNo == null || propBillingNo.containsKey(String.valueOf(header.getId()));
    }

    private Map<Integer, List<BillingONItem>> claimItemsByHeaderId(List<BillingONCHeader1> claimHeaders) {
        List<Integer> headerIds = claimHeaders.stream()
                .map(BillingONCHeader1::getId)
                .toList();
        Map<Integer, List<BillingONItem>> itemsByHeaderId = new HashMap<>();
        for (BillingONItem item : itemDao.findByCh1IdsExcludingDeletedAndSettled(headerIds)) {
            itemsByHeaderId.computeIfAbsent(item.getCh1Id(), ignored -> new ArrayList<>()).add(item);
        }
        return itemsByHeaderId;
    }

    public String getHtmlCode() {
        return htmlCode;
    }

    public String getHtmlValue() {
        return htmlValue;
    }

    public String getOhipClaim() {
        return ohipClaim;
    }

    public String getOhipReciprocal() {
        return ohipReciprocal;
    }

    public String getOhipRecord() {
        return ohipRecord;
    }

    public String getOhipVer() {
        return ohipVer;
    }

    public String getTotalAmount() {
        return totalAmount;
    }

    public String getValue() {
        return value;
    }

    /**
     * Applies the generated-disk DB state transitions after the caller has
     * successfully written the OHIP text file and HTML companion file.
     */
    public void finalizeGeneratedDisk() {
        if (!"1".equals(eFlag) || pendingBatchHeaderId == null) {
            return;
        }
        for (String claimHeaderId : pendingBilledClaimHeaderIds) {
            updateHeader1BilledBatchId(claimHeaderId, pendingBatchHeaderId);
        }
        updateBatchHeaderSum(pendingBatchHeaderId,
                String.valueOf(pendingHealthcardCount),
                String.valueOf(pendingPatientCount),
                String.valueOf(pendingRecordCount));
        resetGeneratedDiskFinalization();
    }

    private void resetGeneratedDiskFinalization() {
        pendingBilledClaimHeaderIds.clear();
        pendingBatchHeaderId = null;
        pendingHealthcardCount = 0;
        pendingPatientCount = 0;
        pendingRecordCount = 0;
    }

    private void stageHeaderBilledBatchId(String claimHeaderId, String batchHeaderId) {
        pendingBilledClaimHeaderIds.add(claimHeaderId);
        pendingBatchHeaderId = batchHeaderId;
    }

    private void stageBatchHeaderSum(String batchHeaderId,
                                     int healthcardCount,
                                     int patientCount,
                                     int recordCount) {
        pendingBatchHeaderId = batchHeaderId;
        pendingHealthcardCount = healthcardCount;
        pendingPatientCount = patientCount;
        pendingRecordCount = recordCount;
    }

    /**
     * Marks one Ontario claim header as billed and links it to the generated
     * batch header.
     */
    public void updateHeader1BilledBatchId(String newInvNo, String batchId) {
        BillingONCHeader1 header = cheaderDao.find(Integer.parseInt(newInvNo));
        if (header != null) {
            header.setStatusStrict(BillingStatus.BILLED);
            header.setHeaderId(Integer.parseInt(batchId));
            cheaderDao.merge(header);
        }
    }

    private void updateBatchHeaderSum(String bid, String hn, String rn, String tn) {
        BillingONHeader h = headerDao.find(Integer.parseInt(bid));
        if (h != null) {
            h.sethCount(hn);
            h.setrCount(rn);
            h.settCount(tn);
            headerDao.merge(h);
        }
    }

    /**
     * Updates the generated disk-name row with final claim counts and total.
     */
    public void updateDisknameSum(int bid) {
        for (BillingONFilename f : filenameDao.findByDiskIdAndProvider(bid, providerNo)) {
            f.setClaimRecord((healthcardCount + patientCount) + "/" + recordCount);
            f.setTotal(totalAmount);
            filenameDao.merge(f);
        }
    }

    private void updateDemoData(LoggedInInfo loggedInInfo, BillingClaimHeaderDto chObj) {
        // last_name,first_name,dob,hin,ver,hc_type,sex
        List<String> demoFields = lookupService
                .getPatientCurBillingDemo(loggedInInfo, chObj.demographicNo());

        //Bonus Billing (Incentives)? Block out patient data : update with patient data
        if (BillingONCHeader1.INDEPENDENT.equals(chObj.getStatus())) {
            currentClaimHeader = currentClaimHeader.withDemographicName("");
            currentClaimHeader = currentClaimHeader.withDob("");
            currentClaimHeader = currentClaimHeader.withHin("");
            currentClaimHeader = currentClaimHeader.withVer("");
            currentClaimHeader = currentClaimHeader.withProvince("ON");
            currentClaimHeader = currentClaimHeader.withSex("");
        } else {
            currentClaimHeader = currentClaimHeader.withDemographicName(demoFields.get(0) + "," + demoFields.get(1));
            currentClaimHeader = currentClaimHeader.withDob(demoFields.get(2));
            currentClaimHeader = currentClaimHeader.withHin(demoFields.get(3));
            currentClaimHeader = currentClaimHeader.withVer(demoFields.get(4));
            currentClaimHeader = currentClaimHeader.withProvince(demoFields.get(5));
            currentClaimHeader = currentClaimHeader.withSex(demoFields.get(6));
        }

        if (!"ON".equals(currentClaimHeader.getProvince()) && !"".equals(currentClaimHeader.getProvince())) currentClaimHeader = currentClaimHeader.withPayProgram("RMB");
    }

    /**
     * Loads the batch header DTO into this instance and resolves its OHIP
     * filename from the disk-name table.
     */
    public void getBatchHeaderObj(String bid) {
        BillingONHeader h = headerDao.find(ConversionUtils.fromIntString(bid));
        if (h == null) {
            // Distinguish missing-header from file-write failure: a missing
            // batch_header row is a data-integrity error, not a disk error.
            // Throw the typed BillingDataLoadException so the surrounding
            // catch in createBillingFileStr lets it through to the
            // billingDataLoadError struts mapping, instead of collapsing
            // it to "disk full" misdirection.
            throw new BillingDataLoadException(
                    "OHIP claim file aborted: batch_header bid=" + bid + " not found",
                    BillingDataLoadException.Phase.BATCH_HEADER_LOOKUP,
                    java.util.Map.of("bid", bid == null ? "" : bid));
        }
        currentBatchHeader = new BillingBatchHeaderDto();
        currentBatchHeader.setId(bid);
        currentBatchHeader.setDiskId("" + h.getDiskId());
        currentBatchHeader.setTranscId(h.getTransactionId());
        currentBatchHeader.setRecId(h.getRecordId());
        currentBatchHeader.setSpecId(h.getSpecId());
        currentBatchHeader.setMohOffice(h.getMohOffice());

        currentBatchHeader.setBatchId(h.getBatchId());
        currentBatchHeader.setOperator(h.getOperator());
        currentBatchHeader.setGroupNum(h.getGroupNum());
        currentBatchHeader.setProviderRegNum(h.getProviderRegNum());
        currentBatchHeader.setSpecialty(h.getSpecialty());
        currentBatchHeader.setHCount(h.gethCount());
        currentBatchHeader.setRCount(h.getrCount());
        currentBatchHeader.setTCount(h.gettCount());
        currentBatchHeader.setBatchDate(ConversionUtils.toDateString(h.getBatchDate()));

        setOhipFilename(getOhipFilename(currentBatchHeader.getDiskId()));
    }

    /** Replaces this instance's current batch header and refreshes its filename. */
    public void setBatchHeaderObj(BillingBatchHeaderDto value) {
        currentBatchHeader = value;
        setOhipFilename(getOhipFilename(currentBatchHeader.getDiskId()));
    }

    public String getOhipFilename(String id) {
        BillingONDiskName n = diskNameDao.find(ConversionUtils.fromIntString(id));
        if (n != null) {
            return n.getOhipFilename();
        }
        return null;
    }

    /** Sets the claim date range used by subsequent file generation. */
    public void setDateRange(DateRange newDateRange) {
        dateRange = newDateRange;
    }

    /** Sets whether generated claims remain unchanged ({@code "0"}) or are marked billed ({@code "1"}). */
    public void setEFlag(String neweFlag) {
        eFlag = neweFlag;
    }

    public void setHtmlFilename(String newHtmlFilename) {
        htmlFilename = newHtmlFilename;
    }

    public void setOhipFilename(String newOhipFilename) {
        ohipFilename = newOhipFilename;
    }

    public void setOhipVer(String newOhipVer) {
        ohipVer = newOhipVer;
    }

    public void setProviderNo(String newProviderNo) {
        providerNo = newProviderNo;
    }

    // return i space str, e.g. " "
    public String space(int i) {
        String returnValue = new String();
        for (int j = 0; j < i; j++) {
            returnValue += " ";
        }
        return returnValue;
    }

    /**
     * Reads billing numbers from the current OHIP file into the regeneration
     * de-duplication set used by later claim-file generation.
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public void readInBillingNo() {
        String home_dir = CarlosProperties.getInstance().getProperty("HOME_DIR");
        propBillingNo = new Properties();
        // Path-validate the filename before opening — companion writeFile/
        // writeHtml already do this; defense-in-depth so a future caller
        // that sets ohipFilename from user input can't traverse out of
        // home_dir.
        java.io.File safeOhipFile = io.github.carlos_emr.carlos.utility.PathValidationUtils.validatePath(
                ohipFilename, new java.io.File(home_dir));
        // try-with-resources: a mid-read throw must close the file handle.
        try (RandomAccessFile raf = new RandomAccessFile(safeOhipFile, "r")) {
            do {
                String lineValue = raf.readLine();
                if (lineValue == null) {
                    break;
                }
                if (lineValue.startsWith("HEH")) {
                    // consider different cases
                    String bNo = "-1";
                    if (lineValue.length() == 79 && lineValue.substring(31, 34).matches("HCP|WCB|RMB")) {
                        bNo = lineValue.substring(23, 31);
                    } else {
                        int nt = 0;
                        if (lineValue.indexOf("HCPP") > 0) nt = lineValue.indexOf("HCPP");
                        if (lineValue.indexOf("WCBP") > 0) nt = lineValue.indexOf("WCBP");
                        if (lineValue.indexOf("RMBP") > 0) nt = lineValue.indexOf("RMBP");
                        bNo = lineValue.substring(nt - 8, nt);
                    }

                    bNo = "" + Integer.parseInt(bNo);

                    propBillingNo.setProperty(bNo, "");
                }
            } while (true);

        } catch (IOException | RuntimeException e) {
            // Aborting here is critical: this method populates the dedup-set
            // (propBillingNo) used during regeneration to skip already-billed
            // records. A partial read would leave the set incomplete and the
            // subsequent createBillingFileStr() would re-emit those records to
            // OHIP — duplicate claim submission. RuntimeException covers the
            // String#substring/Integer.parseInt paths above on a malformed line.
            _logger.error("Failed to read OHIP file {} (record dedup will be incomplete; aborting to prevent duplicate-claim submission)",
                    LogSafe.sanitize(ohipFilename), e);
            throw new IllegalStateException(
                    "Failed to read OHIP file for billing-no dedup; aborting regeneration", e);
        }
    }

    /** Renames the current OHIP file to a timestamp-suffixed backup name. */
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public void renameFile() {
        String home_dir;
        home_dir = CarlosProperties.getInstance().getProperty("HOME_DIR");
        // PathValidationUtils for consistency with the rest of the billing
        // module (MoveMohFiles2Action, BillingDocumentErrorReportUpload2Action).
        // The filenames here are server-derived (not user input) so the
        // immediate risk is low; the validation closes any path-traversal
        // hole that would surface if a future caller wired filename through
        // request params without sanitization.
        File homeDirFile = new File(home_dir);
        File file = io.github.carlos_emr.carlos.utility.PathValidationUtils.validatePath(ohipFilename, homeDirFile);

        // new filename
        String newName = ohipFilename + "." + GregorianCalendar.getInstance().getTimeInMillis();

        File file2 = io.github.carlos_emr.carlos.utility.PathValidationUtils.validatePath(newName, homeDirFile);

        // Files.move (not renameTo) so an IOException surfaces with a real
        // cause and the action's exception mapping fires. A silent rename
        // failure here would re-pick the un-renamed file on the next batch
        // run, producing a duplicate MOH submission.
        try {
            java.nio.file.Files.move(file.toPath(), file2.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            rememberRenamedFile(file, file2);
        } catch (java.nio.file.AtomicMoveNotSupportedException atomicNotSupported) {
            // Cross-volume rename — fall back to plain move.
            try {
                java.nio.file.Files.move(file.toPath(), file2.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                rememberRenamedFile(file, file2);
            } catch (java.io.IOException ioe) {
                _logger.error("OHIP file rename failed: {} -> {}", ohipFilename, newName, ioe);
                throw new BillingFileWriteException(
                        "OHIP file rename failed: " + ohipFilename, ioe);
            }
        } catch (java.io.IOException ioe) {
            _logger.error("OHIP file rename failed: {} -> {}", ohipFilename, newName, ioe);
            throw new BillingFileWriteException(
                    "OHIP file rename failed: " + ohipFilename, ioe);
        }
    }

    private void rememberRenamedFile(File originalFile, File backupFile) {
        lastRenamedOriginalFile = originalFile;
        lastRenamedBackupFile = backupFile;
    }

    /** Deletes the generated OHIP claim file, suppressing cleanup failures. */
    public void deleteOhipFileQuietly() {
        deleteConfiguredOutputQuietly(ohipFilename, "OHIP");
    }

    /** Deletes the generated HTML companion file, suppressing cleanup failures. */
    public void deleteHtmlFileQuietly() {
        deleteConfiguredOutputQuietly(htmlFilename, "HTML");
    }

    /**
     * Restores the original OHIP file after a regeneration failure, suppressing
     * cleanup failures so the original exception remains visible to callers.
     */
    public void restoreLastRenameQuietly() {
        if (lastRenamedOriginalFile == null || lastRenamedBackupFile == null
                || !lastRenamedBackupFile.exists()) {
            return;
        }
        try {
            java.nio.file.Files.move(lastRenamedBackupFile.toPath(),
                    lastRenamedOriginalFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            lastRenamedOriginalFile = null;
            lastRenamedBackupFile = null;
        } catch (IOException | RuntimeException e) {
            _logger.warn("Failed to restore renamed OHIP file from {} to {}",
                    LogSafe.sanitize(lastRenamedBackupFile.getName()),
                    LogSafe.sanitize(lastRenamedOriginalFile.getName()), e);
        }
    }

    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    private void deleteConfiguredOutputQuietly(String configuredFilename, String outputLabel) {
        if (configuredFilename == null || configuredFilename.isBlank()) {
            return;
        }
        try {
            String homeDir = CarlosProperties.getInstance().getProperty("HOME_DIR");
            if (homeDir == null || homeDir.isBlank()) {
                _logger.warn("Skipping {} cleanup because HOME_DIR is not configured", outputLabel);
                return;
            }
            File safeOut = io.github.carlos_emr.carlos.utility.PathValidationUtils.validatePath(
                    configuredFilename, new File(homeDir));
            java.nio.file.Files.deleteIfExists(safeOut.toPath());
        } catch (IOException | RuntimeException e) {
            _logger.warn("Failed to delete generated {} file {} during cleanup", outputLabel,
                    LogSafe.sanitize(configuredFilename), e);
        }
    }

    /**
     * Writes the fixed-width OHIP claim file to the configured output path.
     *
     * @param value1 complete MOH fixed-width claim payload to write.
     * @throws BillingFileWriteException when path validation or disk I/O fails.
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public void writeFile(String value1) {
        String home_dir = CarlosProperties.getInstance().getProperty("HOME_DIR");
        File safeOut = io.github.carlos_emr.carlos.utility.PathValidationUtils.validatePath(
                ohipFilename, new File(home_dir));
        // try-with-resources: close both streams on every exit path so a
        // mid-write throw (PrintStream.println, FileOutputStream constructor)
        // doesn't leak file handles.
        // Wrap in BufferedWriter rather than PrintStream — PrintStream's
        // close() calls flush() internally and swallows the resulting
        // IOException, so checkError() inside try-with-resources misses
        // the canonical disk-full-on-final-flush failure mode. BufferedWriter
        // close() actually throws IOException, so try-with-resources surfaces
        // close-time IO failures.
        try (FileOutputStream out = new FileOutputStream(safeOut);
             java.io.OutputStreamWriter osw = new java.io.OutputStreamWriter(out, java.nio.charset.StandardCharsets.US_ASCII);
             java.io.BufferedWriter bw = new java.io.BufferedWriter(osw)) {
            bw.write(value1);
            bw.newLine();
        } catch (IOException e) {
            _logger.error("Write OHIP File Error: filename={}", ohipFilename, e);
            throw new BillingFileWriteException(
                    "Failed to write OHIP claim file: " + ohipFilename, e);
        }
    }

    /**
     * Writes the companion HTML claim preview to the configured output path.
     *
     * @param htmlvalue1 complete HTML preview payload to write.
     * @throws BillingFileWriteException when path validation or disk I/O fails.
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public void writeHtml(String htmlvalue1) {
        String home_dir1 = CarlosProperties.getInstance().getProperty("HOME_DIR");
        File safeHtml = io.github.carlos_emr.carlos.utility.PathValidationUtils.validatePath(
                htmlFilename, new File(home_dir1));
        // Same BufferedWriter shape as writeFile — see writeFile rationale.
        try (FileOutputStream out1 = new FileOutputStream(safeHtml);
             java.io.OutputStreamWriter osw = new java.io.OutputStreamWriter(out1, java.nio.charset.StandardCharsets.UTF_8);
             java.io.BufferedWriter bw = new java.io.BufferedWriter(osw)) {
            bw.write(htmlvalue1);
            bw.newLine();
        } catch (IOException e) {
            _logger.error("Write HTML File Error: filename={}", htmlFilename, e);
            throw new BillingFileWriteException(
                    "Failed to write OHIP HTML companion file: " + htmlFilename, e);
        }
    }

    // return x zero str, e.g. 000000
    public String zero(int x) {
        String returnZeroValue = new String();
        for (int y = 0; y < x; y++) {
            returnZeroValue += "0";
        }
        return returnZeroValue;
    }

    // return x length string with zero str, e.g. 0018
    public String forwardZero(String y, int x) {
        // x must >= y.length()
        String returnZeroValue = "";
        for (int i = y.length(); i < x; i++) {
            returnZeroValue += "0";
        }

        return (returnZeroValue + y);
    }

    // return x length string with zero str, e.g. 1800
    public String leftJustify(String y, int x, String z) {
        // x must >= y.length()
        if (z != null && z.length() > x) {
            z = z.substring(0, x);
        }

        String returnZeroValue = "";
        for (int i = 0; i < x; i++) {
            returnZeroValue += y;
        }

        if (z != null) {
            returnZeroValue = z + returnZeroValue.substring(z.length());
        }

        return (returnZeroValue);
    }

    public String rightJustify(String y, int x, String z) {
        // x must >= y.length()
        String returnZeroValue = "";
        for (int i = 0; i < x; i++) {
            returnZeroValue += y;
        }

        if (z != null) {
            returnZeroValue = returnZeroValue.substring(0, x - z.length()) + z;
        }

        return (returnZeroValue);
    }

    private String getCompactDateStr(String y) {
        String ret = "";
        if (y != null) {
            ret = y;
            if (y.length() > 6) {
                String[] temp = y.split("\\-");
                if (temp.length == 3) {
                    ret = temp[0] + (temp[1].length() == 1 ? ("0" + temp[1]) : temp[1]) + (temp[2].length() == 1 ? ("0" + temp[2]) : temp[2]);
                }
            }
        }
        return ret;
    }

    public BillingBatchHeaderDto getCurrentBatchHeader() {
        return currentBatchHeader;
    }

    public void setCurrentBatchHeader(BillingBatchHeaderDto currentBatchHeader) {
        this.currentBatchHeader = currentBatchHeader;
    }

    public BillingClaimHeaderDto getCurrentClaimHeader() {
        return currentClaimHeader;
    }

    public void setCurrentClaimHeader(BillingClaimHeaderDto currentClaimHeader) {
        this.currentClaimHeader = currentClaimHeader;
    }

    public String getErrorFatalMsg() {
        return errorFatalMsg;
    }

    public void setErrorFatalMsg(String errorFatalMsg) {
        this.errorFatalMsg = errorFatalMsg == null ? "" : errorFatalMsg;
    }

    public void appendErrorFatalMsg(String value) {
        this.errorFatalMsg += value == null ? "" : value;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg == null ? "" : errorMsg;
    }

    public void appendErrorMsg(String value) {
        this.errorMsg += value == null ? "" : value;
    }

    public String getErrorPartMsg() {
        return errorPartMsg;
    }

    public void setErrorPartMsg(String errorPartMsg) {
        this.errorPartMsg = errorPartMsg == null ? "" : errorPartMsg;
    }

    public void appendErrorPartMsg(String value) {
        this.errorPartMsg += value == null ? "" : value;
    }

    public String[] getErrorParams() {
        return errorParams == null ? null : errorParams.clone();
    }

    public void setErrorParams(String[] errorParams) {
        this.errorParams = errorParams == null ? null : errorParams.clone();
    }

    public String getHtmlContent() {
        return htmlContent;
    }

    public void setHtmlContent(String htmlContent) {
        this.htmlContent = htmlContent;
    }

}
