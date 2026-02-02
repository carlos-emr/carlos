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

import java.util.List;
import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.FlowSheetUserCreated;
import org.springframework.stereotype.Repository;

@Repository
public class FlowSheetUserCreatedDaoImpl extends AbstractDaoImpl<FlowSheetUserCreated> implements FlowSheetUserCreatedDao {

    public FlowSheetUserCreatedDaoImpl() {
        super(FlowSheetUserCreated.class);
    }

    public List<FlowSheetUserCreated> findActiveNoTemplate() {
        Query query = entityManager.createQuery("SELECT f FROM FlowSheetUserCreated f WHERE f.archived=?1 and f.template IS NULL or f.template = ''");
        query.setParameter(1, false);
        return query.getResultList();
    }

    public List<FlowSheetUserCreated> getAllUserCreatedFlowSheets() {
        Query query = entityManager.createQuery("SELECT f FROM FlowSheetUserCreated f WHERE f.archived=?1");
        query.setParameter(1, false);
        return query.getResultList();
    }

    public List<FlowSheetUserCreated> findActiveByScope(String scope) {
        Query query = entityManager.createQuery("SELECT f FROM FlowSheetUserCreated f WHERE f.archived=?1 AND f.scope = ?2");
        query.setParameter(1, false);
        query.setParameter(2, scope);
        return query.getResultList();
    }

    public FlowSheetUserCreated findByPatientScope(String template, Integer demographicNo) {
        Query query = entityManager.createQuery("SELECT f FROM FlowSheetUserCreated f WHERE f.archived=?1 AND f.scope = ?2 AND f.scopeDemographicNo = ?3 AND f.template = ?4");
        query.setParameter(1, false);
        query.setParameter(2, FlowSheetUserCreated.SCOPE_PATIENT);
        query.setParameter(3, demographicNo);
        query.setParameter(4, template);
        return this.getSingleResultOrNull(query);
    }

    public FlowSheetUserCreated findByProviderScope(String template, String providerNo) {
        Query query = entityManager.createQuery("SELECT f FROM FlowSheetUserCreated f WHERE f.archived=?1 AND f.scope = ?2 AND f.scopeProviderNo = ?3 AND f.template = ?4");
        query.setParameter(1, false);
        query.setParameter(2, FlowSheetUserCreated.SCOPE_PROVIDER);
        query.setParameter(3, providerNo);
        query.setParameter(4, template);
        return this.getSingleResultOrNull(query);
    }

    public FlowSheetUserCreated findByClinicScope(String template) {
        Query query = entityManager.createQuery("SELECT f FROM FlowSheetUserCreated f WHERE f.archived=?1 AND f.scope = ?2 AND f.template = ?3");
        query.setParameter(1, false);
        query.setParameter(2, FlowSheetUserCreated.SCOPE_CLINIC);
        query.setParameter(3, template);
        return this.getSingleResultOrNull(query);
    }

    public FlowSheetUserCreated findByPatientScopeName(String name, Integer demographicNo) {
        Query query = entityManager.createQuery("SELECT f FROM FlowSheetUserCreated f WHERE f.archived=?1 AND f.scope = ?2 AND f.scopeDemographicNo = ?3 AND f.name = ?4");
        query.setParameter(1, false);
        query.setParameter(2, FlowSheetUserCreated.SCOPE_PATIENT);
        query.setParameter(3, demographicNo);
        query.setParameter(4, name);
        return this.getSingleResultOrNull(query);
    }

    public FlowSheetUserCreated findByProviderScopeName(String name, String providerNo) {
        Query query = entityManager.createQuery("SELECT f FROM FlowSheetUserCreated f WHERE f.archived=?1 AND f.scope = ?2 AND f.scopeProviderNo = ?3 AND f.name = ?4");
        query.setParameter(1, false);
        query.setParameter(2, FlowSheetUserCreated.SCOPE_PROVIDER);
        query.setParameter(3, providerNo);
        query.setParameter(4, name);
        return this.getSingleResultOrNull(query);
    }

    public FlowSheetUserCreated findByClinicScopeName(String name) {
        Query query = entityManager.createQuery("SELECT f FROM FlowSheetUserCreated f WHERE f.archived=?1 AND f.scope = ?2 AND f.name = ?3");
        query.setParameter(1, false);
        query.setParameter(2, FlowSheetUserCreated.SCOPE_CLINIC);
        query.setParameter(3, name);
        return this.getSingleResultOrNull(query);
    }
}
