/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 */

package io.github.carlos_emr.carlos.billings.ca.bc.MSP;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.carlos_emr.carlos.commn.dao.BillingCdmServiceCodesDao;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceAgeRulesDao;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceSexRulesDao;
import io.github.carlos_emr.carlos.commn.dao.DxresearchDAO;
import io.github.carlos_emr.carlos.commn.model.BillingCdmServiceCodes;
import io.github.carlos_emr.carlos.commn.model.CtlBillingServiceAgeRules;
import io.github.carlos_emr.carlos.commn.model.CtlBillingServiceSexRules;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Dxresearch;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.entities.Billingmaster;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingFormData.BillingService;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.util.DateUtils;
import io.github.carlos_emr.carlos.util.UtilMisc;

/**
 * <p>Title:ServiceCodeValidationLogic </p>
 *
 * @author Joel Legris
 * @version 1.0
 * @todo Should be renamed to something more appropriate eg ServiceCodeDAO
 * <p>Description: </p>
 * <p>Responsible for service code validation
 */
public class ServiceCodeValidationLogic {

    private String demographicNo;
    private String serviceCode;
    private SimpleDateFormat fmt;
    public static final int DAILY_CODES_USED = 2;
    public static final int YEARLY_CODES_USED = 1;
    public static final String ANNUAL_AVAILABLE_UNITS = "ANNUAL_AVAILABLE_UNITS";
    public static final String DAILY_AVAILABLE_UNITS = "DAILY_AVAILABLE_UNITS";

    /**
     * Create a new ServiceCodeValidationLogic object
     */
    public ServiceCodeValidationLogic() {
    }

    /**
     * Filters a list of BillingService objects according to the supplied Demographic data
     * The filter essentially creates a new list with the codes that pertain to the specified
     * Demographic record's age and gender
     *
     * @param svcList BillingService[]
     * @param d       Demographic
     * @return BillingService[]
     */
    public BillingService[] filterServiceCodeList(BillingService[] svcList, Demographic d) {
        ArrayList<BillingService> v = new ArrayList<BillingService>();
        BillingService[] arr = {};
        for (int i = 0; i < svcList.length; i++) {
            String svcCode = svcList[i].getServiceCode();
            if (isValidServiceCode(d, svcCode)) {
                v.add(svcList[i]);
            }
        }
        return v.toArray(arr);
    }

    /**
     * Returns true if the service code is valid for the specified demographic and service code
     *
     * @param d       Demographic
     * @param svcCode String
     * @return boolean
     */
    private boolean isValidServiceCode(Demographic d, String svcCode) {
        AgeValidator age = (AgeValidator) this.getAgeValidator(svcCode, d);
        SexValidator sex = (SexValidator) this.getSexValidator(svcCode, d);
        return (sex.isValid() && age.isValid());
    }

    /**
     * Returns a ServiceCodeValidator for the supplied demographic data and service code
     *
     * @param serviceCode String
     * @param d           Demographic
     * @return ServiceCodeValidator
     */
    public ServiceCodeValidator getSexValidator(String serviceCode, Demographic d) {
        SexValidator v = new SexValidator(serviceCode, d.getSex());
        CtlBillingServiceSexRulesDao dao = SpringUtils.getBean(CtlBillingServiceSexRulesDao.class);

        for (CtlBillingServiceSexRules r : dao.findByServiceCode(serviceCode)) {
            v.setGender("" + r.getGender());
        }

        return v;
    }

    /**
     * Returns a ServiceCodeValidator for the supplied demographic data and service code
     *
     * @param serviceCode String
     * @param d           Demographic
     * @return ServiceCodeValidator
     */
    public ServiceCodeValidator getAgeValidator(String serviceCode, Demographic d) {
        AgeValidator v = new AgeValidator(serviceCode, d.getAgeInYears());
        CtlBillingServiceAgeRulesDao dao = SpringUtils.getBean(CtlBillingServiceAgeRulesDao.class);
        for (CtlBillingServiceAgeRules r : dao.findByServiceCode(serviceCode)) {
            v.setMinAge(r.getMinAge());
            v.setMaxAge(r.getMaxAge());
        }
        return v;
    }

    /**
     * Returns the number of days since a 13050 code was billed to a patient
     * if no record is found the return value is -1
     *
     * @param demoNo String
     * @return int
     */
    public int daysSinceLast13050(String demoNo) {
        return daysSinceCodeLastBilled(demoNo, "13050");
    }

    /**
     * Returns the number of days since a 13050 code was billed to a patient
     * if no record is found the return value is -1
     *
     * @param demoNo String
     * @return int
     */
    public int daysSinceCodeLastBilled(String demoNo, String code) {
        int result = -1;

        BillingmasterDAO dao = SpringUtils.getBean(BillingmasterDAO.class);
        for (Billingmaster bm : dao.findByDemoNoCodeAndStatuses(ConversionUtils.fromIntString(demoNo), code, Arrays.asList(new String[]{"D", "R", "F"}))) {
            int days = ConversionUtils.toDays(System.currentTimeMillis() - bm.getServiceDateAsDate().getTime());
            if (result == -1) {
                result = days;
            } else {
                if (days < result) {
                    result = days;
                }
            }
        }
        return result;
    }

    /**
     * Returns false if a patient has used up all 4 allowable 00120 codes
     * for the current year
     *
     * @param demoNo String
     * @return boolean
     */
    public boolean hasMore00120Codes(String demoNo, String cnslCode, String serviceDate) {
        BillingmasterDAO dao = SpringUtils.getBean(BillingmasterDAO.class);
        int count = dao.findByDemoNoCodeStatusesAndYear(ConversionUtils.fromIntString(demoNo),
                ConversionUtils.fromDateString(serviceDate), cnslCode).size();
        return count < 4;
    }

    /**
     * Returns true if the 14015 code is billable
     * for the specified demographic number.
     * The rules are as follows:
     * A maximum of 6 units may be billed per calendar year
     * A maximum of 4 units may be billed on any given day
     *
     * @param demoNo      String - The uid of the patient
     * @param code        String - The service code to be evaluated
     * @param serviceDate String - The date of service
     * @return 0 if more service units available, 1 if annual max units consumed, 2 if daily max units consumed
     */
    public int getPatientManagementStatus(String demoNo, String code, String serviceDate) {
        int ret = 0;

        //Number of units billed for the current year
        double currentYearCount = 0;

        // Number of units billed for the current day
        double currentDayCount = 0;

        java.util.Date currentDate = new java.util.Date();

        try {
            BillingmasterDAO dao = SpringUtils.getBean(BillingmasterDAO.class);
            for (Billingmaster bm : dao.findByDemoNoCodeStatusesAndYear(ConversionUtils.fromIntString(demoNo), ConversionUtils.fromDateString(serviceDate), code)) {

                String svcDate = bm.getServiceDate();
                String unit = bm.getBillingUnit();

                fmt = new SimpleDateFormat("yyyyMMdd");
                java.util.Date dateSvcDate = fmt.parse(svcDate);
                double dblUnit = UtilMisc.safeParseDouble(unit);
                currentYearCount += dblUnit;

                if (DateUtils.getDifDays(currentDate, dateSvcDate) == 0) {
                    currentDayCount += dblUnit;
                }

                if (currentYearCount >= 6) {
                    ret = YEARLY_CODES_USED;
                    break;
                } else if (currentDayCount >= 4) {
                    ret = DAILY_CODES_USED;
                    break;
                }
            }
        } catch (Exception ex) {
            MiscUtils.getLogger().error("Error", ex);
        }

        return ret;
    }

    /**
     * @param demoNo      String
     * @param code        String
     * @param serviceDate String
     * @return double
     */
    public Map<String, Double> getCountAvailablePatientManagementUnits(String demoNo, String code, String serviceDate) {

        //Number of units billed for the current year
        double currentYearAvailable = 6.0;

        // Number of units billed for the current day
        double currentDayAvailable = 4.0;

        try {
            BillingmasterDAO dao = SpringUtils.getBean(BillingmasterDAO.class);
            for (Billingmaster bm : dao.findByDemoNoCodeStatusesAndYear(ConversionUtils.fromIntString(demoNo), ConversionUtils.fromDateString(serviceDate), code)) {

                String svcDate = bm.getServiceDate();
                String unit = bm.getBillingUnit();
                fmt = new SimpleDateFormat("yyyyMMdd");
                java.util.Date dateSvcDate = fmt.parse(svcDate);
                fmt = new SimpleDateFormat("yy-MM-dd");
                java.util.Date currentDate = fmt.parse(serviceDate);
                double dblUnit = UtilMisc.safeParseDouble(unit);
                currentYearAvailable -= dblUnit;
                if (DateUtils.getDifDays(currentDate, dateSvcDate) == 0) {
                    currentDayAvailable -= dblUnit;
                }
            }
        } catch (Exception ex) {
            MiscUtils.getLogger().error("Error", ex);
        }

        HashMap<String, Double> availableUnits = new HashMap<String, Double>();
        availableUnits.put(DAILY_AVAILABLE_UNITS, Double.valueOf(currentDayAvailable));
        availableUnits.put(ANNUAL_AVAILABLE_UNITS, Double.valueOf(currentYearAvailable));
        return availableUnits;
    }

    /**
     * Returns the date of the last time that Service Code 13050 was billed
     *
     * @param demoNo String
     * @return String
     */
    public String getDateofLast13050(String demoNo) {
        BillingmasterDAO dao = SpringUtils.getBean(BillingmasterDAO.class);
        for (Billingmaster bm : dao.findByDemoNoCodeAndStatuses(ConversionUtils.fromIntString(demoNo), "13050", Arrays.asList(new String[]{"D", "R", "F"}))) {
            return bm.getServiceDate();
        }
        return "";
    }

    public void setDemographicNo(String demographicNo) {
        this.demographicNo = demographicNo;
    }

    public void setServiceCode(String serviceCode) {
        this.serviceCode = serviceCode;
    }

    public String getDemographicNo() {
        return demographicNo;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public ArrayList<String> getPatientDxCodes(String demoNo) {
        ArrayList<String> codes = new ArrayList<String>();
        DxresearchDAO dao = SpringUtils.getBean(DxresearchDAO.class);
        for (Dxresearch r : dao.getByDemographicNo(ConversionUtils.fromIntString(demoNo))) {
            if ('A' == r.getStatus()) {
                codes.add(r.getDxresearchCode());
            }
        }
        return codes;
    }

    public List<String[]> getCDMCodes() {
        BillingCdmServiceCodesDao dao = SpringUtils.getBean(BillingCdmServiceCodesDao.class);
        List<String[]> result = new ArrayList<String[]>();
        for (BillingCdmServiceCodes c : dao.findAll()) {
            result.add(new String[]{c.getCdmCode(), c.getServiceCode()});
        }
        return result;
    }

}
