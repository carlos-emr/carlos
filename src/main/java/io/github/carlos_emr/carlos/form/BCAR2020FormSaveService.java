/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form;

import io.github.carlos_emr.carlos.commn.dao.forms.FormBCAR2020Dao;
import io.github.carlos_emr.carlos.commn.dao.forms.FormBCAR2020DataDao;
import io.github.carlos_emr.carlos.commn.dao.forms.FormBCAR2020TextDao;
import io.github.carlos_emr.carlos.form.model.FormBCAR2020;
import io.github.carlos_emr.carlos.form.model.FormBCAR2020Data;
import io.github.carlos_emr.carlos.form.model.FormBCAR2020Text;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Saves each BC-AR 2020 version, including both field tables, in one transaction. */
@Service
public class BCAR2020FormSaveService {
    private final FormBCAR2020Dao bcar2020Dao;
    private final FormBCAR2020DataDao bcar2020DataDao;
    private final FormBCAR2020TextDao bcar2020TextDao;
    private final SecurityInfoManager securityInfoManager;

    public BCAR2020FormSaveService(FormBCAR2020Dao bcar2020Dao, FormBCAR2020DataDao bcar2020DataDao,
            FormBCAR2020TextDao bcar2020TextDao, SecurityInfoManager securityInfoManager) {
        this.bcar2020Dao = bcar2020Dao;
        this.bcar2020DataDao = bcar2020DataDao;
        this.bcar2020TextDao = bcar2020TextDao;
        this.securityInfoManager = securityInfoManager;
    }

    /**
     * Copies the patient's source version and applies the submitted page. No header or field group
     * survives a failed save, even when a batch has flushed and cleared the persistence context.
     * The caller may navigate only after this Spring-proxied method returns and commits.
     *
     * @param formId source version, or zero for a new form
     * @return the new version; its provider is always the signed-in actor
     */
    @Transactional(rollbackFor = Exception.class)
    public FormBCAR2020 saveVersion(LoggedInInfo info, int formId, int demographicNo, int page,
            Map<String, String> values) {
        if (!securityInfoManager.hasPrivilege(info, "_form", "w", demographicNo)) {
            throw new SecurityException("missing required sec object (_form)");
        }
        if (!securityInfoManager.hasPrivilege(info, "_demographic", "w", demographicNo)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
        FrmBCAR2020Record.requireFormOfPatient(bcar2020Dao, formId, demographicNo);
        String providerNo = info.getLoggedInProviderNo();

        List<FormBCAR2020Data> currentRecords = bcar2020DataDao.findFields(formId);
        List<FormBCAR2020Text> currentTextRecords = bcar2020TextDao.findFields(formId);
        List<FormBCAR2020Data> addRecords = new ArrayList<>();
        List<FormBCAR2020Text> addTextRecords = new ArrayList<>();

        Map<String, String> currentValues = getMappedRecords(currentRecords);
        currentValues.putAll(getMappedTextRecords(currentTextRecords));

        Set<String> keys = values.keySet();

        FormBCAR2020 formBCAR2020 = new FormBCAR2020();
        formBCAR2020.setDemographicNo(demographicNo);
        formBCAR2020.setProviderNo(providerNo);
        formBCAR2020.setFormCreated(new Date());

        bcar2020Dao.persist(formBCAR2020);
        formId = formBCAR2020.getId();

        // copy all current values to new form
        for (FormBCAR2020Data rec : currentRecords) {
            if ((rec.getPageNo() == null || !rec.getPageNo().equals(page)) || StringUtils.trimToNull(values.get(rec.getField())) != null) {
                FormBCAR2020Data copy = new FormBCAR2020Data(formId, rec.getProviderNo(), rec.getPageNo(), rec.getField(), rec.getValue());
                copy.setFieldEdited(rec.getFieldEdited());
                addRecords.add(copy);
            }
        }
        for (FormBCAR2020Text rec : currentTextRecords) {
            if ((rec.getPageNo() == null || !rec.getPageNo().equals(page)) || StringUtils.trimToNull(values.get(rec.getField())) != null) {
                FormBCAR2020Text copy = new FormBCAR2020Text(formId, rec.getProviderNo(), rec.getPageNo(), rec.getField(), rec.getValue());
                copy.setFieldEdited(rec.getFieldEdited());
                addTextRecords.add(copy);
            }
        }

        for (String key : keys) {
            if (key.contains("mt_")) {
                // Multi text field type (this gets stored in a different table due to the size requirements
                String value = StringUtils.trimToNull(values.get(key));
                // If the field is a global field (exists on all pages), set the page number to 0
                Integer setPage = FrmBCAR2020Record.isGlobalFieldName(key) ? 0 : page;

                if (currentValues.get(setPage + "_" + key) != null) {
                    FormBCAR2020Text record = getTextRecordByKey(addTextRecords, key);

                    if (record != null) {
                        if (value != null && !value.equals(currentValues.get(setPage + "_" + key))) {
                            record.setValue(value);
                        } else if (value == null) {
                            record.setValue("");
                        }
                    }

                } else if (currentValues.get(setPage + "_" + key) == null && value != null) {
                    addTextRecords.add(new FormBCAR2020Text(formId, providerNo, setPage, key, value));
                }
            } else if (key.contains("_")) {
                String value = StringUtils.trimToNull(values.get(key));
                // If the field is a global field (exists on all pages), set the page number to 0
                Integer setPage = FrmBCAR2020Record.isGlobalFieldName(key) ? 0 : page;

                if (currentValues.get(setPage + "_" + key) != null) {
                    FormBCAR2020Data record = getRecordByKey(addRecords, key);

                    if (record != null) {
                        if (value != null && !value.equals(currentValues.get(setPage + "_" + key))) {
                            value = ((key.startsWith("c_") && value.equals("checked")) || value.equals("on")) ? "X" : value;
                            record.setValue(value);
                        } else if (value == null) {
                            record.setValue("");
                        }
                    }

                } else if (currentValues.get(setPage + "_" + key) == null && value != null) {
                    value = ((key.startsWith("c_") && value.equals("checked")) || value.equals("on")) ? "X" : value;

                    addRecords.add(new FormBCAR2020Data(formId, providerNo, setPage, key, value));
                }
            }
        }

        bcar2020DataDao.batchPersistAtomically(addRecords, 50);
        bcar2020TextDao.batchPersistAtomically(addTextRecords, 50);
        return formBCAR2020;
    }

    private FormBCAR2020Data getRecordByKey(List<FormBCAR2020Data> records, String key) {
        FormBCAR2020Data record = null;

        for (FormBCAR2020Data rec : records) {
            if (rec.getField().equals(key)) {
                record = rec;
            }
        }

        return record;
    }

    private Map<String, String> getMappedRecords(List<FormBCAR2020Data> records) {
        Map<String, String> recordMap = new HashMap<String, String>();

        if (records != null) {
            for (FormBCAR2020Data record : records) {
                recordMap.put(record.getPageNo() + "_" + record.getField(), record.getValue());
            }
        }

        return recordMap;
    }

    private FormBCAR2020Text getTextRecordByKey(List<FormBCAR2020Text> records, String key) {
        FormBCAR2020Text record = null;

        for (FormBCAR2020Text rec : records) {
            if (rec.getField().equals(key)) {
                record = rec;
            }
        }

        return record;
    }

    private Map<String, String> getMappedTextRecords(List<FormBCAR2020Text> records) {
        Map<String, String> recordMap = new HashMap<String, String>();

        if (records != null) {
            for (FormBCAR2020Text record : records) {
                recordMap.put(record.getPageNo() + "_" + record.getField(), record.getValue());
            }
        }

        return recordMap;
    }
}
