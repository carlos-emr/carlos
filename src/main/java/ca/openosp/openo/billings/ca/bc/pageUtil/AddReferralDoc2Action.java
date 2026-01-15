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

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.commn.dao.BillingreferralDao;
import ca.openosp.openo.commn.model.Billingreferral;
import ca.openosp.openo.utility.SpringUtils;

/**
 * @author Jay Gallagher
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class AddReferralDoc2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private BillingreferralDao billingReferralDao = (BillingreferralDao) SpringUtils.getBean(BillingreferralDao.class);

    public String execute() {
        String af = SUCCESS;
        
        String id = request.getParameter("id");
        if (id == null) {

            List<Billingreferral> billingReferrals = billingReferralDao.getBillingreferral(referral_no);

            if (billingReferrals == null) {
                Billingreferral billingReferral = new Billingreferral();
                billingReferral.setReferralNo(referral_no);
                billingReferral.setLastName(last_name);
                billingReferral.setFirstName(first_name);
                billingReferral.setAddress1(address1);
                billingReferral.setAddress2(address2);
                billingReferral.setCity(city);
                billingReferral.setProvince(province);
                billingReferral.setPostal(postal);
                billingReferral.setPhone(phone);
                billingReferral.setFax(fax);
                billingReferral.setSpecialty(specialty);
                billingReferral.setCountry("Canada");
                billingReferralDao.updateBillingreferral(billingReferral);
            } else {
                request.setAttribute("Error", "Billing # already in use");
                af = ERROR;
            }

        } else {
            Billingreferral billingReferral = billingReferralDao.getById(Integer.parseInt(id));
            if (billingReferral != null) {
                billingReferral.setReferralNo(referral_no);
                billingReferral.setLastName(last_name);
                billingReferral.setFirstName(first_name);
                billingReferral.setAddress1(address1);
                billingReferral.setAddress2(address2);
                billingReferral.setCity(city);
                billingReferral.setProvince(province);
                billingReferral.setPostal(postal);
                billingReferral.setPhone(phone);
                billingReferral.setFax(fax);
                billingReferralDao.updateBillingreferral(billingReferral);
            }
        }

        //af.setRedirect(true);
        return af;
    }

    public AddReferralDoc2Action() {
    }
    String referral_no;
    String last_name;

    String first_name;
    String specialty;
    String address1;
    String address2;
    String city;
    String province;
    String postal;
    String phone;
    String fax;

    /**
     * Getter for property referral_no.
     *
     * @return Value of property referral_no.
     */
    public String getReferral_no() {
        return referral_no;
    }

    /**
     * Setter for property referral_no.
     *
     * @param referral_no New value of property referral_no.
     */
    public void setReferral_no(String referral_no) {
        this.referral_no = referral_no;
    }

    /**
     * Getter for property last_name.
     *
     * @return Value of property last_name.
     */
    public String getLast_name() {
        return last_name;
    }

    /**
     * Setter for property last_name.
     *
     * @param last_name New value of property last_name.
     */
    public void setLast_name(String last_name) {
        this.last_name = last_name;
    }

    /**
     * Getter for property first_name.
     *
     * @return Value of property first_name.
     */
    public String getFirst_name() {
        return first_name;
    }

    /**
     * Setter for property first_name.
     *
     * @param first_name New value of property first_name.
     */
    public void setFirst_name(String first_name) {
        this.first_name = first_name;
    }

    /**
     * Getter for property specialty.
     *
     * @return Value of property specialty.
     */
    public String getSpecialty() {
        return specialty;
    }

    /**
     * Setter for property specialty.
     *
     * @param specialty New value of property specialty.
     */
    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    /**
     * Getter for property address1.
     *
     * @return Value of property address1.
     */
    public String getAddress1() {
        return address1;
    }

    /**
     * Setter for property address1.
     *
     * @param address1 New value of property address1.
     */
    public void setAddress1(String address1) {
        this.address1 = address1;
    }

    /**
     * Getter for property address2.
     *
     * @return Value of property address2.
     */
    public String getAddress2() {
        return address2;
    }

    /**
     * Setter for property address2.
     *
     * @param address2 New value of property address2.
     */
    public void setAddress2(String address2) {
        this.address2 = address2;
    }

    /**
     * Getter for property city.
     *
     * @return Value of property city.
     */
    public String getCity() {
        return city;
    }

    /**
     * Setter for property city.
     *
     * @param city New value of property city.
     */
    public void setCity(String city) {
        this.city = city;
    }

    /**
     * Getter for property province.
     *
     * @return Value of property province.
     */
    public String getProvince() {
        return province;
    }

    /**
     * Setter for property province.
     *
     * @param province New value of property province.
     */
    public void setProvince(String province) {
        this.province = province;
    }

    /**
     * Getter for property postal.
     *
     * @return Value of property postal.
     */
    public String getPostal() {
        return postal;
    }

    /**
     * Setter for property postal.
     *
     * @param postal New value of property postal.
     */
    public void setPostal(String postal) {
        this.postal = postal;
    }

    /**
     * Getter for property phone.
     *
     * @return Value of property phone.
     */
    public String getPhone() {
        return phone;
    }

    /**
     * Setter for property phone.
     *
     * @param phone New value of property phone.
     */
    public void setPhone(String phone) {
        this.phone = phone;
    }

    /**
     * Getter for property fax.
     *
     * @return Value of property fax.
     */
    public String getFax() {
        return fax;
    }

    /**
     * Setter for property fax.
     *
     * @param fax New value of property fax.
     */
    public void setFax(String fax) {
        this.fax = fax;
    }
}
