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


package io.github.carlos_emr.carlos.commn.dao;

import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.Document;

import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;

/**
 * DAO interface for document management operations.
 *
 * @since 2001
 */

public interface DocumentResultsDao {
    /**
     * Is Sent To Valid Provider.
     *
     * @param docNo String the docNo
     * @return boolean
     */
    public boolean isSentToValidProvider(String docNo);

    /**
     * Is Sent To Provider.
     *
     * @param docNo String the docNo
     * @param providerNo String the providerNo
     * @return boolean
     */
    public boolean isSentToProvider(String docNo, String providerNo);

    public ArrayList<LabResultData> populateDocumentResultsDataOfAllProviders(String providerNo, String demographicNo,
                                                                              String status);

    //retrieve documents belonging to a providers
    public ArrayList<LabResultData> populateDocumentResultsDataLinkToProvider(String providerNo, String demographicNo,
                                                                              String status);

    //retrieve all documents from database
    /**
     * Populate Document Results Data.
     *
     * @param providerNo String the providerNo
     * @param demographicNo String the demographicNo
     * @param status String the status
     * @return ArrayList<LabResultData>
     */
    public ArrayList<LabResultData> populateDocumentResultsData(String providerNo, String demographicNo, String status);

    /**
     * Get Photos By Appointment No.
     *
     * @param appointmentNo int the appointmentNo
     * @return List<Document>
     */
    public List<Document> getPhotosByAppointmentNo(int appointmentNo);
}
 