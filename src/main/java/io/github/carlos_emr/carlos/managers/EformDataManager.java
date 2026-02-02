//CHECKSTYLE:OFF
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;

import io.github.carlos_emr.carlos.eform.data.EForm;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;

public interface EformDataManager {

    public Integer saveEformData(LoggedInInfo loggedInInfo, EForm eform);

    /**
     * Saves an form as PDF EDoc.
     * Returns the Eform id that was saved.
     */
    public Integer saveEformDataAsEDoc(LoggedInInfo loggedInInfo, String fdid);

    public Integer saveEFormWithAttachmentsAsEDoc(LoggedInInfo loggedInInfo, String fdid, String demographicId, Path eFormPDFPath) throws PDFGenerationException;

    public EFormData findByFdid(LoggedInInfo loggedInInfo, Integer fdid);

    /**
     * Saves an form as PDF in a temp directory.
     * <p>
     * Path to a temp file is returned. Remember to change the .tmp filetype and to delete the tmp file when finished.
     */
    public Path createEformPDF(LoggedInInfo loggedInInfo, int fdid) throws PDFGenerationException;


    /**
     * Get all current eForms by demographic number but do not include the HTML data.
     * This is a good method for getting just the list and status of eForms. It's a little lighter on the database.
     * <p>
     * Returns a map - not an entity
     */
    public List<Map<String, Object>> findCurrentByDemographicIdNoData(LoggedInInfo loggedInInfo, Integer demographicId);

    public ArrayList<HashMap<String, ? extends Object>> getHRMDocumentsAttachedToEForm(LoggedInInfo loggedInInfo, String fdid, String demographicId);

    public List<EctFormData.PatientForm> getFormsAttachedToEForm(LoggedInInfo loggedInInfo, String fdid, String demographicId);

    public void removeEFormData(LoggedInInfo loggedInInfo, String fdid);

}
