/**
 * Registers PathValidationUtils methods as taint sanitizers (barriers) for
 * the java/path-injection query (CWE-022: Path Traversal).
 *
 * PathValidationUtils is the CARLOS EMR project's centralized path validation
 * utility. Every public method performs canonical path resolution and directory
 * containment checks before returning a safe File object. CodeQL's built-in
 * taint analysis cannot trace sanitization through custom utility methods,
 * causing false positives on every call site.
 *
 * By subclassing the abstract PathInjectionSanitizer, these methods are
 * automatically recognized as barriers by the existing TaintedPath query
 * without any query duplication or workflow changes.
 *
 * @see io.github.carlos_emr.carlos.utility.PathValidationUtils
 */

import java
import semmle.code.java.dataflow.DataFlow
import semmle.code.java.security.PathSanitizer

/**
 * Treats the return value of any PathValidationUtils public method as a
 * sanitized (untainted) value for path-injection analysis.
 *
 * Covered methods:
 *   - validatePath(String, File)         -> sanitizes filename, validates within dir
 *   - validateExistingPath(File, File)   -> validates file is within allowed dir
 *   - validateUpload(File)               -> validates source is in allowed temp dir
 *   - validateUpload(File, String, File) -> validates source + sanitizes destination
 */
class PathValidationUtilsSanitizer extends PathInjectionSanitizer {
  PathValidationUtilsSanitizer() {
    exists(MethodCall mc |
      mc.getMethod().getDeclaringType().hasQualifiedName(
        "io.github.carlos_emr.carlos.utility", "PathValidationUtils"
      ) and
      mc.getMethod().hasName([
        "validatePath",
        "validateExistingPath",
        "validateUpload"
      ]) and
      this.asExpr() = mc
    )
  }
}

/**
 * Treats a successful isInAllowedTempDirectory() check as a guard condition
 * that sanitizes the file argument in the true branch.
 */
class PathValidationTempDirGuard extends PathInjectionSanitizer {
  PathValidationTempDirGuard() {
    exists(MethodCall mc |
      mc.getMethod().getDeclaringType().hasQualifiedName(
        "io.github.carlos_emr.carlos.utility", "PathValidationUtils"
      ) and
      mc.getMethod().hasName("isInAllowedTempDirectory") and
      this.asExpr() = mc.getArgument(0)
    )
  }
}
