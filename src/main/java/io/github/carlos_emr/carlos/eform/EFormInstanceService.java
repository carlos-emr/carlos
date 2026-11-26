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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.TokenQueue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDocsDao;
import io.github.carlos_emr.carlos.commn.dao.EFormValueDao;
import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.EFormDocs;
import io.github.carlos_emr.carlos.commn.model.EFormValue;
import io.github.carlos_emr.carlos.documentManager.ConvertToEdoc;
import io.github.carlos_emr.carlos.eform.data.EForm;
import io.github.carlos_emr.carlos.encounter.data.EctProgram;
import io.github.carlos_emr.carlos.encounter.data.EctProgramManager;
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
 * caller text is escaped and cannot add markup to the stored record. Only values the renderer
 * actually writes are accepted: each name must be a text, hidden, date, checkbox or radio input,
 * a textarea or a select on the template, a radio or select value must match one of its choices,
 * and a checkbox value must be the box's own {@code value} ({@code "on"} when it has none), as a
 * browser submits it. Anything else (an unknown name, {@code type="number"}, a control the
 * renderer would fill by inserting text into its markup) is refused rather than stored as a value
 * the form never shows. The one exception is a revision: a value the template cannot show that is
 * exactly what the revised row stored (a browser save keeps such values too) is carried forward as
 * a value row, sent back or omitted, so a read can always be sent back. A blank value clears the template default in the rendered form (a checkbox included) but
 * gets no {@code eform_values} row. That is the same nonblank-only contract the browser save keeps
 * for downstream readers; a read reports such a clear as {@code ""} so it survives a round trip.</p>
 *
 * <p><b>What a save does, as the browser save does it.</b> A new instance gets the template's
 * {@code oscarDB=} prefill (patient and provider fields) for every field the caller does not send,
 * as the new-form page shows it before the clinician submits. Every save then runs the template's
 * chart writes ({@link EFormUtil#writeEformTemplate}: encounter and CPP notes, documents,
 * preventions, messages, ticklers, consultation requests), exactly once, in the same transaction.
 * A revision also takes over the instance's place in the chart: its attachments are copied to the
 * new row, and the consultation packets and eForms that attached the old row now attach the
 * revision. Fax, e-mail, PDF and eDoc handoffs are not part of this API.</p>
 *
 * <p><b>Authorization.</b> Each operation first needs the {@code _eform} right with no patient
 * attached, so a caller with no eForm rights cannot probe which fdids exist. It then checks the
 * same right for the patient the row belongs to, and the patient-record restrictions
 * ({@link SecurityInfoManager#isAllowedAccessToPatientRecord}). A template with a {@code roleType}
 * also needs {@code _eform.<roleType>}, the rule the patient eForm list uses to hide such forms.
 * Creating and revising need {@code w}, as the browser save does. A refusal for an existing
 * instance is an {@link InstanceAccessDeniedException}, which the REST layer answers exactly as it
 * answers a missing one; a refusal for the patient named in a create is answered exactly as a
 * patient that does not exist.</p>
 *
 * @since 2026-11-17
 */
@Service
public class EFormInstanceService {

    /** Audit content type the browser save writes for eForm data rows. */
    static final String AUDIT_CONTENT = "eform";

    /** Width of {@code eform_values.var_name}; a longer name fails on a strict server and is cut on others. */
    static final int FIELD_NAME_MAX_LENGTH = 30;

    /** Width of {@code eform_data.subject}. */
    static final int SUBJECT_MAX_LENGTH = 255;

    private final SecurityInfoManager securityInfoManager;
    private final EformDataManager eformDataManager;
    private final EFormDao eFormDao;
    private final EFormDataDao eFormDataDao;
    private final EFormValueDao eFormValueDao;
    private final DemographicDao demographicDao;
    private final EFormDocsDao eFormDocsDao;
    private final ConsultDocsDao consultDocsDao;
    private final EctProgramManager ectProgramManager;

    @Autowired
    public EFormInstanceService(SecurityInfoManager securityInfoManager, EformDataManager eformDataManager,
                                EFormDao eFormDao, EFormDataDao eFormDataDao, EFormValueDao eFormValueDao,
                                DemographicDao demographicDao, EFormDocsDao eFormDocsDao,
                                ConsultDocsDao consultDocsDao, EctProgramManager ectProgramManager) {
        this.securityInfoManager = securityInfoManager;
        this.eformDataManager = eformDataManager;
        this.eFormDao = eFormDao;
        this.eFormDataDao = eFormDataDao;
        this.eFormValueDao = eFormValueDao;
        this.demographicDao = demographicDao;
        this.eFormDocsDao = eFormDocsDao;
        this.consultDocsDao = consultDocsDao;
        this.ectProgramManager = ectProgramManager;
    }

    /**
     * A saved instance together with its field values.
     *
     * @param data the {@code eform_data} row
     * @param values the instance's {@code eform_values}, keyed by field name, in storage order,
     *               followed by {@code ""} for each field the instance cleared from a nonblank
     *               template default (those have no {@code eform_values} row)
     */
    public record Instance(EFormData data, Map<String, String> values) {
    }

    /**
     * Reads one saved instance, including retired ones, so a caller can inspect the history.
     *
     * @param loggedInInfo the caller
     * @param fdid the instance id
     * @return the instance and its stored values
     * @throws SecurityException when the caller lacks {@code _eform} read access; an
     *         {@link InstanceAccessDeniedException} when the refusal is for this instance's patient or role
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

        Map<String, String> values = storedValues(fdid);
        // A revision is rebuilt from the template as it is now, so a clear only round-trips if the
        // caller sends it back; report the clears against that same template.
        io.github.carlos_emr.carlos.commn.model.EForm template = eFormDao.findById(data.getFormId());
        if (template != null) {
            addClearedDefaults(template.getFormHtml(), data.getFormData(), values);
        }
        // In a transaction of its own: addLog falls back to writing in the caller's transaction when
        // its executor is saturated, and this one is read-only, so that row would be lost.
        LogAction.addLogSynchronousInNewTransaction(loggedInInfo, LogConst.READ, AUDIT_CONTENT, String.valueOf(fdid),
                String.valueOf(data.getDemographicId()), null);
        return new Instance(data, values);
    }

    /**
     * Saves a new instance of an active template for a patient.
     *
     * <p>Fields the caller leaves out get the template's {@code oscarDB=} prefill, as the new-form
     * page shows them, or else the template default. The template's chart writes then run.</p>
     *
     * @param loggedInInfo the caller, recorded as the instance's provider
     * @param fid the template id; the template must exist and be active
     * @param demographicNo the patient; the patient must exist
     * @param subject the instance subject; {@code null} uses the template's subject
     * @param values field values keyed by field name; {@code null} saves the prefill and template defaults
     * @param contextPath the application's context path, for the links the chart writes put in notes
     * @return the new {@code eform_data} row
     * @throws SecurityException when the caller lacks {@code _eform} write access or the template's role
     * @throws EFormInstanceException when the template cannot be used, the patient does not exist or
     *         may not be written to by this caller (one answer for both), the subject or a field name
     *         is too long, or a field name is blank or names no control the template can show it in
     */
    @Transactional
    public EFormData create(LoggedInInfo loggedInInfo, int fid, int demographicNo, String subject,
                            Map<String, String> values, String contextPath) {
        requireEFormPrivilege(loggedInInfo, SecurityInfoManager.WRITE, null);
        requireValidFieldNames(values);
        requireValidSubject(subject);

        io.github.carlos_emr.carlos.commn.model.EForm template = eFormDao.findById(fid);
        // A retired template cannot start new instances; the web form list hides it for the same reason.
        if (template == null || !template.isCurrent()) {
            throw new EFormInstanceException(EFormInstanceException.Reason.TEMPLATE_UNAVAILABLE);
        }
        // The patient checks come first and are refused exactly as a missing patient is, so a caller
        // cannot learn which demographic numbers exist or which charts are closed to it. Neither
        // check needs the row to exist.
        try {
            requireEFormPrivilege(loggedInInfo, SecurityInfoManager.WRITE, String.valueOf(demographicNo));
            requirePatientRecordAccess(loggedInInfo, demographicNo);
        } catch (SecurityException denied) {
            throw new EFormInstanceException(EFormInstanceException.Reason.PATIENT_NOT_FOUND);
        }
        if (demographicDao.getDemographicById(demographicNo) == null) {
            throw new EFormInstanceException(EFormInstanceException.Reason.PATIENT_NOT_FOUND);
        }
        requireRoleTypeAccess(loggedInInfo, template.getRoleType());

        String effectiveSubject = subject != null ? subject : template.getSubject();
        return saveFromTemplate(loggedInInfo, template, demographicNo, effectiveSubject,
                new SaveInputs(values, Map.of(), Map.of(), true, contextPath));
    }

    /**
     * Replaces an instance with a new revision and retires the old row, in one transaction.
     *
     * <p>Like a browser re-save, the revision is built from the instance's template as it is now,
     * not from the old row's HTML. The values therefore fully replace the old ones (PUT
     * semantics): a field left out gets the template default. {@code values} itself is required,
     * so a body that names no fields cannot silently reset the form. A stored value of a field the
     * template cannot show is carried forward unchanged, whether sent back or left out; it cannot
     * be changed through the API. A {@code <select multiple>} keeps every choice it had while its
     * value is sent back unchanged. The old row is locked first, so of two concurrent revisions of
     * the same fdid, the second finds it retired and is refused rather than forking the history.</p>
     *
     * <p>The revision takes over the old row's place: its attachments are copied to the revision,
     * and consultation packets and eForms that attached the old row attach the revision instead.
     * The template's chart writes run, as they do on every browser save.</p>
     *
     * <p>For a template that shows only its latest form ({@code showLatestFormOnly}), a revision of
     * a row that is no longer the newest one for the patient is refused: the chart would then show
     * the revision and hide the newer form a clinician saved in the browser.</p>
     *
     * @param loggedInInfo the caller, recorded as the revision's provider
     * @param fdid the instance to replace; it must still be current
     * @param subject the revision's subject; {@code null} keeps the old row's subject
     * @param values the complete field values of the revision; required
     * @param contextPath the application's context path, for the links the chart writes put in notes
     * @return the new {@code eform_data} row; its id is the instance's new fdid
     * @throws SecurityException when the caller lacks {@code _eform} write access
     * @throws InstanceAccessDeniedException when the caller may not revise this instance
     * @throws EFormInstanceException when the values are missing, the instance is missing, already
     *         retired or superseded by a newer form, its template is gone, the subject or a field
     *         name is too long, or a field name is blank or names no control the template can show it in
     */
    @Transactional
    public EFormData revise(LoggedInInfo loggedInInfo, int fdid, String subject, Map<String, String> values,
                            String contextPath) {
        // WRITE, as the browser re-save (AddEForm2Action) needs; an UPDATE-only role cannot re-save
        // an eForm in the browser either.
        requireEFormPrivilege(loggedInInfo, SecurityInfoManager.WRITE, null);
        if (values == null) {
            throw new EFormInstanceException(EFormInstanceException.Reason.VALUES_REQUIRED);
        }
        requireValidFieldNames(values);
        requireValidSubject(subject);

        EFormData previous = eFormDataDao.lockForAttachmentSync(fdid);
        if (previous == null) {
            throw new EFormInstanceException(EFormInstanceException.Reason.NOT_FOUND);
        }
        requireInstanceAccess(loggedInInfo, SecurityInfoManager.WRITE, previous);
        if (!previous.isCurrent()) {
            throw new EFormInstanceException(EFormInstanceException.Reason.RETIRED);
        }

        // A retired template may still revise existing instances: the browser re-save of an opened
        // form loads the template by fid without checking its status either.
        io.github.carlos_emr.carlos.commn.model.EForm template = eFormDao.findById(previous.getFormId());
        if (template == null) {
            throw new EFormInstanceException(EFormInstanceException.Reason.TEMPLATE_UNAVAILABLE);
        }
        try {
            requireRoleTypeAccess(loggedInInfo, template.getRoleType());
        } catch (SecurityException denied) {
            throw new InstanceAccessDeniedException(denied.getMessage());
        }
        if (template.isShowLatestFormOnly() && hasNewerCurrentForm(previous)) {
            throw new EFormInstanceException(EFormInstanceException.Reason.SUPERSEDED);
        }

        String effectiveSubject = subject != null ? subject : previous.getSubject();
        Map<String, String> previousValues = storedValues(fdid);
        EFormData revision = saveFromTemplate(loggedInInfo, template, previous.getDemographicId(), effectiveSubject,
                new SaveInputs(values, previousValues, multipleSelections(previous.getFormData()), false, contextPath));

        previous.setCurrent(false);
        eFormDataDao.merge(previous);
        moveAttachments(fdid, revision.getId());

        LogAction.addLog(loggedInInfo, LogConst.UPDATE, AUDIT_CONTENT, String.valueOf(revision.getId()),
                String.valueOf(previous.getDemographicId()), "Revision of eform data " + fdid);
        return revision;
    }

    /**
     * Whether the patient has a current form of the same template saved after this one. A browser
     * re-save stores a new row and leaves the opened one current, so this is the only sign of it.
     */
    private boolean hasNewerCurrentForm(EFormData previous) {
        List<EFormData> current = eFormDataDao.findByDemographicIdAndFormId(previous.getDemographicId(),
                previous.getFormId());
        return current != null && current.stream()
                .anyMatch(row -> row.getId() != null && row.getId() > previous.getId());
    }

    /**
     * Gives the revision the old row's place in the chart: the old row's attachments are copied to
     * the revision (the old row keeps its own, so its history still shows what it carried), and
     * the consultation packets and other eForms that attached the old row attach the revision. A
     * retired eForm is otherwise listed as unavailable in a consultation packet and left out of it.
     */
    private void moveAttachments(int oldFdid, int newFdid) {
        for (EFormDocs attached : eFormDocsDao.findByFdid(oldFdid)) {
            EFormDocs copy = new EFormDocs(newFdid, attached.getDocumentNo(), attached.getDocType(),
                    attached.getProviderNo());
            copy.setLabType(attached.getLabType());
            copy.setAttachDate(attached.getAttachDate());
            eFormDocsDao.persist(copy);
        }
        for (ConsultDocs packet : consultDocsDao.findActiveByAttachedDocumentForUpdate(ConsultDocs.DOCTYPE_EFORM, oldFdid)) {
            packet.setDocumentNo(newFdid);
            consultDocsDao.merge(packet);
        }
        for (EFormDocs attaching : eFormDocsDao.findActiveByAttachedDocumentForUpdate(EFormDocs.DOCTYPE_EFORM, oldFdid)) {
            attaching.setDocumentNo(newFdid);
            eFormDocsDao.merge(attaching);
        }
    }

    /**
     * Retires an instance, the way the eForm list's delete button does. The row and its values are
     * kept and can be restored from the deleted-eForms list.
     *
     * @param loggedInInfo the caller
     * @param fdid the instance to retire; it must still be current
     * @throws SecurityException when the caller lacks {@code _eform} write access; an
     *         {@link InstanceAccessDeniedException} when the refusal is for this instance's patient or role
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
     * What one save is built from.
     *
     * @param values the caller's values, or {@code null}
     * @param previousValues the stored values of the row a revision replaces; empty on create
     * @param multipleSelections every selected choice of each {@code <select multiple>} in the row a
     *                           revision replaces; empty on create
     * @param prefill whether fields the caller leaves out get the template's {@code oscarDB=} values,
     *                as on the new-form page (create only: a browser re-save does not refresh them)
     * @param contextPath the application's context path, for the chart writes' links
     */
    private record SaveInputs(Map<String, String> values, Map<String, String> previousValues,
                              Map<String, List<String>> multipleSelections, boolean prefill, String contextPath) {
    }

    /**
     * Fills the template with the values and stores the result as a new current row plus its
     * nonblank {@code eform_values}, then runs the template's chart writes, mirroring
     * {@code AddEForm2Action.executeSubmission}.
     */
    private EFormData saveFromTemplate(LoggedInInfo loggedInInfo, io.github.carlos_emr.carlos.commn.model.EForm template,
                                       int demographicNo, String subject, SaveInputs inputs) {
        Map<String, String> previousValues = inputs.previousValues();
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

        Map<String, String> values = inputs.values();
        ValuePlan plan = planValues(template.getFormHtml(), values, previousValues);
        Set<String> checkboxClears = plan.checkboxClears();
        ArrayList<String> names = new ArrayList<>();
        ArrayList<String> fieldValues = new ArrayList<>();
        Map<String, List<String>> submittedSelections = new LinkedHashMap<>();
        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (entry.getValue() == null) {
                    continue; // JSON null means "not submitted", which keeps the template default
                }
                if (checkboxClears.contains(entry.getKey())) {
                    continue; // setValues would check the box for any value; cleared below
                }
                if (plan.carried().containsKey(entry.getKey())) {
                    continue; // the template cannot show it; stored as a value row below
                }
                names.add(entry.getKey());
                fieldValues.add(entry.getValue());
                // A multiple select's value row holds only its first choice. Sent back unchanged, it
                // keeps every choice the revised row had, as a browser re-save submits them all.
                List<String> selections = inputs.multipleSelections().get(entry.getKey());
                if (selections != null && entry.getValue().equals(previousValues.get(entry.getKey()))) {
                    submittedSelections.put(entry.getKey(), selections);
                }
            }
        }

        // The new-form page fills oscarDB= fields before the clinician submits, so the browser save
        // stores them like any other submitted value. setValues then overwrites the ones the caller sent.
        Map<String, String> prefilled = inputs.prefill() ? applyDatabasePrefill(form, values) : Map.of();

        // Rendering sees explicit clears; value rows keep the browser save's nonblank-only contract.
        form.setValues(names, fieldValues, submittedSelections);
        for (String name : checkboxClears) {
            form.uncheckCheckboxes(name);
        }
        for (int i = fieldValues.size() - 1; i >= 0; i--) {
            if (fieldValues.get(i).trim().isEmpty()) {
                names.remove(i);
                fieldValues.remove(i);
            }
        }
        for (Map.Entry<String, String> carried : plan.carried().entrySet()) {
            if (!carried.getValue().trim().isEmpty()) {
                names.add(carried.getKey());
                fieldValues.add(carried.getValue());
            }
        }
        for (Map.Entry<String, String> filled : prefilled.entrySet()) {
            names.add(filled.getKey());
            fieldValues.add(filled.getValue());
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

        // The template's chart writes, with the same nonblank values the browser save passes. In this
        // transaction, so a failure stores neither the form nor half of its chart entries.
        EFormUtil.writeEformTemplate(loggedInInfo, names, fieldValues, form, String.valueOf(savedId),
                EctProgram.programFor(ectProgramManager, providerNo),
                inputs.contextPath() == null ? "" : inputs.contextPath());

        EFormData saved = eFormDataDao.find(savedId.intValue());
        if (saved == null) {
            throw new IllegalStateException("Saved eForm data row could not be read back");
        }
        return saved;
    }

    /**
     * Fills the form's {@code oscarDB=} fields the way the new-form page does, and returns the
     * values it filled into fields the caller did not send, for their value rows. A name longer than
     * a value row can hold is rendered but gets no row, as it could not be read back by name.
     */
    private static Map<String, String> applyDatabasePrefill(EForm form, Map<String, String> values) {
        String before = form.getFormHtml();
        form.setDatabaseAPs();
        Document filled = parse(form.getFormHtml());
        Map<String, String> prefilled = new LinkedHashMap<>();
        if (filled == null || before == null || before.equals(form.getFormHtml())) {
            return prefilled;
        }
        String marker = StringUtils.removeEnd(EFormLoader.getMarker(), "=").toLowerCase(Locale.ROOT);
        for (Element control : filled.select("[" + marker + "][name]")) {
            String name = control.attr("name");
            boolean sent = values != null && values.get(name) != null;
            if (sent || StringUtils.isBlank(name) || name.length() > FIELD_NAME_MAX_LENGTH || prefilled.containsKey(name)) {
                continue;
            }
            String value = switch (control.normalName()) {
                case "textarea" -> control.wholeText();
                case "select" -> {
                    Element option = control.selectFirst("option[selected]");
                    yield option == null ? "" : optionValue(option);
                }
                default -> control.attr("value");
            };
            if (StringUtils.isNotBlank(value)) {
                prefilled.put(name, value);
            }
        }
        return prefilled;
    }

    /** Every selected choice of each {@code <select multiple>} that has more than one, by name. */
    private static Map<String, List<String>> multipleSelections(String instanceHtml) {
        Map<String, List<String>> selections = new LinkedHashMap<>();
        Document instance = parse(instanceHtml);
        if (instance == null) {
            return selections;
        }
        for (Element select : instance.select("select[multiple][name]")) {
            List<String> chosen = select.select("option[selected]").stream().map(EFormInstanceService::optionValue).toList();
            if (chosen.size() > 1) {
                selections.putIfAbsent(select.attr("name"), chosen);
            }
        }
        return selections;
    }

    private void requireEFormPrivilege(LoggedInInfo loggedInInfo, String privilege, String demographicNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", privilege, demographicNo)) {
            throw new SecurityException("missing required sec object (_eform)");
        }
    }

    /**
     * The checks for one existing row. Their refusal is an {@link InstanceAccessDeniedException}
     * so the REST layer can answer it as it answers a missing row: a caller who passes the global
     * gate must not learn that an fdid exists for a patient or role it cannot see.
     */
    private void requireInstanceAccess(LoggedInInfo loggedInInfo, String privilege, EFormData data) {
        try {
            requireEFormPrivilege(loggedInInfo, privilege, String.valueOf(data.getDemographicId()));
            requirePatientRecordAccess(loggedInInfo, data.getDemographicId());
            requireRoleTypeAccess(loggedInInfo, data.getRoleType());
        } catch (SecurityException denied) {
            throw new InstanceAccessDeniedException(denied.getMessage());
        }
    }

    private void requirePatientRecordAccess(LoggedInInfo loggedInInfo, Integer demographicNo) {
        if (!securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, demographicNo)) {
            throw new SecurityException("Access to this patient record is restricted");
        }
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

    /** The input types {@link EForm#setValues} writes a value into (its jsoup setter cases). */
    private static final Set<String> SETTABLE_INPUT_TYPES = Set.of("text", "hidden", "date", "checkbox", "radio");

    /**
     * How the submitted values are saved.
     *
     * @param checkboxClears checkboxes a blank value explicitly unchecks
     * @param carried values the template cannot show, kept as value rows only, because they are
     *                exactly what the revised row stored
     */
    private record ValuePlan(Set<String> checkboxClears, Map<String, String> carried) {
    }

    /**
     * Refuses any submitted value the renderer would not show: a name with no control on the
     * template, a control type {@link EForm#setValues} skips (such as {@code type="number"}) or
     * fills by inserting text into its markup, controls of mixed types under one name, a radio or
     * select value that matches none of its choices, and a checkbox value other than the box's
     * own. Null values are not submitted and pass. A refused value that equals the revised row's
     * stored value is carried instead, and so is such a stored value when it is not sent back, so
     * a revision never loses what a browser save stored.
     */
    private static ValuePlan planValues(String templateHtml, Map<String, String> values,
                                        Map<String, String> previousValues) {
        Set<String> checkboxClears = new LinkedHashSet<>();
        Map<String, String> carried = new LinkedHashMap<>();
        boolean submitted = values != null && values.values().stream().anyMatch(java.util.Objects::nonNull);
        if (!submitted && previousValues.isEmpty()) {
            return new ValuePlan(checkboxClears, carried);
        }
        Document template = parse(templateHtml);
        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String name = entry.getKey();
                String value = entry.getValue();
                if (value == null) {
                    continue;
                }
                String type = template == null ? null : settableType(template, name);
                if (type != null && applies(template, name, type, value, checkboxClears)) {
                    continue;
                }
                if (value.equals(previousValues.get(name))) {
                    carried.put(name, value);
                    continue;
                }
                throw new EFormInstanceException(EFormInstanceException.Reason.UNSUPPORTED_FIELD);
            }
        }
        for (Map.Entry<String, String> stored : previousValues.entrySet()) {
            String name = stored.getKey();
            boolean omitted = values == null || values.get(name) == null;
            if (omitted && (template == null || settableType(template, name) == null)) {
                carried.put(name, stored.getValue());
            }
        }
        return new ValuePlan(checkboxClears, carried);
    }

    /** Whether {@link EForm#setValues} shows this value in the named control of this type. */
    private static boolean applies(Document template, String name, String type, String value,
                                   Set<String> checkboxClears) {
        boolean blank = value.trim().isEmpty();
        return switch (type) {
            case "checkbox" -> {
                if (blank) {
                    checkboxClears.add(name);
                    yield true;
                }
                // The renderer checks only the first box of a name, so a value for a group of
                // boxes cannot say which one it means.
                var boxes = namedControls(template, name);
                yield boxes.size() == 1 && value.equals(checkboxValue(boxes.first()));
            }
            case "radio" -> blank || namedControls(template, name).stream()
                    .anyMatch(radio -> value.equals(radio.attr("value")));
            case "select" -> blank || namedControls(template, name).select("option").stream()
                    .anyMatch(option -> value.equals(optionValue(option)));
            default -> true;
        };
    }

    /** The value a browser submits for a checked box: its {@code value}, or {@code "on"} without one. */
    private static String checkboxValue(Element box) {
        return box.hasAttr("value") ? box.attr("value") : "on";
    }

    private Map<String, String> storedValues(int fdid) {
        Map<String, String> values = new LinkedHashMap<>();
        for (EFormValue value : eFormValueDao.findByFormDataId(fdid)) {
            values.put(value.getVarName(), value.getVarValue());
        }
        return values;
    }

    /**
     * Adds {@code ""} for each field whose template default is nonblank but whose control in the
     * saved form is empty, and that has no stored value: the instance cleared it. A control missing
     * from the saved form (the template has changed since) is not reported as a clear.
     */
    private static void addClearedDefaults(String templateHtml, String instanceHtml, Map<String, String> values) {
        Document template = parse(templateHtml);
        Document instance = parse(instanceHtml);
        if (template == null || instance == null) {
            return;
        }
        Set<String> names = new LinkedHashSet<>();
        for (Element control : template.select("input[name], textarea[name], select[name]")) {
            names.add(control.attr("name"));
        }
        for (String name : names) {
            if (StringUtils.isBlank(name) || values.containsKey(name)) {
                continue;
            }
            String type = settableType(template, name);
            if (type == null || !type.equals(settableType(instance, name))) {
                continue;
            }
            if (hasValue(template, name, type) && !hasValue(instance, name, type)) {
                values.put(name, "");
            }
        }
    }

    /** Whether the named control shows a value, read the way {@link EForm#setValues} writes it. */
    private static boolean hasValue(Document document, String name, String type) {
        var controls = namedControls(document, name);
        return switch (type) {
            case "checkbox" -> controls.stream().anyMatch(control -> control.hasAttr("checked"));
            // A checked choice whose value is blank shows no value, like a select's placeholder: it is
            // reported as "" so a revision checks that choice again instead of the template default.
            case "radio" -> controls.stream()
                    .anyMatch(control -> control.hasAttr("checked") && StringUtils.isNotBlank(control.attr("value")));
            // A selected placeholder (value="") shows no value; it is what a cleared select keeps.
            case "select" -> controls.first().select("option[selected]").stream()
                    .anyMatch(option -> StringUtils.isNotBlank(optionValue(option)));
            case "textarea" -> StringUtils.isNotBlank(controls.first().wholeText());
            default -> StringUtils.isNotBlank(controls.first().attr("value"));
        };
    }

    /** The one settable control type every element with this name has, or null if there is none. */
    private static String settableType(Document document, String name) {
        String found = null;
        for (Element control : namedControls(document, name)) {
            String type = switch (control.normalName()) {
                case "textarea", "select" -> control.normalName();
                // A missing type is a text input, as EForm reads it; a present one is used verbatim.
                case "input" -> control.hasAttr("type") ? control.attr("type") : "text";
                default -> null;
            };
            if (type == null || !(type.equals("textarea") || type.equals("select") || SETTABLE_INPUT_TYPES.contains(type))
                    || (found != null && !found.equals(type))) {
                return null;
            }
            found = type;
        }
        return found;
    }

    private static org.jsoup.select.Elements namedControls(Document document, String name) {
        return document.select("[name=\"" + TokenQueue.escapeCssIdentifier(name) + "\"]");
    }

    /** The value a browser submits for an option, as {@link EForm} matches it. */
    private static String optionValue(Element option) {
        return option.hasAttr("value") ? option.attr("value") : option.text();
    }

    private static Document parse(String html) {
        return StringUtils.isBlank(html) ? null : ConvertToEdoc.parseDocument(html);
    }

    /**
     * Field names must fit {@code eform_values.var_name}: a longer one fails the save on a strict
     * server, and is cut on others, so its value could not be read back by name or sent back.
     */
    private static void requireValidFieldNames(Map<String, String> values) {
        if (values == null) {
            return;
        }
        for (String name : values.keySet()) {
            if (StringUtils.isBlank(name) || name.length() > FIELD_NAME_MAX_LENGTH) {
                throw new EFormInstanceException(EFormInstanceException.Reason.INVALID_FIELD_NAME);
            }
        }
    }

    /** The subject must fit {@code eform_data.subject}, for the same reason. */
    private static void requireValidSubject(String subject) {
        if (subject != null && subject.length() > SUBJECT_MAX_LENGTH) {
            throw new EFormInstanceException(EFormInstanceException.Reason.INVALID_SUBJECT);
        }
    }

    /**
     * A refusal for one existing instance (its patient, patient record or role), as opposed to the
     * caller lacking the {@code _eform} right altogether. Callers answer it as they answer
     * {@link EFormInstanceException.Reason#NOT_FOUND}, so an fdid the caller cannot see is
     * indistinguishable from one that does not exist.
     */
    public static class InstanceAccessDeniedException extends SecurityException {

        private static final long serialVersionUID = 1L;

        public InstanceAccessDeniedException(String message) {
            super(message);
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
            /**
             * The instance is current, but its template shows only the latest form and the patient
             * has a newer one, such as a clinician's re-save in the browser. Revising this row would
             * hide that newer form from the chart.
             */
            SUPERSEDED("A newer form of this template exists for the patient; read and revise that one"),
            /** The template does not exist, or is retired and cannot start new instances. */
            TEMPLATE_UNAVAILABLE("EForm template not found or not active"),
            /** The patient does not exist, or the caller may not write to the patient's chart. */
            PATIENT_NOT_FOUND("Patient not found"),
            /** A field name in the values was blank or longer than 30 characters. */
            INVALID_FIELD_NAME("EForm field names must not be blank or longer than 30 characters"),
            /** The subject was longer than 255 characters. */
            INVALID_SUBJECT("EForm subject must not be longer than 255 characters"),
            /** A revision named no values; it replaces every field, so they are required. */
            VALUES_REQUIRED("values is required: a revision replaces every field of the form"),
            /**
             * A value names no control on the template, a control the API cannot fill (such as
             * {@code type="number"}), or a radio or select choice that does not exist.
             */
            UNSUPPORTED_FIELD("EForm field is not on the template or cannot be set through the API");

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
