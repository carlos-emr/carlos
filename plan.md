# Plan: Remove Apache XML-RPC 1.2 dependency and replace with Java 21 HttpClient

## Goal
Remove the `xmlrpc:xmlrpc:1.2-b1` Maven dependency (ancient, unmaintained Apache XML-RPC library)
and replace it with a lightweight ~160-line XML-RPC client using Java 21's built-in `HttpClient` and
DOM parser. Also delete confirmed dead code that exists solely because of this library.

---

## Scope of changes

| File | Action | Risk |
|------|--------|------|
| `SimpleXmlRpcClient.java` | **CREATE** — replacement XML-RPC client | Medium |
| `XmlRpcFaultException.java` | **CREATE** — exception with fault code | Low |
| `RxDrugRef.java` | **MODIFY** — new imports, rewrite 2 private methods, delete dead methods | Medium |
| `RxDrugData.java` | **MODIFY** — delete `getComponentsFromDrugCode()` (compile fix) | Low |
| `TimingOutCallback.java` | **DELETE** — zero callers anywhere | Zero |
| `efmformmanagerdownload.jsp` | **DELETE** — orphaned, broken (`server_url=""`) | Zero |
| `pom.xml` | **MODIFY** — remove xmlrpc dependency | Low |
| Lock files | **REGENERATE** via `make lock` | Low |

---

## Step 1: Create `SimpleXmlRpcClient.java`

**Path:** `src/main/java/io/github/carlos_emr/carlos/prescript/util/SimpleXmlRpcClient.java`

A minimal XML-RPC 1.0 client with three responsibilities:
1. Serialize Java objects into XML-RPC `<methodCall>` request XML
2. POST the request via `java.net.http.HttpClient`
3. Parse the `<methodResponse>` XML back into Java objects

### Design decisions

**HttpClient sharing:** Use a `static final HttpClient` shared across all instances. `HttpClient` is
thread-safe by design, and creating one per RPC call would spawn unnecessary thread pools.

**Proxy support:** Use `ProxySelector.getDefault()` explicitly — `HttpClient` does NOT automatically
read `http.proxyHost` system properties without this. This replaces the old proxy-branching logic
in `callWebserviceLite()` (which chose between `XmlRpcClient` and `XmlRpcClientLite`).

**DocumentBuilderFactory:** Create per-call inside `parseResponse()`. `DocumentBuilderFactory` is NOT
thread-safe per JDK docs, so sharing a static instance would require synchronization. Per-call
creation is negligible overhead for occasional RPC calls.

**XXE protection:** MANDATORY. Set these features on every `DocumentBuilderFactory`:
```java
factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
```

### Type mapping (serialization — Java → XML-RPC)

Only 4 types are sent as params in live code, but we handle all standard types for robustness:

| Java type | XML-RPC type | Used in live code? |
|-----------|-------------|-------------------|
| `String` | `<string>` | Yes — drug names, DINs, IDs |
| `Integer` | `<int>` | Yes — `minimum_significance` |
| `Boolean` | `<boolean>` (0/1) | Yes — `boolVal` |
| `Vector` | `<array><data>` | Yes — ATC lists, drug lists |
| `Double` | `<double>` | No |
| `Hashtable` | `<struct>` | No (only in dead tagCreator code) |

**XML escaping:** `serializeValue()` must escape `&`, `<`, `>`, `"`, `'` in all String values.
Method names are hardcoded constants (never user input) so don't need escaping.

### Type mapping (deserialization — XML-RPC → Java)

| XML-RPC type | Java type | Used in live responses? |
|-------------|-----------|------------------------|
| `<string>` | `String` | Yes |
| `<int>` / `<i4>` | `Integer` | Yes (inside structs) |
| `<boolean>` | `Boolean` | Yes (inside structs) |
| `<array>` | `Vector` | Yes — most common return type |
| `<struct>` | `Hashtable` | Yes — drug data dictionaries |
| `<double>` | `Double` | Unlikely but possible |
| `<dateTime.iso8601>` | `String` (passthrough) | No |
| `<base64>` | `byte[]` via `java.util.Base64` | No |
| bare `<value>text</value>` | `String` | Possible (XML-RPC spec) |

**Critical:** Must return `Vector` for arrays and `Hashtable` for structs — callers cast to these.

### Fault handling

XML-RPC faults come as `<fault><value><struct>` with `faultCode` (int) and `faultString` (string).
Parse into `XmlRpcFaultException` with a public `code` field, matching the old `XmlRpcException.code`.

### Imports needed

All from JDK — no external dependencies:
- `java.io.StringReader`
- `java.net.ProxySelector`, `java.net.URI`
- `java.net.http.HttpClient`, `HttpRequest`, `HttpResponse`
- `java.time.Duration`
- `java.util.Base64`, `Hashtable`, `Map`, `Vector`
- `javax.xml.parsers.DocumentBuilderFactory`, `ParserConfigurationException`
- `org.w3c.dom.Document`, `Element`, `Node`, `NodeList`
- `org.xml.sax.InputSource`

### Estimated size: ~160 lines

---

## Step 2: Create `XmlRpcFaultException.java`

**Path:** `src/main/java/io/github/carlos_emr/carlos/prescript/util/XmlRpcFaultException.java`

Simple exception class with public `int code` field, replacing `org.apache.xmlrpc.XmlRpcException`.
The `code` field is checked in `callWebserviceLite()` line 483: `((XmlRpcException) exception).code == 0`.

```java
public class XmlRpcFaultException extends Exception {
    public final int code;
    public XmlRpcFaultException(int code, String message) {
        super(message);
        this.code = code;
    }
}
```

~10 lines including copyright header.

---

## Step 3: Modify `RxDrugRef.java`

### 3a. Replace imports (lines 42-45)

**Remove:**
```java
import org.apache.xmlrpc.Base64;        // only used in deprecated get_drug_html, get_product_CPI
import org.apache.xmlrpc.XmlRpcClient;     // replaced by SimpleXmlRpcClient
import org.apache.xmlrpc.XmlRpcClientLite; // replaced by SimpleXmlRpcClient
import org.apache.xmlrpc.XmlRpcException;  // replaced by XmlRpcFaultException
```

**Also remove** (only used by dead tagCreator methods):
```java
import java.text.SimpleDateFormat;  // line 47
import java.util.Date;              // line 48
```

**Keep all other imports** — verified each is used by live code:
- `Enumeration` — `getInteractions()` line 794
- `HashMap` — `verify()` line 306
- `Hashtable`, `List`, `Map`, `Vector` — used throughout

### 3b. Rewrite `callWebservice()` (lines 456-468)

Replace body to use `SimpleXmlRpcClient` instead of `XmlRpcClient`:

```java
private Object callWebservice(String procedureName, Vector params) {
    MiscUtils.getLogger().debug("#CALLDRUGREF-" + procedureName);
    Object object = null;
    try {
        SimpleXmlRpcClient server = new SimpleXmlRpcClient(server_url);
        object = server.execute(procedureName, params);
    } catch (XmlRpcFaultException exception) {
        logger.error("JavaClient: XML-RPC Fault #" + exception.code, exception);
    } catch (Exception exception) {
        logger.error("JavaClient: ", exception);
    }
    return object;
}
```

**Error handling preserved:** Swallows ALL exceptions, returns null on failure. Matches original.

### 3c. Rewrite `callWebserviceLite()` (lines 470-491)

Replace body — proxy branching eliminated (HttpClient handles it):

```java
private Object callWebserviceLite(String procedureName, Vector params) throws Exception {
    Object object = null;
    try {
        SimpleXmlRpcClient server = new SimpleXmlRpcClient(server_url);
        object = server.execute(procedureName, params);
    } catch (Exception exception) {
        if (exception instanceof XmlRpcFaultException && ((XmlRpcFaultException) exception).code == 0) {
            logger.error("JavaClient: XML-RPC Fault. NoResultException thrown for procedure: {} with parameters {}", procedureName, params);
        } else {
            logger.error("JavaClient: XML-RPC Fault ", exception);
            throw new Exception("JavaClient: XML-RPC Fault", exception);
        }
    }
    return object;
}
```

**Error handling preserved exactly:**
- Fault code 0: log + suppress + return null
- All other exceptions: log + re-throw wrapped in `new Exception`

### 3d. Delete dead methods

**`listComponents`** (lines 356-363): `@Deprecated`, only caller is dead `RxDrugData.getComponentsFromDrugCode()`.

**Everything from line 494 to line 770** — the entire "DRUGREF API" block and tag creators:

| Method | Lines | Why dead |
|--------|-------|----------|
| `list_sources` | 513-516 | `@Deprecated` stub, 0 callers |
| `get_source_tag` | 524-527 | `@Deprecated` stub, 0 callers |
| `list_drugs` | 551-560 | All callers commented out |
| `tagCreatorEx` (Date overload) | 563-580 | 0 callers (built tags for dead stubs) |
| `tagCreatorEx` (no-Date overload) | 582-594 | 0 callers |
| `tagCreator` (Date overload) | 608-621 | 0 callers |
| `tagCreator` (no-Date overload) | 634-642 | 0 callers |
| `get_drug_html` | 652-655 | `@Deprecated` stub, 0 callers, uses `Base64` type |
| `list_products` | 671-674 | `@Deprecated` stub, 0 callers |
| `get_product` | 698-701 | `@Deprecated` stub, 0 callers |
| `get_product_CPI` | 711-714 | `@Deprecated` stub, 0 callers, uses `Base64` type |
| `list_interactions` | 734-737 | `@Deprecated` stub, 0 callers |
| `list_conditions` | 743-746 | `@Deprecated` stub, 0 callers |
| `list_drugs_for_indication` | 751-754 | `@Deprecated` stub, 0 callers |
| `list_references` | 767-770 | `@Deprecated` stub, 0 callers |

Also delete the associated comments/JavaDoc for the `////DRUGREF API` section header (line 494)
and the `//////DRUGREF Second Gen API` header (line 771 — moves to just before `removeNullFromVector`).

### 3e. Methods that STAY (lines 773-826)

These are live methods with callers — DO NOT DELETE:
- `removeNullFromVector` — used by `getInteractions()`
- `getInteractions` — live (called from prescription interaction checking)
- `getAlergyWarnings` — live
- `getAllergyClasses` — live
- `getInactiveDate` — live

---

## Step 4: Modify `RxDrugData.java`

**Delete `getComponentsFromDrugCode()`** (lines 719-740, including JavaDoc).

**Reason:** This `@Deprecated` method with 0 callers calls `d.listComponents(drugCode)` which we're
deleting from `RxDrugRef`. Without this deletion, `RxDrugData` won't compile.

No other methods in `RxDrugData` call methods we're deleting. Verified that:
- `getDistinctForms()` calls `d.getDistinctForms()` — we're KEEPING that in RxDrugRef
- All other `RxDrugRef` method calls in RxDrugData target methods we're keeping

---

## Step 5: Delete `TimingOutCallback.java`

**Path:** `src/main/java/io/github/carlos_emr/carlos/prescript/util/TimingOutCallback.java`

**Verification:** Searched every `.java`, `.jsp`, `.jspf`, `.js.jsp`, `.xml`, `.json`, `.properties`
file. Zero callers. Even the generated JavaDoc "class-use" page says "No usage". Implements
`org.apache.xmlrpc.AsyncCallback` which is being removed.

---

## Step 6: Delete `efmformmanagerdownload.jsp`

**Path:** `src/main/webapp/eform/efmformmanagerdownload.jsp`

**Verification:**
- Zero references from any `.java`, `.jsp`, `.xml`, Struts config, or `web.xml`
- Only reference is self-link on line 58: `href="efmformmanagerdownload.jsp?grid=..."`
- Contains its own copy of `callWebserviceLite()` with `server_url = ""` (permanently broken)
- Imports `org.apache.xmlrpc.*` (line 40) — won't compile without the dependency

---

## Step 7: Remove xmlrpc from `pom.xml`

Delete lines 610-615 (the dependency block) and line 617 (trailing comment):
```xml
        <!-- apache xmlrpc -->
        <dependency>
            <groupId>xmlrpc</groupId>
            <artifactId>xmlrpc</artifactId>
            <version>1.2-b1</version>
        </dependency>

        <!-- apache xmlrpc -->
```

---

## Step 8: Update lock files

Run `make lock` to regenerate `dependencies-lock.json` and `dependencies-lock-modern.json`.

---

## Step 9: Build and verify

Run `make install` to confirm:
- All 3 files using `org.apache.xmlrpc` are either deleted or migrated
- No compilation errors
- WAR deploys successfully

---

## Verification checklist

- [ ] `grep -r "org.apache.xmlrpc" src/` returns zero matches
- [ ] `SimpleXmlRpcClient` has XXE protection enabled
- [ ] `SimpleXmlRpcClient` uses `ProxySelector.getDefault()` for proxy support
- [ ] `callWebservice()` error handling: swallow all, return null
- [ ] `callWebserviceLite()` error handling: swallow fault code 0, re-throw others
- [ ] `XmlRpcFaultException.code` field matches old `XmlRpcException.code` usage
- [ ] XML escaping applied to all string parameter values
- [ ] `Vector` returned for XML-RPC arrays (not `ArrayList`)
- [ ] `Hashtable` returned for XML-RPC structs (not `HashMap`)
- [ ] `RxDrugData.getComponentsFromDrugCode()` deleted (prevents compile error)
- [ ] Lock files regenerated
- [ ] Build succeeds

---

## What we are NOT changing

- **Public API of RxDrugRef** — all live public methods keep their exact signatures
- **Caller code** — nothing that calls RxDrugRef needs to change
- **RxDrugData** — only the dead `getComponentsFromDrugCode()` method is removed
- **search2.jsp** — uses `RxDrugRef.list_drug_element2()` which we're keeping
- **`list_drugs()`** — deleting this dead method, but its callers are already commented out
- **`getDistinctForms()`** — keeping in RxDrugRef (used by deprecated RxDrugData wrapper)

---

## Risk assessment

**Highest risk:** `SimpleXmlRpcClient` XML parsing must exactly match the old library's type mapping.
If the drugref server returns an unexpected type structure, responses could be misinterpreted.

**Mitigation:** The XML-RPC 1.0 spec has only 8 types. We handle all of them. The old xmlrpc 1.2-b1
library is a faithful implementation of the same spec. Type mapping is deterministic.

**Second highest risk:** Proxy behavior change. The old code had explicit branching; now it's implicit
via `ProxySelector.getDefault()`.

**Mitigation:** `ProxySelector.getDefault()` reads the same `http.proxyHost` / `http.proxyPort`
system properties that `java.net.URLConnection` (used by old `XmlRpcClient`) reads. Behavior
should be identical.
