# Struts Web Endpoints and JSP Placement

## Summary

CARLOS no longer treats JSPs as public entrypoints. New web pages should be exposed through Struts actions with extensionless routes, and the rendered JSPs should live under `src/main/webapp/WEB-INF/jsp/**`.

This is the default pattern for login pages, forms, eForms, provider views, and other new page work.

## Current Routing Rules

- Struts actions are extensionless. Use `/login`, `/index`, `/form/setupSelect`, `/eform/efmshowform_data`, not `.do`.
- Public JSPs are not the endpoint contract. JSPs under `WEB-INF` are internal views only.
- `struts.xml` is the parent config. Add new actions to the appropriate `struts-*.xml` module file, not to the parent file.
- The root app path is handled by `RootEntryRedirectFilter`, which forwards `/context/` to the public `index` action. Users should not need to browse to `/index` directly.
- Struts request processing in `web.xml` is split into `StrutsPrepareFilter` and `StrutsExecuteFilter`, with `LoggedInUserFilter` between them to rebuild authenticated request context before action execution.
- Migrated section-home pages that used to rely on public `index.jsp` resolution may need a small allowlisted compatibility filter to preserve clean directory URLs such as `/administration/`.

## How To Add a New Page

### 1. Put the view under `WEB-INF`

- Create the JSP under the correct internal folder, usually `src/main/webapp/WEB-INF/jsp/<domain>/...`.
- Keep fragments and helper JSPFs internal as well.
- Do not add new public JSP files under `src/main/webapp/`.

### 2. Add a Struts action in the right module

- Add the action mapping to the domain module, such as `struts-login.xml`, `struts-form.xml`, `struts-eform.xml`, or `struts-provider.xml`.
- Use an extensionless action name.
- Point success/error results at internal `WEB-INF` JSPs or at other extensionless action paths.
- Do not point results at public JSP paths.

Example:

```xml
<action name="admin/example" class="io.github.carlos_emr.carlos.admin.gate.ViewExample2Action">
    <result name="success">/WEB-INF/jsp/admin/example.jsp</result>
</action>
```

### 3. Add the action class

- Use the normal `*2Action` naming pattern.
- Default to `org.apache.struts2.ActionSupport`.
- Enforce security in the action before forwarding to the view.
- For pure page rendering, gate the request and then forward internally.

Example:

```java
public class ViewExample2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager =
            SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();

        if (!securityInfoManager.hasPrivilege(
                LoggedInInfo.getLoggedInInfoFromSession(request),
                "_example",
                SecurityInfoManager.READ,
                null)) {
            throw new SecurityException("missing required sec object (_example)");
        }

        return SUCCESS;
    }
}
```

### 4. Update callers to the action path

- Forms, links, redirects, JavaScript, popup URLs, and Java-generated HTML should target the action path, not the JSP.
- Internal forwards should use `/WEB-INF/jsp/...` only when they are deliberately internal server-side dispatches.
- New code should not generate `.do` URLs.

## Choosing the Right Pattern

### Use an explicit action by default

This is the normal choice for new work. It is the right pattern when:

- the page has its own security rules
- the page mixes view and workflow behavior
- the page should be easy to grep and review
- the route should be individually testable

### Use a route registry only for controlled families of pages

The repo now has two migrated patterns:

- `form/*` uses a wildcard action plus a strict allowlist in `FormViewRoutes`
- `eform/*` uses explicit action names backed by `EFormViewRoutes`

For new work, prefer the eForm-style explicit route unless there is a strong reason to support a controlled family of many legacy names. If a wildcard is used, it must resolve only an allowlisted set of internal views, not arbitrary files from the filesystem.

### Preserve clean section-home URLs when they were part of the old contract

If a page used to be publicly reachable through a directory route because the container resolved `index.jsp`, do not assume `/section/index` is an acceptable new public URL.

When migrating one of these pages:

- check nav links, popup helpers, menu builders, generated URLs, and relative links for `/section/` or `/section`
- keep the clean route if that was the historical user-facing contract
- preserve it through a small explicit compatibility mapping, not a generic rewrite
- keep the real JSP behind `WEB-INF` and the migrated action as the internal target

Current example:

- `/administration/` remains the public section-home route
- `/administration/index` is only the migrated internal action target

## Security and Filter Expectations

- Page actions must enforce privilege checks themselves; do not rely only on JSP tags or scriptlets.
- Use `GET` and `HEAD` only for read-only view pages.
- Keep state-changing operations on `POST`, `PUT`, `DELETE`, or `PATCH` so CSRFGuard protects them.
- `LoggedInInfo` should be available from the authenticated session bootstrap. Do not recreate it ad hoc in gate actions.
- Static assets are excluded from Struts by `struts.action.excludePattern`; do not create fake actions for files that should remain static.

## Testing Expectations

For new routed pages, add focused tests that cover:

- successful render for an authorized request
- denied access when the required privilege is missing
- `404` for unknown routed views, where applicable
- method restrictions when the page is read-only
- route generation or registry behavior if the page participates in a mapped family

## Migration Rules for Old JSP-Based Flows

- Move the JSP to `WEB-INF/jsp/...`.
- Replace direct `/something.jsp` links with an extensionless action route.
- Replace old `.do` links with the extensionless path.
- If the old page was only a thin wrapper around a JSP, create a gate action instead of keeping a public JSP.
- If the old page name is important for user workflows, preserve the recognizable action name and change only the internal rendering location.
- If the old page was a section home served through `index.jsp`, explicitly decide whether the clean directory route must continue to work and inspect nav/menu/relative callers before finishing the migration.
