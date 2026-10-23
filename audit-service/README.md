# Audit service

Runs on port 8088. Provides the blueprint audit ingestion, payment timeline, decision history, risk history, frozen risk snapshot and combined event/user/date filters.

Read [Developer 3 guide](../DEVELOPER3_README.md) for every API and the Insomnia collection.

Existing database: run db/oracle-upgrade-existing-audit.sql once before restarting. New database: use db/oracle-audit-schema.sql instead. AUDIT_DDL_AUTO defaults to validate. No passwords are stored in this project.

The snapshot needs a completed risk event with a frozen configuration and score breakdown. It reports missing/incomplete historical data instead of reconstructing from current rules.

Run mvn test for database-backed checks using H2; Oracle smoke tests and team event delivery remain integration work.
