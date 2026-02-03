/**
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.webserv.rest.conversion;

import io.github.carlos_emr.carlos.commn.model.DocumentReview;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DocumentReviewTo1;

public class DocumentReviewConverter extends AbstractConverter<DocumentReview, DocumentReviewTo1> {
    @Override
    public DocumentReview getAsDomainObject(LoggedInInfo loggedInInfo, DocumentReviewTo1 t) throws ConversionException {
        DocumentReview d = new DocumentReview();

        d.setId(t.getId());
        d.setDocumentNo(t.getDocumentNo());
        d.setProviderNo(t.getProviderNo());
        d.setDateTimeReviewed(t.getDateTimeReviewed());

        if (t.getReviewer() != null) {
            d.setReviewer(new ProviderConverter().getAsDomainObject(loggedInInfo, t.getReviewer()));
        }

        return d;
    }

    @Override
    public DocumentReviewTo1 getAsTransferObject(LoggedInInfo loggedInInfo, DocumentReview d) throws ConversionException {
        DocumentReviewTo1 t = new DocumentReviewTo1();

        t.setId(d.getId());
        t.setDocumentNo(d.getDocumentNo());
        t.setProviderNo(d.getProviderNo());
        t.setDateTimeReviewed(d.getDateTimeReviewed());

        if (d.getReviewer() != null) {
            t.setReviewer(new ProviderConverter().getAsTransferObject(loggedInInfo, d.getReviewer()));
        }

        return t;
    }
}
