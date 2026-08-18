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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.MyDateFormat;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingPercLimitDao;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingPercLimit;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.util.StringUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;

/**
 * Write access to the {@code billing_service} table — the private-code
 * admin workflow (add / update / delete). Split out of the former
 * {@code BillingONServiceCodeService}; reads now live on
 * {@link ServiceCodeLoader}.
 *
 * @since 2026-04-27
 */
@Service
@Transactional
public class ServiceCodePersister {

    private static final String MSG_SAME_DATE_EXISTS =
            "The selected Service Code has an entry for this Issue Date. <br> Select new issue date, or use 'Save' to update the existing entry.";
    private static final String MSG_SEARCH_FIRST =
            "You can not save the service code. Please search the service code first.";
    private static final String PRIVATE_CODE_SEARCH_PROMPT =
            "Type in a service code and search first to see if it is available.";
    private static final String FONT_RED_NOT = "<font color='red'>NOT</font>";
    private static final String VERB_UPDATED = "u" + "pdated";
    private static final String VERB_DELETED = "d" + "eleted";
    private static final String VERB_ADDED = "added";

    private final BillingServiceDao dao;
    private final BillingPercLimitDao billingPercLimitDao;

    @Autowired
    public ServiceCodePersister(BillingServiceDao dao, BillingPercLimitDao billingPercLimitDao) {
        this.dao = dao;
        this.billingPercLimitDao = billingPercLimitDao;
    }

    /** Test-friendly constructor — package-private, takes a DAO mock directly. */
    ServiceCodePersister(BillingServiceDao dao) {
        this(dao, null);
    }

    public boolean updateCodeByName(String serviceCode, String description, String value,
                                    String percentage, String billingservice_date, String gstFlag) {
        List<BillingService> bs = dao.findByServiceCode(serviceCode);
        for (BillingService b : bs) {
            b.setDescription(description);
            b.setValue(value);
            b.setPercentage(percentage);
            b.setGstFlag(Boolean.valueOf(gstFlag));
            b.setBillingserviceDate(ConversionUtils.fromDateString(billingservice_date));
            dao.merge(b);
        }
        return true;
    }

    public int addCodeByStr(String serviceCode, String description, String value,
                            String percentage, String billingservice_date, String gstFlag) {
        BillingService b = new BillingService();
        b.setServiceCompositecode("");
        b.setServiceCode(serviceCode);
        b.setDescription(description);
        b.setValue(value);
        b.setPercentage(percentage);
        b.setGstFlag("1".equals(gstFlag));
        b.setBillingserviceDate(ConversionUtils.fromDateString(billingservice_date));
        b.setSliFlag(false);
        b.setTerminationDate(ConversionUtils.fromDateString("9999-12-31"));

        dao.persist(b);
        return b.getId();
    }

    public boolean deletePrivateCode(String serviceCode) {
        List<BillingService> bs = dao.findByServiceCode(serviceCode);
        for (BillingService b : bs) {
            dao.remove(b.getId());
        }
        return true;
    }

    /** Applies the private-code add/edit/delete branches for the admin page. */
    public PrivateCodeMutationResult saveOrDeletePrivateCode(PrivateCodeMutationRequest request) {
        String submit = request.submit();
        if ("Save".equals(submit)) {
            return handlePrivateCodeSave(request);
        }
        if ("Delete".equals(submit)) {
            return handlePrivateCodeDelete(request);
        }
        return new PrivateCodeMutationResult("info", PRIVATE_CODE_SEARCH_PROMPT, "search", Map.of());
    }

    private PrivateCodeMutationResult handlePrivateCodeSave(PrivateCodeMutationRequest request) {
        String action = nullToEmpty(request.action());
        if (action.startsWith("edit")) {
            return handlePrivateCodeEdit(request, action);
        }
        if (action.startsWith("add")) {
            return handlePrivateCodeAdd(request, action);
        }
        return new PrivateCodeMutationResult("error", MSG_SEARCH_FIRST, "search", Map.of());
    }

    private PrivateCodeMutationResult handlePrivateCodeEdit(PrivateCodeMutationRequest request, String action) {
        String serviceCode = "_" + nullToEmpty(request.serviceCode());
        Map<String, String> formFields = new LinkedHashMap<>();
        if (!serviceCode.equals(action.substring("edit".length()))) {
            formFields.put("service_code", serviceCode);
            String message = new StringBuilder()
                    .append("You can ").append(FONT_RED_NOT)
                    .append(" save the service code - ")
                    .append(SafeEncode.forHtml(serviceCode))
                    .append(". Please search the service code first.")
                    .toString();
            return new PrivateCodeMutationResult("info", message, "search", formFields);
        }
        boolean ok = updateCodeByName(serviceCode, request.description(), request.value(), "0.00",
                request.billingServiceDate(), request.gstFlag());
        formFields.put("service_code", serviceCode);
        String safeCode = SafeEncode.forHtml(serviceCode);
        if (ok) {
            String message = new StringBuilder()
                    .append(safeCode).append(" is ").append(VERB_UPDATED)
                    .append(".<br>").append(PRIVATE_CODE_SEARCH_PROMPT)
                    .toString();
            return new PrivateCodeMutationResult("info", message, "search", formFields);
        }
        capturePrivateCodeFields(request, formFields, serviceCode);
        String message = new StringBuilder()
                .append(safeCode).append(" is ").append(FONT_RED_NOT)
                .append(" ").append(VERB_UPDATED)
                .append(". Action failed! Try edit it again.")
                .toString();
        return new PrivateCodeMutationResult("info", message, "edit" + serviceCode, formFields);
    }

    private PrivateCodeMutationResult handlePrivateCodeAdd(PrivateCodeMutationRequest request, String action) {
        String serviceCode = "_" + nullToEmpty(request.serviceCode());
        Map<String, String> formFields = new LinkedHashMap<>();
        String safeCode = SafeEncode.forHtml(serviceCode);
        if (!serviceCode.equals(action.substring("add".length()))) {
            formFields.put("service_code", serviceCode);
            String message = new StringBuilder()
                    .append("You can not save the service code - ")
                    .append(safeCode)
                    .append(". Please search the service code first.")
                    .toString();
            return new PrivateCodeMutationResult("error", message, "search", formFields);
        }
        int rc = addCodeByStr(serviceCode, request.description(), request.value(), "0.00",
                request.billingServiceDate(), request.gstFlag());
        if (rc > 0) {
            formFields.put("service_code", serviceCode);
            String message = new StringBuilder()
                    .append(safeCode).append(" is ").append(VERB_ADDED)
                    .append(".<br>").append(PRIVATE_CODE_SEARCH_PROMPT)
                    .toString();
            return new PrivateCodeMutationResult("info", message, "search", formFields);
        }
        capturePrivateCodeFields(request, formFields, serviceCode);
        return new PrivateCodeMutationResult("error",
                safeCode + " is not added. Action failed! Try edit it again.",
                "add" + serviceCode, formFields);
    }

    private PrivateCodeMutationResult handlePrivateCodeDelete(PrivateCodeMutationRequest request) {
        if (request.serviceCode() == null) {
            return new PrivateCodeMutationResult("info", "Please type in a right service code.", "search", Map.of());
        }
        String serviceCode = "_" + nullToEmpty(request.serviceCode());
        if (deletePrivateCode(serviceCode)) {
            Map<String, String> formFields = new LinkedHashMap<>();
            formFields.put("service_code", "_");
            String message = new StringBuilder()
                    .append(SafeEncode.forHtml(serviceCode))
                    .append(" is ").append(VERB_DELETED)
                    .append(".<br>").append(PRIVATE_CODE_SEARCH_PROMPT)
                    .toString();
            return new PrivateCodeMutationResult("info", message, "search", formFields);
        }
        return new PrivateCodeMutationResult("info", PRIVATE_CODE_SEARCH_PROMPT, "search", Map.of());
    }

    private static void capturePrivateCodeFields(PrivateCodeMutationRequest request,
                                                 Map<String, String> formFields,
                                                 String serviceCode) {
        formFields.put("service_code", serviceCode);
        formFields.put("description", nullToEmpty(request.description()));
        formFields.put("value", nullToEmpty(request.value()));
        formFields.put("billingservice_date", nullToEmpty(request.billingServiceDate()));
        formFields.put("gstFlag", nullToEmpty(request.gstFlag()));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * Bulk-update the description on every {@link BillingService} row sharing
     * a service code. Used by the {@code billingCodeUpdate.jsp} popup's
     * "update &lt;code&gt;" branch.
     *
     * @param serviceCode    String 5-char service code (e.g. {@code "A001A"})
     * @param newDescription String replacement description
     * @return int count of rows merged
     */
    public int updateDescriptionByServiceCode(String serviceCode, String newDescription) {
        List<BillingService> bs = dao.findByServiceCode(serviceCode);
        int updated = 0;
        for (BillingService b : bs) {
            b.setDescription(newDescription);
            dao.merge(b);
            updated++;
        }
        return updated;
    }

    public AddEditServiceCodeResult saveOrAdd(AddEditServiceCodeRequest request) {
        String value = request.value();
        if (request.percentage() != null
                && request.percentage().length() > 0
                && (value == null || value.trim().isEmpty())) {
            value = ".00";
        }

        String action = request.action();
        if (action == null) {
            return error(MSG_SEARCH_FIRST);
        }
        if (action.startsWith("edit")) {
            return handleEditSubmit(request, value);
        }
        if (action.startsWith("add")) {
            return handleAddSubmit(request, value);
        }
        return error(MSG_SEARCH_FIRST);
    }

    private AddEditServiceCodeResult handleEditSubmit(AddEditServiceCodeRequest request, String value) {
        String serviceCode = request.serviceCode();
        String action = request.action();
        if (serviceCode == null || !serviceCode.equals(action.substring("edit".length()))) {
            Properties prop = new Properties();
            if (serviceCode != null) {
                prop.setProperty("service_code", serviceCode);
            }
            return new AddEditServiceCodeResult("error",
                    "You can not save the service code - " + SafeEncode.forHtml(serviceCode)
                            + ". Please search the service code first.",
                    "search", "", prop, Map.of());
        }

        BillingService bs = findSubmittedBillingService(request);
        if (bs == null) {
            Properties prop = new Properties();
            populatePropFromRequest(prop, request, serviceCode);
            return new AddEditServiceCodeResult("error",
                    SafeEncode.forHtml(serviceCode) + " is not updated. Action failed! Try edit it again.",
                    "edit" + serviceCode, "", prop, Map.of());
        }

        bs.setDescription(request.description());
        bs.setValue(value);
        bs.setPercentage(request.percentage());
        bs.setBillingserviceDate(MyDateFormat.getSysDate(request.billingserviceDate()));
        bs.setSliFlag(request.sliFlag());
        bs.setTerminationDate(MyDateFormat.getSysDate(request.terminationDate()));
        applyDisplayStyle(bs, request.servicecodeStyle());

        upsertPercLimit(request, serviceCode);

        dao.merge(bs);
        Properties prop = new Properties();
        prop.setProperty("service_code", serviceCode);
        return new AddEditServiceCodeResult("success",
                SafeEncode.forHtml(serviceCode)
                        + " is updated.<br>Type in a service code and search first to see if it is available.",
                "search", "", prop, Map.of());
    }

    private BillingService findSubmittedBillingService(AddEditServiceCodeRequest request) {
        if (request.billingserviceNo() != null && !request.billingserviceNo().isBlank()) {
            try {
                return dao.find(Integer.parseInt(request.billingserviceNo()));
            } catch (NumberFormatException nfe) {
                throw new BillingValidationException("Invalid billingserviceNo: " + request.billingserviceNo(), nfe);
            }
        }
        List<BillingService> bsList = dao.findByServiceCode(request.serviceCode());
        return bsList.isEmpty() ? null : bsList.get(0);
    }

    private AddEditServiceCodeResult handleAddSubmit(AddEditServiceCodeRequest request, String value) {
        String serviceCode = request.serviceCode();
        String action = request.action();
        if (serviceCode == null || !serviceCode.equals(action.substring("add".length()))) {
            Properties prop = new Properties();
            if (serviceCode != null) {
                prop.setProperty("service_code", serviceCode);
            }
            return new AddEditServiceCodeResult("error",
                    "You can not save the service code - " + SafeEncode.forHtml(serviceCode)
                            + ". Please search the service code first.",
                    "search", "", prop, Map.of());
        }

        BillingService bs = new BillingService();
        bs.setServiceCompositecode("");
        bs.setServiceCode(serviceCode);
        bs.setDescription(request.description());
        bs.setValue(value);
        bs.setPercentage(request.percentage());
        bs.setBillingserviceDate(MyDateFormat.getSysDate(request.billingserviceDate()));
        bs.setSpecialty("");
        bs.setRegion("ON");
        bs.setAnaesthesia("00");
        bs.setTerminationDate(MyDateFormat.getSysDate(request.terminationDate()));
        bs.setSliFlag(request.sliFlag());
        applyDisplayStyle(bs, request.servicecodeStyle());
        bs.setGstFlag(false);

        if (hasPercWithLimits(request)) {
            BillingPercLimit bpl = new BillingPercLimit();
            bpl.setService_code(serviceCode);
            bpl.setMin(request.min());
            bpl.setMax(request.max());
            bpl.setEffective_date(MyDateFormat.getSysDate(request.billingserviceDate()));
            billingPercLimitDao.persist(bpl);
        }

        @SuppressWarnings("rawtypes")
        List scadList = dao.findByServiceCodeAndDate(bs.getServiceCode(), bs.getBillingserviceDate());
        if (scadList != null && !scadList.isEmpty()) {
            Properties prop = new Properties();
            populatePropFromBillingService(prop, bs, serviceCode);
            return new AddEditServiceCodeResult("error", MSG_SAME_DATE_EXISTS,
                    "edit" + serviceCode, "add" + serviceCode, prop, Map.of());
        }

        dao.persist(bs);
        Properties prop = new Properties();
        prop.setProperty("service_code", serviceCode);
        return new AddEditServiceCodeResult("success",
                SafeEncode.forHtml(serviceCode)
                        + " is added.<br>Type in a service code and search first to see if it is available.",
                "search", "", prop, Map.of());
    }

    private void upsertPercLimit(AddEditServiceCodeRequest request, String serviceCode) {
        if (!hasPercWithLimits(request)) {
            return;
        }
        List<BillingPercLimit> percLimits = billingPercLimitDao.findByServiceCode(serviceCode);
        if (percLimits.isEmpty()) {
            BillingPercLimit pl = new BillingPercLimit();
            pl.setService_code(serviceCode);
            pl.setMin(request.min());
            pl.setMax(request.max());
            pl.setEffective_date(MyDateFormat.getSysDate(request.billingserviceDate()));
            billingPercLimitDao.persist(pl);
            return;
        }
        BillingPercLimit pl = billingPercLimitDao.findByServiceCodeAndEffectiveDate(serviceCode,
                MyDateFormat.getSysDate(request.billingserviceDate()));
        if (pl != null) {
            pl.setMin(request.min());
            pl.setMax(request.max());
            billingPercLimitDao.merge(pl);
        }
    }

    private static boolean hasPercWithLimits(AddEditServiceCodeRequest request) {
        String percentage = request.percentage();
        String min = request.min();
        String max = request.max();
        return percentage != null && percentage.length() > 1
                && min != null && min.length() > 1
                && max != null && max.length() > 1;
    }

    private static void applyDisplayStyle(BillingService bs, String servicecodeStyle) {
        if (servicecodeStyle == null || servicecodeStyle.startsWith("-1")) {
            bs.setDisplayStyle(null);
            return;
        }
        String[] tmp = servicecodeStyle.split(",");
        try {
            bs.setDisplayStyle(Integer.parseInt(tmp[0]));
        } catch (NumberFormatException nfe) {
            bs.setDisplayStyle(null);
        }
    }

    private static void populatePropFromRequest(Properties prop, AddEditServiceCodeRequest request, String serviceCode) {
        prop.setProperty("service_code", serviceCode);
        setIfPresent(prop, "description", request.description());
        setIfPresent(prop, "value", request.value());
        setIfPresent(prop, "percentage", request.percentage());
        setIfPresent(prop, "billingservice_date", request.billingserviceDate());
        prop.setProperty("sliFlag", String.valueOf(request.sliFlag()));
    }

    private static void populatePropFromBillingService(Properties prop, BillingService bs, String serviceCode) {
        prop.setProperty("service_code", serviceCode);
        prop.setProperty("description", StringUtils.noNull(bs.getDescription()));
        prop.setProperty("value", StringUtils.noNull(bs.getValue()));
        prop.setProperty("percentage", StringUtils.noNull(bs.getPercentage()));
        prop.setProperty("billingservice_date",
                StringUtils.noNull(MyDateFormat.getMyStandardDate(bs.getBillingserviceDate())));
        prop.setProperty("sliFlag", StringUtils.noNull(String.valueOf(bs.getSliFlag())));
        prop.setProperty("termination_date",
                StringUtils.noNull(MyDateFormat.getMyStandardDate(bs.getTerminationDate())));
        if (bs.getDisplayStyle() != null) {
            prop.setProperty("displaystyle", StringUtils.noNull(bs.getDisplayStyle().toString()));
        }
    }

    private static void setIfPresent(Properties prop, String key, String value) {
        if (value != null) {
            prop.setProperty(key, value);
        }
    }

    private static AddEditServiceCodeResult error(String message) {
        return new AddEditServiceCodeResult("error", message, "search", "", new Properties(), Map.of());
    }

    public record AddEditServiceCodeRequest(String submitFrm, String action, String serviceCode,
                                            String billingserviceNo, String description, String value,
                                            String percentage, String billingserviceDate,
                                            String terminationDate, boolean sliFlag,
                                            String servicecodeStyle, String min, String max) {
    }

    public record AddEditServiceCodeResult(String alert, String message, String action, String action2,
                                           Properties prop, Map<String, String> codes) {
        public AddEditServiceCodeResult {
            prop = prop == null ? new Properties() : (Properties) prop.clone();
            codes = codes == null ? Map.of() : new LinkedHashMap<>(codes);
        }
    }

    public record PrivateCodeMutationRequest(String submit, String action, String serviceCode,
                                             String description, String value, String billingServiceDate,
                                             String gstFlag) {
    }

    public record PrivateCodeMutationResult(String alert, String message, String action,
                                            Map<String, String> formFields) {
        public PrivateCodeMutationResult {
            formFields = formFields == null ? Map.of() : new LinkedHashMap<>(formFields);
        }
    }
}
