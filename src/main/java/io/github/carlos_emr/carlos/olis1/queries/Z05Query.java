//CHECKSTYLE:OFF
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
import io.github.carlos_emr.carlos.olis1.parameters.QRD7;
import io.github.carlos_emr.carlos.olis1.parameters.ZBR8;
import io.github.carlos_emr.carlos.olis1.parameters.ZPD1;

/**
 * Z05 - Retrieve Laboratory Information Updates for Destination Laboratory
 *
 * @author jen
 */
public class Z05Query extends Query {

    private OBR22 startEndTimestamp = new OBR22(); // mandatory
    private QRD7 quantityLimitedRequest = null;
    private ZBR8 destinationLaboratory = new ZBR8(); // mandatory

    @Override
    public String getQueryHL7String() {
        String query = "";

        if (startEndTimestamp != null)
            query += startEndTimestamp.toOlisString() + "~";

        if (quantityLimitedRequest != null)
            query += quantityLimitedRequest.toOlisString() + "~";

        if (destinationLaboratory != null)
            query += destinationLaboratory.toOlisString() + "~";

        return query;
    }

    public void setStartEndTimestamp(OBR22 startEndTimestamp) {
        this.startEndTimestamp = startEndTimestamp;
    }

    public void setQuantityLimitedRequest(QRD7 quantityLimitedRequest) {
        this.quantityLimitedRequest = quantityLimitedRequest;
    }

    public void setDestinationLaboratory(ZBR8 destinationLaboratory) {
        this.destinationLaboratory = destinationLaboratory;
    }

    @Override
    public QueryType getQueryType() {
        return QueryType.Z05;
    }

    @Override
    public void setConsentToViewBlockedInformation(ZPD1 consentToViewBlockedInformation) {
        throw new RuntimeException("Not valid for this type of query.");
    }
}
