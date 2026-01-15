//CHECKSTYLE:OFF
/**
 * Copyright (c) 2001-2002. Andromedia. All Rights Reserved.
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
 * Andromedia, to be provided as
 * part of the OSCAR McMaster
 * EMR System
 */


package ca.openosp.openo.billings.ca.bc.administration;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.Misc;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.billing.CA.BC.dao.WcbDao;
import ca.openosp.openo.billing.CA.BC.model.Wcb;
import ca.openosp.openo.commn.dao.BillingDao;
import ca.openosp.openo.commn.dao.BillingServiceDao;
import ca.openosp.openo.commn.model.Billing;
import ca.openosp.openo.commn.model.BillingService;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.managers.DemographicManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.billings.ca.bc.MSP.MSPReconcile;
import ca.openosp.openo.billings.ca.bc.data.BillingHistoryDAO;
import ca.openosp.openo.billings.ca.bc.data.BillingmasterDAO;
import ca.openosp.openo.providers.data.ProviderData;
import ca.openosp.openo.util.ConversionUtils;
import ca.openosp.openo.util.StringUtils;

/*
 * @author Jef King
 * For The Oscar McMaster Project
 * Developed By Andromedia
 * www.andromedia.ca
 */
/*
 * Created on Mar 10, 2004
 */

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class TeleplanCorrectionActionWCB2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    static Logger log = MiscUtils.getLogger();

    private BillingDao billingDao = SpringUtils.getBean(BillingDao.class);
    private WcbDao wcbDao = SpringUtils.getBean(WcbDao.class);
    private DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);

    public String execute()
            throws IOException, ServletException {

        String where = "success";

        //TeleplanCorrectionFormWCB data = (TeleplanCorrectionFormWCB) form;

        try {

            MSPReconcile msp = new MSPReconcile();
            String status = this.getStatus();

            log.debug("adj amount " + this.getAdjAmount());
            if (request.getParameter("settle") != null && request.getParameter("settle").equals("Settle Bill")) {
                status = "S";
            }

            if (!StringUtils.isNullOrEmpty(status)) {
                status = MSPReconcile.NOTSUBMITTED.equals(this.getStatus()) ? MSPReconcile.WCB : status;
                msp.updateBillingStatusWCB(this.getBillingNo(), status, this.getId());
            }
            BillingHistoryDAO dao = new BillingHistoryDAO();
            //If the adjustment amount field isn't empty, create an archive of the adjustment
            if (this.getAdjAmount() != null && !"".equals(this.getAdjAmount())) {
                double dblAdj = Math.abs(Double.valueOf(this.getAdjAmount()).doubleValue());
                //if 1 this adjustment is a debit
                if ("1".equals(this.getAdjType())) {
                    dblAdj = dblAdj * -1.0;
                }
                dao.createBillingHistoryArchive(this.getId(), dblAdj, MSPReconcile.PAYTYPE_IA);
                msp.settleIfBalanced(this.getId());
            } else {
                /**
                 * Ensure that an audit of the currently modified bill is captured
                 */
                dao.createBillingHistoryArchive(this.getId());
            }
            updateUnitValue(this.getBillingUnit(), this.getBillingNo());


            Billing billing = billingDao.find(Integer.parseInt(this.getBillingNo()));
            if (billing != null) {
                billing.setStatus(this.getStatus());
                billingDao.merge(billing);
            }

            String feeItem = this.getW_feeitem();
            String extraFeeItem = this.getW_extrafeeitem();
            String getItemAmt = this.GetFeeItemAmount(feeItem, extraFeeItem);
            log.debug("fee " + feeItem + " extra " + extraFeeItem + " item amt " + getItemAmt);

            Demographic d = demographicManager.getDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), this.getDemographicNumber());

            for (Wcb w : wcbDao.findByBillingNo(Integer.parseInt(this.getBillingNo()))) {
                w.setFormEdited(new Date());
                w.setStatus("O");
                w.setReportType(this.getW_reportype());
                w.setBillAmount(getItemAmt);
                w.setfName(d.getFirstName());
                w.setlName(d.getLastName());
                w.setmName("");
                w.setGender(d.getSex());
                w.setDob(ConversionUtils.fromDateString(d.getYearOfBirth() + "-" + d.getMonthOfBirth() + "-" + d.getDateOfBirth()));
                w.setAddress(d.getAddress());
                w.setCity(d.getCity());
                w.setPostal(d.getPostal());
                w.setArea(Misc.areaCode(d.getPhone2()));
                w.setPhone(Misc.phoneNumber(d.getPhone2()));
                w.setPhn(d.getHin() + d.getVer());
                w.setEmpName(this.getW_empname());
                w.setEmpArea(this.getW_emparea());
                w.setEmpPhone(this.getW_empphone());
                w.setWcbNo(this.getW_wcbno());
                w.setOpAddress(this.getW_opaddress());
                w.setOpCity(this.getW_opcity());
                w.setrPhysician(this.getW_rphysician());
                w.setDuration(Integer.parseInt(this.getW_duration()));
                w.setProblem(this.getW_problem());
                w.setServiceDate(ConversionUtils.fromDateString(this.getW_servicedate()));
                w.setDiagnosis(this.getW_diagnosis());
                w.setIcd9(this.getW_icd9());
                w.setBp(this.getW_bp());
                w.setSide(this.getW_side());
                w.setNoi(this.getW_noi());
                w.setWork(this.getW_work());
                w.setWorkDate(ConversionUtils.fromDateString(this.getW_workdate()));
                w.setClinicInfo(this.getW_clinicinfo());
                w.setCapability(this.getW_capability());
                w.setCapReason(this.getW_capreason());
                w.setEstimate(this.getW_estimate());
                w.setRehab(this.getW_rehab());
                w.setRehabType(this.getW_rehabtype());
                w.setWcbAdbvisor(this.getW_wcbadvisor());
                w.setfTreatment(this.getW_ftreatment());
                w.setEstimateDate(ConversionUtils.fromDateString(this.getW_estimate()));
                w.setToFollow(this.getW_tofollow());
                w.setPracNo(this.getW_pracno());
                w.setDoi(ConversionUtils.fromDateString(this.getW_doi()));
                w.setServiceLocation(this.getServiceLocation());
                w.setFeeItem(this.getW_feeitem());
                w.setExtraFeeItem(this.getW_extrafeeitem());

                wcbDao.merge(w);
            }

            String providerNo = this.getProviderNo();

            ProviderData pd = new ProviderData(providerNo);
            String payee = pd.getBilling_no();
            String pracno = pd.getOhip_no();
            String billingNo = this.getBillingNo();


            for (Wcb wcb : wcbDao.findByBillingNo(Integer.parseInt(billingNo))) {
                //TODO: This has to be eventually changed to a string
                wcb.setProviderNo(Integer.parseInt(providerNo));
                wcb.setPayeeNo(payee);
                wcb.setPracNo(pracno);
                wcbDao.merge(wcb);
            }

        } catch (Exception ex) {
            log.error("WCB Teleplan Correction Query Error: " + ex.getMessage() + " - ", ex);
        }

        String newURL = "/billing/CA/BC/billingTeleplanCorrectionWCB.jsp";
        newURL = newURL + "?billing_no=" + this.getId();
        MiscUtils.getLogger().debug(newURL);

        response.sendRedirect(newURL);
        return NONE;
    }

    private void updateUnitValue(String i, String billingno) {
        BillingmasterDAO dao = (BillingmasterDAO) SpringUtils.getBean(BillingmasterDAO.class);
        dao.updateBillingUnitForBillingNumber(i, Integer.parseInt(billingno));
    }

    private String GetFeeItemAmount(String fee1, String fee2) {
        BillingServiceDao dao = SpringUtils.getBean(BillingServiceDao.class);
        List<BillingService> services = dao.findByServiceCode(fee1);
        for (BillingService service : services)
            return service.getValue();
        return "0.00";
    }

    private String id = "", demographicNumber = "", lastName = "", firstName = "", yearOfBirth = "", monthOfBirth = "", dayOfBirth = "", address = "", city = "", province = "", postal = "", hin = "", practitioner = "", billingUnit = "", billingCode = "", billingAmount = "", serviceLocation = "", date = "", billingNo = "", dataSeqNo = "", w_reportype = "", w_mname = "", w_gender = "", w_doi = "", w_area = "", w_phone = "", w_empname = "", w_emparea = "", w_empphone = "", w_wcbno = "", w_opaddress = "", w_opcity = "", w_rphysician = "", w_duration = "", w_ftreatment = "", w_problem = "", w_servicedate = "", w_diagnosis = "", w_icd9 = "", w_bp = "", w_side = "", w_noi = "", w_work = "", w_workdate = "", w_clinicinfo = "", w_capability = "", w_capreason = "", w_estimate = "", w_rehab = "", w_rehabtype = "", w_estimatedate = "", w_tofollow = "", w_wcbadvisor = "", w_feeitem = "", w_extrafeeitem = "", status = "", formNeeded = "", providerNo = "", w_payeeno = "", w_pracno = "";
    private String xml_status;
    private String adjType;
    private String adjAmount;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDemographicNumber() {
        return demographicNumber;
    }

    public void setDemographicNumber(String demographicNumber) {
        this.demographicNumber = demographicNumber;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getYearOfBirth() {
        return yearOfBirth;
    }

    public void setYearOfBirth(String yearOfBirth) {
        this.yearOfBirth = yearOfBirth;
    }

    public String getMonthOfBirth() {
        return monthOfBirth;
    }

    public void setMonthOfBirth(String monthOfBirth) {
        this.monthOfBirth = monthOfBirth;
    }

    public String getDayOfBirth() {
        return dayOfBirth;
    }

    public void setDayOfBirth(String dayOfBirth) {
        this.dayOfBirth = dayOfBirth;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getPostal() {
        return postal;
    }

    public void setPostal(String postal) {
        this.postal = postal;
    }

    public String getHin() {
        return hin;
    }

    public void setHin(String hin) {
        this.hin = hin;
    }

    public String getPractitioner() {
        return practitioner;
    }

    public void setPractitioner(String practitioner) {
        this.practitioner = practitioner;
    }

    public String getBillingUnit() {
        return billingUnit;
    }

    public void setBillingUnit(String billingUnit) {
        this.billingUnit = billingUnit;
    }

    public String getBillingCode() {
        return billingCode;
    }

    public void setBillingCode(String billingCode) {
        this.billingCode = billingCode;
    }

    public String getBillingAmount() {
        return billingAmount;
    }

    public void setBillingAmount(String billingAmount) {
        this.billingAmount = billingAmount;
    }

    public String getServiceLocation() {
        return serviceLocation;
    }

    public void setServiceLocation(String serviceLocation) {
        this.serviceLocation = serviceLocation;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getBillingNo() {
        return billingNo;
    }

    public void setBillingNo(String billingNo) {
        this.billingNo = billingNo;
    }

    public String getDataSeqNo() {
        return dataSeqNo;
    }

    public void setDataSeqNo(String dataSeqNo) {
        this.dataSeqNo = dataSeqNo;
    }

    public String getW_reportype() {
        return w_reportype;
    }

    public void setW_reportype(String w_reportype) {
        this.w_reportype = w_reportype;
    }

    public String getW_mname() {
        return w_mname;
    }

    public void setW_mname(String w_mname) {
        this.w_mname = w_mname;
    }

    public String getW_gender() {
        return w_gender;
    }

    public void setW_gender(String w_gender) {
        this.w_gender = w_gender;
    }

    public String getW_doi() {
        return w_doi;
    }

    public void setW_doi(String w_doi) {
        this.w_doi = w_doi;
    }

    public String getW_area() {
        return w_area;
    }

    public void setW_area(String w_area) {
        this.w_area = w_area;
    }

    public String getW_phone() {
        return w_phone;
    }

    public void setW_phone(String w_phone) {
        this.w_phone = w_phone;
    }

    public String getW_empname() {
        return w_empname;
    }

    public void setW_empname(String w_empname) {
        this.w_empname = w_empname;
    }

    public String getW_emparea() {
        return w_emparea;
    }

    public void setW_emparea(String w_emparea) {
        this.w_emparea = w_emparea;
    }

    public String getW_empphone() {
        return w_empphone;
    }

    public void setW_empphone(String w_empphone) {
        this.w_empphone = w_empphone;
    }

    public String getW_wcbno() {
        return w_wcbno;
    }

    public void setW_wcbno(String w_wcbno) {
        this.w_wcbno = w_wcbno;
    }

    public String getW_opaddress() {
        return w_opaddress;
    }

    public void setW_opaddress(String w_opaddress) {
        this.w_opaddress = w_opaddress;
    }

    public String getW_opcity() {
        return w_opcity;
    }

    public void setW_opcity(String w_opcity) {
        this.w_opcity = w_opcity;
    }

    public String getW_rphysician() {
        return w_rphysician;
    }

    public void setW_rphysician(String w_rphysician) {
        this.w_rphysician = w_rphysician;
    }

    public String getW_duration() {
        return w_duration;
    }

    public void setW_duration(String w_duration) {
        this.w_duration = w_duration;
    }

    public String getW_ftreatment() {
        return w_ftreatment;
    }

    public void setW_ftreatment(String w_ftreatment) {
        this.w_ftreatment = w_ftreatment;
    }

    public String getW_problem() {
        return w_problem;
    }

    public void setW_problem(String w_problem) {
        this.w_problem = w_problem;
    }

    public String getW_servicedate() {
        return w_servicedate;
    }

    public void setW_servicedate(String w_servicedate) {
        this.w_servicedate = w_servicedate;
    }

    public String getW_diagnosis() {
        return w_diagnosis;
    }

    public void setW_diagnosis(String w_diagnosis) {
        this.w_diagnosis = w_diagnosis;
    }

    public String getW_icd9() {
        return w_icd9;
    }

    public void setW_icd9(String w_icd9) {
        this.w_icd9 = w_icd9;
    }

    public String getW_bp() {
        return w_bp;
    }

    public void setW_bp(String w_bp) {
        this.w_bp = w_bp;
    }

    public String getW_side() {
        return w_side;
    }

    public void setW_side(String w_side) {
        this.w_side = w_side;
    }

    public String getW_noi() {
        return w_noi;
    }

    public void setW_noi(String w_noi) {
        this.w_noi = w_noi;
    }

    public String getW_work() {
        return w_work;
    }

    public void setW_work(String w_work) {
        this.w_work = w_work;
    }

    public String getW_workdate() {
        return w_workdate;
    }

    public void setW_workdate(String w_workdate) {
        this.w_workdate = w_workdate;
    }

    public String getW_clinicinfo() {
        return w_clinicinfo;
    }

    public void setW_clinicinfo(String w_clinicinfo) {
        this.w_clinicinfo = w_clinicinfo;
    }

    public String getW_capability() {
        return w_capability;
    }

    public void setW_capability(String w_capability) {
        this.w_capability = w_capability;
    }

    public String getW_capreason() {
        return w_capreason;
    }

    public void setW_capreason(String w_capreason) {
        this.w_capreason = w_capreason;
    }

    public String getW_estimate() {
        return w_estimate;
    }

    public void setW_estimate(String w_estimate) {
        this.w_estimate = w_estimate;
    }

    public String getW_rehab() {
        return w_rehab;
    }

    public void setW_rehab(String w_rehab) {
        this.w_rehab = w_rehab;
    }

    public String getW_rehabtype() {
        return w_rehabtype;
    }

    public void setW_rehabtype(String w_rehabtype) {
        this.w_rehabtype = w_rehabtype;
    }

    public String getW_estimatedate() {
        return w_estimatedate;
    }

    public void setW_estimatedate(String w_estimatedate) {
        this.w_estimatedate = w_estimatedate;
    }

    public String getW_tofollow() {
        return w_tofollow;
    }

    public void setW_tofollow(String w_tofollow) {
        this.w_tofollow = w_tofollow;
    }

    public String getW_wcbadvisor() {
        return w_wcbadvisor;
    }

    public void setW_wcbadvisor(String w_wcbadvisor) {
        this.w_wcbadvisor = w_wcbadvisor;
    }

    public String getW_feeitem() {
        return w_feeitem;
    }

    public void setW_feeitem(String w_feeitem) {
        this.w_feeitem = w_feeitem;
    }

    public String getW_extrafeeitem() {
        return w_extrafeeitem;
    }

    public void setW_extrafeeitem(String w_extrafeeitem) {
        this.w_extrafeeitem = w_extrafeeitem;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFormNeeded() {
        return formNeeded;
    }

    public void setFormNeeded(String formNeeded) {
        this.formNeeded = formNeeded;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public String getW_payeeno() {
        return w_payeeno;
    }

    public void setW_payeeno(String w_payeeno) {
        this.w_payeeno = w_payeeno;
    }

    public String getW_pracno() {
        return w_pracno;
    }

    public void setW_pracno(String w_pracno) {
        this.w_pracno = w_pracno;
    }

    public String getXml_status() {
        return xml_status;
    }

    public void setXml_status(String xml_status) {
        this.xml_status = xml_status;
    }

    public String getAdjType() {
        return adjType;
    }

    public void setAdjType(String adjType) {
        this.adjType = adjType;
    }

    public String getAdjAmount() {
        return adjAmount;
    }

    public void setAdjAmount(String adjAmount) {
        this.adjAmount = adjAmount;
    }
}
