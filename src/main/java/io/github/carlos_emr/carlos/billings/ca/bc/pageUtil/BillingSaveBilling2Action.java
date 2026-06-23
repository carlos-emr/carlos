/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import io.github.carlos_emr.carlos.appt.ApptStatusData;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.RedirectValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.sec.UnauthenticatedRejectionResolver;
import io.github.carlos_emr.MyDateFormat;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.entities.Billingmaster;
import io.github.carlos_emr.carlos.billings.ca.bc.MSP.MSPBillingNote;
import io.github.carlos_emr.carlos.billings.ca.bc.MSP.MSPReconcile;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingHistoryDAO;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingNote;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class BillingSaveBilling2Action extends ActionSupport {
    private static final String BILLING_SESSION_EXPIRED_KEY = "billing.billingSave.sessionExpired";
    private static final String BILLING_SESSION_EXPIRED_FALLBACK = "Billing session expired.";
    private static final String MALFORMED_BILLING_REQUEST_KEY = "billing.billingSave.malformedBillingRequest";
    private static final String MALFORMED_BILLING_REQUEST_FALLBACK =
            "Malformed billing request. Please return to billing and retry.";
    private static final String MALFORMED_APPOINTMENT_NO_KEY = "billing.billingSave.malformedAppointmentNo";
    /** MessageFormat pattern consumed by {@link #formatMalformedAppointmentMessage(String, String)}. */
    private static final String MALFORMED_APPOINTMENT_NO_FALLBACK = "Malformed appointment number \"{0}\". "
            + "Please return to billing and re-select the appointment.";
    private static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    private static final String NOSNIFF = "nosniff";

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private static Logger log = MiscUtils.getLogger();
    private AppointmentArchiveDao appointmentArchiveDao = (AppointmentArchiveDao) SpringUtils.getBean(AppointmentArchiveDao.class);
    private OscarAppointmentDao appointmentDao = (OscarAppointmentDao) SpringUtils.getBean(OscarAppointmentDao.class);

    private BillingmasterDAO billingmasterDAO = SpringUtils.getBean(BillingmasterDAO.class);

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws IOException, ServletException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        LoggedInInfo loggedInInfo = authenticatedBillingUser(request);
        if (loggedInInfo == null) {
            rejectUnauthenticatedBillingSave(request, response);
            return NONE;
        }

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        BillingSessionBean bean = billingSessionBean(request);
        if (bean == null) {
            rejectExpiredBillingSession(request, response);
            return NONE;
        }

        bean.setCreator(loggedInInfo.getLoggedInProviderNo());

        MiscUtils.getLogger().debug("BC billing appointment link present={}", bean.getApptNo() != null);

        Date curDate = new Date();
        String billingid = "";
        ArrayList<String> billingIds = new ArrayList<String>();
        String dataCenterId = CarlosProperties.getInstance().getProperty("dataCenterId");
        String billingMasterId = "";

        ArrayList<BillingBillingManager.BillingItem> billItem = bean.getBillItem();

        int appointmentNo;
        int paymentMethod;
        Integer wcbId = null;
        Map<String, Double> priceOverrides;
        try {
            appointmentNo = parseOptionalAppointmentNo(bean.getApptNo());
        } catch (IllegalArgumentException malformedAppointmentNo) {
            rejectMalformedAppointmentNo(request, response, bean.getApptNo(), malformedAppointmentNo);
            return NONE;
        }
        try {
            paymentMethod = parseRequiredInteger(bean.getPaymentType(), "payment type");
            if ("WCB".equals(bean.getBillingType())) {
                wcbId = parseRequiredInteger(bean.getWcbId(), "WCB id");
            }
            priceOverrides = parsePriceOverrides(request, billItem);
        } catch (IllegalArgumentException malformedBillingValue) {
            rejectMalformedBillingValue(request, response, malformedBillingValue);
            return NONE;
        }
        updateAppointmentStatus(bean, appointmentNo);

        char billingAccountStatus = getBillingAccountStatus(bean);

        char paymentMode = (bean.getEncounter().equals("E") && !bean.getBillingType().equals("ICBC") && !bean.getBillingType().equals("Pri") && !bean.getBillingType().equals("WCB")) ? 'E' : '0';

        String billedAmount;

        for (BillingBillingManager.BillingItem bItem : billItem) {

            Billing billing = getBillingObj(bean, curDate, billingAccountStatus, appointmentNo);
            if (priceOverrides.containsKey(bItem.getServiceCode())) {
                Double updatedPrice = priceOverrides.get(bItem.getServiceCode());
                log.debug("Applying BC billing price override for serviceCode={}",
                        LogSafe.sanitize(bItem.getServiceCode()));
                bItem.price = updatedPrice;
                bItem.getLineTotal();
            }

            billingmasterDAO.save(billing);
            billingid = "" + billing.getId();

            billingIds.add(billingid);
            if (paymentMode == 'E') {
                billedAmount = "0.00";
            } else {
                billedAmount = bItem.getDispLineTotal();
            }

            Billingmaster billingmaster = saveBill(billingid, "" + billingAccountStatus, dataCenterId,
                    billedAmount, "" + paymentMode, bean, bItem, appointmentNo, paymentMethod); //billItem.get(i));

            if (bean.getBillingType().equals("WCB")) {
                billingmaster.setWcbId(wcbId);
            }
            billingmasterDAO.save(billingmaster);
            billingMasterId = "" + billingmaster.getBillingmasterNo();
            this.createBillArchive(billingMasterId);

            //Changed March 8th to be included side this loop,  before only one billing would get this information.
            if (bean.getCorrespondenceCode().equals("N") || bean.getCorrespondenceCode().equals("B")) {

                MSPBillingNote n = new MSPBillingNote();
                n.addNote(billingMasterId, bean.getCreator(), bean.getNotes());

            }
            if (bean.getMessageNotes() != null && !bean.getMessageNotes().trim().equals("")) {
                BillingNote n = new BillingNote();
                n.addNote(billingMasterId, bean.getCreator(), bean.getMessageNotes());
            }
        }

        if (bean.getBillingType().equals("WCB")) {
            // Legacy WCB billing still records the WCB id on each Billingmaster row above. Keep
            // this branch as the existing operational marker until WCB linkage is redesigned as a
            // dedicated model/migration rather than an inline billing-save concern.
            MiscUtils.getLogger().debug("BC billing save includes WCB linkage");
        }


        if ("Another Bill".equals(submit)) {
            return "anotherBill";
        } else if ("Save & Print Receipt".equals(submit)) {
            String redirectUrl = receiptRedirectUrl(request.getContextPath(), billingIds);
            sendReceiptRedirect(response, redirectUrl);
            return NONE;
        }

        return SUCCESS;
    }

    /**
     * Returns the logged-in billing user without creating a new session.
     *
     * <p>BC billing save is a mutator, so missing session or missing {@code user} must be rejected
     * before any privilege checks or DAO writes occur.</p>
     */
    private LoggedInInfo authenticatedBillingUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            return null;
        }
        return LoggedInInfo.getLoggedInInfoFromSession(request);
    }

    /**
     * Reads the staged BC billing session bean without assuming the session is still valid.
     */
    private BillingSessionBean billingSessionBean(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object billingSessionAttr = session.getAttribute("billingSessionBean");
        return billingSessionAttr instanceof BillingSessionBean
                ? (BillingSessionBean) billingSessionAttr
                : null;
    }

    /**
     * Sends a bad-request response for expired or replayed billing submits.
     *
     * <p>The response body is written directly instead of using {@code sendError}; otherwise the
     * servlet error-page mapping replaces the localized operator guidance.</p>
     */
    private void rejectExpiredBillingSession(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        log.warn("Rejected BC billing save because billingSessionBean is missing: method={}, uri={}, remote={}",
                LogSafe.sanitize(request.getMethod()),
                LogSafe.sanitizeUri(request.getRequestURI()),
                LogSafe.sanitize(request.getRemoteAddr()));
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        preparePlainTextRejection(response);
        writeBody(request, response,
                message(request.getLocale(), BILLING_SESSION_EXPIRED_KEY, BILLING_SESSION_EXPIRED_FALLBACK));
    }

    /**
     * Resolves short direct-response messages from the same resource bundle used by the billing JSPs.
     *
     * <p>Resolution mirrors the login action: preferred locale, then English, then the supplied
     * hardcoded fallback. A missing key should not turn a rejected billing submit into a server
     * error. Callers that feed the result to {@link MessageFormat} must remember that single quotes
     * are escape characters in translated patterns.</p>
     */
    // Package-private so direct-response fallback behavior can be pinned without servlet plumbing.
    static String message(Locale locale, String key, String fallback) {
        Locale effectiveLocale = locale == null ? Locale.ENGLISH : locale;
        String localized = messageFromBundle(effectiveLocale, key);
        if (localized != null) {
            return localized;
        }

        if (!Locale.ENGLISH.getLanguage().equals(effectiveLocale.getLanguage())) {
            String english = messageFromBundle(Locale.ENGLISH, key);
            if (english != null) {
                return english;
            }
        }
        return fallback;
    }

    /**
     * Looks up one resource-bundle message for a non-null locale.
     */
    private static String messageFromBundle(Locale locale, String key) {
        ResourceBundle bundle;
        try {
            bundle = ResourceBundle.getBundle("oscarResources", locale);
        } catch (MissingResourceException e) {
            log.warn("Missing billing save resource bundle: locale={}",
                    LogSafe.sanitize(String.valueOf(locale)));
            return null;
        }

        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            log.warn("Missing billing save resource bundle key: key={}, locale={}",
                    LogSafe.sanitize(key), LogSafe.sanitize(String.valueOf(locale)));
            return null;
        }
    }

    /**
     * Rejects non-empty appointment numbers that cannot be parsed before any billing writes occur.
     *
     * <p>Dropping malformed values to appointment {@code 0} would silently save an unlinked bill.
     * A direct bad-request response keeps the operator-visible contract clear and avoids mutating
     * billing state when the appointment linkage is ambiguous. This writes the localized body
     * explicitly instead of using {@code sendError}, because the container error page would otherwise
     * replace the response body.</p>
     */
    private void rejectMalformedAppointmentNo(
            HttpServletRequest request,
            HttpServletResponse response,
            String rawAppointmentNo,
            IllegalArgumentException malformedAppointmentNo) throws IOException {

        log.warn("Rejected BC billing save because appointment number is malformed: "
                        + "method={}, uri={}, remote={}, apptNo={}",
                LogSafe.sanitize(request.getMethod()),
                LogSafe.sanitizeUri(request.getRequestURI()),
                LogSafe.sanitize(request.getRemoteAddr()),
                LogSafe.sanitize(rawAppointmentNo),
                malformedAppointmentNo);
        String messagePattern = message(request.getLocale(),
                MALFORMED_APPOINTMENT_NO_KEY, MALFORMED_APPOINTMENT_NO_FALLBACK);
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        preparePlainTextRejection(response);
        writeBody(request, response, formatMalformedAppointmentMessage(messagePattern, rawAppointmentNo));
    }

    /**
     * Rejects malformed required billing fields before any database mutation occurs.
     */
    private void rejectMalformedBillingValue(
            HttpServletRequest request,
            HttpServletResponse response,
            IllegalArgumentException malformedBillingValue) throws IOException {

        log.warn("Rejected BC billing save because a required numeric field is malformed: "
                        + "method={}, uri={}, remote={}, reason={}",
                LogSafe.sanitize(request.getMethod()),
                LogSafe.sanitizeUri(request.getRequestURI()),
                LogSafe.sanitize(request.getRemoteAddr()),
                LogSafe.sanitize(malformedBillingValue.getMessage()),
                malformedBillingValue);
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        preparePlainTextRejection(response);
        writeBody(request, response,
                message(request.getLocale(),
                        MALFORMED_BILLING_REQUEST_KEY,
                        MALFORMED_BILLING_REQUEST_FALLBACK));
    }

    /**
     * Formats the localized malformed-appointment response body.
     *
     * <p>{@link MessageFormat} treats single quotes as escape delimiters. Translators often write
     * natural-language apostrophes without doubling them, which can make {@code {0}} render
     * literally. The first pass preserves correctly escaped translations; if the appointment
     * placeholder survives and the pattern has an unescaped quote, the second pass escapes only those
     * unescaped quotes. Invalid patterns skip recovery and use the appointment-specific fallback so
     * operator logs do not imply quote recovery was attempted.</p>
     */
    static String formatMalformedAppointmentMessage(String messagePattern, String rawAppointmentNo) {
        String sanitizedAppointmentNo = LogSafe.sanitizeForDisplay(rawAppointmentNo);
        MessagePatternResult result = formatMessagePatternOnce(
                messagePattern, sanitizedAppointmentNo, MALFORMED_APPOINTMENT_NO_FALLBACK);
        if (result.usedFallback()) {
            return result.message();
        }

        String formatted = result.message();
        if (containsUnsubstitutedAppointmentPlaceholder(formatted, sanitizedAppointmentNo)) {
            if (!hasUnescapedSingleQuote(messagePattern)) {
                log.warn("Billing save message pattern left appointment placeholder unsubstituted: "
                        + "pattern={}", LogSafe.sanitize(messagePattern));
                return formatted;
            }
            log.warn("Billing save message pattern left appointment placeholder unsubstituted; "
                    + "retrying with escaped single quotes");
            result = formatMessagePatternOnce(
                    escapeUnescapedSingleQuotes(messagePattern),
                    sanitizedAppointmentNo,
                    MALFORMED_APPOINTMENT_NO_FALLBACK);
            formatted = result.message();
            if (containsUnsubstitutedAppointmentPlaceholder(formatted, sanitizedAppointmentNo)) {
                log.warn("Billing save message pattern still left appointment placeholder "
                        + "unsubstituted after quote recovery: pattern={}",
                        LogSafe.sanitize(messagePattern));
            }
        }
        return formatted;
    }

    private static MessagePatternResult formatMessagePatternOnce(
            String messagePattern,
            String sanitizedAppointmentNo,
            String fallbackPattern) {

        try {
            return new MessagePatternResult(
                    MessageFormat.format(messagePattern, sanitizedAppointmentNo), false);
        } catch (IllegalArgumentException e) {
            log.warn("Billing save message pattern is invalid; using hardcoded fallback: pattern={}",
                    LogSafe.sanitize(messagePattern), e);
            return new MessagePatternResult(
                    fallbackPattern.replace("{0}", sanitizedAppointmentNo), true);
        }
    }

    /**
     * Detects whether a rendered message still contains the appointment placeholder outside the
     * sanitized appointment value itself. A literal appointment number such as {@code bad-{0}} must
     * not look like a translation failure.
     */
    private static boolean containsUnsubstitutedAppointmentPlaceholder(
            String formatted,
            String sanitizedAppointmentNo) {

        if (!formatted.contains("{0}")) {
            return false;
        }
        if (sanitizedAppointmentNo == null || sanitizedAppointmentNo.isEmpty()) {
            return true;
        }
        return formatted.replace(sanitizedAppointmentNo, "").contains("{0}");
    }

    /**
     * Returns true when the pattern has at least one single quote that is not already doubled for
     * {@link MessageFormat}.
     */
    private static boolean hasUnescapedSingleQuote(String messagePattern) {
        for (int i = 0; i < messagePattern.length(); i++) {
            if (messagePattern.charAt(i) == '\'') {
                if (i + 1 < messagePattern.length() && messagePattern.charAt(i + 1) == '\'') {
                    i++;
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Escapes only single quotes that are not already escaped for {@link MessageFormat}.
     */
    private static String escapeUnescapedSingleQuotes(String messagePattern) {
        StringBuilder escaped = new StringBuilder(messagePattern.length());
        for (int i = 0; i < messagePattern.length(); i++) {
            char current = messagePattern.charAt(i);
            if (current != '\'') {
                escaped.append(current);
                continue;
            }
            escaped.append("''");
            if (i + 1 < messagePattern.length() && messagePattern.charAt(i + 1) == '\'') {
                i++;
            }
        }
        return escaped.toString();
    }

    /**
     * Writes a direct billing-rejection body after the caller has selected status and content type.
     *
     * <p>The request is used only for sanitized diagnostics if an upstream wrapper has already
     * obtained the servlet output stream and the writer path is unavailable.</p>
     */
    private void writeBody(HttpServletRequest request, HttpServletResponse response, String body) throws IOException {
        try {
            response.getWriter().write(body);
        } catch (IllegalStateException writerUnavailable) {
            log.warn("Response writer unavailable while writing BC billing rejection body; "
                            + "falling back to output stream: method={}, uri={}, remote={}",
                    LogSafe.sanitize(request.getMethod()),
                    LogSafe.sanitizeUri(request.getRequestURI()),
                    LogSafe.sanitize(request.getRemoteAddr()),
                    writerUnavailable);
            response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void preparePlainTextRejection(HttpServletResponse response) {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/plain;charset=UTF-8");
        response.setHeader(X_CONTENT_TYPE_OPTIONS, NOSNIFF);
    }

    private record MessagePatternResult(String message, boolean usedFallback) {
    }

    /**
     * Delegates unauthenticated billing-save rejection to the shared login policy.
     */
    private void rejectUnauthenticatedBillingSave(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            UnauthenticatedRejectionResolver.rejectUnauthenticatedRequest(request, response);
        } catch (IOException e) {
            log.warn("Unable to reject unauthenticated BC billing save request: method={}, uri={}, remote={}",
                    LogSafe.sanitize(request.getMethod()),
                    LogSafe.sanitizeUri(request.getRequestURI()),
                    LogSafe.sanitize(request.getRemoteAddr()),
                    e);
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            } else {
                log.error("BC billing save rejection failed after response commit", e);
            }
        }
    }

    // Package-private so the legacy receipt redirect shape can be tested without saving bills.
    static String receiptRedirectUrl(String contextPath, Iterable<String> billingIds) {
        StringBuilder redirectUrl = new StringBuilder(contextPath == null ? "" : contextPath);
        redirectUrl.append("/billing/CA/BC/billingView?");
        for (String billingId : billingIds) {
            redirectUrl.append("billing_no=")
                    .append(URLEncoder.encode(String.valueOf(billingId), StandardCharsets.UTF_8))
                    .append("&");
        }
        redirectUrl.append("receipt=yes");
        return redirectUrl.toString();
    }

    private void sendReceiptRedirect(HttpServletResponse response, String redirectUrl) throws IOException {
        if (!RedirectValidationUtils.isValidRelativeRedirect(redirectUrl)) {
            log.error("Refused unsafe BC billing receipt redirect: redirectUrl={}",
                    LogSafe.sanitize(redirectUrl));
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }
        // nosemgrep: java.lang.security.audit.unvalidated-redirect.unvalidated-redirect -- redirectUrl is generated server-side by receiptRedirectUrl(...), URL-encodes billing_no values, and is accepted only after RedirectValidationUtils.isValidRelativeRedirect(...)
        response.sendRedirect(redirectUrl);
    }

    /**
     * Updates the linked appointment after the request-level appointment number has been parsed.
     *
     * <p>The caller parses once before any persistence so malformed non-empty appointment numbers
     * reject the request instead of silently saving a bill detached from its appointment.</p>
     */
    private void updateAppointmentStatus(BillingSessionBean bean, int apptNo) {
        if (apptNo == 0) {
            return;
        }

        Appointment appt = appointmentDao.find(apptNo);
        if (appt == null) {
            log.warn("BC billing save could not update linked appointment because it was not found");
            return;
        }

        String billStatus = new ApptStatusData().billStatus(appt.getStatus());
        log.debug("BC billing save updating linked appointment status");

        appointmentArchiveDao.archiveAppointment(appt);
        appt.setStatus(billStatus);
        appt.setLastUpdateUser(bean.getCreator());
        appointmentDao.merge(appt);
    }

    private Billing getBillingObj(final BillingSessionBean bean, final Date curDate,
                                  final char billingAccountStatus, final int appointmentNo) {

        Billing bill = new Billing();
        bill.setDemographicNo(Integer.parseInt(bean.getPatientNo()));
        bill.setProviderNo(bean.getBillingProvider());
        bill.setAppointmentNo(appointmentNo);
        bill.setDemographicName(bean.getPatientName());
        bill.setHin(bean.getPatientPHN());
        bill.setUpdateDate(curDate);
        bill.setBillingDate(MyDateFormat.getSysDate(bean.getServiceDate()));
        bill.setTotal(bean.getGrandtotal());
        bill.setStatus("" + billingAccountStatus);
        bill.setDob(bean.getPatientDoB());
        bill.setVisitDate(MyDateFormat.getSysDate(bean.getAdmissionDate()));
        bill.setVisitType(bean.getVisitType());
        bill.setProviderOhipNo(bean.getBillingPracNo());
        bill.setApptProviderNo(bean.getApptProviderNo());
        bill.setCreator(bean.getCreator());
        bill.setBillingtype(bean.getBillingType());
        return bill;
    }

    private char getBillingAccountStatus(BillingSessionBean bean) {
        char billingAccountStatus = 'O';
        if ("DONOTBILL".equals(bean.getBillingType())) {
            //bean.setBillingType("MSP"); //RESET this to MSP to get processed
            billingAccountStatus = 'N';
        } else if ("WCB".equals(bean.getBillingType())) {
            billingAccountStatus = 'O';
        } else if (MSPReconcile.BILLTYPE_PRI.equals(bean.getBillingType())) {
            billingAccountStatus = 'P';
        }
        return billingAccountStatus;
    }

    public String convertDate8Char(String s) {
        String sdate = "00000000", syear = "", smonth = "", sday = "";
        if (s != null) {

            if (s.indexOf("-") != -1) {

                syear = s.substring(0, s.indexOf("-"));
                s = s.substring(s.indexOf("-") + 1);
                smonth = s.substring(0, s.indexOf("-"));
                if (smonth.length() == 1) {
                    smonth = "0" + smonth;
                }
                s = s.substring(s.indexOf("-") + 1);
                sday = s;
                if (sday.length() == 1) {
                    sday = "0" + sday;
                }

                sdate = syear + smonth + sday;

            } else {
                sdate = s;
            }
        } else {
            sdate = "00000000";

        }
        return sdate;
    }


    String moneyFormat(String str) {
        if (str == null || str.trim().isEmpty()) {
            return "0.00";
        }
        try {
            return new java.math.BigDecimal(str.trim()).movePointLeft(2).toString();
        } catch (NumberFormatException moneyException) {
            throw new IllegalArgumentException(
                    "BC billing amount is malformed [" + LogSafe.sanitizeForDisplay(str) + "]",
                    moneyException);
        }
    }

    /**
     * Parses the optional BC appointment number.
     *
     * <p>Blank and legacy literal {@code "null"} mean no linked appointment. Any other malformed
     * value is rejected by the caller before billing rows are saved, so data entry mistakes are not
     * hidden as appointment {@code 0}.</p>
     *
     * @param raw raw appointment number from the billing session bean
     * @return parsed appointment number, or {@code 0} when the request has no appointment link
     * @throws IllegalArgumentException when a non-empty appointment value is not numeric
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private static int parseOptionalAppointmentNo(String raw) {
        if (raw == null || raw.trim().isEmpty() || raw.trim().equalsIgnoreCase("null")) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("malformed BC billing appointment number", e);
        }
    }

    /**
     * Parses required numeric billing fields before the save loop starts.
     *
     * @param raw raw numeric value from the billing session bean
     * @param fieldName PHI-safe field label used only in logs and exception messages
     * @return parsed integer value
     * @throws IllegalArgumentException when the value is null, blank, or not numeric
     */
    private static int parseRequiredInteger(String raw, String fieldName) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("missing BC billing " + fieldName);
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("malformed BC billing " + fieldName, e);
        }
    }

    private static Map<String, Double> parsePriceOverrides(
            HttpServletRequest request,
            Iterable<BillingBillingManager.BillingItem> billItems) {

        Map<String, Double> priceOverrides = new HashMap<>();
        if (billItems == null) {
            return priceOverrides;
        }
        for (BillingBillingManager.BillingItem billItem : billItems) {
            String serviceCode = billItem.getServiceCode();
            String updatedPrice = request.getParameter("dispPrice+" + serviceCode);
            if (updatedPrice == null || updatedPrice.trim().isEmpty()) {
                continue;
            }
            try {
                double parsedPrice = Double.parseDouble(updatedPrice.trim());
                if (!Double.isFinite(parsedPrice)) {
                    throw new NumberFormatException("non-finite price");
                }
                priceOverrides.put(serviceCode, parsedPrice);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "malformed BC billing display price for service code "
                                + LogSafe.sanitizeForDisplay(serviceCode),
                        e);
            }
        }
        return priceOverrides;
    }

    /**
     * Adds a new entry into the billing_history table
     */
    private void createBillArchive(String billingMasterNo) {
        BillingHistoryDAO dao = new BillingHistoryDAO();
        dao.createBillingHistoryArchive(billingMasterNo);
    }

    private Billingmaster saveBill(String billingid, String billingAccountStatus, String dataCenterId,
                                   String billedAmount, String paymentMode, BillingSessionBean bean,
                                   BillingBillingManager.BillingItem billItem, int appointmentNo,
                                   int paymentMethod) {
        return saveBill(billingid, billingAccountStatus, dataCenterId, billedAmount, paymentMode,
                bean, "" + billItem.getUnit(), "" + billItem.getServiceCode(), appointmentNo,
                paymentMethod);
    }

    private Billingmaster saveBill(String billingid, String billingAccountStatus, String dataCenterId,
                                   String billedAmount, String paymentMode, BillingSessionBean bean,
                                   String billingUnit, String serviceCode, int appointmentNo,
                                   int paymentMethod) {
        Billingmaster bill = new Billingmaster();

        String timeCall = bean.getTimeCall();
        String startTime = bean.getStartTime();
        String endTime = bean.getEndTime();

        if (timeCall != null && timeCall.contains(":")) {
            timeCall = timeCall.replace(":", "");
        }

        if (startTime != null && startTime.contains(":")) {
            startTime = startTime.replace(":", "");
        }

        if (endTime != null && endTime.contains(":")) {
            endTime = endTime.replace(":", "");
        }

        bill.setBillingNo(Integer.parseInt(billingid));
        bill.setCreatedate(new Date());
        bill.setBillingstatus(billingAccountStatus);
        bill.setDemographicNo(Integer.parseInt(bean.getPatientNo()));
        bill.setAppointmentNo(appointmentNo);
        bill.setClaimcode("C02");
        bill.setDatacenter(dataCenterId);
        bill.setPayeeNo(bean.getBillingGroupNo());
        bill.setPractitionerNo(bean.getBillingPracNo());
        bill.setPhn(bean.getPatientPHN());


        bill.setNameVerify(bean.getPatientFirstName(), bean.getPatientLastName());
        bill.setDependentNum(bean.getDependent());
        bill.setBillingUnit(billingUnit); //"" + billItem.getUnit());
        bill.setClarificationCode(bean.getVisitLocation().substring(0, 2));

        String anatomicalArea = "00";
        bill.setAnatomicalArea(anatomicalArea);
        bill.setAfterHour(bean.getAfterHours());
        String newProgram = "00";
        bill.setNewProgram(newProgram);
        bill.setBillingCode(serviceCode); //billItem.getServiceCode());
        bill.setBillAmount(billedAmount);
        bill.setPaymentMode(paymentMode);
        bill.setServiceDate(convertDate8Char(bean.getServiceDate())); //aka: xml_appointment_date
        bill.setServiceToDay(bean.getService_to_date());
        bill.setSubmissionCode(bean.getSubmissionCode());
        bill.setExtendedSubmissionCode(" ");
        bill.setDxCode1(bean.getDx1());
        bill.setDxCode2(bean.getDx2());
        bill.setDxCode3(bean.getDx3());
        bill.setDxExpansion(" ");

        bill.setServiceLocation(bean.getVisitType().substring(0, 1));
        bill.setReferralFlag1(bean.getReferType1());
        bill.setReferralNo1(bean.getReferral1());
        bill.setReferralFlag2(bean.getReferType2());
        bill.setReferralNo2(bean.getReferral2());

        bill.setTimeCall(timeCall);
        bill.setServiceStartTime(startTime);
        bill.setServiceEndTime(endTime);

        bill.setBirthDate(convertDate8Char(bean.getPatientDoB()));
        bill.setOfficeNumber("");
        bill.setCorrespondenceCode(bean.getCorrespondenceCode());
        bill.setClaimComment(bean.getShortClaimNote());
        bill.setMvaClaimCode(bean.getMva_claim_code());
        bill.setIcbcClaimNo(bean.getIcbc_claim_no());
        bill.setFacilityNo(bean.getFacilityNum());
        bill.setFacilitySubNo(bean.getFacilitySubNum());
        bill.setPaymentMethod(paymentMethod);

        if (bean.getPatientHCType() != null && !bean.getPatientHCType().isEmpty() && !bean.getBillRegion().trim().equals(bean.getPatientHCType().trim())) {

            bill.setOinInsurerCode(bean.getPatientHCType());
            bill.setOinRegistrationNo(bean.getPatientPHN());
            bill.setOinBirthdate(convertDate8Char(bean.getPatientDoB()));
            bill.setOinFirstName(bean.getPatientFirstName());
            bill.setOinSecondName(" ");
            bill.setOinSurname(bean.getPatientLastName());
            bill.setOinSexCode(bean.getPatientSex());
            bill.setOinAddress(bean.getPatientAddress1());
            bill.setOinAddress2(bean.getPatientAddress2());
            bill.setOinAddress3("");
            bill.setOinAddress4("");
            bill.setOinPostalcode(bean.getPatientPostal());

            bill.setPhn("0000000000");
            bill.setNameVerify("0000");
            bill.setDependentNum("00");
            bill.setBirthDate("00000000");

        }
        log.debug("Prepared BC billing row for serviceCode={}", LogSafe.sanitize(bill.getBillingCode()));
        return bill;
    }

    private String submit;
    private String[] billingIds;

    public String getSubmit() {
        return submit;
    }

    @StrutsParameter
    public void setSubmit(String submit) {
        this.submit = submit;
    }

    public String[] getBillingIds() {
        return billingIds;
    }

    @StrutsParameter
    public void setBillingIds(String[] billingIds) {
        this.billingIds = billingIds;
    }
}
