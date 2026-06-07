Employee Management System (Phase 1)

Phase 1: Authentication and Employee Management (no frameworks)

Run instructions:
- Create MySQL database using `db/schema.sql`.
- Update `src/db.properties` with your DB credentials.
- Compile Java sources and run `com.hrms.server.AppServer`.
- Open `http://localhost:8080/static/index.html` in browser.

Notes:
- Uses built-in Java HTTP server (no servlets, no frameworks).
- JDBC with PreparedStatements is used for DB access.
