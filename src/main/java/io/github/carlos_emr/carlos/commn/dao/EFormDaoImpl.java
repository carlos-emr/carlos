/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.commn.dao;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.EForm;
import org.springframework.stereotype.Repository;

@Repository
public class EFormDaoImpl extends AbstractDaoImpl<EForm> implements EFormDao {

    public EFormDaoImpl() {
        super(EForm.class);
    }

    @Override
    public EForm findByName(String name) {
        Query query = entityManager.createQuery("SELECT e from EForm e where e.formName = ?1 and e.current=?2");
        query.setParameter(1, name);
        query.setParameter(2, true);

        @SuppressWarnings("unchecked")
        List<EForm> results = query.getResultList();
        if (!results.isEmpty())
            return results.get(0);
        return null;
    }

    @Override
    public List<EForm> findByNameSimilar(String name) {
        if (name == null || name.trim().isEmpty()) {
            return new ArrayList<EForm>();
        }

        Query query = entityManager.createQuery("SELECT e from EForm e where e.formName like ?1 and e.current=?2");
        query.setParameter(1, "%" + name + "%");
        query.setParameter(2, true);

        @SuppressWarnings("unchecked")
        List<EForm> results = query.getResultList();
        return results;
    }

    @Override
    public EForm findById(Integer formId) {
        Query query = entityManager.createQuery("select x from " + modelClass.getSimpleName() + " x where x.id=?1");
        query.setParameter(1, formId);

        return this.getSingleResultOrNull(query);
    }

    /**
     * @param current can be null for both
     * @return list of EForms
     */
    @Override
    public List<EForm> findAll(Boolean current) {
        StringBuilder sb = new StringBuilder();
        sb.append("select x from ");
        sb.append(modelClass.getSimpleName());
        sb.append(" x");

        if (current != null) {
            sb.append(" where x.current=?1");
        }

        Query query = entityManager.createQuery(sb.toString());
        if (current != null) {
            query.setParameter(1, current);
        }

        @SuppressWarnings("unchecked")
        List<EForm> results = query.getResultList();

        return (results);
    }

    @Override
    public boolean isIndivicaRTLEnabled() {

        StringBuilder sb = new StringBuilder();
        sb.append("select x from ");
        sb.append(modelClass.getSimpleName());
        sb.append(" x where x.formName='Rich Text Letter' and x.subject='Rich Text Letter Generator'");
        Query query = entityManager.createQuery(sb.toString());

        @SuppressWarnings("unchecked")
        List<EForm> forms = query.getResultList();

        boolean enabled = false;
        for (EForm form : forms) {
            enabled |= form.isCurrent();
        }

        return enabled;
    }

    @Override
    public void setIndivicaRTLEnabled(boolean enabled) {

        StringBuilder sb = new StringBuilder();
        sb.append("select x from ");
        sb.append(modelClass.getSimpleName());
        sb.append(" x where x.formName='Rich Text Letter' and x.subject='Rich Text Letter Generator'");
        Query query = entityManager.createQuery(sb.toString());

        @SuppressWarnings("unchecked")
        List<EForm> forms = query.getResultList();
        for (EForm form : forms) {
            form.setCurrent(enabled);
            enabled |= form.isCurrent();
            merge(form);
        }
    }

    /**
     * Finds all eforms based on the status.
     *
     * @param status Status to be used when looking up forms.
     * @return Returns the list of all forms with the specified status.
     */
    @Override
    public List<EForm> findByStatus(boolean status) {
        return findByStatus(status, null);
    }

    /**
     * Finds all eforms based on the status.
     *
     * @param status    Status to be used when looking up forms.
     * @param sortOrder Order how records should be sorted. Providing no sort order
     *                  delegates to the default sorting order of the persistence
     *                  providers
     * @return Returns the list of all forms with the specified status.
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<EForm> findByStatus(boolean status, EFormSortOrder sortOrder) {
        StringBuilder buf = new StringBuilder("FROM " + modelClass.getSimpleName() + " ef WHERE ef.current = ?1");
        buf.append(getSortOrderClause(sortOrder));

        Query query = entityManager.createQuery(buf.toString());
        query.setParameter(1, status);

        return query.getResultList();
    }

    private Object getSortOrderClause(EFormSortOrder sortOrder) {
        if (sortOrder == null)
            return "";

        switch (sortOrder) {
            case DATE:
                return " ORDER BY ef.formDate DESC, ef.formTime DESC";
            case NAME:
                return " ORDER BY ef.formName";
            case FILE_NAME:
                return " ORDER BY ef.fileName";
            case SUBJECT:
                return " ORDER BY ef.subject";
            default:
                break;
        }

        return "";
    }

    /**
     * Finds the largest identifier for the specified form name.
     *
     * @param formName Form name to find the largest identifier for the form with
     *                 the specified name and set as enabled (current)
     * @return Returns the largest identifier found.
     */
    @Override
    public Integer findMaxIdForActiveForm(String formName) {
        Query query = entityManager.createQuery("SELECT MAX(ef.id) FROM " + modelClass.getSimpleName() + " ef WHERE ef.formName = ?1 AND ef.current = TRUE");
        query.setParameter(1, formName);
        return (Integer) query.getSingleResult();
    }

    /**
     * Counts forms with the specified form name excluding the specified form ID.
     *
     * @param formName Form name to be counted
     * @param id       ID of the form to exclude from the count results
     * @return Returns the number of all active forms with the forms with the
     * specified ID
     */
    @Override
    public Long countFormsOtherThanSpecified(String formName, Integer id) {
        // TODO test me
        Query query = entityManager.createQuery("SELECT COUNT(ef) FROM " + modelClass.getSimpleName() + " ef WHERE ef.current = TRUE AND ef.formName = ?1 AND ef.id != ?2");
        query.setParameter(1, formName);
        query.setParameter(2, id);
        return (Long) query.getSingleResult();
    }

    /**
     * get eform in group by group name
     *
     * @param groupName
     * @return list of EForms
     */
    @Override
    public List<EForm> getEfromInGroupByGroupName(String groupName) {
        String queryString = "select e from EForm e where e.id in (select formId from EFormGroup eg  where eg.groupName =?1)";
        Query query = entityManager.createQuery(queryString);
        query.setParameter(1, groupName);
        @SuppressWarnings("unchecked")
        List<EForm> results = query.getResultList();
        return (results);
    }
}
