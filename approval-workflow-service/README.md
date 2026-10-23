# Approval workflow service

Runs on port 8089. Implements the blueprint's 13 route/task APIs with persisted routes, assignments, decisions, SLA escalation history, optional backup mappings and Finance Manager override.

Read [Developer 3 guide](../DEVELOPER3_README.md) for the API table, payloads, Oracle environment settings and Insomnia collection.

Task creation and decisions need a Bearer token and Developer 1 security-service on port 8081. Security-service verifies active user status, approval role and authority. No client-supplied manager role is trusted.

Developer 2's risk band, amount approval level, configuration version and SLA must be supplied by the coordinator. SLA escalation is invoked through the API; automatic scheduling and next-approver selection remain team integration work. The 60-minute fallback is configurable prototype data.

Legacy /approval-workflows URLs remain adapters for newly created records. Old APR_WORKFLOWS demo rows are preserved but are not migrated.

Run mvn test for routing, persisted decisions, self-approval/assignment guards, override reasons, reassignment and SLA checks.
