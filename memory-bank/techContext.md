# Tech Context

## Technology Stack

- **Runtime:** Java 21
- **Framework:** Spring Boot 4.0.6 (as per configuration)
- **Database:** Oracle 23c Free (PDB: `FREEPDB1`)
- **JDBC Driver:** `ojdbc11` (com.oracle.database.jdbc)
- **Hibernate Dialect:** `org.hibernate.dialect.OracleDialect`
- **ORM/Data:** JPA/Hibernate (Spring Data JPA)
- **Security:** Spring Security, JWT (RBAC compliant)
- **Build Tool:** Maven
- **Misc:** Lombok, MapStruct (with lombok-mapstruct-binding), SpringDoc OpenAPI

## Development Setup

- Active Profiles: `dev` (Local development), `test`, `prod`
- Port: 8080

## Technical Constraints

- Minimum 80% test coverage for service layer.
- Transactional integrity for all financial/banking operations.
- Modular monolith or layered architecture.
