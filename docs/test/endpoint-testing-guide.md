# REST & SOAP Endpoint Testing Guide

## Overview

CARLOS EMR provides two test base classes for HTTP-level endpoint testing using CXF's **local transport** — an in-memory transport that exercises the full CXF pipeline (routing, serialization, interceptors, content negotiation) without opening TCP sockets.

| Base Class | Purpose | Transport | Tags |
|---|---|---|---|
| `CarlosRestTestBase` | JAX-RS REST endpoints | CXF local | `endpoint`, `rest` |
| `CarlosSoapTestBase` | JAX-WS SOAP endpoints | CXF local | `endpoint`, `soap` |

Both extend `CarlosUnitTestBase` — no Spring context or database needed. Tests run in milliseconds.

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
- **Jackson ObjectMapper**: Configured to match production (JAXB + Jackson annotation introspector pair).

### Testing POST/PUT

```java
@Test
void shouldReturn200_whenPostingData() {
    MyTransferObject input = new MyTransferObject();
    input.setName("test");

    Response response = request().path("/mypath")
        .post(jakarta.ws.rs.client.Entity.json(input));

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

## Running Endpoint Tests

```bash
# All endpoint tests
mvn test -Dgroups="endpoint"

# REST endpoint tests only
mvn test -Dgroups="endpoint,rest"

# SOAP endpoint tests only
mvn test -Dgroups="endpoint,soap"

# Specific test class
mvn test -Dtest=AllergyServiceEndpointTest
mvn test -Dtest=SystemInfoWsEndpointTest

# Via make
make install --run-unit-tests
```

## Architecture

```
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
