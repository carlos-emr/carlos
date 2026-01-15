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

package ca.openosp.openo.prescript.data;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import ca.openosp.openo.commn.model.Allergy;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.caisi_integrator.CaisiIntegratorManager;
import ca.openosp.openo.PMmodule.caisi_integrator.IntegratorFallBackManager;
import ca.openosp.openo.caisi_integrator.ws.CachedDemographicAllergy;
import ca.openosp.openo.commn.dao.AllergyDao;
import ca.openosp.openo.commn.dao.DiseasesDao;
import ca.openosp.openo.commn.dao.PartialDateDao;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.Diseases;
import ca.openosp.openo.commn.model.PartialDate;
import ca.openosp.openo.managers.DemographicManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

public class RxPatientData {
    private static Logger logger = MiscUtils.getLogger();
    private static final DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);

    private RxPatientData() {
        // prevent instantiation
    }

    /* Patient Search */

    public static Patient[] PatientSearch(LoggedInInfo loggedInInfo, String surname, String firstName) {

        Patient[] arr = {};
        List<Patient> patients = new ArrayList<Patient>();
        List<Demographic> demographics = demographicManager.searchDemographic(loggedInInfo, surname + "," + firstName);
        for (Demographic demographic : demographics) {
            Patient p = new Patient(demographic);
            patients.add(p);
        }
        return patients.toArray(arr);
    }

    /* Patient Information */

    public static Patient getPatient(LoggedInInfo loggedInInfo, int demographicNo) {
        Demographic demographic = demographicManager.getDemographic(loggedInInfo, demographicNo);
        return new Patient(demographic);
    }

    public static Patient getPatient(LoggedInInfo loggedInInfo, String demographicNo) {
        Demographic demographic = demographicManager.getDemographic(loggedInInfo, demographicNo);
        return new Patient(demographic);
    }

    private static int calcAge(java.util.Date DOB) {
        if (DOB == null) return 0;

        GregorianCalendar now = new GregorianCalendar();
        int curYear = now.get(Calendar.YEAR);
        int curMonth = (now.get(Calendar.MONTH) + 1);
        int curDay = now.get(Calendar.DAY_OF_MONTH);

        Calendar cal = new GregorianCalendar();
        cal.setTime(DOB);
        int iYear = cal.get(Calendar.YEAR);
        int iMonth = (cal.get(Calendar.MONTH) + 1);
        int iDay = cal.get(Calendar.DAY_OF_MONTH);
        int age = 0;

        if (curMonth > iMonth || (curMonth == iMonth && curDay >= iDay)) {
            age = curYear - iYear;
        } else {
            age = curYear - iYear - 1;
        }

        return age;
    }

    public static class Patient {
        private Demographic demographic = null;
        private static AllergyDao allergyDao = (AllergyDao) SpringUtils.getBean(AllergyDao.class);
        private PartialDateDao partialDateDao = (PartialDateDao) SpringUtils.getBean(PartialDateDao.class);

        public Patient(Demographic demographic) {
            this.demographic = demographic;

            if (demographic == null) MiscUtils.getLogger().warn("Demographic is not set!");
        }

        public Demographic getDemographic() {
            return this.demographic;
        }

        public int getDemographicNo() {
            if (demographic != null) {
                return demographic.getDemographicNo();
            } else {
                MiscUtils.getLogger().warn("DemographicNo is not set!");
                return -1;
            }
        }

        public String getSurname() {
            if (demographic != null) return demographic.getLastName();
            else return "";
        }

        public String getFirstName() {
            if (demographic != null) return demographic.getFirstName();
            else return "";
        }

        public String getSex() {
            if (demographic != null) return demographic.getSex();
            else return "";
        }

        public String getHin() {
            if (demographic != null) return demographic.getHin();
            else return "";
        }

        public java.util.Date getDOB() {
            Date dob = null;
            if (demographic != null && demographic.getBirthDay() != null) dob = demographic.getBirthDay().getTime();

            return dob;
        }

        public int getAge() {
            return calcAge(this.getDOB());
        }

        public String getAddress() {
            if (demographic != null) return demographic.getAddress();
            else return "";
        }

        public String getCity() {
            if (demographic != null) return demographic.getCity();
            else return "";
        }

        public String getProvince() {
            if (demographic != null) return demographic.getProvince();
            else return "";
        }

        public String getPostal() {
            if (demographic != null) return demographic.getPostal();
            else return "";
        }

        public String getPhone() {
            if (demographic != null) return demographic.getPhone();
            else return "";
        }

        public String getChartNo() {
            if (demographic != null) return demographic.getChartNo();
            else return "";
        }

        public Allergy getAllergy(int id) {
            Allergy allergy = allergyDao.find(id);
            PartialDate pd = partialDateDao.getPartialDate(PartialDate.ALLERGIES, allergy.getId(), PartialDate.ALLERGIES_STARTDATE);
            if (pd != null) allergy.setStartDateFormat(pd.getFormat());

            return allergy;
        }

        public Allergy[] getAllergies(LoggedInInfo loggedInInfo) {
            ArrayList<Allergy> results = new ArrayList<Allergy>();
            Integer demographicNo = getDemographicNo();
            List<Allergy> allergies = allergyDao.findAllergies(demographicNo);
            results.addAll(allergies);

            if (loggedInInfo.getCurrentFacility().isIntegratorEnabled()) {
                try {
                    List<CachedDemographicAllergy> remoteAllergies = null;
                    try {
                        if (!CaisiIntegratorManager.isIntegratorOffline(loggedInInfo.getSession())) {
                            remoteAllergies = CaisiIntegratorManager.getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility()).getLinkedCachedDemographicAllergies(demographicNo);
                        }
                    } catch (Exception e) {
                        MiscUtils.getLogger().error("Unexpected error.", e);
                        CaisiIntegratorManager.checkForConnectionError(loggedInInfo.getSession(), e);
                    }

                    if (CaisiIntegratorManager.isIntegratorOffline(loggedInInfo.getSession())) {
                        remoteAllergies = IntegratorFallBackManager.getRemoteAllergies(loggedInInfo, demographicNo);
                    }

                    for (CachedDemographicAllergy remoteAllergy : remoteAllergies) {
                        Date date = null;
                        if (remoteAllergy.getEntryDate() != null) date = remoteAllergy.getEntryDate().getTime();

                        Allergy a = new Allergy();
                        a.setDemographicNo(demographicNo);
                        a.setId(remoteAllergy.getFacilityIdIntegerCompositePk().getCaisiItemId().intValue());
                        a.setEntryDate(date);
                        a.setDescription(remoteAllergy.getDescription());
                        a.setHiclSeqno(remoteAllergy.getHiclSeqNo());
                        a.setHicSeqno(remoteAllergy.getHicSeqNo());
                        a.setAgcsp(remoteAllergy.getAgcsp());
                        a.setAgccs(remoteAllergy.getAgccs());
                        a.setTypeCode(remoteAllergy.getTypeCode());
                        a.setIntegratorResult(true);
                        a.setReaction(remoteAllergy.getReaction());

                        if (remoteAllergy.getStartDate() != null) date = remoteAllergy.getStartDate().getTime();

                        a.setStartDate(date);
                        a.setAgeOfOnset(remoteAllergy.getAgeOfOnset());
                        a.setSeverityOfReaction(remoteAllergy.getSeverityCode());
                        a.setOnsetOfReaction(remoteAllergy.getOnSetCode());
                        a.setRegionalIdentifier(remoteAllergy.getRegionalIdentifier());
                        a.setLifeStage(remoteAllergy.getLifeStage());
                        a.setDrugrefId(String.valueOf(remoteAllergy.getPickId()));

                        results.add(a);
                    }
                } catch (Exception e) {
                    logger.error("error getting remote allergies", e);
                }
            }

            return (results.toArray(new Allergy[0]));
        }

        public Allergy[] getActiveAllergies() {
            List<Allergy> allergies = allergyDao.findActiveAllergies(getDemographicNo());
            return allergies.toArray(new Allergy[allergies.size()]);
        }

        public void addAllergy(java.util.Date entryDate, Allergy allergy) {
            allergy.setEntryDate(entryDate);
            allergyDao.persist(allergy);
            partialDateDao.setPartialDate(PartialDate.ALLERGIES, allergy.getId(), PartialDate.ALLERGIES_STARTDATE, allergy.getStartDateFormat());
        }

        private static boolean setAllergyArchive(int allergyId, boolean archive) {
            Allergy allergy = allergyDao.find(allergyId);
            if (allergy != null) {
                allergy.setArchived(archive);
                allergyDao.merge(allergy);
                return (true);
            }

            return (false);
        }

        public boolean deleteAllergy(int allergyId) {
            return (setAllergyArchive(allergyId, true));
        }

        public boolean activateAllergy(int allergyId) {
            return (setAllergyArchive(allergyId, false));
        }

        public Diseases[] getDiseases() {
            DiseasesDao diseasesDao = SpringUtils.getBean(DiseasesDao.class);
            List<Diseases> diseases = diseasesDao.findByDemographicNo(getDemographicNo());
            return diseases.toArray(new Diseases[diseases.size()]);
        }

        public Diseases addDisease(String ICD9, java.util.Date entryDate) {
            DiseasesDao diseasesDao = SpringUtils.getBean(DiseasesDao.class);
            Diseases disease = new Diseases();
            disease.setDemographicNo(getDemographicNo());
            disease.setIcd9Entry(ICD9);
            disease.setEntryDate(entryDate);
            diseasesDao.persist(disease);
            return disease;
        }

        public RxPrescriptionData.Prescription[] getPrescribedDrugsUnique() {
            return new RxPrescriptionData().getUniquePrescriptionsByPatient(this.getDemographicNo());
        }

        public RxPrescriptionData.Prescription[] getPrescribedDrugs() {
            return new RxPrescriptionData().getPrescriptionsByPatient(this.getDemographicNo());
        }

        public RxPrescriptionData.Prescription[] getPrescribedDrugScripts() {
            return new RxPrescriptionData().getPrescriptionScriptsByPatient(this.getDemographicNo());
        }

        @Override
        public String toString() {
            return new ToStringBuilder(this)
                    .append("demographic", demographic)
                    .append("partialDateDao", partialDateDao)
                    .toString();
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .toString();
    }
}
