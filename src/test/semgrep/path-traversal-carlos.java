// Semgrep rule fixtures; intentionally unsafe cases are never compiled or executed.
import java.io.File;
import java.io.FileInputStream;
import jakarta.servlet.http.HttpServletRequest;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import static io.github.carlos_emr.carlos.utility.PathValidationUtils.validateExistingDocumentPath;

class DocumentPathValidationCases {
    Object validatedDocument(HttpServletRequest request) throws Exception {
        File file = PathValidationUtils.validateExistingDocumentPath(request.getParameter("path"));
        // ok: carlos.httpservlet-path-traversal
        return new FileInputStream(file);
    }

    Object qualifiedDocument(HttpServletRequest request) throws Exception {
        File file = io.github.carlos_emr.carlos.utility.PathValidationUtils.validateExistingDocumentPath(request.getParameter("path"));
        // ok: carlos.httpservlet-path-traversal
        return new FileInputStream(file);
    }

    Object staticDocument(HttpServletRequest request) throws Exception {
        File file = validateExistingDocumentPath(request.getParameter("path"));
        // ok: carlos.httpservlet-path-traversal
        return new FileInputStream(file);
    }

    Object rawRequest(HttpServletRequest request) throws Exception {
        // ruleid: carlos.httpservlet-path-traversal
        return new FileInputStream(request.getParameter("path"));
    }

    Object canonicalizationOnly(HttpServletRequest request) throws Exception {
        File file = PathValidationUtils.resolveConfiguredDirectory(request.getParameter("path"), "test");
        // ruleid: carlos.httpservlet-path-traversal
        return new FileInputStream(file);
    }

    Object sharedTempOnly(HttpServletRequest request) throws Exception {
        // ruleid: carlos.httpservlet-path-traversal
        File input = new File(request.getParameter("path"));
        File file = PathValidationUtils.validateUpload(input);
        // ruleid: carlos.httpservlet-path-traversal
        return new FileInputStream(file);
    }
}
