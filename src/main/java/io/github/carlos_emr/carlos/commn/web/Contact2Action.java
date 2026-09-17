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


package io.github.carlos_emr.carlos.commn.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.ContactDao;
import io.github.carlos_emr.carlos.commn.dao.ContactSpecialtyDao;
import io.github.carlos_emr.carlos.commn.dao.CtlRelationshipsDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicContactDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalContactDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.Contact;
import io.github.carlos_emr.carlos.commn.model.ContactSpecialty;
import io.github.carlos_emr.carlos.commn.model.CtlRelationships;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicContact;
import io.github.carlos_emr.carlos.commn.model.DemographicPharmacy;
import io.github.carlos_emr.carlos.commn.model.PharmacyInfo;
import io.github.carlos_emr.carlos.commn.model.ProfessionalContact;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.PharmacyManager;
import io.github.carlos_emr.carlos.utility.DemographicContactCreator;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.springframework.beans.BeanUtils;

import io.github.carlos_emr.CarlosProperties;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.utility.LogSafe;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class Contact2Action extends ActionSupport {
    private static final String PERSONAL_CONTACT_PREFIX = "contact_";
    private static final String PROFESSIONAL_CONTACT_PREFIX = "procontact_";
    private static final String CONTACT_ID_SUFFIX = ".contactId";
    private static final String CONTACT_ID_PARAMETER = "contactId";
    private static final String CONTACT_ROLE_SUFFIX = ".role";
    private static final String CONTACT_TYPE_SUFFIX = ".type";
    private static final String CONTACT_NOTE_SUFFIX = ".note";
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    static Logger logger = MiscUtils.getLogger();
    static ContactDao contactDao = (ContactDao) SpringUtils.getBean(ContactDao.class);
    static ProfessionalContactDao proContactDao = (ProfessionalContactDao) SpringUtils.getBean(ProfessionalContactDao.class);
    static DemographicContactDao demographicContactDao = (DemographicContactDao) SpringUtils.getBean(DemographicContactDao.class);
    static DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);
    static DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    static ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);
    static ProfessionalSpecialistDao professionalSpecialistDao = SpringUtils.getBean(ProfessionalSpecialistDao.class);
    static ContactSpecialtyDao contactSpecialtyDao = SpringUtils.getBean(ContactSpecialtyDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private static CtlRelationshipsDao ctlRelationshipsDao = SpringUtils.getBean(CtlRelationshipsDao.class);
    private static PharmacyManager pharmacyManager = SpringUtils.getBean(PharmacyManager.class);

    @Override
    public String execute() {
        String method = request.getParameter("method");
        if ("saveManage".equals(method)) {
            return saveManage();
        } else if ("removeContact".equals(method)) {
            return removeContact();
        } else if ("addContact".equals(method)) {
            return addContact();
        } else if ("addProContact".equals(method)) {
            return addProContact();
        } else if ("editHealthCareTeam".equals(method)) {
            return editHealthCareTeam();
        } else if ("editContact".equals(method)) {
            return editContact();
        } else if ("viewContact".equals(method)) {
            return viewContact();
        } else if ("editProContact".equals(method)) {
            return editProContact();
        } else if ("saveContact".equals(method)) {
            return saveContact();
        } else if ("saveProContact".equals(method)) {
            return saveProContact();
        } else if ("setEmergencyContact".equals(method)) {
            return setEmergencyContact();
        } else if ("setDNC".equals(method)) {
            return setDNC();
        } else if ("setMRP".equals(method)) {
            return setMRP();
        } else if ("searchAllContacts".equals(method)) {
            return searchAllContacts();
        } else if ("addPharmacy".equals(method)) {
            return addPharmacy();
        } else if ("removePharmacy".equals(method)) {
            return removePharmacy();
        } else if ("addPharmacyInfo".equals(method)) {
            return addPharmacyInfo();
        } else if ("editPharmacyInfo".equals(method)) {
            return editPharmacyInfo();
        } else if ("savePharmacyInfo".equals(method)) {
            return savePharmacyInfo();
        } 

        // Return the manage method by default
        return manage();
    }

    public String manage() {
        String demographicNo = request.getParameter("demographic_no");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "r", demographicNo)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        List<DemographicContact> dcs = demographicContactDao.findByDemographicNoAndCategory(Integer.parseInt(demographicNo), DemographicContact.CATEGORY_PERSONAL);
        for (DemographicContact dc : dcs) {
            if (dc.getType() == (DemographicContact.TYPE_DEMOGRAPHIC)) {
                dc.setContactName(demographicDao.getClientByDemographicNo(Integer.parseInt(dc.getContactId())).getFormattedName());
            }
            if (dc.getType() == (DemographicContact.TYPE_CONTACT)) {
                dc.setContactName(contactDao.find(Integer.parseInt(dc.getContactId())).getFormattedName());
            }
        }

        request.setAttribute("contacts", dcs);
        request.setAttribute("contact_num", dcs.size());

        List<DemographicContact> pdcs = demographicContactDao.findByDemographicNoAndCategory(Integer.parseInt(demographicNo), DemographicContact.CATEGORY_PROFESSIONAL);
        for (DemographicContact dc : pdcs) {
            // workaround: UI allows to enter specialist with  a type that is not set, prevent NPE and display 'Unknown' as name
            // user then can choose to delete this entry
            String contactName = null;
            if (dc.getType() == (DemographicContact.TYPE_PROVIDER)) {
                Provider provider = providerDao.getProvider(dc.getContactId());
                contactName = (provider == null) ? "Unknown" : provider.getFormattedName();
            }
            if (dc.getType() == (DemographicContact.TYPE_CONTACT)) {
                Contact contact = contactDao.find(Integer.parseInt(dc.getContactId()));
                contactName = (contact == null) ? "Unknown" : contact.getFormattedName();
            }
            if (dc.getType() == (DemographicContact.TYPE_PROFESSIONALSPECIALIST)) {
                ProfessionalSpecialist profSpecialist = professionalSpecialistDao.find(Integer.parseInt(dc.getContactId()));
                contactName = (profSpecialist == null) ? "Unknown" : profSpecialist.getFormattedName();
            }
            StringUtils.trimToEmpty(contactName);
            dc.setContactName(contactName);
        }
        request.setAttribute("procontacts", pdcs);
        request.setAttribute("procontact_num", pdcs.size());

        if (request.getParameter("demographic_no") != null && request.getParameter("demographic_no").length() > 0)
            request.setAttribute("demographic_no", request.getParameter("demographic_no"));

        return "manage";
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String saveManage() {
        if (!requireContactPost()) {
            return NONE;
        }
        try {
            return saveManagedContacts();
        } catch (NumberFormatException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
    }

    private String saveManagedContacts() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        String submittedPatient = request.getParameter("demographic_no");
        requireContactWriteAccess(loggedInInfo, submittedPatient);
        int demographicNo = positiveContactId(submittedPatient);
        String canonicalPatient = Integer.toString(demographicNo);
        // A padded/signed input must not bypass a patient-specific ACL by
        // falling back to general privileges under a different string key.
        if (!canonicalPatient.equals(submittedPatient)) {
            requireContactWriteAccess(loggedInInfo, canonicalPatient);
        }
        String forward = "windowClose";
        String postMethod = request.getParameter("postMethod");

        int maxContact = contactRowCount("contact_num");
        int maxProContact = contactRowCount("procontact_num");
        // Validate both categories and reciprocal writes before changing any row.
        Map<Integer, String> reciprocalRoles = validateContactSaves(PERSONAL_CONTACT_PREFIX, maxContact, demographicNo, loggedInInfo);
        validateContactSaves(PROFESSIONAL_CONTACT_PREFIX, maxProContact, demographicNo, loggedInInfo);
        findContactRemovals(demographicNo);

        if ("ajax".equalsIgnoreCase(postMethod)) {
            forward = postMethod;
        }

        saveContactRows(PERSONAL_CONTACT_PREFIX, maxContact, demographicNo, loggedInInfo, reciprocalRoles);
        saveContactRows(PROFESSIONAL_CONTACT_PREFIX, maxProContact, demographicNo, loggedInInfo, Map.of());

        //handle removes
        removeContact();

        return forward;
    }

    private void requireContactWriteAccess(LoggedInInfo loggedInInfo, String demographicNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "w", demographicNo)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
    }

    private void saveContactRows(String prefix, int count, int demographicNo, LoggedInInfo loggedInInfo,
                                 Map<Integer, String> reciprocalRoles) {
        String category = PERSONAL_CONTACT_PREFIX.equals(prefix)
                ? DemographicContact.CATEGORY_PERSONAL : DemographicContact.CATEGORY_PROFESSIONAL;
        for (int row = 1; row <= count; row++) {
            String field = prefix + row;
            String associationId = request.getParameter(field + ".id");
            String contactId = request.getParameter(field + CONTACT_ID_SUFFIX);
            if (associationId == null || StringUtils.isBlank(contactId) || "0".equals(contactId)) continue;
            boolean consent = !"0".equals(request.getParameter(field + ".consentToContact"));
            boolean active = !"0".equals(request.getParameter(field + ".active"));
            linkContactToDemographic(contactId, Integer.parseInt(associationId), demographicNo,
                    request.getParameter(field + CONTACT_ROLE_SUFFIX),
                    request.getParameter(field + CONTACT_TYPE_SUFFIX),
                    request.getParameter(field + CONTACT_NOTE_SUFFIX), category,
                    request.getParameter(field + ".sdm"), request.getParameter(field + ".ec"),
                    consent, active, loggedInInfo);

            // The preflight authorizes reciprocal writes before any mutation.
            // Recheck existence so repeated targets cannot create duplicate rows.
            String reverseRole = reciprocalRoles.get(row);
            if (reverseRole != null && demographicContactDao.findPersonalPatientLinks(Integer.parseInt(contactId), demographicNo).isEmpty()) {
                linkContactToDemographic(Integer.toString(demographicNo), 0, Integer.parseInt(contactId),
                        reverseRole, Integer.toString(DemographicContact.TYPE_DEMOGRAPHIC),
                        request.getParameter(field + CONTACT_NOTE_SUFFIX), DemographicContact.CATEGORY_PERSONAL,
                        null, null, // Reverse relationships do not grant SDM or emergency-contact status.
                        consent, active, loggedInInfo);
            }
        }
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private String getReverseRole(String roleName, int sourceDemographicNo) {
        if (roleName == null) return null;
        return switch (roleName) {
            case "Mother", "Father", "Parent" -> genderedReverseRole(sourceDemographicNo, "Son", "Daughter");
            case "Son", "Daughter" -> genderedReverseRole(sourceDemographicNo, "Father", "Mother");
            case "Brother", "Sister" -> genderedReverseRole(sourceDemographicNo, "Brother", "Sister");
            case "Wife" -> "Husband";
            case "Husband" -> "Wife";
            case "Partner" -> "Partner";
            default -> null;
        };
    }

    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "M/F domain codes select a relationship label; not identity or authorization")
    private String genderedReverseRole(int sourceDemographicNo, String maleRole, String femaleRole) {
        Demographic demographic = demographicDao.getDemographicById(sourceDemographicNo);
        // Do not infer a gendered relationship when the source patient's sex is unknown.
        if (demographic == null) return null;
        if ("M".equalsIgnoreCase(demographic.getSex())) return maleRole;
        if ("F".equalsIgnoreCase(demographic.getSex())) return femaleRole;
        return null;
    }

    private int contactRowCount(String parameter) {
        int count = Integer.parseInt(request.getParameter(parameter));
        if (count < 0 || count > 1000) throw new NumberFormatException("Invalid contact count");
        return count;
    }

    private static int positiveContactId(String value) {
        int id = Integer.parseInt(value);
        if (id <= 0) throw new NumberFormatException("Invalid contact identifier");
        return id;
    }

    private static void requireContactCategory(DemographicContact association, String category) {
        if (!category.equals(association.getCategory())) {
            throw new SecurityException("Contact association category does not match");
        }
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @SuppressWarnings("unused")
    public String removeContact() {
        if (!requireContactPost()) {
            return NONE;
        }

        String demographicNo = StringUtils.trimToNull(request.getParameter("demographic_no"));
        if (demographicNo == null || !securityInfoManager.hasPrivilege(
                LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "w", demographicNo)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
        for (DemographicContact association : findContactRemovals(Integer.parseInt(demographicNo))) {
            association.setDeleted(true);
            demographicContactDao.merge(association);
        }
        String postMethod = request.getParameter("postMethod");
        return "ajax".equalsIgnoreCase(postMethod) ? postMethod : null;
    }

    private List<DemographicContact> findContactRemovals(int demographicNo) {
        List<String> ids = new ArrayList<>();
        for (String parameter : Arrays.asList("procontact.delete", "contact.delete")) {
            String[] values = request.getParameterValues(parameter);
            if (values != null) {
                for (String value : values) {
                    if (StringUtils.isNotBlank(value)) ids.add(value);
                }
            }
        }
        if (ids.isEmpty() && request.getParameter(CONTACT_ID_PARAMETER) != null) {
            ids.add(request.getParameter(CONTACT_ID_PARAMETER));
        }
        List<DemographicContact> removals = new ArrayList<>();
        for (String id : ids) {
            int associationId = Integer.parseInt(id);
            // An unsaved editor row has no persisted association to remove.
            if (associationId != 0) removals.add(requireOwnedContact(associationId, demographicNo));
        }
        return removals;
    }

    private Map<Integer, String> validateContactSaves(String prefix, int count, int demographicNo, LoggedInInfo loggedInInfo) {
        Map<Integer, String> reciprocalRoles = new HashMap<>();
        String category = PERSONAL_CONTACT_PREFIX.equals(prefix)
                ? DemographicContact.CATEGORY_PERSONAL : DemographicContact.CATEGORY_PROFESSIONAL;
        for (int row = 1; row <= count; row++) {
            String field = prefix + row;
            String id = request.getParameter(field + ".id");
            if (id == null) continue;
            DemographicContact existing = validateContactRow(field, id, category, demographicNo);
            if (!PERSONAL_CONTACT_PREFIX.equals(prefix)) continue;
            String reverseRole = findNewReciprocalRole(field, existing, demographicNo);
            if (reverseRole == null) continue;
            String contactId = request.getParameter(field + CONTACT_ID_SUFFIX);
            if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "w", contactId)) {
                throw new SecurityException("missing required sec object (_demographic)");
            }
            reciprocalRoles.put(row, reverseRole);
        }
        return reciprocalRoles;
    }

    private DemographicContact validateContactRow(String field, String id, String category, int demographicNo) {
        int associationId = Integer.parseInt(id);
        if (associationId < 0) throw new NumberFormatException("Invalid association identifier");
        DemographicContact existing = associationId == 0 ? null : requireOwnedContact(associationId, demographicNo);
        if (existing != null) requireContactCategory(existing, category);
        String contactId = request.getParameter(field + CONTACT_ID_SUFFIX);
        if (StringUtils.isNotBlank(contactId) && !"0".equals(contactId)) {
            // provider_no is a string namespace (for example T099); the other
            // association namespaces use positive numeric identifiers.
            int type = effectiveContactType(field, existing);
            if (existing == null) requireNewContactType(category, type);
            if (type != DemographicContact.TYPE_PROVIDER) positiveContactId(contactId);
        }
        return existing;
    }

    private static void requireNewContactType(String category, int type) {
        if (type == DemographicContact.TYPE_CONTACT) return;
        boolean supported = DemographicContact.CATEGORY_PERSONAL.equals(category)
                ? type == DemographicContact.TYPE_DEMOGRAPHIC
                : type == DemographicContact.TYPE_PROVIDER || type == DemographicContact.TYPE_PROFESSIONALSPECIALIST;
        if (!supported) throw new NumberFormatException("Contact type is not supported in this category");
    }

    private int effectiveContactType(String field, DemographicContact existing) {
        if (existing != null) return existing.getType();
        String submitted = request.getParameter(field + CONTACT_TYPE_SUFFIX);
        int type = submitted == null ? DemographicContact.TYPE_PROVIDER : Integer.parseInt(submitted);
        if (type < 0 || type > DemographicContact.TYPE_PROFESSIONALSPECIALIST) {
            throw new NumberFormatException("Invalid contact type");
        }
        return type;
    }

    private String findNewReciprocalRole(String field, DemographicContact existing, int demographicNo) {
        String contactId = request.getParameter(field + CONTACT_ID_SUFFIX);
        if (StringUtils.isBlank(contactId) || "0".equals(contactId)) return null;

        // Existing rows disable their type selector. Preserve that classification
        // even if a crafted request supplies a different type; persistence uses
        // the same rule, so reciprocal planning cannot change independently.
        int effectiveType = effectiveContactType(field, existing);
        if (effectiveType != DemographicContact.TYPE_DEMOGRAPHIC
                || !demographicContactDao.findPersonalPatientLinks(Integer.parseInt(contactId), demographicNo).isEmpty()) return null;
        return getReverseRole(request.getParameter(field + CONTACT_ROLE_SUFFIX), demographicNo);
    }

    private static DemographicContact requireOwnedContact(int associationId, int demographicNo) {
        DemographicContact association = demographicContactDao.find(associationId);
        if (association == null || association.getDemographicNo() != demographicNo) {
            throw new SecurityException("Contact association does not belong to the requested patient");
        }
        return association;
    }

    private boolean requireContactPost() {
        if ("POST".equals(request.getMethod())) {
            return true;
        }
        response.setHeader("Allow", "POST");
        response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        return false;
    }

    @SuppressWarnings("unused")
    public String addContact() {
        CtlRelationshipsDao relationshipDao = SpringUtils.getBean(CtlRelationshipsDao.class);
        CarlosProperties prop = CarlosProperties.getInstance();
        List<CtlRelationships> relationships = relationshipDao.findAllActive();
        request.setAttribute("relationships", relationships);
        request.setAttribute("region", prop.getProperty("billregion"));
        request.setAttribute("contactRole", request.getParameter("contact.role"));
        return "cForm";
    }

    @SuppressWarnings("unused")
    public String addProContact() {
        ContactSpecialtyDao specialtyDao = SpringUtils.getBean(ContactSpecialtyDao.class);
        List<ContactSpecialty> specialties = specialtyDao.findAll();
        CarlosProperties prop = CarlosProperties.getInstance();
        request.setAttribute("region", prop.getProperty("billregion"));
        request.setAttribute("specialties", specialties);
        request.setAttribute("pcontact.lastName", request.getParameter("keyword"));
        request.setAttribute("contactRole", request.getParameter("contactRole"));

        return "pForm";
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @SuppressWarnings("unused")
    public String editHealthCareTeam() {

        String demographicContactId = request.getParameter(CONTACT_ID_PARAMETER);
        DemographicContact demographicContact = null;
        Integer contactType = null;
        String contactCategory = "";
        String contactId = "";
        ProfessionalSpecialist professionalSpecialist = null;
        String contactRole = "";
        List<ContactSpecialty> specialtyList = null;

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "w", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        if (StringUtils.isNotBlank(demographicContactId)) {

            specialtyList = contactSpecialtyDao.findAll();
            demographicContact = demographicContactDao.find(Integer.parseInt(demographicContactId));
            contactType = demographicContact.getType();
            contactCategory = demographicContact.getCategory();
            contactId = demographicContact.getContactId();
            contactRole = demographicContact.getRole();

            if (DemographicContact.CATEGORY_PROFESSIONAL.equalsIgnoreCase(contactCategory)) {

                if (DemographicContact.TYPE_CONTACT == contactType) {

                    ProfessionalContact contact = proContactDao.find(Integer.parseInt(contactId));
                    request.setAttribute("pcontact", contact);

                } else if (DemographicContact.TYPE_PROFESSIONALSPECIALIST == contactType) {

                    professionalSpecialist = professionalSpecialistDao.find(Integer.parseInt(contactId));

                    if (professionalSpecialist != null) {
                        request.setAttribute("pcontact", DemographicContactCreator.buildContact(professionalSpecialist));
                    }
                }
            }

            // specialty should be from the relational table via specialty id.
            // converting back to id here.
            if (!StringUtils.isNumeric(contactRole)) {
                String specialtyDesc;
                for (ContactSpecialty specialty : specialtyList) {
                    specialtyDesc = specialty.getSpecialty().trim();
                    if (specialtyDesc.equalsIgnoreCase(contactRole)) {
                        contactRole = specialty.getId() + "";
                    }
                }
            }

            request.setAttribute("specialties", specialtyList);
            request.setAttribute("contactRole", contactRole);
            request.setAttribute("demographicContactId", demographicContactId);
        }

        return "pForm";
    }

    @SuppressWarnings("unused")
    public String editContact() {
        String id = request.getParameter("contact.id");
        String demographicContactId = request.getParameter("demographicContactId");
        Contact contact = null;
        CtlRelationshipsDao relationshipDao = SpringUtils.getBean(CtlRelationshipsDao.class);
        List<CtlRelationships> relationships = relationshipDao.findAllActive();

        if (StringUtils.isNotBlank(demographicContactId)) {

            DemographicContact demographicContact = demographicManager.getPersonalEmergencyContactById(
                    LoggedInInfo.getLoggedInInfoFromSession(request),
                    Integer.parseInt(demographicContactId));
            contact = demographicContact.getDetails();
            request.setAttribute("ecRelationship", demographicContact.getRole());
            request.setAttribute("demographicContactId", demographicContact.getId());

        } else if (StringUtils.isNotBlank(id)) {

            id = id.trim();
            contact = contactDao.find(Integer.parseInt(id));
        }

        request.setAttribute("relationships", relationships);
        request.setAttribute("contact", contact);

        return "cForm";
    }

    public String viewContact() {
        String id = request.getParameter("contact.id");
        Contact contact = null;
        if (StringUtils.isNotBlank(id)) {
            id = id.trim();
            contact = contactDao.find(Integer.parseInt(id));
            request.setAttribute("contact", contact);
        }
        return "view";
    }

    @SuppressWarnings("unused")
    public String editProContact() {
        String id = request.getParameter("pcontact.id");
        ProfessionalContact contact = null;

        if (StringUtils.isNotBlank(id)) {
            id = id.trim();
            contact = proContactDao.find(Integer.parseInt(id));
            request.setAttribute("pcontact", contact);

            // Set specialties list for dropdown
            List<ContactSpecialty> specialties = contactSpecialtyDao.findAll();
            request.setAttribute("specialties", specialties);

            // Set contactRole from the contact's specialty for proper selection
            if (contact != null && StringUtils.isNotBlank(contact.getSpecialty())) {
                request.setAttribute("contactRole", contact.getSpecialty());
            }
        }
        return "pForm";
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @SuppressWarnings("unused")
    public String saveContact() {

        String postMethod = request.getParameter("postMethod");
        String forward = "cForm";

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "w", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        if ("ajax".equalsIgnoreCase(postMethod)) {
            forward = postMethod;
        }

        //DynaValidatorForm dform = (DynaValidatorForm) form;
        //Contact contact = (Contact) dform.get("contact");
        String id = request.getParameter("contact.id");

        if (id != null && id.length() > 0 && !"0".equals(id)) {
            Contact savedContact = contactDao.find(Integer.parseInt(id));
            if (savedContact != null) {
                BeanUtils.copyProperties(contact, savedContact, new String[]{"id"});
                contactDao.merge(savedContact);
            }
        } else {
            contact.setId(null);
            contactDao.persist(contact);
            id = contact.getId() + "";
        }

        // slingshot the DemographicContact details back to the request.
        // the saveManage method is to difficult to re-engineer
        request.setAttribute(CONTACT_ID_PARAMETER, id);

        // forward from pop-up to forward page.
        request.setAttribute("demographicContactId", request.getParameter("demographicContactId"));
        request.setAttribute("contactRole", request.getParameter("contact.role"));
        request.setAttribute("contactType", DemographicContact.TYPE_CONTACT);
        request.setAttribute("contactName", contact.getFormattedName());

        return forward;
    }

    /**
     * Saves a new or edited ProfessionalSpecialist or ProfessionalContact.
     * <p>
     * Switches in the request parameters determine the action:
     * <p>
     * "contactType": DemographicContact.TYPE_PROFESSIONALSPECIALIST [3] = ProfessionalSpecialist, else ProfessionalContact
     * CONTACT_ID_PARAMETER: >0 = merge edited specialist by contactType, 0 = new specialist by contactType
     * "demographicContactId" plus "demographicNo" = when both >0 edit current DemographicContact entry.
     * <p>
     * The incoming DynaForm is an abstract Contact entity as ProfessionalContact.
     * Contact entities are transferred to a ProfessionalSpecialst entity when required.
     * <p>
     * This method will return a ProfessionalSpecialist entity and Contact entity whenever a potential for
     * a duplicate ProfessionalSpecialist addition is caught.  This is controlled by the ProfessionalSpecialst.referralNo
     * property.
     * <p>
     * This method should be normalized into a Manager class in the future. It's like this because this method has/had
     * pre-existing dependents.
     */
    @SuppressWarnings("unused")
    public String saveProContact() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        //DynaValidatorForm dform = (DynaValidatorForm) form;
        ProfessionalContact contact = pcontact;
        String demographic_no = request.getParameter("demographic_no");
        String ec = null;
        String sdm = null;
        Boolean consentToContact = Boolean.TRUE;
        Boolean active = Boolean.TRUE;

        String demographicContactId = request.getParameter("demographicContactId");
        DemographicContact demographicContact = null;
        Integer contactType = null; // this needs to be null as there are -1 and 0 contact types

        // what type did the user interface send
        String contactTypeString = request.getParameter("contactType");
        if (contactTypeString != null && !contactTypeString.isEmpty()) {
            contactType = Integer.parseInt(contactTypeString);
        }

        String contactRole = "";
        Integer contactId = contact.getId();

        if (demographicContactId == null) {
            demographicContactId = "";
        }

        if (demographic_no == null) {
            demographic_no = "";
        }

        if (contactId == null) {
            contactId = 0;
        }

        // If the ID (pcontact.id) has been set > 0 then the contact is being edited.
        if (contactId > 0) {

            logger.info("Editing a current Professional Contact with id " + contactId);

            // changes for the DemographicContact table
            // when a demographicContactId is provided this means that the linked information needs to
            // be changed as well.
            if (!demographicContactId.isEmpty()) {
                demographicContact = demographicContactDao.find(Integer.parseInt(demographicContactId));
                contactType = demographicContact.getType();
                ec = demographicContact.getEc();
                sdm = demographicContact.getSdm();
                consentToContact = demographicContact.isConsentToContact();
                active = demographicContact.isActive();
            } else {
                // this is an indicator that this contact is being edited before
                // being linked with a demographic in the DemographicContact table.
                demographicContactId = "0";
            }

            // changes for the ProfessionalSpecialist table
            if (DemographicContact.TYPE_PROFESSIONALSPECIALIST == contactType) {
                // convert from a ProfessionalContact to ProfessionalSpecialist
                ProfessionalSpecialist professionalSpecialist = DemographicContactCreator.convertProfessionalContactAsProfessionalSpecialist(loggedInInfo, contact);
                professionalSpecialist.setLastUpdated(new Date(System.currentTimeMillis()));
                professionalSpecialistDao.merge(professionalSpecialist);

                contactRole = contact.getSpecialty();

                // changes for the Contact table.
            } else {

                ProfessionalContact savedContact = proContactDao.find(contactId);
                if (savedContact != null) {

                    BeanUtils.copyProperties(contact, savedContact, new String[]{"id"});
                    proContactDao.merge(savedContact);
                    contactRole = savedContact.getSpecialty();
                }
            }

            // Otherwise this is a new contact
        } else {

            if (DemographicContact.TYPE_PROFESSIONALSPECIALIST == contactType) {

                List<ProfessionalSpecialist> specialists = professionalSpecialistDao.findByReferralNo(contact.getCpso().trim());

                if (specialists == null) {
                    ProfessionalSpecialist professionalSpecialist = DemographicContactCreator.convertProfessionalContactAsProfessionalSpecialist(loggedInInfo, contact);
                    professionalSpecialistDao.persist(professionalSpecialist);
                    contactId = professionalSpecialist.getId();

                    logger.info("Saved a new Professional Specialist with id " + contactId);

                } else {
                    // return a message.
                    request.setAttribute("existing_contact_found", DemographicContactCreator.buildContact(specialists.get(0)));
                    request.setAttribute("contact_submitted", contact);
                    request.setAttribute("specialties", contactSpecialtyDao.findAll());
                }

            } else {

                proContactDao.persist(contact);
                contactId = contact.getId();
                contactType = DemographicContact.TYPE_CONTACT;

                logger.info("Saved a new Professional Contact with id " + contactId);
            }

            contactRole = contact.getSpecialty();

            // contact id of 0 indicates that this is a new contact
            // to be linked with a given Demographic number in the
            // DemographicContacts table (DemographicContacts.id)
            demographicContactId = "0";
        }

        // When a demographic number is provided and demographicContactId is set at 0: this NEW contact should be linked.
        // When a demographic number is provided and the demographicContactId is > 0: this demographicContact should be edited.
        if ((!demographic_no.isEmpty()) && (contactId > 0) && (!demographicContactId.isEmpty())) {

            demographicContact = linkContactToDemographic(contactId + "", Integer.parseInt(demographicContactId),
                    Integer.parseInt(demographic_no), contactRole, contactType + "", contact.getNote(), DemographicContact.CATEGORY_PROFESSIONAL,
                    sdm, ec, consentToContact, active, loggedInInfo);

            demographicContactId = demographicContact.getId() + "";

            logger.info("Linked contact id {}-{} with demographic {}", LogSafe.sanitize(String.valueOf(contactType)), LogSafe.sanitize(String.valueOf(contactId)), LogSafe.sanitize(String.valueOf(demographic_no))); // NOSONAR javasecurity:S5145 — sanitized with LogSafe

            request.setAttribute("demographic_no", demographic_no);
            request.setAttribute("id", demographicContactId);
        }

        // Set up attributes for form re-render and parent window communication
        request.setAttribute("specialties", contactSpecialtyDao.findAll());
        request.setAttribute("contactRole", contactRole);
        request.setAttribute(CONTACT_ID_PARAMETER, contactId);
        request.setAttribute("contactName", contact.getFormattedName());
        request.setAttribute("demographicContactId", demographicContactId);
        request.setAttribute("contactType", contactType);

        return "pForm";
    }


    /**
     * Assigns the given contact with emergency contact status.
     */
    @SuppressWarnings("unused")
    public String setEmergencyContact() {

        String contactId = request.getParameter(CONTACT_ID_PARAMETER);
        boolean toggle = Boolean.parseBoolean(request.getParameter("setting"));

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        DemographicContact demographicContact = demographicManager.getPersonalEmergencyContactById(
                loggedInInfo, Integer.parseInt(contactId));

        if (toggle) {
            demographicContact.setEc("true");
        } else {
            demographicContact.setEc("");
        }

        demographicContactDao.merge(demographicContact);
        request.setAttribute("demographic_no", demographicContact.getDemographicNo());
        return null;
    }

    /**
     * Set whether or not this external providers is allowed to be contacted
     * by the clinic.
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @SuppressWarnings("unused")
    public String setDNC() {

        String contactId = request.getParameter(CONTACT_ID_PARAMETER);
        String contactGroup = request.getParameter("contactGroup");

        int contactIdInt = Integer.parseInt(contactId);

        boolean dnc = Boolean.parseBoolean(request.getParameter("dnc"));
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if ("pharmacy".equalsIgnoreCase(contactGroup)) {
            pharmacyManager.setDoNotContact(loggedInInfo, contactIdInt, dnc);
        } else {
            DemographicContact demographicContact = demographicManager.getHealthCareMemberbyId(loggedInInfo, contactIdInt);
            demographicContact.setConsentToContact(dnc);
            demographicContactDao.merge(demographicContact);
        }

        return null;
    }

    /**
     * Assigns the given providers with a Most Responsible Provider status.
     */
    @SuppressWarnings("unused")
    public String setMRP() {

        String contactId = request.getParameter(CONTACT_ID_PARAMETER);
        int contactIdInt = Integer.parseInt(contactId);

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        DemographicContact demographicContactMRP = demographicManager.getHealthCareMemberbyId(loggedInInfo, contactIdInt);
        List<DemographicContact> demographicContacts = null;
        if (demographicContactMRP != null) {
            demographicContacts = demographicManager.getHealthCareTeam(loggedInInfo, demographicContactMRP.getDemographicNo());
        }

        // set all contacts in this demographic group to false to ensure no duplicates are made.
        if (demographicContacts != null) {
            for (DemographicContact demographicContact : demographicContacts) {
                if (demographicContact.isMrp()) {
                    demographicContact.setMrp(Boolean.FALSE);
                    demographicContactDao.merge(demographicContact);
                }
            }
        }

        demographicContactMRP.setMrp(Boolean.TRUE);
        demographicContactDao.merge(demographicContactMRP);
        request.setAttribute("demographic_no", demographicContactMRP.getDemographicNo());
        return null; //mapping.findForward("ajax");
    }

    /**
     * Searches all contacts in all contact tables (ProfessionalSpecialist, ProfessionalContact)
     * based on the "searchMode" request parameter.
     */
    @SuppressWarnings("unused")
    public String searchAllContacts() {

        String searchMode = request.getParameter("searchMode");
        String orderBy = request.getParameter("orderBy");
        String keyword = request.getParameter("term");

        List<Contact> contacts = searchAllContacts(searchMode, orderBy, keyword);

        response.setContentType("application/json");
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.writeValue(response.getWriter(), contacts);
        } catch (IOException e) {
            MiscUtils.getLogger().error("ERROR WRITING RESPONSE ", e);
        }

        return null;
    }

    /**
     * Adds a Pharmacy contact to a demographic's list of preferred Pharmacy contacts.
     */
    @SuppressWarnings("unused")
    public String addPharmacy() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        String pharmacyId = request.getParameter(CONTACT_ID_PARAMETER);
        String demographic_no = request.getParameter("demographic_no");
        String preferredOrder = request.getParameter("preferredOrder");

        pharmacyManager.addPharmacy(loggedInInfo, Integer.parseInt(demographic_no), Integer.parseInt(pharmacyId),
                Integer.parseInt(preferredOrder));

        return "ajax";
    }

    /**
     * Removes a Pharmacy from a Demographic's preferred Pharmacy contact list.
     */
    @SuppressWarnings("unused")
    public String removePharmacy() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String demographicPharmacyId = request.getParameter(CONTACT_ID_PARAMETER);
        String demographic_no = request.getParameter("demographic_no");

        pharmacyManager.removePharmacy(loggedInInfo, Integer.parseInt(demographic_no), Integer.parseInt(demographicPharmacyId));

        return "ajax";
    }

    /**
     * Calls a blank addEditPharmacy.jsp page for adding a new Pharmacy to the
     * Pharmacy contact list.
     */
    @SuppressWarnings("unused")
    public String addPharmacyInfo() {
        String demographic_no = request.getParameter("demographic_no");
        request.setAttribute("demographic_no", demographic_no);
        request.setAttribute("method", "savePharmacyInfo");
        return "addEditPharmacy";
    }

    /**
     * Calls the addEditPharmacy.jsp page and populates it with the selected Pharmacy.
     */
    @SuppressWarnings("unused")
    public String editPharmacyInfo() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String pharmacyId = request.getParameter(CONTACT_ID_PARAMETER);
        String demographic_no = request.getParameter("demographic_no");
        PharmacyInfo pharmacyInfo = null;

        DemographicPharmacy demographicPharmacy = pharmacyManager.getDemographicPharmacy(loggedInInfo, Integer.parseInt(pharmacyId));
        if (demographicPharmacy != null) {
            pharmacyInfo = demographicPharmacy.getDetails();
        }
        request.setAttribute("method", "savePharmacyInfo");
        request.setAttribute("pharmacyInfo", pharmacyInfo);
        request.setAttribute("demographic_no", demographic_no);

        return "addEditPharmacy";
    }

    /**
     * Saves a new Pharmacy entry from an incoming PharmacyInfo DynaForm
     */
    @SuppressWarnings("unused")
    public String savePharmacyInfo() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
//        DynaValidatorForm dform = (DynaValidatorForm) form;
//        PharmacyInfo pharmacyInfo = (PharmacyInfo) dform.get("pharmacyInfo");

        logger.debug("PharmacyInfo bean: " + pharmacyInfo.toString());

        String demographic_no = request.getParameter("demographic_no");
        Integer currentPharmacyId = pharmacyInfo.getId();
        pharmacyInfo.setStatus(PharmacyInfo.ACTIVE);

        logger.debug("Incoming Pharmacy ID " + currentPharmacyId);

        if (demographic_no == null) {
            demographic_no = "";
        }

        if (currentPharmacyId == null) {
            currentPharmacyId = 0;
        }

        Integer newPharmacyId = pharmacyManager.savePharmacyInfo(loggedInInfo, pharmacyInfo);

        // Link to demographic if this is a new contact generated from a demographic.
        logger.info("Linking new Pharmacy {} to demographic {}", newPharmacyId, LogSafe.sanitize(demographic_no)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
        if (newPharmacyId > 0 && !demographic_no.isEmpty() && currentPharmacyId == 0) {
            pharmacyManager.addPharmacy(loggedInInfo, Integer.parseInt(demographic_no), newPharmacyId, 0);
        }

        request.setAttribute("demographic_no", demographic_no);
        request.setAttribute("id", newPharmacyId);

        return "addEditPharmacy";
    }

    /**
     * #---------------------------> HELPER METHODS #--------------------------->
     **/

    /*
     * Creates or updates a contact association owned by the supplied patient.
     * A zero association ID creates a row; a positive ID updates an owned row.
     * Existing rows retain their type. New rows use the supplied type or the model default.
     * Null sdm/ec values mean unselected; non-null values represent selected checkboxes.
     */
    private static final DemographicContact linkContactToDemographic(final String contactId, final Integer demographicContactId,
                                                                     final Integer demographic_no, final String role, final String type, final String note, final String category,
                                                                     final String sdm, final String ec, final Boolean consentToContact, final Boolean active, final LoggedInInfo loggedInInfo) {

        DemographicContact demographicContact;

        if (demographicContactId > 0) {
            demographicContact = requireOwnedContact(demographicContactId, demographic_no);
            requireContactCategory(demographicContact, category);
        } else {
            demographicContact = new DemographicContact();
            if (type != null) {
                demographicContact.setType(Integer.parseInt(type));
            }
        }

        demographicContact.setDemographicNo(demographic_no);
        demographicContact.setRole(role);

        demographicContact.setNote(note);
        demographicContact.setContactId(contactId);

        demographicContact.setCategory(category);

        if (sdm != null) {
            demographicContact.setSdm("true");
        } else {
            demographicContact.setSdm("");
        }

        if (ec != null) {
            demographicContact.setEc("true");
        } else {
            demographicContact.setEc("");
        }

        demographicContact.setFacilityId(loggedInInfo.getCurrentFacility().getId());
        demographicContact.setCreator(loggedInInfo.getLoggedInProviderNo());

        demographicContact.setConsentToContact(consentToContact);

        demographicContact.setActive(active);

        if (demographicContact.getId() == null) {
            demographicContactDao.persist(demographicContact);
        } else {
            demographicContactDao.merge(demographicContact);
        }

        return demographicContact;
    }



    /**
     * Return a list of of all the contacts in Oscar's database.
     * Contact, Professional Contact, and Professional Specialists
     */
    public static List<Contact> searchAllContacts(String searchMode, String orderBy, String keyword) {
        List<Contact> contacts = new ArrayList<Contact>();
        List<ProfessionalSpecialist> professionalSpecialistContact = professionalSpecialistDao.search(keyword);

        // if there is a future in adding personal contacts.
        // contacts.addAll( contactDao.search(searchMode, orderBy, keyword) );
        contacts.addAll(proContactDao.search(searchMode, orderBy, keyword));
        contacts.addAll(DemographicContactCreator.buildContact(professionalSpecialistContact));

        Collections.sort(contacts, DemographicContactCreator.byLastName);

        return contacts;
    }

    public static List<Contact> searchContacts(String searchMode, String orderBy, String keyword) {
        List<Contact> contacts = contactDao.search(searchMode, orderBy, keyword);
        return contacts;
    }

    public static List<ProfessionalContact> searchProContacts(String searchMode, String orderBy, String keyword) {
        List<ProfessionalContact> contacts = proContactDao.search(searchMode, orderBy, keyword);
        return contacts;
    }

    public static List<ProfessionalSpecialist> searchProfessionalSpecialists(String keyword) {
        List<ProfessionalSpecialist> contacts = professionalSpecialistDao.search(keyword);
        return contacts;
    }

    /**
     * #---------------------------> DEPRECATED METHODS #--------------------------->
     **/

    @Deprecated
    /**
     * use DemographicManager.getDemographicContacts
     */
    public static List<DemographicContact> getDemographicContacts(Demographic demographic) {
        List<DemographicContact> contacts = demographicContactDao.findByDemographicNo(demographic.getDemographicNo());
        return fillContactNames(contacts);
    }

    @Deprecated
    /**
     * use DemographicManager.getDemographicContacts
     */
    public static List<DemographicContact> getDemographicContacts(Demographic demographic, String category) {
        List<DemographicContact> contacts = demographicContactDao.findByDemographicNoAndCategory(demographic.getDemographicNo(), category);
        return fillContactNames(contacts);
    }

    @Deprecated
    /**
     * Use io.github.carlos_emr.carlos.managers.DemographicManager getHealthCareTeam
     */
    public static List<DemographicContact> fillContactNames(List<DemographicContact> contacts) {

        Provider provider;
        Contact contact;
        ProfessionalSpecialist professionalSpecialist;
        ContactSpecialty specialty = null;
        String providerFormattedName = "";
        String role = "";

        for (DemographicContact c : contacts) {
            role = c.getRole();
            if (role != null && !role.isEmpty() && StringUtils.isNumeric(role)) {

                /*
                 * Try to recover if the specialty is null,
                 * then set it to unknown if nothing could be found.
                 *
                 * First look in the contact info for a specialty.
                 */
                if (c.getType() == DemographicContact.TYPE_PROFESSIONALSPECIALIST
                        && c.getContactId() != null) {
                    ProfessionalSpecialist tempProfessionalSpecialist = professionalSpecialistDao.find(Integer.parseInt(c.getContactId()));
                    String specialtyType = null;
                    if (tempProfessionalSpecialist != null) {
                        specialtyType = tempProfessionalSpecialist.getSpecialtyType();
                    }
                    if (specialtyType != null && !specialtyType.isEmpty() && StringUtils.isNumeric(specialtyType)) {
                        specialty = contactSpecialtyDao.find(Integer.parseInt(specialtyType));
                    }
                }

                if (specialty == null) {
                    specialty = contactSpecialtyDao.find(Integer.parseInt(c.getRole().trim()));
                }

                /*
                 * Try to set "UNKNOWN" if that fails.
                 */
                if (specialty == null) {
                    specialty = contactSpecialtyDao.findBySpecialty("UNKNOWN");
                }

                /*
                 * use a text value as a last resort.
                 */
                if (specialty == null) {
                    c.setRole("UNKNOWN");
                } else {
                    c.setRole(specialty.getSpecialty());
                }

            }

            if (c.getType() == DemographicContact.TYPE_DEMOGRAPHIC) {
                c.setContactName(demographicDao.getClientByDemographicNo(Integer.parseInt(c.getContactId())).getFormattedName());
            }

            if (c.getType() == DemographicContact.TYPE_PROVIDER) {
                provider = providerDao.getProvider(c.getContactId());
                if (provider != null) {
                    providerFormattedName = provider.getFormattedName();
                }
                if (StringUtils.isBlank(providerFormattedName)) {
                    providerFormattedName = "Error: Contact Support";
                    logger.error("Formatted name for provder was not avaialable. Contact number: " + c.getContactId());
                }
                c.setContactName(providerFormattedName);
                contact = new ProfessionalContact();
                contact.setWorkPhone("internal");
                contact.setFax("internal");
                c.setDetails(contact);
            }

            if (c.getType() == DemographicContact.TYPE_CONTACT) {
                contact = contactDao.find(Integer.parseInt(c.getContactId()));
                c.setContactName(contact.getFormattedName());
                c.setDetails(contact);
            }

            if (c.getType() == DemographicContact.TYPE_PROFESSIONALSPECIALIST) {
                professionalSpecialist = professionalSpecialistDao.find(Integer.parseInt(c.getContactId()));
                c.setContactName(professionalSpecialist.getFormattedName());
                contact = buildContact(professionalSpecialist);
                c.setDetails(contact);
            }
        }

        return contacts;
    }


    @Deprecated
    /**
     * Use HealthCareTeamCreator.buildContact
     * @param contact
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

    @Deprecated
    /**
     * Use HealthCareTeamCreator.buildContact
     * @param contact
     */
    private static final Contact buildContact(final Object contactobject) {
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

            if (address.contains(",")) {
                String[] addressArray = address.split(",");
                address = addressArray[0].trim();
                if (addressArray.length > 3) {
                    city = addressArray[1].trim();
                    province = addressArray[2].trim();
                    country = addressArray[3].trim();
                } else if (addressArray.length > 2) {
                    province = addressArray[1].trim();
                    country = addressArray[2].trim();
                } else {
                    province = addressArray[1].trim();
                    country = "";
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

    @Deprecated
    /**
     * Use HealthCareTeamCreator.byLastName
     * @param contact
     */
    public static Comparator<Contact> byLastName = new Comparator<Contact>() {
        public int compare(Contact contact1, Contact contact2) {
            String lastname1 = contact1.getLastName().toUpperCase();
            String lastname2 = contact2.getLastName().toUpperCase();
            return lastname1.compareTo(lastname2);
        }
    };

    private Contact contact;
    private ProfessionalContact pcontact;

    @StrutsParameter(depth = 1)
    public Contact getContact() {
        return contact;
    }

    @StrutsParameter
    public void setContact(Contact contact) {
        this.contact = contact;
    }

    @StrutsParameter(depth = 1)
    public ProfessionalContact getPcontact() {
        return pcontact;
    }

    @StrutsParameter
    public void setPcontact(ProfessionalContact pcontact) {
        this.pcontact = pcontact;
    }

    private PharmacyInfo pharmacyInfo;

    @StrutsParameter(depth = 1)
    public PharmacyInfo getPharmacyInfo() {
        return pharmacyInfo;
    }

    @StrutsParameter
    public void setPharmacyInfo(PharmacyInfo pharmacyInfo) {
        this.pharmacyInfo = pharmacyInfo;
    }
}
