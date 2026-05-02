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
}
