/**
 * Copyright (c) 2026. CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */
package io.github.carlos_emr.carlos.fhir.repository;

import io.github.carlos_emr.carlos.commn.dao.AbstractDao;
import io.github.carlos_emr.carlos.commn.model.AbstractModel;

import java.util.List;
import java.util.Optional;

/**
 * {@link ClinicalRepository} implementation that delegates to an existing
 * Hibernate/JPA {@link AbstractDao}. This preserves the current MySQL-backed
 * behavior and serves as the baseline during migration.
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * AbstractDao<Demographic> demographicDao = SpringUtils.getBean(DemographicDao.class);
 * ClinicalRepository<Demographic, Integer> repo = new JpaClinicalRepository<>(demographicDao);
 * }</pre>
 *
 * @param <T>  the domain entity type (must extend {@link AbstractModel})
 * @param <ID> the entity identifier type
 * @since 2026-04-02
 */
public class JpaClinicalRepository<T extends AbstractModel<ID>, ID> implements ClinicalRepository<T, ID> {

    private final AbstractDao<T> dao;

    /**
     * Creates a JPA-backed clinical repository wrapping an existing DAO.
     *
     * @param dao the existing Hibernate DAO to delegate to
     */
    public JpaClinicalRepository(AbstractDao<T> dao) {
        this.dao = dao;
    }

    @Override
    public Optional<T> findById(ID id) {
        T entity = dao.find(id);
        return Optional.ofNullable(entity);
    }

    @Override
    public List<T> findAll(int offset, int limit) {
        return dao.findAll(offset, limit);
    }

    @Override
    public T save(T entity) {
        return dao.saveEntity(entity);
    }

    @Override
    public boolean delete(ID id) {
        return dao.remove(id);
    }

    @Override
    public long count() {
        return dao.getCountAll();
    }
}
