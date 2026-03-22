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

import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.CustomFilter;
import org.springframework.stereotype.Repository;

@Repository
/**
 * JPA implementation of {@link CustomFilterDao} for custom filter data access.
 *
 * @since 2001
 */

public class CustomFilterDaoImpl extends AbstractDaoImpl<CustomFilter> implements CustomFilterDao {

    public CustomFilterDaoImpl() {
        super(CustomFilter.class);
    }

    /** {@inheritDoc} */

    @Override
    public CustomFilter findByName(String name) {
        Query query = entityManager.createQuery("select c FROM CustomFilter c where c.name = ?1");
        query.setParameter(1, name);

        CustomFilter result = this.getSingleResultOrNull(query);

        return result;
    }

    /** {@inheritDoc} */

    @Override
    public CustomFilter findByNameAndProviderNo(String name, String providerNo) {
        Query query = entityManager.createQuery("select c FROM CustomFilter c where c.name = ?1 and c.providerNo = ?2");
        query.setParameter(1, name);
        query.setParameter(2, providerNo);

        CustomFilter result = this.getSingleResultOrNull(query);

        return result;
    }

    /** {@inheritDoc} */

    @Override
    public List<CustomFilter> getCustomFilters() {
        Query query = entityManager.createQuery("select c FROM CustomFilter c");

        @SuppressWarnings("unchecked")
        List<CustomFilter> result = query.getResultList();

        return result;
    }

    /** {@inheritDoc} */

    @Override
    public List<CustomFilter> findByProviderNo(String providerNo) {
        Query query = entityManager.createQuery("select c FROM CustomFilter c where c.providerNo = ?1");
        query.setParameter(1, providerNo);

        @SuppressWarnings("unchecked")
        List<CustomFilter> result = query.getResultList();

        return result;
    }

    /** {@inheritDoc} */

    @Override
    public List<CustomFilter> getCustomFilterWithShortCut(String providerNo) {
        Query query = entityManager
                .createQuery("select c FROM CustomFilter c where c.providerNo = ?1 and c.shortcut = true");
        query.setParameter(1, providerNo);

        @SuppressWarnings("unchecked")
        List<CustomFilter> result = query.getResultList();

        return result;
    }

    /** {@inheritDoc} */

    @Override
    public void deleteCustomFilter(String name) {
        CustomFilter filter = findByName(name);
        if (filter != null) {
            remove(filter);
        }
    }

}
