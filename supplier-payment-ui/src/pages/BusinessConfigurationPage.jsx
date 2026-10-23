
import {
  useEffect,
  useRef,
  useState
} from "react";

import { useAuth } from "../auth/AuthContext";

import {
  getConfigurationVersions,
  getConfigurationVersionDetails,
  getActiveConfiguration,
  getPublicationReadiness,
  getRiskRules,
  getRiskBands,
  getApprovalThresholds,
  getApprovalSlas,
  createConfigurationVersion,
  updateDraftRiskRule
} from "../api/businessConfigurationApi";

const RULE_TEMPLATES = [
  {
    ruleCode: "STRUCTURING",
    displayName: "Structuring",
    enabled: true,
    weight: 25,
    parameterSchemaVersion: "risk-policy-v1",
    evaluationOrder: 1,
    parameters: {
      approvalBoundaryLevels: [2, 3],
      historyEligibility: "COMPLETED_PAYMENTS",
      minimumPayments: 3,
      windowSeconds: 604800
    }
  },
  {
    ruleCode: "UNUSUAL_FREQUENCY",
    displayName: "Unusual Frequency",
    enabled: true,
    weight: 15,
    parameterSchemaVersion: "risk-policy-v1",
    evaluationOrder: 2,
    parameters: {
      historyEligibility: "COMPLETED_PAYMENTS",
      maximumSubmissions: 5,
      windowSeconds: 604800
    }
  },
  {
    ruleCode: "UNUSUAL_AMOUNT",
    displayName: "Unusual Amount",
    enabled: true,
    weight: 15,
    parameterSchemaVersion: "risk-policy-v1",
    evaluationOrder: 3,
    parameters: {
      meanMultiplier: "2.00",
      minimumObservations: 3,
      windowSeconds: 2592000,
      zeroBaselinePolicy: "INSUFFICIENT_HISTORY"
    }
  },
  {
    ruleCode: "RECENTLY_REACTIVATED_SUPPLIER",
    displayName: "Recently Reactivated Supplier",
    enabled: true,
    weight: 15,
    parameterSchemaVersion: "risk-policy-v1",
    evaluationOrder: 4,
    parameters: {
      windowSeconds: 2592000
    }
  },
  {
    ruleCode: "PREVIOUS_RISK_FLAGS",
    displayName: "Previous Risk Flags",
    enabled: true,
    weight: 10,
    parameterSchemaVersion: "risk-policy-v1",
    evaluationOrder: 5,
    parameters: {
      minimumFlaggedPayments: 1,
      qualifyingRuleCodes: [
        "STRUCTURING",
        "UNUSUAL_FREQUENCY",
        "UNUSUAL_AMOUNT",
        "RECENTLY_REACTIVATED_SUPPLIER",
        "NEW_SUPPLIER",
        "NEAR_DUPLICATE_INVOICE"
      ],
      windowSeconds: 7776000
    }
  },
  {
    ruleCode: "NEW_SUPPLIER",
    displayName: "New Supplier",
    enabled: true,
    weight: 10,
    parameterSchemaVersion: "risk-policy-v1",
    evaluationOrder: 6,
    parameters: {
      windowSeconds: 2592000
    }
  },
  {
    ruleCode: "NEAR_DUPLICATE_INVOICE",
    displayName: "Near Duplicate Invoice",
    enabled: true,
    weight: 10,
    parameterSchemaVersion: "risk-policy-v1",
    evaluationOrder: 7,
    parameters: {
      historyEligibility: "COMPLETED_PAYMENTS",
      invoiceNumberNormalization: "TRIM_UPPERCASE",
      windowSeconds: 2592000
    }
  }
];

const EMPTY_RULE_FORM = {
  displayName: "",
  enabled: true,
  weight: "0",
  parameterSchemaVersion: "risk-policy-v1",
  parametersText: "{}",
  evaluationOrder: "1"
};

function displayValue(value) {
  return value === null ||
    value === undefined ||
    value === ""
    ? "-"
    : String(value);
}

function formatApprovalLevel(level) {
  return level == null
    ? "-"
    : `L${String(level).replace(/^L/i, "")}`;
}

function formatAmount(amount) {
  if (amount == null) return "No upper limit";

  const value = Number(amount);

  return Number.isFinite(value)
    ? value.toLocaleString("en-IN", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
      })
    : String(amount);
}

function formatDateTime(value) {
  if (!value) return "-";

  const date = new Date(value);

  return Number.isNaN(date.getTime())
    ? String(value)
    : date.toLocaleString();
}

function formatDuration(seconds) {
  if (seconds == null) return "-";

  const value = Number(seconds);

  if (!Number.isFinite(value)) return String(seconds);
  if (value === 0) return "0 seconds";

  const days = Math.floor(value / 86400);
  const hours = Math.floor((value % 86400) / 3600);
  const minutes = Math.floor((value % 3600) / 60);
  const remaining = value % 60;

  return [
    days ? `${days} day${days === 1 ? "" : "s"}` : "",
    hours ? `${hours} hour${hours === 1 ? "" : "s"}` : "",
    minutes
      ? `${minutes} minute${minutes === 1 ? "" : "s"}`
      : "",
    remaining
      ? `${remaining} second${remaining === 1 ? "" : "s"}`
      : ""
  ].filter(Boolean).join(" ");
}

function errorMessage(error) {
  if (error?.status === 409) {
    return "Configuration has changed. Latest data has been reloaded. Review the rule before saving again.";
  }

  return error?.message || "Request failed.";
}

export default function BusinessConfigurationPage() {

  const {
    token,
    hasRole
  } = useAuth();

  const canManage =
    hasRole("FINANCE_MANAGER");

  const [versions, setVersions] = useState([]);
  const [selectedVersion, setSelectedVersion] =
    useState("");

  const [activeConfiguration, setActiveConfiguration] =
    useState(null);

  const [versionDetails, setVersionDetails] =
    useState(null);

  const [rules, setRules] = useState([]);
  const [bands, setBands] = useState([]);
  const [thresholds, setThresholds] = useState([]);
  const [slas, setSlas] = useState([]);

  const [loadingVersions, setLoadingVersions] =
    useState(true);

  const [loadingActive, setLoadingActive] =
    useState(true);

  const [loadingConfiguration, setLoadingConfiguration] =
    useState(false);

  const [versionsError, setVersionsError] =
    useState("");

  const [activeError, setActiveError] =
    useState("");

  const [configurationError, setConfigurationError] =
    useState("");

  const [versionLabel, setVersionLabel] =
    useState("");

  const [description, setDescription] =
    useState("");

  const [isDemonstration, setIsDemonstration] =
    useState(true);

  const [scorePolicyCode, setScorePolicyCode] =
    useState("CAP_AT_MAX");

  const [maxFinalScore, setMaxFinalScore] =
    useState("100");

  const [creatingDraft, setCreatingDraft] =
    useState(false);

  const [createError, setCreateError] =
    useState("");

  const [createSuccess, setCreateSuccess] =
    useState("");

  const [readiness, setReadiness] =
    useState(null);

  const [readinessLoading, setReadinessLoading] =
    useState(false);

  const [readinessError, setReadinessError] =
    useState("");

  const readinessRequestId = useRef(0);

  const [selectedRuleCode, setSelectedRuleCode] =
    useState("STRUCTURING");

  const [ruleForm, setRuleForm] =
    useState(EMPTY_RULE_FORM);

  const [savingRule, setSavingRule] =
    useState(false);

  const [ruleError, setRuleError] =
    useState("");

  const [ruleSuccess, setRuleSuccess] =
    useState("");

  const [refreshCounter, setRefreshCounter] =
    useState(0);

  const latestVersion = useRef(selectedVersion);
  latestVersion.current = selectedVersion;

  const latestToken = useRef(token);
  latestToken.current = token;

  const selectedIsDraft =
    versionDetails?.publicationState === "DRAFT" &&
    String(versionDetails.versionId) ===
      String(selectedVersion);

  const canEditDraft =
    canManage &&
    selectedIsDraft &&
    !loadingConfiguration;

  const readinessIssues =
    Array.isArray(readiness?.issues)
      ? readiness.issues
      : [];

  const selectedRule =
    rules.find(
      (rule) =>
        rule.ruleCode === selectedRuleCode
    );

  // Load configuration versions

  useEffect(() => {
    let active = true;

    async function loadVersions() {
      setLoadingVersions(true);
      setVersionsError("");

      try {
        const data =
          await getConfigurationVersions(token);

        if (!active) return;

        const list =
          Array.isArray(data) ? data : [];

        setVersions(list);

        setSelectedVersion((current) =>
          list.some(
            (version) =>
              String(version.versionId) ===
              String(current)
          )
            ? current
            : list.length > 0
              ? String(list[0].versionId)
              : ""
        );

      } catch (error) {
        if (active) {
          setVersionsError(errorMessage(error));
          setVersions([]);
          setSelectedVersion("");
        }
      } finally {
        if (active) setLoadingVersions(false);
      }
    }

    if (token && canManage) {
      loadVersions();
    } else {
      setVersions([]);
      setSelectedVersion("");
      setLoadingVersions(false);
    }

    return () => {
      active = false;
    };
  }, [token, canManage]);

  // Load currently active configuration

  useEffect(() => {
    let active = true;

    async function loadActive() {
      setLoadingActive(true);
      setActiveError("");

      try {
        const result =
          await getActiveConfiguration(token);

        if (active) setActiveConfiguration(result);

      } catch (error) {
        if (active) {
          setActiveError(errorMessage(error));
          setActiveConfiguration(null);
        }
      } finally {
        if (active) setLoadingActive(false);
      }
    }

    if (token && canManage) {
      loadActive();
    } else {
      setActiveConfiguration(null);
      setLoadingActive(false);
    }

    return () => {
      active = false;
    };
  }, [token, canManage]);

  // Load selected configuration and tables

  useEffect(() => {
    let active = true;

    if (!token || !selectedVersion || !canManage) {
      setVersionDetails(null);
      setRules([]);
      setBands([]);
      setThresholds([]);
      setSlas([]);
      setLoadingConfiguration(false);
      return;
    }

    async function loadConfiguration() {
      setLoadingConfiguration(true);
      setConfigurationError("");

      setVersionDetails(null);
      setRules([]);
      setBands([]);
      setThresholds([]);
      setSlas([]);

      const results = await Promise.allSettled([
        getConfigurationVersionDetails(token, selectedVersion),
        getRiskRules(token, selectedVersion),
        getRiskBands(token, selectedVersion),
        getApprovalThresholds(token, selectedVersion),
        getApprovalSlas(token, selectedVersion)
      ]);

      if (!active) return;

      const [
        detailsResult,
        rulesResult,
        bandsResult,
        thresholdsResult,
        slasResult
      ] = results;

      const errors = [];

      const assign = (result, setter, label, isList) => {
        if (result.status === "fulfilled") {
          setter(
            isList
              ? Array.isArray(result.value)
                ? result.value
                : []
              : result.value
          );
        } else {
          errors.push(
            `${label}: ${errorMessage(result.reason)}`
          );
        }
      };

      assign(
        detailsResult,
        setVersionDetails,
        "Version Details",
        false
      );

      assign(
        rulesResult,
        setRules,
        "Risk Rules",
        true
      );

      assign(
        bandsResult,
        setBands,
        "Risk Bands",
        true
      );

      assign(
        thresholdsResult,
        setThresholds,
        "Approval Thresholds",
        true
      );

      assign(
        slasResult,
        setSlas,
        "Approval SLAs",
        true
      );

      setConfigurationError(errors.join(" | "));
      setLoadingConfiguration(false);
    }

    loadConfiguration();

    return () => {
      active = false;
    };

  }, [token, selectedVersion, refreshCounter, canManage]);

  // Clear readiness whenever the version changes

  useEffect(() => {
    readinessRequestId.current += 1;

    setReadiness(null);
    setReadinessError("");
    setReadinessLoading(false);

    return () => {
      readinessRequestId.current += 1;
    };

  }, [token, selectedVersion]);

  // Load the selected rule into the editor

  useEffect(() => {
    const template =
      RULE_TEMPLATES.find(
        (rule) => rule.ruleCode === selectedRuleCode
      );

    const existing =
      rules.find(
        (rule) => rule.ruleCode === selectedRuleCode
      );

    const source = existing || template;

    if (!source) {
      setRuleForm(EMPTY_RULE_FORM);
      return;
    }

    setRuleForm({
      displayName: source.displayName,
      enabled: source.enabled,
      weight: String(source.weight),
      parameterSchemaVersion:
        source.parameterSchemaVersion,
      parametersText:
        JSON.stringify(source.parameters, null, 2),
      evaluationOrder: String(source.evaluationOrder)
    });

  }, [selectedRuleCode, rules, selectedVersion]);

  useEffect(() => {
    setRuleError("");
    setRuleSuccess("");
  }, [selectedRuleCode, selectedVersion]);

  function updateRuleField(name, value) {
    setRuleForm((current) => ({
      ...current,
      [name]: value
    }));
  }

  // API Name: Create Configuration Version
  // Method: POST
  // URL: /api/v1/configuration/versions

  async function handleCreateDraft(event) {
    event.preventDefault();

    if (creatingDraft || !canManage) return;

    setCreateError("");
    setCreateSuccess("");

    const label = versionLabel.trim();

    if (!label || [...label].length > 100) {
      setCreateError("Enter a valid version label.");
      return;
    }

    if ([...description].length > 1000) {
      setCreateError(
        "Description must not exceed 1000 characters."
      );
      return;
    }

    let scoreLimit = null;

    if (scorePolicyCode === "CAP_AT_MAX") {
      const value = Number(maxFinalScore);

      if (
        !/^[0-9]+$/.test(maxFinalScore) ||
        !Number.isSafeInteger(value) ||
        value < 1 ||
        value > 2147483647
      ) {
        setCreateError(
          "Maximum score must be a positive integer."
        );
        return;
      }

      scoreLimit = value;
    }

    setCreatingDraft(true);

    try {
      const created =
        await createConfigurationVersion(token, {
          versionLabel: label,
          description: description.trim(),
          isDemonstration,
          scorePolicyCode,
          maxFinalScore: scoreLimit
        });

      setCreateSuccess(
        `Draft created. Version ID: ${created.versionId}`
      );

      setVersionLabel("");
      setDescription("");
      setIsDemonstration(true);
      setScorePolicyCode("CAP_AT_MAX");
      setMaxFinalScore("100");

      try {
        const updated =
          await getConfigurationVersions(token);

        if (!Array.isArray(updated)) {
          throw new Error("Invalid version list.");
        }

        setVersions(updated);
        setSelectedVersion(String(created.versionId));
        setVersionsError("");

      } catch (error) {
        setVersionsError(
          "Draft created, but list refresh failed: " +
          errorMessage(error)
        );
      }

    } catch (error) {
      setCreateError(errorMessage(error));
    } finally {
      setCreatingDraft(false);
    }
  }

  // API Name: Get Publication Readiness
  // Method: GET
  // URL: /api/v1/configuration/versions/{versionId}/publication-readiness

  async function handleCheckReadiness() {
    if (!token || !selectedVersion || readinessLoading) {
      return;
    }

    const requestId = ++readinessRequestId.current;

    setReadinessLoading(true);
    setReadinessError("");
    setReadiness(null);

    try {
      const result =
        await getPublicationReadiness(token, selectedVersion);

      if (requestId === readinessRequestId.current) {
        setReadiness(result);
      }

    } catch (error) {
      if (requestId === readinessRequestId.current) {
        setReadinessError(errorMessage(error));
      }
    } finally {
      if (requestId === readinessRequestId.current) {
        setReadinessLoading(false);
      }
    }
  }

  // API Name: Update Draft Risk Rule
  // Method: PUT
  // URL: /api/v1/configuration/versions/{versionId}/risk-rules/{ruleCode}

  async function handleSaveRule(event) {
    event.preventDefault();

    if (!canEditDraft || savingRule) return;

    setRuleError("");
    setRuleSuccess("");

    const displayName = ruleForm.displayName.trim();
    const schema = ruleForm.parameterSchemaVersion.trim();

    const weight = Number(ruleForm.weight);
    const order = Number(ruleForm.evaluationOrder);

    if (!displayName || [...displayName].length > 120) {
      setRuleError("Enter a valid display name.");
      return;
    }

    if (!schema || [...schema].length > 32) {
      setRuleError("Enter a valid parameter schema version.");
      return;
    }

    if (
      !/^[0-9]+$/.test(ruleForm.weight) ||
      !Number.isSafeInteger(weight) ||
      weight < 0
    ) {
      setRuleError("Weight must be a nonnegative integer.");
      return;
    }

    if (
      !/^[0-9]+$/.test(ruleForm.evaluationOrder) ||
      !Number.isSafeInteger(order) ||
      order < 1 ||
      order > 99999
    ) {
      setRuleError(
        "Evaluation order must be between 1 and 99999."
      );
      return;
    }

    let parameters;

    try {
      parameters = JSON.parse(ruleForm.parametersText);

      if (
        !parameters ||
        typeof parameters !== "object" ||
        Array.isArray(parameters)
      ) {
        throw new Error();
      }

    } catch {
      setRuleError(
        "Parameters must contain a valid JSON object."
      );
      return;
    }

    const submittedVersion = selectedVersion;
    const submittedToken = token;

    setSavingRule(true);

    try {
      // Retrieve the latest row version before saving.
      // This prevents relying on a previously displayed value.

      const latest =
        await getConfigurationVersionDetails(
          submittedToken,
          submittedVersion
        );

      if (
        String(latest.versionId) !==
          String(submittedVersion) ||
        latest.publicationState !== "DRAFT"
      ) {
        throw new Error(
          "The selected configuration is no longer an editable draft."
        );
      }

      if (
        latest.rowVersion === null ||
        latest.rowVersion === undefined
      ) {
        throw new Error(
          "Unable to retrieve the configuration row version."
        );
      }

      if (
        latestVersion.current !== submittedVersion ||
        latestToken.current !== submittedToken
      ) {
        return;
      }

      const body = {
        displayName,
        enabled: ruleForm.enabled,
        weight,
        parameterSchemaVersion: schema,
        parameters,
        evaluationOrder: order,
        expectedRowVersion: String(latest.rowVersion)
      };

      await updateDraftRiskRule(
        submittedToken,
        submittedVersion,
        selectedRuleCode,
        body
      );

      if (
        latestVersion.current === submittedVersion &&
        latestToken.current === submittedToken
      ) {
        setRuleSuccess(
          `${selectedRuleCode} saved successfully.`
        );
      }

    } catch (error) {
      if (
        latestVersion.current === submittedVersion &&
        latestToken.current === submittedToken
      ) {
        setRuleError(errorMessage(error));
      }

    } finally {
      if (
        latestVersion.current === submittedVersion &&
        latestToken.current === submittedToken
      ) {
        readinessRequestId.current += 1;
        setReadiness(null);
        setReadinessError("");
        setReadinessLoading(false);

        // Refresh rule list and configuration row version.
        setRefreshCounter((value) => value + 1);
      }

      setSavingRule(false);
    }
  }

  if (!canManage) {
    return (
      <div className="page-container">
        <h1>Business Configuration</h1>
        <p role="alert">
          Access restricted to Finance Managers.
        </p>
      </div>
    );
  }

  return (
    <div className="page-container">

      <h1>Business Configuration</h1>

      <h2>Configuration Versions</h2>

      {loadingVersions ? (
        <p>Loading configuration versions...</p>
      ) : (
        <div>
          <label htmlFor="configuration-version">
            Select Configuration Version
          </label>

          <select
            id="configuration-version"
            value={selectedVersion}
            onChange={(event) =>
              setSelectedVersion(event.target.value)
            }
          >
            {versions.length === 0 && (
              <option value="">
                No configuration versions available
              </option>
            )}

            {versions.map((version) => (
              <option
                key={version.versionId}
                value={String(version.versionId)}
              >
                {version.versionLabel}
                {" - "}
                {version.publicationState}
                {" (Version "}
                {version.versionNumber}
                {")"}
              </option>
            ))}
          </select>
        </div>
      )}

      <h2>Create Draft Configuration</h2>

      <form
        className="card draft-config-form"
        onSubmit={handleCreateDraft}
      >
        <div className="draft-form-grid">

          <div className="draft-form-field full-width">
            <label htmlFor="draft-version-label">
              Version Label
            </label>

            <input
              id="draft-version-label"
              type="text"
              value={versionLabel}
              onChange={(event) =>
                setVersionLabel(event.target.value)
              }
              maxLength={100}
              required
              disabled={creatingDraft}
              placeholder="Enter configuration version label"
            />
          </div>

          <div className="draft-form-field full-width">
            <label htmlFor="draft-description">
              Description
            </label>

            <textarea
              id="draft-description"
              value={description}
              onChange={(event) =>
                setDescription(event.target.value)
              }
              maxLength={1000}
              rows={3}
              disabled={creatingDraft}
              placeholder="Describe the configuration changes"
            />
          </div>

          <div className="draft-form-field">
            <label htmlFor="draft-score-policy">
              Score Policy
            </label>

            <select
              id="draft-score-policy"
              value={scorePolicyCode}
              onChange={(event) =>
                setScorePolicyCode(event.target.value)
              }
              disabled={creatingDraft}
            >
              <option value="CAP_AT_MAX">CAP_AT_MAX</option>
              <option value="SUM">SUM</option>
            </select>
          </div>

          {scorePolicyCode === "CAP_AT_MAX" && (
            <div className="draft-form-field">
              <label htmlFor="draft-max-score">
                Maximum Final Score
              </label>

              <input
                id="draft-max-score"
                type="number"
                min="1"
                max="2147483647"
                step="1"
                value={maxFinalScore}
                onChange={(event) =>
                  setMaxFinalScore(event.target.value)
                }
                disabled={creatingDraft}
                required
              />
            </div>
          )}

          <div className="draft-checkbox-field">
            <label
              className="draft-checkbox-label"
              htmlFor="draft-demonstration"
            >
              <input
                id="draft-demonstration"
                type="checkbox"
                checked={isDemonstration}
                onChange={(event) =>
                  setIsDemonstration(event.target.checked)
                }
                disabled={creatingDraft}
              />
              Demonstration Version
            </label>
          </div>
        </div>

        {createError && (
          <p className="draft-error" role="alert">
            {createError}
          </p>
        )}

        {createSuccess && (
          <p className="draft-success" role="status">
            {createSuccess}
          </p>
        )}

        <div className="draft-form-actions">
          <button
            className="draft-submit-button"
            type="submit"
            disabled={creatingDraft || !token}
          >
            {creatingDraft
              ? "Creating Draft..."
              : "Create Draft Configuration"}
          </button>
        </div>
      </form>

      {versionsError && (
        <p role="alert">{versionsError}</p>
      )}

      <h2>Active Configuration</h2>

      {loadingActive ? (
        <p>Loading active configuration...</p>
      ) : activeError ? (
        <p role="alert">{activeError}</p>
      ) : !activeConfiguration ? (
        <p>No active configuration available.</p>
      ) : (
        <div className="card">

          <p><strong>Active Version ID:</strong>{" "}
            {displayValue(activeConfiguration.versionId)}</p>

          <p><strong>Version Row Version:</strong>{" "}
            {displayValue(activeConfiguration.versionRowVersion)}</p>

          <p><strong>Pointer Row Version:</strong>{" "}
            {displayValue(activeConfiguration.pointerRowVersion)}</p>

          <p><strong>Activated At:</strong>{" "}
            {formatDateTime(activeConfiguration.activatedAt)}</p>

          <p><strong>Activated By User ID:</strong>{" "}
            {displayValue(activeConfiguration.activatedByUserId)}</p>

          <p><strong>Published At:</strong>{" "}
            {formatDateTime(activeConfiguration.publishedAt)}</p>

          <p><strong>Published By User ID:</strong>{" "}
            {displayValue(activeConfiguration.publishedByUserId)}</p>

          <p><strong>Hash Format:</strong>{" "}
            {displayValue(activeConfiguration.hashFormat)}</p>

        </div>
      )}

      <h2>Selected Configuration Version Details</h2>

      {loadingConfiguration ? (
        <p>Loading version details...</p>
      ) : !versionDetails ? (
        <p>No configuration version details available.</p>
      ) : (
        <div className="card">

          <p><strong>Version ID:</strong>{" "}
            {displayValue(versionDetails.versionId)}</p>

          <p><strong>Version Number:</strong>{" "}
            {displayValue(versionDetails.versionNumber)}</p>

          <p><strong>Version Label:</strong>{" "}
            {displayValue(versionDetails.versionLabel)}</p>

          <p><strong>Publication State:</strong>{" "}
            {displayValue(versionDetails.publicationState)}</p>

          <p><strong>Description:</strong>{" "}
            {displayValue(versionDetails.description)}</p>

          <p><strong>Demonstration Version:</strong>{" "}
            {versionDetails.isDemonstration ? "Yes" : "No"}</p>

          <p><strong>Score Policy:</strong>{" "}
            {displayValue(versionDetails.scorePolicyCode)}</p>

          <p><strong>Maximum Final Score:</strong>{" "}
            {displayValue(versionDetails.maxFinalScore)}</p>

          <p><strong>Row Version:</strong>{" "}
            {displayValue(versionDetails.rowVersion)}</p>

          <p><strong>Created At:</strong>{" "}
            {formatDateTime(versionDetails.createdAt)}</p>

          <p><strong>Published At:</strong>{" "}
            {formatDateTime(versionDetails.publishedAt)}</p>

          <p><strong>Currently Active:</strong>{" "}
            {activeConfiguration &&
            String(activeConfiguration.versionId) ===
            String(versionDetails.versionId)
              ? "Yes"
              : "No"}</p>

        </div>
      )}

      <h2>Publication Readiness</h2>

      <div className="card">

        <p>
          Check whether the selected configuration version
          satisfies the requirements for publication.
        </p>

        <p><strong>Selected Version ID:</strong>{" "}
          {displayValue(selectedVersion)}</p>

        <button
          type="button"
          onClick={handleCheckReadiness}
          disabled={
            !token ||
            !selectedVersion ||
            loadingConfiguration ||
            readinessLoading
          }
        >
          {readinessLoading
            ? "Checking Publication Readiness..."
            : "Check Publication Readiness"}
        </button>

        {readinessError && (
          <p role="alert">{readinessError}</p>
        )}

        {readiness && (
          <div aria-live="polite">

            <h3>Publication Readiness Result</h3>

            <p><strong>Version ID:</strong>{" "}
              {displayValue(readiness.versionId)}</p>

            <p><strong>Row Version:</strong>{" "}
              {displayValue(readiness.rowVersion)}</p>

            <p><strong>Publication State:</strong>{" "}
              {displayValue(readiness.publicationState)}</p>

            <p><strong>Demonstration Version:</strong>{" "}
              {readiness.isDemonstration ? "Yes" : "No"}</p>

            <p><strong>Structurally Valid:</strong>{" "}
              {readiness.structurallyValid ? "Yes" : "No"}</p>

            <p><strong>Ready for Publication:</strong>{" "}
              {readiness.ready ? "Yes" : "No"}</p>

            <p><strong>Total Issues:</strong>{" "}
              {readinessIssues.length}</p>

            <p role="status">
              {readiness.ready
                ? "Configuration passed the readiness checks."
                : "Configuration is not ready. Review the issues below."}
            </p>

            <h3>Publication Validation Issues</h3>

            <div style={{ overflowX: "auto" }}>
              <table>
                <thead>
                  <tr>
                    <th>Category</th>
                    <th>Code</th>
                    <th>Path</th>
                    <th>Message</th>
                  </tr>
                </thead>
                <tbody>
                  {readinessIssues.length === 0 ? (
                    <tr>
                      <td colSpan={4}>
                        No validation issues reported.
                      </td>
                    </tr>
                  ) : (
                    readinessIssues.map((issue, index) => (
                      <tr key={`${issue.code}-${issue.path}-${index}`}>
                        <td>{displayValue(issue.category)}</td>
                        <td>{displayValue(issue.code)}</td>
                        <td>{displayValue(issue.path)}</td>
                        <td>{displayValue(issue.message)}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>

          </div>
        )}
      </div>

      {configurationError && (
        <p role="alert">{configurationError}</p>
      )}

      <h2>Risk Rules</h2>

      {loadingConfiguration ? (
        <p>Loading risk rules...</p>
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table>
            <thead>
              <tr>
                <th>Rule Code</th>
                <th>Display Name</th>
                <th>Enabled</th>
                <th>Weight</th>
                <th>Evaluation Order</th>
              </tr>
            </thead>
            <tbody>
              {rules.length === 0 ? (
                <tr>
                  <td colSpan={5}>
                    No risk rules available.
                  </td>
                </tr>
              ) : (
                rules.map((rule) => (
                  <tr key={rule.ruleConfigId}>
                    <td>{rule.ruleCode}</td>
                    <td>{rule.displayName}</td>
                    <td>{rule.enabled ? "Yes" : "No"}</td>
                    <td>{rule.weight}</td>
                    <td>{rule.evaluationOrder}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      {selectedIsDraft && (
        <>
          <h2>Manage Draft Risk Rules</h2>

          <form
            className="card draft-config-form"
            onSubmit={handleSaveRule}
          >
            <p>
              Add missing risk rules or update existing
              risk rules for Draft Version {selectedVersion}.
            </p>

            <div className="draft-form-grid">

              <div className="draft-form-field full-width">
                <label htmlFor="edit-rule-code">
                  Select Risk Rule
                </label>

                <select
                  id="edit-rule-code"
                  value={selectedRuleCode}
                  onChange={(event) =>
                    setSelectedRuleCode(event.target.value)
                  }
                  disabled={savingRule}
                >
                  {RULE_TEMPLATES.map((template) => {
                    const exists = rules.some(
                      (rule) =>
                        rule.ruleCode === template.ruleCode
                    );

                    return (
                      <option
                        key={template.ruleCode}
                        value={template.ruleCode}
                      >
                        {template.ruleCode}
                        {exists ? " - Configured" : " - Missing"}
                      </option>
                    );
                  })}
                </select>
              </div>

              <div className="draft-form-field full-width">
                <p>
                  <strong>Rule Status:</strong>{" "}
                  {selectedRule
                    ? "Already configured"
                    : "Missing - ready to add"}
                </p>
              </div>

              <div className="draft-form-field full-width">
                <label htmlFor="edit-rule-name">
                  Display Name
                </label>

                <input
                  id="edit-rule-name"
                  type="text"
                  value={ruleForm.displayName}
                  onChange={(event) =>
                    updateRuleField(
                      "displayName",
                      event.target.value
                    )
                  }
                  maxLength={120}
                  disabled={savingRule || !canEditDraft}
                  required
                />
              </div>

              <div className="draft-form-field">
                <label htmlFor="edit-rule-weight">
                  Rule Weight
                </label>

                <input
                  id="edit-rule-weight"
                  type="number"
                  min="0"
                  step="1"
                  value={ruleForm.weight}
                  onChange={(event) =>
                    updateRuleField(
                      "weight",
                      event.target.value
                    )
                  }
                  disabled={savingRule || !canEditDraft}
                  required
                />
              </div>

              <div className="draft-form-field">
                <label htmlFor="edit-rule-order">
                  Evaluation Order
                </label>

                <input
                  id="edit-rule-order"
                  type="number"
                  min="1"
                  max="99999"
                  step="1"
                  value={ruleForm.evaluationOrder}
                  onChange={(event) =>
                    updateRuleField(
                      "evaluationOrder",
                      event.target.value
                    )
                  }
                  disabled={savingRule || !canEditDraft}
                  required
                />
              </div>

              <div className="draft-form-field full-width">
                <label htmlFor="edit-rule-schema">
                  Parameter Schema Version
                </label>

                <input
                  id="edit-rule-schema"
                  type="text"
                  value={ruleForm.parameterSchemaVersion}
                  onChange={(event) =>
                    updateRuleField(
                      "parameterSchemaVersion",
                      event.target.value
                    )
                  }
                  maxLength={32}
                  disabled={savingRule || !canEditDraft}
                  required
                />
              </div>

              <div className="draft-form-field full-width">
                <label htmlFor="edit-rule-parameters">
                  Rule Parameters (JSON)
                </label>

                <textarea
                  id="edit-rule-parameters"
                  value={ruleForm.parametersText}
                  onChange={(event) =>
                    updateRuleField(
                      "parametersText",
                      event.target.value
                    )
                  }
                  rows={10}
                  spellCheck={false}
                  disabled={savingRule || !canEditDraft}
                  required
                  style={{
                    fontFamily: "monospace"
                  }}
                />
              </div>

              <div className="draft-checkbox-field">
                <label className="draft-checkbox-label">
                  <input
                    type="checkbox"
                    checked={ruleForm.enabled}
                    onChange={(event) =>
                      updateRuleField(
                        "enabled",
                        event.target.checked
                      )
                    }
                    disabled={savingRule || !canEditDraft}
                  />
                  Rule Enabled
                </label>
              </div>
            </div>

            <p>
              <strong>Current Draft Row Version:</strong>{" "}
              {displayValue(versionDetails?.rowVersion)}
            </p>

            <p>
              The latest row version will be retrieved
              automatically before saving.
            </p>

            {ruleError && (
              <p className="draft-error" role="alert">
                {ruleError}
              </p>
            )}

            {ruleSuccess && (
              <p className="draft-success" role="status">
                {ruleSuccess}
              </p>
            )}

            <div className="draft-form-actions">
              <button
                className="draft-submit-button"
                type="submit"
                disabled={savingRule || !canEditDraft}
              >
                {savingRule
                  ? "Saving Risk Rule..."
                  : selectedRule
                    ? "Update Risk Rule"
                    : "Add Risk Rule"}
              </button>
            </div>

          </form>
        </>
      )}

      <h2>Risk Bands</h2>

      {loadingConfiguration ? (
        <p>Loading risk bands...</p>
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table>
            <thead>
              <tr>
                <th>Risk Band</th>
                <th>Minimum Score (Inclusive)</th>
                <th>Maximum Score (Inclusive)</th>
                <th>Approval Level</th>
              </tr>
            </thead>
            <tbody>
              {bands.length === 0 ? (
                <tr>
                  <td colSpan={4}>
                    No risk bands available.
                  </td>
                </tr>
              ) : (
                bands.map((band) => (
                  <tr key={band.bandConfigId}>
                    <td>{band.bandCode}</td>
                    <td>{band.minScoreInclusive}</td>
                    <td>
                      {band.maxScoreInclusive ?? "No upper limit"}
                    </td>
                    <td>
                      {formatApprovalLevel(band.approvalLevel)}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      <h2>Approval Thresholds</h2>

      {loadingConfiguration ? (
        <p>Loading approval thresholds...</p>
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table>
            <thead>
              <tr>
                <th>Approval Level</th>
                <th>Minimum Amount (Inclusive)</th>
                <th>Maximum Amount (Exclusive)</th>
              </tr>
            </thead>
            <tbody>
              {thresholds.length === 0 ? (
                <tr>
                  <td colSpan={3}>
                    No approval thresholds available.
                  </td>
                </tr>
              ) : (
                thresholds.map((threshold) => (
                  <tr key={threshold.thresholdConfigId}>
                    <td>
                      {formatApprovalLevel(threshold.approvalLevel)}
                    </td>
                    <td>
                      ₹{formatAmount(threshold.minAmountInclusive)}
                    </td>
                    <td>
                      {threshold.maxAmountExclusive == null
                        ? "No upper limit"
                        : `₹${formatAmount(threshold.maxAmountExclusive)}`}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      <h2>Approval SLA Configuration</h2>

      {loadingConfiguration ? (
        <p>Loading approval SLAs...</p>
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table>
            <thead>
              <tr>
                <th>Approval Level</th>
                <th>Duration</th>
                <th>Duration (Seconds)</th>
                <th>Start Event</th>
                <th>Escalation Policy</th>
                <th>Terminal Level Policy</th>
              </tr>
            </thead>
            <tbody>
              {slas.length === 0 ? (
                <tr>
                  <td colSpan={6}>
                    No approval SLA configurations available.
                  </td>
                </tr>
              ) : (
                slas.map((sla) => (
                  <tr key={sla.slaConfigId}>
                    <td>
                      {formatApprovalLevel(sla.approvalLevel)}
                    </td>
                    <td>
                      {formatDuration(sla.durationSeconds)}
                    </td>
                    <td>{sla.durationSeconds}</td>
                    <td>{sla.startEventCode}</td>
                    <td>{sla.escalationPolicyCode}</td>
                    <td>{sla.terminalLevelPolicyCode}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

    </div>
  );
}
