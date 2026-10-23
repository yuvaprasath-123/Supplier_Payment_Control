[CmdletBinding()]
param(
    [ValidateSet('Verify','ConfigurationCleanup','RiskCleanup')][string]$Mode = 'Verify',
    [switch]$ConfirmConfigurationIsolated,
    [switch]$ConfirmStopped,
    [string]$Namespace
)

# User-run opt-in only. Verification starts temporary loopback D2 receivers and commits synthetic fixtures.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$internalVerificationRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$internalSavedEnvironment = @{}
function Set-InternalVerificationEnvironment([string]$Name, [AllowNull()][string]$Value) {
    if (-not $internalSavedEnvironment.ContainsKey($Name)) { $internalSavedEnvironment[$Name] = [Environment]::GetEnvironmentVariable($Name, 'Process') }
    [Environment]::SetEnvironmentVariable($Name, $Value, 'Process')
}
function Read-InternalVerificationPassword([string]$Name, [string]$Prompt) {
    $internalSecureInput = Read-Host $Prompt -AsSecureString
    $internalPointer = [IntPtr]::Zero
    try {
        if ($internalSecureInput.Length -eq 0) { throw 'A local database password is required.' }
        $internalPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($internalSecureInput)
        $internalPlainInput = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($internalPointer)
        Set-InternalVerificationEnvironment $Name $internalPlainInput
        $internalPlainInput = $null
    } finally {
        if ($internalPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($internalPointer) }
        $internalSecureInput.Dispose()
    }
}
if ($Mode -eq 'Verify' -and -not $ConfirmConfigurationIsolated) {
    throw 'Confirm an isolated configuration environment with no current pointer and stopped configuration writers using -ConfirmConfigurationIsolated. Existing pointers are never deleted for setup.'
}
if ($Mode -ne 'Verify') {
    if (-not $ConfirmStopped) { throw 'Stop all test receivers, requests and relevant writers, then explicitly pass -ConfirmStopped.' }
    $internalPrefix = if ($Mode -eq 'ConfigurationCleanup') { 'D2_CFG_ACTIVE_' } else { 'D2_RISK_FINAL_' }
    if ([string]::IsNullOrWhiteSpace($Namespace) -or $Namespace -cnotmatch ('^' + $internalPrefix + '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')) {
        throw 'Supply the exact printed canonical fixture namespace. No wildcard cleanup is permitted.'
    }
}
Push-Location -LiteralPath $internalVerificationRoot
try {
    # Real user signing material is not required by this ephemeral TEST issuer suite.
    Set-InternalVerificationEnvironment 'CONFIG_JWT_SECRET' $null
    Set-InternalVerificationEnvironment 'RISK_JWT_SECRET' $null
    if ($Mode -eq 'Verify') {
        Read-InternalVerificationPassword 'CONFIG_DB_PASSWORD' 'D2_CONFIGURATION password (hidden, local only)'
        Read-InternalVerificationPassword 'RISK_DB_PASSWORD' 'D2_RISK password (hidden, local only)'
        Set-InternalVerificationEnvironment 'RISK_DB_USERNAME' 'D2_RISK'
        Set-InternalVerificationEnvironment 'RISK_DB_URL' 'jdbc:oracle:thin:@//localhost:1521/FREEPDB1'
        Set-InternalVerificationEnvironment 'D2_VERIFY_INTERNAL_HTTP_ORACLE_WRITES' 'true'
        Set-InternalVerificationEnvironment 'CONFIG_ORACLE_INTERNAL_HTTP_ISOLATED' 'true'
        Write-Host 'Ephemeral TEST issuer only. This starts temporary loopback receivers and commits isolated fixtures; it does not verify a genuine team issuer.'
        & mvn -f developer2-pom.xml -pl 'business-configuration-service,risk-assessment-service' -am '-Dtest=OracleInternalConfigurationHttpTest,OracleInternalRiskHttpTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
    } elseif ($Mode -eq 'ConfigurationCleanup') {
        Read-InternalVerificationPassword 'CONFIG_DB_PASSWORD' 'D2_CONFIGURATION password (hidden, local only)'
        Set-InternalVerificationEnvironment 'D2_VERIFY_INTERNAL_HTTP_ORACLE_WRITES' 'true'
        Set-InternalVerificationEnvironment 'CONFIG_ORACLE_INTERNAL_HTTP_ISOLATED' 'true'
        Set-InternalVerificationEnvironment 'CONFIG_INTERNAL_HTTP_WORKERS_STOPPED' 'true'
        Set-InternalVerificationEnvironment 'CONFIG_INTERNAL_HTTP_FIXTURE_NAMESPACE' $Namespace
        & mvn -f developer2-pom.xml -pl business-configuration-service -am '-Dtest=OracleInternalConfigurationCleanupTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
    } else {
        Read-InternalVerificationPassword 'RISK_DB_PASSWORD' 'D2_RISK password (hidden, local only)'
        Set-InternalVerificationEnvironment 'RISK_DB_USERNAME' 'D2_RISK'
        Set-InternalVerificationEnvironment 'RISK_DB_URL' 'jdbc:oracle:thin:@//localhost:1521/FREEPDB1'
        Set-InternalVerificationEnvironment 'RISK_VERIFY_ORACLE_HTTP_WRITES' 'true'
        Set-InternalVerificationEnvironment 'RISK_ORACLE_HTTP_CLEANUP_ONLY' 'true'
        Set-InternalVerificationEnvironment 'RISK_HTTP_TEST_REQUESTS_STOPPED' 'true'
        Set-InternalVerificationEnvironment 'RISK_ORACLE_HTTP_NAMESPACE' $Namespace
        & mvn -f developer2-pom.xml -pl risk-assessment-service -am '-Dtest=OracleRiskReadHttpCleanupTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
    }
    if ($LASTEXITCODE -ne 0) { throw 'Verification/recovery failed. Retain printed namespaces and reports; no broader cleanup is attempted.' }
} finally {
    foreach ($internalVariable in $internalSavedEnvironment.Keys) { [Environment]::SetEnvironmentVariable($internalVariable, $internalSavedEnvironment[$internalVariable], 'Process') }
    $internalSavedEnvironment.Clear()
    Pop-Location
}
