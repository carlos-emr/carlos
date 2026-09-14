/**
 * CARLOS EMR — CodeQL customizations.
 *
 * Teaches the standard Java security queries (notably `java/path-injection`,
 * `TaintedPath.ql`) that the containment-validating helpers in
 * `io.github.carlos_emr.carlos.utility.PathValidationUtils` return a safe,
 * canonical-containment-checked path / path component.
 *
 * WHY THIS IS NEEDED
 * ------------------
 * `PathValidationUtils` is part of the analysed source, so CodeQL reads its body,
 * propagates taint through `new File(allowedDir, name)`, does NOT recognise the
 * custom `validateWithinDirectory(...)` canonical-containment guard as a barrier,
 * and therefore reports false-positive path-injection alerts at every downstream
 * filesystem sink (e.g. `DocumentUpload2Action` line 301, code-scanning alert
 * #26023).
 *
 * Because the helper is in-source, a Models-as-Data `neutral` / `summary` model
 * does NOT suppress the body-based flow — only a QL barrier
 * (`PathInjectionSanitizer`) does. That is what this file adds.
 *
 * SCOPE — only methods that genuinely enforce containment or strip path
 * metacharacters are listed. `resolveTrustedPath`, `resolveConfiguredDirectory`,
 * and `resolveConfiguredFile` are deliberately EXCLUDED: their own Javadoc states
 * they only canonicalise a trusted value and are "not a security boundary", so
 * treating them as sanitizers could hide real issues.
 */

import java
import semmle.code.java.security.PathSanitizer

/** Holds for the containment / component-validating `PathValidationUtils` methods. */
private predicate isContainmentValidator(Method m) {
  m.getDeclaringType()
      .hasQualifiedName("io.github.carlos_emr.carlos.utility", "PathValidationUtils") and
  m.hasName([
      "validatePath", // (String, File) -> File : basename-strip + containment
      "validateExistingPath", // (File, File) -> File : containment
      "validateChildPath", // (File, File) -> File : containment
      "validateGeneratedChildPath", // (String, File) -> File : single component + containment
      "validatePathComponent", // (String, String) -> String : rejects / \ . ..
      "validateGeneratedFileName", // (String) -> String : normalised, no separators
      "validateZipEntryPath", // (ZipEntry, File) -> File : ZIP-slip containment
      "validateZipEntryName", // (File, File) -> String : validated relative entry name
      "validateUpload" // (File[, String, File]) -> File : within allowed upload dir
    ])
}

/**
 * The result of a `PathValidationUtils` containment-validating call is a sanitised
 * path for the `java/path-injection` query, so taint flow through that result is
 * blocked.
 *
 * NOTE: CodeQL CLI &lt; 2.16 names the call class `MethodAccess` rather than
 * `MethodCall`. If your CodeQL toolchain predates that rename, change `MethodCall`
 * below to `MethodAccess`.
 */
class PathValidationUtilsSanitizer extends PathInjectionSanitizer {
  PathValidationUtilsSanitizer() {
    exists(MethodCall mc |
      isContainmentValidator(mc.getMethod()) and
      this.asExpr() = mc
    )
  }
}
