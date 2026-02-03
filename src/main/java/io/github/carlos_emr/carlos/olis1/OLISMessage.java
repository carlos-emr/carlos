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

package io.github.carlos_emr.carlos.olis1;

import io.github.carlos_emr.carlos.commn.model.Provider;

import io.github.carlos_emr.carlos.olis1.queries.Query;
import io.github.carlos_emr.carlos.olis1.segments.DSCSegment;
import io.github.carlos_emr.carlos.olis1.segments.MSHSegment;
import io.github.carlos_emr.carlos.olis1.segments.SPRSegment;
import io.github.carlos_emr.carlos.olis1.segments.ZSHSegment;

public class OLISMessage {

    private MSHSegment mshSegment;
    private ZSHSegment zshSegment;
    private SPRSegment sprSegment;
    private DSCSegment dscSegment = null;

    public OLISMessage() {

    }

    public OLISMessage(Provider provider, Query query) {
        mshSegment = new MSHSegment(query.getQueryType());
        zshSegment = new ZSHSegment(provider);
        sprSegment = new SPRSegment(query.getQueryType(), query);
    }

    public OLISMessage(Provider provider, Query query, String continuationPointer) {
        mshSegment = new MSHSegment(query.getQueryType());
        zshSegment = new ZSHSegment(provider);
        sprSegment = new SPRSegment(query.getQueryType(), query);
        dscSegment = new DSCSegment(continuationPointer);
    }

    public String getTransactionId() {
        return mshSegment.getUuidString();
    }

    public String getOlisHL7String() {
        String output = "";

        output += mshSegment.getSegmentHL7String() + "\r";
        output += zshSegment.getSegmentHL7String() + "\r";
        output += sprSegment.getSegmentHL7String();

        if (dscSegment != null)
            output += "\r" + dscSegment.getSegmentHL7String();

        return output;
    }

}
