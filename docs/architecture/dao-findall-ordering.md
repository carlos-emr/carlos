# Stable ordering for generic DAO paging

`AbstractDaoImpl.findAll(offset, limit)` is the generic "list every row of this entity, one page at
a time" read that every JPA DAO inherits. Until issue
[#5308](https://github.com/carlos-emr/carlos/issues/5308) it ran `FROM <Entity>` with no
`ORDER BY` before applying the offset and limit. SQL gives an unordered query no defined row
order, so the rows on one page had no defined relationship to the rows on the next.

This is not theoretical on MariaDB. When a secondary index covers every column a query needs,
InnoDB scans that index instead of the primary key, and an unordered query returns rows in index
order:

```text
CREATE TABLE t (id INT PRIMARY KEY AUTO_INCREMENT, sort_key VARCHAR(20), KEY (sort_key));
-- sort keys inserted out of id order
SELECT id, sort_key FROM t LIMIT 3;   -- id 4 'a', 6 'b', 2 'c'   (sort_key order, not id order)
```

## Contract

- **Order.** `findAll` orders by the entity identifier, ascending. The identifier is unique, so the
  order is total: pages read with increasing offsets concatenate to the sequence one unpaged read
  returns, with no repeats or gaps, while the table is unchanged.
- **Composite keys.** The identifier is read from the JPA metamodel (`EntityIdentifierPaths`), not
  assumed to be `id`:
  - a single identifier orders by its mapped attribute, for example `e.Id` for `FaxConfig`;
  - an `@EmbeddedId` orders by each of its components, for example `e.id.providerNo, e.id.siteId`;
  - an `@IdClass` orders by each `@Id` attribute. An `@Id` that is an association (derived identity,
    such as `EReferAttachmentData.eReferAttachment`) orders by the target's identifier,
    `e.eReferAttachment.id`, which Hibernate reads from the foreign-key column without a join.

  Components are taken in attribute-name order, because the metamodel reports them as an unordered
  set and the query must be the same on every start.
- **Domain order.** A DAO whose callers expect a business order overrides
  `getFindAllDomainOrder(alias)` and returns order items such as `alias + ".name ASC"`. The identifier
  is always appended after it as the final tie-breaker, so an override cannot reintroduce ambiguous
  ties. No DAO currently overrides it; DAOs with their own ordered queries are unaffected.
- **Limits.** Unchanged. `limit` defaults to, and may not exceed, `getMaxSelectSize()`
  (`MAX_LIST_RETURN_SIZE`, 5000, unless a DAO lowers it). An oversized limit now throws
  `MaxSelectLimitExceededException` before any query is built.
- **Reuse.** `getIdentifierOrder(alias)` returns the identifier order items, so a DAO's own paged
  query can use the same tie-breaker.

## Concurrent changes

`ORDER BY` fixes the order of rows, not the set of rows. It is not a snapshot. When each page is
read in its own transaction:

| Change between page reads | Effect on offset paging |
|---|---|
| Row inserted with a higher identifier than any read so far (normal for generated ids) | Appears on a later page; nothing is skipped or repeated |
| Row inserted with a lower identifier than the current offset | Every later row shifts forward; the next page repeats the previous page's last row |
| Row deleted from an earlier page | Every later row shifts back; the next page skips one row |
| Sort column updated (domain order only) | The row can move to a page already read, or one not yet read |

A caller that must visit every row exactly once while other users write should either:

1. read all pages in **one transaction**. Under MariaDB's default `REPEATABLE READ` isolation every
   plain `SELECT` in the transaction reads the same consistent snapshot; or
2. page by **key** instead of by offset, asking for the rows whose identifier is greater than the
   last one seen (`WHERE e.id > :last ORDER BY e.id`). That neither skips nor repeats rows that
   exist for the whole traversal, and it does not slow down on later pages.

The current offset-paging callers (`HRMFixMissingReportHelper`, the pharmacy REST list, the eForm
report tool and demographic-set managers) page tables whose rows are added with increasing
generated identifiers, which the identifier order already handles.

## Tests

| Test | What it proves |
|---|---|
| `AbstractDaoImplFindAllUnitTest` | Exact JPQL, domain order before the tie-breaker, limit refused before any query, offset rules, one metamodel lookup per DAO, identifier expansion for embedded ids, id classes, associations, and cycle and unmapped-class errors |
| `AbstractDaoFindAllOrderingIntegrationTest` | Paged traversal on synthetic tables whose covering index defeats unordered reads; composite `@EmbeddedId` and `@IdClass`-with-association paging; the ordered query compiles for every mapped `AbstractModel` entity and runs against every table the test schema has |

The integration test runs on H2 in the normal suite and on physical MariaDB in the
`mariadb-dao-tests` CI job, where `scripts/ci/verify-mariadb-dao-reports.py` requires all six of its
cases to pass. Without the fix, the two synthetic-table cases fail on MariaDB with rows returned in
index order. See [Hibernate 7.4 and MariaDB regression checks](../test/hibernate-mariadb-regressions.md)
for running the MariaDB lane locally.
