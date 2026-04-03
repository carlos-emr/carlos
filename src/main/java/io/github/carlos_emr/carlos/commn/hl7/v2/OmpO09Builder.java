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

package io.github.carlos_emr.carlos.commn.hl7.v2;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.Gender;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.CarlosProperties;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.DataTypeException;
import ca.uhn.hl7v2.model.v26.datatype.CQ;
import ca.uhn.hl7v2.model.v26.datatype.CWE;
import ca.uhn.hl7v2.model.v26.datatype.CX;
import ca.uhn.hl7v2.model.v26.datatype.DTM;
import ca.uhn.hl7v2.model.v26.datatype.FT;
import ca.uhn.hl7v2.model.v26.datatype.NM;
import ca.uhn.hl7v2.model.v26.datatype.RPT;
import ca.uhn.hl7v2.model.v26.datatype.XAD;
import ca.uhn.hl7v2.model.v26.datatype.XCN;
import ca.uhn.hl7v2.model.v26.datatype.XON;
import ca.uhn.hl7v2.model.v26.datatype.XPN;
import ca.uhn.hl7v2.model.v26.datatype.XTN;
import ca.uhn.hl7v2.model.v26.group.OMP_O09_ORDER;
import ca.uhn.hl7v2.model.v26.group.OMP_O09_PATIENT;
import ca.uhn.hl7v2.model.v26.group.OMP_O09_TIMING;
import ca.uhn.hl7v2.model.v26.message.OMP_O09;
import ca.uhn.hl7v2.model.v26.segment.MSH;
import ca.uhn.hl7v2.model.v26.segment.NTE;
import ca.uhn.hl7v2.model.v26.segment.ORC;
import ca.uhn.hl7v2.model.v26.segment.PID;
import ca.uhn.hl7v2.model.v26.segment.RXO;
import ca.uhn.hl7v2.model.v26.segment.SFT;
import ca.uhn.hl7v2.model.v26.segment.TQ1;

/**
 * Builds HL7 v2.6 OMP_O09 (Pharmacy/Treatment Order) messages for prescription data.
 *
 * <p>Used by {@link io.github.carlos_emr.carlos.web.PrescriptionQrCodeUIBean} to generate
 * QR codes containing prescription data in HL7 format.
 *
 * <p>Extracted from the removed Oscar-to-Oscar communication package. Contains the
 * HL7 segment-filling utilities that were previously in DataTypeUtils, scoped to
 * only what OMP_O09 message construction requires.
 *
 * @since 2026-04-03
 */
public final class OmpO09Builder {
    private static final Logger logger = MiscUtils.getLogger();

    private static final String HL7_VERSION_ID = "2.6";
    private static final int NTE_COMMENT_MAX_SIZE = 65536;
    private static final SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyyMMddHHmmss");

    private OmpO09Builder() {
        // not meant to be instantiated
    }

    /**
     * Builds an OMP_O09 HL7 message from a prescription with its associated drugs.
     *
     * @param clinic       Clinic the clinic issuing the prescription
     * @param provider     Provider the prescribing provider
     * @param demographic  Demographic the patient
     * @param prescription Prescription the prescription record
     * @param drugs        List of Drug objects in this prescription
     * @return OMP_O09 the constructed HL7 message
     * @throws HL7Exception if HL7 message construction fails
     * @throws UnsupportedEncodingException if encoding fails
     */
    public static OMP_O09 makeOmpO09(Clinic clinic, Provider provider, Demographic demographic, Prescription prescription, List<Drug> drugs) throws HL7Exception, UnsupportedEncodingException {
        OMP_O09 prescriptionMsg = new OMP_O09();

        fillMsh(prescriptionMsg.getMSH(), new Date(), clinic.getClinicName(), "OMP", "O09", "OMP_O09", HL7_VERSION_ID);
        fillSft(prescriptionMsg.getSFT(), CarlosProperties.getBuildTag(), CarlosProperties.getBuildDate());

        OMP_O09_PATIENT patient = prescriptionMsg.getPATIENT();
        fillPid(patient.getPID(), 1, demographic);

        String rxComments = prescription.getComments();
        if (rxComments != null)
            fillNte(prescriptionMsg.getNTE(0), "Rx Comments", null, rxComments.getBytes());

        int drugCounter = 0;
        for (Drug drug : drugs) {
            OMP_O09_ORDER order = prescriptionMsg.getORDER(drugCounter);
            fillOrc(order, prescription, provider, clinic);
            fillTq1(order, drug);
            fillRxo(order.getRXO(), drug);

            String special = drug.getSpecial();
            if (special != null) fillNte(order.getNTE(0), "Prescription Text", null, special.getBytes());

            drugCounter++;
        }

        return (prescriptionMsg);
    }

    private static void fillRxo(RXO rxo, Drug drug) throws HL7Exception {
        CWE drugType = rxo.getRequestedGiveCode();

        StringBuilder drugTypeSb = new StringBuilder();
        if (drug.getGenericName() != null) drugTypeSb.append(drug.getGenericName());
        if (drug.getBrandName() != null) {
            drugTypeSb.append('(');
            drugTypeSb.append(drug.getBrandName());
            drugTypeSb.append(')');
        }
        drugType.getText().setValue(drugTypeSb.toString());

        NM dosageMin = rxo.getRequestedGiveAmountMinimum();
        dosageMin.setValue(drug.getDosage());

        CWE dosageUnits = rxo.getRequestedGiveUnits();
        dosageUnits.getText().setValue(drug.getUnit());

        CWE dosageForm = rxo.getRequestedDosageForm();
        dosageForm.getText().setValue(drug.getDrugForm());

        CWE specialInstructions = rxo.getProviderSPharmacyTreatmentInstructions(0);
        specialInstructions.getText().setValue(drug.getSpecialInstruction());

        CWE administraionRouteMethod = rxo.getProviderSAdministrationInstructions(0);
        StringBuilder routeMethodSb = new StringBuilder();
        if (drug.getRoute() != null) routeMethodSb.append(drug.getRoute());
        if (drug.getMethod() != null) {
            if (routeMethodSb.length() > 0) routeMethodSb.append(", ");
            routeMethodSb.append(drug.getMethod());
        }
        administraionRouteMethod.getText().setValue(routeMethodSb.toString());

        rxo.getAllowSubstitutions().setValue(String.valueOf(!drug.isNoSubs()));
    }

    private static void fillTq1(OMP_O09_ORDER order, Drug drug) throws HL7Exception {
        OMP_O09_TIMING timing = order.getTIMING(0);
        TQ1 tq1 = timing.getTQ1();

        CQ cq = tq1.getQuantity();
        NM quantity = cq.getQuantity();
        quantity.setValue(drug.getQuantity());
        CWE units = cq.getUnits();
        units.getText().setValue(drug.getUnit());
        units.getNameOfCodingSystem().setValue(drug.getUnitName());

        RPT rpt = tq1.getRepeatPattern(0);
        rpt.getGeneralTimingSpecification().setValue(drug.getFreqCode());

        CQ serviceDuration = tq1.getServiceDuration();
        serviceDuration.getQuantity().setValue(drug.getDuration());
        serviceDuration.getUnits().getNameOfCodingSystem().setValue(drug.getDurUnit());

        tq1.getStartDateTime().setValue(getAsHl7FormattedString(drug.getRxDate()));
        tq1.getEndDateTime().setValue(getAsHl7FormattedString(drug.getEndDate()));
    }

    private static void fillOrc(OMP_O09_ORDER order, Prescription prescription, Provider provider, Clinic clinic) throws HL7Exception {
        ORC orc = order.getORC();

        orc.getOrderControl().setValue("NW");

        if (prescription.getId() != null) {
            orc.getPlacerOrderNumber().getEntityIdentifier().setValue(prescription.getId().toString());
        }

        fillXcn(orc.getOrderingProvider(0), provider);
        orc.getOrderEffectiveDateTime().setValue(getAsHl7FormattedString(prescription.getDatePrescribed()));

        XON xon = orc.getOrderingFacilityName(0);
        xon.getOrganizationName().setValue(StringUtils.trimToNull(clinic.getClinicName()));

        XAD xad = orc.getOrderingFacilityAddress(0);
        fillXAD(xad, clinic, null, "O");

        XTN xtn = orc.getOrderingFacilityPhoneNumber(0);
        xtn.getUnformattedTelephoneNumber().setValue(StringUtils.trimToNull(clinic.getClinicPhone()));
    }

    // ---- HL7 segment helpers (extracted from DataTypeUtils) ----

    private static String getAsHl7FormattedString(Date date) {
        synchronized (dateTimeFormatter) {
            return (dateTimeFormatter.format(date));
        }
    }

    private static void fillMsh(MSH msh, Date dateOfMessage, String clinicName, String messageCode, String triggerEvent, String messageStructure, String hl7VersionId) throws DataTypeException {
        msh.getFieldSeparator().setValue("|");
        msh.getEncodingCharacters().setValue("^~\\&");
        msh.getVersionID().getVersionID().setValue(hl7VersionId);
        msh.getDateTimeOfMessage().setValue(getAsHl7FormattedString(dateOfMessage));
        msh.getSendingApplication().getNamespaceID().setValue("OSCAR");
        msh.getSendingFacility().getNamespaceID().setValue(clinicName);
        msh.getMessageType().getMessageCode().setValue(messageCode);
        msh.getMessageType().getTriggerEvent().setValue(triggerEvent);
        msh.getMessageType().getMessageStructure().setValue(messageStructure);
    }

    private static void fillSft(SFT sft, String version, String build) throws DataTypeException {
        sft.getSoftwareVendorOrganization().getOrganizationName().setValue("OSCARMcMaster");
        sft.getSoftwareCertifiedVersionOrReleaseNumber().setValue(version);
        sft.getSoftwareProductName().setValue("OSCAR");
        sft.getSoftwareBinaryID().setValue(build);
    }

    private static void fillPid(PID pid, int pidNumber, Demographic demographic) throws HL7Exception {
        pid.getSetIDPID().setValue(String.valueOf(pidNumber));

        CX cx = pid.getPatientIdentifierList(0);
        cx.getIDNumber().setValue(demographic.getHin());
        cx.getIdentifierTypeCode().setValue("HEALTH_NUMBER");
        cx.getIdentifierCheckDigit().setValue(demographic.getVer());
        cx.getAssigningJurisdiction().getIdentifier().setValue(demographic.getHcType());

        GregorianCalendar tempCalendar = new GregorianCalendar();
        if (demographic.getEffDate() != null) {
            tempCalendar.setTime(demographic.getEffDate());
            cx.getEffectiveDate().setYearMonthDayPrecision(tempCalendar.get(GregorianCalendar.YEAR), tempCalendar.get(GregorianCalendar.MONTH) + 1, tempCalendar.get(GregorianCalendar.DAY_OF_MONTH));
        }

        if (demographic.getHcRenewDate() != null) {
            tempCalendar.setTime(demographic.getHcRenewDate());
            cx.getExpirationDate().setYearMonthDayPrecision(tempCalendar.get(GregorianCalendar.YEAR), tempCalendar.get(GregorianCalendar.MONTH) + 1, tempCalendar.get(GregorianCalendar.DAY_OF_MONTH));
        }

        XPN xpn = pid.getPatientName(0);
        xpn.getFamilyName().getSurname().setValue(demographic.getLastName());
        xpn.getGivenName().setValue(demographic.getFirstName());
        xpn.getNameTypeCode().setValue("L");

        if (demographic.getBirthDay() != null) {
            DTM bday = pid.getDateTimeOfBirth();
            tempCalendar = demographic.getBirthDay();
            bday.setDatePrecision(tempCalendar.get(GregorianCalendar.YEAR), tempCalendar.get(GregorianCalendar.MONTH) + 1, tempCalendar.get(GregorianCalendar.DAY_OF_MONTH));
        }

        pid.getAdministrativeSex().setValue(getHl7Gender(demographic.getSex()));

        XAD address = pid.getPatientAddress(0);
        fillXAD(address, demographic, null, "H");

        XTN phone = pid.getPhoneNumberHome(0);
        phone.getUnformattedTelephoneNumber().setValue(demographic.getPhone());

        pid.getPrimaryLanguage().getIdentifier().setValue(demographic.getSpokenLanguage());
        pid.getCitizenship(0).getIdentifier().setValue(demographic.getCitizenship());
    }

    private static void fillNte(NTE nte, String subject, String fileName, byte[] data) throws HL7Exception, UnsupportedEncodingException {
        nte.getCommentType().getText().setValue(subject);
        if (fileName != null) nte.getCommentType().getNameOfCodingSystem().setValue(fileName);

        String stringData = new String(Base64.encodeBase64(data), MiscUtils.DEFAULT_UTF8_ENCODING);
        int dataLength = stringData.length();
        int chunks = dataLength / NTE_COMMENT_MAX_SIZE;
        if (dataLength % NTE_COMMENT_MAX_SIZE != 0) chunks++;

        for (int i = 0; i < chunks; i++) {
            FT commentPortion = nte.getComment(i);
            int startIndex = i * NTE_COMMENT_MAX_SIZE;
            int endIndex = Math.min(dataLength, startIndex + NTE_COMMENT_MAX_SIZE);
            commentPortion.setValue(stringData.substring(startIndex, endIndex));
        }
    }

    private static void fillXcn(XCN xcn, Provider provider) throws DataTypeException {
        xcn.getIDNumber().setValue(provider.getProviderNo());
        xcn.getFamilyName().getSurname().setValue(provider.getLastName());
        xcn.getGivenName().setValue(provider.getFirstName());
        xcn.getPrefixEgDR().setValue(provider.getTitle());
    }

    private static void fillXAD(XAD xad, Clinic clinic, String country, String addressType) throws DataTypeException {
        xad.getStreetAddress().getStreetOrMailingAddress().setValue(StringUtils.trimToNull(clinic.getClinicAddress()));
        xad.getCity().setValue(StringUtils.trimToNull(clinic.getClinicCity()));
        xad.getStateOrProvince().setValue(StringUtils.trimToNull(clinic.getClinicProvince()));
        if (country != null) xad.getCountry().setValue(StringUtils.trimToNull(country));
        xad.getZipOrPostalCode().setValue(StringUtils.trimToNull(clinic.getClinicPostal()));
        xad.getAddressType().setValue(addressType);
    }

    private static void fillXAD(XAD xad, Demographic demographic, String country, String addressType) throws DataTypeException {
        xad.getStreetAddress().getStreetOrMailingAddress().setValue(StringUtils.trimToNull(demographic.getAddress()));
        xad.getCity().setValue(StringUtils.trimToNull(demographic.getCity()));
        xad.getStateOrProvince().setValue(StringUtils.trimToNull(demographic.getProvince()));
        if (country != null) xad.getCountry().setValue(StringUtils.trimToNull(country));
        xad.getZipOrPostalCode().setValue(StringUtils.trimToNull(demographic.getPostal()));
        xad.getAddressType().setValue(addressType);
    }

    private static String getHl7Gender(String oscarGender) {
        Gender gender = null;
        try {
            gender = Gender.valueOf(oscarGender);
        } catch (NullPointerException e) {
            // no gender set
        } catch (Exception e) {
            logger.error("Missed gender or dirty data in database. demographic.sex=" + oscarGender);
        }

        if (gender == null) return "U";
        if (Gender.M == gender) return "M";
        if (Gender.F == gender) return "F";
        return "U";
    }
}
