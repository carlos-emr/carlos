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
package ca.openosp.openo.integration.mchcv;

import ca.ontario.health.hcv.*;
import org.apache.commons.lang3.time.DateFormatUtils;
import ca.openosp.openo.integration.ebs.client.ng.EdtClientBuilder;

import javax.xml.datatype.XMLGregorianCalendar;

public interface HCValidator {

    String VALID_RESPONSE_CODE = "51";
    String NOT_VALID_RESPONSE_CODE = "05";

    /**
     * Validates swipe card of demographics
     *
     * @param healthCardNumber number of health card
     * @param versionCode      version code
     */
    HCValidationResult validate(String healthCardNumber, String versionCode);

    /**
     * Validate a single HIN based and a specific service fee code.
     *
     * @param healthCardNumber
     * @param versionCode
     * @param serviceCode
     * @return
     */
    HCValidationResult validate(String healthCardNumber, String versionCode, String serviceCode);

    /**
     * Validates a batch of HIN
     *
     * @param requests list of HcvRequest objects
     * @param local    local
     */
    HcvResults validate(Requests requests, String local) throws Faultexception;

    /**
     * Get the builder and configuration details
     */
    EdtClientBuilder getBuilder();

    /**
     * Create an object that contains a single HCValidation result.
     *
     * @param results
     * @param index
     */
    static HCValidationResult createSingleResult(HcvResults results, int index) {
        HCValidationResult result = new HCValidationResult();
        Person person = results.getResults().get(index);
        result.setAuditUID(results.getAuditUID());
        if (person != null) {
            result.setResponseCode(person.getResponseCode());
            result.setResponseDescription(person.getResponseDescription());
            result.setResponseAction(person.getResponseAction());
            result.setFirstName(person.getFirstName());
            result.setLastName(person.getLastName());
            result.setGender(person.getGender());
            result.setResponseID(person.getResponseID());
            result.setSecondName(person.getSecondName());
            result.setHealthNumber(person.getHealthNumber());
            result.setVersionCode(person.getVersionCode());
            String birthDate = null;
            XMLGregorianCalendar xmlBirthDate = person.getDateOfBirth();
            if (xmlBirthDate != null) {
                birthDate = makeDate(xmlBirthDate.getYear(), xmlBirthDate.getMonth(), xmlBirthDate.getDay());
            }
            result.setBirthDate(birthDate);

            String expiryDate = null;
            XMLGregorianCalendar xmlExpiryDate = person.getExpiryDate();
            if (xmlExpiryDate != null) {
                expiryDate = makeDate(xmlExpiryDate.getYear(), xmlExpiryDate.getMonth(), xmlExpiryDate.getDay());
            }
            result.setExpiryDate(expiryDate);
            result.setFeeServiceDetails(person.getFeeServiceDetails());
        }
        return result;
    }

    static String makeDate(int year, int month, int day) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(year, month - 1, day, 0, 0, 0);
        return DateFormatUtils.format(calendar, "yyyyMMdd");
    }
}
