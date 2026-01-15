//CHECKSTYLE:OFF
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
 */
package ca.openosp.openo.commn.dao;

import ca.openosp.openo.commn.model.AbstractModel;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

public interface AbstractDao<T extends AbstractModel<?>> {
    public static final int MAX_LIST_RETURN_SIZE = 5000;

    void merge(AbstractModel<?> o);

    void persist(AbstractModel<?> o);

    void batchPersist(List<T> oList);

    void batchPersist(List<T> oList, int batchSize);

    void remove(AbstractModel<?> o);

    void batchRemove(List<T> oList);

    void batchRemove(List<T> oList, int batchSize);

    void refresh(AbstractModel<?> o);

    T find(Object id);

    T find(int id);

    T findDetached(Object id);

    void detach(@Nonnull T t);

    boolean contains(AbstractModel<?> o);

    List<T> findAll(Integer offset, Integer limit);

    boolean remove(Object id);

    int getCountAll();

    List<Object[]> runParameterizedNativeQuery(String sql, Map<String, Object> params);

    T saveEntity(T entity);

    Class<T> getModelClass();

    void flush();

}
