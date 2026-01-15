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

import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.NonUniqueResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import ca.openosp.openo.commn.model.AbstractModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import ca.openosp.openo.util.ParamAppender;

@Transactional
public abstract class AbstractDaoImpl<T extends AbstractModel<?>> implements AbstractDao<T> {

    protected Class<T> modelClass;

    @PersistenceContext(unitName = "entityManagerFactory")
    protected EntityManager entityManager = null;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    public AbstractDaoImpl(Class<T> modelClass) {
        setModelClass(modelClass);
    }

    /**
     * aka update
     */
    @Override
    public void merge(AbstractModel<?> o) {
        entityManager.merge(o);
    }

	/**
	 * Flushes the persistence context.
	 * This forces any pending changes to be synchronized with the database immediately.
	 */
	public void flush() {
		entityManager.flush();
	}

    /**
     * aka create
     */
    @Override
    public void persist(AbstractModel<?> o) {
        entityManager.persist(o);
    }

    @Override
    public void batchPersist(List<T> oList) {
        batchPersist(oList, 25);
    }

    @Override
    public void batchPersist(List<T> oList, int batchSize) {
        EntityManager batchEntityManager = null;
        EntityTransaction transaction = null;
        try {
            batchEntityManager = entityManagerFactory.createEntityManager();
            transaction = batchEntityManager.getTransaction();
            transaction.begin();
            int i = 0;
            for (T entity : oList) {
                batchEntityManager.persist(entity);
                i++;
                if (i > 0 && i % batchSize == 0) {
                    batchEntityManager.flush();
                    batchEntityManager.clear();
                    transaction.commit();
                    transaction.begin();
                }
            }
            transaction.commit();
        } catch (RuntimeException e) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            throw e;
        } finally {
            if (batchEntityManager != null) {
                batchEntityManager.close();
            }
        }
    }

    /**
     * You can only remove attached instances.
     */
    @Override
    public void remove(AbstractModel<?> o) {
        entityManager.remove(o);
    }

    @Override
    public void batchRemove(List<T> oList) {
        batchRemove(oList, 25);
    }

    @Override
    public void batchRemove(List<T> oList, int batchSize) {
        EntityManager batchEntityManager = null;
        EntityTransaction transaction = null;
        try {
            batchEntityManager = entityManagerFactory.createEntityManager();
            transaction = batchEntityManager.getTransaction();
            transaction.begin();
            int i = 0;
            for (T entity : oList) {
                // Gets the model and gets the reference to it so that it is attached to the new entity manager's session
                Object entityObj = batchEntityManager.getReference(entity.getClass(), entity.getId());
                batchEntityManager.remove(entityObj);
                i++;
                if (i > 0 && i % batchSize == 0) {
                    batchEntityManager.flush();
                    batchEntityManager.clear();
                    transaction.commit();
                    transaction.begin();
                }
                // Gets the model and gets the reference to it so that it is attached to the new
                // entity manager's session
            }
            transaction.commit();
        } catch (RuntimeException e) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            throw e;
        } finally {
            if (batchEntityManager != null) {
                batchEntityManager.close();
            }
        }
    }

    /**
     * You can only refresh attached instances.
     */
    @Override
    public void refresh(AbstractModel<?> o) {
        entityManager.refresh(o);
    }

    @Override
    public T find(Object id) {
        return (entityManager.find(modelClass, id));
    }

    @Override
    public T find(int id) {
        return (entityManager.find(modelClass, id));
    }

	/**
	 * Finds an entity by its primary key and detaches it from the persistence context.
	 * <p>
	 * This method retrieves an entity from the database based on the provided `id`.  After retrieval,
	 * the entity is detached from the persistence context. This means that subsequent changes to the entity
	 * will not be tracked or automatically persisted to the database. It is useful when you need to work with
	 * an entity outside the scope of a transaction without affecting the database state.
	 *
	 * @param id The primary key of the entity to retrieve.
	 * @return The detached entity, or `null` if no entity with the given `id` exists.
	 */
	@Override
	public T findDetached(Object id) {
		T t = this.entityManager.find(this.modelClass, id);

		if (Objects.nonNull(t))
			this.detach(t);

		return t;
	}

	/**
	 * Detaches the given entity from the persistence context.
	 *  Changes made to the entity after detachment will not be synchronized with the database.
	 * @param t the entity to detach
	 */
	@Override
	public void detach(@Nonnull T t) {
		this.entityManager.detach(t);
	}

    /**
     * Check if entity exists in the current transaction context.
     */
    @Override
    public boolean contains(AbstractModel<?> o) {
        return entityManager.contains(o);
    }

    /**
     * Fetches all instances of the persistent class handled by this DAO.
     *
     * @return Returns all instances available in the backend
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<T> findAll(Integer offset, Integer limit) {
        Query query = entityManager.createQuery("FROM " + modelClass.getSimpleName());

        if (offset != null && offset > 0) {
            query.setFirstResult(offset);
        }
        // mandatory set limit
        int intLimit = (limit == null) ? getMaxSelectSize() : limit;
        if (intLimit > getMaxSelectSize()) {
            throw new MaxSelectLimitExceededException(getMaxSelectSize(), limit);
        }
        query.setMaxResults(intLimit);

        return query.getResultList();
    }

    protected int getMaxSelectSize() {
        return MAX_LIST_RETURN_SIZE;
    }

    /**
     * Removes an entity based on the ID
     *
     * @param id ID of the entity to be removed
     * @return Returns true if entity has been removed and false otherwise
     */
    @Override
    public boolean remove(Object id) {
        T abstractModel = find(id);
        if (abstractModel == null) {
            return false;
        }

        remove(abstractModel);
        return true;
    }

    protected T getSingleResultOrNull(Query query) {
        query.setMaxResults(1);

        @SuppressWarnings("unchecked")
        List<T> results = query.getResultList();
        if (results.size() == 1)
            return (results.get(0));
        else if (results.size() == 0)
            return (null);
            // this should never happen if we set max results to 1 :)
        else
            throw (new NonUniqueResultException(
                    "SingleResult requested but result was not unique : " + results.size()));
    }

    protected Long getCountResult(Query query) {
        query.setMaxResults(1);

        @SuppressWarnings("unchecked")
        List<Long> results = query.getResultList();
        if (results.size() == 1)
            return (results.get(0));
        else if (results.size() == 0)
            return (null);
            // this should never happen if we set max results to 1 :)
        else
            throw (new NonUniqueResultException(
                    "SingleResult requested but result was not unique : " + results.size()));
    }

    @Override
    public int getCountAll() {
        // new JPA way of doing it, but our hibernate is too old or doesn't support
        // primitives yet?
        // String sqlCommand="select count(*) from "+modelClass.getSimpleName();
        // Query query = entityManager.createNativeQuery(sqlCommand, Integer.class);
        // return((Integer)query.getSingleResult());

        String tableName = modelClass.getSimpleName();
        javax.persistence.Table t = modelClass.getAnnotation(javax.persistence.Table.class);
        if (t != null && t.name() != null && t.name().length() > 0) {
            tableName = t.name();
        }

        // older hibernate work around
        String sqlCommand = "select count(*) from " + tableName;
        Query query = entityManager.createNativeQuery(sqlCommand);
        return (((Number) query.getSingleResult()).intValue());
    }

    /**
     * Gets base JPQL query for the model class.
     *
     * @return Returns the JPQL clause in the form of
     * <code>"FROM {@link #getModelClassName()} AS e "</code>.
     * <code>e</code> stands for "entity"
     */
    protected String getBaseQuery() {
        return getBaseQueryBuf(null, null).toString();
    }

    protected String getBaseQuery(String alias) {
        return getBaseQueryBuf(null, alias).toString();
    }

    /**
     * Creates new string builder containing the base query with the specified
     * select and alias strings
     *
     * @param select Select clause to be appended to the query. May be null
     * @param alias  Alias to be used for referencing the base entity class
     * @return Returns the string buffer containing the base query
     */
    protected StringBuilder getBaseQueryBuf(String select, String alias) {
        StringBuilder buf = new StringBuilder();
        if (select != null) {
            buf.append(select);
            buf.append(" ");
        }
        buf.append("FROM ");
        buf.append(getModelClassName());
        if (alias != null)
            buf.append(" AS ").append(alias).append(" ");
        return buf;
    }

    @Override
    public Class<T> getModelClass() {
        return modelClass;
    }

    protected Query createQuery(String alias, String whereClause) {
        return createQuery(null, alias, whereClause);
    }

    /**
     * Creates a query with the specified entity alias and where clause
     * <p>
     * <p/>
     * <p>
     * For example, invoking
     *
     * <pre>
     * 		createQuery("select entity.id" "entity", "entity.propertyName like :propertyValue");
     * </pre>
     * <p>
     * would create query:
     *
     * <pre>
     * 		SELECT entity.id FROM ModelClass AS entity WHERE entity.propertyName like :propertyValue
     * </pre>
     *
     * @param select      Select clause to be included in the query
     * @param alias       Alias to be included in the query
     * @param whereClause Where clause to be included in the query
     * @return Returns the query
     */
    protected Query createQuery(String select, String alias, String whereClause) {
        StringBuilder buf = createQueryString(select, alias, whereClause);
        return entityManager.createQuery(buf.toString());
    }

    /**
     * Creates query string for the specified alias and where clause
     *
     * @param select      Select clause
     * @param alias       Alias to be included in the query
     * @param whereClause Where clause to be included in the query
     * @return Returns the query string
     * @see #createQuery(String, String)
     */
    protected StringBuilder createQueryString(String select, String alias, String whereClause) {
        StringBuilder buf = getBaseQueryBuf(select, alias);
        if (whereClause != null && !whereClause.isEmpty()) {
            buf.append("WHERE ");
            buf.append(whereClause);
        }
        return buf;
    }

    protected StringBuilder createQueryString(String alias, String whereClause) {
        return createQueryString(null, alias, whereClause);
    }

    /**
     * Gets name of the model class.
     *
     * @return Returns the class name without package prefix
     */
    protected String getModelClassName() {
        return getModelClass().getSimpleName();
    }

    private void setModelClass(Class<T> modelClass) {
        this.modelClass = modelClass;
    }

    /**
     * Saves or updates the entity based on depending if it's persistent, as
     * determined by {@link AbstractModel#isPersistent()}
     *
     * @param entity Entity to be saved or updated
     * @return Returns the entity
     */
    @Override
    public T saveEntity(T entity) {
        if (entity.isPersistent()) {
            merge(entity);
        } else {
            persist(entity);
        }
        return entity;
    }

    /**
     * Executes a parameterized native SQL query with named parameters.
     * This method provides protection against SQL injection by properly binding parameters.
     *
     * @param sql The SQL query with named parameters (e.g., :paramName)
     * @param params Map of parameter names to values
     * @return List of Object arrays containing the query results
     */
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Object[]> runParameterizedNativeQuery(String sql, Map<String, Object> params) {
        Query query = entityManager.createNativeQuery(sql);

        if (params != null) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                query.setParameter(entry.getKey(), entry.getValue());
            }
        }

        List resultList = query.getResultList();
        return resultList;
    }

    /**
     * Gets parameter appender with default base query set
     *
     * @return Returns new appender
     * @see #getBaseQuery()
     */
    protected ParamAppender getAppender() {
        return new ParamAppender(getBaseQuery());
    }

    /**
     * Gets parameter appender with default base query set
     *
     * @param alias Alias to be used in the query
     * @return Returns new appender
     * @see #getBaseQuery(String)
     */
    protected ParamAppender getAppender(String alias) {
        return new ParamAppender(getBaseQuery(alias));
    }

    protected final void setDefaultLimit(Query query) {
        query.setMaxResults(getMaxSelectSize());
    }

    protected final void setLimit(Query query, int itemsToReturn) {
        if (itemsToReturn > getMaxSelectSize())
            throw (new IllegalArgumentException("Requested too large of a result list size : " + itemsToReturn));

        query.setMaxResults(itemsToReturn);
    }

    protected final void setLimit(Query query, int startIndex, int itemsToReturn) {
        query.setFirstResult(startIndex);
        setLimit(query, itemsToReturn);
    }
}
