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


/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package io.github.carlos_emr.carlos.documentManager;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Factory for creating {@link EDoc} instances with properly formatted metadata in the
 * CARLOS EMR document management system.
 *
 * <p>Provides a convenience method for constructing EDoc objects from typed parameters
 * (using {@link Date} objects instead of raw strings), automatically formatting dates
 * to the expected string formats used by the persistence layer.
 *
 * <p>Also defines the standard enumerations for document status ({@link Status}) and
 * module context ({@link Module}) used throughout the document management subsystem.
 *
 * @see EDoc
 * @see EDocUtil
 * @since 2006-07-27
 */
public class EDocFactory {
    public enum Status {
        ACTIVE('A'),
        DELETED('D'),
        SENT('S');

        char statusCharacter;

        private Status(char statusCharacter) {
            this.statusCharacter = statusCharacter;
        }

        public char getStatusCharacter() {
            return statusCharacter;
        }

        @Override
        public String toString() {
            return statusCharacter + "";
        }
    }

    public enum Module {
        demographic, provider;
    }

    /**
     * Creates a new {@link EDoc} instance with typed parameters, formatting dates to their
     * expected string representations for the persistence layer.
     *
     * @param description String the document description/title
     * @param type String the document type
     * @param fileName String the document filename
     * @param contentType String the MIME content type (e.g., "application/pdf")
     * @param html String the HTML content for HTML-type documents
     * @param creatorId String the provider number of the document creator
     * @param responsibleId String the provider number of the responsible provider
     * @param source String the document source
     * @param status Status the document status enum value
     * @param observationDate Date the clinical observation date
     * @param reviewerId String the provider number of the reviewer
     * @param reviewDateTime Date the review date/time
     * @param module Module the module context (demographic or provider)
     * @param moduleId String the module-specific identifier
     * @return EDoc the constructed document object with content type set
     */
    public EDoc createEDoc(String description, String type, String fileName, String contentType, String html, String creatorId, String responsibleId, String source, Status status, Date observationDate, String reviewerId, Date reviewDateTime, Module module, String moduleId) {
        SimpleDateFormat reviewDateTimeFormat = new SimpleDateFormat(EDocUtil.REVIEW_DATETIME_FORMAT);
        SimpleDateFormat observationDateFormat = new SimpleDateFormat(EDocUtil.DMS_DATE_FORMAT);
        String reviewDateTimeStr = reviewDateTimeFormat.format(reviewDateTime);
        String observationDateStr = observationDateFormat.format(observationDate);
        EDoc eDoc = new EDoc(description, type, fileName, html, creatorId, responsibleId, source, status.getStatusCharacter(), observationDateStr, reviewerId, reviewDateTimeStr, module + "", moduleId);
        eDoc.setContentType(contentType);
        return eDoc;
    }
}
