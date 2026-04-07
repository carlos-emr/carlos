---
description: "JUnit 5 test writing rules and pitfall avoidance"
applyTo: "src/test-modern/**/*.java,src/test/**/*Test.java"
---

# Test Writing Rules

## Before Writing Any Test

1. **Read the actual DAO/Manager interface** -- verify methods exist
2. **Never invent method names** -- check actual signatures
3. **Read DAO implementation** -- method names can be misleading

## Base Class Selection

- `CarlosTestBase` -- integration tests (Spring context + H2 database)
- `CarlosUnitTestBase` -- unit tests (mocked SpringUtils, no database)

## BDD Naming Convention

`should<Action>_<preposition><Condition>()` -- ONE underscore, camelCase, `should` prefix.

## Critical Pitfalls

- Use `hibernateTemplate.flush()` NOT `entityManager.flush()` for HibernateDaoSupport DAOs
- HBM property names are case-sensitive (check the `.hbm.xml` file)
- H2 reserved words need backtick quoting in HBM XML
- DAO `save*()` methods may override fields like `update_date`
- Don't assert instance identity across Spring contexts (`isSameAs`)
- HQL LIKE queries need explicit `%` wildcards in test data
