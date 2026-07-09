package io.github.carlos_emr.carlos.eform.util;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.struts2.ServletActionContext;
import org.springframework.stereotype.Service;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;

@Service
public class EFormBrowserPdfRenderer {

    private static final Logger logger = MiscUtils.getLogger();
    private static final Duration RENDER_TIMEOUT = Duration.ofSeconds(90);
    private static final String SCRIPT_RELATIVE_PATH = "scripts/eform-browser-pdf-render.js";
    private static final String PLAYWRIGHT_MODULE_RELATIVE_PATH = "node_modules/playwright";
    private static final String ROOT_PROPERTY = "eform_pdf_browser_render_root";
    private static final String BASE_URL_PROPERTY = "eform_pdf_browser_base_url";
    private static final String NODE_BINARY_PROPERTY = "eform_pdf_browser_node_binary";
    private static final String CHROME_PATH_PROPERTY = "eform_pdf_browser_chromium_path";
    private static final String NODE_MODULES_ROOT_PROPERTY = "eform_pdf_browser_node_modules_root";
    private static final String RENDERER_RESOURCE_ROOT = "io/github/carlos_emr/carlos/eform/browserpdf/";
    private static final String MAIN_SCRIPT_NAME = "eform-browser-pdf-render.js";
    private static final String[] BUNDLED_SCRIPT_NAMES = {
            MAIN_SCRIPT_NAME,
            "eform-local-playwright-utils.js"
    };
    private static final float CSS_PIXEL_TO_POINTS = 72f / 96f;

    public Path renderSavedEformPdf(int fdid, String providerId) throws PDFGenerationException {
        String projectHome = CarlosProperties.getInstance().getProperty("project_home", "");
        String baseUrl = resolveBaseUrl(projectHome, ServletActionContext.getRequest());
        String appPath = buildAppPath(fdid, providerId);
        Path runtimeRoot = prepareRendererRuntime();
        Path scriptPath = runtimeRoot.resolve(MAIN_SCRIPT_NAME);
        Path nodeModulesRoot = resolveNodeModulesRoot(runtimeRoot);
        Path outputDirectory;
        Path outputPdfPath;

        try {
            outputDirectory = Files.createTempDirectory("eform-browser-render-");
            outputPdfPath = Files.createTempFile("eform-browser-render-", ".pdf");
        } catch (IOException e) {
            throw new PDFGenerationException("Unable to allocate temporary files for browser-rendered eForm output.", e);
        }

        List<String> command = buildCommand(
                resolveNodeBinary(),
                scriptPath,
                baseUrl,
                appPath,
                outputDirectory,
                resolveChromiumPath());

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(runtimeRoot.toFile());
        processBuilder.redirectErrorStream(true);
        Map<String, String> environment = processBuilder.environment();
        environment.put("NODE_PATH", nodeModulesRoot.resolve("node_modules").toString());

        String processOutput = "";
        try {
            Process process = processBuilder.start();
            boolean finished = process.waitFor(RENDER_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            processOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!finished) {
                process.destroyForcibly();
                deleteRecursivelyQuietly(outputDirectory);
                deleteQuietly(outputPdfPath);
                throw new PDFGenerationException("Browser rendering timed out while generating the eForm PDF. " + abbreviate(processOutput));
            }
            if (process.exitValue() != 0) {
                deleteRecursivelyQuietly(outputDirectory);
                deleteQuietly(outputPdfPath);
                throw new PDFGenerationException("Browser rendering failed while generating the eForm PDF. " + abbreviate(processOutput));
            }
            List<Path> captureFiles = listCaptureFiles(outputDirectory);
            if (captureFiles.isEmpty()) {
                deleteRecursivelyQuietly(outputDirectory);
                deleteQuietly(outputPdfPath);
                throw new PDFGenerationException("Browser rendering completed without producing any page captures.");
            }
            convertCapturesToPdf(captureFiles, outputPdfPath);
            if (!Files.isReadable(outputPdfPath) || Files.size(outputPdfPath) == 0) {
                deleteRecursivelyQuietly(outputDirectory);
                deleteQuietly(outputPdfPath);
                throw new PDFGenerationException("Browser rendering completed without producing a readable eForm PDF.");
            }
            deleteRecursivelyQuietly(outputDirectory);
            return outputPdfPath;
        } catch (IOException e) {
            deleteRecursivelyQuietly(outputDirectory);
            deleteQuietly(outputPdfPath);
            throw new PDFGenerationException("Unable to start the browser PDF renderer for eForms. " + abbreviate(processOutput), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            deleteRecursivelyQuietly(outputDirectory);
            deleteQuietly(outputPdfPath);
            throw new PDFGenerationException("Browser rendering was interrupted while generating the eForm PDF.", e);
        }
    }

    static String buildAppPath(int fdid, String providerId) {
        StringBuilder path = new StringBuilder("/eformViewForPdfGenerationServlet?fdid=")
                .append(fdid);
        if (providerId != null && !providerId.isBlank()) {
            path.append("&providerId=")
                    .append(URLEncoder.encode(providerId, StandardCharsets.UTF_8));
        }
        return path.toString();
    }

    static String buildDefaultBaseUrl(String projectHome) {
        if (projectHome == null || projectHome.isBlank()) {
            return "http://127.0.0.1:8080";
        }
        String normalizedProjectHome = projectHome.startsWith("/") ? projectHome.substring(1) : projectHome;
        normalizedProjectHome = normalizedProjectHome.endsWith("/")
                ? normalizedProjectHome.substring(0, normalizedProjectHome.length() - 1)
                : normalizedProjectHome;
        return "http://127.0.0.1:8080/" + normalizedProjectHome;
    }

    static String buildLocalBaseUrl(String scheme, int port, String contextPath) {
        String normalizedScheme = (scheme == null || scheme.isBlank()) ? "http" : scheme.trim();
        String normalizedContextPath = contextPath == null ? "" : contextPath.trim();
        if (!normalizedContextPath.isEmpty() && !normalizedContextPath.startsWith("/")) {
            normalizedContextPath = "/" + normalizedContextPath;
        }
        normalizedContextPath = normalizedContextPath.replaceAll("/$", "");
        StringBuilder baseUrl = new StringBuilder(normalizedScheme).append("://127.0.0.1");
        if (port > 0 && !isDefaultPort(normalizedScheme, port)) {
            baseUrl.append(":").append(port);
        }
        baseUrl.append(normalizedContextPath);
        return baseUrl.toString();
    }

    static List<String> buildCommand(String nodeBinary, Path scriptPath, String baseUrl, String appPath, Path outputDirectory, String chromePath) {
        String validatedBaseUrl = validateRendererBaseUrl(baseUrl);
        String validatedAppPath = validateRendererAppPath(appPath);

        List<String> command = new ArrayList<>();
        command.add(nodeBinary);
        command.add(scriptPath.toAbsolutePath().toString());
        command.add("--base-url");
        command.add(validatedBaseUrl);
        command.add("--app-path");
        command.add(validatedAppPath);
        command.add("--output-dir");
        command.add(outputDirectory.toAbsolutePath().toString());
        if (chromePath != null && !chromePath.isBlank()) {
            command.add("--chrome-path");
            command.add(chromePath);
        }
        return command;
    }

    static String validateRendererBaseUrl(String rawBaseUrl) {
        if (rawBaseUrl == null || rawBaseUrl.isBlank()) {
            throw new IllegalArgumentException("Renderer base URL must be non-empty");
        }

        URI uri = URI.create(rawBaseUrl.trim());
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Renderer base URL must use http or https");
        }
        if (uri.getHost() == null || !isLocalRendererHost(uri.getHost())) {
            throw new IllegalArgumentException("Renderer base URL host must be local or private");
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

    static boolean isLocalRendererHost(String rawHost) {
        String host = rawHost == null ? "" : rawHost.trim().toLowerCase();
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        if (host.isEmpty()) {
            return false;
        }
        if (Set.of("localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "0.0.0.0", "host.docker.internal", "carlos").contains(host)) {
            return true;
        }
        return host.matches("^(10\\.|192\\.168\\.|172\\.(1[6-9]|2\\d|3[0-1])\\.).*");
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

    private Path prepareRendererRuntime() throws PDFGenerationException {
        Path checkoutRoot = resolveScriptRoot();
        if (checkoutRoot != null) {
            return checkoutRoot.resolve("scripts");
        }
        return extractBundledRendererRuntime();
    }

    private Path resolveScriptRoot() {
        for (Path candidate : candidateRoots(ROOT_PROPERTY, "CARLOS_EFORM_PDF_BROWSER_RENDER_ROOT")) {
            if (Files.isRegularFile(candidate.resolve(SCRIPT_RELATIVE_PATH))) {
                return candidate;
            }
        }
        return null;
    }

    private Path resolveNodeModulesRoot(Path runtimeRoot) throws PDFGenerationException {
        Set<Path> candidates = new LinkedHashSet<>();
        Path runtimeParent = runtimeRoot.getParent();
        if (runtimeParent != null) {
            candidates.add(runtimeParent);
        }
        candidates.add(runtimeRoot);
        candidates.addAll(candidateRoots(NODE_MODULES_ROOT_PROPERTY, "CARLOS_EFORM_PDF_BROWSER_NODE_MODULES_ROOT"));
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate.resolve(PLAYWRIGHT_MODULE_RELATIVE_PATH))) {
                return candidate;
            }
        }
        throw new PDFGenerationException("Unable to locate the Playwright node_modules directory for eForm PDF generation.");
    }

    private List<Path> candidateRoots(String propertyName, String environmentVariableName) {
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        addCandidate(candidates, CarlosProperties.getInstance().getProperty(propertyName));
        addCandidate(candidates, System.getenv(environmentVariableName));
        addCandidate(candidates, System.getProperty(propertyName));
        addCandidate(candidates, System.getProperty("user.dir"));
        addCandidate(candidates, System.getProperty("catalina.base"));
        addCandidate(candidates, System.getProperty("catalina.home"));
        addCandidate(candidates, "/workspace");
        addCandidate(candidates, "/tmp/carlos-develop-clean");
        addAncestorCandidates(candidates, Paths.get(""));
        addCodeSourceCandidates(candidates);
        return new ArrayList<>(candidates);
    }

    private void addCandidate(Set<Path> candidates, String rawCandidate) {
        if (rawCandidate == null || rawCandidate.isBlank()) {
            return;
        }
        try {
            candidates.add(Path.of(rawCandidate.trim()).toAbsolutePath().normalize());
        } catch (RuntimeException e) {
            logger.warn("Ignoring invalid eForm PDF browser renderer root candidate: {}", rawCandidate);
        }
    }

    private void addAncestorCandidates(Set<Path> candidates, Path rawPath) {
        try {
            Path current = rawPath.toAbsolutePath().normalize();
            for (int depth = 0; current != null && depth < 6; depth += 1) {
                candidates.add(current);
                current = current.getParent();
            }
        } catch (RuntimeException e) {
            logger.debug("Ignoring invalid ancestor path candidate {}", rawPath, e);
        }
    }

    private void addCodeSourceCandidates(Set<Path> candidates) {
        try {
            URL location = EFormBrowserPdfRenderer.class.getProtectionDomain().getCodeSource().getLocation();
            if (location == null) {
                return;
            }
            Path codeSourcePath = Path.of(location.toURI()).toAbsolutePath().normalize();
            addAncestorCandidates(candidates, codeSourcePath);
        } catch (URISyntaxException | RuntimeException e) {
            logger.debug("Unable to derive eForm PDF renderer roots from the code source location", e);
        }
    }

    private Path extractBundledRendererRuntime() throws PDFGenerationException {
        try {
            Path runtimeDir = Files.createTempDirectory("eform-browser-pdf-runtime-");
            runtimeDir.toFile().deleteOnExit();
            for (String scriptName : BUNDLED_SCRIPT_NAMES) {
                copyBundledScript(runtimeDir, scriptName);
            }
            return runtimeDir;
        } catch (IOException e) {
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
                    .collect(Collectors.toList());
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

    private static boolean isDefaultPort(String scheme, int port) {
        return ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
    }

    private static void deleteQuietly(Path outputPath) {
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
        try {
            Files.walk(directory)
                    .sorted(Comparator.reverseOrder())
                    .forEach(EFormBrowserPdfRenderer::deleteQuietly);
        } catch (IOException e) {
            logger.debug("Unable to delete temporary browser-rendered capture directory {}", directory, e);
        }
    }

    private static String abbreviate(String processOutput) {
        if (processOutput == null) {
            return "";
        }
        String normalized = processOutput.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= 400) {
            return normalized;
        }
        return normalized.substring(0, 400) + "...";
    }
}
