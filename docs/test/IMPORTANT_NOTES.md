# Important Notes for OpenO Test Framework

## Author: yingbull
## Date: September 15, 2025

## Critical Package Structure Issues

### ProviderDao Location (EXCEPTION TO THE RULE)

**IMPORTANT**: There are multiple ProviderDao classes in the codebase, and you must use the correct one:

#### The Correct ProviderDao
```java
import io.github.carlos_emr.carlos.dao.ProviderDao;  // ✅ CORRECT
```

#### Incorrect Locations (DO NOT USE)
```java
import io.github.carlos_emr.carlos.commn.dao.ProviderDao;     // ❌ WRONG - doesn't exist
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;  // ❌ WRONG - different purpose
```

#### Why This Matters
- Most DAOs are in `io.github.carlos_emr.carlos.commn.dao.*`
- ProviderDao is an **EXCEPTION** - it's at `io.github.carlos_emr.carlos.dao.ProviderDao`
- This is explicitly documented in CLAUDE.md as a special case
- Using the wrong one will cause compilation errors or incorrect behavior

### General DAO Import Pattern

For most DAOs, use the commn.dao package:
```java
// Standard pattern for most DAOs
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.TicklerDao;
import io.github.carlos_emr.carlos.commn.dao.AppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.DrugDao;
```

Exception for ProviderDao:
```java
// Special case - ProviderDao is NOT in commn.dao
import io.github.carlos_emr.carlos.dao.ProviderDao;
```

## Multiple ProviderDao Classes in Codebase

The system has several Provider-related DAOs for different purposes:

1. **`io.github.carlos_emr.carlos.dao.ProviderDao`** - Main provider DAO (USE THIS ONE)
2. **`io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao`** - Program module specific
3. **`io.github.carlos_emr.carlos.commn.dao.ReportProviderDao`** - Reporting specific
4. **`io.github.carlos_emr.carlos.daos.security.SecProviderDao`** - Security specific

Always verify you're importing the correct one for your use case. When in doubt, check CLAUDE.md.

## Test Data Conventions

### Provider Numbers
- Use string constants for provider numbers: `"999998"`, `"999997"`
- These are typically 6-digit strings, not integers
- No need to import ProviderDao if you're just using provider numbers as strings

### Demographic Numbers
- Use Integer for demographic IDs: `12345`, `12346`
- Create test demographics in setUp() for isolation
- Clean up in tearDown() or rely on @Rollback

## SpringUtils Anti-Pattern

### The Problem
Production code uses:
```java
SomeDao dao = SpringUtils.getBean(SomeDao.class);
```

Instead of proper dependency injection:
```java
@Autowired
private SomeDao dao;
```

### How Tests Handle This
1. Base test classes initialize SpringUtils with test context
2. Use `verifySpringUtilsIntegration()` to ensure it works
3. Can replace beans using `replaceSpringUtilsBean()` for mocking

## Partial Mock Implementations

When implementing mock versions of interfaces with many methods (like TicklerManager with 50+ methods):

1. **Only implement what you need** - Don't implement all 50+ methods
2. **Document it clearly** - State it's a partial implementation
3. **Let it fail explicitly** - Unimplemented methods will throw `UnsupportedOperationException`
4. **Add methods as needed** - When new tests require them

Example:
```java
/**
 * Partial implementation for testing.
 * Only implements methods used in these specific tests.
 */
private class MockTicklerManager implements TicklerManager {
    // Only implement the 5-10 methods your tests actually use
}
```

## Common Testing Pitfalls

### 1. Wrong DAO Package
- Always check if it's a special case (like ProviderDao)
- Most are in commn.dao, but not all

### 2. Date Handling
- Use `Date` objects, not strings for most date fields
- Service dates, creation dates, update dates are `java.util.Date`
- Format dates properly when setting request parameters

### 3. Transaction Rollback
- Tests use @Transactional and @Rollback by default
- Data won't persist between tests
- Good for isolation, but can be confusing if you expect data to remain

### 4. Security Context
- Always set up LoggedInInfo in tests that need it
- Use mockLoggedInInfo for web tests
- Security checks will fail without proper setup

---

*Last Updated: September 15, 2025*
*Author: yingbull*