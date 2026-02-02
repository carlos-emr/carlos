//CHECKSTYLE:OFF
/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 */

package io.github.carlos_emr.carlos.commn.dao;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Document;

import io.github.carlos_emr.carlos.documentManager.EDocUtil.EDocSort;

public interface DocumentDao extends AbstractDao<Document> {

    public enum Module {
        DEMOGRAPHIC;

        public String getName() {
            return this.name().toLowerCase();
        }
    }

    public enum DocumentType {
        CONSULT, LAB, ECONSULT;

        public String getName() {
            return this.name().toLowerCase();
        }
    }

    public List<Object[]> getCtlDocsAndDocsByDemoId(Integer demoId, Module moduleName, DocumentType docType);

    public List<Document> findActiveByDocumentNo(Integer demoId);

    public List<Object[]> findCtlDocsAndDocsByModuleDocTypeAndModuleId(Module module, DocumentType docType,
                                                                       Integer moduleId);

    public List<Object[]> findCtlDocsAndDocsByModuleAndModuleId(Module module, Integer moduleId);

    public List<Object[]> findDocsAndConsultDocsByConsultId(Integer consultationId);

    public List<Object[]> findDocsAndEFormDocsByFdid(Integer fdid);

    public List<Object[]> findDocsAndConsultResponseDocsByConsultId(Integer consultationId);

    public List<Object[]> findCtlDocsAndDocsByDocNo(Integer documentNo);

    public List<Object[]> findCtlDocsAndDocsByModuleCreatorResponsibleAndDates(Module module, String providerNo,
                                                                               String responsible, Date from, Date to, boolean unmatchedDemographics);

    public List<Object[]> findConstultDocsDocsAndProvidersByModule(Module module, Integer moduleId);

    public Integer findMaxDocNo();

    public Document getDocument(String documentNo);

    public Demographic getDemoFromDocNo(String docNo);

    public int getNumberOfDocumentsAttachedToAProviderDemographics(String providerNo, Date startDate, Date endDate);

    public void subtractPages(String documentNo, Integer i);

    public List<Document> findByDemographicId(String demoNo);

    public List<Object[]> findDocuments(String module, String moduleid, String docType, boolean includePublic,
                                        boolean includeDeleted, boolean includeActive, EDocSort sort, Date since);

    public List<Document> findByUpdateDate(Date updatedAfterThisDateExclusive, int itemsToReturn);

    public List<Document> findByDemographicUpdateDate(Integer demographicId, Date updatedAfterThisDateInclusive);

    public List<Document> findByDemographicUpdateAfterDate(Integer demographicId, Date updatedAfterThisDate);

    public List<Document> findByProgramProviderDemographicUpdateDate(Integer programId, String providerNo,
                                                                     Integer demographicId, Date updatedAfterThisDateExclusive, int itemsToReturn);

    public List<Integer> findDemographicIdsSince(Date since);

    public List<Document> findByDoctype(String docType);

    public List<Document> findByDoctypeAndProviderNo(String docType, String provider_no, Integer isPublic);

    public List<Document> findByDemographicAndDoctype(int demographicId, DocumentType documentType);

    public Document findByDemographicAndFilename(int demographicId, String fileName);
}
