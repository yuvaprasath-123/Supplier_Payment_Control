# Business Configuration service

Java 21 / Spring Boot 4.1.1; default port 8086. Developer 2 owns D2_CONFIGURATION's six deployed tables. Installed Maven 3.9.16 is the supported build path; the Maven Wrapper remains unresolved. No automatic DDL, migration, SQL initialization or seeding.

The current [integration handoff](../docs/developer2-handoff/README.md) is the consolidated source for setup, authentication, examples, test commands, recovery and D1/D3 contracts. It supersedes older per-milestone “mechanics only/policy-blocked” and “populated JDBC unverified” statements. Repository SQL remains review-only and retains historical values/stops.

## Implemented API

All public routes require a verified user JWT with unprefixed FINANCE_MANAGER, an approved prototype policy rather than an original scope-document requirement.

| Method | Route |
|---|---|
| GET, POST | /api/v1/configuration/versions |
| GET | /api/v1/configuration/versions/{versionId} |
| GET / PUT item | /api/v1/configuration/versions/{versionId}/risk-rules[/{ruleCode}] |
| GET / PUT item | /api/v1/configuration/versions/{versionId}/risk-bands[/{bandCode}] |
| GET / PUT item | /api/v1/configuration/versions/{versionId}/approval-thresholds[/{approvalLevel}] |
| GET / PUT item | /api/v1/configuration/versions/{versionId}/approval-slas[/{approvalLevel}] |
| GET | /api/v1/configuration/versions/{versionId}/publication-readiness |
| POST | /api/v1/configuration/versions/{versionId}/publish |
| POST | /api/v1/configuration/versions/{versionId}/activate |
| GET | /api/v1/configuration/active |
| GET (service-only) | /internal/v1/configuration/active |
| GET (service-only) | /internal/v1/configuration/versions/{versionId} |

Square brackets in this summary indicate an item suffix only for PUT; they are not literal route characters. Exact paths, schemas/statuses and receiver permissions are in the [OpenAPI](../docs/developer2-handoff/openapi/business-configuration.openapi.json), with [validated examples](../docs/developer2-handoff/examples/README.md). No delete/edit-version-metadata API is implemented.

DRAFT writes lock/check the parent, preserve child identity and atomically increment rowVersion. Expected revisions are required; output IDs/revisions are strings. Money requests are exact plain decimal strings; responses use two decimals. Rule parameter JSON retains exact numeric values. Drafts may remain incomplete; real readiness validates all seven typed contracts, contiguous bands/amount tiers, fixed authority mapping and supported SLA semantics before publication.

Publication derives the verified actor, records UTC time and hashes complete configuration-content-v2 including approval-policy-v1. Repeat publish is 409 VERSION_ALREADY_PUBLISHED; no automatic activation. Published application content is immutable. Activation requires an explicit expectedPointerRowVersion (null only when no pointer exists), verifies published content and serializes pointer changes. Repeating activation of the current target with the matching pointer revision preserves metadata. Full snapshots expose versionRowVersion separately from pointerRowVersion; consumers must not confuse them.

Legacy V1 reads preserve historical meanings; new Risk submissions require supported V2 semantics. Unknown/unresolved policy choices still fail closed. Upstream API unavailability no longer blocks publication; runtime evidence completeness/certification remains mandatory. No bypass/approval flag exists.

## Security, setup and verification

CONFIG_DB_USERNAME/PASSWORD and CONFIG_JWT_SECRET are external. Separate internal RS256 trust is opt-in through CONFIG_INTERNAL_*; disabled internal access returns 404 while manager APIs remain usable. User/service tokens cannot substitute for each other. Full variables, hidden input, current MFA flow and operator commands are in [operations](../docs/developer2-handoff/operations.md).

Retained latest evidence: 2,117 offline baseline and 173 affected passes including 10 new safety cases; overlapping counts are not added. The user-run configured-client Oracle suite passed 7 tests,zero failures/errors/skips, using real readiness-approved publication/activation and all three production clients. All worker groups terminated and both schemas had zero fixture parents. Earlier real-publication suite passed 3 tests and activation mechanics suite 6 tests at their respective checkpoints. These used isolated fixtures/ephemeral service issuers and do not verify genuine D1/D3 provider/issuer/TLS interoperability.

See [completion checklist](../docs/developer2-handoff/completion-checklist.md) and [configured-client verification instructions](../risk-assessment-service/docs/configured-clients-oracle-verification.md). Detailed historical counts describe earlier selections, not additive coverage or reruns performed for this documentation. Actual Insomnia desktop import is not tested.
