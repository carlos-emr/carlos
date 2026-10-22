# Hardcoded-Credential False Positive Suppressions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Suppress 14 hardcoded-credential false-positive scanner alerts across 4 files using the codebase's established suppression conventions, closing the alerts in GitHub Code Scanning without changing any runtime behavior.

**Architecture:** Two suppression layers: `spotbugs-exclude.xml` for structural patterns (empty-string field clears, WS-Security protocol constants, UI mask sentinels) and per-site `@SuppressFBWarnings` / `// NOSONAR` / `// NOPMD` annotations for reviewed intentional code. Both layers require prose justification explaining why the finding is a false positive.

**Tech Stack:** Java 21, SpotBugs + Find Security Bugs, SonarCloud, PMD, `edu.umd.cs.findbugs.annotations.SuppressFBWarnings`

---

## Files

| Action | File |
|--------|------|
| Modify | `.github/spotbugs/spotbugs-exclude.xml` |
| Modify | `src/main/java/io/github/carlos_emr/carlos/login/LoginCheckLoginBean.java` |
| Modify | `src/main/java/io/github/carlos_emr/carlos/managers/SecurityManager.java` |
| Modify | `src/main/java/io/github/carlos_emr/carlos/lab/ca/all/pageUtil/LabUpload2Action.java` |

---

## Task 1: Add structural `HARD_CODE_PASSWORD` exclusions to spotbugs-exclude.xml

These five methods always trigger `HARD_CODE_PASSWORD` by structural pattern, not by defect:
`EmailManager.sanitizeEmailFields` (sets `""` — field clear), the three WS-Security
interceptor constructors/initializers (contain the string `"PasswordText"` — a protocol-type
constant), and `ConfigureFax2Action.isPasswordUnchanged` (compares against `"**********"` — a UI mask sentinel).

**Files:**
- Modify: `.github/spotbugs/spotbugs-exclude.xml`

- [ ] **Step 1: Add the new section to spotbugs-exclude.xml**

Open `.github/spotbugs/spotbugs-exclude.xml`. Find the closing `</FindBugsFilter>` tag at the
end of the file. Insert the following block immediately before it (after the last `</Match>` block):

```xml
    <!-- ================================================================
         HARD_CODE_PASSWORD FALSE POSITIVES
         SpotBugs HARD_CODE_PASSWORD fires whenever a String literal is
         passed to (or compared with) a method whose name contains
         "password". Three structural patterns recur in this codebase
         that are never real credentials:

           1. Field-clear pattern: setPassword("") clears an email/form
              field before sending. The empty literal is not a credential.

           2. WS-Security protocol-type constant: the string "PasswordText"
              (aliased as WSS4JConstants.PW_TEXT / WSConstants.PW_TEXT) is
              a WS-Security UsernameToken mechanism URI, not a password value.
              Any method that configures a WS-Security interceptor property
              map will trigger this detector.

           3. UI mask sentinel: PASSWORD_MASK_SENTINEL = "**********" is
              compared against a submitted form field to detect "unchanged"
              password submissions. It is not a stored or usable credential.

         Each entry below is method-scoped. If you encounter the same
         pattern in a new method, add a method-scoped entry here rather
         than annotating the call site.
         ================================================================ -->

    <!-- setPassword("") calls inside sanitizeEmailFields clear the email
         DTO's credential fields when encryption is not being used. -->
    <Match>
        <Bug pattern="HARD_CODE_PASSWORD"/>
        <Class name="io.github.carlos_emr.carlos.managers.EmailManager"/>
        <Method name="sanitizeEmailFields"/>
    </Match>

    <!-- properties.put(WSHandlerConstants.PASSWORD_TYPE, WSS4JConstants.PW_TEXT)
         configures the WS-Security UsernameToken mechanism. PW_TEXT is a
         protocol-type URI string, not a password value. -->
    <Match>
        <Bug pattern="HARD_CODE_PASSWORD"/>
        <Class name="io.github.carlos_emr.carlos.webserv.AuthenticationInWSS4JInterceptor"/>
        <Method name="initialize"/>
    </Match>

    <!-- Same WS-Security protocol-type constant set in the outbound interceptor
         constructor. SpotBugs uses <init> (bytecode name) for constructors. -->
    <Match>
        <Bug pattern="HARD_CODE_PASSWORD"/>
        <Class name="io.github.carlos_emr.carlos.utility.AuthenticationOutWSS4JInterceptor"/>
        <Method name="&lt;init&gt;"/>
    </Match>

    <!-- Same WS-Security protocol-type constant in the EDT billing client
         WS-Security outbound property map builder. -->
    <Match>
        <Bug pattern="HARD_CODE_PASSWORD"/>
        <Class name="io.github.carlos_emr.carlos.integration.ebs.client.ng.EdtClientBuilder"/>
        <Method name="newWSSOutInterceptorConfiguration"/>
    </Match>

    <!-- PASSWORD_MASK_SENTINEL = "**********" is the UI placeholder shown
         in password fields when a credential is already stored. This method
         checks whether a submitted value equals the sentinel (meaning the
         admin left the field blank/unchanged). It is not a credential comparison. -->
    <Match>
        <Bug pattern="HARD_CODE_PASSWORD"/>
        <Class name="io.github.carlos_emr.carlos.fax.admin.ConfigureFax2Action"/>
        <Method name="isPasswordUnchanged"/>
    </Match>
```

- [ ] **Step 2: Verify the XML is well-formed**

```bash
xmllint --noout .github/spotbugs/spotbugs-exclude.xml && echo "XML valid"
```

Expected output: `XML valid`

If `xmllint` is not available: `python3 -c "import xml.etree.ElementTree as ET; ET.parse('.github/spotbugs/spotbugs-exclude.xml'); print('XML valid')"`

- [ ] **Step 3: Commit**

```bash
git add .github/spotbugs/spotbugs-exclude.xml
git commit -m "fix: suppress HARD_CODE_PASSWORD structural false positives in spotbugs-exclude.xml

Five method-scoped entries cover the three recurring false-positive patterns:
empty-string field clears (EmailManager), WS-Security protocol-type constants
(AuthenticationInWSS4JInterceptor, AuthenticationOutWSS4JInterceptor,
EdtClientBuilder), and the fax admin UI mask sentinel (ConfigureFax2Action).
Section header documents each pattern so future developers don't re-suppress
individually."
```

---

## Task 2: Suppress LoginCheckLoginBean dummy-hash findings (SpotBugs + SonarCloud)

`MISSING_USER_DUMMY_PASSWORD_HASH` is a pre-computed BCrypt hash of a random decoy
password used solely to equalize timing of the missing-user authentication path
(prevents user enumeration via response-time differences). It has no usable plaintext.
SonarCloud `secrets:S8215` and `java:S2068` fire on the field declaration;
SpotBugs `HARD_CODE_PASSWORD` fires on the `missingUserDummySecurity()` method that
reads it.

**Files:**
- Modify: `src/main/java/io/github/carlos_emr/carlos/login/LoginCheckLoginBean.java`

- [ ] **Step 1: Add the SuppressFBWarnings import**

`LoginCheckLoginBean.java` does not currently import `SuppressFBWarnings`. Add it
after the existing imports (around line 54, after the last `import` statement):

Find this block:
```java
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
```

Replace with:
```java
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
```

- [ ] **Step 2: Annotate the field and add NOSONAR**

Find the field declaration (around line 121–126):
```java
    /**
     * Pre-computed BCrypt hash of a random decoy password, used only to equalize missing-user
     * authentication timing with the normal password-validation path.
     */
    private static final String MISSING_USER_DUMMY_PASSWORD_HASH =
            "{bcrypt}$2b$10$YzOXP.2axkRiYS07sVHWkuyvQjcuwR.bGeZd5WHQVJ23py57UES8C";
```

Replace with:
```java
    /**
     * Pre-computed BCrypt hash of a random decoy password, used only to equalize missing-user
     * authentication timing with the normal password-validation path.
     */
    @SuppressFBWarnings(value = "HARD_CODE_PASSWORD",
            justification = "BCrypt timing-equalization decoy: a pre-computed hash of a random "
                    + "throwaway password with no usable plaintext, used only to make "
                    + "missing-user paths take the same wall-clock time as real password checks")
    // NOSONAR java:S2068,secrets:S8215 — BCrypt decoy hash for timing equalization; not a usable credential
    private static final String MISSING_USER_DUMMY_PASSWORD_HASH =
            "{bcrypt}$2b$10$YzOXP.2axkRiYS07sVHWkuyvQjcuwR.bGeZd5WHQVJ23py57UES8C";
```

- [ ] **Step 3: Annotate the missingUserDummySecurity() method**

Find the method (around line 342–351):
```java
    /**
     * Builds a throwaway security record for the missing-user password validation path.
     *
     * @return Security object containing only the precomputed BCrypt dummy password hash
     */
    private static Security missingUserDummySecurity() {
        Security dummySecurity = new Security();
        dummySecurity.setPassword(MISSING_USER_DUMMY_PASSWORD_HASH);
        return dummySecurity;
    }
```

Replace with:
```java
    /**
     * Builds a throwaway security record for the missing-user password validation path.
     *
     * @return Security object containing only the precomputed BCrypt dummy password hash
     */
    @SuppressFBWarnings(value = "HARD_CODE_PASSWORD",
            justification = "Sets the BCrypt timing-equalization decoy hash; see MISSING_USER_DUMMY_PASSWORD_HASH")
    // BCrypt timing-equalization decoy — sets dummy hash, not a real credential
    private static Security missingUserDummySecurity() {
        Security dummySecurity = new Security();
        dummySecurity.setPassword(MISSING_USER_DUMMY_PASSWORD_HASH);
        return dummySecurity;
    }
```

- [ ] **Step 4: Compile to confirm no errors**

```bash
mvn compile -pl . -q 2>&1 | grep -E "ERROR|error:" | head -20
```

Expected: no output (clean compile). If errors appear, check that the import was added correctly and the annotation syntax matches existing `@SuppressFBWarnings` usages in the file.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/carlos_emr/carlos/login/LoginCheckLoginBean.java
git commit -m "fix: suppress HARD_CODE_PASSWORD/S2068/secrets:S8215 on BCrypt timing-equalization decoy

MISSING_USER_DUMMY_PASSWORD_HASH is a pre-computed BCrypt hash of a random
throwaway password used only to equalize missing-user authentication timing
and prevent user enumeration via response-time differences. It has no usable
plaintext. Annotated with @SuppressFBWarnings and // NOSONAR with justification."
```

---

## Task 3: Suppress SecurityManager policy-sentinel false positive

SpotBugs fires `HARD_CODE_PASSWORD` on `checkPasswordAgainstPrevious` because the
string `"0"` (a policy threshold meaning "zero past passwords to check") appears in a
method whose name contains "password". It is not a credential.

**Files:**
- Modify: `src/main/java/io/github/carlos_emr/carlos/managers/SecurityManager.java`

- [ ] **Step 1: Add the SuppressFBWarnings import**

`SecurityManager.java` does not currently import `SuppressFBWarnings`. Find the last
import line (around line 45, `import java.util.List;`) and add after it:

Find:
```java
import java.util.Date;
import java.util.List;
```

Replace with:
```java
import java.util.Date;
import java.util.List;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
```

- [ ] **Step 2: Annotate the method**

Find the method signature (around line 88):
```java
    public boolean checkPasswordAgainstPrevious(String newPassword, String providerNo) {
        //check previous passwords policy if the password is being changed
        String previousPasswordPolicy = CarlosProperties.getInstance().getProperty("password.pastPasswordsToNotUse", "0");
```

Add the annotation and comment immediately before the method:

Find:
```java
    public boolean checkPasswordAgainstPrevious(String newPassword, String providerNo) {
```

Replace with:
```java
    @SuppressFBWarnings(value = "HARD_CODE_PASSWORD",
            justification = "\"0\" is a policy threshold sentinel (zero past passwords to check), "
                    + "not a credential; compared against the pastPasswordsToNotUse config property")
    // "0" = policy threshold, not a password — prevents false positive on method name containing "password"
    public boolean checkPasswordAgainstPrevious(String newPassword, String providerNo) {
```

- [ ] **Step 3: Compile to confirm no errors**

```bash
mvn compile -pl . -q 2>&1 | grep -E "ERROR|error:" | head -20
```

Expected: no output.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/io/github/carlos_emr/carlos/managers/SecurityManager.java
git commit -m "fix: suppress HARD_CODE_PASSWORD false positive in SecurityManager

\"0\" in checkPasswordAgainstPrevious is a policy threshold sentinel
(pastPasswordsToNotUse config default), not a credential. SpotBugs fires
because the string appears in a method whose name contains \"password\"."
```

---

## Task 4: Suppress LabUpload2Action HardCodedCryptoKey false positive

PMD `HardCodedCryptoKey` fires on `Cipher.getInstance("RSA/ECB/PKCS1Padding")` at line 236
because the string argument contains cipher algorithm/mode details. No cryptographic key
material is hardcoded — the algorithm string is a standard JCA transformation name. The
RSA private key is loaded from the server keystore at runtime.

**Files:**
- Modify: `src/main/java/io/github/carlos_emr/carlos/lab/ca/all/pageUtil/LabUpload2Action.java`

- [ ] **Step 1: Add the NOPMD suppression comment**

Find line 236 (inside the lab upload decrypt block):
```java
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
```

Replace with:
```java
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding"); // NOPMD HardCodedCryptoKey — algorithm name string, not a key; RSA private key loaded from server keystore at runtime
```

- [ ] **Step 2: Compile to confirm no errors**

```bash
mvn compile -pl . -q 2>&1 | grep -E "ERROR|error:" | head -20
```

Expected: no output.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/io/github/carlos_emr/carlos/lab/ca/all/pageUtil/LabUpload2Action.java
git commit -m "fix: suppress HardCodedCryptoKey PMD false positive in LabUpload2Action

Cipher.getInstance(\"RSA/ECB/PKCS1Padding\") uses a JCA transformation name
string, not hardcoded key material. The RSA private key is loaded from the
server keystore at runtime."
```

---

## Task 5: Run unit tests and verify overall health

- [ ] **Step 1: Run unit tests**

```bash
make install --run-unit-tests 2>&1 | tail -20
```

Expected: `BUILD SUCCESS` with all tests passing. These are compilation + unit tests only (no database required). If tests fail, check that none of the annotation changes accidentally altered method signatures or moved code.

- [ ] **Step 2: Verify alert count in spotbugs-exclude.xml matches expectation**

```bash
grep -c 'pattern="HARD_CODE_PASSWORD"' .github/spotbugs/spotbugs-exclude.xml
```

Expected output: `5` (the five new `<Bug pattern="HARD_CODE_PASSWORD"/>` entries added in Task 1).
Note: a plain `grep -c "HARD_CODE_PASSWORD"` would return 7 because the XML section-header comment also mentions the string twice; use the narrower pattern above to count only the suppression entries.

- [ ] **Step 3: Verify all four Java files compile independently**

```bash
grep -l "SuppressFBWarnings\|NOPMD HardCodedCryptoKey" \
  src/main/java/io/github/carlos_emr/carlos/login/LoginCheckLoginBean.java \
  src/main/java/io/github/carlos_emr/carlos/managers/SecurityManager.java \
  src/main/java/io/github/carlos_emr/carlos/lab/ca/all/pageUtil/LabUpload2Action.java
```

Expected: all three paths printed (confirms the suppressions were written to disk).

---

## Alert-to-task mapping (for verification)

| GitHub Alert # | Rule | File | Task |
|---|---|---|---|
| 19383–19388 | `HARD_CODE_PASSWORD` | `EmailManager.java` | Task 1 |
| 19598 | `HARD_CODE_PASSWORD` | `AuthenticationInWSS4JInterceptor.java` | Task 1 |
| 19556 | `HARD_CODE_PASSWORD` | `AuthenticationOutWSS4JInterceptor.java` | Task 1 |
| 11059 | `HARD_CODE_PASSWORD` | `EdtClientBuilder.java` | Task 1 |
| 10510 | `HARD_CODE_PASSWORD` | `ConfigureFax2Action.java` | Task 1 |
| 19370, 19253, 19254 | `HARD_CODE_PASSWORD`, `secrets:S8215`, `java:S2068` | `LoginCheckLoginBean.java` | Task 2 |
| 19414 | `HARD_CODE_PASSWORD` | `SecurityManager.java` | Task 3 |
| 18453 | `HardCodedCryptoKey` | `LabUpload2Action.java` | Task 4 |
