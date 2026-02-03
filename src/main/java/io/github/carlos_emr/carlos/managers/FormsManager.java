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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public interface FormsManager {


    public static final String EFORM = "eform";
    public static final String FORM = "form";

    public List<EForm> findByStatus(LoggedInInfo loggedInInfo, boolean status, EFormSortOrder sortOrder);

    public List<EForm> getEfromInGroupByGroupName(LoggedInInfo loggedInInfo, String groupName);


    public List<String> getGroupNames();


    public List<EFormData> findByDemographicId(LoggedInInfo loggedInInfo, Integer demographicId);

    public List<EncounterForm> getAllEncounterForms();

    public List<EncounterForm> getSelectedEncounterForms();

    public List<PatientForm> getEncounterFormsbyDemographicNumber(LoggedInInfo loggedInInfo, Integer demographicId, boolean getAllVersions, boolean getOnlyPDFReadyForms);

    public Integer saveFormDataAsEDoc(LoggedInInfo loggedInInfo, FormTransportContainer formTransportContainer);

    public Path renderForm(HttpServletRequest request, HttpServletResponse response, Integer formId, Integer demographicNo) throws PDFGenerationException;

    public Path renderForm(LoggedInInfo loggedInInfo, FormTransportContainer formTransportContainer);

    public Path renderForm(HttpServletRequest request, HttpServletResponse response, EctFormData.PatientForm form) throws PDFGenerationException;

    public PatientForm getFormById(LoggedInInfo loggedInInfo, Integer formId, Integer demographicNo);
}

