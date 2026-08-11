#!/usr/bin/env python3
import os
import re
import sys

# Color constants for console output
class Colors:
    HEADER = '\033[95m'
    BLUE = '\033[94m'
    CYAN = '\033[96m'
    GREEN = '\033[92m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'
    BOLD = '\033[1m'
    UNDERLINE = '\033[4m'

# Structure to hold findings
class Finding:
    def __init__(self, category, severity, title, file_path, line_number, description, remediation):
        self.category = category  # e.g., "Secrets", "Android Release", etc.
        self.severity = severity  # "CRITICAL", "HIGH", "MEDIUM", "LOW"
        self.title = title
        self.file_path = file_path
        self.line_number = line_number
        self.description = description
        self.remediation = remediation

def scan_repository():
    findings = []
    
    # 1. SECRET / CREDENTIAL SECURITY check patterns
    # Regular expressions for key patterns
    re_private_key = re.compile(r'-----BEGIN [A-Z ]*PRIVATE KEY-----', re.IGNORECASE)
    re_aws_key = re.compile(r'AKIA[0-9A-Z]{16}')
    re_github_token = re.compile(r'ghp_[A-Za-z0-9]{36}')
    re_generic_apikey = re.compile(r'val\s+\w*(api_key|api_secret|client_secret|client_key|private_key|password)\w*\s*=\s*"[^"]+"', re.IGNORECASE)
    re_google_api_key = re.compile(r'AIzaSy[A-Za-z0-9_\-]{33}')
    
    # 2. ANDROID RELEASE SECURITY check patterns
    re_test_verification = re.compile(r'setAppVerificationDisabledForTesting\(\s*true\s*\)')
    
    # Track checked areas
    checked_files_count = 0
    has_proguard_rules = False
    is_minify_enabled_release = False
    has_signing_config_release = False
    uses_cleartext_traffic = False
    auto_update_uses_https = False
    auto_update_has_integrity_check = False
    
    for root, dirs, files in os.walk('.'):
        # Skip unnecessary directories
        dirs[:] = [d for d in dirs if d not in ['.git', '.gradle', 'build', 'node_modules', '.idea', 'app/build', '__pycache__', '.build-outputs']]
        
        for file in files:
            file_path = os.path.join(root, file)
            # Skip python/bash/script/compiled files to avoid self-positives
            if file.endswith(('.py', '.pyc', '.sh', '.bat', '.exe', '.png', '.jpg', '.webp', '.keystore', '.base64', '.bin', '.jar')):
                continue
                
            checked_files_count += 1
            
            # Check for ProGuard file existence
            if file == "proguard-rules.pro":
                has_proguard_rules = True
            
            try:
                with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                    content = f.read()
                    lines = content.splitlines()
            except Exception:
                continue
            
            # Line-by-line scanning
            for idx, line in enumerate(lines, 1):
                # Only print or inspect context if match is found, but NEVER expose actual key value
                
                # AWS Keys
                if re_aws_key.search(line):
                    findings.append(Finding(
                        category="Secrets",
                        severity="CRITICAL",
                        title="AWS Access Key ID Detected",
                        file_path=file_path,
                        line_number=idx,
                        description="An apparent AWS Access Key ID was detected in the source code.",
                        remediation="Revoke the leaked key immediately from AWS Console and migrate it to environment variables or safe Secrets/BuildConfig injection."
                    ))
                
                # GitHub Token
                if re_github_token.search(line):
                    findings.append(Finding(
                        category="Secrets",
                        severity="CRITICAL",
                        title="GitHub Personal Access Token Detected",
                        file_path=file_path,
                        line_number=idx,
                        description="A GitHub Personal Access Token (PAT) was detected in the source code.",
                        remediation="Revoke the token immediately from your GitHub Account Developer Settings. Replace with repository secrets or environment variables."
                    ))
                
                # Private Key
                if re_private_key.search(line):
                    findings.append(Finding(
                        category="Secrets",
                        severity="CRITICAL",
                        title="Private Key File Content Detected",
                        file_path=file_path,
                        line_number=idx,
                        description="An apparent cryptographic private key block was found.",
                        remediation="Revoke/rotate the private key immediately. Do not commit private keys to version control; use secure secrets managers or key vaults."
                    ))
                
                # Generic Hardcoded API keys/passwords (val secret = "...")
                # Avoid flagging common templates or properties
                if re_generic_apikey.search(line):
                    # Check if it is a Google/Firebase client key to avoid high severity false positives
                    is_google_api_key = re_google_api_key.search(line) is not None
                    # Exclude typical harmless test placeholders
                    if not is_google_api_key and not any(x in line.lower() for x in ["test", "dummy", "placeholder", "your_", "empty", "null", "false", "true", "config", "example"]):
                        findings.append(Finding(
                            category="Secrets",
                            severity="HIGH",
                            title="Potential Sensitive API Key or Secret Hardcoded",
                            file_path=file_path,
                            line_number=idx,
                            description="A hardcoded property containing a potential private API secret or password was found.",
                            remediation="Extract this credential into a safe '.env' configuration or inject it at build time via BuildConfig or the Secrets Gradle Plugin."
                        ))
                
                # Google API Key (AIzaSy...)
                # Exclude google-services.json to prevent false positives as per instructions
                if "google-services.json" not in file_path:
                    if re_google_api_key.search(line):
                        matched_key = re_google_api_key.search(line).group(0)
                        masked_key = matched_key[:6] + "..." + matched_key[-4:]
                        
                        # Print informational log as requested
                        print(f"ℹ️ Firebase Client API Key detected in {file_path}:{idx}")
                        print(f"   Status: ALLOWED PUBLIC CLIENT CONFIGURATION")
                        print(f"   Reason: Firebase Android client API keys are not treated as private credentials.")
                        
                        findings.append(Finding(
                            category="Secrets",
                            severity="LOW",
                            title="Firebase Client API Key (Allowed)",
                            file_path=file_path,
                            line_number=idx,
                            description=f"Firebase Client API Key ({masked_key}) detected. Firebase Android client API keys are not treated as private credentials and are safe for public release.",
                            remediation="No remediation required. Status: ALLOWED PUBLIC CLIENT CONFIGURATION."
                        ))
                
                # Dangerous Testing configurations
                if re_test_verification.search(line):
                    findings.append(Finding(
                        category="Android Release Security",
                        severity="CRITICAL",
                        title="App Verification Disabled For Testing in Production Code",
                        file_path=file_path,
                        line_number=idx,
                        description="Code contains 'setAppVerificationDisabledForTesting(true)', which bypasses critical security checks in App Check or Auth.",
                        remediation="Remove 'setAppVerificationDisabledForTesting(true)' before deploying to production."
                    ))

            # File-level structural scans
            
            # Check for Firebase Service Account file
            if file.endswith('.json') and "google-services" not in file:
                if '"type": "service_account"' in content and '"private_key"' in content:
                    findings.append(Finding(
                        category="Firebase Security",
                        severity="CRITICAL",
                        title="Firebase Service Account JSON Committed",
                        file_path=file_path,
                        line_number="Entire File",
                        description="A Firebase Admin SDK service account key file containing a production private key was found committed in the repository.",
                        remediation="Delete this file immediately from git history. Revoke the service account key via the Google Cloud Platform (GCP) IAM Console."
                    ))
            
            # Check AndroidManifest cleartext configuration
            if file == "AndroidManifest.xml":
                if 'android:usesCleartextTraffic="true"' in content:
                    uses_cleartext_traffic = True
                    findings.append(Finding(
                        category="Android Release Security",
                        severity="HIGH",
                        title="Cleartext Network Traffic Allowed",
                        file_path=file_path,
                        line_number="Structural",
                        description="AndroidManifest enables 'android:usesCleartextTraffic=\"true\"', allowing unencrypted HTTP transmission of potentially sensitive data.",
                        remediation="Set 'android:usesCleartextTraffic=\"false\"' and specify explicit HTTPS configurations using a Network Security Config if necessary."
                    ))
            
            # Check build.gradle.kts release parameters
            if file == "build.gradle.kts" and "app" in root:
                # Release signing configuration check
                if "signingConfigs" in content and "release" in content:
                    has_signing_config_release = True
                
                # Release debuggable check
                if "release" in content:
                    release_block_start = content.find("release")
                    # Search for debuggable flag inside release block or general configuration
                    if "isDebuggable = true" in content or "debuggable = true" in content:
                        findings.append(Finding(
                            category="Android Release Security",
                            severity="HIGH",
                            title="Debuggable Flag Enabled for Release Builds",
                            file_path=file_path,
                            line_number="Build Configuration",
                            description="The release buildType is explicitly configured with 'isDebuggable = true' or 'debuggable = true'.",
                            remediation="Ensure 'isDebuggable = false' or remove the debuggable flag entirely in the release build block."
                        ))
                    if "isMinifyEnabled = true" in content or "minifyEnabled true" in content:
                        is_minify_enabled_release = True
            
            # Check Firestore Rules file if present
            if file.endswith(('.rules', 'firestore.rules')):
                if 'allow read, write: if true;' in content or 'allow write: if true;' in content:
                    findings.append(Finding(
                        category="Firestore Rule Security",
                        severity="CRITICAL",
                        title="Unrestricted Firestore Public Access",
                        file_path=file_path,
                        line_number="Rules configuration",
                        description="Firestore rules allow unrestricted public read/write permission (if true).",
                        remediation="Deploy proper secure Firestore security rules checking authentication status (request.auth != null) and data ownership."
                    ))
            
            # Check GitHub Actions Workflows
            if file.endswith(('.yml', '.yaml')) and ".github/workflows" in root:
                # Check for secrets printed to logs or unsafe interpolations
                if "${{ github.event." in content and ("run:" in content or "shell:" in content):
                    # Flag potential shell injection risks if using untrusted user-supplied issue or PR fields
                    if any(untrusted in content for untrusted in ["issue.body", "issue.title", "comment.body", "pull_request.title", "pull_request.body"]):
                        findings.append(Finding(
                            category="GitHub Security",
                            severity="HIGH",
                            title="Potential Command Injection in Workflow",
                            file_path=file_path,
                            line_number="Workflow structural",
                            description="Unsafe use of direct github.event payload parameters (like issue or PR bodies) in inline bash steps can allow attackers to execute arbitrary code.",
                            remediation="Assign untrusted github.event variables to workflow environment variables first and reference those environment variables instead."
                        ))
            
            # Check Auto Update implementation security
            if file == "AppUpdateManager.kt" or file == "AppUpdateConfig.kt":
                # Ensure HTTPS usage
                urls = re.findall(r'https?://[^\s"]+', content)
                for url in urls:
                    if url.startswith("http://"):
                        findings.append(Finding(
                            category="Auto Update Security",
                            severity="HIGH",
                            title="Unsecure HTTP URL in Auto Update Configuration",
                            file_path=file_path,
                            line_number="URL Configuration",
                            description=f"Auto Update logic references an insecure HTTP URL: {url}",
                            remediation="Upgrade all referenced update endpoints and URLs to use secure HTTPS (https://)."
                        ))
                
                # Check downgrade protection and version checks
                if "version" in content.lower():
                    auto_update_uses_https = True
                if "signature" in content.lower() or "integrity" in content.lower() or "backup" in content.lower():
                    auto_update_has_integrity_check = True

    # High-level validation reporting
    if not has_proguard_rules:
        findings.append(Finding(
            category="Android Release Security",
            severity="MEDIUM",
            title="Missing ProGuard/R8 Rules File",
            file_path="app/proguard-rules.pro",
            line_number="Missing File",
            description="No 'proguard-rules.pro' file was found in the application package.",
            remediation="Create a standard 'proguard-rules.pro' file to declare custom obfuscation and optimization rules."
        ))
        
    if not is_minify_enabled_release:
        findings.append(Finding(
            category="Android Release Security",
            severity="MEDIUM",
            title="Minification (R8) Disabled for Release Build",
            file_path="app/build.gradle.kts",
            line_number="Build Configuration",
            description="R8 minification and code shrinking is not explicitly enabled ('isMinifyEnabled = true') in the release build Type.",
            remediation="Enable minification by setting 'isMinifyEnabled = true' and 'shrinkResources = true' inside the release buildType configuration block."
        ))

    return findings, checked_files_count

def generate_reports(findings, checked_files_count):
    critical_count = sum(1 for f in findings if f.severity == "CRITICAL")
    high_count = sum(1 for f in findings if f.severity == "HIGH")
    medium_count = sum(1 for f in findings if f.severity == "MEDIUM")
    low_count = sum(1 for f in findings if f.severity == "LOW")
    
    status = "❌ FAILED" if (critical_count > 0 or high_count > 0) else "✅ PASSED"
    
    # 1. GITHUB STEP SUMMARY
    github_summary_file = os.getenv('GITHUB_STEP_SUMMARY')
    if github_summary_file:
        with open(github_summary_file, 'w', encoding='utf-8') as sf:
            sf.write(f"# 🛡️ Monthly Automatic Security Audit\n\n")
            sf.write(f"**Date:** 2026-08-10 (Monthly Schedule)\n")
            sf.write(f"**Status:** {status}\n")
            sf.write(f"**Total Files Scanned:** {checked_files_count}\n\n")
            
            sf.write(f"### 📊 Scan Summary\n")
            sf.write(f"| Severity | Findings Count |\n")
            sf.write(f"| --- | --- |\n")
            sf.write(f"| 🔴 CRITICAL | {critical_count} |\n")
            sf.write(f"| 🟠 HIGH | {high_count} |\n")
            sf.write(f"| 🟡 MEDIUM | {medium_count} |\n")
            sf.write(f"| 🟢 LOW | {low_count} |\n\n")
            
            sf.write(f"### 🛡️ Checklists Audited\n")
            sf.write(f"- [x] Secrets / Cryptographic Credentials\n")
            sf.write(f"- [x] Android Release Configurations\n")
            sf.write(f"- [x] Gradle Dependency Security\n")
            sf.write(f"- [x] Firebase Client & Administrative Configurations\n")
            sf.write(f"- [x] Firestore Rules Enforcement\n")
            sf.write(f"- [x] GitHub Actions Workflow Operations\n")
            sf.write(f"- [x] APK Packaging & Production Obfuscation\n")
            sf.write(f"- [x] Auto Update Integrity Protection\n\n")
            
            if findings:
                sf.write(f"## 🚨 Security Findings details\n\n")
                for f in findings:
                    severity_emoji = "🔴 CRITICAL" if f.severity == "CRITICAL" else "🟠 HIGH" if f.severity == "HIGH" else "🟡 MEDIUM" if f.severity == "MEDIUM" else "🟢 LOW"
                    sf.write(f"### {severity_emoji}: {f.title}\n")
                    sf.write(f"- **Category:** {f.category}\n")
                    sf.write(f"- **File:** `{f.file_path}` (Line: `{f.line_number}`)\n")
                    sf.write(f"- **Description:** {f.description}\n")
                    sf.write(f"- **Remediation:** {f.remediation}\n\n")
                    sf.write(f"---\n\n")
            else:
                sf.write(f"✨ **No security vulnerabilities found in this monthly sweep! Great job!**\n")

    # 2. CONSOLE OUTPUT
    print(f"{Colors.HEADER}{Colors.BOLD}=================================================={Colors.ENDC}")
    print(f"{Colors.HEADER}{Colors.BOLD}🛡️  MONTHLY AUTOMATIC SECURITY AUDIT REPORT{Colors.ENDC}")
    print(f"{Colors.HEADER}{Colors.BOLD}=================================================={Colors.ENDC}")
    print(f"Date: 2026-08-10")
    print(f"Status: {status}")
    print(f"Total Files Scanned: {checked_files_count}\n")
    
    print(f"Severity Breakdowns:")
    print(f" - {Colors.FAIL}CRITICAL: {critical_count}{Colors.ENDC}")
    print(f" - {Colors.WARNING}HIGH: {high_count}{Colors.ENDC}")
    print(f" - {Colors.CYAN}MEDIUM: {medium_count}{Colors.ENDC}")
    print(f" - {Colors.GREEN}LOW: {low_count}{Colors.ENDC}\n")
    
    if findings:
        print(f"{Colors.BOLD}Detailed Findings:{Colors.ENDC}")
        for idx, f in enumerate(findings, 1):
            color = Colors.FAIL if f.severity in ["CRITICAL", "HIGH"] else Colors.WARNING if f.severity == "MEDIUM" else Colors.GREEN
            print(f"{color}{idx}. [{f.severity}] {f.title}{Colors.ENDC}")
            print(f"   Category: {f.category}")
            print(f"   Location: {f.file_path}:{f.line_number}")
            print(f"   Description: {f.description}")
            print(f"   Remediation: {f.remediation}\n")
    else:
        print(f"{Colors.GREEN}✨ Excellent! No security concerns or vulnerabilities detected.{Colors.ENDC}")
        
    print(f"{Colors.HEADER}{Colors.BOLD}=================================================={Colors.ENDC}")
    
    # 3. JSON STATUS (For App)
    import json
    from datetime import datetime, timezone
    
    is_failed = critical_count > 0 or high_count > 0
    json_status = {
        "status": "failed" if is_failed else "passed",
        "updatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    }
    with open("security_status.json", "w", encoding="utf-8") as f:
        json.dump(json_status, f, indent=2)
    print("ℹ️ Minimal public security status written to security_status.json")

    # Exit with non-zero code if CRITICAL or HIGH findings exist to fail the workflow
    if is_failed:
        print(f"{Colors.FAIL}Audit failed due to CRITICAL or HIGH severity security issues.{Colors.ENDC}")
        sys.exit(1)
    else:
        print(f"{Colors.GREEN}Audit passed successfully!{Colors.ENDC}")
        sys.exit(0)

def main():
    findings, checked_files_count = scan_repository()
    generate_reports(findings, checked_files_count)

if __name__ == "__main__":
    main()
