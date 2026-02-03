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

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Query;

import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.commn.model.IncomingLabRules;
import io.github.carlos_emr.carlos.commn.model.ProviderInboxItem;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.stereotype.Repository;

import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;

/**
 * @author jay gallagher
 */
@Repository
public class ProviderInboxRoutingDaoImpl extends AbstractDaoImpl<ProviderInboxItem> implements ProviderInboxRoutingDao {

    public ProviderInboxRoutingDaoImpl() {
        super(ProviderInboxItem.class);
    }

    @Override
    public boolean removeLinkFromDocument(String docType, Integer docId, String providerNo) {
        return CommonLabResultData.updateReportStatus(docId, providerNo, 'X', null, "DOC");
    }

    @Override
    public List<ProviderInboxItem> getProvidersWithRoutingForDocument(String docType, Integer docId) {
        Query query = entityManager
                .createQuery("select p from ProviderInboxItem p where p.labType = ?1 and p.labNo = ?2");
        query.setParameter(1, docType);
        query.setParameter(2, docId);

        @SuppressWarnings("unchecked")
        List<ProviderInboxItem> results = query.getResultList();

        return results;
    }

    @Override
    public boolean hasProviderBeenLinkedWithDocument(String docType, Integer docId, String providerNo) {
        Query query = entityManager.createQuery(
                "select p from ProviderInboxItem p where p.labType = ?1 and p.labNo = ?2 and p.providerNo=?3");
        query.setParameter(1, docType);
        query.setParameter(2, docId);
        query.setParameter(3, providerNo);

        @SuppressWarnings("unchecked")
        List<ProviderInboxItem> results = query.getResultList();

        return (results.size() > 0);
    }

    @Override
    public int howManyDocumentsLinkedWithAProvider(String providerNo) {
        Query query = entityManager.createQuery("select p from ProviderInboxItem p where p.providerNo=?1");
        query.setParameter(1, providerNo);

        @SuppressWarnings("unchecked")
        List<ProviderInboxItem> results = query.getResultList();

        return results.size();
    }

    @Override
    public List<ProviderInboxItem> findDocumentsLinkedWithProvider(String docType, Integer docId, String providerNo) {
        Query query = entityManager.createQuery(
                "select p from ProviderInboxItem p where p.labType = ?1 and p.labNo = ?2 and p.providerNo=?3");
        query.setParameter(1, docType);
        query.setParameter(2, docId);
        query.setParameter(3, providerNo);

        @SuppressWarnings("unchecked")
        List<ProviderInboxItem> results = query.getResultList();

        return results;
    }

    /**
     * Adds lab results to the providers inbox
     *
     * @param providerNo Provider to add lab results to
     * @param labNo      Document id to be added to the inbox
     * @param labType    Type of the document to be added. Available document types
     *                   are defined in {@link LabResultData}
     *                   class.
     */
    // TODO Replace labType parameter with an enum
    @SuppressWarnings("unchecked")
    @Override
    public void addToProviderInbox(String providerNo, Integer labNo, String labType) {
        ArrayList<String> listofAdditionalProviders = new ArrayList<String>();
        boolean fileForMainProvider = false;

        try {
            Query rulesQuery = entityManager
                    .createQuery("FROM IncomingLabRules r WHERE r.archive = 0 AND r.providerNo = ?1");
            rulesQuery.setParameter(1, providerNo);

            for (IncomingLabRules rules : (List<IncomingLabRules>) rulesQuery.getResultList()) {
                String status = rules.getStatus();
                String frwdProvider = rules.getFrwdProviderNo();

                listofAdditionalProviders.add(frwdProvider);
                if (status != null && status.equals("F"))
                    fileForMainProvider = true;
            }

            ProviderInboxItem p = new ProviderInboxItem();
            p.setProviderNo(providerNo);
            p.setLabNo(labNo);
            p.setLabType(labType);
            p.setStatus(fileForMainProvider ? ProviderInboxItem.FILE : ProviderInboxItem.NEW);

            List<ProviderInboxItem> documentsLinkedWithProvider = findDocumentsLinkedWithProvider(labType, labNo, providerNo);
            if (documentsLinkedWithProvider.isEmpty()) {
                persist(p);
            } else {
                ProviderInboxItem existingProviderInboxItem = documentsLinkedWithProvider.get(0);
                existingProviderInboxItem.setStatus(p.getStatus());
                merge(existingProviderInboxItem);
            }

            for (String provider : listofAdditionalProviders) {
                addToProviderInbox(provider, labNo, labType);
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

    }

}
