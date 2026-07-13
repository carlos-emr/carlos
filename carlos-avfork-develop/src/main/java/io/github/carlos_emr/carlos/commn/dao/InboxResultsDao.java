/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.commn.dao;

import java.util.ArrayList;
import java.util.Date;

import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;

public interface InboxResultsDao {

    public ArrayList populateHL7ResultsData(String demographicNo, String consultationId, boolean attached);

    public boolean isSentToProvider(String docNo, String providerNo);

    public ArrayList populateDocumentResultsData(String providerNo, String demographicNo, String patientFirstName,
                                                 String patientLastName, String patientHealthNumber, String status);

    public ArrayList<LabResultData> populateDocumentResultsData(String providerNo, String demographicNo, String patientFirstName,
                                                                String patientLastName, String patientHealthNumber, String status, boolean isPaged, Integer page,
                                                                Integer pageSize, boolean mixLabsAndDocs, Boolean isAbnormal);

    public ArrayList<LabResultData> populateDocumentResultsData(String providerNo, String demographicNo, String patientFirstName,
                                                                String patientLastName, String patientHealthNumber, String status, boolean isPaged, Integer page,
                                                                Integer pageSize, boolean mixLabsAndDocs, Boolean isAbnormal, Date startDate, Date endDate);

    //String getStringValue(Object value);
}
 