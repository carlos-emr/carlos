# Contribution [#]: [Issue Title]

**Contribution Number:** 2182  
**Student:** Hana Ahmed 
**Issue:** https://github.com/carlos-emr/carlos/issues/2182#event-25727024596
**Status:** Phase II

---

## Why I Chose This Issue

I chose this issue because it's healthcare-related and seemed like a straightforward first bug to tackle. Honestly, I just wanted to contribute to something that actually matters, and fixing broken images in medical forms felt important
---

## Understanding the Issue

### Problem Description

When you start up the CARLOS application, the system tries to put eForm image files into a specific folder (/var/lib/OscarDocument/oscar/eform/images/), but that folder doesn't exist by default. Instead of creating the folder automatically, the system just logs a warning and skips deploying the images. This means any eForm that needs those images (like logos or icons) will show broken image placeholders, and most people probably won't even notice the warning buried in all the startup log messages.


### Expected Behavior

The system should automatically create the missing /var/lib/OscarDocument/oscar/eform/images/ directory at startup if it doesn't already exist. Then it should deploy the eForm images there without any errors or warnings. If something still goes wrong, the error message should clearly tell someone what to do, not just get lost in a pile of logs.


### Current Behavior

The system checks for the directory, doesn't find it, logs a warning that says something like "eForm image directory missing - skipping deployment," and then just moves on with the startup process. The directory never gets created, so the images never get copied over. Users only see broken image icons in their eForms, and unless someone is actively watching the startup logs (which almost no one does), there's no clear indication that anything went wrong

### Affected Components

The Java class EFormAssetDeployer.java in src/main/java/io/github/carlos_emr/carlos/eform/ is where the warning is logged. The .devcontainer/ bootstrap scripts are also involved.
---

## Reproduction Process

### Environment Setup

I cloned the repo using git clone https://github.com/Hanaahmed12/carlos.git and opened it in VS Code. Used VS Code global search to locate the relevant files.

### Steps to Reproduce

Start the CARLOS application (foreground Tomcat smoke test)

Watch the startup logs

Notice the warning: "eForm image directory missing - skipping deployment"

Check /var/lib/OscarDocument/oscar/eform/images/ - it doesn't exist

Open an eForm that uses images - they show as broken

### Reproduction Evidence

Commit showing reproduction: Not yet - still searching for the warning location

Screenshots/logs: Not yet

My findings: Exact file: src/main/java/io/github/carlos_emr/carlos/eform/EFormAssetDeployer.java

Exact lines causing the bug:
if (!targetDir.isDirectory()) {
    logger.warn("eForm image directory does not exist: {}; skipping asset deployment", imageDir);
    return;
}
Branch link: https://github.com/Hanaahmed12/carlos/tree/fix-issue-2182-eform-image-directory
---

## Solution Approach

### Analysis

The root cause is that the system expects a directory to exist (/var/lib/OscarDocument/oscar/eform/images/) but never creates it. Instead of creating it automatically, the code just logs a warning and skips deploying the images. This is a defensive programming gap.

### Proposed Solution

Add mkdir -p /var/lib/OscarDocument/oscar/eform/images/ in the bootstrap script before the eForm deployment runs. Also improve the warning message to say "Directory created automatically" instead of just "missing."

### Implementation Plan

Using UMPIRE framework (adapted):

Understand: When CARLOS starts, it tries to deploy eForm images but fails because the target directory doesn't exist.

Match: Other projects handle this by checking for directory existence and creating it if missing (idempotent initialization).

Plan:

Open EFormAssetDeployer.java in src/main/java/io/github/carlos_emr/carlos/eform/
Find the if (!targetDir.isDirectory()) block in afterPropertiesSet()

Add mkdir -p /var/lib/OscarDocument/oscar/eform/images/

Replace the return with targetDir.mkdirs() to auto-create the directory
Log an info message confirming creation instead of a warning
Test by running the devcontainer smoke test

Test by running the devcontainer and verifying directory exists and images deploy

Implement: Will link branch once created

Review: Check if change follows CARLOS contribution guidelines (need to read CONTRIBUTING.md)

Evaluate: Run the smoke test again - warning should be gone or changed, directory should exist, eForm images should render


---

## Testing Strategy

### Unit Tests

- [ ] Test case 1: [Description]
- [ ] Test case 2: [Description]
- [ ] Test case 3: [Description]

### Integration Tests

Run full Tomcat startup, verify no "directory missing" warning

Verify directory exists after startup

Verify eForm images actually appear in a real eForm

### Manual Testing

Run the devcontainer, check the directory path, open an eForm that previously had broken images

---

## Implementation Notes

### Week [1] Progress

Just getting started - cloned the fork, reading through issues, trying to locate the exact files. Need to search the codebase for the warning message.

### Week [Y] Progress

[Continue documenting as you work]

### Code Changes

- **Files modified:** src/main/java/io/github/carlos_emr/carlos/eform/EFormAssetDeployer.java
- **Key commits:** None yet
- **Approach decisions:** Will check if maintainers reply to my comment before opening PR

---

## Pull Request

PR Link: Not yet submitted

PR Description: Will adapt this template once PR is ready

Maintainer Feedback:

Pending - no reply to my comment on issue #2182 yet

Status: Researching / Locating files

---

## Learnings & Reflections

### Technical Skills Gained

Not yet - still in planning phase


### Challenges Overcome

Finding the exact file location has been harder than expected need to get better at searching large codebases

### What I'd Do Differently Next Time

Maybe ask for file location earlier instead of guessing

---

## Resources Used

Issue 2182 on carlos-emr/carlos

CARLOS README and CONTRIBUTING.md (need to read more thoroughly)