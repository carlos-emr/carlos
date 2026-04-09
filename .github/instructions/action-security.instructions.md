---
description: "Struts 2Action security and structure patterns"
applyTo: "**/*2Action.java"
---

# Struts2 2Action Rules

## MANDATORY Security Check (FIRST operation in every method)

```java
LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
if (!securityInfoManager.hasPrivilege(loggedInInfo, "_objectname", "r" /* or "w"/"d" per operation */, null)) {
    throw new SecurityException("missing required security object: _objectname");
}
```

## Required Imports

```java
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.ActionSupport;
```

Do NOT use legacy `com.opensymphony.xwork2.*` imports.

## Spring Integration

Use `SpringUtils.getBean()` for dependency injection:
```java
private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
```

## Package Namespace

Use `io.github.carlos_emr.carlos.*` for all new code. NOT `org.oscarehr.*`.

## Struts Configuration

Add new action mappings to the appropriate domain-specific `struts-*.xml` file (not `struts.xml`).
