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

package io.github.carlos_emr.carlos.encounter.data;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.clinic.ClinicData;

/**
 * Provides access to healthcare provider data combined with clinic information.
 * Retrieves provider details (name) along with the associated clinic's contact
 * information (address, phone, fax).
 *
 * @since 2001-01-01
 */
public class EctProviderData {
    /**
     * Inner class representing a healthcare provider with associated clinic details.
     */
    public class Provider {
        String providerNo;
        String surname;
        String firstName;
        String clinicName;
        String clinicAddress;
        String clinicCity;
        String clinicPostal;
        String clinicPhone;
        String clinicFax;

        public String getProviderNo() {
            return providerNo;
        }

        public String getSurname() {
            return surname;
        }

        public String getFirstName() {
            return firstName;
        }

        public String getClinicName() {
            return clinicName;
        }

        public String getClinicAddress() {
            return clinicAddress;
        }

        public String getClinicCity() {
            return clinicCity;
        }

        public String getClinicPostal() {
            return clinicPostal;
        }

        public String getClinicPhone() {
            return clinicPhone;
        }

        public String getClinicFax() {
            return clinicFax;
        }


        public Provider(String providerNo, String surname, String firstName, String clinicName, String clinicAddress, String clinicCity, String clinicPostal, String clinicPhone, String clinicFax) {
            this.providerNo = providerNo;
            this.surname = surname;
            this.firstName = firstName;
            this.clinicName = clinicName;
            this.clinicAddress = clinicAddress;
            this.clinicCity = clinicCity;
            this.clinicPostal = clinicPostal;
            this.clinicPhone = clinicPhone;
            this.clinicFax = clinicFax;
        }

    }

    /**
     * Retrieves provider and clinic data for the given provider number.
     * Prepends "Dr." to the first name if not already present.
     *
     * @param providerNo String the provider number to look up
     * @return Provider the provider with clinic data, or null if not found
     */
    public Provider getProvider(String providerNo) {
        Provider provider = null;
        ProviderDao dao = SpringUtils.getBean(ProviderDao.class);
        io.github.carlos_emr.carlos.commn.model.Provider p = dao.getProvider(providerNo);
        if (p != null) {
            String surname = p.getLastName();
            String firstName = p.getFirstName();
            String comments = p.getComments();
            String selfLearningPasswd = SxmlMisc.getXmlContent(comments, "xml_p_slppassword");

            if (firstName.indexOf("Dr.") < 0) {
                firstName = "Dr. " + firstName;
            }

            ClinicData clinic = new ClinicData();
            clinic.refreshClinicData();
            String clinicName = clinic.getClinicName();
            String clinicAddress = clinic.getClinicAddress();
            String clinicCity = clinic.getClinicCity();
            String clinicPostal = clinic.getClinicPostal();
            String clinicPhone = clinic.getClinicPhone();
            String clinicFax = clinic.getClinicFax();

            provider = new Provider(providerNo, surname, firstName, clinicName, clinicAddress, clinicCity, clinicPostal, clinicPhone, clinicFax);
        }

        return provider;
    }

    @Override
    public String toString() {
        return (ReflectionToStringBuilder.toString(this));
    }

}
