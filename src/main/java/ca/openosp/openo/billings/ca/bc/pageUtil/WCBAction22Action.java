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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.utility.MiscUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import ca.openosp.openo.entities.WCB;
import ca.openosp.openo.billings.ca.bc.data.BillingmasterDAO;
import ca.openosp.openo.util.UtilDateUtilities;

/**
 * These Actions Handle of all the interactions with WCB Billing
 * <p>
 * Save   -- this will always save a new form and not update an new one.
 * ================
 * Called from the WCB form.  Could be an update or a save.
 * Possible Forwarding mappings
 * - Billing form ( will need to pass
 * - Just Save
 * - Save and Close.
 * - Print ( this is just an hl7 print right now)
 *
 * @author jaygallagher
 */
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class WCBAction22Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    public String execute() throws Exception {
        return save();
    }

    public String save() throws Exception {
        if (request.getSession().getAttribute("user") == null) {
            return "Logout";
        }
        MiscUtils.getLogger().debug("In WCBAction22Action Jackson");

        //Get rid of this
        WebApplicationContext ctx = WebApplicationContextUtils.getRequiredWebApplicationContext(request.getSession().getServletContext());
        BillingmasterDAO billingmasterDAO = (BillingmasterDAO) ctx.getBean(BillingmasterDAO.class);

        //save new wcb form
        WCB wcb = this.getWCB();
        wcb.setProvider_no((String) request.getSession().getAttribute("user"));
        if (wcb.getFormCreated() == null) {
            wcb.setFormCreated(new Date());
        }
        if (wcb.getFormEdited() == null) {
            wcb.setFormEdited(new Date());
        }
        wcb.setStatus("W");
        billingmasterDAO.save(wcb);


        if (request.getParameter("saveAndClose") != null) {
            MiscUtils.getLogger().debug("QUITINN");
            request.setAttribute("WCBFormId", wcb.getId());
            return "saveAndClose";
        }

        if (request.getParameter("saveandbill") != null) {
            request.setAttribute("WCBFormId", wcb.getId());
            request.setAttribute("icd9", wcb.getW_icd9());
            request.setAttribute("serviceDate", UtilDateUtilities.DateToString(wcb.getW_servicedate()));
            List list = new ArrayList();
            String feeitem = wcb.getW_feeitem();
            String xfeeitem = wcb.getW_extrafeeitem();

            if (feeitem != null && !feeitem.trim().equals("")) {
                list.add(feeitem);
            }
            if (xfeeitem != null && !xfeeitem.trim().equals("")) {
                list.add(xfeeitem);
            }
            request.setAttribute("billingcodes", list);


            return "newbill";
        }


        MiscUtils.getLogger().debug("OVER AND OUT.");
        response.sendRedirect(request.getContextPath() + "/billing/CA/BC/formwcb.jsp");
        return NONE;
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
    public WCB getWCB() {
        WCB wcb = new WCB();


        wcb.setDemographic_no(Integer.parseInt(demographic_no));

        wcb.setProvider_no(providerNo);

        //wcb.set formCreated);
        //wcb.set formEdited);
        wcb.setW_reporttype(w_reportype);
        wcb.setW_fname(w_fname);
        wcb.setW_lname(w_lname);
        wcb.setW_mname(w_mname);
        wcb.setW_gender(w_gender);
        wcb.setW_dob(UtilDateUtilities.StringToDate(ddate(w_dob)));
        wcb.setW_doi(UtilDateUtilities.StringToDate(ddate(w_doi)));
        wcb.setW_address(w_address);
        wcb.setW_city(w_city);
        wcb.setW_postal(w_postal);
        wcb.setW_area(w_area);
        wcb.setW_phone(w_phone);
        wcb.setW_phn(w_phn);
        wcb.setW_empname(w_empname);
        wcb.setW_emparea(w_emparea);
        wcb.setW_empphone(w_empphone);
        wcb.setW_wcbno(w_wcbno);
        wcb.setW_opaddress(w_opaddress);
        wcb.setW_opcity(w_opcity);
        wcb.setW_rphysician(w_rphysician);  // <!___what happens here
        wcb.setW_duration(Integer.parseInt(w_duration)); // <!___what happens here
        wcb.setW_ftreatment(w_ftreatment);
        wcb.setW_problem(w_problem);
        wcb.setW_servicedate(UtilDateUtilities.StringToDate(ddate(w_servicedate)));
        wcb.setW_diagnosis(w_diagnosis);
        wcb.setW_icd9(w_icd9);
        wcb.setW_bp(w_bp);
        wcb.setW_side(w_side);
        wcb.setW_noi(w_noi);
        wcb.setW_work(w_work);//="Y"
        wcb.setW_workdate(UtilDateUtilities.StringToDate(ddate(w_workdate)));
        wcb.setW_clinicinfo(w_clinicinfo);
        wcb.setW_capability(w_capability);//="Y"
        wcb.setW_capreason(w_capreason);
        wcb.setW_estimate(w_estimate);//="0"
        wcb.setW_rehab(w_rehab);//="N"
        wcb.setW_rehabtype(w_rehabtype);

        MiscUtils.getLogger().debug("ESTMATE DATE =" + w_estimatedate + "--" + UtilDateUtilities.StringToDate(ddate(w_estimatedate)));
        wcb.setW_estimatedate(UtilDateUtilities.StringToDate(ddate(w_estimatedate)));
        wcb.setW_tofollow(w_tofollow);//="N"
        wcb.setW_payeeno(w_payeeno);
        wcb.setW_pracno(w_pracno);
        ////wcb.setW_pracname( w_pracname);
        wcb.setW_wcbadvisor(w_wcbadvisor); //="N"
        wcb.setW_feeitem(w_feeitem); //--
        wcb.setW_extrafeeitem(w_extrafeeitem); //--
        wcb.setW_servicelocation(w_servicelocation); //--
        //wcb.setW_formNeeded(formNeeded);
        //private List injuryLocations;


        return wcb;
    }

    String ddate(String s) {
        if (s == null) {
            return "0001-01-01";
        }
        return s;
    }
}
