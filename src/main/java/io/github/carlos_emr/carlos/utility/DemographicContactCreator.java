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
package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.carlos.commn.model.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.ContactDao;
import io.github.carlos_emr.carlos.commn.dao.ContactSpecialtyDao;
import io.github.carlos_emr.carlos.commn.dao.CtlRelationshipsDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Utility class for creating and populating generic {@link Contact} objects and attaching
 * them to {@link DemographicContact} instances as contact details.
 *
 * <p>Bridges multiple contact systems (providers, contacts, professional specialists) into
 * a unified contact representation. Resolves role names from numeric IDs, builds formatted
 * contact names, and converts between contact types.
 *
 * @since 2026-03-17
 */
public class DemographicContactCreator {

    static Logger logger = MiscUtils.getLogger();
    private static ProfessionalSpecialistDao professionalSpecialistDao = SpringUtils.getBean(ProfessionalSpecialistDao.class);
    private static SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Populates contact details (name, phone, fax, etc.) for a list of demographic contacts.
     *
     * @param demographicContactList List&lt;DemographicContact&gt; the contacts to populate
     * @return List&lt;DemographicContact&gt; the same list with contact details filled in
     */
    public static List<DemographicContact> addContactDetailsToDemographicContact(List<DemographicContact> demographicContactList) {
        return fillContactNames(demographicContactList);
    }

    /**
     * Populates contact details (name, phone, fax, etc.) for a single demographic contact.
     *
     * @param demographicContact DemographicContact the contact to populate
     * @return DemographicContact the same contact with details filled in
     */
    public static DemographicContact addContactDetailsToDemographicContact(DemographicContact demographicContact) {
        return fillContactName(demographicContact);
    }

    private static List<DemographicContact> fillContactNames(List<DemographicContact> demographicContacts) {

        if (demographicContacts == null || demographicContacts.size() < 1) {
            return demographicContacts;
        }

        for (DemographicContact demographicContact : demographicContacts) {
            fillContactName(demographicContact);
        }

        return demographicContacts;
    }

    private static DemographicContact fillContactName(DemographicContact demographicContact) {
        if (demographicContact == null) {
            return demographicContact;
        }
        Provider provider;
        Contact contact;
        ProfessionalSpecialist professionalSpecialist;
        ContactSpecialty specialty;
        CtlRelationships contactRelationship;
        String providerFormattedName = "";
        ContactDao contactDao = SpringUtils.getBean(ContactDao.class);
        ContactSpecialtyDao contactSpecialtyDao = SpringUtils.getBean(ContactSpecialtyDao.class);
        CtlRelationshipsDao ctlRelationshipsDao = SpringUtils.getBean(CtlRelationshipsDao.class);
        ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
        ProfessionalSpecialistDao professionalSpecialistDao = SpringUtils.getBean(ProfessionalSpecialistDao.class);
        String demographicContactRole = demographicContact.getRole();
        Integer roleid = null;

        if (!StringUtils.isBlank(demographicContactRole)) {
            demographicContactRole = demographicContactRole.trim();

            if (StringUtils.isNumeric(demographicContactRole)) {
                roleid = toInteger(demographicContactRole, "HealthCareTeamCreator.fillContactName");
            }
        }

        if (roleid != null) {
            if (DemographicContact.CATEGORY_PROFESSIONAL.equalsIgnoreCase(demographicContact.getCategory())) {
                specialty = contactSpecialtyDao.find(roleid);
                if (specialty != null) {
                    demographicContactRole = specialty.getSpecialty();
                }

            } else if (DemographicContact.CATEGORY_PERSONAL.equalsIgnoreCase(demographicContact.getCategory())) {
                contactRelationship = ctlRelationshipsDao.find(roleid);
                if (contactRelationship != null) {
                    demographicContactRole = contactRelationship.getValue();
                }
            }
        }

        if (demographicContactRole != null) {
            demographicContact.setRole(demographicContactRole);
        } else {
            demographicContact.setRole("");
        }

        if (demographicContact.getType() == DemographicContact.TYPE_PROVIDER) {
            provider = providerDao.getProvider(demographicContact.getContactId());
            if (provider != null) {
                providerFormattedName = provider.getFormattedName();
            }
            if (StringUtils.isBlank(providerFormattedName)) {
                providerFormattedName = "Error: Contact Support";
                logger.error("Formatted name for provder was not avaialable. Contact number: " + demographicContact.getContactId());
            }
            demographicContact.setContactName(providerFormattedName);
            contact = new ProfessionalContact();
            contact.setWorkPhone("internal");
            contact.setFax("internal");
            demographicContact.setDetails(contact);
        }

        if (demographicContact.getType() == DemographicContact.TYPE_CONTACT) {
            contact = contactDao.find(Integer.parseInt(demographicContact.getContactId()));
            demographicContact.setContactName(contact.getFormattedName());
            demographicContact.setDetails(contact);
        }

        if (demographicContact.getType() == DemographicContact.TYPE_PROFESSIONALSPECIALIST) {
            professionalSpecialist = professionalSpecialistDao.find(Integer.parseInt(demographicContact.getContactId()));
            demographicContact.setContactName(professionalSpecialist.getFormattedName());
            contact = buildContact(professionalSpecialist);
            demographicContact.setDetails(contact);
        }

        return demographicContact;
    }

    /**
     * Converts a list of contact objects into a list of generic {@link Contact} instances.
     *
     * @param contact List&lt;?&gt; the source contact objects to convert
     * @return List&lt;Contact&gt; the converted generic contacts
     */
    public static final List<Contact> buildContact(final List<?> contact) {
        List<Contact> contactlist = new ArrayList<Contact>();
        Contact contactitem;
        Iterator<?> contactiterator = contact.iterator();
        while (contactiterator.hasNext()) {
            contactitem = buildContact(contactiterator.next());
            contactlist.add(contactitem);
        }
        return contactlist;
    }

    /**
     * Converts a contact object (e.g., {@link ProfessionalSpecialist}) into a generic
     * {@link Contact} instance, extracting and normalizing address, phone, and identity fields.
     *
     * @param contactobject Object the source contact to convert
     * @return Contact a generic contact populated with the source object's data
     */
    public static final Contact buildContact(final Object contactobject) {
        ProfessionalContact contact = new ProfessionalContact();

        Integer id = null;
        String systemId = "";
        String firstName = "";
        String lastName = "";
        String address = "";
        String address2 = "";
        String city = "";
        String country = "";
        String postal = "";
        String province = "";
        boolean deleted = false;
        String cellPhone = "-";
        String workPhone = "";
        String email = "";
        String residencePhone = "";
        String fax = "";
        String specialty = "";
        String cpso = "";

        if (contactobject instanceof ProfessionalSpecialist) {

            ProfessionalSpecialist professionalSpecialist = (ProfessionalSpecialist) contactobject;

            // assuming that the address String is always csv.
            address = professionalSpecialist.getStreetAddress();

            // see pattern in method: convertProfessionalContactAsProfessionalSpecialist(final ProfessionalContact contact)
            if (address.contains(",")) {
                String[] addressArray = address.split(",");
                address = addressArray[0].trim();
                if (addressArray.length > 3) {
                    city = addressArray[1].trim();
                    postal = addressArray[2].trim();
                    province = addressArray[3].trim();
                } else if (addressArray.length > 4) {
                    city = addressArray[1].trim();
                    postal = addressArray[2].trim();
                    province = addressArray[3].trim();
                    country = addressArray[4].trim();
                } else if (addressArray.length == 3) {
                    province = addressArray[1].trim();
                    country = addressArray[2].trim();
                } else {
                    province = addressArray[1];
                }

            }

            // mark the contact with Specialist Type - Later parsed in client Javascript.
            // using SystemId as a transient parameter only.
            systemId = DemographicContact.TYPE_PROFESSIONALSPECIALIST + "";
            id = professionalSpecialist.getId();
            firstName = professionalSpecialist.getFirstName();
            lastName = professionalSpecialist.getLastName();
            email = professionalSpecialist.getEmailAddress();
            residencePhone = professionalSpecialist.getPhoneNumber();
            workPhone = professionalSpecialist.getPhoneNumber();
            fax = professionalSpecialist.getFaxNumber();
            cpso = professionalSpecialist.getReferralNo();

        }

        contact.setId(id);
        contact.setSystemId(systemId);
        contact.setFirstName(firstName);
        contact.setLastName(lastName);
        contact.setAddress(address);
        contact.setAddress2(address2);
        contact.setCity(city);
        contact.setCountry(country);
        contact.setPostal(postal);
        contact.setProvince(province);
        contact.setDeleted(deleted);
        contact.setCellPhone(cellPhone);
        contact.setWorkPhone(workPhone);
        contact.setResidencePhone(residencePhone);
        contact.setFax(fax);
        contact.setEmail(email);
        contact.setSpecialty(specialty);
        contact.setCpso(cpso);

        return contact;
    }

    /**
     * Converts a {@link ProfessionalContact} to a {@link ProfessionalSpecialist}, with
     * security privilege verification.
     *
     * @param loggedInInfo LoggedInInfo the current user's session info for authorization
     * @param contact      ProfessionalContact the contact to convert
     * @return ProfessionalSpecialist the converted specialist entity
     * @throws SecurityException if the user lacks {@code _demographic} read privilege
     */
    public static ProfessionalSpecialist convertProfessionalContactAsProfessionalSpecialist(LoggedInInfo loggedInInfo, ProfessionalContact contact) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
        return convertProfessionalContactAsProfessionalSpecialist(contact);
    }

    private static ProfessionalSpecialist convertProfessionalContactAsProfessionalSpecialist(final ProfessionalContact contact) {

        Integer contactId = contact.getId();
        ProfessionalSpecialist professionalSpecialist = null;
        String address = contact.getAddress();
        String addressTwo = contact.getAddress2();
        String postal = contact.getPostal();
        String city = contact.getCity();
        String province = contact.getProvince();
        String country = contact.getCountry();
        String workExt = contact.getWorkPhoneExtension();

        StringBuilder stringBuilder = new StringBuilder("");

        if (address != null) {
            stringBuilder.append(address.trim());
        }

        if (addressTwo != null) {
            stringBuilder.append(" ");
            stringBuilder.append(addressTwo.trim());

        }
        stringBuilder.append(", ");

        if (city != null) {
            stringBuilder.append(city.trim());

        }
        stringBuilder.append(", ");

        if (postal != null) {
            stringBuilder.append(postal.trim());
        }
        stringBuilder.append(", ");

        if (province != null) {
            stringBuilder.append(province.trim());
        }
        stringBuilder.append(", ");

        if (country != null) {
            stringBuilder.append(country.trim());
        }

        stringBuilder.append(", ");

        if (contactId == null) {
            contactId = 0;
        }

        if (workExt == null || workExt.isEmpty()) {
            workExt = "";
        } else {
            workExt = " ext " + workExt;
        }

        if (contactId > 0) {
            professionalSpecialist = professionalSpecialistDao.find(contactId);
        } else {
            professionalSpecialist = new ProfessionalSpecialist();
        }

        professionalSpecialist.setStreetAddress(stringBuilder.toString());
        professionalSpecialist.setFirstName(contact.getFirstName());
        professionalSpecialist.setLastName(contact.getLastName());
        professionalSpecialist.setEmailAddress(contact.getEmail());
        professionalSpecialist.setPhoneNumber(contact.getWorkPhone() + workExt);
        professionalSpecialist.setFaxNumber(contact.getFax());
        professionalSpecialist.setReferralNo(contact.getCpso().trim());
        professionalSpecialist.setSpecialtyType(contact.getSpecialty());
        professionalSpecialist.setPrivatePhoneNumber(contact.getResidencePhone());
        professionalSpecialist.setCellPhoneNumber(contact.getCellPhone());

        return professionalSpecialist;
    }

    /** Comparator that sorts {@link Contact} objects alphabetically by last name (case-insensitive). */
    public static Comparator<Contact> byLastName = new Comparator<Contact>() {
        public int compare(Contact contact1, Contact contact2) {
            String lastname1 = contact1.getLastName().toUpperCase();
            String lastname2 = contact2.getLastName().toUpperCase();
            return lastname1.compareTo(lastname2);
        }
    };


    private static final Integer toInteger(final String numberstring, final String methodSignature) {
        Integer number = null;
        try {
            number = Integer.parseInt(numberstring);
        } catch (NumberFormatException e) {
            MiscUtils.getLogger().warn("Error converting String to Integer for method DemographicManager." + methodSignature);
            return null;
        }
        return number;
    }
}