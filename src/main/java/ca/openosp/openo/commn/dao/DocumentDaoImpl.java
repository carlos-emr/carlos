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

package ca.openosp.openo.commn.dao;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.Query;

import ca.openosp.openo.commn.model.ConsultDocs;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.Document;
import ca.openosp.openo.commn.model.EFormDocs;
import org.springframework.stereotype.Repository;

import ca.openosp.openo.documentManager.EDocUtil;
import ca.openosp.openo.documentManager.EDocUtil.EDocSort;

@Repository
public class DocumentDaoImpl extends AbstractDaoImpl<Document> implements DocumentDao {

    public DocumentDaoImpl() {
        super(Document.class);
    }

    @Override
    public List<Object[]> getCtlDocsAndDocsByDemoId(Integer demoId, Module moduleName, DocumentType docType) {
        String sql = "FROM CtlDocument c, Document d " +
                "WHERE c.id.module = ?1 " +
                "AND c.id.documentNo = d.documentNo " +
                "AND d.doctype = ?2 " +
                "AND c.id.moduleId = ?3";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, moduleName.name().toLowerCase());
        query.setParameter(2, docType.name().toLowerCase());
        query.setParameter(3, demoId);
        return query.getResultList();

    }

    @Override
    public List<Document> findActiveByDocumentNo(Integer demoId) {
        String sql = "SELECT d FROM Document d where d.documentNo = ?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, demoId);
        return query.getResultList();
    }

    @Override
    public List<Object[]> findCtlDocsAndDocsByModuleDocTypeAndModuleId(Module module, DocumentType docType,
                                                                       Integer moduleId) {
        String sql = "FROM CtlDocument c, Document d " +
                "WHERE c.id.module = ?1 " +
                "AND c.id.documentNo = d.documentNo " +
                "AND d.doctype = ?2 " +
                "AND c.id.moduleId = ?3";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, module.getName());
        query.setParameter(2, docType.getName());
        query.setParameter(3, moduleId);
        return query.getResultList();
    }

    @Override
    public List<Object[]> findCtlDocsAndDocsByModuleAndModuleId(Module module, Integer moduleId) {
        String sql = "FROM CtlDocument c, Document d " +
                "WHERE d.status = c.status " +
                "AND d.status != 'D' " +
                "AND c.id.documentNo = d.documentNo " +
                "AND c.id.module = ?1 " +
                "AND c.id.moduleId = ?2";

        Query query = entityManager.createQuery(sql);
        query.setParameter(1, module.getName());
        query.setParameter(2, moduleId);
        return query.getResultList();
    }

    @Override
    public List<Object[]> findDocsAndConsultDocsByConsultId(Integer consultationId) {
        String sql = "FROM Document d, ConsultDocs cd " +
                "WHERE d.documentNo = cd.documentNo " +
                "AND cd.requestId = ?1 " +
                "AND cd.docType = ?2 " +
                "AND cd.deleted IS NULL";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, consultationId);
        query.setParameter(2, ConsultDocs.DOCTYPE_DOC);
        return query.getResultList();
    }

    @Override
    public List<Object[]> findDocsAndEFormDocsByFdid(Integer fdid) {
        String sql = "FROM Document d, EFormDocs cd " +
                "WHERE d.documentNo = cd.documentNo " +
                "AND cd.fdid = ?1 " +
                "AND cd.docType = ?2 " +
                "AND cd.deleted IS NULL";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, fdid);
        query.setParameter(2, EFormDocs.DOCTYPE_DOC);
        return query.getResultList();
    }

    @Override
    public List<Object[]> findDocsAndConsultResponseDocsByConsultId(Integer consultationId) {
        String sql = "FROM Document d, ConsultResponseDoc crd " +
                "WHERE d.documentNo = crd.documentNo " +
                "AND crd.responseId = ?1 " +
                "AND crd.docType = 'D' " +
                "AND crd.deleted IS NULL";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, consultationId);
        return query.getResultList();
    }

    @Override
    public List<Object[]> findCtlDocsAndDocsByDocNo(Integer documentNo) {
        String sql = "FROM Document d, CtlDocument c " +
                "WHERE c.id.documentNo = d.documentNo " +
                "AND c.id.documentNo = ?1 " +
                "ORDER BY d.observationdate DESC, d.updatedatetime DESC";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, documentNo);
        return query.getResultList();
    }

    @Override
    public List<Object[]> findCtlDocsAndDocsByModuleCreatorResponsibleAndDates(Module module, String providerNo,
                                                                               String responsible, Date from, Date to, boolean unmatchedDemographics) {
        String sql = "FROM Document d, CtlDocument c " +
                "WHERE c.documentNo = d.documentNo " +
                "AND c.module= ?1 " +
                "AND d.doccreator = ?2 " +
                "AND d.responsible = ?3" +
                "AND d.updatedatetime >= ?4 " +
                "AND d.updatedatetime <= ?5";
        if (unmatchedDemographics) {
            sql += " AND c.id.moduleId = -1 ";
        }
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, module.getName());
        query.setParameter(2, providerNo);
        query.setParameter(3, responsible);
        query.setParameter(4, from);
        query.setParameter(5, to);
        return query.getResultList();
    }

    @Override
    public List<Object[]> findConstultDocsDocsAndProvidersByModule(Module module, Integer moduleId) {
        String sql = "FROM Document d, Provider p, CtlDocument c " +
                "WHERE d.doccreator = p.ProviderNo " +
                "AND d.id = c.id.documentNo " +
                "AND c.id.module = ?1 " +
                "AND c.id.moduleId = ?2";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, module.getName());
        query.setParameter(2, moduleId);
        return query.getResultList();
    }

    @Override
    public Integer findMaxDocNo() {
        String sql = "select max(d.documentNo) from Document d";
        Query query = entityManager.createQuery(sql);
        List<Object> o = query.getResultList();
        if (o.isEmpty()) {
            return 0;
        }
        Object r = o.get(0);
        if (r == null) {
            return 0;
        }
        return (Integer) r;
    }

    @Override
    public Document getDocument(String documentNo) {
        Integer id = null;
        try {
            id = Integer.parseInt(documentNo);
        } catch (NumberFormatException e) {
            // ignore
            return null;
        }
        return find(id);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Demographic getDemoFromDocNo(String docNo) {// return null if no demographic linked to this document
        Demographic d = null;
        Integer id = null;
        try {
            id = Integer.parseInt(docNo);
        } catch (NumberFormatException e) {
            // ignore
            return null;
        }

        String q = "select d from Demographic d, CtlDocument c where c.id.module='demographic'"
                + " and c.id.moduleId!='-1' and c.id.moduleId=d.DemographicNo and c.id.documentNo=?1 ";

        Query query = entityManager.createQuery(q);
        query.setParameter(1, id);

        List<Demographic> rs = query.getResultList();
        if (rs.size() > 0)
            d = rs.get(0);
        return d;
    }

    @Override
    public int getNumberOfDocumentsAttachedToAProviderDemographics(String providerNo, Date startDate, Date endDate) {
        Query query = entityManager.createNativeQuery(
                "select count(*) from ctl_document c, demographic d,document doc where c.module_id = d.demographic_no and c.document_no = doc.document_no   and d.provider_no = ?1 and doc.observationdate >= ?2 and doc.observationdate <= ?3 ");
        query.setParameter(1, providerNo);
        query.setParameter(2, new Timestamp(startDate.getTime()));
        query.setParameter(3, new Timestamp(endDate.getTime()));
        BigInteger result = (BigInteger) query.getSingleResult();
        if (result == null)
            return 0;
        return result.intValue();
    }

    @Override
    public void subtractPages(String documentNo, Integer i) {
        Document doc = getDocument(documentNo);
        if (doc != null) {
            doc.setNumberofpages(doc.getNumberofpages() - i);
            merge(doc);
        }
    }

    /**
     * Finds all documents for the specified demographic id
     *
     * @param demoNo
     */
    @Override
    public List<Document> findByDemographicId(String demoNo) {
        Integer id = null;
        try {
            id = Integer.parseInt(demoNo);
        } catch (NumberFormatException e) {
            // ignore
            return new ArrayList<Document>();
        }
        Query query = entityManager.createQuery(
                "SELECT DISTINCT d FROM Document d, CtlDocument c WHERE d.status = c.status AND d.status != 'D' AND c.id.documentNo=d.documentNo AND "
                        + "c.id.module='demographic' AND c.id.moduleId = ?1");
        query.setParameter(1, id);

        List<Document> result = query.getResultList();
        return result;
    }

    /**
     * @param module
     * @param moduleid
     * @param docType
     * @param includePublic
     * @param includeDeleted
     * @param includeActive
     * @return Returns a list containing array with CtlDocument and Document pairs
     * in the corresponding order.
     * @Deprecated Chop-chop. Please don't ask what this spagehtti does, better
     * don't use it.
     */
    @SuppressWarnings("unchecked")
    @Deprecated
    @Override
    public List<Object[]> findDocuments(String module, String moduleid, String docType, boolean includePublic,
                                        boolean includeDeleted, boolean includeActive, EDocSort sort, Date since) {
        Map<String, Object> params = new HashMap<String, Object>();

        Integer moduleIdInt = null;
        try {
            if (moduleid == null) {
                throw new IllegalArgumentException("moduleid cannot be null");
            }
            
            String cleanedModuleId = moduleid.trim();
            if (cleanedModuleId.isEmpty()) {
                throw new IllegalArgumentException("moduleid cannot be empty");
            }

            moduleIdInt = Integer.valueOf(cleanedModuleId);
            if (moduleIdInt <= 0) {
                throw new IllegalArgumentException("moduleid must be positive");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("moduleid must be a valid integer, but got:" + moduleid, e);
        }

        List<String> modules = EDocUtil.getModulesForQuery(module);
        StringBuilder buf = new StringBuilder("SELECT DISTINCT c, d " +
                "FROM Document d, CtlDocument c " +
                "WHERE c.id.documentNo = d.id AND c.id.module IN :modules");
        params.put("modules", modules);

        boolean isShowingAllDocuments = docType == null || docType.equals("all") || docType.length() == 0;

        if (includePublic) {
            if (isShowingAllDocuments) {
                buf.append(" AND d.public1 = 1");
            } else {
                buf.append(" AND d.public1 = 1 AND d.doctype = :doctype");
                params.put("doctype", docType);
            }
        } else {
            if (isShowingAllDocuments) {
                buf.append(" AND c.id.moduleId = :moduleId AND d.public1 = 0");
                params.put("moduleId", moduleIdInt);
            } else {
                buf.append(" AND c.id.moduleId = :moduleId AND d.public1 = 0 AND d.doctype = :doctype");
                params.put("doctype", docType);
                params.put("moduleId",moduleIdInt);
            }
        }

        if (includeDeleted) {
            buf.append(" AND d.status = 'D'");
        } else if (includeActive) {
            buf.append(" AND d.status != 'D'");
        }

        if (since != null) {
            buf.append(" AND d.updatedatetime > :updatedatetime ");
            params.put("updatedatetime", since);
        }

        buf.append(" ORDER BY ").append(sort.getValue());

        Query query = entityManager.createQuery(buf.toString());
        for (String key : params.keySet()) {
            Object val = params.get(key);
            query.setParameter(key, val);
        }
        List<Object[]> result = query.getResultList();
        return result;

    }

    /**
     * @return results ordered by updatedatetime
     */
    @Override
    public List<Document> findByUpdateDate(Date updatedAfterThisDateExclusive, int itemsToReturn) {
        String sql = "select x from " + modelClass.getSimpleName()
                + " x where x.updatedatetime>?1 order by x.updatedatetime";

        Query query = entityManager.createQuery(sql);
        query.setParameter(1, updatedAfterThisDateExclusive);
        setLimit(query, itemsToReturn);

        @SuppressWarnings("unchecked")
        List<Document> documents = query.getResultList();
        return documents;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Document> findByDemographicUpdateDate(Integer demographicId, Date updatedAfterThisDateInclusive) {
        String sql = "select d from "
                + modelClass.getSimpleName()
                + " d, CtlDocument c "
                + "where c.id.documentNo=d.documentNo "
                + "and c.id.module LIKE 'demographic' "
                + "AND c.id.moduleId = ?1 "
                + "and d.updatedatetime >= ?2";

        Query query = entityManager.createQuery(sql);
        query.setParameter(1, demographicId);
        query.setParameter(2, updatedAfterThisDateInclusive);
        List<Document> documents = query.getResultList();
        if (documents == null) {
            documents = Collections.emptyList();
        }
        return documents;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Document> findByDemographicUpdateAfterDate(Integer demographicId, Date updatedAfterThisDate) {
        String sql = "select d from "
                + modelClass.getSimpleName()
                + " d, CtlDocument c "
                + "where c.id.documentNo=d.documentNo "
                + "and c.id.module LIKE 'demographic' "
                + "AND c.id.moduleId = ?1 "
                + "and d.updatedatetime > ?2";

        Query query = entityManager.createQuery(sql);
        query.setParameter(1, demographicId);
        query.setParameter(2, updatedAfterThisDate);
        List<Document> documents = query.getResultList();
        if (documents == null) {
            documents = Collections.emptyList();
        }
        return documents;
    }

    /**
     * @return results ordered by updatedatetime
     */
    @Override
    public List<Document> findByProgramProviderDemographicUpdateDate(Integer programId, String providerNo,
                                                                     Integer demographicId, Date updatedAfterThisDateExclusive, int itemsToReturn) {
        String sql = "select d from " + modelClass.getSimpleName()
                + " d, CtlDocument c where c.id.documentNo=d.documentNo and c.id.module='demographic' AND c.id.moduleId = ?1 and (d.programId=?2 or d.programId is null or d.programId=-1) and d.doccreator=?3 and d.updatedatetime>?4 order by d.updatedatetime";

        Query query = entityManager.createQuery(sql);
        query.setParameter(1, demographicId);
        query.setParameter(2, programId);
        query.setParameter(3, providerNo);
        query.setParameter(4, updatedAfterThisDateExclusive);
        setLimit(query, itemsToReturn);

        @SuppressWarnings("unchecked")
        List<Document> documents = query.getResultList();
        return documents;
    }

    // for integrator
    @Override
    public List<Integer> findDemographicIdsSince(Date since) {

        String sql = "SELECT DISTINCT c.id.moduleId "
                + "FROM Document d, CtlDocument c WHERE c.id.documentNo=d.documentNo AND c.id.module='demographic'";

        if (since != null) {
            sql += " AND d.updatedatetime > ?1";
        }

        Query query = entityManager.createQuery(sql);

        if (since != null) {
            query.setParameter(1, since);
        }
        @SuppressWarnings("unchecked")
        List<Integer> resultDocs = query.getResultList();

        return resultDocs;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Document> findByDoctype(String docType) {
        Query query = entityManager
                .createQuery("SELECT d FROM Document d WHERE d.doctype = ?1 AND d.status = 'A'");
        query.setParameter(1, docType);
        return query.getResultList();
    }

    @Override
    public List<Document> findByDoctypeAndProviderNo(String docType, String provider_no, Integer isPublic) {
        Query query = entityManager.createQuery(
                "SELECT d FROM Document d WHERE d.doctype = ?1 AND d.status = 'A' and d.doccreator=?2 and d.public1=?3 ORDER BY updatedatetime DESC");
        query.setParameter(1, docType);
        query.setParameter(2, provider_no);
        query.setParameter(3, isPublic);

        return query.getResultList();

    }

    @Override
    public List<Document> findByDemographicAndDoctype(int demographicId, DocumentType documentType) {
        Query query = entityManager.createNativeQuery(
                "SELECT d.* FROM document d, ctl_document c WHERE c.document_no = d.document_no AND d.doctype = ?1 AND d.status NOT LIKE 'D' AND c.module LIKE 'demographic' AND c.module_id = ?2",
                Document.class);
        query.setParameter(1, demographicId);
        query.setParameter(2, documentType.getName());

        @SuppressWarnings("unchecked")
        List<Document> results = query.getResultList();

        if (results == null) {
            results = Collections.emptyList();
        }

        return results;
    }

    @Override
    public Document findByDemographicAndFilename(int demographicId, String fileName) {
        Query query = entityManager.createNativeQuery(
                "SELECT d.* FROM document d, ctl_document c WHERE c.document_no = d.document_no AND d.docfilename = ?1 AND d.status NOT LIKE 'D' AND c.module LIKE 'demographic' AND c.module_id = ?2",
                Document.class);
        query.setParameter(1, demographicId);
        query.setParameter(2, fileName);

        return getSingleResultOrNull(query);
    }
}
