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
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.PMmodule.service.AdmissionManager;
import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.web.formbeans.CaseManagementEntryFormBean;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.GroupNoteDao;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.encounter.pageUtil.EncounterPatientContext;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.springframework.web.util.WebUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Native group selection: a read-only popup and a separate POST/redirect/GET save.
 *
 * @since 2026-10-25
 */
public class GroupNoteSelect2Action extends ActionSupport {
    private final SecurityInfoManager security = SpringUtils.getBean(SecurityInfoManager.class);
    private final CaseManagementManager notes = SpringUtils.getBean(CaseManagementManager.class);
    private final AdmissionManager admissions = SpringUtils.getBean(AdmissionManager.class);
    private final DemographicDao demographics = SpringUtils.getBean(DemographicDao.class);
    private final GroupNoteDao links = SpringUtils.getBean(GroupNoteDao.class);
    private String demographicNo;
    private String programId;
    private String chartContext;

    public record Member(String id, String name, boolean anonymous) { }

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        if (!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())) {
            return refuse(response, 405, "GET, HEAD");
        }
        Selection selection = authorize(request, response);
        if (selection == null) return NONE;
        if ("0".equals(selection.noteId())) {
            request.setAttribute("groupNoteUnsaved", true);
            return SUCCESS;
        }
        List<Member> members = members(selection);
        List<Member> current = new ArrayList<>();
        for (var link : links.findLinksByNoteId(Integer.parseInt(selection.noteId()))) {
            if (!link.isActive() || !canRead(selection.login(), link.getDemographicNo())) continue;
            Demographic client = demographics.getDemographic(String.valueOf(link.getDemographicNo()));
            if (client != null) current.add(new Member(String.valueOf(link.getDemographicNo()),
                    client.getFormattedName(), link.isAnonymous()));
        }
        request.setAttribute("groupNoteMembers", members);
        request.setAttribute("groupNoteCurrentMembers", current);
        request.setAttribute("groupNotePatient", demographicNo);
        request.setAttribute("groupNoteProgram", programId);
        request.setAttribute("groupNoteId", selection.noteId());
        request.setAttribute("groupNoteContext", chartContext);
        return SUCCESS;
    }

    public String save() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        if (!"POST".equals(request.getMethod())) return refuse(response, 405, "POST");
        Selection selection = authorize(request, response);
        if (selection == null) return NONE;
        String count;
        try { count = number(request, "num_anonymous", true); }
        catch (IllegalArgumentException invalid) { return refuse(response, 400, null); }
        // The native field is three digits; never turn a malformed request into unbounded patient creation.
        if (Integer.parseInt(count) > 999) return refuse(response, 400, null);
        Set<String> available = new LinkedHashSet<>();
        for (Member member : members(selection)) available.add(member.id());
        Set<String> selected = new LinkedHashSet<>();
        String[] posted = request.getParameterValues("group_client_id");
        if (posted != null) for (String value : posted) {
            String patient;
            try { patient = positive(value); }
            catch (IllegalArgumentException invalid) { return refuse(response, 400, null); }
            if (!available.contains(patient)) return refuse(response, 403, null);
            selected.add(patient);
        }
        // Replacing or clearing a group also retires its old recipients. Authorize
        // that complete set before GroupNoteAction changes its first link/patient.
        for (var link : links.findLinksByNoteId(Integer.parseInt(selection.noteId()))) {
            if (!canWriteRecipient(selection.login(), link.getDemographicNo())) return refuse(response, 403, null);
            if (link.isAnonymous()) for (Admission admission : admissions.getAdmissions(link.getDemographicNo())) {
                if (!programId.equals(String.valueOf(admission.getProgramId()))) return refuse(response, 403, null);
            }
        }
        CaseManagementEntryFormBean detached = new CaseManagementEntryFormBean();
        detached.setNoteId(selection.noteId());
        detached.setGroupNote(true);
        detached.setGroupNoteClientIds(selected.toArray(String[]::new));
        detached.setGroupNoteTotalAnonymous(Integer.parseInt(count));
        synchronized (WebUtils.getSessionMutex(request.getSession())) {
            if (request.getSession().getAttribute(selection.formKey()) != selection.form()
                    || !selection.noteId().equals(selection.form().getNoteId())) {
                return refuse(response, 409, null);
            }
        }
        saveGroupNote(selection.login(), detached, programId);
        // Do not publish over another editor which became current while the persistence call ran.
        synchronized (WebUtils.getSessionMutex(request.getSession())) {
            if (request.getSession().getAttribute(selection.formKey()) == selection.form()
                    && selection.noteId().equals(selection.form().getNoteId())) {
                selection.form().setGroupNote(true);
                selection.form().setGroupNoteClientIds(detached.getGroupNoteClientIds());
                selection.form().setGroupNoteTotalAnonymous(detached.getGroupNoteTotalAnonymous());
            }
        }
        return SUCCESS;
    }

    protected void saveGroupNote(LoggedInInfo login, CaseManagementEntryFormBean form, String program) {
        // Existing DAO/service REQUIRED transactions join this boundary: a failed
        // replacement must not leave the old recipients partially retired.
        new TransactionTemplate(SpringUtils.getBean(PlatformTransactionManager.class))
                .executeWithoutResult(status -> {
                    if (!links.lockNoteForGroupUpdate(Integer.valueOf(form.getNoteId()))) {
                        throw new SecurityException("Group source note is unavailable");
                    }
                    // Repeat recipient checks at the actual persistence boundary, then
                    // pass exactly these links/admissions to the legacy implementation.
                    Set<Integer> enrolled = new LinkedHashSet<>();
                    for (Admission admission : admissions.getCurrentAdmissionsByProgramId(program)) {
                        enrolled.add(admission.getClientId());
                    }
                    if (form.getGroupNoteClientIds() != null) for (String patient : form.getGroupNoteClientIds()) {
                        if (!enrolled.contains(Integer.valueOf(patient)) || !canWriteRecipient(login, Integer.parseInt(patient))) {
                            throw new SecurityException("Group recipient is not writable");
                        }
                    }
                    var actualMembers = GroupNoteAction.membersForSave(form.getNoteId());
                    for (var member : actualMembers) {
                        if (!canWriteRecipient(login, member.link().getDemographicNo())) {
                            throw new SecurityException("Existing group recipient is not writable");
                        }
                        if (member.link().isAnonymous()) for (Admission admission : member.admissions()) {
                            if (!program.equals(String.valueOf(admission.getProgramId()))) {
                                throw new SecurityException("Anonymous group recipient belongs to another program");
                            }
                        }
                    }
                    GroupNoteAction.saveGroupNote(login, form, program, actualMembers);
                });
    }

    private Selection authorize(HttpServletRequest request, HttpServletResponse response) {
        String noteId;
        try {
            demographicNo = EncounterPatientContext.requireDemographicNo(request);
            noteId = number(request, "noteId", !"POST".equals(request.getMethod()));
        } catch (IllegalArgumentException invalid) { refuse(response, 400, null); return null; }
        // Both values are derived from the signed window/per-patient capture, never posted program hints.
        chartContext = ChartProgramContext.token(request);
        programId = ChartProgramContext.resolve(request);
        if (programId == null) { refuse(response, 400, null); return null; }
        LoggedInInfo login = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (login == null || login.getCurrentFacility() == null || !login.getCurrentFacility().isEnableGroupNotes()
                || !security.hasPrivilege(login, "_eChart", "w", demographicNo)
                || !security.hasPrivilege(login, "_casemgmt.notes", "w", demographicNo)
                || !security.isAllowedAccessToPatientRecord(login, Integer.parseInt(demographicNo))) {
            refuse(response, 403, null); return null;
        }
        String provider = login.getLoggedInProviderNo();
        if (notes.hasAccessRight("note-read-only", "access", provider, demographicNo, programId)
                || (!notes.isClientInProgramDomain(provider, demographicNo)
                && !notes.isClientReferredInProgramDomain(provider, demographicNo))) {
            refuse(response, 403, null); return null;
        }
        String key = ChartProgramContext.formKey(request, demographicNo);
        Object value = request.getSession().getAttribute(key);
        if (!(value instanceof CaseManagementEntryFormBean form)
                || !(noteId.equals(form.getNoteId()) || "0".equals(noteId)
                && (form.getNoteId() == null || form.getNoteId().isEmpty()))) {
            refuse(response, 409, null); return null;
        }
        if ("0".equals(noteId)) return new Selection(login, form, key, noteId);
        CaseManagementNote note = notes.getNote(noteId);
        if (note == null || !demographicNo.equals(note.getDemographic_no())
                || notes.filterNotes(login, provider, List.of(note), programId).isEmpty()) {
            refuse(response, 403, null); return null;
        }
        return new Selection(login, form, key, noteId);
    }

    private List<Member> members(Selection selection) {
        List<Member> result = new ArrayList<>();
        Set<Integer> seen = new LinkedHashSet<>();
        for (Admission admission : admissions.getCurrentAdmissionsByProgramId(programId)) {
            Integer patient = admission.getClientId();
            if (patient == null || demographicNo.equals(patient.toString()) || !seen.add(patient)
                    || !canRead(selection.login(), patient) || !canWriteRecipient(selection.login(), patient)) continue;
            Demographic client = demographics.getDemographic(patient.toString());
            if (client != null) result.add(new Member(patient.toString(), client.getFormattedName(), false));
        }
        return result;
    }

    private boolean canRead(LoggedInInfo login, int patient) {
        return security.isAllowedAccessToPatientRecord(login, patient)
                && security.hasPrivilege(login, "_demographic", "r", String.valueOf(patient));
    }

    private boolean canWriteRecipient(LoggedInInfo login, int patient) {
        String target = Integer.toString(patient);
        String provider = login.getLoggedInProviderNo();
        return security.isAllowedAccessToPatientRecord(login, patient)
                && security.hasPrivilege(login, "_eChart", "w", target)
                && security.hasPrivilege(login, "_casemgmt.notes", "w", target)
                && !notes.hasAccessRight("note-read-only", "access", provider, target, programId)
                && (notes.isClientInProgramDomain(provider, target) || notes.isClientReferredInProgramDomain(provider, target));
    }

    private static String positive(String value) {
        if (value == null || !value.matches("[0-9]{1,10}")) throw new IllegalArgumentException();
        int parsed = Integer.parseInt(value);
        if (parsed <= 0) throw new IllegalArgumentException();
        return Integer.toString(parsed);
    }

    private static String number(HttpServletRequest request, String name, boolean zero) {
        String[] values = request.getParameterValues(name);
        if (values == null || values.length == 0) throw new IllegalArgumentException();
        String result = null;
        for (String value : values) {
            String next = zero && value != null && value.matches("0{1,10}") ? "0" : positive(value);
            if (result != null && !result.equals(next)) throw new IllegalArgumentException();
            result = next;
        }
        return result;
    }

    private static String refuse(HttpServletResponse response, int status, String allow) {
        response.setStatus(status);
        if (allow != null) response.setHeader("Allow", allow);
        return NONE;
    }

    private record Selection(LoggedInInfo login, CaseManagementEntryFormBean form, String formKey, String noteId) { }
    public String getDemographicNo() { return demographicNo; }
    public String getProgramId() { return programId; }
    public String getChartContext() { return chartContext; }
}
