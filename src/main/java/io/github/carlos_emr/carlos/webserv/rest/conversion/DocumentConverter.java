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

import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DocumentReviewTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DocumentTo1;

public class DocumentConverter extends AbstractConverter<Document, DocumentTo1> {
    @Override
    public Document getAsDomainObject(LoggedInInfo loggedInInfo, DocumentTo1 t) throws ConversionException {
        Document d = new Document();

        d.setDocumentNo(t.getId());
        d.setDoctype(t.getType());
        d.setDocClass(t.getDocClass());
        d.setDocSubClass(t.getSubClass());
        d.setDocdesc(t.getDescription());
        d.setDocxml(t.getXml());
        d.setDocfilename(t.getFileName());
        d.setDoccreator(t.getCreator());
        d.setResponsible(t.getResponsible());
        d.setSource(t.getSource());
        d.setSourceFacility(t.getSourceFacility());
        d.setProgramId(t.getProgramId());
        d.setUpdatedatetime(t.getUpdateDateTime());
        d.setStatus(t.getStatus());
        d.setContenttype(t.getContentType());
        d.setContentdatetime(t.getContentDateTime());
        d.setReportMedia(t.getReportMedia());
        d.setSentDateTime(t.getSentDateTime());
        d.setPublic1(t.getPublic1());
        d.setObservationdate(t.getObservationDate());
        int numberOfPages = t.getNumberOfPages() != null ? t.getNumberOfPages() : 0;
        d.setNumberofpages(numberOfPages);
        d.setAppointmentNo(t.getAppointmentNo());
        d.setAbnormal(t.getAbnormal());
        d.setRestrictToProgram(t.getRestrictToProgram());

        if (t.getReviews() != null) {
            DocumentReviewConverter reviewConverter = new DocumentReviewConverter();
            for (DocumentReviewTo1 reviewTo1 : t.getReviews()) {
                d.getReviews().add(reviewConverter.getAsDomainObject(loggedInInfo, reviewTo1));
            }
        }

        return d;
    }

    @Override
    public DocumentTo1 getAsTransferObject(LoggedInInfo loggedInInfo, Document d) throws ConversionException {
        DocumentTo1 t = new DocumentTo1();

        t.setId(d.getDocumentNo());
        t.setType(d.getDoctype());
        t.setDocClass(d.getDocClass());
        t.setSubClass(d.getDocSubClass());
        t.setDescription(d.getDocdesc());
        t.setXml(d.getDocxml());
        t.setFileName(d.getDocfilename());
        t.setCreator(d.getDoccreator());
        t.setResponsible(d.getResponsible());
        t.setSource(d.getSource());
        t.setSourceFacility(d.getSourceFacility());
        t.setProgramId(d.getProgramId());
        t.setUpdateDateTime(d.getUpdatedatetime());
        t.setStatus(d.getStatus());
        t.setContentType(d.getContenttype());
        t.setContentDateTime(d.getContentdatetime());
        t.setReportMedia(d.getReportMedia());
        t.setSentDateTime(d.getSentDateTime());
        t.setPublic1(d.getPublic1());
        t.setObservationDate(d.getObservationdate());
        t.setNumberOfPages(d.getNumberofpages());
        t.setAppointmentNo(d.getAppointmentNo());
        t.setAbnormal(d.isAbnormal());
        t.setRestrictToProgram(d.isRestrictToProgram());

        if (d.getReviews() != null) {
            DocumentReviewConverter reviewConverter = new DocumentReviewConverter();
            t.setReviews(reviewConverter.getAllAsTransferObjects(loggedInInfo, d.getReviews()));
        }

        return t;
    }
}
