//CHECKSTYLE:OFF
/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 */

package io.github.carlos_emr.carlos.olis1.segments;

import java.util.Random;

import io.github.carlos_emr.carlos.olis1.queries.Query;
import io.github.carlos_emr.carlos.olis1.queries.QueryType;

public class SPRSegment implements Segment {

    private QueryType queryType;
    private Query query;
    private Random r = new Random();
    private Integer transactionId = r.nextInt(999999999);

    public Integer getTransactionId() {
        return transactionId;
    }

    public SPRSegment(QueryType queryType, Query query) {
        this.queryType = queryType;
        this.query = query;
    }

    private String getStoredProcedureName() {
        if (queryType == QueryType.Z01)
            return "Z_QryLabInfoForPatientID";
        else if (queryType == QueryType.Z02)
            return "Z_QryLabInfoForOrderID";
        else if (queryType == QueryType.Z04)
            return "Z_QryLabInfoUpdatesForPractitionerID";
        else if (queryType == QueryType.Z05)
            return "Z_QryLabInfoUpdatesForLaboratoryID";
        else if (queryType == QueryType.Z06)
            return "Z_QryLabInfoUpdatesForHCFID";
        else if (queryType == QueryType.Z07)
            return "Z_QryLabInfoByPHBReportFlag";
        else if (queryType == QueryType.Z08)
            return "Z_QryLabInfoByCCOReportFlag";
        else if (queryType == QueryType.Z50)
            return "Z_IDPatientByNameSexDoB";

        return "";
    }

    @Override
    public String getSegmentHL7String() {
        String queryHl7String = query.getQueryHL7String();

        if (queryHl7String.charAt(queryHl7String.length() - 1) == '~')
            queryHl7String = queryHl7String.substring(0, queryHl7String.length() - 2);

        return "SPR|" + transactionId + "|R|" + getStoredProcedureName() + "^^HL70471|" + query.getQueryHL7String();
    }

}
