/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.casemgmt.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.carlos_emr.carlos.model.BaseObject;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteLinkDAO;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;


@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "casemgmt_note")
@jakarta.persistence.Access(jakarta.persistence.AccessType.PROPERTY)
@jakarta.persistence.SqlResultSetMapping(name = "CaseManagementNoteNativeMapping",
        entities = @jakarta.persistence.EntityResult(entityClass = CaseManagementNote.class, fields = {
                @jakarta.persistence.FieldResult(name = "id", column = "id"),
                @jakarta.persistence.FieldResult(name = "update_date", column = "update_date"),
                @jakarta.persistence.FieldResult(name = "create_date", column = "create_date"),
                @jakarta.persistence.FieldResult(name = "observation_date", column = "observation_date"),
                @jakarta.persistence.FieldResult(name = "demographic_no", column = "demographic_no"),
                @jakarta.persistence.FieldResult(name = "note", column = "note"),
                @jakarta.persistence.FieldResult(name = "signed", column = "signed"),
                @jakarta.persistence.FieldResult(name = "includeissue", column = "includeissue"),
                @jakarta.persistence.FieldResult(name = "providerNo", column = "providerNo"),
                @jakarta.persistence.FieldResult(name = "signing_provider_no", column = "signing_provider_no"),
                @jakarta.persistence.FieldResult(name = "encounter_type", column = "encounter_type"),
                @jakarta.persistence.FieldResult(name = "billing_code", column = "billing_code"),
                @jakarta.persistence.FieldResult(name = "program_no", column = "program_no"),
                @jakarta.persistence.FieldResult(name = "reporter_caisi_role", column = "reporter_caisi_role"),
                @jakarta.persistence.FieldResult(name = "reporter_program_team", column = "reporter_program_team"),
                @jakarta.persistence.FieldResult(name = "history", column = "history"),
                @jakarta.persistence.FieldResult(name = "roleName", column = "roleName"),
                @jakarta.persistence.FieldResult(name = "programName", column = "programName"),
                @jakarta.persistence.FieldResult(name = "uuid", column = "uuid"),
                @jakarta.persistence.FieldResult(name = "revision", column = "revision"),
                @jakarta.persistence.FieldResult(name = "locked", column = "locked"),
                @jakarta.persistence.FieldResult(name = "archived", column = "archived"),
                @jakarta.persistence.FieldResult(name = "position", column = "position"),
                @jakarta.persistence.FieldResult(name = "appointmentNo", column = "appointmentNo"),
                @jakarta.persistence.FieldResult(name = "hourOfEncounterTime", column = "hourOfEncounterTime"),
                @jakarta.persistence.FieldResult(name = "minuteOfEncounterTime", column = "minuteOfEncounterTime"),
                @jakarta.persistence.FieldResult(name = "hourOfEncTransportationTime", column = "hourOfEncTransportationTime"),
                @jakarta.persistence.FieldResult(name = "minuteOfEncTransportationTime", column = "minuteOfEncTransportationTime")
        }))
@jakarta.persistence.NamedNativeQueries({
        @jakarta.persistence.NamedNativeQuery(name = "mostRecentTime", resultSetMapping = "CaseManagementNoteNativeMapping", query = """
                select cmn.note_id as id, cmn.update_date as update_date, cmn.observation_date as observation_date,
                cmn.demographic_no as demographic_no, cmn.provider_no as providerNo, cmn.note as note, cmn.signed as signed,
                cmn.include_issue_innote as includeissue, cmn.signing_provider_no as signing_provider_no,
                cmn.encounter_type as encounter_type, cmn.billing_code as billing_code, cmn.program_no as program_no,
                cmn.reporter_caisi_role as reporter_caisi_role, cmn.reporter_program_team as reporter_program_team,
                cmn.history as history, cmn.uuid as uuid, cmn.locked as locked, cmn.archived as archived, cmn.position as position,
                cmn.appointmentNo as appointmentNo, cmn.hourOfEncounterTime as hourOfEncounterTime,
                cmn.minuteOfEncounterTime as minuteOfEncounterTime, cmn.hourOfEncTransportationTime as hourOfEncTransportationTime,
                cmn.minuteOfEncTransportationTime as minuteOfEncTransportationTime,
                (select r.role_name from secRole r where r.role_no = cmn.reporter_caisi_role) as roleName,
                (select p.name from program p where p.id = cmn.program_no) as programName,
                (select count(cmn2.uuid) from casemgmt_note cmn2 where cmn2.uuid = cmn.uuid) as revision,
                (select min(cmn2.update_date) from casemgmt_note cmn2 where cmn2.uuid = cmn.uuid) as create_date
                from casemgmt_note cmn
                join (select max(note_id) as note_id from casemgmt_note
                where demographic_no = :demographicNo and observation_date >= :staleDate group by uuid) recent on recent.note_id = cmn.note_id
                order by cmn.observation_date asc
                """),
        @jakarta.persistence.NamedNativeQuery(name = "mostRecentDateRange", resultSetMapping = "CaseManagementNoteNativeMapping", query = """
                select cmn.note_id as id, cmn.update_date as update_date, cmn.observation_date as observation_date,
                cmn.demographic_no as demographic_no, cmn.provider_no as providerNo, cmn.note as note, cmn.signed as signed,
                cmn.include_issue_innote as includeissue, cmn.signing_provider_no as signing_provider_no,
                cmn.encounter_type as encounter_type, cmn.billing_code as billing_code, cmn.program_no as program_no,
                cmn.reporter_caisi_role as reporter_caisi_role, cmn.reporter_program_team as reporter_program_team,
                cmn.history as history, cmn.uuid as uuid, cmn.locked as locked, cmn.archived as archived, cmn.position as position,
                cmn.appointmentNo as appointmentNo, cmn.hourOfEncounterTime as hourOfEncounterTime,
                cmn.minuteOfEncounterTime as minuteOfEncounterTime, cmn.hourOfEncTransportationTime as hourOfEncTransportationTime,
                cmn.minuteOfEncTransportationTime as minuteOfEncTransportationTime,
                (select r.role_name from secRole r where r.role_no = cmn.reporter_caisi_role) as roleName,
                (select p.name from program p where p.id = cmn.program_no) as programName,
                (select count(cmn2.uuid) from casemgmt_note cmn2 where cmn2.uuid = cmn.uuid) as revision,
                (select min(cmn2.update_date) from casemgmt_note cmn2 where cmn2.uuid = cmn.uuid) as create_date
                from casemgmt_note cmn
                join (select max(note_id) as note_id from casemgmt_note
                where demographic_no = :demographicNo and observation_date >= :startDate and observation_date <= :endDate group by uuid) recent on recent.note_id = cmn.note_id
                order by cmn.observation_date asc
                """),
        @jakarta.persistence.NamedNativeQuery(name = "mostRecentLimit", resultSetMapping = "CaseManagementNoteNativeMapping", query = """
                select cmn.note_id as id, cmn.update_date as update_date, cmn.observation_date as observation_date,
                cmn.demographic_no as demographic_no, cmn.provider_no as providerNo, cmn.note as note, cmn.signed as signed,
                cmn.include_issue_innote as includeissue, cmn.signing_provider_no as signing_provider_no,
                cmn.encounter_type as encounter_type, cmn.billing_code as billing_code, cmn.program_no as program_no,
                cmn.reporter_caisi_role as reporter_caisi_role, cmn.reporter_program_team as reporter_program_team,
                cmn.history as history, cmn.uuid as uuid, cmn.locked as locked, cmn.archived as archived, cmn.position as position,
                cmn.appointmentNo as appointmentNo, cmn.hourOfEncounterTime as hourOfEncounterTime,
                cmn.minuteOfEncounterTime as minuteOfEncounterTime, cmn.hourOfEncTransportationTime as hourOfEncTransportationTime,
                cmn.minuteOfEncTransportationTime as minuteOfEncTransportationTime,
                (select r.role_name from secRole r where r.role_no = cmn.reporter_caisi_role) as roleName,
                (select p.name from program p where p.id = cmn.program_no) as programName,
                (select count(cmn2.uuid) from casemgmt_note cmn2 where cmn2.uuid = cmn.uuid) as revision,
                (select min(cmn2.update_date) from casemgmt_note cmn2 where cmn2.uuid = cmn.uuid) as create_date
                from casemgmt_note cmn
                join (select max(note_id) as note_id from casemgmt_note where demographic_no = :demographicNo group by uuid) recent on recent.note_id = cmn.note_id
                order by cmn.observation_date desc
                """)
})
public class CaseManagementNote extends BaseObject {

    private Long id;
    private Date update_date;
    private Date create_date;
    private Date observation_date;
    private String demographic_no;
    private String note;
    private boolean signed = false;
    private boolean includeissue = true;
    private String providerNo;
    private String signing_provider_no;
    private String encounter_type = "";
    private String billing_code = "";
    private String program_no;
    private String reporter_caisi_role;
    private String reporter_program_team;
    private String history;
    private Provider provider;
    private Set<CaseManagementIssue> issues = new HashSet<CaseManagementIssue>();
    private Set<CaseManagementNoteExt> extend = new HashSet<>();
    private List<Provider> editors = new ArrayList<Provider>();
    private String roleName;
    private String programName;
    private String uuid;
    private String revision;

    private boolean locked;
    private boolean archived;

    private boolean remote = false;
    private String facilityName = "None Specified";

    private int hashCode = Integer.MIN_VALUE;
    private int position = 0;

    private int appointmentNo;
    private Integer hourOfEncounterTime;
    private Integer minuteOfEncounterTime;
    private Integer hourOfEncTransportationTime;
    private Integer minuteOfEncTransportationTime;

    private transient CaseManagementNoteLinkDAO caseManagementNoteLinkDao;

    private CaseManagementNoteLink cmnLink = null;
    private boolean cmnLinkRetrieved = false;

    @jakarta.persistence.Transient
    public Map<String, Object> getMap() {
        HashMap<String, Object> map = new HashMap<String, Object>();

        map.put("id", id);
        map.put("update_date", update_date);
        map.put("create_date", create_date);
        map.put("observation_date", observation_date);
        map.put("demographic_no", demographic_no);
        map.put("note", note);
        map.put("signed", signed);
        map.put("includeissue", includeissue);
        map.put("provider_no", providerNo);
        map.put("signing_provider_no", signing_provider_no);
        map.put("encounter_type", encounter_type);
        map.put("billing_code", billing_code);
        map.put("program_no", program_no);
        map.put("reporter_caisi_role", reporter_caisi_role);
        map.put("reporter_caisi_team", reporter_program_team);
        map.put("history", history);
        map.put("providers", provider);
        map.put("editors", editors);
        map.put("role_name", roleName);
        map.put("program_name", programName);
        map.put("uuid", uuid);
        map.put("revision", revision);
        map.put("locked", locked);
        map.put("archived", archived);
        map.put("remote", remote);
        map.put("facility_name", facilityName);
        map.put("appointment_no", appointmentNo);

        return map;
    }

    @Override
    public boolean equals(Object obj) {
        if (null == obj) return false;
        if (!(obj instanceof CaseManagementNote)) return false;
        else {
            CaseManagementNote mObj = (CaseManagementNote) obj;
            if (null == this.getId() || null == mObj.getId()) return false;
            else return (this.getId().equals(mObj.getId()));
        }
    }

    @Override
    public int hashCode() {
        if (Integer.MIN_VALUE == this.hashCode) {
            if (null == this.getId()) return super.hashCode();
            else {
                String hashStr = this.getClass().getName() + ":" + this.getId().hashCode();
                this.hashCode = hashStr.hashCode();
            }
        }
        return this.hashCode;
    }

    public CaseManagementNote() {
        update_date = new Date();
    }
    @jakarta.persistence.Transient

    public String getAuditString() {
        StringBuilder auditStr = new StringBuilder(getNote());
        Iterator<CaseManagementIssue> iter = issues.iterator();
        auditStr.append("\nIssues\n");
        int idx = 0;
        while (iter.hasNext()) {
            auditStr.append(iter.next().getIssue().getDescription() + "\n");
            ++idx;
        }
        if (idx == 0) {
            auditStr.append("None");
        }
        return auditStr.toString();
    }
    @jakarta.persistence.Column(name = "billing_code")

    public String getBilling_code() {
        return billing_code;
    }

    public void setBilling_code(String billing_code) {
        this.billing_code = billing_code;
    }
    @jakarta.persistence.Column(name = "demographic_no")

    public String getDemographic_no() {
        return demographic_no;
    }

    public void setDemographic_no(String demographic_no) {
        this.demographic_no = demographic_no;
    }
    @jakarta.persistence.Column(name = "encounter_type")

    public String getEncounter_type() {
        return encounter_type;
    }

    public void setEncounter_type(String encounter_type) {
        this.encounter_type = encounter_type;
    }
    @jakarta.persistence.Id

    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)

    @jakarta.persistence.Column(name = "note_id")

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    @jakarta.persistence.Column(name = "note")

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
    @jakarta.persistence.Column(name = "signed")

    public boolean isSigned() {
        return signed;
    }

    public void setSigned(boolean signed) {
        this.signed = signed;
    }
    @jakarta.persistence.Column(name = "signing_provider_no")

    public String getSigning_provider_no() {
        return signing_provider_no;
    }

    public void setSigning_provider_no(String signing_provider_no) {
        this.signing_provider_no = signing_provider_no;
    }
    @jakarta.persistence.Temporal(jakarta.persistence.TemporalType.TIMESTAMP)
    @jakarta.persistence.Column(name = "update_date")

    public Date getUpdate_date() {
        return update_date;
    }

    public void setUpdate_date(Date update_date) {
        this.update_date = update_date;
    }
    @org.hibernate.annotations.Formula("(select min(cmn.update_date) from casemgmt_note cmn where cmn.uuid = uuid)")

    public Date getCreate_date() {
        return create_date;
    }

    public void setCreate_date(Date create_date) {
        this.create_date = create_date;
    }
    @jakarta.persistence.Temporal(jakarta.persistence.TemporalType.TIMESTAMP)
    @jakarta.persistence.Column(name = "observation_date")

    public Date getObservation_date() {
        return this.observation_date;
    }

    public void setObservation_date(Date observation_date) {
        this.observation_date = observation_date;
    }
    @jakarta.persistence.Column(name = "provider_no")

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String provider_no) {
        this.providerNo = provider_no;
    }

    // nys
    @jakarta.persistence.Column(name = "program_no")
    public String getProgram_no() {
        return program_no;
    }

    public void setProgram_no(String program_no) {
        this.program_no = program_no;
    }
    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.EAGER)

    @jakarta.persistence.JoinColumn(name = "provider_no", insertable = false, updatable = false)

    @org.hibernate.annotations.NotFound(action = org.hibernate.annotations.NotFoundAction.IGNORE)

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    /**
     * deprecated too inefficient and too many dependencies use CaseManagementIssueNotesDao
     */
    @jakarta.persistence.ManyToMany(fetch = jakarta.persistence.FetchType.EAGER)
    @jakarta.persistence.JoinTable(name = "casemgmt_issue_notes", joinColumns = @jakarta.persistence.JoinColumn(name = "note_id"), inverseJoinColumns = @jakarta.persistence.JoinColumn(name = "id"))
    public Set<CaseManagementIssue> getIssues() {
        return issues;
    }

    /**
     * deprecated too inefficient and too many dependencies use CaseManagementIssueNotesDao
     */
    public void setIssues(Set issues) {
        this.issues = issues;
    }
    @jakarta.persistence.OneToMany(fetch = jakarta.persistence.FetchType.EAGER, targetEntity = CaseManagementNoteExt.class)

    @jakarta.persistence.JoinColumn(name = "note_id", updatable = false)

    public Set<CaseManagementNoteExt> getExtend() {
        return extend;
    }

    public void setExtend(Set<CaseManagementNoteExt> extend) {
        this.extend = extend;
    }
    @jakarta.persistence.Transient

    public List<Provider> getEditors() {
        return editors;
    }

    public void setEditors(List editors) {
        this.editors = editors;
    }
    @jakarta.persistence.Column(name = "include_issue_innote")

    public boolean isIncludeissue() {
        return includeissue;
    }

    public void setIncludeissue(boolean includeissue) {
        this.includeissue = includeissue;
    }
    @jakarta.persistence.Column(name = "reporter_caisi_role")

    public String getReporter_caisi_role() {
        return reporter_caisi_role;
    }

    public void setReporter_caisi_role(String reporter_caisi_role) {
        this.reporter_caisi_role = reporter_caisi_role;
    }
    @jakarta.persistence.Column(name = "reporter_program_team")

    public String getReporter_program_team() {
        return reporter_program_team;
    }

    public void setReporter_program_team(String reporter_program_team) {
        this.reporter_program_team = reporter_program_team;
    }
    @jakarta.persistence.Column(name = "history")

    public String getHistory() {
        return history;
    }

    public void setHistory(String history) {
        this.history = history;
    }
    @org.hibernate.annotations.Formula("(select p.name from program p where p.id = program_no)")

    public String getProgramName() {
        return programName;
    }
    @org.hibernate.annotations.Formula("(select r.role_name from secRole r where r.role_no = reporter_caisi_role)")

    public String getRoleName() {
        return roleName;
    }

    public void setProgramName(String programName) {
        this.programName = programName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
    @jakarta.persistence.Column(name = "uuid")

    public String getUuid() {
        return this.uuid;
    }

    public void setRevision(String rev) {
        this.revision = rev;
    }
    @org.hibernate.annotations.Formula("(select count(cmn.uuid) from casemgmt_note cmn where cmn.uuid = uuid)")

    public String getRevision() {
        return this.revision;
    }
    @jakarta.persistence.Transient

    public String getProviderName() {
        if (getProvider() == null) {
            return "DELETED";
        }
        return getProvider().getFormattedName();
    }
    @jakarta.persistence.Transient

    public String getProviderNameFirstLast() {
        if (getProvider() == null) {
            return "DELETED";
        }
        return getProvider().getFullName();
    }

    public static Comparator<CaseManagementNote> getProviderComparator() {
        return new Comparator<CaseManagementNote>() {
            public int compare(CaseManagementNote note1, CaseManagementNote note2) {
                if (note1 == null || note2 == null) {
                    return 0;
                }

                return note1.getProviderName().compareTo(note2.getProviderName());
            }
        };

    }

    public static Comparator<CaseManagementNote> getProgramComparator() {
        return new Comparator<CaseManagementNote>() {
            public int compare(CaseManagementNote note1, CaseManagementNote note2) {
                if (note1 == null || note1.getProgramName() == null || note2 == null || note2.getProgramName() == null) {
                    return 0;
                }
                return note1.getProgramName().compareTo(note2.getProgramName());
            }
        };

    }

    public static Comparator<CaseManagementNote> getRoleComparator() {
        return new Comparator<CaseManagementNote>() {
            public int compare(CaseManagementNote note1, CaseManagementNote note2) {
                if (note1 == null || note2 == null) {
                    return 0;
                }
                return note1.getRoleName().compareTo(note2.getRoleName());
            }
        };

    }

    public static Comparator<CaseManagementNote> noteObservationDateComparator = new Comparator<CaseManagementNote>() {

        public int compare(CaseManagementNote note1, CaseManagementNote note2) {
            if (note1 == null || note2 == null) {
                return 0;
            }

            return note2.getObservation_date().compareTo(note1.getObservation_date());
        }
    };

    public static Comparator<CaseManagementNote> getPositionComparator() {
        return new Comparator<CaseManagementNote>() {
            public int compare(CaseManagementNote note1, CaseManagementNote note2) {
                if (note1 == null || note2 == null) {
                    return 0;
                }

                return Integer.valueOf(note1.getPosition()).compareTo(Integer.valueOf(note2.getPosition()));
            }
        };

    }
    @jakarta.persistence.Transient

    public boolean getHasHistory() {
        if (getHistory() != null) {
            if (getHistory().indexOf("----------------History Record----------------") != -1) {
                return true;
            }
        }
        return false;
    }
    @jakarta.persistence.Column(name = "locked")

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }
    @jakarta.persistence.Column(name = "archived")

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }
    @jakarta.persistence.Transient

    public String getStatus() {
        String status;
        if (isSigned()) {
            status = "Signed";
        } else {
            status = "Unsigned";
        }
        if (locked) {
            status += "/Locked";
        }
        return status;
    }
    @jakarta.persistence.Column(name = "position")

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
    @jakarta.persistence.Transient

    public boolean isRemote() {
        return remote;
    }

    public void setRemote(boolean isRemote) {
        this.remote = isRemote;
    }
    @jakarta.persistence.Transient

    public String getFacilityName() {
        return facilityName;
    }

    public void setFacilityName(String facilityName) {
        this.facilityName = facilityName;
    }
    @jakarta.persistence.Transient

    public boolean isDocumentNote() {
        return isLinkTo(CaseManagementNoteLink.DOCUMENT);
    }
    @jakarta.persistence.Transient

    public boolean isEmailNote() {
        return isLinkTo(CaseManagementNoteLink.EMAIL);
    }
    @jakarta.persistence.Transient

    public boolean isRxAnnotation() {
        return isLinkTo(CaseManagementNoteLink.DRUGS);
    }
    @jakarta.persistence.Transient

    public boolean isEformData() {
        return isLinkTo(CaseManagementNoteLink.EFORMDATA);
    }

    private CaseManagementNoteLinkDAO lookupNoteLinkDao() {
        if (caseManagementNoteLinkDao == null) {
            caseManagementNoteLinkDao = (CaseManagementNoteLinkDAO) SpringUtils.getBean(CaseManagementNoteLinkDAO.class);
        }
        return caseManagementNoteLinkDao;
    }

    private boolean isLinkTo(Integer tableName) {
        if (!cmnLinkRetrieved) {
            cmnLink = lookupNoteLinkDao().getLastLinkByNote(this.id);
            cmnLinkRetrieved = true;
        }

        if (cmnLink != null && cmnLink.getTableName().equals(tableName)) {
            return true;
        }
        return false;
    }

    public RxPrescriptionData.Prescription getRxFromAnnotation(CaseManagementNoteLink cmnl) {
        if (this.isRxAnnotation()) {
            String drugId = cmnl.getTableId().toString();

            //get drug id from cmn_link table
            RxPrescriptionData rxData = new RxPrescriptionData();
            // create Prescription
            RxPrescriptionData.Prescription rx = rxData.getLatestPrescriptionScriptByPatientDrugId(Integer.parseInt(this.getDemographic_no()), drugId);
            return rx;
        }

        return null;
    }

    @jakarta.persistence.Column(name = "appointmentNo")


    public int getAppointmentNo() {
        return appointmentNo;
    }

    public void setAppointmentNo(int appointmentNo) {
        this.appointmentNo = appointmentNo;
    }
    @jakarta.persistence.Column(name = "hourOfEncounterTime")

    public Integer getHourOfEncounterTime() {
        return hourOfEncounterTime;
    }

    public void setHourOfEncounterTime(Integer hourOfEncounterTime) {
        this.hourOfEncounterTime = hourOfEncounterTime;
    }
    @jakarta.persistence.Column(name = "minuteOfEncounterTime")

    public Integer getMinuteOfEncounterTime() {
        return minuteOfEncounterTime;
    }

    public void setMinuteOfEncounterTime(Integer minuteOfEncounterTime) {
        this.minuteOfEncounterTime = minuteOfEncounterTime;
    }
    @jakarta.persistence.Column(name = "hourOfEncTransportationTime")

    public Integer getHourOfEncTransportationTime() {
        return hourOfEncTransportationTime;
    }

    public void setHourOfEncTransportationTime(Integer hourOfEncTransportationTime) {
        this.hourOfEncTransportationTime = hourOfEncTransportationTime;
    }
    @jakarta.persistence.Column(name = "minuteOfEncTransportationTime")

    public Integer getMinuteOfEncTransportationTime() {
        return minuteOfEncTransportationTime;
    }

    public void setMinuteOfEncTransportationTime(Integer minuteOfEncTransportationTime) {
        this.minuteOfEncTransportationTime = minuteOfEncTransportationTime;
    }


}
