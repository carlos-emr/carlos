## 2026-04-27 - JPQL Injection via String Concatenation Fixed
**Vulnerability:** Found a critical JPQL injection vulnerability in `ConsultationRequestDaoImpl.getConsults(...)` where variables like `team` were directly concatenated into the query string (`sql.append("and cr.sendTo = '" + team + "' ");`).
**Learning:** Legacy queries using `StringBuilder` often still contain raw string concatenation which is vulnerable to injection if user input reaches it.
**Prevention:** Always use parameterized/named parameters (e.g. `:team`) and bind them securely using `query.setParameter("team", team)`. Never append user input directly into JPA queries.
