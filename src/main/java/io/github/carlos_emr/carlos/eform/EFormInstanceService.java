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

package io.github.carlos_emr.carlos.eform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.dao.EFormValueDao;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.EFormValue;
import io.github.carlos_emr.carlos.eform.data.EForm;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Creates, reads, revises and retires saved eForm instances ({@code eform_data} rows) for callers
 * that are not the browser save form, such as the REST API.
 *
 * <p><b>Saved instances are never rewritten.</b> The web UI never updates a saved row's content:
 * re-saving an opened eForm stores a new row. This service keeps that rule. A revision stores a
 * new row built from the template and the submitted values, and retires the row it replaces
 * ({@code status = 0}). Both happen in one transaction, so a revision either fully replaces the
 * old row or changes nothing. A delete also only retires the row, the same way
 * {@code RemEForm2Action} does. The prior content stays in the chart history and can be restored
 * from the deleted-eForms list.</p>
 *
 * <p><b>Content comes from values, not markup.</b> Callers send field values. The HTML is built
 * the same way {@code AddEForm2Action} builds it ({@link EForm#setValues}, {@link EForm#setAction()},
 * {@link EForm#setNowDateTime()}). Values are written through jsoup attribute and text setters, so
 * caller text is escaped and cannot add markup to the stored record. A blank value clears the
 * template default in the rendered form but gets no {@code eform_values} row. That is the same
 * nonblank-only contract the browser save keeps for downstream readers.</p>
 *
 * <p><b>Authorization.</b> Each operation first needs the {@code _eform} right with no patient
 * attached, so a caller with no eForm rights cannot probe which fdids exist. It then checks the
 * same right for the patient the row belongs to, which applies patient-level restrictions. A
 * template with a {@code roleType} also needs {@code _eform.<roleType>}, the rule the patient
 * eForm list uses to hide such forms.</p>
 *
 * @since 2026-11-17
 */
@Service
public class EFormInstanceService {

    /** Audit content type the browser save writes for eForm data rows. */
    static final String AUDIT_CONTENT = "eform";

    private final SecurityInfoManager securityInfoManager;
    private final EformDataManager eformDataManager;
    private final EFormDao eFormDao;
    private final EFormDataDao eFormDataDao;
    private final EFormValueDao eFormValueDao;
    private final DemographicDao demographicDao;

    @Autowired
    public EFormInstanceService(SecurityInfoManager securityInfoManager, EformDataManager eformDataManager,
                                EFormDao eFormDao, EFormDataDao eFormDataDao, EFormValueDao eFormValueDao,
                                DemographicDao demographicDao) {
        this.securityInfoManager = securityInfoManager;
        this.eformDataManager = eformDataManager;
        this.eFormDao = eFormDao;
        this.eFormDataDao = eFormDataDao;
        this.eFormValueDao = eFormValueDao;
        this.demographicDao = demographicDao;
    }

    /**
     * A saved instance together with its stored field values.
     *
     * @param data the {@code eform_data} row
     * @param values the instance's {@code eform_values}, keyed by field name, in storage order
     */
    public record Instance(EFormData data, Map<String, String> values) {
    }

    /**
     * Reads one saved instance, including retired ones, so a caller can inspect the history.
     *
     * @param loggedInInfo the caller
     * @param fdid the instance id
     * @return the instance and its stored values
     * @throws SecurityException when the caller lacks {@code _eform} read access to the instance
     * @throws EFormInstanceException {@link EFormInstanceException.Reason#NOT_FOUND} when no row exists
     */
    @Transactional(readOnly = true)
    public Instance get(LoggedInInfo loggedInInfo, int fdid) {
        requireEFormPrivilege(loggedInInfo, SecurityInfoManager.READ, null);
        EFormData data = eFormDataDao.find(fdid);
        if (data == null) {
            throw new EFormInstanceException(EFormInstanceException.Reason.NOT_FOUND);
        }
        requireInstanceAccess(loggedInInfo, SecurityInfoManager.READ, data);

        Map<String, String> values = new LinkedHashMap<>();
        for (EFormValue value : eFormValueDao.findByFormDataId(fdid)) {
            values.put(value.getVarName(), value.getVarValue());
        }
        LogAction.addLog(loggedInInfo, LogConst.READ, AUDIT_CONTENT, String.valueOf(fdid),
                String.valueOf(data.getDemographicId()), null);
        return new Instance(data, values);
    }

    /**
     * Saves a new instance of an active template for a patient.
     *
     * @param loggedInInfo the caller, recorded as the instance's provider
     * @param fid the template id; the template must exist and be active
     * @param demographicNo the patient; the patient must exist
     * @param subject the instance subject; {@code null} uses the template's subject
     * @param values field values keyed by field name; {@code null} saves the template defaults
     * @return the new {@code eform_data} row
     * @throws SecurityException when the caller lacks {@code _eform} write access for the patient
     * @throws EFormInstanceException when the template or patient cannot be used, or a field name is blank
     */
    @Transactional
    public EFormData create(LoggedInInfo loggedInInfo, int fid, int demographicNo, String subject,
                            Map<String, String> values) {
        requireEFormPrivilege(loggedInInfo, SecurityInfoManager.WRITE, null);
        requireValidFieldNames(values);

        io.github.carlos_emr.carlos.commn.model.EForm template = eFormDao.findById(fid);
        // A retired template cannot start new instances; the web form list hides it for the same reason.
        if (template == null || !template.isCurrent()) {
            throw new EFormInstanceException(EFormInstanceException.Reason.TEMPLATE_UNAVAILABLE);
        }
        if (demographicDao.getDemographicById(demographicNo) == null) {
            throw new EFormInstanceException(EFormInstanceException.Reason.PATIENT_NOT_FOUND);
        }
        requireEFormPrivilege(loggedInInfo, SecurityInfoManager.WRITE, String.valueOf(demographicNo));
        requireRoleTypeAccess(loggedInInfo, template.getRoleType());

        String effectiveSubject = subject != null ? subject : template.getSubject();
        return saveFromTemplate(loggedInInfo, template, demographicNo, effectiveSubject, values);
    }

    /**
     * Replaces an instance with a new revision and retires the old row, in one transaction.
     *
     * <p>Like a browser re-save, the revision is built from the instance's template as it is now,
     * not from the old row's HTML. The values therefore fully replace the old ones (PUT
     * semantics): a field left out gets the template default. The old row is locked first, so of
     * two concurrent revisions of the same fdid, the second finds it retired and is refused rather
     * than forking the history.</p>
     *
     * @param loggedInInfo the caller, recorded as the revision's provider
     * @param fdid the instance to replace; it must still be current
     * @param subject the revision's subject; {@code null} keeps the old row's subject
     * @param values the complete field values of the revision
     * @return the new {@code eform_data} row; its id is the instance's new fdid
     * @throws SecurityException when the caller lacks {@code _eform} update access for the patient
     * @throws EFormInstanceException when the instance is missing or already retired, its template
     *         is gone, or a field name is blank
     */
    @Transactional
    public EFormData revise(LoggedInInfo loggedInInfo, int fdid, String subject, Map<String, String> values) {
        requireEFormPrivilege(loggedInInfo, SecurityInfoManager.UPDATE, null);
        requireValidFieldNames(values);

        EFormData previous = eFormDataDao.lockForAttachmentSync(fdid);
        if (previous == null) {
            throw new EFormInstanceException(EFormInstanceException.Reason.NOT_FOUND);
        }
        requireInstanceAccess(loggedInInfo, SecurityInfoManager.UPDATE, previous);
        if (!previous.isCurrent()) {
            throw new EFormInstanceException(EFormInstanceException.Reason.RETIRED);
        }

        // A retired template may still revise existing instances: the browser re-save of an opened
        // form loads the template by fid without checking its status either.
        io.github.carlos_emr.carlos.commn.model.EForm template = eFormDao.findById(previous.getFormId());
        if (template == null) {
            throw new EFormInstanceException(EFormInstanceException.Reason.TEMPLATE_UNAVAILABLE);
        }
        requireRoleTypeAccess(loggedInInfo, template.getRoleType());

        String effectiveSubject = subject != null ? subject : previous.getSubject();
        EFormData revision = saveFromTemplate(loggedInInfo, template, previous.getDemographicId(),
                effectiveSubject, values);

        previous.setCurrent(false);
        eFormDataDao.merge(previous);

        LogAction.addLog(loggedInInfo, LogConst.UPDATE, AUDIT_CONTENT, String.valueOf(revision.getId()),
                String.valueOf(previous.getDemographicId()), "Revision of eform data " + fdid);
        return revision;
    }

    /**
     * Retires an instance, the way the eForm list's delete button does. The row and its values are
     * kept and can be restored from the deleted-eForms list.
     *
     * @param loggedInInfo the caller
     * @param fdid the instance to retire; it must still be current
     * @throws SecurityException when the caller lacks {@code _eform} write access for the patient
     * @throws EFormInstanceException when the instance is missing or already retired
     */
    @Transactional
    public void delete(LoggedInInfo loggedInInfo, int fdid) {
        // WRITE, not DELETE: retiring is reversible, and the eForm list's delete button
        // (RemEForm2Action) needs only _eform w. The seeded doctor role holds w and not d, and a REST
        // delete must not be stricter than the same action in the browser.
        requireEFormPrivilege(loggedInInfo, SecurityInfoManager.WRITE, null);

        EFormData data = eFormDataDao.lockForAttachmentSync(fdid);
        if (data == null) {
            throw new EFormInstanceException(EFormInstanceException.Reason.NOT_FOUND);
        }
        requireInstanceAccess(loggedInInfo, SecurityInfoManager.WRITE, data);
        if (!data.isCurrent()) {
            throw new EFormInstanceException(EFormInstanceException.Reason.RETIRED);
        }

        data.setCurrent(false);
        eFormDataDao.merge(data);
        LogAction.addLog(loggedInInfo, LogConst.DELETE, AUDIT_CONTENT, String.valueOf(fdid),
                String.valueOf(data.getDemographicId()), null);
    }

    /**
     * Fills the template with the values and stores the result as a new current row plus its
     * nonblank {@code eform_values}, mirroring {@code AddEForm2Action.executeSubmission}.
     */
    private EFormData saveFromTemplate(LoggedInInfo loggedInInfo, io.github.carlos_emr.carlos.commn.model.EForm template,
                                       int demographicNo, String subject, Map<String, String> values) {
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        // Built from the entity already loaded instead of EForm(fid, demographicNo, providerNo), which
        // reloads the template through static SpringUtils lookups.
        EForm form = new EForm();
        form.setFid(String.valueOf(template.getId()));
        form.setFormName(template.getFormName());
        form.setFormFileName(template.getFileName());
        form.setFormCreator(template.getCreator());
        form.setFormHtml(template.getFormHtml());
        form.setShowLatestFormOnly(template.isShowLatestFormOnly());
        form.setPatientIndependent(template.isPatientIndependent());
        form.setRoleType(template.getRoleType());
        form.setDemographicNo(String.valueOf(demographicNo));
        form.setProviderNo(providerNo);
        form.setFormSubject(subject == null ? "" : subject);

        ArrayList<String> names = new ArrayList<>();
        ArrayList<String> fieldValues = new ArrayList<>();
        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (entry.getValue() == null) {
                    continue; // JSON null means "not submitted", which keeps the template default
                }
                names.add(entry.getKey());
                fieldValues.add(entry.getValue());
            }
        }

        // Rendering sees explicit clears; value rows keep the browser save's nonblank-only contract.
        form.setValues(names, fieldValues);
        for (int i = fieldValues.size() - 1; i >= 0; i--) {
            if (fieldValues.get(i).trim().isEmpty()) {
                names.remove(i);
                fieldValues.remove(i);
            }
        }
        form.setAction();
        form.setNowDateTime();

        // The manager re-checks _eform and writes the same ADD audit row the browser save writes.
        Integer savedId = eformDataManager.saveEformData(loggedInInfo, form);
        for (int i = 0; i < names.size(); i++) {
            EFormValue value = new EFormValue();
            value.setFormId(template.getId());
            value.setFormDataId(savedId);
            value.setDemographicId(demographicNo);
            value.setVarName(names.get(i));
            value.setVarValue(fieldValues.get(i));
            eFormValueDao.persist(value);
        }

        EFormData saved = eFormDataDao.find(savedId.intValue());
        if (saved == null) {
            throw new IllegalStateException("Saved eForm data row could not be read back");
        }
        return saved;
    }

    private void requireEFormPrivilege(LoggedInInfo loggedInInfo, String privilege, String demographicNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", privilege, demographicNo)) {
            throw new SecurityException("missing required sec object (_eform)");
        }
    }

    private void requireInstanceAccess(LoggedInInfo loggedInInfo, String privilege, EFormData data) {
        requireEFormPrivilege(loggedInInfo, privilege, String.valueOf(data.getDemographicId()));
        requireRoleTypeAccess(loggedInInfo, data.getRoleType());
    }

    private void requireRoleTypeAccess(LoggedInInfo loggedInInfo, String roleType) {
        String role = StringUtils.trimToNull(roleType);
        if (role == null) {
            return;
        }
        String objectName = "_eform." + role;
        if (!securityInfoManager.hasPrivilege(loggedInInfo, objectName, SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (" + objectName + ")");
        }
    }

    private static void requireValidFieldNames(Map<String, String> values) {
        if (values == null) {
            return;
        }
        for (String name : values.keySet()) {
            if (StringUtils.isBlank(name)) {
                throw new EFormInstanceException(EFormInstanceException.Reason.INVALID_FIELD_NAME);
            }
        }
    }

    /**
     * A request that names no usable instance, template or patient. The reason is safe to show to
     * an authorized caller: it never carries patient data.
     */
    public static class EFormInstanceException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        /** Why the operation was refused. */
        public enum Reason {
            /** No {@code eform_data} row has this fdid. */
            NOT_FOUND("EForm instance not found"),
            /** The instance was already deleted or replaced by a newer revision. */
            RETIRED("EForm instance is no longer current"),
            /** The template does not exist, or is retired and cannot start new instances. */
            TEMPLATE_UNAVAILABLE("EForm template not found or not active"),
            /** The patient does not exist. */
            PATIENT_NOT_FOUND("Patient not found"),
            /** A field name in the values was blank. */
            INVALID_FIELD_NAME("EForm field names must not be blank");

            private final String message;

            Reason(String message) {
                this.message = message;
            }

            public String message() {
                return message;
            }
        }

        private final Reason reason;

        public EFormInstanceException(Reason reason) {
            super(reason.message());
            this.reason = reason;
        }

        public Reason getReason() {
            return reason;
        }
    }
}
