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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.commn.dao;

import java.util.Date;
import java.util.List;

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

    /**
     * Returns distinct document descriptions (docdesc) that contain the given keyword,
     * ordered descending by document number (most recently created first).
     */
    public List<Object[]> getCtlDocsAndDocsByDemoId(Integer demoId, Module moduleName, DocumentType docType);

    public List<Document> findActiveByDocumentNo(Integer demoId);

    public List<Object[]> findCtlDocsAndDocsByModuleDocTypeAndModuleId(Module module, DocumentType docType,
                                                                       Integer moduleId);

    /**
     * Returns distinct document descriptions (docdesc) that contain the given keyword,
     * ordered descending by document number (most recently created first).
     */
    public List<Object[]> findCtlDocsAndDocsByModuleAndModuleId(Module module, Integer moduleId);

    public List<Object[]> findDocsAndConsultDocsByConsultId(Integer consultationId);

    /**
     * Returns a list of documents and electronic form documents associated with the given fdid.
     */
    public List<Object[]> findDocsAndEFormDocsByFdid(Integer fdid);

    /**
     * Returns distinct document descriptions (docdesc) that contain the given keyword,
     * ordered descending by document number (most recently created first).
     */
    public List<Object[]> findDocsAndConsultResponseDocsByConsultId(Integer consultationId);

    /**
     * Returns a list of control documents and documents associated with the specified document number.
     */
    public List<Object[]> findCtlDocsAndDocsByDocNo(Integer documentNo);

    /**
     * Finds documents and their details by module, creator, responsible person, and date range.
     */
    public List<Object[]> findCtlDocsAndDocsByModuleCreatorResponsibleAndDates(Module module, String providerNo,
                                                                               String responsible, Date from, Date to, boolean unmatchedDemographics);

    /**
     * Returns distinct document descriptions (docdesc) that contain the given keyword,
     * ordered descending by document number (most recently created first).
     */
    public List<Object[]> findConstultDocsDocsAndProvidersByModule(Module module, Integer moduleId);

    /**
     * Finds the maximum document number.
     */
    public Integer findMaxDocNo();

    public Document getDocument(String documentNo);

    /**
     * Retrieves a Demographic object based on the provided document number.
     */
    public Demographic getDemoFromDocNo(String docNo);

    /**
     * Retrieves the number of documents attached to a provider's demographics.
     */
    public int getNumberOfDocumentsAttachedToAProviderDemographics(String providerNo, Date startDate, Date endDate);

    /**
     * Subtracts a specified number of pages from the document identified by the given document number.
     */
    public void subtractPages(String documentNo, Integer i);

    /**
     * Retrieves a list of documents associated with the specified demographic ID.
     */
    public List<Document> findByDemographicId(String demoNo);

    /**
     * Returns distinct document descriptions (docdesc) that contain the given keyword,
     * ordered descending by document number (most recently created first).
     */
    public List<Object[]> findDocuments(String module, String moduleid, String docType, boolean includePublic,
                                        boolean includeDeleted, boolean includeActive, EDocSort sort, Date since);

    /**
     * Finds documents updated after a specified date.
     */
    public List<Document> findByUpdateDate(Date updatedAfterThisDateExclusive, int itemsToReturn);

    public List<Document> findByDemographicUpdateDate(Integer demographicId, Date updatedAfterThisDateInclusive);

    /**
     * Retrieves a list of documents updated after a specified date for a given demographic ID.
     */
    public List<Document> findByDemographicUpdateAfterDate(Integer demographicId, Date updatedAfterThisDate);

    public List<Document> findByProgramProviderDemographicUpdateDate(Integer programId, String providerNo,
                                                                     Integer demographicId, Date updatedAfterThisDateExclusive, int itemsToReturn);

    /**
     * Retrieves demographic IDs since the specified date.
     */
    public List<Integer> findDemographicIdsSince(Date since);

    /**
     * Retrieves a list of documents based on the specified document type.
     */
    public List<Document> findByDoctype(String docType);

    /**
     * Retrieves a list of documents based on the specified document type and provider number.
     */
    public List<Document> findByDoctypeAndProviderNo(String docType, String provider_no, Integer isPublic);

    /**
     * Finds documents by demographic ID and document type.
     */
    public List<Document> findByDemographicAndDoctype(int demographicId, DocumentType documentType);

    public Document findByDemographicAndFilename(int demographicId, String fileName);

    /**
     * Returns distinct document descriptions (docdesc) that contain the given keyword,
     * ordered descending by document number (most recently created first).
     *
     * @param keyword String the search term; use {@code %keyword%} wildcards for partial matching
     * @return List&lt;String&gt; list of distinct matching document descriptions
     */
    public List<String> findDocumentDescriptions(String keyword);
}
