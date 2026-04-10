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

package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.model.DemographicExt.DemographicProperty;
import io.github.carlos_emr.carlos.commn.model.enumerator.ConsultationRequestExtKey;
import io.github.carlos_emr.carlos.fax.core.FaxRecipient;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.DemographicContactCreator;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.util.StringUtils;
import org.owasp.encoder.Encode;

import java.util.*;

/**
 * Utility bean that aggregates patient, provider, specialist, and consultation data
 * for the Consultation Request form ({@code ConsultationFormRequest.jsp}).
 *
 * <p>Instantiated by the JSP scriptlet and populated through a series of {@code est*()} methods
 * that query DAOs to fill patient demographics, team lists, specialist details,
 * and existing consultation data. The populated fields are then read directly by the JSP
 * and by {@link EctViewRequest2Action#fillFormValues} to transfer values into the Struts form bean.</p>
 *
 * <p>This class uses public fields (not getters/setters) for most properties because the JSP
 * accesses them as scriptlet expressions ({@code consultUtil.fieldName}). This is a legacy
 * pattern inherited from the original OSCAR codebase.</p>
 *
 * @since 2003-09-18
 */
public class EctConsultationFormRequestUtil {

    public String patientName;
    public String patientFirstName;
    public String patientLastName;
    public String patientAddress;
    public String patientPhone;
    public String patientWPhone;
    public String patientCellPhone;
    public String patientEmail;
    public String patientDOB;
    public String patientHealthNum;
    public String patientSex;
    public String patientAge;
    public String patientHealthCardType;
    public String patientHealthCardVersionCode;
    public String patientChartNo;
    public String familyPhysician;
    public String referalDate;
    public String service;
    public String specialist;
    public String appointmentDate;
    public String appointmentHour;
    public String appointmentMinute;
    public String appointmentPm;
    public String reasonForConsultation;
    public String clinicalInformation;
    public String concurrentProblems;
    public String currentMedications;
    public String allergies;
    public String sendTo;
    public String status;
    public String appointmentNotes;
    public String providerNo;
    public String urgency;
    public String specPhone;
    public String specFax;
    public String specAddr;
    public String specEmail;
    /**
     * Provider team names for the "Send To" dropdown. Initialized to an empty Vector
     * to prevent NullPointerException in JSP and {@link EctViewRequest2Action#fillFormValues}
     * when no {@code estTeams*()} method has been called. Populated by
     * {@link #estActiveTeams()}, {@link #estTeams()}, {@link #estTeamsBySite(String)},
     * or {@link #estTeamsByTeam(String)}.
     */
    public Vector<String> teamVec = new Vector<>();
    public String demoNo;
    public String pwb;
    public String mrp = "";
    public String siteName;
    public String signatureImg;
    public ProfessionalSpecialist professionalSpecialist;

	public String letterheadName;
	public String letterheadTitle;
	public String letterheadAddress;
	public String letterheadPhone;
	public String letterheadFax;
	public String demographicCellPhone;
	public Integer fdid;
	
	private String appointmentInstructions;
	private String appointmentInstructionsLabel;

	public boolean isEReferral = false;

	private final ConsultationRequestDao consultationRequestDao = SpringUtils.getBean(ConsultationRequestDao.class);
	private final ConsultationRequestExtDao consultationRequestExtDao = SpringUtils.getBean(ConsultationRequestExtDao.class);
	private final ConsultationServiceDao consultationServiceDao = (ConsultationServiceDao) SpringUtils.getBean(ConsultationServiceDao.class);
	private final DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
	private final ContactDao contactDao = SpringUtils.getBean(ContactDao.class);
	private final FaxJobDao faxJobDao = SpringUtils.getBean(FaxJobDao.class);
    private final FaxClientLogDao faxClientLogDao = SpringUtils.getBean(FaxClientLogDao.class);

    private final boolean bMultisites = IsPropertiesOn.isMultisitesEnable();

    private FaxRecipient specialistFaxLog;
    private Set<FaxRecipient> copyToFaxLog;

    public boolean estPatient(LoggedInInfo loggedInInfo, String demographicNo) {

        int demographic_number = Integer.parseInt(demographicNo);
        Demographic demographic = demographicManager.getDemographic(loggedInInfo, demographic_number);
        boolean estPatient = false;
        DemographicExt demographicExt = demographicManager.getDemographicExt(loggedInInfo, demographic_number, DemographicProperty.demo_cell);
        String demographicCellPhone = "";
        if (demographicExt != null) {
            demographicCellPhone = demographicExt.getValue();
        }

        if (demographic != null) {
            demoNo = demographicNo;
            patientFirstName = demographic.getFirstName();
            patientLastName = demographic.getLastName();
            patientName = demographic.getFormattedName();

            StringBuilder patientAddressSb = new StringBuilder();
            patientAddressSb.append(Encode.forHtml(StringUtils.noNull(demographic.getAddress()))).append("<br>")
                    .append(Encode.forHtml(StringUtils.noNull(demographic.getCity()))).append(",")
                    .append(Encode.forHtml(StringUtils.noNull(demographic.getProvince()))).append("<br>")
                    .append(Encode.forHtml(StringUtils.noNull(demographic.getPostal())));
            patientAddress = patientAddressSb.toString();
            patientPhone = StringUtils.noNull(demographic.getPhone());
            patientWPhone = StringUtils.noNull(demographic.getPhone2());
            patientCellPhone = StringUtils.noNull(demographicCellPhone);
            patientEmail = StringUtils.noNull(demographic.getEmail());
            patientDOB = demographic.getFormattedDob();
            patientHealthNum = StringUtils.noNull(demographic.getHin());
            patientSex = demographic.getSex();
            patientHealthCardType = StringUtils.noNull(demographic.getHcType());
            patientHealthCardVersionCode = StringUtils.noNull(demographic.getVer());
            patientChartNo = StringUtils.noNull(demographic.getChartNo());
            patientAge = demographic.getAge();
            mrp = demographic.getFamilyDoctor();

            estPatient = true;
        }

        return estPatient;
    }

    public boolean estActiveTeams() {
        boolean verdict = true;
        teamVec = new Vector();

        ProviderDao dao = SpringUtils.getBean(ProviderDao.class);
        for (String teamName : dao.getActiveTeams()) {
            if (teamName != null && !teamName.equals("")) {
                teamVec.add(teamName);
            }
        }
        return verdict;
    }

    public boolean estTeams() {
        boolean verdict = true;
        teamVec = new Vector();

        ProviderDao dao = SpringUtils.getBean(ProviderDao.class);
        for (String teamName : dao.getActiveTeams()) {
            if (teamName != null && !teamName.equals("")) {
                teamVec.add(teamName);
            }
        }
        return verdict;
    }

    public boolean estTeamsBySite(String providerNo) {
        boolean verdict = true;
        teamVec = new Vector();
        ProviderDao dao = SpringUtils.getBean(ProviderDao.class);
        for (String teamName : dao.getActiveTeamsViaSites(providerNo)) {
            if (teamName != null && !teamName.equals("")) {
                teamVec.add(teamName);
            }
        }
        return verdict;
    }

    public boolean estTeamsByTeam(String providerNo) {

        boolean verdict = true;
        teamVec = new Vector();

        ProviderDao dao = SpringUtils.getBean(ProviderDao.class);
        for (String teamName : dao.getUniqueTeams()) {
            if (teamName != null && !teamName.equals("")) {
                teamVec.add(teamName);
            }
        }

        return verdict;
    }

    public boolean estRequestFromId(LoggedInInfo loggedInInfo, String id) {

        boolean verdict = true;
        getSpecailistsName(id);


		ConsultationRequest cr = consultationRequestDao.find(Integer.parseInt(id));
		
		if (cr != null) {
			fdid = cr.getFdid();
			pwb = ConversionUtils.toBoolString(cr.isPatientWillBook());
			urgency = cr.getUrgency();
			providerNo = cr.getProviderNo();
			referalDate = ConversionUtils.toDateString(cr.getReferralDate());
			service = "" + cr.getServiceId();

            // attempting to make this code as backwards compatible as possible while enabling
            // the use of a patients health care team directly in the Consultation module.
            if (CarlosProperties.getInstance().getBooleanProperty("ENABLE_HEALTH_CARE_TEAM_IN_CONSULTATION_REQUESTS", "true")) {
                Integer demographicContactId = cr.getDemographicContactId();
                DemographicContact demographicContact = cr.getDemographicContact();
                Integer contactType = null;
                String contactCategory = null;
                Integer contactId = null;
                ProfessionalContact professionalContact = null;

                if (demographicContactId == null) {
                    // a null demographicContactId will return a negative professionalSpecialist id or zero for null.
                    // This is an indication for other methods to convert this professionalSpecialist
                    // into a DemographicContact.
                    Integer specialistId = cr.getSpecialistId();
                    if (specialistId == null) {
                        specialistId = 0;
                    }
                    specialist = (specialistId * -1) + "";
                } else {
                    // if there is a demographic contact id, then the specialist id
                    // returned will reference the DemographicContact model.
                    specialist = demographicContactId + "";
                }

                // if the professionalSpecialist is not found and the Health Care Team is enabled
                // this could mean that the professionalSpecialist is in a different table.
                // Some professional specialists - such as nurse practitioners - are stored in the contacts table.
                if (demographicContact != null) {
                    contactType = demographicContact.getType();
                    contactCategory = demographicContact.getCategory();
                    contactId = Integer.parseInt(demographicContact.getContactId());
                }

                if (contactType == DemographicContact.TYPE_CONTACT
                        && DemographicContact.CATEGORY_PROFESSIONAL.equalsIgnoreCase(contactCategory)) {
                    professionalContact = (ProfessionalContact) contactDao.find(contactId);
                }

                if (professionalContact != null) {
                    cr.setProfessionalSpecialist(DemographicContactCreator.convertProfessionalContactAsProfessionalSpecialist(loggedInInfo, professionalContact));
                }

            } else {
                // The HCT is not enabled therefore identifiers for the DemographicContact model
                // will not be returned.  The previous professionalSpecialist model is used as usual.
                specialist = "" + cr.getSpecialistId();
            }

            // ProfessionalSpecialist joined to the ConsultationRequest will always
            // be available. This model is used in the Fax and PDF printing methods.
            professionalSpecialist = cr.getProfessionalSpecialist();


			Date appointmentTime = cr.getAppointmentTime();
			reasonForConsultation = cr.getReasonForReferral();
			clinicalInformation = cr.getClinicalInfo();
			concurrentProblems = cr.getConcurrentProblems();
			currentMedications = cr.getCurrentMeds();
			allergies = cr.getAllergies();
			sendTo = cr.getSendTo();
			status = cr.getStatus();
			setAppointmentInstructions( cr.getAppointmentInstructions() );
			setAppointmentInstructionsLabel( cr.getAppointmentInstructionsLabel() );
			letterheadName = cr.getLetterheadName();
			letterheadTitle = consultationRequestExtDao.getConsultationRequestExtsByKey(Integer.parseInt(id), "letterheadTitle");
			letterheadAddress = cr.getLetterheadAddress();
			letterheadPhone = cr.getLetterheadPhone();
			letterheadFax = cr.getLetterheadFax();

            letterheadName = letterheadName == null ? "" : letterheadName;
            letterheadTitle = letterheadTitle == null ? "" : letterheadTitle;
            letterheadAddress = letterheadAddress == null ? "" : letterheadAddress;
            letterheadPhone = letterheadPhone == null ? "" : letterheadPhone;
            letterheadFax = letterheadFax == null ? "" : letterheadFax;

            signatureImg = cr.getSignatureImg();

            appointmentNotes = cr.getStatusText();
            if (appointmentNotes == null || appointmentNotes.equals("null")) {
                appointmentNotes = "";
            }
            estPatient(loggedInInfo, "" + cr.getDemographicId());

            if (bMultisites) {
                siteName = cr.getSiteName();
            }

            String date = ConversionUtils.toDateString(cr.getAppointmentDate());
            if (date == null || date.equals("")) {
                appointmentDate = "";
                appointmentHour = "";
                appointmentMinute = "";
                appointmentPm = "";

            } else {
                appointmentDate = date;
                Calendar cal = Calendar.getInstance();
                if (appointmentTime != null) {
                    cal.setTime(appointmentTime);
                    if (cal.get(Calendar.AM_PM) == Calendar.AM)
                        appointmentPm = "AM";
                    else
                        appointmentPm = "PM";
                    if (cal.get(Calendar.HOUR) == 0)
                        appointmentHour = "12";
                    else
                        appointmentHour = String.valueOf(cal.get(Calendar.HOUR));
                    if (cal.get(Calendar.MINUTE) < 10)
                        appointmentMinute = "0" + cal.get(Calendar.MINUTE);
                    else
                        appointmentMinute = String.valueOf(cal.get(Calendar.MINUTE));
                } else {
                    appointmentHour = "";
                    appointmentMinute = "";
                    appointmentPm = "";
                }
            }

			isEReferral = consultationRequestExtDao.getConsultationRequestExtsByKey(Integer.parseInt(id), ConsultationRequestExtKey.EREFERRAL_REF.getKey()) != null;
        }

        getFaxLogs(id);

        return verdict;
    }

    private void getFaxLogs(String requestId) {

        List<FaxClientLog> faxClientLogs = faxClientLogDao.findClientLogbyRequestId(Integer.parseInt(requestId));
        for (FaxClientLog faxClientLog : faxClientLogs) {
            FaxJob faxJob = faxJobDao.find(faxClientLog.getFaxId());
            FaxRecipient faxRecipient = null;
            String specialistFax = "";

            if (this.professionalSpecialist != null) {
                specialistFax = this.professionalSpecialist.getFaxNumber();
            }

            // overcome those silly default nulls in the database.
            if (specialistFax == null) {
                specialistFax = "";
            }

            if (!specialistFax.isEmpty()) {
                specialistFax = specialistFax.trim().replaceAll("\\D", "");
            }

            if (faxJob != null) {
                faxRecipient = new FaxRecipient();
                faxRecipient.setFax(faxJob.getDestination());
                faxRecipient.setName(faxJob.getRecipient());
                faxRecipient.setStatus(faxJob.getStatus());
                faxRecipient.setSent(faxClientLog.getStartTime());

                MiscUtils.getLogger().debug("Does this fax number " + specialistFax + " equal this fax number " + faxRecipient.getFax());
            }


            // isolate the main specialist fax log
            if (faxRecipient != null && specialistFax.equals(faxRecipient.getFax())) {
                setSpecialistFaxLog(faxRecipient);
            }

            // set the copy-to fax logs
            else if (faxRecipient != null) {
                getCopyToFaxLog().add(faxRecipient);
            }
        }

    }

    public String getSpecailistsName(String id) {
        if (id == null || id.trim().length() == 0) {
            return "-1";
        }

        String retval = "";

        ProfessionalSpecialistDao dao = SpringUtils.getBean(ProfessionalSpecialistDao.class);
        ProfessionalSpecialist ps = dao.find(ConversionUtils.fromIntString(id));
        if (ps != null) {
            retval = ps.getFormattedTitle();
            specPhone = ps.getPhoneNumber();
            specFax = ps.getFaxNumber();
            specAddr = ps.getStreetAddress();
            specEmail = ps.getEmailAddress();
            MiscUtils.getLogger().debug("getting Null" + specEmail + "<");
            if (specPhone == null || specPhone.equals("null")) {
                specPhone = "";
            }
            if (specFax == null || specFax.equals("null")) {
                specFax = "";
            }
            if (specAddr == null || specAddr.equals("null")) {
                specAddr = "";
            }
            if (specEmail == null || specEmail.equalsIgnoreCase("null")) {
                specEmail = "";
            }
        }

        return retval;

    }

    public String getSpecailistsEmail(String id) {
        MiscUtils.getLogger().debug("in Get SPECAILISTS EMAIL \n\n" + id);
        String retval = "";

        ProfessionalSpecialistDao dao = SpringUtils.getBean(ProfessionalSpecialistDao.class);
        ProfessionalSpecialist ps = dao.find(ConversionUtils.fromIntString(id));
        if (ps != null) {
            if (specEmail == null || specEmail.equalsIgnoreCase("null")) {
                specEmail = new String();
            }
            retval = specEmail;
        }
        return retval;
    }

    public String getProviderTeam(String id) {
        if (id == null || id.length() == 0)
            return "";
        ProviderDao dao = SpringUtils.getBean(ProviderDao.class);
        Provider p = dao.getProvider(id);
        if (p != null) {
            return p.getTeam();
        }
        return "";
    }

    public String getProviderName(String id) {
        if (id == null || id.length() == 0)
            return "";

        ProviderDao dao = SpringUtils.getBean(ProviderDao.class);
        Provider p = dao.getProvider(id);
        if (p != null) {
            return p.getFormattedName();
        }
        return "";
    }

    public String getFamilyDoctor() {
        ProviderDao dao = SpringUtils.getBean(ProviderDao.class);
        List<Provider> ps = dao.getProviderByPatientId(ConversionUtils.fromIntString(demoNo));
        if (ps.isEmpty()) {
            return "";
        }
        return ps.get(0).getFormattedName();
    }

    public String getServiceName(String id) {
        String retval = new String();
        ConsultationServices cs = consultationServiceDao.find(Integer.parseInt(id));
        if (cs != null) {
            retval = cs.getServiceDesc();
        }

        return retval;
    }

    public String getClinicName() {
        ClinicDAO clinicDao = (ClinicDAO) SpringUtils.getBean(ClinicDAO.class);

        String retval = new String();
        Clinic clinic = clinicDao.getClinic();
        if (clinic != null) {
            retval = clinic.getClinicName();
        }

        return retval;
    }

    public String getAppointmentInstructions() {
        return appointmentInstructions;
    }

    private void setAppointmentInstructions(String appointmentInstructions) {
        if (appointmentInstructions == null) {
            appointmentInstructions = "";
        }
        this.appointmentInstructions = appointmentInstructions;
    }

    public String getAppointmentInstructionsLabel() {
        return appointmentInstructionsLabel;
    }

    private void setAppointmentInstructionsLabel(String appointmentInstructionsLabel) {
        if (appointmentInstructionsLabel == null) {
            appointmentInstructionsLabel = "";
        }
        this.appointmentInstructionsLabel = appointmentInstructionsLabel;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getPatientFirstName() {
        return patientFirstName;
    }

    public void setPatientFirstName(String patientFirstName) {
        this.patientFirstName = patientFirstName;
    }

    public String getPatientLastName() {
        return patientLastName;
    }

    public void setPatientLastName(String patientLastName) {
        this.patientLastName = patientLastName;
    }

    public String getPatientAddress() {
        return patientAddress;
    }

    public void setPatientAddress(String patientAddress) {
        this.patientAddress = patientAddress;
    }

    public String getPatientPhone() {
        return patientPhone;
    }

    public void setPatientPhone(String patientPhone) {
        this.patientPhone = patientPhone;
    }

    public String getPatientWPhone() {
        return patientWPhone;
    }

    public void setPatientWPhone(String patientWPhone) {
        this.patientWPhone = patientWPhone;
    }

    public String getPatientEmail() {
        return patientEmail;
    }

    public void setPatientEmail(String patientEmail) {
        this.patientEmail = patientEmail;
    }

    public String getPatientDOB() {
        return patientDOB;
    }

    public void setPatientDOB(String patientDOB) {
        this.patientDOB = patientDOB;
    }

    public String getPatientHealthNum() {
        return patientHealthNum;
    }

    public void setPatientHealthNum(String patientHealthNum) {
        this.patientHealthNum = patientHealthNum;
    }

    public String getPatientSex() {
        return patientSex;
    }

    public void setPatientSex(String patientSex) {
        this.patientSex = patientSex;
    }

    public String getPatientAge() {
        return patientAge;
    }

    public void setPatientAge(String patientAge) {
        this.patientAge = patientAge;
    }

    public String getPatientHealthCardType() {
        return patientHealthCardType;
    }

    public void setPatientHealthCardType(String patientHealthCardType) {
        this.patientHealthCardType = patientHealthCardType;
    }

    public String getPatientHealthCardVersionCode() {
        return patientHealthCardVersionCode;
    }

    public void setPatientHealthCardVersionCode(String patientHealthCardVersionCode) {
        this.patientHealthCardVersionCode = patientHealthCardVersionCode;
    }

    public String getPatientChartNo() {
        return patientChartNo;
    }

    public void setPatientChartNo(String patientChartNo) {
        this.patientChartNo = patientChartNo;
    }

    public String getFamilyPhysician() {
        return familyPhysician;
    }

    public void setFamilyPhysician(String familyPhysician) {
        this.familyPhysician = familyPhysician;
    }

    public String getReferalDate() {
        return referalDate;
    }

    public void setReferalDate(String referalDate) {
        this.referalDate = referalDate;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getSpecialist() {
        return specialist;
    }

    public void setSpecialist(String specialist) {
        this.specialist = specialist;
    }

    public String getAppointmentDate() {
        return appointmentDate;
    }

    public void setAppointmentDate(String appointmentDate) {
        this.appointmentDate = appointmentDate;
    }

    public String getAppointmentHour() {
        return appointmentHour;
    }

    public void setAppointmentHour(String appointmentHour) {
        this.appointmentHour = appointmentHour;
    }

    public String getAppointmentMinute() {
        return appointmentMinute;
    }

    public void setAppointmentMinute(String appointmentMinute) {
        this.appointmentMinute = appointmentMinute;
    }

    public String getAppointmentPm() {
        return appointmentPm;
    }

    public void setAppointmentPm(String appointmentPm) {
        this.appointmentPm = appointmentPm;
    }

    public String getReasonForConsultation() {
        return reasonForConsultation;
    }

    public void setReasonForConsultation(String reasonForConsultation) {
        this.reasonForConsultation = reasonForConsultation;
    }

    public String getClinicalInformation() {
        return clinicalInformation;
    }

    public void setClinicalInformation(String clinicalInformation) {
        this.clinicalInformation = clinicalInformation;
    }

    public String getConcurrentProblems() {
        return concurrentProblems;
    }

    public void setConcurrentProblems(String concurrentProblems) {
        this.concurrentProblems = concurrentProblems;
    }

    public String getCurrentMedications() {
        return currentMedications;
    }

    public void setCurrentMedications(String currentMedications) {
        this.currentMedications = currentMedications;
    }

    public String getAllergies() {
        return allergies;
    }

    public void setAllergies(String allergies) {
        this.allergies = allergies;
    }

    public String getSendTo() {
        return sendTo;
    }

    public void setSendTo(String sendTo) {
        this.sendTo = sendTo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAppointmentNotes() {
        return appointmentNotes;
    }

    public void setAppointmentNotes(String appointmentNotes) {
        this.appointmentNotes = appointmentNotes;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public String getUrgency() {
        return urgency;
    }

    public void setUrgency(String urgency) {
        this.urgency = urgency;
    }

    public String getSpecPhone() {
        return specPhone;
    }

    public void setSpecPhone(String specPhone) {
        this.specPhone = specPhone;
    }

    public String getSpecFax() {
        return specFax;
    }

    public void setSpecFax(String specFax) {
        this.specFax = specFax;
    }

    public String getSpecAddr() {
        return specAddr;
    }

    public void setSpecAddr(String specAddr) {
        this.specAddr = specAddr;
    }

    public String getSpecEmail() {
        return specEmail;
    }

    public void setSpecEmail(String specEmail) {
        this.specEmail = specEmail;
    }

    public Vector getTeamVec() {
        return teamVec;
    }

    public void setTeamVec(Vector teamVec) {
        this.teamVec = teamVec;
    }

    public String getDemoNo() {
        return demoNo;
    }

    public void setDemoNo(String demoNo) {
        this.demoNo = demoNo;
    }

    public String getPwb() {
        return pwb;
    }

    public void setPwb(String pwb) {
        this.pwb = pwb;
    }

    public String getMrp() {
        return mrp;
    }

    public void setMrp(String mrp) {
        this.mrp = mrp;
    }

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    public String getSignatureImg() {
        return signatureImg;
    }

    public void setSignatureImg(String signatureImg) {
        this.signatureImg = signatureImg;
    }

    public String getLetterheadName() {
        return letterheadName;
    }

    public void setLetterheadName(String letterheadName) {
        this.letterheadName = letterheadName;
    }

    public String getLetterheadTitle() {
        return letterheadTitle;
    }

    public void setLetterheadTitle(String letterheadTitle) {
        this.letterheadTitle = letterheadTitle;
    }

    public String getLetterheadAddress() {
        return letterheadAddress;
    }

    public void setLetterheadAddress(String letterheadAddress) {
        this.letterheadAddress = letterheadAddress;
    }

    public String getLetterheadPhone() {
        return letterheadPhone;
    }

    public void setLetterheadPhone(String letterheadPhone) {
        this.letterheadPhone = letterheadPhone;
    }

    public String getLetterheadFax() {
        return letterheadFax;
    }

    public void setLetterheadFax(String letterheadFax) {
        this.letterheadFax = letterheadFax;
    }

    public Integer getFdid() {
        return fdid;
    }

    public void setFdid(Integer fdid) {
        this.fdid = fdid;
    }

    public ProfessionalSpecialist getProfessionalSpecialist() {
        return professionalSpecialist;
    }

    public void setProfessionalSpecialist(ProfessionalSpecialist professionalSpecialist) {
        this.professionalSpecialist = professionalSpecialist;
    }

    public FaxRecipient getSpecialistFaxLog() {
        return specialistFaxLog;
    }

    private void setSpecialistFaxLog(FaxRecipient specialistFaxLog) {
        this.specialistFaxLog = specialistFaxLog;
    }

    public Set<FaxRecipient> getCopyToFaxLog() {
        if (copyToFaxLog == null) {
            copyToFaxLog = new HashSet<FaxRecipient>();
        }
        return copyToFaxLog;
    }

}
