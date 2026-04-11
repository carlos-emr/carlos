## 2026-04-11 - [SQL Injection Fix] QueueDaoImpl.getQueueid HQL String Concatenation
**Vulnerability:** Found a SQL injection vulnerability in `src/main/java/io/github/carlos_emr/carlos/commn/dao/QueueDaoImpl.java` where `getQueueid` used HQL string concatenation (`"select q from Queue q where q.name=" + name`) rather than parameterized queries.
**Learning:** This repo has custom HQL string query constructions in some legacy DAO objects that are susceptible to SQL injection and break H2 compatibility, requiring careful auditing to catch.
**Prevention:** Always use parameterized queries (e.g. `q.name=:name` and `query.setParameter("name", name)`) for HQL/JPA queries rather than raw string concatenation.
