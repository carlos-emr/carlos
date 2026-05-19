/**
 * Copyright (c) 2005, 2009 IBM Corporation and others.
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
 * Contributors:
 * <Quatro Group Software Systems inc.>  <OSCAR Team>
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.model.security;

import java.util.Date;

/**
 * Provider entity.
 *
 * @author JZhang
 */


@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "provider")
@jakarta.persistence.Access(jakarta.persistence.AccessType.PROPERTY)
public class SecProvider implements java.io.Serializable {

    // Fields

    private String providerNo;
    private String lastName;
    private String firstName;
    private String providerType;
    private String specialty;
    private String team;
    private String sex;
    private Date dob;
    private String address;
    private String phone;
    private String workPhone;
    private String ohipNo;
    private String rmaNo;
    private String billingNo;
    private String hsoNo;
    private String status;
    private String comments;
    private String providerActivity;

    private String init;
    private String title;
    private String jobTitle;
    private String email;


    // Constructors

    /**
     * default constructor
     */
    public SecProvider() {
    }

    /**
     * full constructor
     */
    public SecProvider(String lastName, String firstName, String providerType,
                       String specialty, String team, String sex, Date dob,
                       String address, String phone, String workPhone, String ohipNo,
                       String rmaNo, String billingNo, String hsoNo, String status,
                       String comments, String providerActivity) {
        this.lastName = lastName;
        this.firstName = firstName;
        this.providerType = providerType;
        this.specialty = specialty;
        this.team = team;
        this.sex = sex;
        this.dob = dob;
        this.address = address;
        this.phone = phone;
        this.workPhone = workPhone;
        this.ohipNo = ohipNo;
        this.rmaNo = rmaNo;
        this.billingNo = billingNo;
        this.hsoNo = hsoNo;
        this.status = status;
        this.comments = comments;
        this.providerActivity = providerActivity;
    }

    // Property accessors
    @jakarta.persistence.Id

    @jakarta.persistence.Column(name = "provider_no", length = 6)

    public String getProviderNo() {
        return this.providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }
    @jakarta.persistence.Column(name = "last_name", length = 30)

    public String getLastName() {
        return this.lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
    @jakarta.persistence.Column(name = "first_name", length = 30)

    public String getFirstName() {
        return this.firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    @jakarta.persistence.Column(name = "provider_type", length = 15)

    public String getProviderType() {
        return this.providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }
    @jakarta.persistence.Column(name = "specialty", length = 20)

    public String getSpecialty() {
        return this.specialty;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }
    @jakarta.persistence.Column(name = "team", length = 20)

    public String getTeam() {
        return this.team;
    }

    public void setTeam(String team) {
        this.team = team;
    }
    @jakarta.persistence.Column(name = "sex", length = 1)

    public String getSex() {
        return this.sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }
    @jakarta.persistence.Column(name = "dob")

    public Date getDob() {
        return this.dob;
    }

    public void setDob(Date dob) {
        this.dob = dob;
    }
    @jakarta.persistence.Column(name = "address", length = 40)

    public String getAddress() {
        return this.address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
    @jakarta.persistence.Column(name = "phone", length = 20)

    public String getPhone() {
        return this.phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
    @jakarta.persistence.Column(name = "work_phone", length = 50)

    public String getWorkPhone() {
        return this.workPhone;
    }

    public void setWorkPhone(String workPhone) {
        this.workPhone = workPhone;
    }
    @jakarta.persistence.Column(name = "ohip_no", length = 20)

    public String getOhipNo() {
        return this.ohipNo;
    }

    public void setOhipNo(String ohipNo) {
        this.ohipNo = ohipNo;
    }
    @jakarta.persistence.Column(name = "rma_no", length = 20)

    public String getRmaNo() {
        return this.rmaNo;
    }

    public void setRmaNo(String rmaNo) {
        this.rmaNo = rmaNo;
    }
    @jakarta.persistence.Column(name = "billing_no", length = 20)

    public String getBillingNo() {
        return this.billingNo;
    }

    public void setBillingNo(String billingNo) {
        this.billingNo = billingNo;
    }
    @jakarta.persistence.Column(name = "hso_no", length = 10)

    public String getHsoNo() {
        return this.hsoNo;
    }

    public void setHsoNo(String hsoNo) {
        this.hsoNo = hsoNo;
    }
    @jakarta.persistence.Column(name = "status", length = 1)

    public String getStatus() {
        return this.status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    @jakarta.persistence.Column(name = "comments", length = 4000)

    public String getComments() {
        return this.comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }
    @jakarta.persistence.Column(name = "provider_activity", length = 3)

    public String getProviderActivity() {
        return this.providerActivity;
    }

    public void setProviderActivity(String providerActivity) {
        this.providerActivity = providerActivity;
    }
    @jakarta.persistence.Column(name = "email", length = 320)

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
    @jakarta.persistence.Column(name = "init", length = 10)

    public String getInit() {
        return init;
    }

    public void setInit(String init) {
        this.init = init;
    }
    @jakarta.persistence.Column(name = "job_title", length = 100)

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }
    @jakarta.persistence.Column(name = "title", length = 20)

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
    @jakarta.persistence.Transient

    public String getFormattedName() {
        return getLastName() + ", " + getFirstName();
    }
    @jakarta.persistence.Transient

    public String getFullName() {
        return getFirstName() + " " + getLastName();
    }

}
