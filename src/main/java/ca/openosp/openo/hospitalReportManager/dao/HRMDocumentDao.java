//CHECKSTYLE:OFF
/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 */

package ca.openosp.openo.hospitalReportManager.dao;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.persistence.Query;

import org.apache.commons.lang3.StringUtils;
import ca.openosp.openo.commn.dao.AbstractDaoImpl;
import ca.openosp.openo.hospitalReportManager.model.HRMDocument;
import org.springframework.stereotype.Repository;

@Repository
public class HRMDocumentDao extends AbstractDaoImpl<HRMDocument> {

    public HRMDocumentDao() {
        super(HRMDocument.class);
    }

    public List<HRMDocument> findById(int id) {
        String sql = "select x from " + this.modelClass.getName() + " x where x.id=?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, id);
        @SuppressWarnings("unchecked")
        List<HRMDocument> documents = query.getResultList();
        return documents;
    }

    public List<HRMDocument> findAll(int offset, int limit) {
        String sql = "select x from " + this.modelClass.getName() + " x order by x.id";
        Query query = entityManager.createQuery(sql);
        query.setFirstResult(offset);
        query.setMaxResults(limit);

        @SuppressWarnings("unchecked")
        List<HRMDocument> documents = query.getResultList();
        return documents;
    }

    public List<HRMDocument> findAll() {
        String sql = "select x from " + this.modelClass.getName() + " x order by x.id";
        Query query = entityManager.createQuery(sql);

        @SuppressWarnings("unchecked")
        List<HRMDocument> documents = query.getResultList();
        return documents;
    }


    public List<Integer> findByHash(String hash) {
        String sql = "select distinct id from " + this.modelClass.getName() + " x where x.reportHash=?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, hash);
        @SuppressWarnings("unchecked")
        List<Integer> matches = query.getResultList();
        return matches;
    }

    public List<HRMDocument> findByNoTransactionInfoHash(String hash) {
        String sql = "select x from " + this.modelClass.getName() + " x where x.reportLessTransactionInfoHash=?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, hash);
        @SuppressWarnings("unchecked")
        List<HRMDocument> matches = query.getResultList();
        return matches;
    }

    @SuppressWarnings("unchecked")
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

    public List<HRMDocument> getAllChildrenOf(Integer docId) {
        String sql = "select x from " + this.modelClass.getName() + " x where x.parentReport=?1 and x.id != ?2 order by id asc";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, docId);
        query.setParameter(2, docId);
        @SuppressWarnings("unchecked")
        List<HRMDocument> documents = query.getResultList();
        return documents;
    }

    public List<HRMDocument> findByKey(String sourceFacility, String sourceFacilityReportNo, String deliverToId) {
        String sql = "select x from " + this.modelClass.getName() + " x where x.sourceFacility=?1 AND x.sourceFacilityReportNo = ?2 AND x.recipientId = ?3";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, sourceFacility);
        query.setParameter(2, sourceFacilityReportNo);
        query.setParameter(3, deliverToId);

        @SuppressWarnings("unchecked")
        List<HRMDocument> documents = query.getResultList();
        return documents;
    }


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
