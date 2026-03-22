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

package io.github.carlos_emr.carlos.hospitalReportManager.dao;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import jakarta.persistence.Query;

import org.apache.commons.lang3.StringUtils;
import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import org.springframework.stereotype.Repository;

/**
 * Data access object for {@link HRMDocument} entities, providing CRUD operations,
 * hash-based lookups, parent-child relationship queries, and paginated provider-filtered
 * queries for the HRM inbox.
 *
 * @see HRMDocument
 * @since 2008-11-05
 */
@Repository
public class HRMDocumentDao extends AbstractDaoImpl<HRMDocument> {

    public HRMDocumentDao() {
        super(HRMDocument.class);
    }

    /**
     * Finds HRM documents by primary key ID.
     *
     * @param id int the document ID
     * @return List&lt;HRMDocument&gt; matching documents (typically zero or one)
     */
    public List<HRMDocument> findById(int id) {
        String sql = "select x from " + this.modelClass.getName() + " x where x.id=?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, id);
        @SuppressWarnings("unchecked")
        List<HRMDocument> documents = query.getResultList();
        return documents;
    }

    /**
     * Returns a paginated list of all HRM documents ordered by ID.
     *
     * @param offset int the starting position
     * @param limit int the maximum number of results
     * @return List&lt;HRMDocument&gt; the documents in the specified range
     */
    public List<HRMDocument> findAll(int offset, int limit) {
        String sql = "select x from " + this.modelClass.getName() + " x order by x.id";
        Query query = entityManager.createQuery(sql);
        query.setFirstResult(offset);
        query.setMaxResults(limit);

        @SuppressWarnings("unchecked")
        List<HRMDocument> documents = query.getResultList();
        return documents;
    }

    /**
     * Returns all HRM documents ordered by ID.
     *
     * @return List&lt;HRMDocument&gt; all documents
     */
    public List<HRMDocument> findAll() {
        String sql = "select x from " + this.modelClass.getName() + " x order by x.id";
        Query query = entityManager.createQuery(sql);

        @SuppressWarnings("unchecked")
        List<HRMDocument> documents = query.getResultList();
        return documents;
    }


    /**
     * Finds distinct document IDs that match the given report hash.
     *
     * @param hash String the SHA hash of the report content
     * @return List&lt;Integer&gt; distinct document IDs matching the hash
     */
    public List<Integer> findByHash(String hash) {
        String sql = "select distinct id from " + this.modelClass.getName() + " x where x.reportHash=?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, hash);
        @SuppressWarnings("unchecked")
        List<Integer> matches = query.getResultList();
        return matches;
    }

    @SuppressWarnings("unchecked")
    /**
     * Finds parent report IDs for documents sharing the same demographic-stripped hash.
     *
     * <p>Used for duplicate detection: if the hash matches but demographics differ,
     * it indicates the same report was sent for a different patient.</p>
     *
     * @param hash String the report hash with demographic info stripped
     * @return List&lt;Integer&gt; parent report IDs or document IDs matching the hash
     */
    public List<Integer> findAllWithSameNoDemographicInfoHash(String hash) {
        String sql = "select distinct parentReport from " + this.modelClass.getName() + " x where x.reportLessDemographicInfoHash=?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, hash);
        List<Integer> matches = query.getResultList();

        if (matches != null && matches.size() == 1 && matches.get(0) == null) {
            sql = "select distinct id from " + this.modelClass.getName() + " x where x.reportLessDemographicInfoHash=?1";
            query = entityManager.createQuery(sql);
            query.setParameter(1, hash);
            matches = query.getResultList();
        }
        return matches;
    }

    @SuppressWarnings("unchecked")
    /**
     * Finds all documents in the same parent-child chain as the specified document.
     *
     * <p>If the document is a child, returns the parent and all siblings. If it is the
     * parent, returns itself and all children, ordered by ID ascending.</p>
     *
     * @param docId Integer the document ID to find relationships for
     * @return List&lt;HRMDocument&gt; all related documents, or empty list if document not found
     */
    public List<HRMDocument> findAllDocumentsWithRelationship(Integer docId) {
        List<HRMDocument> documentsWithRelationship = new LinkedList<HRMDocument>();
        // Get the document that was specified first
        HRMDocument firstDocument = this.find(docId);
        if (firstDocument != null) {
            String sql = null;
            Query query = null;
            if (firstDocument.getParentReport() != null && !firstDocument.getParentReport().equals(docId)) {
                // This is a child report; get the parent and all siblings of this report (which includes itself)
                sql = "select x from " + this.modelClass.getName() + " x where x.id = ?1 order by x.id asc";
                query = entityManager.createQuery(sql);
                query.setParameter(1, firstDocument.getParentReport());
                documentsWithRelationship.addAll(query.getResultList());

                sql = "select x from " + this.modelClass.getName() + " x where x.parentReport = ?1 order by x.id asc";
                query = entityManager.createQuery(sql);
                query.setParameter(1, firstDocument.getParentReport());
                documentsWithRelationship.addAll(query.getResultList());


            } else {
                // This is a parent report; get all the children of this report as well as itself
                sql = "select x from " + this.modelClass.getName() + " x where x.parentReport = ?1 or x.id = ?2  order by x.id asc";
                query = entityManager.createQuery(sql);
                query.setParameter(1, firstDocument.getId());
                query.setParameter(2, firstDocument.getId());
                documentsWithRelationship = query.getResultList();
            }

        }

        return documentsWithRelationship;

    }

    /**
     * Returns all child documents of a parent document, excluding the parent itself.
     *
     * @param docId Integer the parent document ID
     * @return List&lt;HRMDocument&gt; child documents ordered by ID ascending
     */
    public List<HRMDocument> getAllChildrenOf(Integer docId) {
        String sql = "select x from " + this.modelClass.getName() + " x where x.parentReport=?1 and x.id != ?2 order by id asc";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, docId);
        query.setParameter(2, docId);
        @SuppressWarnings("unchecked")
        List<HRMDocument> documents = query.getResultList();
        return documents;
    }

    /**
     * Queries HRM documents with provider, sign-off, and demographic filtering for the inbox.
     *
     * @param providerNo String the provider number to filter by, or {@code null} for all
     * @param providerUnmatched boolean {@code true} to show only unmatched (providerNo="-1") documents
     * @param noSignOff boolean {@code true} to show only unsigned documents
     * @param demographicUnmatched boolean {@code true} to show only documents not linked to any demographic
     * @param start int the first result offset for pagination
     * @param length int the maximum number of results
     * @param orderColumn String the column name to order by (formattedName, dob, reportDate, timeReceived, sourceFacility)
     * @param orderDirection String "ASC" or "DESC"
     * @return List&lt;HRMDocument&gt; matching documents, or empty list if order parameters are invalid
     */
    public List<HRMDocument> query(String providerNo, boolean providerUnmatched, boolean noSignOff, boolean demographicUnmatched, int start, int length, String orderColumn, String orderDirection) {

        if (orderColumn != null && !orderColumn.equals("formattedName") && !orderColumn.equals("dob") && !orderColumn.equals("reportDate")
                && !orderColumn.equals("timeReceived") && !orderColumn.equals("sourceFacility")) {
            return new ArrayList<HRMDocument>();
        }
        if (orderDirection != null && !orderDirection.equalsIgnoreCase("ASC") && !orderDirection.equalsIgnoreCase("DESC")) {
            return new ArrayList<HRMDocument>();
        }
        String sql = "select x from " + this.modelClass.getName() + " x   ";

        //	if(providerNo != null || providerUnmatched) {
        sql += " inner JOIN x.matchedProviders p ";
        //	}

        sql += " WHERE x.parentReport IS NULL  ";

        if (demographicUnmatched) {
            sql = sql + " AND SIZE(x.matchedDemographics) = 0 ";
        }

        if (providerUnmatched) {
            sql += "  AND p.providerNo = :pNo ";
        } else {
            if (providerNo != null) {
                sql += "  AND p.providerNo = :pNo ";
            }
            if (noSignOff) {
                sql += " AND p.signedOff = 0";
            }
        }


        if (!StringUtils.isEmpty(orderColumn) && !StringUtils.isEmpty(orderDirection)) {
            sql = sql + " ORDER BY x." + orderColumn + " " + orderDirection;
        }


        Query query = entityManager.createQuery(sql);
        if (providerNo != null || providerUnmatched) {

        }

        if (providerUnmatched) {
            query.setParameter("pNo", "-1");
        } else {
            if (providerNo != null) {
                query.setParameter("pNo", providerNo);
            }
        }


        query.setFirstResult(start);
        query.setMaxResults(length);

        @SuppressWarnings("unchecked")
        List<HRMDocument> documents = query.getResultList();
        return documents;
    }

    /**
     * Returns the total count of documents matching the same criteria as {@link #query}.
     *
     * @param providerNo String the provider number to filter by, or {@code null} for all
     * @param providerUnmatched boolean {@code true} to count only unmatched documents
     * @param noSignOff boolean {@code true} to count only unsigned documents
     * @param demographicUnmatched boolean {@code true} to count only documents not linked to any demographic
     * @param start int unused (present for parameter parity with query)
     * @param length int unused (present for parameter parity with query)
     * @param orderColumn String the order column for validation
     * @param orderDirection String the order direction for validation
     * @return long the count of matching documents, or 0 if order parameters are invalid
     */
    public long queryForCount(String providerNo, boolean providerUnmatched, boolean noSignOff, boolean demographicUnmatched, int start, int length, String orderColumn, String orderDirection) {

        if (orderColumn != null && !orderColumn.equals("formattedName") && !orderColumn.equals("dob") && !orderColumn.equals("reportDate")
                && !orderColumn.equals("timeReceived") && !orderColumn.equals("sourceFacility")) {
            return 0;
        }
        if (orderDirection != null && !orderDirection.equalsIgnoreCase("ASC") && !orderDirection.equalsIgnoreCase("DESC")) {
            return 0;
        }
        String sql = "select count(x) from " + this.modelClass.getName() + " x   ";

        //	if(providerNo != null || providerUnmatched) {
        sql += " inner JOIN x.matchedProviders p ";
        //	}

        sql += " WHERE x.parentReport IS NULL  ";

        if (demographicUnmatched) {
            sql = sql + " AND SIZE(x.matchedDemographics) = 0 ";
        }

        if (providerUnmatched) {
            sql += "  AND p.providerNo = :pNo ";
        } else {
            if (providerNo != null) {
                sql += "  AND p.providerNo = :pNo ";
            }
            if (noSignOff) {
                sql += " AND p.signedOff = 0";
            }
        }


        if (!StringUtils.isEmpty(orderColumn) && !StringUtils.isEmpty(orderDirection)) {
            sql = sql + " ORDER BY x." + orderColumn + " " + orderDirection;
        }


        Query query = entityManager.createQuery(sql);
        if (providerNo != null || providerUnmatched) {

        }

        if (providerUnmatched) {
            query.setParameter("pNo", "-1");
        } else {
            if (providerNo != null) {
                query.setParameter("pNo", providerNo);
            }
        }


        Long count = (Long) query.getSingleResult();


        return count;
    }
}
