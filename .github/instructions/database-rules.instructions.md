---
description: "SQL security rules for database migration scripts"
applyTo: "database/**/*.sql"
---

# Database Migration Rules

## Migration File Naming

Format: `update-YYYY-MM-DD-description.sql`

## Audit Trail Requirement

Every new table MUST include:
```sql
lastUpdateUser VARCHAR(100) NOT NULL,
lastUpdateDate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
```

## Security

- NEVER include real patient data or PHI in migration scripts
- Use parameterized queries in stored procedures
- Quote reserved words with backticks

## Reserved Words

Column names like `value`, `key`, `order`, `group`, `status` must be backtick-quoted:
```sql
`value` VARCHAR(255),
`key` VARCHAR(255),
`order` INT
```
