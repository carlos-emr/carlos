package io.github.carlos_emr.carlos.integration.fhir.utils;

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

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.dstu3.model.Address;
import org.hl7.fhir.dstu3.model.ContactPoint;
import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.StringType;
import org.hl7.fhir.dstu3.model.codesystems.ContactPointSystem;
import org.hl7.fhir.dstu3.model.codesystems.IdentifierUse;

/**
 * Utility methods for extracting data from FHIR DSTU3 resource types.
 *
 * <p>Provides helper methods for converting FHIR Address lines to strings,
 * extracting phone/fax/email from ContactPoint lists, and retrieving
 * identifier values by use type.</p>
 *
 * @since 2026-03-17
 */
public final class FhirUtils {

    /**
     * Converts a list of FHIR Address objects to a list of concatenated street line strings.
     *
     * @param addresses the list of FHIR Address objects
     * @return List of street address strings, or {@code null} if the list is empty
     */
    public static final List<String> fhirAddressLineToString(List<Address> addresses) {
        List<String> addressList = null;
        for (Address address : addresses) {
            if (addressList == null) {
                addressList = new ArrayList<String>();
            }
            addressList.add(fhirAddressLineToString(address));
        }

        return addressList;
    }

    /**
     * Concatenates a FHIR Address's line components into a single string.
     *
     * @param address the FHIR Address object
     * @return String the concatenated street address lines
     */
    public static final String fhirAddressLineToString(Address address) {
        List<StringType> addressLine = address.getLine();
        String street = "";
        for (StringType line : addressLine) {
            street += line.asStringValue() + " ";
        }
        return street;
    }

    /**
     * Extracts the fax number from a list of FHIR ContactPoint objects.
     *
     * @param contactPointList the list of contact points
     * @return String the fax number, or an empty string if not found
     */
    public static final String getFhirFax(List<ContactPoint> contactPointList) {
        return loopContactPointList(contactPointList, ContactPointSystem.FAX);
    }

    /**
     * Extracts the phone number from a list of FHIR ContactPoint objects.
     *
     * @param contactPointList the list of contact points
     * @return String the phone number, or an empty string if not found
     */
    public static final String getFhirPhone(List<ContactPoint> contactPointList) {
        return loopContactPointList(contactPointList, ContactPointSystem.PHONE);
    }

    /**
     * Extracts the email address from a list of FHIR ContactPoint objects.
     *
     * @param contactPointList the list of contact points
     * @return String the email address, or an empty string if not found
     */
    public static final String getFhirEmail(List<ContactPoint> contactPointList) {
        return loopContactPointList(contactPointList, ContactPointSystem.EMAIL);
    }

    private static final String loopContactPointList(List<ContactPoint> contactPointList, ContactPointSystem contactPointSystem) {
        String contact = "";
        for (ContactPoint contactPoint : contactPointList) {
            contact = getContactPointBySystem(contactPoint, contactPointSystem);
        }
        return contact;
    }

    private static final String getContactPointBySystem(ContactPoint contactPoint, ContactPointSystem contactPointSystem) {
        String contact = "";
        switch (contactPointSystem) {
            case EMAIL:
                contact = contactPoint.getValue();
                break;
            case FAX:
                contact = contactPoint.getValue();
                break;
            case NULL:
                break;
            case OTHER:
                break;
            case PAGER:
                break;
            case PHONE:
                contact = contactPoint.getValue();
                break;
            case SMS:
                break;
            case URL:
                break;
            default:
                break;
        }
        return contact;
    }

    /**
     * Extracts the official identifier value from a list of FHIR Identifiers.
     *
     * @param identifierList the list of FHIR Identifier objects
     * @return String the official identifier value, or an empty string if not found
     */
    public static final String getFhirOfficialIdentifier(List<Identifier> identifierList) {
        return loopIdentifierList(identifierList, IdentifierUse.OFFICIAL);
    }

    /**
     * Extracts the secondary identifier value from a list of FHIR Identifiers.
     *
     * @param identifierList the list of FHIR Identifier objects
     * @return String the secondary identifier value, or an empty string if not found
     */
    public static final String getFhirSecondaryIdentifier(List<Identifier> identifierList) {
        return loopIdentifierList(identifierList, IdentifierUse.SECONDARY);
    }

    private static final String loopIdentifierList(List<Identifier> identifierList, IdentifierUse identifierUse) {
        String id = "";
        for (Identifier identifier : identifierList) {
            id = getIdentifierByIdentifierUse(identifier, identifierUse);
        }
        return id;
    }

    private static final String getIdentifierByIdentifierUse(Identifier identifier, IdentifierUse identifierUse) {
        String id = "";
        switch (identifierUse) {
            case NULL:
                break;
            case OFFICIAL:
                id = identifier.getValue();
                break;
            case SECONDARY:
                id = identifier.getValue();
                break;
            case TEMP:
                break;
            case USUAL:
                break;
            default:
                break;
        }

        return id;
    }

}
