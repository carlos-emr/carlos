/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 */

package io.github.carlos_emr.carlos.olis1.queries;

import io.github.carlos_emr.carlos.olis1.parameters.OBR22;
import io.github.carlos_emr.carlos.olis1.parameters.ORC21;
import io.github.carlos_emr.carlos.olis1.parameters.QRD7;
import io.github.carlos_emr.carlos.olis1.parameters.ZPD1;

/**
 * Z06 - Retrieve Laboratory Information Updates for Ordering Facility
 *
 * @author jen
 */
public class Z06Query extends Query {

    private OBR22 startEndTimestamp = new OBR22(); // mandatory
    private QRD7 quantityLimitedRequest = null;
    private ORC21 orderingFacilityId = new ORC21(); // mandatory

    @Override
    public String getQueryHL7String() {
        String query = "";

        if (startEndTimestamp != null)
            query += startEndTimestamp.toOlisString() + "~";

        if (quantityLimitedRequest != null)
            query += quantityLimitedRequest.toOlisString() + "~";

        if (orderingFacilityId != null)
            query += orderingFacilityId.toOlisString() + "~";

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

    public void setOrderingFacilityId(ORC21 orderingFacilityId) {
        this.orderingFacilityId = orderingFacilityId;
    }

    @Override
    public QueryType getQueryType() {
        return QueryType.Z06;
    }

    @Override
    public void setConsentToViewBlockedInformation(ZPD1 consentToViewBlockedInformation) {
        throw new RuntimeException("Not valid for this type of query.");
    }
}
