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

package ca.openosp.openo.billings.ca.bc.pageUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.managers.DemographicManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import ca.openosp.Misc;
import ca.openosp.openo.entities.WCB;
import ca.openosp.openo.billings.ca.bc.data.BillingFormData;
import ca.openosp.openo.billings.ca.bc.data.BillingmasterDAO;
import ca.openosp.openo.util.SqlUtils;

/**
 * <p>Title:ViewWCB2Action </p>
 *
 * <p>Description: Coordinates data retrieval and configuration parameters for rendering either</p>
 * <p>a new or existing WCB form
 *
 * @author Joel Legris
 * @version 1.0
 */
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.util.UtilDateUtilities;

public class ViewWCB2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();



    DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);

    public String execute() {
        String demoNo = request.getParameter("demographic_no");
        String providerNo = request.getParameter("provNo");
        BillingFormData data = new BillingFormData();
        String formId = request.getParameter("formId");
        this.setWcbFormId(formId);
        //if the formId is zero, this is a new form

        if ("0".equals(formId)) {
            this.setFormNeeded("1");
            Demographic demographic = demographicManager.getDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), demoNo);

            if (demographic != null) {
                this.setDemographic(demographic.getDemographicNo().toString());
                this.setW_fname(demographic.getFirstName());
                this.setW_lname(demographic.getLastName());
                this.setW_gender(demographic.getSex());
                if (demographic.getPhone() != null && demographic.getPhone().length() > 0) {
                    this.setW_phone(Misc.phoneNumber(demographic.getPhone().replaceAll("-", "")));
                    this.setW_area(Misc.areaCode(demographic.getPhone()));
                }

                String[] pc = demographic.getPostal().split(" ");

                String postal = "";
                for (int i = 0; i < pc.length; i++) {  // DOES THIS JUST REMOVE SPACES???
                    postal += pc[i];
                }
                this.setW_postal(postal);

                this.setW_phn(demographic.getHin());
                String seperator = "-";
                String dob = demographic.getYearOfBirth() + seperator +
                        demographic.getMonthOfBirth() + seperator + demographic.getDateOfBirth();
                this.setW_dob(dob);
                this.setW_address(demographic.getAddress());
                this.setW_opcity(demographic.getCity());
                this.setW_city(demographic.getCity());
                this.setInjuryLocations(data.getInjuryLocationList());

                //Retrieve provider ohip number and payee number

                List lstResults = SqlUtils.getQueryResultsList("select ohip_no,billing_no from provider where provider_no = " + providerNo);
                if (lstResults != null) {
                    String[] providerData = (String[]) lstResults.get(0);
                    this.setW_pracno(providerData[0]);
                    this.setW_payeeno(providerData[1]);
                }

                this.setProviderNo(providerNo);

                SimpleDateFormat fm = new SimpleDateFormat("yyyy-MM-dd");
                String fmtStrDate = fm.format(new Date());
                this.setW_servicedate(fmtStrDate);
            }
        }
        //If the incoming request is for an existing form, retrieve the WCB form data
        //for readonly viewing on the WCB Form Screen
        else {
            request.setAttribute("readonly", "true");
            WebApplicationContext ctx = WebApplicationContextUtils.getRequiredWebApplicationContext(request.getSession().getServletContext());
            BillingmasterDAO billingmasterDAO = (BillingmasterDAO) ctx.getBean(BillingmasterDAO.class);

            this.setWCBForms(billingmasterDAO.getWCBForm(formId));
        }
        return SUCCESS;
    }
    private String demographic_no;

    private String providerNo;

    private String formCreated;
    private String formEdited;
    private String w_reportype = "F";
    private String w_fname;
    private String w_lname;
    private String w_mname;
    private String w_gender;
    private String w_dob;
    private String w_doi;
    private String w_address;
    private String w_city;
    private String w_postal;
    private String w_area;
    private String w_phone;
    private String w_phn;
    private String w_empname;
    private String w_emparea;
    private String w_empphone;
    private String w_wcbno;
    private String w_opaddress;
    private String w_opcity;
    private String w_rphysician = "Y";
    private String w_duration = "1";
    private String w_ftreatment;
    private String w_problem;
    private String w_servicedate;
    private String w_diagnosis;
    private String w_icd9;
    private String w_bp;
    private String w_side;
    private String w_noi;
    private String w_work = "Y";
    private String w_workdate;
    private String w_clinicinfo;
    private String w_capability = "Y";
    private String w_capreason;
    private String w_estimate = "0";
    private String w_rehab = "N";
    private String w_rehabtype;
    private String w_estimatedate;
    private String w_tofollow = "N";
    private String w_payeeno;
    private String w_pracno;
    private String w_pracname;
    private String w_wcbadvisor = "N";
    private String w_feeitem; //--
    private String w_extrafeeitem; //--
    private String w_servicelocation; //--
    private String formNeeded;
    private List injuryLocations;

    /**
     * @todo Database code should be moved out of the model and into an appropriate persistence class
     */
    private String demographic;
    private String w_demographic;
    private String w_providerno;
    private boolean notBilled;
    private String wcbFormId;
    private boolean doValidate;

    public String getDemographic_no() {
        return demographic_no;
    }

    public void setDemographic_no(String demographic_no) {
        this.demographic_no = demographic_no;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public String getFormCreated() {
        return formCreated;
    }

    public void setFormCreated(String formCreated) {
        this.formCreated = formCreated;
    }

    public String getFormEdited() {
        return formEdited;
    }

    public void setFormEdited(String formEdited) {
        this.formEdited = formEdited;
    }

    public String getW_reportype() {
        return w_reportype;
    }

    public void setW_reportype(String w_reportype) {
        this.w_reportype = w_reportype;
    }

    public String getW_fname() {
        return w_fname;
    }

    public void setW_fname(String w_fname) {
        this.w_fname = w_fname;
    }

    public String getW_lname() {
        return w_lname;
    }

    public void setW_lname(String w_lname) {
        this.w_lname = w_lname;
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

    public String getW_dob() {
        return w_dob;
    }

    public void setW_dob(String w_dob) {
        this.w_dob = w_dob;
    }

    public String getW_doi() {
        return w_doi;
    }

    public void setW_doi(String w_doi) {
        this.w_doi = w_doi;
    }

    public String getW_address() {
        return w_address;
    }

    public void setW_address(String w_address) {
        this.w_address = w_address;
    }

    public String getW_city() {
        return w_city;
    }

    public void setW_city(String w_city) {
        this.w_city = w_city;
    }

    public String getW_postal() {
        return w_postal;
    }

    public void setW_postal(String w_postal) {
        this.w_postal = w_postal;
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

    public String getW_phn() {
        return w_phn;
    }

    public void setW_phn(String w_phn) {
        this.w_phn = w_phn;
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

    public String getW_pracname() {
        return w_pracname;
    }

    public void setW_pracname(String w_pracname) {
        this.w_pracname = w_pracname;
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

    public String getW_servicelocation() {
        return w_servicelocation;
    }

    public void setW_servicelocation(String w_servicelocation) {
        this.w_servicelocation = w_servicelocation;
    }

    public String getFormNeeded() {
        return formNeeded;
    }

    public void setFormNeeded(String formNeeded) {
        this.formNeeded = formNeeded;
    }

    public List getInjuryLocations() {
        return injuryLocations;
    }

    public void setInjuryLocations(List injuryLocations) {
        this.injuryLocations = injuryLocations;
    }

    public String getDemographic() {
        return demographic;
    }

    public void setDemographic(String demographic) {
        this.demographic = demographic;
    }

    public String getW_demographic() {
        return w_demographic;
    }

    public void setW_demographic(String w_demographic) {
        this.w_demographic = w_demographic;
    }

    public String getW_providerno() {
        return w_providerno;
    }

    public void setW_providerno(String w_providerno) {
        this.w_providerno = w_providerno;
    }

    public boolean isNotBilled() {
        return notBilled;
    }

    public void setNotBilled(boolean notBilled) {
        this.notBilled = notBilled;
    }

    public String getWcbFormId() {
        return wcbFormId;
    }

    public void setWcbFormId(String wcbFormId) {
        this.wcbFormId = wcbFormId;
    }

    public boolean isDoValidate() {
        return doValidate;
    }

    public void setDoValidate(boolean doValidate) {
        this.doValidate = doValidate;
    }

    public void setWCBForms(WCB wcb) {

        demographic_no = "" + wcb.getDemographic_no();
        providerNo = wcb.getProvider_no();
        formCreated = UtilDateUtilities.DateToString(wcb.getFormCreated());
        formEdited = UtilDateUtilities.DateToString(wcb.getFormEdited());
        w_reportype = wcb.getW_reporttype();
        w_fname = wcb.getW_fname();
        w_lname = wcb.getW_lname();
        w_mname = wcb.getW_mname();
        w_gender = wcb.getW_gender();
        w_dob = UtilDateUtilities.DateToString(wcb.getW_dob());
        w_doi = UtilDateUtilities.DateToString(wcb.getW_doi());
        w_address = wcb.getW_address();
        w_city = wcb.getW_city();
        w_postal = wcb.getW_postal();
        w_area = wcb.getW_area();
        w_phone = wcb.getW_phone();
        w_phn = wcb.getW_phn();
        w_empname = wcb.getW_empname();
        w_emparea = wcb.getW_emparea();
        w_empphone = wcb.getW_empphone();
        w_wcbno = wcb.getW_wcbno();
        w_opaddress = wcb.getW_opaddress();
        w_opcity = wcb.getW_opcity();
        w_rphysician = wcb.getW_rphysician();
        w_duration = "" + wcb.getW_duration();
        w_ftreatment = wcb.getW_ftreatment();
        w_problem = wcb.getW_problem();
        w_servicedate = UtilDateUtilities.DateToString(wcb.getW_servicedate());
        w_diagnosis = wcb.getW_diagnosis();
        w_icd9 = wcb.getW_icd9();
        w_bp = wcb.getW_bp();
        w_side = wcb.getW_side();
        w_noi = wcb.getW_noi();
        w_work = wcb.getW_work();
        w_workdate = UtilDateUtilities.DateToString(wcb.getW_workdate());
        w_clinicinfo = wcb.getW_clinicinfo();
        w_capreason = wcb.getW_capreason();
        w_capability = wcb.getW_capability();
        w_estimate = wcb.getW_estimate();
        w_rehab = wcb.getW_rehab();
        w_rehabtype = wcb.getW_rehabtype();
        w_estimatedate = UtilDateUtilities.DateToString(wcb.getW_estimatedate());
        w_tofollow = wcb.getW_tofollow();
        w_payeeno = wcb.getW_payeeno();
        w_pracno = wcb.getW_pracno();
        //w_pracname= result.get("w_pracname").asText();
        w_wcbadvisor = wcb.getW_wcbadvisor();
        w_feeitem = wcb.getW_feeitem();
        w_extrafeeitem = wcb.getW_extrafeeitem();
        w_servicelocation = wcb.getW_servicelocation();
        int intFormNeeded = wcb.getFormNeeded();
        this.formNeeded = intFormNeeded == 1 ? "true" : "false";

    }
}
