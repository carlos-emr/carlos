# Spring Method Security Proof of Concept

CARLOS EMR can run Spring Security 7 method-security interceptors in front of
Spring-managed Struts 2 action beans. The initial proof of concept applies
`@PreAuthorize` to `SecurityDelete2Action#execute()` while keeping the existing
manual privilege check as defense in depth during rollout.

## Feasibility findings

- Spring method security works with Struts 2 actions when the action is obtained
  from the Struts Spring object factory as a Spring bean, not constructed as a
  plain Java object.
- Class-based proxies are required because Struts 2 actions generally extend
  `ActionSupport` directly rather than implementing action-specific interfaces.
- CARLOS session authorization can remain the source of truth. The
  `carlosMethodSecurity` expression helper resolves the current Struts request,
  obtains `LoggedInInfo`, and delegates to `SecurityInfoManager.hasPrivilege(...)`.
- No patient or provider PHI is exposed by the helper; denied access returns
  `false` to Spring Security and lets the interceptor raise the access-denied
  exception before the action body executes.

## Required Spring configuration

The application context now registers `MethodSecurityConfig`, which enables:

```java
@EnableMethodSecurity(prePostEnabled = true, proxyTargetClass = true)
```

The POC also adds Spring Security's `spring-security-core` and
`spring-security-config` modules, and updates Spring Security artifacts to
7.0.5 because 7.0.4 has published advisories in `spring-security-config`.

## Struts action requirements

For an action to be protected before `execute()` runs:

1. The action must be a Spring bean.
2. The bean must be prototype-scoped unless the action is proven stateless.
3. The Struts action mapping must resolve to that Spring bean so Struts invokes
   the proxied instance.
4. The secured method must be non-final and public.
5. The Struts package must map Spring Security's `AccessDeniedException` to the
   same user-facing security error result used by legacy `SecurityException`
   handling. Method security still blocks before the action body runs; the
   mapping only keeps the denied-access response consistent with existing
   CARLOS behavior.
6. The method should retain the existing explicit `SecurityInfoManager` check
   until the migration has enough coverage to remove manual checks safely.

`SecurityDelete2Action` uses a bean name equal to its Struts `class` value so the
current Struts mapping can resolve the Spring bean without changing the URL or
result configuration.

## Rollout considerations

- Add one focused proxy test for every migrated action to prove
  `@PreAuthorize` denies access before action side effects run.
- When migrating actions in another Struts package, add or verify that package's
  `AccessDeniedException` mapping before enabling method security there.
- Keep expressions small and auditable by delegating to `carlosMethodSecurity`
  rather than embedding request/session lookup in SpEL.
- Prefer simple privilege expressions such as:

```java
@PreAuthorize("@carlosMethodSecurity.hasPrivilege('_admin', 'w')")
```

- Actions with patient-specific checks will need an expression helper overload
  that accepts or derives the demographic number before those checks are moved
  to `@PreAuthorize`.
