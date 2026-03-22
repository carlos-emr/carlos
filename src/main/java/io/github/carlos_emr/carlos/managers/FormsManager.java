/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.managers;


import java.nio.file.Path;
import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.EFormDao.EFormSortOrder;
import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;

import io.github.carlos_emr.carlos.form.util.FormTransportContainer;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;
import io.github.carlos_emr.carlos.encounter.data.EctFormData.PatientForm;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Service interface for managing clinical forms and eForms in the CARLOS EMR system.
 *
 * <p>Provides operations for retrieving form definitions, form group management,
 * patient form data access, PDF rendering, and saving form data as electronic
 * documents. Supports both legacy encounter forms and modern eForms.</p>
 *
 * @see FormsManagerImpl
 * @see io.github.carlos_emr.carlos.commn.model.EForm
 * @see io.github.carlos_emr.carlos.commn.model.EncounterForm
 * @since 2026-03-17
 */
public interface FormsManager {

    /** Form type constant for electronic forms. */
    public static final String EFORM = "eform";
    /** Form type constant for legacy encounter forms. */
    public static final String FORM = "form";

    /**
     * Retrieves eForms filtered by active/inactive status with the specified sort order.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param status boolean true for active eForms, false for inactive
     * @param sortOrder EFormSortOrder the sort order for the results
     * @return List of EForm records matching the status filter
     */
    public List<EForm> findByStatus(LoggedInInfo loggedInInfo, boolean status, EFormSortOrder sortOrder);

    /**
     * Retrieves eForms belonging to a specific form group.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param groupName String the eForm group name
     * @return List of EForm records in the specified group
     */
    public List<EForm> getEfromInGroupByGroupName(LoggedInInfo loggedInInfo, String groupName);

    /**
     * Retrieves all distinct eForm group names.
     *
     * @return List of String group names
     */
    public List<String> getGroupNames();

    /**
     * Retrieves submitted eForm data for a specific patient.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param demographicId Integer the patient demographic number
     * @return List of EFormData submissions for the patient
     */
    public List<EFormData> findByDemographicId(LoggedInInfo loggedInInfo, Integer demographicId);

    /**
     * Retrieves all encounter form definitions.
     *
     * @return List of all EncounterForm records
     */
    public List<EncounterForm> getAllEncounterForms();

    /**
     * Retrieves encounter forms that are marked as selected/active.
     *
     * @return List of selected EncounterForm records
     */
    public List<EncounterForm> getSelectedEncounterForms();

    /**
     * Retrieves patient-specific encounter form instances for a demographic.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param demographicId Integer the patient demographic number
     * @param getAllVersions boolean if true, returns all versions; if false, only the latest
     * @param getOnlyPDFReadyForms boolean if true, only returns forms that can be rendered as PDF
     * @return List of PatientForm instances for the patient
     */
    public List<PatientForm> getEncounterFormsbyDemographicNumber(LoggedInInfo loggedInInfo, Integer demographicId, boolean getAllVersions, boolean getOnlyPDFReadyForms);

    /**
     * Saves form data as an electronic document (EDoc) in the document library.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param formTransportContainer FormTransportContainer the form data and metadata
     * @return Integer the saved document identifier
     */
    public Integer saveFormDataAsEDoc(LoggedInInfo loggedInInfo, FormTransportContainer formTransportContainer);

    /**
     * Renders a form as a PDF document using the servlet request/response context.
     *
     * @param request HttpServletRequest the current HTTP request
     * @param response HttpServletResponse the current HTTP response
     * @param formId Integer the form identifier
     * @param demographicNo Integer the patient demographic number
     * @return Path to the generated temporary PDF file
     * @throws PDFGenerationException if PDF rendering fails
     */
    public Path renderForm(HttpServletRequest request, HttpServletResponse response, Integer formId, Integer demographicNo) throws PDFGenerationException;

    /**
     * Renders a form as a PDF document from a form transport container.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param formTransportContainer FormTransportContainer the form data and metadata
     * @return Path to the generated temporary PDF file
     */
    public Path renderForm(LoggedInInfo loggedInInfo, FormTransportContainer formTransportContainer);

    /**
     * Renders a patient form instance as a PDF document.
     *
     * @param request HttpServletRequest the current HTTP request
     * @param response HttpServletResponse the current HTTP response
     * @param form PatientForm the patient form instance to render
     * @return Path to the generated temporary PDF file
     * @throws PDFGenerationException if PDF rendering fails
     */
    public Path renderForm(HttpServletRequest request, HttpServletResponse response, EctFormData.PatientForm form) throws PDFGenerationException;

    /**
     * Retrieves a specific patient form by form identifier and demographic number.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param formId Integer the form identifier
     * @param demographicNo Integer the patient demographic number
     * @return PatientForm the matching form instance, or null if not found
     */
    public PatientForm getFormById(LoggedInInfo loggedInInfo, Integer formId, Integer demographicNo);
}

