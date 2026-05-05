/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.commn.model;

import io.github.carlos_emr.carlos.utility.MiscUtils;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Comparator;
import java.util.Date;

/**
 * This is the object class that relates to the provider table. Any customizations belong here.
 */
@Entity
@Table(name = "provider")
@jakarta.persistence.Access(jakarta.persistence.AccessType.PROPERTY)
public class Provider extends AbstractModel<String> implements Comparable<Provider> {

    public static final String SYSTEM_PROVIDER_NO = "-1";
    private int hashCode = Integer.MIN_VALUE; // primary key
    private String providerNo;
    private String comments;
    private String phone;
    private String billingNo;
    private String workPhone;
    private String address;
    private String team;

    /**
     * "1"=active "0"=inactive
     */
    private String status;

    private String lastName;
    private String providerType;
    private String sex;
    private String ohipNo;
    private String specialty;
    private Date dob;
    private String hsoNo;
    private String providerActivity;
    private String firstName;
    private String rmaNo;
    private Date signedConfidentiality;
    private String practitionerNo;
    private String practitionerNoType;
    private String email;
    private String title;
    private String lastUpdateUser;
    private Date lastUpdateDate = new Date();
    private String supervisor;
    @jakarta.persistence.Column(name = "practitionerNo", length = 20)

    public String getPractitionerNo() {
        return practitionerNo;
    }

    public void setPractitionerNo(String practitionerNo) {
        this.practitionerNo = practitionerNo;
    }

    @jakarta.persistence.Column(name = "practitionerNoType", length = 255)


    public String getPractitionerNoType() {
        return practitionerNoType;
    }

    public void setPractitionerNoType(String practitionerNoType) {
        this.practitionerNoType = practitionerNoType;
    }

    // constructors
    public Provider() {
    }

    /**
     * Constructor for primary key
     */
    public Provider(String providerNo) {
        this.setProviderNo(providerNo);
    }

    /**
     * Constructor for required fields
     */
    public Provider(String providerNo, String lastName, String providerType, String sex, String specialty, String firstName) {

        this.setProviderNo(providerNo);
        this.setLastName(lastName);
        this.setProviderType(providerType);
        this.setSex(sex);
        this.setSpecialty(specialty);
        this.setFirstName(firstName);
    }

    public Provider(Provider provider) {
        providerNo = provider.providerNo;
        comments = provider.comments;
        phone = provider.phone;
        billingNo = provider.billingNo;
        workPhone = provider.workPhone;
        address = provider.address;
        team = provider.team;
        status = provider.status;
        lastName = provider.lastName;
        providerType = provider.providerType;
        sex = provider.sex;
        ohipNo = provider.ohipNo;
        specialty = provider.specialty;
        dob = provider.dob;
        hsoNo = provider.hsoNo;
        providerActivity = provider.providerActivity;
        firstName = provider.firstName;
        rmaNo = provider.rmaNo;
        signedConfidentiality = provider.signedConfidentiality;
        practitionerNo = provider.practitionerNo;
        practitionerNoType = provider.practitionerNoType;
        email = provider.email;
        title = provider.title;
        lastUpdateUser = provider.lastUpdateUser;
        lastUpdateDate = provider.lastUpdateDate;
        supervisor = provider.supervisor;

    }
    @jakarta.persistence.Transient

    public String getFormattedName() {
        return getLastName() + ", " + getFirstName();
    }
    @jakarta.persistence.Transient

    public String getFullName() {
        return getFirstName() + " " + getLastName();
    }
    @jakarta.persistence.Id

    @jakarta.persistence.Column(name = "provider_no")

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
        this.hashCode = Integer.MIN_VALUE;
    }
    @jakarta.persistence.Column(name = "comments")

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }
    @jakarta.persistence.Column(name = "phone", length = 20)

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
    @jakarta.persistence.Column(name = "billing_no", length = 20)

    public String getBillingNo() {
        return billingNo;
    }

    public void setBillingNo(String billingNo) {
        this.billingNo = billingNo;
    }
    @jakarta.persistence.Column(name = "work_phone", length = 50)

    public String getWorkPhone() {
        return workPhone;
    }

    public void setWorkPhone(String workPhone) {
        this.workPhone = workPhone;
    }
    @jakarta.persistence.Column(name = "address", length = 40)

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
    @jakarta.persistence.Column(name = "team", length = 20)

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }
    @jakarta.persistence.Column(name = "status", length = 1)

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    @jakarta.persistence.Column(name = "last_name", length = 30, nullable = false)

    public String getLastName() {
        // sanitize extra white space.  There are lots of areas in the
        // code that concat and delimit full names by a single space.
        if (lastName != null) {
            lastName = lastName.trim();
        }
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    /**
     * @deprecated no longer is use 2010-04-23, marked for future removal
     */
    @Deprecated
    @jakarta.persistence.Column(name = "provider_type", length = 15, nullable = false)
    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }
    @jakarta.persistence.Column(name = "sex", length = 1, nullable = false)

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }
    @jakarta.persistence.Column(name = "ohip_no", length = 20)

    public String getOhipNo() {
        return ohipNo;
    }

    public void setOhipNo(String ohipNo) {
        this.ohipNo = ohipNo;
    }
    @jakarta.persistence.Column(name = "specialty", length = 20, nullable = false)

    public String getSpecialty() {
        return specialty;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }
    @jakarta.persistence.Temporal(jakarta.persistence.TemporalType.DATE)

    @jakarta.persistence.Column(name = "dob")

    public java.util.Date getDob() {
        return dob;
    }

    public void setDob(java.util.Date dob) {
        this.dob = dob;
    }
    @jakarta.persistence.Column(name = "hso_no", length = 10)

    public String getHsoNo() {
        return hsoNo;
    }

    public void setHsoNo(String hsoNo) {
        this.hsoNo = hsoNo;
    }
    @jakarta.persistence.Column(name = "provider_activity", length = 3)

    public String getProviderActivity() {
        return providerActivity;
    }

    public void setProviderActivity(String providerActivity) {
        this.providerActivity = providerActivity;
    }
    @jakarta.persistence.Column(name = "first_name", length = 30, nullable = false)

    public String getFirstName() {
        // sanitize extra white space.  There are lots of areas in the
        // code that concat and delimit full names by a single space.
        if (firstName != null) {
            firstName = firstName.trim();
        }
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    @jakarta.persistence.Column(name = "rma_no", length = 20)

    public String getRmaNo() {
        return rmaNo;
    }

    public void setRmaNo(String rmaNo) {
        this.rmaNo = rmaNo;
    }
    @jakarta.persistence.Column(name = "email")

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
    @jakarta.persistence.Column(name = "signed_confidentiality")

    public Date getSignedConfidentiality() {
        return this.signedConfidentiality;
    }

    public void setSignedConfidentiality(Date signedConfidentiality) {
        this.signedConfidentiality = signedConfidentiality;
    }
    @jakarta.persistence.Column(name = "title")

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @jakarta.persistence.Column(name = "lastUpdateUser")


    public String getLastUpdateUser() {
        return lastUpdateUser;
    }

    public void setLastUpdateUser(String lastUpdateUser) {
        this.lastUpdateUser = lastUpdateUser;
    }
    @jakarta.persistence.Temporal(jakarta.persistence.TemporalType.TIMESTAMP)

    @jakarta.persistence.Column(name = "lastUpdateDate")

    public Date getLastUpdateDate() {
        return lastUpdateDate;
    }
    @jakarta.persistence.Column(name = "supervisor")

    public String getSupervisor() {
        return this.supervisor;
    }

    public void setLastUpdateDate(Date lastUpdateDate) {
        this.lastUpdateDate = lastUpdateDate;
    }

    public void setSupervisor(String supervisor) {
        this.supervisor = supervisor;
    }

    public ComparatorName ComparatorName() {
        return new ComparatorName();
    }

    @Override
    public boolean equals(Object provider) {

        if (this.getProviderNo() == null) {
            // do nothing, warn everyone.
            MiscUtils.getLogger().warn(OBJECT_NOT_YET_PERISTED, new Exception());
        }

        return (provider != null
                && provider instanceof Provider
                && this.getProviderNo() != null
                && this.getProviderNo().equals(((Provider) provider).providerNo));

    }

    @Override
    public int hashCode() {
        if (Integer.MIN_VALUE == this.hashCode) {
            if (null == this.getProviderNo()) {
                // do nothing, warn everyone.
                MiscUtils.getLogger().warn(OBJECT_NOT_YET_PERISTED, new Exception());
            } else {
                String hashStr = this.getClass().getName() + ":" + this.getProviderNo().hashCode();
                this.hashCode = hashStr.hashCode();
            }
        }
        return this.hashCode;
    }

    public class ComparatorName implements Comparator<Provider>, Serializable {

        public int compare(Provider o1, Provider o2) {
            Provider bp1 = o1;
            Provider bp2 = o2;
            String lhs = bp1.getLastName() + bp1.getFirstName();
            String rhs = bp2.getLastName() + bp2.getFirstName();

            return lhs.compareTo(rhs);
        }
    }

    public int compareTo(Provider o) {
        if (providerNo == null) return (0);
        return (providerNo.compareTo(o.providerNo));
    }

    @Override
    @jakarta.persistence.Transient
    public String getId() {
        return providerNo;
    }

}
