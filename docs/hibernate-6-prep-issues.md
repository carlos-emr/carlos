# Hibernate 6 Preparation - GitHub Issues

These issues track forward-compatible changes that can be made now on Hibernate 5.x
to prepare for Hibernate 6 migration.

---

## Issue 1: Fix zero-based positional parameters (?0 → ?1)

**Title:** `Hibernate 6 Prep: Fix zero-based positional parameters (?0 → ?1)`

**Labels:** `type: maintenance`, `priority: medium`

**Body:**

### Summary
Replace zero-based positional parameters (`?0`) with JPA-standard one-based parameters (`?1`) to prepare for Hibernate 6 migration.

### Background
Hibernate 5 accepts both zero-based (`?0`) and one-based (`?1`) positional parameters. Hibernate 6 only accepts JPA-standard one-based parameters.

### Scope
- **166 occurrences** across **27 files**
- All in `src/main/java`

### Affected Directories
- `PMmodule/dao/` - Multiple DAO implementations
- `casemgmt/dao/` - Case management DAOs
- `daos/security/` - Security DAOs
- `commn/dao/DemographicDaoImpl.java`

### Migration Pattern
```java
// Before (H5 only)
"FROM Provider WHERE status = ?0 AND type = ?1"

// After (works in H5 AND H6)
"FROM Provider WHERE status = ?1 AND type = ?2"
```

**Important**: When shifting `?0` to `?1`, all subsequent parameters must also shift (`?1`→`?2`, `?2`→`?3`, etc.)

### Verification
- [ ] All `?0` patterns replaced
- [ ] Parameter indices shifted correctly
- [ ] Corresponding `setParameter()` calls updated if using positional indices
- [ ] Unit/integration tests pass

---

## Issue 2: Migrate Query.list() to getResultList()

**Title:** `Hibernate 6 Prep: Migrate Query.list() to getResultList()`

**Labels:** `type: maintenance`, `priority: low`

**Body:**

### Summary
Replace Hibernate-specific `.list()` calls with JPA-standard `.getResultList()` method.

### Background
`Query.list()` is deprecated in Hibernate 6. The JPA-standard `getResultList()` works in both Hibernate 5 and 6.

### Scope
- **64 occurrences** across **~17 files**

### Migration Pattern
```java
// Before
List results = query.list();

// After (works in H5 AND H6)
List results = query.getResultList();
```

### Verification
- [ ] All `.list()` calls on Query/TypedQuery replaced
- [ ] Return types unchanged
- [ ] Unit/integration tests pass

---

## Issue 3: Migrate uniqueResult() to getSingleResult()

**Title:** `Hibernate 6 Prep: Migrate uniqueResult() to getSingleResult()`

**Labels:** `type: maintenance`, `priority: low`

**Body:**

### Summary
Replace Hibernate-specific `.uniqueResult()` calls with JPA-standard `.getSingleResult()`.

### Background
`Query.uniqueResult()` is deprecated in Hibernate 6. The JPA-standard `getSingleResult()` works in both versions.

### Scope
- **4 occurrences** across **2 files**

### Migration Pattern
```java
// Before
Object result = query.uniqueResult();

// After (works in H5 AND H6)
try {
    Object result = query.getSingleResult();
} catch (NoResultException e) {
    // Handle no result case
}
```

**Note**: `getSingleResult()` throws `NoResultException` if no result found, while `uniqueResult()` returns null. Handle accordingly.

### Affected Files
- `CaseManagementNoteDAOImpl.java`
- `DemographicDaoImpl.java`

### Verification
- [ ] All `.uniqueResult()` calls replaced
- [ ] Null handling converted to exception handling where needed
- [ ] Unit/integration tests pass

---

## Issue 4: Migrate typed parameter setters to setParameter()

**Title:** `Hibernate 6 Prep: Migrate typed setters (setInteger, setString, etc.) to setParameter()`

**Labels:** `type: maintenance`, `priority: low`

**Body:**

### Summary
Replace Hibernate-specific typed parameter setters with JPA-standard `setParameter()`.

### Background
Methods like `setInteger()`, `setString()`, `setLong()` are removed in Hibernate 6. The generic `setParameter()` method works in both versions.

### Scope
- **4 occurrences** across **2 files**

### Affected Files
- `DemographicDaoImpl.java` (lines 137, 2972)
- `ProgramClientStatusDAOImpl.java` (lines 94, 95)

### Migration Pattern
```java
// Before (removed in H6)
query.setInteger("id", 123);
query.setString("name", "test");
query.setLong(1, 456L);

// After (works in H5 AND H6)
query.setParameter("id", 123);
query.setParameter("name", "test");
query.setParameter(1, 456L);
```

### Verification
- [ ] All typed setters replaced with `setParameter()`
- [ ] Type inference works correctly
- [ ] Unit/integration tests pass

---

## Issue 5: Migrate org.hibernate.Query to javax.persistence.Query

**Title:** `Hibernate 6 Prep: Migrate org.hibernate.Query imports to javax.persistence.Query`

**Labels:** `type: maintenance`, `priority: low`

**Body:**

### Summary
Replace Hibernate-specific Query import with JPA-standard Query interface.

### Background
`org.hibernate.Query` has a different API in Hibernate 6. Using JPA's `javax.persistence.Query` provides forward compatibility.

### Scope
- **9 imports** across **9 files**

### Affected Files
- `IssueDAOImpl.java`
- `SecuserroleDaoImpl.java`
- `SecobjprivilegeDaoImpl.java`
- `SecProviderDaoImpl.java`
- `ProviderDaoImpl.java`
- `ProgramTeamDAOImpl.java`
- `ProgramFunctionalUserDAOImpl.java`
- `ProgramClientStatusDAOImpl.java`
- `CaseManagementNoteDAOImpl.java`

### Migration Pattern
```java
// Before
import org.hibernate.Query;

// After (works in H5 AND H6)
import javax.persistence.Query;
// or for typed queries:
import javax.persistence.TypedQuery;
```

### Verification
- [ ] All `org.hibernate.Query` imports replaced
- [ ] Code compiles with JPA Query interface
- [ ] Unit/integration tests pass

---

## Issue 6: Migrate old Criteria API to JPA Criteria API

**Title:** `Hibernate 6 Prep: Migrate Hibernate Criteria API to JPA Criteria API`

**Labels:** `type: maintenance`, `priority: high`

**Body:**

### Summary
Replace deprecated Hibernate Criteria API with JPA-standard Criteria API.

### Background
Hibernate's proprietary Criteria API (`session.createCriteria()`) is completely removed in Hibernate 6. The JPA Criteria API works in both versions.

### Scope
- **8 usages** of `createCriteria()` across **6 files**

### Affected Files
- `DemographicDaoImpl.java` (lines 279, 1845, 1972)
- `CaseManagementNoteDAOImpl.java` (line 552)
- `ClientReferralDAOImpl.java` (line 289)
- `SecuserroleDaoImpl.java` (line 229)
- `SecProviderDaoImpl.java` (line 123)
- `ProviderDaoImpl.java` (line 382)

### Migration Pattern
```java
// Before (removed in H6)
Criteria criteria = session.createCriteria(Demographic.class);
criteria.add(Restrictions.eq("status", "AC"));
criteria.addOrder(Order.asc("lastName"));
List results = criteria.list();

// After (works in H5 AND H6)
CriteriaBuilder cb = session.getCriteriaBuilder();
CriteriaQuery<Demographic> cq = cb.createQuery(Demographic.class);
Root<Demographic> root = cq.from(Demographic.class);
cq.where(cb.equal(root.get("status"), "AC"));
cq.orderBy(cb.asc(root.get("lastName")));
List<Demographic> results = session.createQuery(cq).getResultList();
```

### Additional Removals Required
- Remove imports: `org.hibernate.Criteria`
- Remove imports: `org.hibernate.criterion.*`

### Verification
- [ ] All `createCriteria()` calls replaced
- [ ] All criterion imports removed
- [ ] Equivalent query behavior verified
- [ ] Unit/integration tests pass

---

## Issue 7: Migrate createSQLQuery() to createNativeQuery()

**Title:** `Hibernate 6 Prep: Migrate createSQLQuery() to createNativeQuery()`

**Labels:** `type: maintenance`, `priority: medium`

**Body:**

### Summary
Replace deprecated `createSQLQuery()` with `createNativeQuery()`.

### Background
`Session.createSQLQuery()` is removed in Hibernate 6. The `createNativeQuery()` method works in both Hibernate 5.2+ and Hibernate 6.

### Scope
- **16 occurrences** across **5 files**

### Affected Files
- `DemographicDaoImpl.java` (7 usages)
- `CaseManagementNoteDAOImpl.java` (3 usages)
- `ProviderDaoImpl.java` (4 usages)
- `LookupDaoImpl.java` (1 usage)
- `AbstractQueryHandler.java` (1 usage)

### Migration Pattern
```java
// Before (removed in H6)
SQLQuery query = session.createSQLQuery(sql);

// After (works in H5.2+ AND H6)
NativeQuery query = session.createNativeQuery(sql);
// or with result class:
NativeQuery<MyEntity> query = session.createNativeQuery(sql, MyEntity.class);
```

### Import Change
```java
// Before
import org.hibernate.SQLQuery;

// After
import org.hibernate.query.NativeQuery;
```

### Verification
- [ ] All `createSQLQuery()` calls replaced
- [ ] `SQLQuery` imports replaced with `NativeQuery`
- [ ] Result mapping still works correctly
- [ ] Unit/integration tests pass

---

## Issue 8: Remove HibernateTemplate and HibernateDaoSupport

**Title:** `Hibernate 6 Prep: Remove HibernateTemplate and HibernateDaoSupport usage`

**Labels:** `type: maintenance`, `priority: high`

**Body:**

### Summary
Migrate from Spring's `HibernateTemplate`/`HibernateDaoSupport` to direct `EntityManager` or `SessionFactory` injection.

### Background
`HibernateTemplate` and `HibernateDaoSupport` are part of `spring-orm-hibernate5` which will not exist for Hibernate 6. Direct JPA `EntityManager` or Hibernate `SessionFactory` injection works in both versions.

### Scope
- **32 files** extending `HibernateDaoSupport`
- **318 calls** to `getHibernateTemplate()`

### Affected Directories
- `PMmodule/dao/` (13 files)
- `casemgmt/dao/` (8 files)
- `daos/security/` (6 files)
- `commn/dao/` (3 files)
- `daos/` (2 files)

### Migration Pattern
```java
// Before
public class MyDaoImpl extends HibernateDaoSupport implements MyDao {

    public List<Entity> findAll() {
        return getHibernateTemplate().find("FROM Entity");
    }

    public void save(Entity entity) {
        getHibernateTemplate().save(entity);
    }
}

// After (works in H5 AND H6)
@Repository
public class MyDaoImpl implements MyDao {

    @PersistenceContext
    private EntityManager entityManager;

    public List<Entity> findAll() {
        return entityManager.createQuery("FROM Entity", Entity.class)
            .getResultList();
    }

    public void save(Entity entity) {
        entityManager.persist(entity);
    }
}
```

### Common Method Mappings
| HibernateTemplate | EntityManager |
|-------------------|---------------|
| `find(hql)` | `createQuery(hql).getResultList()` |
| `get(Class, id)` | `find(Class, id)` |
| `save(entity)` | `persist(entity)` |
| `update(entity)` | `merge(entity)` |
| `delete(entity)` | `remove(entity)` |
| `saveOrUpdate(entity)` | `merge(entity)` |

### Verification
- [ ] All DAOs migrated from HibernateDaoSupport
- [ ] All getHibernateTemplate() calls removed
- [ ] Transaction management still works
- [ ] Unit/integration tests pass

---

## Issue 9: Remove Example.create() usage

**Title:** `Hibernate 6 Prep: Remove Example.create() query-by-example usage`

**Labels:** `type: maintenance`, `priority: medium`

**Body:**

### Summary
Replace Hibernate's `Example.create()` query-by-example with JPA Criteria API.

### Background
`Example.create()` is part of the old Hibernate Criteria API which is removed in Hibernate 6.

### Scope
- **2 occurrences** across **2 files**

### Affected Files
- `SecProviderDaoImpl.java`
- `SecuserroleDaoImpl.java`

### Migration Pattern
```java
// Before (removed in H6)
Criteria criteria = session.createCriteria(SecProvider.class);
criteria.add(Example.create(exampleEntity));
List results = criteria.list();

// After (works in H5 AND H6)
CriteriaBuilder cb = session.getCriteriaBuilder();
CriteriaQuery<SecProvider> cq = cb.createQuery(SecProvider.class);
Root<SecProvider> root = cq.from(SecProvider.class);

List<Predicate> predicates = new ArrayList<>();
if (exampleEntity.getStatus() != null) {
    predicates.add(cb.equal(root.get("status"), exampleEntity.getStatus()));
}
// Add more predicates for non-null fields...

cq.where(predicates.toArray(new Predicate[0]));
List<SecProvider> results = session.createQuery(cq).getResultList();
```

### Verification
- [ ] All `Example.create()` calls replaced
- [ ] Query-by-example logic preserved
- [ ] Unit/integration tests pass

---

## Issue 10: Remove deprecated Hibernate Criterion imports

**Title:** `Hibernate 6 Prep: Remove org.hibernate.criterion.* imports`

**Labels:** `type: maintenance`, `priority: medium`

**Body:**

### Summary
Remove all imports from `org.hibernate.criterion.*` package and replace with JPA Criteria API equivalents.

### Background
The entire `org.hibernate.criterion` package is removed in Hibernate 6 as part of the old Criteria API removal.

### Scope
- **15 imports** across **6 files**

### Affected Imports
- `org.hibernate.criterion.Expression` → Use `CriteriaBuilder` methods
- `org.hibernate.criterion.Restrictions` → Use `CriteriaBuilder` methods
- `org.hibernate.criterion.Order` → Use `CriteriaBuilder.asc()`/`desc()`
- `org.hibernate.criterion.Example` → Manual predicate building
- `org.hibernate.criterion.Property` → Use `Root.get()`
- `org.hibernate.criterion.DetachedCriteria` → Use `CriteriaQuery` subqueries

### Affected Files
- `DemographicDaoImpl.java` (6 imports)
- `CaseManagementNoteDAOImpl.java` (3 imports)
- `ProviderDaoImpl.java` (3 imports)
- `SecuserroleDaoImpl.java` (1 import)
- `ClientReferralDAOImpl.java` (1 import)
- `SecProviderDaoImpl.java` (1 import)

### Migration Mappings
| Criterion | JPA Criteria Equivalent |
|-----------|------------------------|
| `Restrictions.eq(prop, val)` | `cb.equal(root.get(prop), val)` |
| `Restrictions.ne(prop, val)` | `cb.notEqual(root.get(prop), val)` |
| `Restrictions.gt(prop, val)` | `cb.greaterThan(root.get(prop), val)` |
| `Restrictions.lt(prop, val)` | `cb.lessThan(root.get(prop), val)` |
| `Restrictions.like(prop, val)` | `cb.like(root.get(prop), val)` |
| `Restrictions.in(prop, vals)` | `root.get(prop).in(vals)` |
| `Restrictions.isNull(prop)` | `cb.isNull(root.get(prop))` |
| `Restrictions.and(c1, c2)` | `cb.and(p1, p2)` |
| `Restrictions.or(c1, c2)` | `cb.or(p1, p2)` |
| `Order.asc(prop)` | `cb.asc(root.get(prop))` |
| `Order.desc(prop)` | `cb.desc(root.get(prop))` |

### Note
This issue overlaps with Issue #6 (Criteria API migration). Complete that issue first, then verify all criterion imports are removed.

### Verification
- [ ] All `org.hibernate.criterion.*` imports removed
- [ ] No compilation errors
- [ ] Unit/integration tests pass

---

## Summary Table

| Issue | Title | Scope | Priority | Effort |
|-------|-------|-------|----------|--------|
| 1 | Fix ?0 → ?1 positional params | 166 occurrences / 27 files | Medium | Low |
| 2 | Migrate .list() → .getResultList() | 64 occurrences | Low | Low |
| 3 | Migrate .uniqueResult() → .getSingleResult() | 4 occurrences | Low | Low |
| 4 | Migrate typed setters → setParameter() | 4 occurrences | Low | Low |
| 5 | Migrate org.hibernate.Query import | 9 files | Low | Low |
| 6 | Migrate Criteria API to JPA | 8 usages / 6 files | High | **High** |
| 7 | Migrate createSQLQuery() → createNativeQuery() | 16 occurrences / 5 files | Medium | Low |
| 8 | Remove HibernateTemplate/DaoSupport | 32 files / 318 calls | High | **High** |
| 9 | Remove Example.create() | 2 occurrences | Medium | Medium |
| 10 | Remove Criterion imports | 15 imports / 6 files | Medium | Medium |

**Total effort**: ~283 code changes across ~50 unique files

---

*Generated for Hibernate 6 migration preparation*
*All changes are forward-compatible with Hibernate 5.x*
