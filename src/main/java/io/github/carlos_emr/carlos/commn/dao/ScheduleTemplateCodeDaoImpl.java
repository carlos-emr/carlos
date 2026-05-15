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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.AbstractModel;
import io.github.carlos_emr.carlos.commn.model.ScheduleTemplateCode;
import io.github.carlos_emr.carlos.config.CacheConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Repository;

@Repository
public class ScheduleTemplateCodeDaoImpl extends AbstractDaoImpl<ScheduleTemplateCode> implements ScheduleTemplateCodeDao {
    private static final String ALL_KEY = "all";
    private static final String TEMPLATE_CODES_KEY = "templateCodes";
    private static final String CODE_KEY_PREFIX = "codeChar:";

    @Autowired
    private CacheManager cacheManager;

    public ScheduleTemplateCodeDaoImpl() {
        super(ScheduleTemplateCode.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<ScheduleTemplateCode> findAll() {
        List<ScheduleTemplateCode> cached = getCachedList(ALL_KEY);
        if (cached != null) {
            return copyScheduleTemplateCodes(cached);
        }

        Query query = createQuery("x", null);
        List<ScheduleTemplateCode> snapshot = Collections.unmodifiableList(copyScheduleTemplateCodes(query.getResultList()));
        cache().put(ALL_KEY, snapshot);
        return copyScheduleTemplateCodes(snapshot);
    }

    @Override
    public ScheduleTemplateCode getByCode(char code) {
        String cacheKey = CODE_KEY_PREFIX + code;
        ScheduleTemplateCode cached = getCachedScheduleTemplateCode(cacheKey);
        if (cached != null) {
            return ScheduleTemplateCode.copyOf(cached);
        }

        //Query query = entityManager.createQuery("FROM " + modelClass.getSimpleName() + " bst WHERE bst.id IN (:typeCodes)");
        Query query = entityManager.createQuery("select s from ScheduleTemplateCode s where s.code=?1");
        query.setParameter(1, code);

        @SuppressWarnings("unchecked")
        List<ScheduleTemplateCode> results = query.getResultList();
        if (!results.isEmpty()) {
            ScheduleTemplateCode result = ScheduleTemplateCode.copyOf(results.get(0));
            cache().put(cacheKey, result);
            return ScheduleTemplateCode.copyOf(result);
        }
        return null;
    }

    @Override
    public List<ScheduleTemplateCode> findTemplateCodes() {
        List<ScheduleTemplateCode> cached = getCachedList(TEMPLATE_CODES_KEY);
        if (cached != null) {
            return copyScheduleTemplateCodes(cached);
        }

        Query query = entityManager.createQuery("select s from ScheduleTemplateCode s where s.bookinglimit > 0 and s.duration <>''");

        @SuppressWarnings("unchecked")
        List<ScheduleTemplateCode> results = query.getResultList();

        List<ScheduleTemplateCode> snapshot = Collections.unmodifiableList(copyScheduleTemplateCodes(results));
        cache().put(TEMPLATE_CODES_KEY, snapshot);
        return copyScheduleTemplateCodes(snapshot);
    }

    @Override
    public ScheduleTemplateCode findByCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        return getByCode(code.charAt(0));
    }

    private Cache cache() {
        Cache cache = cacheManager.getCache(CacheConfig.SCHEDULE_TEMPLATE_CODES);
        if (cache == null) {
            throw new IllegalStateException("Cache not configured: " + CacheConfig.SCHEDULE_TEMPLATE_CODES);
        }
        return cache;
    }

    @SuppressWarnings("unchecked")
    private List<ScheduleTemplateCode> getCachedList(String key) {
        Cache.ValueWrapper wrapper = cache().get(key);
        return wrapper == null ? null : (List<ScheduleTemplateCode>) wrapper.get();
    }

    private ScheduleTemplateCode getCachedScheduleTemplateCode(String key) {
        Cache.ValueWrapper wrapper = cache().get(key);
        return wrapper == null ? null : (ScheduleTemplateCode) wrapper.get();
    }

    private List<ScheduleTemplateCode> copyScheduleTemplateCodes(List<ScheduleTemplateCode> source) {
        List<ScheduleTemplateCode> copies = new ArrayList<>(source.size());
        for (ScheduleTemplateCode scheduleTemplateCode : source) {
            copies.add(ScheduleTemplateCode.copyOf(scheduleTemplateCode));
        }
        return copies;
    }

    @CacheEvict(value = CacheConfig.SCHEDULE_TEMPLATE_CODES, allEntries = true)
    @Override
    public void persist(AbstractModel<?> o) { super.persist(o); }

    @CacheEvict(value = CacheConfig.SCHEDULE_TEMPLATE_CODES, allEntries = true)
    @Override
    public void merge(AbstractModel<?> o) { super.merge(o); }

    @CacheEvict(value = CacheConfig.SCHEDULE_TEMPLATE_CODES, allEntries = true)
    @Override
    public void remove(AbstractModel<?> o) { super.remove(o); }

    @CacheEvict(value = CacheConfig.SCHEDULE_TEMPLATE_CODES, allEntries = true)
    @Override
    public boolean remove(Object id) { return super.remove(id); }

    @CacheEvict(value = CacheConfig.SCHEDULE_TEMPLATE_CODES, allEntries = true)
    @Override
    public ScheduleTemplateCode saveEntity(ScheduleTemplateCode entity) { return super.saveEntity(entity); }

    @CacheEvict(value = CacheConfig.SCHEDULE_TEMPLATE_CODES, allEntries = true, beforeInvocation = true)
    @Override
    public void batchPersist(List<ScheduleTemplateCode> oList) { super.batchPersist(oList); }

    @CacheEvict(value = CacheConfig.SCHEDULE_TEMPLATE_CODES, allEntries = true, beforeInvocation = true)
    @Override
    public void batchPersist(List<ScheduleTemplateCode> oList, int batchSize) { super.batchPersist(oList, batchSize); }

    @CacheEvict(value = CacheConfig.SCHEDULE_TEMPLATE_CODES, allEntries = true, beforeInvocation = true)
    @Override
    public void batchRemove(List<ScheduleTemplateCode> oList) { super.batchRemove(oList); }

    @CacheEvict(value = CacheConfig.SCHEDULE_TEMPLATE_CODES, allEntries = true, beforeInvocation = true)
    @Override
    public void batchRemove(List<ScheduleTemplateCode> oList, int batchSize) { super.batchRemove(oList, batchSize); }
}
