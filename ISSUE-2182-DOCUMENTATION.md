# Contribution 2182: Smoke test: eForm image directory is missing during startup

**Contribution Number:** 2182  
**Student:** Hana Ahmed  
**Issue:** https://github.com/carlos-emr/carlos/issues/2182  
**Status:** Phase II Complete

---

## Why I Chose This Issue

I chose this issue because it's healthcare-related and seemed like a straightforward first bug to tackle. Fixing broken images in medical forms felt important — eForms are used by clinicians daily, and silent failures like this can erode trust in the system.

---

## Understanding the Issue

### Problem Description

When the CARLOS application starts up, it tries to deploy eForm image assets into `/var/lib/OscarDocument/oscar/eform/images/`. That directory doesn't exist by default. Instead of creating it automatically, the Java class `EFormAssetDeployer.java` logs a warning and skips the deployment entirely. Users see broken image placeholders in eForms, and the warning is easy to miss in startup logs.

### Expected Behavior

The system should automatically create `/var/lib/OscarDocument/oscar/eform/images/` at startup if it doesn't already exist, then deploy the eForm assets there successfully.

### Current Behavior

`EFormAssetDeployer.java` checks if the directory exists, finds it missing, logs:
`"eForm image directory does not exist: {}; skipping asset deployment"`
and returns without creating the directory or deploying any assets.

### Affected Components

- `src/main/java/io/github/carlos_emr/carlos/eform/EFormAssetDeployer.java` — the Java class that logs the warning and skips deployment
- `.devcontainer/development/Dockerfile` — creates directories under a different path than what the app expects

---

## Reproduction Process

### Environment Setup

Forked and cloned the repo locally. Opened in VS Code. No devcontainer spin-up issues. Used VS Code's global search (magnifying glass icon) to locate the relevant files by searching `eform/images` and `skipping asset deployment`.

### Steps to Reproduce

1. Start the CARLOS application (foreground Tomcat smoke test)
2. Watch the startup logs
3. Observe the warning: `eForm image directory does not exist: /var/lib/OscarDocument/oscar/eform/images/; skipping asset deployment`
4. Check that `/var/lib/OscarDocument/oscar/eform/images/` does not exist
5. Open an eForm that uses images — they show as broken

### Reproduction Evidence

**Exact file:** `src/main/java/io/github/carlos_emr/carlos/eform/EFormAssetDeployer.java`

**Exact lines causing the bug:**
```java
if (!targetDir.isDirectory()) {
    logger.warn("eForm image directory does not exist: {}; skipping asset deployment", imageDir);
    return;
}
```

The method simply returns instead of creating the directory.

**Branch link:** https://github.com/Hanaahmed12/carlos/tree/fix-issue-2182-eform-image-directory

---

## Solution Approach

### Analysis

The root cause is in `EFormAssetDeployer.java`. The `afterPropertiesSet()` method checks whether the eForm images directory exists. If it doesn't, it logs a warning and exits — never creating the directory or deploying the assets. This is a defensive programming gap: the fix should attempt to create the directory automatically before giving up.

### Proposed Solution

In `EFormAssetDeployer.java`, replace the early `return` with a `targetDir.mkdirs()` call to auto-create the directory, then continue with asset deployment. Log an info message confirming the directory was created rather than a warning that it's missing.

### Implementation Plan (UMPIRE)

**Understand:** At startup, `EFormAssetDeployer.java` checks for `/var/lib/OscarDocument/oscar/eform/images/`. If missing, it logs a warning and skips deploying eForm assets, causing broken images in eForms.

**Match:** The `populate_db.sh` script in `.devcontainer/db/scripts/` already uses `mkdir -p /var/lib/OscarDocument/oscar/eform/images/` to create this same directory during database bootstrap. The same auto-create pattern should be applied in the Java deployer itself.

**Plan:**
1. Open `src/main/java/io/github/carlos_emr/carlos/eform/EFormAssetDeployer.java`
2. Find the `if (!targetDir.isDirectory())` block in `afterPropertiesSet()`
3. Replace the warning + return with a `targetDir.mkdirs()` call
4. Log an info message: `"eForm image directory did not exist; created: {}"`
5. If `mkdirs()` fails, log an error with remediation steps and return
6. If successful, continue to deploy assets normally

**Implement:** https://github.com/Hanaahmed12/carlos/tree/fix-issue-2182-eform-image-directory *(code coming in Phase III)*

**Review:** Read `CONTRIBUTING.md` for commit message format and PR conventions before submitting

**Evaluate:** Run Tomcat smoke test — no "skipping asset deployment" warning should appear, directory should exist at `/var/lib/OscarDocument/oscar/eform/images/`, and eForm images should render correctly

### Files to Modify

- `src/main/java/io/github/carlos_emr/carlos/eform/EFormAssetDeployer.java`

---

## Testing Strategy

### Unit Tests

- [ ] Test that `afterPropertiesSet()` creates the directory if it doesn't exist
- [ ] Test that `afterPropertiesSet()` skips creation if directory already exists
- [ ] Test that a proper error is logged if `mkdirs()` fails

### Integration Tests

- Run full Tomcat startup, verify no "skipping asset deployment" warning
- Verify directory exists at `/var/lib/OscarDocument/oscar/eform/images/` after startup
- Verify eForm images render correctly in a real eForm

### Manual Testing

Run the devcontainer, check the directory path exists, open an eForm that previously had broken images and confirm they now display correctly

---

## Implementation Notes

### Week 1 Progress

Cloned the fork, set up VS Code, created working branch `fix-issue-2182-eform-image-directory`. Used VS Code global search to locate the exact file (`EFormAssetDeployer.java`) and the exact lines causing the bug. Documented full reproduction steps and UMPIRE solution plan.

### Code Changes

- **Files to modify:** `src/main/java/io/github/carlos_emr/carlos/eform/EFormAssetDeployer.java`
- **Key commits:** Branch pushed — code changes coming in Phase III
- **Approach decisions:** Will use `targetDir.mkdirs()` in Java rather than only relying on shell scripts, so the fix works in all environments not just devcontainer

---

## Pull Request

PR Link: Not yet submitted — coming in Phase III

---

## Learnings & Reflections

### Technical Skills Gained

Learned how to navigate a large Java/Spring codebase using VS Code global search. Understood how Spring's `InitializingBean` pattern works for startup initialization. Learned the difference between devcontainer bootstrap paths and runtime application paths.

### Challenges Overcome

Tracking down the exact Java file took effort — the warning message text was the key search term that led directly to `EFormAssetDeployer.java`.

### What I'd Do Differently Next Time

Search for the exact warning message text earlier instead of browsing folders manually.

---

## Resources Used

- Issue #2182: https://github.com/carlos-emr/carlos/issues/2182
- `EFormAssetDeployer.java`: `src/main/java/io/github/carlos_emr/carlos/eform/`
- `runtime-directories.md`: `docs/`
- CARLOS `CONTRIBUTING.md`