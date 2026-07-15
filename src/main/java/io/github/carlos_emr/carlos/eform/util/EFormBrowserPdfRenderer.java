/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform.util;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.struts2.ServletActionContext;
import org.springframework.stereotype.Service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;

/**
 * Browser-backed eForm PDF renderer.
 *
 * <p>Loads the local renderer servlet with the current authenticated session,
 * captures stabilized page regions through Playwright, and assembles the captures
 * into a PDF for fax and eDoc workflows.</p>
 */
@Service
public class EFormBrowserPdfRenderer {

    private static final Logger logger = MiscUtils.getLogger();
    private static final Duration RENDER_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration RENDERED_PDF_MAX_AGE = Duration.ofHours(24);
    private static final String RENDER_ROOT_PREFIX = "carlos-eform-browser-pdf-";
    private static final String RENDER_OUTPUT_PREFIX = "eform-browser-render-";
    private static final String PDF_SUFFIX = ".pdf";
    private static final String SCRIPT_RELATIVE_PATH = "scripts/eform-browser-pdf-render.js";
    private static final String PLAYWRIGHT_MODULE_RELATIVE_PATH = "node_modules/playwright";
    private static final String PLAYWRIGHT_PACKAGE_NAME = "playwright";
    private static final String NODE_MODULES_DIRECTORY_NAME = "node_modules";
    private static final String ROOT_PROPERTY = "eform_pdf_browser_render_root";
    private static final String BASE_URL_PROPERTY = "eform_pdf_browser_base_url";
    private static final String NODE_BINARY_PROPERTY = "eform_pdf_browser_node_binary";
    private static final String CHROME_PATH_PROPERTY = "eform_pdf_browser_chromium_path";
    private static final String NODE_MODULES_ROOT_PROPERTY = "eform_pdf_browser_node_modules_root";
    private static final String CATALINA_BASE_PROPERTY = "catalina.base";
    private static final String LOOPBACK_BASE_URL = "http://127.0.0.1:8080";
    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";
    private static final char URL_PATH_SEPARATOR = '/';
    private static final String ENV_BASE_URL = "CARLOS_EFORM_RENDER_BASE_URL";
    private static final String ENV_APP_PATH = "CARLOS_EFORM_RENDER_APP_PATH";
    private static final String ENV_COOKIE_HEADER = "CARLOS_EFORM_RENDER_COOKIE_HEADER";
    private static final String ENV_CHROME_PATH = "CARLOS_EFORM_RENDER_CHROME_PATH";
    private static final String ENV_NODE_PATH = "NODE_PATH";
    private static final String RENDERER_RESOURCE_ROOT = "io/github/carlos_emr/carlos/eform/browserpdf/";
    private static final String MAIN_SCRIPT_NAME = "eform-browser-pdf-render.js";
    private static final String[] BUNDLED_SCRIPT_NAMES = {
            MAIN_SCRIPT_NAME,
            "eform-local-playwright-utils.js"
    };
    private static final List<Path> FALLBACK_NODE_MODULES_DIRECTORIES = List.of(
            Path.of("/usr/lib/node_modules"),
            Path.of("/usr/local/lib/node_modules"));
    private static final float CSS_PIXEL_TO_POINTS = 72f / 96f;

    /**
     * Renders a saved eForm by loading the authenticated local servlet route in Playwright,
     * capturing page images, and assembling those captures into a PDF.
     *
     * @param fdid saved eForm data identifier
     * @param providerId provider number expected by the renderer session gate
     * @return readable temporary PDF path; caller owns cleanup
     * @throws PDFGenerationException when the renderer cannot start, times out, fails, or produces no readable PDF
     */
    // FindSecBugs COMMAND_INJECTION: fixed argv slots only; validated local URL fragments; local Node script launched without shell expansion.
    @SuppressFBWarnings(value = "COMMAND_INJECTION", justification = "The renderer command uses fixed argv slots, validates request-derived URL fragments, and launches a local Node script without shell expansion.")
    public Path renderSavedEformPdf(int fdid, String providerId) throws PDFGenerationException {
        HttpServletRequest currentRequest = ServletActionContext.getRequest();
        String projectHome = CarlosProperties.getInstance().getProperty("project_home", "");
        String baseUrl = resolveBaseUrl(projectHome, currentRequest);
        String appPath = buildAppPath(fdid, providerId);
        String cookieHeader = buildRendererSessionCookieHeader(currentRequest);
        Path tempRoot = resolveRendererTempRoot();
        cleanupExpiredRendererRoots(tempRoot, RENDERED_PDF_MAX_AGE);
        RendererRuntime rendererRuntime = null;
        Path renderRoot = null;
        Path outputDirectory = null;
        Path outputPdfPath = null;
        Process process = null;
        boolean success = false;

        try {
            renderRoot = createSecureTempDirectory(tempRoot, RENDER_ROOT_PREFIX);
            rendererRuntime = prepareRendererRuntime(renderRoot);
            Path runtimeRoot = rendererRuntime.runtimeRoot();
            Path scriptPath = runtimeRoot.resolve(MAIN_SCRIPT_NAME);
            // Resolved inside the try block so a discovery failure still cleans up any staged runtime directory.
            Path nodeModulesDirectory = resolveNodeModulesDirectory(runtimeRoot);
            outputDirectory = createSecureTempDirectory(renderRoot, RENDER_OUTPUT_PREFIX);
            outputPdfPath = createSecureTempFile(renderRoot, RENDER_OUTPUT_PREFIX, PDF_SUFFIX);

            List<String> command = buildCommand(
                    resolveNodeBinary(),
                    scriptPath,
                    outputDirectory);

            ProcessBuilder processBuilder = new ProcessBuilder(command); // nosemgrep: java.lang.security.audit.command-injection-process-builder.command-injection-process-builder -- command is built from fixed argv positions after resolving the trusted Node binary, trusted renderer script, and managed temp output directory; request/session values are validated and passed separately via environment variables
            processBuilder.directory(runtimeRoot.toFile());
            processBuilder.redirectErrorStream(true);
            Map<String, String> environment = processBuilder.environment();
            environment.put(ENV_NODE_PATH, nodeModulesDirectory.toString());
            applyRendererEnvironment(environment, baseUrl, appPath, cookieHeader, resolveChromiumPath());

            process = processBuilder.start();
            try (ExecutorService processOutputExecutor = Executors.newSingleThreadExecutor()) {
                Process rendererProcess = process;
                CompletableFuture<Void> outputFuture = CompletableFuture.runAsync(
                        () -> drainProcessOutput(rendererProcess),
                        processOutputExecutor);
                waitForRendererProcess(process, outputFuture);
            }
            List<Path> captureFiles = listCaptureFiles(outputDirectory);
            if (captureFiles.isEmpty()) {
                throw new PDFGenerationException("Browser rendering completed without producing any page captures.");
            }
            convertCapturesToPdf(captureFiles, outputPdfPath);
            if (!Files.isReadable(outputPdfPath) || Files.size(outputPdfPath) == 0) {
                throw new PDFGenerationException("Browser rendering completed without producing a readable eForm PDF.");
            }
            success = true;
            return outputPdfPath;
        } catch (IOException e) {
            throw new PDFGenerationException("Unable to start the browser PDF renderer for eForms.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PDFGenerationException("Browser rendering was interrupted while generating the eForm PDF.", e);
        } finally {
            if (!success) {
                terminateProcessTree(process);
                deleteQuietly(outputPdfPath);
            }
            deleteRecursivelyQuietly(outputDirectory);
            if (rendererRuntime != null && rendererRuntime.temporary()) {
                deleteRecursivelyQuietly(rendererRuntime.runtimeRoot());
            }
            if (!success) {
                deleteRecursivelyQuietly(renderRoot);
            }
        }
    }

    static String buildAppPath(int fdid, String providerId) {
        StringBuilder path = new StringBuilder("/EFormViewForPdfGenerationServlet?fdid=")
                .append(fdid);
        if (providerId != null && !providerId.isBlank()) {
            path.append("&providerId=")
                    .append(URLEncoder.encode(providerId, StandardCharsets.UTF_8));
        }
        return path.append("&browserRender=true").toString();
    }


    static String buildRendererSessionCookieHeader(HttpServletRequest request) throws PDFGenerationException {
        if (request == null) {
            throw new PDFGenerationException("Browser rendering requires an active authenticated request context.");
        }

        HttpSession session = request.getSession(false);
        if (session == null || LoggedInInfo.getLoggedInInfoFromSession(request) == null) {
            throw new PDFGenerationException("Browser rendering requires an active authenticated session.");
        }

        String cookieName = request.getServletContext().getSessionCookieConfig().getName();
        if (cookieName == null || cookieName.isBlank()) {
            cookieName = "JSESSIONID";
        }
        return cookieName + "=" + session.getId();
    }

    private void waitForRendererProcess(Process process, CompletableFuture<Void> outputFuture)
            throws InterruptedException, PDFGenerationException {
        try {
            boolean finished = process.waitFor(RENDER_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                terminateProcessTree(process);
                awaitProcessOutput(outputFuture);
                throw new PDFGenerationException("Browser rendering timed out while generating the eForm PDF.");
            }
            awaitProcessOutput(outputFuture);
            if (process.exitValue() != 0) {
                throw new PDFGenerationException("Browser rendering failed while generating the eForm PDF. exitStatus=" + process.exitValue());
            }
        } catch (InterruptedException e) {
            terminateProcessTree(process);
            outputFuture.cancel(true);
            throw e;
        }
    }

    private static void drainProcessOutput(Process process) {
        try {
            process.getInputStream().transferTo(OutputStream.nullOutputStream());
        } catch (IOException e) {
            logger.debug("Unable to drain browser renderer output", e);
        }
    }

    private static void awaitProcessOutput(CompletableFuture<Void> outputFuture) throws InterruptedException {
        try {
            outputFuture.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException | java.util.concurrent.TimeoutException e) {
            logger.debug("Browser renderer output drain did not finish cleanly", e);
        }
    }

    static String buildDefaultBaseUrl(String projectHome) {
        if (projectHome == null || projectHome.isBlank()) {
            return LOOPBACK_BASE_URL;
        }
        String normalizedProjectHome = projectHome.charAt(0) == URL_PATH_SEPARATOR ? projectHome.substring(1) : projectHome;
        normalizedProjectHome = !normalizedProjectHome.isEmpty()
                && normalizedProjectHome.charAt(normalizedProjectHome.length() - 1) == URL_PATH_SEPARATOR
                ? normalizedProjectHome.substring(0, normalizedProjectHome.length() - 1)
                : normalizedProjectHome;
        if (normalizedProjectHome.isEmpty()) {
            return LOOPBACK_BASE_URL;
        }
        return LOOPBACK_BASE_URL + URL_PATH_SEPARATOR + normalizedProjectHome;
    }

    static String buildLocalBaseUrl(String scheme, int port, String contextPath) {
        String normalizedScheme = (scheme == null || scheme.isBlank()) ? "http" : scheme.trim();
        String normalizedContextPath = contextPath == null ? "" : contextPath.trim();
        if (!normalizedContextPath.isEmpty() && normalizedContextPath.charAt(0) != URL_PATH_SEPARATOR) {
            normalizedContextPath = URL_PATH_SEPARATOR + normalizedContextPath;
        }
        if (!normalizedContextPath.isEmpty()
                && normalizedContextPath.charAt(normalizedContextPath.length() - 1) == URL_PATH_SEPARATOR) {
            normalizedContextPath = normalizedContextPath.substring(0, normalizedContextPath.length() - 1);
        }
        StringBuilder baseUrl = new StringBuilder(normalizedScheme).append("://127.0.0.1");
        if (port > 0 && !isDefaultPort(normalizedScheme, port)) {
            baseUrl.append(":").append(port);
        }
        baseUrl.append(normalizedContextPath);
        return baseUrl.toString();
    }

    static List<String> buildCommand(String nodeBinary, Path scriptPath, Path outputDirectory) {
        List<String> command = new ArrayList<>();
        command.add(nodeBinary);
        command.add(scriptPath.toAbsolutePath().toString());
        command.add("--output-dir");
        command.add(outputDirectory.toAbsolutePath().toString());
        return command;
    }

    static void applyRendererEnvironment(
            Map<String, String> environment,
            String baseUrl,
            String appPath,
            String cookieHeader,
            String chromePath) {
        environment.put(ENV_BASE_URL, validateRendererBaseUrl(baseUrl));
        environment.put(ENV_APP_PATH, validateRendererAppPath(appPath));
        putIfPresent(environment, ENV_COOKIE_HEADER, cookieHeader);
        putIfPresent(environment, ENV_CHROME_PATH, chromePath);
    }

    private static void putIfPresent(Map<String, String> environment, String key, String value) {
        if (value != null && !value.isBlank()) {
            environment.put(key, value);
        }
    }

    static String validateRendererBaseUrl(String rawBaseUrl) {
        if (rawBaseUrl == null || rawBaseUrl.isBlank()) {
            throw new IllegalArgumentException("Renderer base URL must be non-empty");
        }

        URI uri = URI.create(rawBaseUrl.trim());
        String scheme = uri.getScheme();
        if (!equalsAsciiIgnoreCase(scheme, HTTP_SCHEME) && !equalsAsciiIgnoreCase(scheme, HTTPS_SCHEME)) {
            throw new IllegalArgumentException("Renderer base URL must use http or https");
        }
        if (uri.getHost() == null || !isLoopbackRendererHost(uri.getHost())) {
            throw new IllegalArgumentException("Renderer base URL host must be loopback");
        }
        return rawBaseUrl.trim().replaceAll("/$", "");
    }

    static String validateRendererAppPath(String appPath) {
        if (appPath == null || appPath.isBlank()) {
            throw new IllegalArgumentException("Application path must be non-empty");
        }
        String normalizedPath = appPath.trim();
        if (!normalizedPath.startsWith("/") || normalizedPath.startsWith("//")) {
            throw new IllegalArgumentException("Application path must be root-relative");
        }
        return normalizedPath;
    }

    static boolean isLoopbackRendererHost(String rawHost) {
        String host = rawHost == null ? "" : toAsciiLowerCase(rawHost.trim());
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        if (host.isEmpty()) {
            return false;
        }
        return Set.of("localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1").contains(host);
    }

    private String resolveBaseUrl(String projectHome, HttpServletRequest request) {
        String configuredBaseUrl = CarlosProperties.getInstance().getProperty(BASE_URL_PROPERTY);
        if (configuredBaseUrl != null && !configuredBaseUrl.isBlank()) {
            return configuredBaseUrl.trim().replaceAll("/$", "");
        }
        if (request != null) {
            return buildLocalBaseUrl(request.getScheme(), request.getLocalPort(), request.getContextPath());
        }
        return buildDefaultBaseUrl(projectHome);
    }

    private String resolveNodeBinary() {
        String configuredNodeBinary = CarlosProperties.getInstance().getProperty(NODE_BINARY_PROPERTY);
        if (configuredNodeBinary != null && !configuredNodeBinary.isBlank()) {
            return configuredNodeBinary.trim();
        }
        return "node";
    }

    private String resolveChromiumPath() {
        String configuredChromiumPath = CarlosProperties.getInstance().getProperty(CHROME_PATH_PROPERTY);
        if (configuredChromiumPath != null && !configuredChromiumPath.isBlank()) {
            return configuredChromiumPath.trim();
        }
        return null;
    }

    private Path resolveRendererTempRoot() throws PDFGenerationException {
        try {
            return resolveRendererTempRoot(
                    System.getProperty(CATALINA_BASE_PROPERTY),
                    System.getProperty("java.io.tmpdir"));
        } catch (RuntimeException e) {
            throw new PDFGenerationException("Unable to resolve a managed temporary directory for eForm browser PDF generation.", e);
        }
    }

    /**
     * Resolves the allowed temp parent for private per-render artifact roots.
     *
     * <p>The rendered PDF is later reused by the fax flow as {@code faxFilePath}, and
     * {@code FaxManagerImpl.validateFilePath}/{@code resolveAndValidateFilePath} only accept files
     * under {@code DOCUMENT_DIR} or {@link PathValidationUtils#isInAllowedTempDirectory(File)}
     * (java.io.tmpdir and the Tomcat work directories). The renderer therefore creates a fresh
     * private directory directly under one of those already-whitelisted temp locations; do not add
     * roots (such as {@code BASE_DOCUMENT_DIR}, removed for exactly this reason) that fax path
     * validation rejects.</p>
     */
    static Path resolveRendererTempRoot(String catalinaBase, String javaTmpDir) {
        if (catalinaBase != null && !catalinaBase.isBlank()) {
            File catalinaDir = PathValidationUtils.resolveConfiguredDirectory(catalinaBase.trim(), CATALINA_BASE_PROPERTY);
            return Path.of(catalinaDir.getPath(), "work");
        }
        File tempDir = PathValidationUtils.validateConfiguredDirectory(javaTmpDir, "java.io.tmpdir");
        return tempDir.toPath();
    }

    private RendererRuntime prepareRendererRuntime(Path tempRoot) throws PDFGenerationException {
        Path checkoutRoot = resolveScriptRoot();
        if (checkoutRoot != null) {
            return new RendererRuntime(checkoutRoot.resolve("scripts"), false);
        }
        return new RendererRuntime(extractBundledRendererRuntime(tempRoot), true);
    }

    private Path resolveScriptRoot() {
        for (Path candidate : configuredCandidateRoots(ROOT_PROPERTY, "CARLOS_EFORM_PDF_BROWSER_RENDER_ROOT")) {
            Path scriptPath = candidate.resolve(SCRIPT_RELATIVE_PATH).normalize();
            if (!Files.isRegularFile(scriptPath)) {
                continue;
            }
            try {
                PathValidationUtils.validateExistingPath(scriptPath.toFile(), candidate.toFile());
                return candidate;
            } catch (SecurityException e) {
                logger.warn("Ignoring eForm PDF browser renderer root outside its configured directory: {}", candidate, e);
            }
        }
        return null;
    }

    private Path resolveNodeModulesDirectory(Path runtimeRoot) throws PDFGenerationException {
        Path nodeModulesDirectory = findNodeModulesDirectory(nodeModulesCandidates(runtimeRoot));
        if (nodeModulesDirectory != null) {
            return nodeModulesDirectory;
        }
        throw new PDFGenerationException("Unable to locate the Playwright node_modules directory for eForm PDF generation.");
    }

    static Path findNodeModulesDirectory(List<Path> candidates) {
        for (Path candidate : candidates) {
            Path nodeModulesDirectory = validateNodeModulesCandidate(candidate);
            if (nodeModulesDirectory != null) {
                return nodeModulesDirectory;
            }
        }
        return null;
    }

    private List<Path> nodeModulesCandidates(Path runtimeRoot) {
        Set<Path> candidates = new LinkedHashSet<>();
        Path runtimeParent = runtimeRoot.getParent();
        if (runtimeParent != null) {
            candidates.add(runtimeParent.toAbsolutePath().normalize());
        }
        candidates.add(runtimeRoot.toAbsolutePath().normalize());
        candidates.addAll(configuredCandidateRoots(NODE_MODULES_ROOT_PROPERTY, "CARLOS_EFORM_PDF_BROWSER_NODE_MODULES_ROOT"));
        candidates.addAll(parsePathList(System.getenv(ENV_NODE_PATH)));
        candidates.addAll(FALLBACK_NODE_MODULES_DIRECTORIES);
        return new ArrayList<>(candidates);
    }

    private static Path validateNodeModulesCandidate(Path candidate) {
        if (candidate == null) {
            return null;
        }
        Path normalizedCandidate = candidate.toAbsolutePath().normalize();
        if (normalizedCandidate.getParent() == null) {
            return null;
        }
        List<Path> playwrightPaths = List.of(
                normalizedCandidate.resolve(PLAYWRIGHT_MODULE_RELATIVE_PATH).normalize(),
                normalizedCandidate.resolve(PLAYWRIGHT_PACKAGE_NAME).normalize());
        for (Path playwrightPath : playwrightPaths) {
            Path nodeModulesDirectory = validatePlaywrightPath(candidate, playwrightPath);
            if (nodeModulesDirectory != null) {
                return nodeModulesDirectory;
            }
        }
        return null;
    }

    private static Path validatePlaywrightPath(Path candidate, Path playwrightPath) {
        if (!Files.isDirectory(playwrightPath)) {
            return null;
        }
        Path nodeModulesDirectory = playwrightPath.getParent();
        Path nodeModulesFileName = nodeModulesDirectory == null ? null : nodeModulesDirectory.getFileName();
        if (nodeModulesFileName == null || !NODE_MODULES_DIRECTORY_NAME.equals(nodeModulesFileName.toString())) {
            return null;
        }
        try {
            PathValidationUtils.validateExistingPath(playwrightPath.toFile(), nodeModulesDirectory.toFile());
            return nodeModulesDirectory;
        } catch (SecurityException e) {
            logger.warn("Ignoring Playwright node_modules directory outside its candidate root: {}", candidate, e);
            return null;
        }
    }

    private List<Path> configuredCandidateRoots(String propertyName, String environmentVariableName) {
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        addConfiguredCandidate(candidates, CarlosProperties.getInstance().getProperty(propertyName), propertyName);
        addConfiguredCandidate(candidates, System.getenv(environmentVariableName), environmentVariableName);
        addConfiguredCandidate(candidates, System.getProperty(propertyName), propertyName);
        return new ArrayList<>(candidates);
    }

    private void addConfiguredCandidate(Set<Path> candidates, String rawCandidate, String label) {
        if (rawCandidate == null || rawCandidate.isBlank()) {
            return;
        }
        try {
            candidates.add(PathValidationUtils.validateConfiguredDirectory(rawCandidate.trim(), label).toPath());
        } catch (RuntimeException e) {
            logger.warn("Ignoring invalid eForm PDF browser renderer root candidate: {}", rawCandidate, e);
        }
    }

    static List<Path> parsePathList(String rawPaths) {
        if (rawPaths == null || rawPaths.isBlank()) {
            return Collections.emptyList();
        }
        String[] entries = rawPaths.split(java.util.regex.Pattern.quote(File.pathSeparator));
        List<Path> parsedPaths = new ArrayList<>();
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            parsedPaths.add(Path.of(entry.trim()).toAbsolutePath().normalize());
        }
        return parsedPaths;
    }

    private Path extractBundledRendererRuntime(Path tempRoot) throws PDFGenerationException {
        Path runtimeDir = null;
        try {
            runtimeDir = createSecureTempDirectory(tempRoot, "eform-browser-pdf-runtime-");
            for (String scriptName : BUNDLED_SCRIPT_NAMES) {
                copyBundledScript(runtimeDir, scriptName);
            }
            return runtimeDir;
        } catch (IOException | PDFGenerationException e) {
            deleteRecursivelyQuietly(runtimeDir);
            throw new PDFGenerationException("Unable to stage the bundled Playwright renderer assets for eForm PDF generation.", e);
        }
    }

    private void copyBundledScript(Path runtimeDir, String scriptName) throws IOException, PDFGenerationException {
        Path outputPath = runtimeDir.resolve(scriptName);
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(RENDERER_RESOURCE_ROOT + scriptName)) {
            if (inputStream == null) {
                throw new PDFGenerationException("Unable to locate bundled eForm PDF renderer asset: " + scriptName);
            }
            Files.copy(inputStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private List<Path> listCaptureFiles(Path outputDirectory) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(outputDirectory, "page-*.png")) {
            List<Path> captures = new ArrayList<>();
            for (Path capture : stream) {
                captures.add(capture);
            }
            return captures.stream()
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private void convertCapturesToPdf(List<Path> captureFiles, Path outputPdfPath) throws PDFGenerationException {
        try (PDDocument document = new PDDocument()) {
            for (Path captureFile : captureFiles) {
                BufferedImage image = ImageIO.read(captureFile.toFile());
                if (image == null) {
                    throw new PDFGenerationException("Unable to read eForm browser capture image: " + captureFile.getFileName());
                }
                float pageWidth = image.getWidth() * CSS_PIXEL_TO_POINTS;
                float pageHeight = image.getHeight() * CSS_PIXEL_TO_POINTS;
                PDPage page = new PDPage(new PDRectangle(pageWidth, pageHeight));
                document.addPage(page);
                PDImageXObject pdImage = LosslessFactory.createFromImage(document, image);
                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    contentStream.drawImage(pdImage, 0, 0, pageWidth, pageHeight);
                }
            }
            document.save(outputPdfPath.toFile());
        } catch (IOException e) {
            throw new PDFGenerationException("Unable to assemble the browser-rendered eForm captures into a PDF.", e);
        }
    }

    static Path createSecureTempDirectory(Path tempRoot, String prefix) throws IOException {
        return createSecureTempPath(tempRoot, true, prefix, null);
    }

    static Path createSecureTempFile(Path tempRoot, String prefix, String suffix) throws IOException {
        return createSecureTempPath(tempRoot, false, prefix, suffix);
    }

    static void cleanupExpiredRendererRoots(Path tempRoot, Duration maxAge) {
        if (tempRoot == null || maxAge == null || maxAge.isZero() || maxAge.isNegative()) {
            return;
        }

        try {
            Path managedRoot = Files.createDirectories(tempRoot);
            FileTime cutoff = FileTime.from(Instant.now().minus(maxAge));
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(
                    managedRoot, RENDER_ROOT_PREFIX + "*")) {
                for (Path candidate : stream) {
                    if (Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)
                            && Files.getLastModifiedTime(candidate, LinkOption.NOFOLLOW_LINKS).compareTo(cutoff) < 0) {
                        deleteRecursivelyQuietly(candidate);
                    }
                }
            }
        } catch (IOException e) {
            logger.debug("Unable to clean up expired browser-rendered eForm artifacts under {}", tempRoot, e);
        }
    }

    // FindSecBugs PATH_TRAVERSAL_IN: temp artifacts are created only under a validated managed temp root, with caller-controlled filenames disallowed.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "Renderer temp files are created only beneath resolveRendererTempRoot(), which validates configured roots before creating a managed private temp directory.")
    private static Path createSecureTempPath(Path tempRoot, boolean directory, String prefix, String suffix) throws IOException {
        Path managedRoot = Files.createDirectories(tempRoot);
        FileAttribute<?>[] secureAttributes = securePosixAttributes(directory);
        try {
            return directory
                    ? Files.createTempDirectory(managedRoot, prefix, secureAttributes)
                    : Files.createTempFile(managedRoot, prefix, suffix, secureAttributes);
        } catch (UnsupportedOperationException e) {
            logger.warn("Renderer temp path POSIX permissions are unsupported under {}; using platform temp-file defaults", managedRoot);
            return directory
                    ? Files.createTempDirectory(managedRoot, prefix)
                    : Files.createTempFile(managedRoot, prefix, suffix);
        }
    }

    private static FileAttribute<?>[] securePosixAttributes(boolean directory) {
        try {
            Set<PosixFilePermission> permissions = directory
                    ? EnumSet.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE)
                    : EnumSet.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE);
            return new FileAttribute<?>[] { PosixFilePermissions.asFileAttribute(permissions) };
        } catch (UnsupportedOperationException e) {
            return new FileAttribute<?>[0];
        }
    }

    private static boolean isDefaultPort(String scheme, int port) {
        return (equalsAsciiIgnoreCase(scheme, HTTP_SCHEME) && port == 80)
                || (equalsAsciiIgnoreCase(scheme, HTTPS_SCHEME) && port == 443);
    }

    private static boolean equalsAsciiIgnoreCase(String value, String expectedLowerCase) {
        if (value == null || value.length() != expectedLowerCase.length()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char candidate = value.charAt(i);
            if (candidate >= 'A' && candidate <= 'Z') {
                candidate = (char) (candidate + ('a' - 'A'));
            }
            if (candidate != expectedLowerCase.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static String toAsciiLowerCase(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char candidate = value.charAt(i);
            if (candidate >= 'A' && candidate <= 'Z') {
                candidate = (char) (candidate + ('a' - 'A'));
            }
            builder.append(candidate);
        }
        return builder.toString();
    }

    private record RendererRuntime(Path runtimeRoot, boolean temporary) {
    }

    private static void terminateProcessTree(Process process) {
        if (process == null) {
            return;
        }
        process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private static void deleteQuietly(Path outputPath) {
        if (outputPath == null) {
            return;
        }
        try {
            Files.deleteIfExists(outputPath);
        } catch (IOException e) {
            logger.debug("Unable to delete temporary browser-rendered PDF {}", outputPath, e);
        }
    }

    private static void deleteRecursivelyQuietly(Path directory) {
        if (directory == null) {
            return;
        }
        try (var stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(EFormBrowserPdfRenderer::deleteQuietly);
        } catch (IOException e) {
            logger.debug("Unable to delete temporary browser-rendered capture directory {}", directory, e);
        }
    }

}
