/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.olis1.queries;

import java.util.LinkedList;
import java.util.List;

import io.github.carlos_emr.carlos.olis1.parameters.OBR22;
import io.github.carlos_emr.carlos.olis1.parameters.OBR4;
import io.github.carlos_emr.carlos.olis1.parameters.OBX3;
import io.github.carlos_emr.carlos.olis1.parameters.QRD7;
import io.github.carlos_emr.carlos.olis1.parameters.ZPD1;
import io.github.carlos_emr.carlos.olis1.parameters.ZRP1;

/**
 * Z04 - Retrieve Laboratory Information Updates for Practitioner
 *
 * @author jen
 */
public class Z04Query extends Query {

    private OBR22 startEndTimestamp = new OBR22(); // mandatory
    private QRD7 quantityLimitedRequest = null;
    private ZRP1 requestingHic = new ZRP1(); // mandatory
    private List<OBR4> testRequestCodeList = new LinkedList<OBR4>();
    private List<OBX3> testResultCodeList = new LinkedList<OBX3>();

    @Override
    public String getQueryHL7String() {
        String query = "";

        if (startEndTimestamp != null)
            query += startEndTimestamp.toOlisString() + "~";

        if (quantityLimitedRequest != null)
            query += quantityLimitedRequest.toOlisString() + "~";

        if (requestingHic != null)
            query += requestingHic.toOlisString() + "~";

        for (OBR4 testRequestCode : testRequestCodeList)
            query += testRequestCode.toOlisString() + "~";

        for (OBX3 testResultCode : testResultCodeList)
            query += testResultCode.toOlisString() + "~";

        if (query.endsWith("~")) {
            query = query.substring(0, query.length() - 1);
        }
        return query;
    }

    public void setStartEndTimestamp(OBR22 startEndTimestamp) {
        this.startEndTimestamp = startEndTimestamp;
    }

    public void setQuantityLimitedRequest(QRD7 quantityLimitedRequest) {
        this.quantityLimitedRequest = quantityLimitedRequest;
    }

    public void setRequestingHic(ZRP1 requestingHic) {
        this.requestingHic = requestingHic;
    }

    public void setTestRequestCodeList(List<OBR4> testRequestCodeList) {
        this.testRequestCodeList = testRequestCodeList;
    }

    public void setTestResultCodeList(List<OBX3> testResultCodeList) {
        this.testResultCodeList = testResultCodeList;
    }

    public void addToTestRequestCodeList(OBR4 testRequestCode) {
        this.testRequestCodeList.add(testRequestCode);
    }

    public void addToTestResultCodeList(OBX3 testResultCode) {
        this.testResultCodeList.add(testResultCode);
    }

    public String getRequestingHicIdNumber() {
        return requestingHic.getIdNumber();
    }

    @Override
    public QueryType getQueryType() {
        return QueryType.Z04;
    }

    @Override
    public void setConsentToViewBlockedInformation(ZPD1 consentToViewBlockedInformation) {
        throw new RuntimeException("Not valid for this type of query.");
    }
}
