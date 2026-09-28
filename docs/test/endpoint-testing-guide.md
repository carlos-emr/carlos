# REST & SOAP Endpoint Testing Guide

## Overview

CARLOS EMR provides two test base classes for endpoint message testing using CXF's **local transport** — an in-memory transport that exercises CXF routing, serialization, interceptors and content negotiation without opening TCP sockets.

| Base Class | Purpose | Transport | Tags |
|---|---|---|---|
| `CarlosRestTestBase` | JAX-RS REST endpoints | CXF local | `endpoint`, `rest` |
| `CarlosSoapTestBase` | JAX-WS SOAP endpoints | CXF local | `endpoint`, `soap` |

Both extend `CarlosUnitTestBase` — no Spring context or database needed. The tests require no running application server.

## When to Use What

| Scenario | Base Class |
|---|---|
| Test JSON/XML serialization round-trips | `CarlosRestTestBase` |
| Test HTTP status codes and routing | `CarlosRestTestBase` |
| Test SOAP envelope processing | `CarlosSoapTestBase` |
| Test business logic with mocked DAOs | `CarlosUnitTestBase` |
| Test with real database | `CarlosTestBase` |
| Test Struts2 Actions | `CarlosWebTestBase` |

## REST Endpoint Testing

### Quick Start

```java
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("MyService REST endpoint tests")
class MyServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private SomeManager mockManager;

    @Override
    protected Object getServiceBean() {
        MyService service = new MyService();
        injectDependency(service, "someManager", mockManager);
        return service;
    }

    @Test
    void shouldReturn200_whenGetEndpoint() {
        when(mockManager.getData(any(), eq(1))).thenReturn(testData);

        Response response = request().path("/mypath")
            .query("id", 1)
            .get();

        assertThat(response.getStatus()).isEqualTo(200);
        MyResponse body = response.readEntity(MyResponse.class);
        assertThat(body.getData()).isNotNull();
    }
}
```

### Key Points

- **`getServiceBean()`**: Return the JAX-RS service instance with mocked dependencies injected via `injectDependency()`.
- **`request()`**: Returns a fresh CXF `WebClient` copy reset to the base address. Always use `request()` instead of `client` directly to avoid path accumulation across calls.
- **`mockLoggedInInfo`**: Pre-injected into the CXF message — `AbstractServiceImpl.getLoggedInInfo()` works automatically.
- **`mockServletRequest`**: Accessible if you need to set custom request headers or parameters.
- **Jackson ObjectMapper**: Uses the production Jackson/JAXB annotation priority and `SmartDateModule`; XML requests use `JAXBElementProvider`.

### Testing POST/PUT

```java
@Test
void shouldReturn200_whenPostingData() {
    MyTransferObject input = new MyTransferObject();
    input.setName("test");

    Response response = request().path("/mypath")
        .post(input);

    assertThat(response.getStatus()).isEqualTo(200);
}
```

## SOAP Endpoint Testing

### Quick Start

```java
@Tag("unit")
@Tag("endpoint")
@Tag("soap")
@DisplayName("MyWs SOAP endpoint tests")
class MyWsEndpointTest extends CarlosSoapTestBase {

    @Override
    protected Object getServiceBean() {
        return new MyWs();
    }

    @Override
    protected Class<?> getServiceInterface() {
        return MyWs.class;
    }

    @Test
    void shouldReturnResult_viaSoap() {
        MyWs proxy = createClient(MyWs.class);

        String result = proxy.someMethod();

        assertThat(result).isEqualTo("expected");
    }
}
```

### Key Points

- **`getServiceBean()`**: Return the `@WebService` annotated service instance.
- **`getServiceInterface()`**: Return the `@WebService` class (used by both server and client factory).
- **`createClient(Class<T>)`**: Creates a typed JAX-WS client proxy — call service methods directly.
- **WS-Security**: Bypassed by default. The test interceptor provides `LoggedInInfo` directly.
- **`mockLoggedInInfo`**: Available for services that call `AbstractWs.getLoggedInInfo()`.

### Injecting Dependencies for Authenticated SOAP Services

```java
@Override
protected Object getServiceBean() {
    DemographicWs service = new DemographicWs();
    injectDependency(service, "demographicManager", mockDemographicManager);
    return service;
}
```

## Isolation and mock setup

Each test instance gets a unique local address and its own CXF bus. Teardown
closes client transports, destroys the server and shuts down that bus, including
when setup or the test fails. The base classes initialize `@Mock` fields before
calling `getServiceBean()`; do not also call `MockitoAnnotations.openMocks()`.

CXF local dispatch runs on the calling test thread so Mockito static mocks for
`SpringUtils` and audit helpers remain in scope. Register every service or
converter dependency with `registerMock()` before invoking the endpoint. Inject
and stub the real service's permission checks explicitly; the harness does not
grant privileges automatically. Include denied-access tests that verify the DAO
was not called, as well as positive payload assertions.

Use try-with-resources for `Response` objects. For POST/PUT, pass the payload
itself to CXF `WebClient.post()`/`put()`, not a JAX-RS `Entity` wrapper. Use
`.query()` for query parameters, not a question mark embedded in `.path()`.

## Authentication Handling

Both base classes use a CXF `Phase.PRE_INVOKE` interceptor that:

1. Places a `MockHttpServletRequest` (with `MockHttpSession`) into the CXF message at `AbstractHTTPDestination.HTTP_REQUEST`
2. Sets `LoggedInInfo` on both session and request attributes using the production key pattern

This satisfies:
- `AbstractServiceImpl.getLoggedInInfo()` (REST) — reads from `PhaseInterceptorChain.getCurrentMessage()`
- `AbstractWs.getLoggedInInfo()` (SOAP) — reads from `WebServiceContext.getMessageContext()`

To customize the authenticated user:
```java
when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
when(mockLoggedInInfo.getLoggedInProvider()).thenReturn(testProvider);
```

These tests bypass production OAuth/WS-Security authentication and do not start
a servlet container. They do not validate HTTP sockets, servlet filters, WAF
rules, Spring wiring, transaction boundaries or real database persistence. Use
integration/browser tests for those boundaries. A mocked authenticated identity
does not establish that the production authentication flow is secure.

## Running Endpoint Tests

```bash
# All endpoint tests; one fork is useful on memory-constrained machines
mvn test -Dtest.forkCount=1 -Dgroups="endpoint"

# REST endpoint tests only
mvn test -Dgroups="endpoint & rest"

# SOAP endpoint tests only
mvn test -Dgroups="endpoint & soap"

# Specific test class
mvn test -Dtest=AllergyServiceEndpointTest
mvn test -Dtest=SystemInfoWsEndpointTest
```

## Architecture

```text
CarlosUnitTestBase              (SpringUtils mocking, no Spring context)
  ├── CarlosRestTestBase        (CXF JAX-RS server + WebClient)
  │     └── *EndpointTest.java  (REST endpoint tests)
  └── CarlosSoapTestBase        (CXF JAX-WS server + proxy client)
        └── *EndpointTest.java  (SOAP endpoint tests)
```

The CXF local transport (`cxf-rt-transports-local`) provides a full CXF message processing pipeline in-memory. No TCP sockets are opened, no ports are allocated, and no embedded servlet container is started.

## Tag Conventions

| Tag | Meaning |
|---|---|
| `endpoint` | All REST and SOAP endpoint tests |
| `rest` | REST (JAX-RS) endpoint tests |
| `soap` | SOAP (JAX-WS) endpoint tests |
| `unit` | Fast tests (no database, no Spring context) |

## Testing the harnesses

```bash
mvn test -Dtest.forkCount=1 -Dtest=CarlosRestTestBaseTest,CarlosSoapTestBaseTest
```

These tests exercise request routing, JSON/date and XML round trips, injected
request identity, same-thread static mocks, SOAP faults and two independent
instances of the same test class. They also verify that closing one instance
leaves the other usable. Do not replace payload or error assertions with only
HTTP 200 checks, and do not mark failing endpoint examples disabled to make CI
pass. Reproduce the failure, determine whether the fixture or application is
wrong, and document any application defect with its regression test.

For XML requests, replace the default JSON `Accept` header rather than adding
another value:

```java
request().type(MediaType.APPLICATION_XML)
    .replaceHeader("Accept", MediaType.APPLICATION_XML).post(payload);
```

An unchecked service exception can surface as a client `ProcessingException`
with the original root cause when using direct local dispatch. Assert that cause
and verify that protected dependencies were not called; this does not prove that
a deployed HTTP endpoint maps the exception to 403. SOAP exceptions are tested
as SOAP faults. An empty SOAP return array may unmarshal as `null` because no
return elements were written; pair `isNullOrEmpty()` with a nonempty fixture that
checks the returned data.

The combined suite deliberately keeps the release branch's PDF permission and
XML external-entity security regressions. Removed legacy SHA-1 APIs are not
reintroduced to satisfy obsolete tests. LoginWs, DocumentWs, InboxService and
ResourceService examples removed from the source PR are not claimed as covered.
