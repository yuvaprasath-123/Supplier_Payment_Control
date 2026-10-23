# Internal read authentication contract

This library supplies verification only. It has no application entry point, token issuance endpoint, network key lookup, credentials or default service grants. User authentication remains in each host's existing public security chain. `/internal/**` has a separate stateless chain and cannot create, edit, publish or activate configuration, submit assessments, or expose raw input evidence.

## Opt-in receiver configuration

Both hosts default to internal access disabled. Disabled internal requests return sanitized HTTP 404 `INTERNAL_ACCESS_DISABLED`; manager routes continue using their existing external user JWT configuration. No internal credentials are required in disabled mode. Enabling with missing/invalid issuer, audience, keys or grants fails startup with a sanitized configuration error; there is no user-key fallback.

| Property | Business Configuration environment | Risk Assessment environment | Required when enabled |
|---|---|---|---|
| internal-access.enabled | CONFIG_INTERNAL_ENABLED | RISK_INTERNAL_ENABLED | Explicit true |
| internal-access.issuer | CONFIG_INTERNAL_ISSUER | RISK_INTERNAL_ISSUER | Exact trusted issuer |
| internal-access.audience | CONFIG_INTERNAL_AUDIENCE | RISK_INTERNAL_AUDIENCE | Exact receiver-specific audience |
| internal-access.public-keys | CONFIG_INTERNAL_PUBLIC_KEYS | RISK_INTERNAL_PUBLIC_KEYS | JSON object mapping allowed kid to Base64 X.509 SubjectPublicKeyInfo DER RSA public key; minimum 2048 bits |
| internal-access.service-permissions | CONFIG_INTERNAL_SERVICE_PERMISSIONS | RISK_INTERNAL_SERVICE_PERMISSIONS | JSON object mapping explicitly allowed service ID to nonempty permission array |

External Spring configuration may supply the same properties. Duplicate JSON configuration keys, unknown permissions, duplicate/empty grants and malformed service IDs are rejected. Service IDs match `[a-z][a-z0-9-]{0,63}`; key IDs match `[A-Za-z0-9._-]{1,64}`. Neither keys nor identities/grants have embedded defaults. Keep different audiences for the two receivers, for example their exact application names. These examples do not configure a running service.

## Signed service token

Require **RS256**, a known `kid`, header `typ=service+jwt`, `iss`, a single exact `aud`, nonblank allowlisted `sub`, future `exp`, claim `principalType=SERVICE`, and a nonempty JSON string array `permissions`. Standard signature/expiry/nbf verification applies with no configured clock skew. Host clocks must be synchronized. User `roles` and `userId` claims are forbidden in service tokens. Token permissions must be a subset of that subject's locally configured grants; a token may request fewer grants, never more. Identities and permissions are case-sensitive. The resulting principal has only `SERVICE_PERMISSION_*` authorities and retains no bearer credentials.

JWT JSON field names above describe the contract; no usable token or signing key is provided here. A valid service token missing the route permission gets 403. Bad signature/key ID, issuer, audience, identity, expiry, required claims or unauthorized token grants get sanitized 401 with `WWW-Authenticate: Bearer ... error="invalid_token"`. Missing/unsupported credentials get 401 with a Bearer challenge without a token-error attribute. Bearer scheme matching is case-insensitive, with the token unchanged. Service credentials are never interpreted as FINANCE_MANAGER users; user credentials cannot authenticate internal routes. Existing manager JSON/challenge behavior is retained.

## Routes and permissions

| Receiver / GET route | Permission | Response |
|---|---|---|
| Configuration `/internal/v1/configuration/active` | CONFIGURATION_ACTIVE_READ | Existing verified active snapshot, including pointer revision |
| Configuration `/internal/v1/configuration/versions/{versionId}` | CONFIGURATION_VERSION_READ | Explicit pinned published snapshot; never selects current/newer configuration |
| Risk `/internal/v1/risk-assessments/{assessmentId}` | RISK_ASSESSMENT_READ | Existing summary projection, COMPLETED only |
| Risk `/internal/v1/risk-assessments/{assessmentId}/factors` | RISK_FACTORS_READ | Existing ordered factor projection, COMPLETED only |
| Risk `/internal/v1/risk-assessments/{assessmentId}/configuration-snapshot` | RISK_CONFIGURATION_READ | That completed assessment's frozen engine configuration |
| Risk POST `/internal/v1/risk-assessments` | RISK_ASSESSMENT_SUBMIT | Separate opt-in orchestration; receiver-owned namespace binding also required |
| Risk GET `/internal/v1/risk-assessment-submissions/status` | RISK_ASSESSMENT_SUBMIT | Source/key lookup, same namespace ownership; no work triggered |

No other internal methods/routes are authorized. Ordinary manager routes retain their existing access policy and DTOs. String IDs/revisions, UTC timestamps and exact numeric/money representations are preserved. Factors expose outcomes, weights, contributions and explanations, not unrestricted matched evidence, observations, diagnostics, lease capabilities or completion proofs.

Both configuration reads run read-only SERIALIZABLE transactions and reuse the complete publication hash verifier. A pinned response contains `versionId`, `versionRowVersion`, `publishedAt`, `publishedByUserId`, `contentHash`, `hashFormat` and the complete canonical `configuration` object (rules, bands, thresholds and SLAs). An active response additionally contains the existing pointer activation fields. No active pointer is 404 `NO_ACTIVE_CONFIGURATION`; missing versions are 404 `VERSION_NOT_FOUND`; drafts are 409 `VERSION_NOT_PUBLISHED`; integrity/database failures are sanitized 503. Publication readiness blockers remain unchanged. Controlled PUBLISHED fixtures do not imply genuine publication approval.

Risk reads reuse the existing coherent Oracle read-only snapshot and persisted integrity checks, without rescoring or writes. Non-completed states return 409 `ASSESSMENT_NOT_COMPLETED` on every internal risk route; public manager reads retain their existing non-completed responses. Invalid IDs, missing records and corrupt/unavailable data retain existing sanitized 400/404/503 errors. The risk engine configuration hash is distinct from the publication content hash; mapping between full published configuration and engine snapshots remains a later adapter contract.

## Provisioning and rotation for later integration

Provision a dedicated internal RSA key pair through the team's approved secret-management process. The trusted issuer alone holds private signing material; receivers hold only externally provisioned public keys. Never reuse security-service's user HMAC key, give a consumer the issuer private key, embed credentials or paste tokens/keys into chat. This milestone does not make security-service an internal token issuer and does not change Developer 1/3. Issuer ownership, actual service identities, credential distribution and transport protection must be agreed before live interoperability. Use TLS on non-loopback deployment paths; this code does not provision certificates or a deployment proxy.

Recommended least-privilege mapping to agree with integration owners (examples only, **no default grants**):

| Proposed consumer identity | Configuration grants | Risk grants |
|---|---|---|
| risk-assessment-service | CONFIGURATION_ACTIVE_READ, CONFIGURATION_VERSION_READ | None |
| approval-service (confirm Developer 3's exact identity) | CONFIGURATION_VERSION_READ | RISK_ASSESSMENT_READ, RISK_FACTORS_READ, RISK_CONFIGURATION_READ |

Deploy a new public key under a new `kid` to all intended receivers before changing the issuer's signing key. During a bounded overlap, receivers may allow both known keys. Then issue new short-lived tokens with the new `kid`, wait for old tokens to expire plus operational margin, and remove the old key. Receivers reload configuration by restart/rolling deployment; no hot-reload or revocation endpoint exists. Remove a compromised key/service grant promptly through external configuration; disabling internal access is independent of manager authentication. Token lifetime, rotation window, per-consumer credential provisioning and issuer operational controls remain integration responsibilities.

## Offline verification

Use Java 21, Boot 4.1.1 and installed Maven 3.9.16 from the repository reactor. Building either host with `-pl <service> -am` includes this library and the existing risk contracts. Standalone service Maven commands require shared artifacts installed locally first. Tests generate ephemeral RSA/HMAC material only in the test JVM and use mocked JDBC/HTTP servlet contexts; they do not establish live issuer or Oracle integration. No live test or service is launched by this module.

Verification: **1,503 offline reactor tests passed, zero failures/errors/skips**, including 105 new security/internal-read tests. Every live Oracle test class was explicitly excluded. Genuine Developer 1/3 issuer interoperability and deployed key rotation remain unverified; user-run test-issuer HTTP-to-Oracle verification is recorded below.

## Prepared opt-in HTTP-to-Oracle verification

`OracleInternalConfigurationHttpTest` and `OracleInternalRiskHttpTest` are disabled by default. The tests start real D2 application/security/read paths on temporary loopback ports and connect to the fixed local FREEPDB1 schemas with external passwords. They generate ephemeral test-only RSA keys and an ephemeral user HMAC key in memory. **This proves only test-issuer mechanics if it passes; it is not genuine Developer 1/3 issuer, MFA or credential-provisioning interoperability.** No production issuer, key files or issuance endpoint is added. Verification helpers are attached only as a test-scope artifact; production executable jars do not depend on that artifact.

Do not run against an existing active configuration: the configuration suite requires an isolated environment, stopped configuration writers and no current pointer. Setup holds the singleton-table lock, refuses any pointer and commits two uniquely labelled `D2_CFG_ACTIVE_<UUID>_A/B` controlled PUBLISHED versions, then activates its own first version. It reuses the reviewed activation fixture guards. These intentionally empty configuration sections verify read/security mechanics, not readiness-approved publication or populated rule/band/threshold/SLA Oracle mapping. Real readiness blockers remain untouched. Do not delete a real pointer to satisfy the test guard.

The risk suite reuses reviewed `D2_RISK_FINAL_<UUID>` fixtures with only keys `success` (completed through acceptance/freeze/finalization) and `insufficient` (accepted only). Fixture writes commit so HTTP connections can read them. These namespace keys are not business submission identities. No unrelated records, identities/sequences, schema objects, teammate services, Kafka or Stripe processing are changed or started.

Each receiver has six cases covering all internal reads, allowlisted and denied identities, route/grant separation, wrong audience/key/issuer/expiry/missing claims, overlapping keys/unknown kid, user/service cross-use, and disabled internal mode with a working public test-manager read. Risk also rejects every non-completed read and checks factor field allowlists. Column/state snapshots before and after requests detect writes. Every test token and key remains in test JVM memory; request/response bodies and credentials are withheld from failure messages.

After requests, all temporary receivers and HTTP clients are closed before cleanup. Any uncertain request or failed shutdown defers cleanup and fails verification. Configuration cleanup verifies exact fixture labels, synthetic markers/actors, immutable parent/hash, empty children and ownership of any pointer before deleting only its pointer/versions. Risk cleanup uses the existing guarded snapshot/evidence/factor verifier and the two-key allowlist. Successful cleanup verifies zero parents. There is no wildcard fallback or identity reset.

From the repository root, when ready to perform those committed fixture writes and temporary server starts yourself:

```powershell
Set-Location 'C:\risk_assessment\Risk_Assessment'
.\internal-service-security\scripts\verify-internal-http-oracle.ps1 -Mode Verify -ConfirmConfigurationIsolated
```

The runner prompts for D2_CONFIGURATION and D2_RISK passwords using hidden input, sets `D2_VERIFY_INTERNAL_HTTP_ORACLE_WRITES=true` and `CONFIG_ORACLE_INTERNAL_HTTP_ISOLATED=true`, and explicitly selects only the two live test classes through installed Maven/reactor dependencies. It temporarily removes real user-signing variables, requires no signing key/token/OTP from you, disposes hidden-input buffers and restores all touched process environment variables in `finally`. It does not change User/Machine settings or write secrets to files. Inspect both Surefire reports; the user-run result for the 12 live cases is recorded below. The agent did not execute them.

If interrupted, first stop outstanding test requests/receivers and configuration writers. Retain the exact printed namespace. These recovery modes validate the canonical namespace before any connection and select only the guarded cleanup test; they never start an HTTP receiver:

```powershell
# Configuration fixture labels D2_CFG_ACTIVE_<UUID>_A/B: supply the common namespace without _A/B.
.\internal-service-security\scripts\verify-internal-http-oracle.ps1 -Mode ConfigurationCleanup -Namespace (Read-Host 'Exact printed D2_CFG_ACTIVE namespace') -ConfirmStopped
# Risk uses the existing two-key HTTP fixture recovery verifier.
.\internal-service-security\scripts\verify-internal-http-oracle.ps1 -Mode RiskCleanup -Namespace (Read-Host 'Exact printed D2_RISK_FINAL namespace') -ConfirmStopped
```

Recovery refuses unexpected content or any unrelated active pointer. Inspect refused data before further action; never broaden deletion. The SQL review stops remain unchanged. Broad progress documentation is deferred.

Review follow-up: the unchanged implementation reuses the **1,503-test** regression result. **24 additional offline safety tests passed** (16 shared harness, six configuration guard, two risk guard), zero failures/errors/skips. The prepared live suites compiled but were not executed; no receiver was started during these offline checks.

Checkpoint verification (user-reported live run): **12 internal HTTP-to-Oracle tests passed, zero failures/errors/skips**. Both receiver/client groups terminated, and guarded cleanup left zero fixture parents. The unchanged implementation retains **1,503 passing regression tests plus 24 additional passing offline safety tests**. Live tests used an ephemeral test issuer; genuine Developer 1/3 issuer interoperability remains pending. Controlled PUBLISHED fixtures do not establish readiness-approved publication. Broad documentation remains deferred.
