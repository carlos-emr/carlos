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

import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.annotation.Nonnull;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.NonUniqueResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.AbstractModel;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.github.carlos_emr.carlos.util.ParamAppender;

/**
 * Base DAO implementation providing common CRUD operations for JPA entities.
 *
 * <p><strong>AOP / caching caveat:</strong> Several methods in this class delegate to
 * other public methods via {@code this.xxx()} (self-invocation). Because Spring AOP
 * proxies are method-level interceptors on the proxy object, self-invocations bypass
 * the proxy entirely. This means {@code @Cacheable} or {@code @CacheEvict} annotations
 * on the target of the self-call are <em>never triggered</em>.</p>
 *
 * <p>Consequently, if a subclass caches read methods, it <strong>must</strong> override
 * the write methods that perform self-invocation ({@link #batchPersist(List, int)},
 * {@link #batchRemove(List, int)}, {@link #saveEntity},
 * {@link #remove(Object)}) and annotate each override with the appropriate
 * {@code @CacheEvict}. Without these overrides, batch and save operations will leave
 * stale entries in the cache.</p>
 *
 * @param <T> the entity type managed by this DAO
 */
@Transactional
public abstract class AbstractDaoImpl<T extends AbstractModel<?>> implements AbstractDao<T> {

    private static final Logger BATCH_LOGGER = MiscUtils.getLogger();

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

    // SUPPORTS (not the class-default REQUIRED): batchPersist manages its OWN EntityManager and
    // per-chunk resource-local transactions, so it must not open a Spring transaction of its own.
    // With SUPPORTS the batch runs non-transactionally when the caller has no transaction (the
    // standalone-import case), and only joins — and so warnIfInSpringManagedTransaction only warns
    // about — a genuinely pre-existing caller transaction whose rollback the per-chunk commits escape.
    @Override
    @Deprecated
    @Transactional(propagation = Propagation.SUPPORTS)
    public void batchPersist(List<T> oList) {
        batchPersistWithIndependentCommits(oList, 25);
    }

    /**
     * Persists a list of entities in batches.
     *
     * <p><strong>AOP caveat:</strong> This method creates its own {@link EntityManager} and
     * calls {@code persist()} on it directly — it does <em>not</em> delegate through the
     * Spring proxy. If a subclass annotates {@link #persist(AbstractModel)} with
     * {@code @CacheEvict}, that eviction will <strong>not</strong> be triggered by this
     * method. Subclasses that cache reads must override this method and add appropriate
     * {@code @CacheEvict} annotations.</p>
     */
    @Override
    @Deprecated
    @Transactional(propagation = Propagation.SUPPORTS)
    public void batchPersist(List<T> oList, int batchSize) {
        warnIfInSpringManagedTransaction("batchPersist");
        batchPersistWithIndependentCommitsInternal(oList, batchSize);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void batchPersistAtomically(List<T> oList) {
        batchPersistAtomically(oList, 25);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void batchPersistAtomically(List<T> oList, int batchSize) {
        requirePositiveBatchSize(batchSize);
        int i = 0;
        for (T entity : oList) {
            entityManager.persist(entity);
            i++;
            if (i % batchSize == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
        entityManager.flush();
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public void batchPersistWithIndependentCommits(List<T> oList) {
        batchPersistWithIndependentCommitsInternal(oList, 25);
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public void batchPersistWithIndependentCommits(List<T> oList, int batchSize) {
        batchPersistWithIndependentCommitsInternal(oList, batchSize);
    }

    private void batchPersistWithIndependentCommitsInternal(List<T> oList, int batchSize) {
        requirePositiveBatchSize(batchSize);
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
     * Warns when a batch method runs inside an active Spring-managed transaction.
     *
     * <p>{@link #batchPersist} / {@link #batchRemove} open their OWN {@link EntityManager} and commit
     * each chunk in its own resource-local transaction, so they do NOT participate in — and cannot be
     * rolled back by — a surrounding {@code @Transactional} boundary: a committed chunk survives a
     * caller-side rollback. That per-chunk-commit behaviour is deliberately relied on by large
     * standalone imports (which run with no ambient transaction). Calling these methods from within a
     * transactional service is almost always a mistake, so it is surfaced here rather than silently
     * escaping the transaction. (Reworking the semantics to join the ambient transaction would collapse
     * large imports into a single long transaction and is tracked as separate work.)</p>
     *
     * <p>The batch methods declare {@link Propagation#SUPPORTS} precisely so this check is meaningful:
     * they never start a Spring transaction of their own, so an active transaction here can only be a
     * <em>pre-existing caller</em> transaction (the risky case). Under the class-default
     * {@code REQUIRED} this warning fired on every call — including standalone imports — because the
     * batch call itself would have started one.</p>
     */
    private static void warnIfInSpringManagedTransaction(String method) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            BATCH_LOGGER.warn("{} was called inside an active Spring transaction, but it commits each "
                    + "chunk in its own transaction and does not participate in the caller's rollback; "
                    + "committed chunks will survive a surrounding rollback.", method);
        }
    }

    /**
     * You can only remove attached instances.
     */
    @Override
    public void remove(AbstractModel<?> o) {
        entityManager.remove(o);
    }

    // SUPPORTS for the same reason as batchPersist: batchRemove owns its EntityManager and per-chunk
    // transactions, so it must not start a Spring transaction — that is what made the warning fire on
    // every call. See batchPersist(List) above.
    @Override
    @Deprecated
    @Transactional(propagation = Propagation.SUPPORTS)
    public void batchRemove(List<T> oList) {
        batchRemoveWithIndependentCommits(oList, 25);
    }

    /**
     * Removes a list of entities in batches.
     *
     * <p><strong>AOP caveat:</strong> This method creates its own {@link EntityManager} and
     * calls {@code remove()} on it directly — it does <em>not</em> delegate through the
     * Spring proxy. If a subclass annotates {@link #remove(AbstractModel)} with
     * {@code @CacheEvict}, that eviction will <strong>not</strong> be triggered by this
     * method. Subclasses that cache reads must override this method and add appropriate
     * {@code @CacheEvict} annotations.</p>
     */
    @Override
    @Deprecated
    @Transactional(propagation = Propagation.SUPPORTS)
    public void batchRemove(List<T> oList, int batchSize) {
        warnIfInSpringManagedTransaction("batchRemove");
        batchRemoveWithIndependentCommitsInternal(oList, batchSize);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void batchRemoveAtomically(List<T> oList) {
        batchRemoveAtomically(oList, 25);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void batchRemoveAtomically(List<T> oList, int batchSize) {
        requirePositiveBatchSize(batchSize);
        int i = 0;
        for (T entity : oList) {
            Object attached = entityManager.getReference(entity.getClass(), entity.getId());
            entityManager.remove(attached);
            i++;
            if (i % batchSize == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
        entityManager.flush();
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public void batchRemoveWithIndependentCommits(List<T> oList) {
        batchRemoveWithIndependentCommitsInternal(oList, 25);
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public void batchRemoveWithIndependentCommits(List<T> oList, int batchSize) {
        batchRemoveWithIndependentCommitsInternal(oList, batchSize);
    }

    private void batchRemoveWithIndependentCommitsInternal(List<T> oList, int batchSize) {
        requirePositiveBatchSize(batchSize);
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

    private static void requirePositiveBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
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
     * Removes an entity based on the ID.
     *
     * <p><strong>AOP caveat:</strong> This method delegates to {@link #find(Object)} and
     * {@link #remove(AbstractModel)} via {@code this.xxx()} (self-invocation), which
     * bypasses the Spring AOP proxy. If a subclass annotates those methods with
     * {@code @CacheEvict}, the evictions will <strong>not</strong> be triggered through
     * this path. Subclasses that cache reads must override this method and add
     * appropriate {@code @CacheEvict} annotations.</p>
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
        jakarta.persistence.Table t = modelClass.getAnnotation(jakarta.persistence.Table.class);
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
        return entityManager.createQuery(buf.toString()); // nosemgrep: hibernate-sqli, formatted-sql-string -- query construction utility; callers provide parameterized WHERE clauses
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
     * <p><strong>AOP caveat:</strong> This method delegates to {@link #merge(AbstractModel)}
     * or {@link #persist(AbstractModel)} via {@code this.xxx()} (self-invocation), which
     * bypasses the Spring AOP proxy. If a subclass annotates those methods with
     * {@code @CacheEvict}, the evictions will <strong>not</strong> be triggered through
     * this path. Subclasses that cache reads must override this method and add
     * appropriate {@code @CacheEvict} annotations.</p>
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
