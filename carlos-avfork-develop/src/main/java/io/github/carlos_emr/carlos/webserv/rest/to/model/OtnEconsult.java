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
package io.github.carlos_emr.carlos.webserv.rest.to.model;

import java.io.Serializable;
import java.util.Date;

import jakarta.xml.bind.annotation.XmlRootElement;

import io.github.carlos_emr.carlos.commn.dao.DocumentDao.DocumentType;

@XmlRootElement
public class OtnEconsult implements Serializable {

    private static final DocumentType docType = DocumentType.ECONSULT;
    private static final char docTypeCode = 'E';
    private final String defaultDocDescription = "OTN eConsult Consultation";

    private String contentType;
    private Integer demographicNo;
    private String fileName;
    private byte[] contents;
    private Date importDate;
    private Integer consultId;
    private String docDescription;

    /**
     * Default
     */
    public OtnEconsult() {
        // default
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Integer getDemographicNo() {
        return demographicNo;
    }

    public void setDemographicNo(Integer demographicNo) {
        this.demographicNo = demographicNo;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public byte[] getContents() {
        return contents;
    }

    public void setContents(byte[] contents) {
        this.contents = contents;
    }

    public Date getImportDate() {
        return importDate;
    }

    public void setImportDate(Date importDate) {
        this.importDate = importDate;
    }

    public Integer getConsultId() {
        return consultId;
    }

    public void setConsultId(Integer consultId) {
        this.consultId = consultId;
    }

    public static DocumentType getDoctype() {
        return docType;
    }

    public static char getDoctypecode() {
        return docTypeCode;
    }

    public String getDocDescription() {
        if (this.docDescription == null || this.docDescription.isEmpty()) {
            return defaultDocDescription;
        }
        return docDescription;
    }

    public void setDocDescription(String docDescription) {
        this.docDescription = docDescription;
    }

}
