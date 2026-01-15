//CHECKSTYLE:OFF
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
 */


package ca.openosp.openo.encounter.oscarConsultationRequest.pageUtil;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.logging.log4j.Logger;
import org.apache.xml.security.exceptions.Base64DecodingException;
import org.apache.xml.security.utils.Base64;
import ca.openosp.openo.PMmodule.dao.ProviderDao;
import ca.openosp.openo.commn.dao.ConsultationRequestDao;
import ca.openosp.openo.commn.dao.ConsultationRequestExtDao;
import ca.openosp.openo.commn.dao.Hl7TextMessageDao;
import ca.openosp.openo.commn.hl7.v2.oscar_to_oscar.DataTypeUtils;
import ca.openosp.openo.commn.hl7.v2.oscar_to_oscar.OscarToOscarUtils;
import ca.openosp.openo.commn.hl7.v2.oscar_to_oscar.RefI12;
import ca.openosp.openo.commn.model.ConsultationRequest;
import ca.openosp.openo.commn.model.ConsultationRequestExt;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.DemographicExt;
import ca.openosp.openo.commn.model.DemographicExt.DemographicProperty;
import ca.openosp.openo.commn.model.Hl7TextMessage;
import ca.openosp.openo.commn.model.ProfessionalSpecialist;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.commn.model.enumerator.ConsultationRequestExtKey;
import ca.openosp.openo.managers.ConsultationManager;
import ca.openosp.openo.managers.DemographicManager;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.util.UtilDateUtilities;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.v26.message.REF_I12;
import ca.uhn.hl7v2.model.v26.segment.PID;
import ca.uhn.hl7v2.model.v26.segment.PRD;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class EctViewRequest2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static final Logger logger = MiscUtils.getLogger();
    private static SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws ServletException, IOException {
        checkPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request));

        request.setAttribute("id", this.getRequestId());

        logger.debug("Id:" + this.getRequestId());
        logger.debug("SegmentId:" + request.getParameter("segmentId"));

        return SUCCESS;
    }

    private static void setAppointmentDateTime(EctConsultationFormRequest2Form thisForm, ConsultationRequest consult) {
        Calendar cal = Calendar.getInstance();
        Date date1 = consult.getAppointmentDate();
        Date date2 = consult.getAppointmentTime();

        if (date1 == null || date2 == null) {
            thisForm.setAppointmentDate("");
        } else {
            thisForm.setAppointmentDate(DateFormatUtils.ISO_DATE_FORMAT.format(date1));
            cal.setTime(date2);

            int hour = cal.get(Calendar.HOUR_OF_DAY);
            if (cal.get(Calendar.AM_PM) == Calendar.AM) {
                if (hour == 0) {
                    hour = 12;
                }
                thisForm.setAppointmentPm("AM");
            } else {
                if (hour > 12) {
                    hour -= 12;
                }
                thisForm.setAppointmentPm("PM");
            }

            if (cal.get(Calendar.HOUR) == 0)
                thisForm.setAppointmentHour("12");
            else
                thisForm.setAppointmentHour(String.valueOf(cal.get(Calendar.HOUR)));

            thisForm.setAppointmentMinute(String.valueOf(cal.get(Calendar.MINUTE)));
        }
    }


    public static void fillFormValues(LoggedInInfo loggedInInfo, EctConsultationFormRequest2Form thisForm, Integer requestId) {
        checkPrivilege(loggedInInfo);

        ConsultationManager consultationManager = SpringUtils.getBean(ConsultationManager.class);
        ConsultationRequestExtDao consultationRequestExtDao = SpringUtils.getBean(ConsultationRequestExtDao.class);
        List<ConsultationRequestExt> extras = consultationRequestExtDao.getConsultationRequestExts(requestId);
        Map<String, String> extraMap = consultationManager.getExtValuesAsMap(extras);

        ConsultationRequestDao consultDao = (ConsultationRequestDao)SpringUtils.getBean(ConsultationRequestDao.class);;
        ConsultationRequest consult = consultDao.find(requestId);

        thisForm.setAllergies(consult.getAllergies());
        thisForm.setReasonForConsultation(consult.getReasonForReferral());
        thisForm.setClinicalInformation(consult.getClinicalInfo());
        thisForm.setCurrentMedications(consult.getCurrentMeds());
        Date date = consult.getReferralDate();
        thisForm.setReferalDate(DateFormatUtils.ISO_DATE_FORMAT.format(date));
        thisForm.setSendTo(consult.getSendTo());
        thisForm.setService(consult.getServiceId().toString());
        thisForm.setStatus(consult.getStatus());

        setAppointmentDateTime(thisForm, consult);

        thisForm.setConcurrentProblems(consult.getConcurrentProblems());
        thisForm.setAppointmentNotes(consult.getStatusText());
        thisForm.setUrgency(consult.getUrgency());
        thisForm.setAppointmentInstructions(consult.getAppointmentInstructions());

        thisForm.setPatientWillBook(String.valueOf(consult.isPatientWillBook()));

        date = consult.getFollowUpDate();
        if (date != null) {
            thisForm.setFollowUpDate(DateFormatUtils.ISO_DATE_FORMAT.format(date));
        } else {
            thisForm.setFollowUpDate("");
        }

        DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
        Demographic demo = demographicManager.getDemographic(loggedInInfo, consult.getDemographicId());
        DemographicExt demographicExt = demographicManager.getDemographicExt(loggedInInfo, consult.getDemographicId(), DemographicProperty.demo_cell);
        String demographicCellPhone = "";
        if (demographicExt != null) {
            demographicCellPhone = demographicExt.getValue();
        }
        thisForm.setPatientAddress(demo.getAddress());
        thisForm.setPatientDOB(demo.getFormattedDob());
        thisForm.setPatientHealthNum(demo.getHin());
        thisForm.setPatientHealthCardVersionCode(demo.getVer());
        thisForm.setPatientHealthCardType(demo.getHcType());
        thisForm.setPatientFirstName(demo.getFirstName());
        thisForm.setPatientLastName(demo.getLastName());
        thisForm.setPatientPhone(demo.getPhone());
        thisForm.setPatientSex(demo.getSex());
        thisForm.setPatientWPhone(demo.getPhone2());
        thisForm.setPatientCellPhone(demographicCellPhone);
        thisForm.setPatientEmail(demo.getEmail());
        thisForm.setPatientAge(demo.getAge());

        ProviderDao provDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);
        Provider prov = provDao.getProvider(consult.getProviderNo());
        thisForm.setProviderName(prov.getFormattedName());

        boolean isEReferral = extraMap.containsKey(ConsultationRequestExtKey.EREFERRAL_REF.getKey());
        thisForm.seteReferral(isEReferral);
        if (isEReferral) {
            thisForm.setProfessionalSpecialistName(extraMap.getOrDefault(ConsultationRequestExtKey.EREFERRAL_DOCTOR.getKey(), ""));
            thisForm.seteReferralService(extraMap.getOrDefault(ConsultationRequestExtKey.EREFERRAL_SERVICE.getKey(), ""));
            thisForm.seteReferralId(extraMap.get(ConsultationRequestExtKey.EREFERRAL_REF.getKey()));
        }

        thisForm.setLetterheadName(consult.getLetterheadName());
        thisForm.setLetterheadAddress(consult.getLetterheadAddress());
        thisForm.setLetterheadPhone(consult.getLetterheadPhone());
        thisForm.setLetterheadFax(consult.getLetterheadFax());

        thisForm.setFdid(consult.getFdid());
    }

    public static void fillFormValues(EctConsultationFormRequest2Form thisForm, EctConsultationFormRequestUtil consultUtil) {
        thisForm.setAllergies(consultUtil.allergies);
        thisForm.setReasonForConsultation(consultUtil.reasonForConsultation);
        thisForm.setClinicalInformation(consultUtil.clinicalInformation);
        thisForm.setCurrentMedications(consultUtil.currentMedications);
        thisForm.setReferalDate(consultUtil.referalDate);
        thisForm.setSendTo(consultUtil.sendTo);
        thisForm.setService(consultUtil.service);
        thisForm.setStatus(consultUtil.status);
        thisForm.setAppointmentDate(consultUtil.appointmentDate);
        thisForm.setAppointmentHour(consultUtil.appointmentHour);
        thisForm.setAppointmentMinute(consultUtil.appointmentMinute);
        thisForm.setAppointmentPm(consultUtil.appointmentPm);
        thisForm.setConcurrentProblems(consultUtil.concurrentProblems);
        thisForm.setAppointmentNotes(consultUtil.appointmentNotes);
        thisForm.setUrgency(consultUtil.urgency);
        thisForm.setAppointmentInstructions(consultUtil.getAppointmentInstructions());
        thisForm.setAppointmentInstructionsLabel(consultUtil.getAppointmentInstructionsLabel());
        thisForm.setPatientWillBook(consultUtil.pwb);

        if (consultUtil.sendTo != null && !consultUtil.teamVec.contains(consultUtil.sendTo)) {
            consultUtil.teamVec.add(consultUtil.sendTo);
        }

        //---

        thisForm.setPatientAddress(consultUtil.patientAddress);
        thisForm.setPatientDOB(consultUtil.patientDOB);
        thisForm.setPatientHealthNum(consultUtil.patientHealthNum);
        thisForm.setPatientHealthCardVersionCode(consultUtil.patientHealthCardVersionCode);
        thisForm.setPatientHealthCardType(consultUtil.patientHealthCardType);
        thisForm.setPatientFirstName(consultUtil.patientFirstName);
        thisForm.setPatientLastName(consultUtil.patientLastName);
        thisForm.setPatientPhone(consultUtil.patientPhone);
        thisForm.setPatientSex(consultUtil.patientSex);
        thisForm.setPatientWPhone(consultUtil.patientWPhone);
        thisForm.setPatientCellPhone(consultUtil.patientCellPhone);
        thisForm.setPatientEmail(consultUtil.patientEmail);
        thisForm.setPatientAge(consultUtil.patientAge);

        thisForm.setProviderName(consultUtil.getProviderName(consultUtil.providerNo));

        thisForm.setLetterheadName(consultUtil.letterheadName);
        thisForm.setLetterheadAddress(consultUtil.letterheadAddress);
        thisForm.setLetterheadPhone(consultUtil.letterheadPhone);
        thisForm.setLetterheadFax(consultUtil.letterheadFax);

        thisForm.seteReferral(false);

        thisForm.setFdid(consultUtil.fdid);

    }

    public static void fillFormValues(EctConsultationFormRequest2Form thisForm, String segmentId) throws HL7Exception, UnsupportedEncodingException, Base64DecodingException {
        Hl7TextMessageDao hl7TextMessageDao = (Hl7TextMessageDao) SpringUtils.getBean(Hl7TextMessageDao.class);
        Hl7TextMessage hl7TextMessage = hl7TextMessageDao.find(Integer.parseInt(segmentId));

        String encodedMessage = hl7TextMessage.getBase64EncodedeMessage();
        byte[] decodedMessage = Base64.decode(encodedMessage);
        String decodedMessageString = new String(decodedMessage, MiscUtils.DEFAULT_UTF8_ENCODING);

        REF_I12 refI12 = (REF_I12) OscarToOscarUtils.pipeParserParse(decodedMessageString);

        thisForm.setHl7TextMessageId(hl7TextMessage.getId());

        thisForm.setAllergies(RefI12.getNteValue(refI12, RefI12.REF_NTE_TYPE.ALLERGIES));
        thisForm.setReasonForConsultation(RefI12.getNteValue(refI12, RefI12.REF_NTE_TYPE.REASON_FOR_CONSULTATION));
        thisForm.setClinicalInformation(RefI12.getNteValue(refI12, RefI12.REF_NTE_TYPE.CLINICAL_INFORMATION));
        thisForm.setCurrentMedications(RefI12.getNteValue(refI12, RefI12.REF_NTE_TYPE.CURRENT_MEDICATIONS));

        GregorianCalendar referralDate = DataTypeUtils.getCalendarFromDTM(refI12.getRF1().getEffectiveDate());
        thisForm.setReferalDate(DateFormatUtils.ISO_DATE_FORMAT.format(referralDate));

        thisForm.setConcurrentProblems(RefI12.getNteValue(refI12, RefI12.REF_NTE_TYPE.CONCURRENT_PROBLEMS));

        // spoecifically told that this field should not be sent electronically (so it shouldn't be received either).
        // thisForm.setAppointmentNotes(RefI12.getNteValue(refI12, RefI12.REF_NTE_TYPE.APPOINTMENT_NOTES));

        //---


        PID pid = refI12.getPID();
        Demographic demographic = DataTypeUtils.parsePid(pid);

        StringBuilder address = new StringBuilder();
        if (demographic.getAddress() != null) address.append(demographic.getAddress()).append("<br />");
        if (demographic.getCity() != null) address.append(demographic.getCity()).append(", ");
        if (demographic.getProvince() != null) address.append(demographic.getProvince());
        thisForm.setPatientAddress(address.toString());

        if (demographic.getBirthDay() != null) {
            thisForm.setPatientDOB(DateFormatUtils.ISO_DATE_FORMAT.format(demographic.getBirthDay()));
            String ageString = UtilDateUtilities.calcAgeAtDate(demographic.getBirthDay().getTime(), new Date());
            thisForm.setPatientAge(ageString);
        }

        thisForm.setPatientHealthNum(demographic.getHin());
        thisForm.setPatientHealthCardType(demographic.getHcType());
        thisForm.setPatientHealthCardVersionCode(demographic.getVer());

        thisForm.setPatientFirstName(demographic.getFirstName());
        thisForm.setPatientLastName(demographic.getLastName());
        thisForm.setPatientPhone(demographic.getPhone());
        thisForm.setPatientSex(demographic.getSex());
//        thisForm.setPatientWPhone(patientAddress);
        thisForm.setPatientEmail(demographic.getEmail());

        // referring providers
        PRD referringPrd = RefI12.getPrdByRoleId(refI12, "RP");
        Provider provider = DataTypeUtils.parsePrdAsProvider(referringPrd);
        thisForm.setProviderName(provider.getLastName() + ", " + provider.getFirstName());

        thisForm.seteReferral(true);

        // referredTo specialist
        PRD referredToPrd = RefI12.getPrdByRoleId(refI12, "RT");
        ProfessionalSpecialist professionalSpecialist = DataTypeUtils.parsePrdAsProfessionalSpecialist(referredToPrd);
        thisForm.setProfessionalSpecialistName(professionalSpecialist.getLastName() + ", " + professionalSpecialist.getFirstName());
        thisForm.setProfessionalSpecialistAddress(professionalSpecialist.getStreetAddress());
        thisForm.setProfessionalSpecialistPhone(professionalSpecialist.getPhoneNumber());

    }


    private static void checkPrivilege(LoggedInInfo loggedInInfo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_con", "r", null)) {
            throw new SecurityException("missing required sec object (_con)");
        }
    }


    public String getRequestId() {
        if (requestId == null)
            requestId = new String();
        return requestId;
    }

    public void setRequestId(String str) {
        requestId = str;
    }

    String requestId;
}
