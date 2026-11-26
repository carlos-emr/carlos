/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.test.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilege;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilegePrimaryKey;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.apache.commons.text.WordUtils;
import org.apache.jasper.servlet.JasperInitializer;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Renders real CARLOS JSPs in an embedded Tomcat with Jasper, so a test asserts the markup a page
 * actually produces instead of its source text.
 *
 * <p>Built for the #6336 render-evidence tests and modelled on
 * {@code ProgramFormControlsIntegrationTest} and {@code ClinicalReportExportJspIntegrationTest}: the
 * same Tomcat, Jasper, jar-scanner and JSTL-jar calls, wrapped once so each render test only states
 * its page, its beans and its request data.</p>
 *
 * <p>What {@link Builder#start()} sets up:</p>
 * <ul>
 *   <li>A temporary web application under the caller's directory. It holds the pages named with
 *       {@link Builder#jsp(String...)} and, transitively, every file they include by a literal path
 *       ({@code <%@ include file="..." %>}, {@code <jsp:include page="..."/>},
 *       {@code <c:import url="..."/>}; relative paths resolve against the including file). It also
 *       holds every {@code WEB-INF/*.tld}, the {@code WEB-INF/tags} tag files, the root
 *       {@code taglibs.jsp}, the JSTL API and implementation jars in {@code WEB-INF/lib}, and any
 *       test-only file given with {@link Builder#file(String, String)}. Files come from
 *       {@code src/main/webapp}; a page not named when building is copied the first time it is
 *       rendered.</li>
 *   <li>A stand-in for {@code /WEB-INF/jsp/error/errorpage.jsp}, the error page every CARLOS JSP
 *       names: the production page needs the whole application, and this one keeps the failure
 *       visible in {@link RenderedPage#failure()} and the body instead of a second error.</li>
 *   <li>A {@link DefaultListableBeanFactory} holding only the beans given with
 *       {@link Builder#bean(Class, Object)}, installed as {@link SpringUtils}' factory until
 *       {@link #close()} puts the previous one back. Pages that call {@code SpringUtils.getBean}
 *       see these beans and nothing else.</li>
 *   <li>Tomcat on an ephemeral 127.0.0.1 port, context path {@value #CONTEXT_PATH}, with the JSP
 *       servlet also mapped to {@code *.jspf} as the production {@code web.xml} maps it.</li>
 * </ul>
 *
 * <p>Each render is a real HTTP request from a new client session to a fixture servlet that runs
 * the test's {@link RequestSetup} on the Tomcat request thread (set request and session attributes
 * there), sets UTF-8 request and response encoding as the production {@code ResponseDefaultsFilter}
 * does, and forwards to the page, as a Struts result does. Requests send
 * {@code Accept-Language: en} unless the test sets that header.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * @TempDir
 * static Path directory;
 * private static JspRenderServer server;
 *
 * @BeforeAll
 * static void start() throws Exception {
 *     server = JspRenderServer.builder(directory)
 *             .jsp("/WEB-INF/jsp/admin/auditLogPurge.jsp")
 *             .bean(SecObjPrivilegeDao.class, JspRenderServer.privilegeDaoGranting("admin", "x"))
 *             .start();
 * }
 *
 * @AfterAll
 * static void stop() throws Exception {
 *     if (server != null) {
 *         server.close();
 *     }
 * }
 *
 * @Test
 * void shouldShowTheMessage_whenTheActionSetsOne() throws Exception {
 *     Document html = server.render("/WEB-INF/jsp/admin/auditLogPurge.jsp", request -> {
 *         request.getSession().setAttribute("userrole", "admin");
 *         request.getSession().setAttribute("user", "999998");
 *         request.setAttribute("msg", "FAKE message");
 *     }).assertOk().document();
 *     assertThat(html.selectFirst("p[style]").text()).isEqualTo("FAKE message");
 * }
 *
 * // Parameters, headers or a POST: describe the request first.
 * JspRenderServer.RenderedPage page = server.request("/WEB-INF/jsp/messenger/SentMessage.jsp")
 *         .header("Accept-Language", "fr-CA")
 *         .parameter("scheduleNav", "1")
 *         .render(request -> request.getSession().setAttribute("user", "999998"));
 * assertThat(page.status()).isEqualTo(302);
 * assertThat(page.header("Location")).hasValue("/carlos/securityError?type=_msg");
 * }</pre>
 *
 * <p>Rules for render tests:</p>
 * <ul>
 *   <li>Mockito's {@code mockStatic} does not reach Tomcat's request threads. Give pages their
 *       collaborators as beans or request/session attributes; plain mocks made on the test thread
 *       are fine.</li>
 *   <li>Only one server should be open at a time in a JVM; servers closed in reverse order of
 *       starting still restore {@link SpringUtils}' factory correctly.</li>
 *   <li>A test-only page given with {@link Builder#file(String, String)} is written as UTF-8, so it
 *       should declare {@code pageEncoding="UTF-8"} if it holds non-ASCII text.</li>
 *   <li>A {@code <jsp:include>} of a Struts route needs a stand-in, given with
 *       {@link Builder#servlet(String, HttpServlet)}.</li>
 * </ul>
 *
 * @since 2026-11-25
 */
public final class JspRenderServer implements AutoCloseable {

    /** Context path of the fixture web application, the same as the packaged deployment's. */
    public static final String CONTEXT_PATH = "/carlos";

    /** The production web application, relative to the Maven module directory tests run in. */
    static final Path SOURCE_ROOT = Path.of("src/main/webapp");

    /** The page every CARLOS {@code errorPage} directive names, directly or through taglibs.jsp. */
    static final String ERROR_PAGE = "WEB-INF/jsp/error/errorpage.jsp";

    /** Request attribute in which the stand-in error page leaves the page's exception for the fixture. */
    static final String FAILURE_ATTRIBUTE = "io.github.carlos_emr.carlos.test.web.JspRenderServer.failure";

    /**
     * Stand-in error page. Jasper forwards a failing page here, the generated error-page code sets
     * status 500, and the exception is copied to {@link #FAILURE_ATTRIBUTE}: Jasper removes its own
     * exception attributes once the error page returns, before the fixture servlet could read them.
     */
    static final String ERROR_PAGE_STAND_IN =
            "<%@ page isErrorPage=\"true\" contentType=\"text/html; charset=UTF-8\" %><%\n"
            + "    request.setAttribute(\"" + FAILURE_ATTRIBUTE + "\", exception);\n"
            + "%><!DOCTYPE html>\n"
            + "<html><body><h1>JspRenderServer stand-in error page</h1><pre id=\"jsp-render-failure\"><%=\n"
            + "    String.valueOf(exception).replace(\"&\", \"&amp;\").replace(\"<\", \"&lt;\").replace(\">\", \"&gt;\")\n"
            + "%></pre></body></html>\n";

    private static final String RENDER_PATH = "/__jsp-render";
    private static final String TOKEN_HEADER = "X-Jsp-Render-Token";
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);

    /** {@code <%@ include file="..." %>}, with or without spaces and in either quote style. */
    private static final Pattern INCLUDE_DIRECTIVE =
            Pattern.compile("<%@\\s*include\\s+file\\s*=\\s*([\"'])(.*?)\\1");
    /** {@code <jsp:include page="..."/>}, whatever attribute comes first. */
    private static final Pattern INCLUDE_ACTION =
            Pattern.compile("<jsp:include\\b[^>]*?\\bpage\\s*=\\s*([\"'])(.*?)\\1");
    /** {@code <c:import url="..."/>} of a page of this application. */
    private static final Pattern IMPORT_ACTION =
            Pattern.compile("<c:import\\b[^>]*?\\burl\\s*=\\s*([\"'])(.*?)\\1");

    private final Path source;
    private final Path webapp;
    private final Map<String, String> files;
    private final Set<String> present = new HashSet<>();
    private final Map<String, PendingRender> pending = new ConcurrentHashMap<>();
    private final BeanFactory previousBeanFactory;
    private Tomcat tomcat;
    private boolean startAttempted;
    private HttpClient client;
    private int port;
    private boolean closed;

    private JspRenderServer(Builder builder) throws Exception {
        source = builder.source;
        files = Map.copyOf(builder.files);
        Path base = Files.createTempDirectory(builder.directory, "jsp-render-");
        webapp = Files.createDirectory(base.resolve("web"));
        assembleWebapp(builder.pages);

        previousBeanFactory = SpringUtils.getBeanFactory();
        ReflectionTestUtils.setField(SpringUtils.class, "beanFactory", builder.beans);
        try {
            tomcat = new Tomcat();
            tomcat.setBaseDir(base.resolve("tomcat").toString());
            tomcat.setPort(0);
            tomcat.getConnector().setProperty("address", "127.0.0.1");
            Context context = tomcat.addContext(CONTEXT_PATH, webapp.toString());
            context.setParentClassLoader(JspRenderServer.class.getClassLoader());
            StandardJarScanner scanner = new StandardJarScanner();
            scanner.setScanClassPath(false);
            context.setJarScanner(scanner);
            context.addServletContainerInitializer(new JasperInitializer(), null);
            Tomcat.initWebappDefaults(context);
            // The production web.xml maps *.jspf to the JSP servlet too, so a
            // <jsp:include page="x.jspf"/> runs the fragment as a page there and here.
            context.addServletMapping("*.jspf", "jsp");
            Tomcat.addServlet(context, "jsp-render", new RenderServlet());
            context.addServletMapping(RENDER_PATH, "jsp-render");
            int stub = 0;
            for (Map.Entry<String, HttpServlet> servlet : builder.servlets.entrySet()) {
                String name = "jsp-render-stub-" + stub++;
                Tomcat.addServlet(context, name, servlet.getValue());
                context.addServletMapping(servlet.getKey(), name);
            }
            startAttempted = true;
            tomcat.start();
            port = tomcat.getConnector().getLocalPort();
            client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
        } catch (Exception | Error failure) {
            try {
                close();
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    /**
     * Starts describing a fixture server.
     *
     * @param directory a directory the test owns, normally a JUnit {@code @TempDir}; each server
     *     creates its own subdirectory, so one directory can serve several servers
     * @return Builder the builder
     */
    public static Builder builder(Path directory) {
        return new Builder(directory);
    }

    /**
     * A {@link SecObjPrivilegeDao} mock that grants {@code role} the {@code privilege} on every
     * security object it is asked about, which is what {@code <security:oscarSec>} and
     * {@code OscarRoleObjectPrivilege} read. Register it with
     * {@code .bean(SecObjPrivilegeDao.class, ...)} and put the same role in the session's
     * {@code userrole} attribute.
     *
     * @param role the role, for example {@code doctor} or {@code admin}
     * @param privilege the privilege, for example {@code x} (all), {@code w} or {@code r}
     * @return SecObjPrivilegeDao the mock
     */
    public static SecObjPrivilegeDao privilegeDaoGranting(String role, String privilege) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(privilege, "privilege");
        SecObjPrivilegeDao privileges = mock(SecObjPrivilegeDao.class);
        when(privileges.findByObjectNames(any())).thenAnswer(invocation -> {
            Collection<String> objectNames = invocation.getArgument(0);
            List<SecObjPrivilege> grants = new ArrayList<>();
            for (String objectName : objectNames) {
                SecObjPrivilege grant = new SecObjPrivilege();
                grant.setId(new SecObjPrivilegePrimaryKey(role, objectName));
                grant.setPrivilege(privilege);
                grant.setPriority(0);
                grants.add(grant);
            }
            return grants;
        });
        return privileges;
    }

    /**
     * Renders a page with the default request: GET, no parameters, {@code Accept-Language: en}.
     *
     * @param jsp the page, for example {@code /WEB-INF/jsp/admin/auditLogPurge.jsp}
     * @param setup sets the request and session attributes the page reads
     * @return RenderedPage the response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting for the response
     */
    public RenderedPage render(String jsp, RequestSetup setup) throws IOException, InterruptedException {
        return request(jsp).render(setup);
    }

    /**
     * Starts describing a request for a page, to add parameters or headers or to send a POST.
     *
     * @param jsp the page, for example {@code /WEB-INF/jsp/fax/EFormMissingContent.jsp}
     * @return Request the request
     */
    public Request request(String jsp) {
        return new Request(Objects.requireNonNull(jsp, "jsp"));
    }

    /**
     * Stops Tomcat and puts back the {@link SpringUtils} bean factory that was installed before
     * {@link Builder#start()}, even if stopping fails. Closing again does nothing.
     *
     * @throws LifecycleException if Tomcat fails to stop
     */
    @Override
    public void close() throws LifecycleException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (client != null) {
                client.close();
            }
            if (tomcat != null && startAttempted) {
                tomcat.stop();
                tomcat.destroy();
            }
        } finally {
            ReflectionTestUtils.setField(SpringUtils.class, "beanFactory", previousBeanFactory);
        }
    }

    /** The temporary web application root, for tests of this fixture. */
    Path webapp() {
        return webapp;
    }

    private void assembleWebapp(List<String> pages) throws IOException, URISyntaxException {
        List<String> infrastructure = new ArrayList<>();
        infrastructure.add("taglibs.jsp");
        if (Files.isDirectory(source.resolve("WEB-INF"))) {
            try (DirectoryStream<Path> tlds = Files.newDirectoryStream(source.resolve("WEB-INF"), "*.tld")) {
                for (Path tld : tlds) {
                    infrastructure.add("WEB-INF/" + tld.getFileName());
                }
            }
        }
        Path tags = source.resolve("WEB-INF/tags");
        if (Files.isDirectory(tags)) {
            try (Stream<Path> tagFiles = Files.walk(tags)) {
                tagFiles.filter(Files::isRegularFile)
                        .map(tag -> source.relativize(tag).toString().replace(File.separatorChar, '/'))
                        .sorted()
                        .forEach(infrastructure::add);
            }
        }
        for (String path : infrastructure) {
            if (Files.isRegularFile(source.resolve(path)) || files.containsKey(path)) {
                assemble(source, webapp, files, present, path);
            }
        }
        for (String path : files.keySet()) {
            assemble(source, webapp, files, present, path);
        }
        for (String page : pages) {
            assemble(source, webapp, files, present, page);
        }
        if (present.add(ERROR_PAGE)) {
            Path errorPage = webapp.resolve(ERROR_PAGE);
            Files.createDirectories(errorPage.getParent());
            Files.writeString(errorPage, ERROR_PAGE_STAND_IN, StandardCharsets.UTF_8);
        }
        Path lib = Files.createDirectories(webapp.resolve("WEB-INF/lib"));
        for (Class<?> type : List.of(jakarta.servlet.jsp.jstl.core.Config.class,
                org.apache.taglibs.standard.tag.rt.fmt.SetBundleTag.class)) {
            Path jar = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
            Files.copy(jar, lib.resolve(jar.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Copies {@code page} into {@code webapp} with every file it includes by a literal path,
     * transitively. A file given in {@code files} is written from that text instead of copied, so
     * a test can supply its own page or replace an included fragment. An included path with no
     * file behind it (a Struts route, for instance) is skipped; Jasper reports it if it matters.
     *
     * @param source the production web application root
     * @param webapp the fixture web application root
     * @param files test-supplied files by web application path
     * @param present paths already in {@code webapp}; updated
     * @param page the page to add
     * @return List the web application paths written by this call, in discovery order
     * @throws IOException if a file cannot be copied
     * @throws IllegalArgumentException if {@code page} is not a file of either source
     */
    static List<String> assemble(Path source, Path webapp, Map<String, String> files, Set<String> present,
            String page) throws IOException {
        String root = normalize(page);
        if (root == null) {
            throw new IllegalArgumentException("Not a path inside the web application: " + page);
        }
        if (!files.containsKey(root) && !Files.isRegularFile(source.resolve(root))) {
            throw new IllegalArgumentException("No such file in " + source + ": " + root);
        }
        List<String> written = new ArrayList<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            String path = queue.removeFirst();
            boolean supplied = files.containsKey(path);
            Path origin = source.resolve(path);
            if (present.contains(path) || (!supplied && !Files.isRegularFile(origin))) {
                continue;
            }
            present.add(path);
            Path target = webapp.resolve(path);
            Files.createDirectories(target.getParent());
            String text;
            if (supplied) {
                text = files.get(path);
                Files.writeString(target, text, StandardCharsets.UTF_8);
            } else {
                Files.copy(origin, target, StandardCopyOption.REPLACE_EXISTING);
                // Include syntax is ASCII; ISO-8859-1 reads any legacy page without a decoding error.
                text = new String(Files.readAllBytes(origin), StandardCharsets.ISO_8859_1);
            }
            written.add(path);
            queue.addAll(literalIncludes(path, text));
        }
        return written;
    }

    /**
     * Finds the files a page includes by a literal path. Values built at run time
     * ({@code <%= %>}, {@code ${}}) and URLs with a scheme are left out; a query string or
     * fragment is dropped.
     *
     * @param page the including file's web application path
     * @param text the including file's text
     * @return List the included files' web application paths
     */
    static List<String> literalIncludes(String page, String text) {
        List<String> includes = new ArrayList<>();
        for (Pattern pattern : List.of(INCLUDE_DIRECTIVE, INCLUDE_ACTION, IMPORT_ACTION)) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                String include = resolveReference(page, matcher.group(2));
                if (include != null) {
                    includes.add(include);
                }
            }
        }
        return includes;
    }

    /**
     * Resolves an include reference: a leading {@code /} is the web application root, anything
     * else is relative to the including file's directory, as Jasper resolves it.
     *
     * @return String the web application path, or {@code null} for a dynamic or external reference
     */
    static String resolveReference(String page, String reference) {
        String value = reference.strip();
        if (value.contains("<%") || value.contains("${") || value.contains("#{") || value.contains(":")) {
            return null;
        }
        int end = value.length();
        for (char stop : new char[] {'?', '#'}) {
            int at = value.indexOf(stop);
            if (at >= 0 && at < end) {
                end = at;
            }
        }
        value = value.substring(0, end);
        if (value.isEmpty()) {
            return null;
        }
        if (value.startsWith("/")) {
            return normalize(value);
        }
        int slash = page.lastIndexOf('/');
        return normalize((slash < 0 ? "" : page.substring(0, slash + 1)) + value);
    }

    /**
     * Normalizes a web application path to its form without a leading slash.
     *
     * @return String the normalized path, or {@code null} if it is empty or leaves the root
     */
    static String normalize(String path) {
        String relative = path.startsWith("/") ? path.substring(1) : path;
        if (relative.isEmpty() || relative.indexOf('\\') >= 0) {
            return null;
        }
        Path normalized;
        try {
            normalized = Path.of(relative).normalize();
        } catch (InvalidPathException notAPath) {
            return null;
        }
        String result = normalized.toString().replace(File.separatorChar, '/');
        if (normalized.isAbsolute() || result.isEmpty() || result.equals("..") || result.startsWith("../")) {
            return null;
        }
        return result;
    }

    private synchronized String include(String jsp) throws IOException {
        String path = normalize(jsp);
        if (path == null) {
            throw new IllegalArgumentException("Not a path inside the web application: " + jsp);
        }
        if (!present.contains(path)) {
            assemble(source, webapp, files, present, path);
        }
        return path;
    }

    private RenderedPage send(Request request, RequestSetup setup) throws IOException, InterruptedException {
        Objects.requireNonNull(setup, "setup");
        if (closed) {
            throw new IllegalStateException("The JspRenderServer is closed");
        }
        String page = include(request.jsp);
        String token = UUID.randomUUID().toString();
        PendingRender render = new PendingRender("/" + page, setup);
        pending.put(token, render);
        try {
            String form = encode(request.parameters);
            URI uri = URI.create("http://127.0.0.1:" + port + CONTEXT_PATH + RENDER_PATH
                    + (request.post || form.isEmpty() ? "" : "?" + form));
            HttpRequest.Builder http = HttpRequest.newBuilder(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .header(TOKEN_HEADER, token);
            if (request.headers.keySet().stream().noneMatch("Accept-Language"::equalsIgnoreCase)) {
                http.header("Accept-Language", "en");
            }
            request.headers.forEach(http::header);
            if (request.post) {
                http.header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                        .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8));
            } else {
                http.GET();
            }
            HttpResponse<byte[]> response = client.send(http.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (render.setupFailure != null) {
                throw new IllegalStateException("The request setup for " + page + " failed on the server thread",
                        render.setupFailure);
            }
            return new RenderedPage(uri, response.statusCode(), response.headers(),
                    new String(response.body(), StandardCharsets.UTF_8), render.failure);
        } finally {
            pending.remove(token);
        }
    }

    private static String encode(List<Map.Entry<String, String>> parameters) {
        StringJoiner encoded = new StringJoiner("&");
        for (Map.Entry<String, String> parameter : parameters) {
            encoded.add(URLEncoder.encode(parameter.getKey(), StandardCharsets.UTF_8) + "="
                    + URLEncoder.encode(parameter.getValue(), StandardCharsets.UTF_8));
        }
        return encoded.toString();
    }

    /** Sets request and session state on the Tomcat request thread using the test class loader. */
    @FunctionalInterface
    public interface RequestSetup {
        /**
         * Prepares the request, for example with {@code request.setAttribute(...)} or
         * {@code request.getSession().setAttribute(...)}.
         *
         * @param request the request about to be forwarded to the page
         * @throws Exception any failure, rethrown on the test thread by the render call
         */
        void prepare(HttpServletRequest request) throws Exception;
    }

    /** Describes a fixture server; {@link #start()} builds and starts it. */
    public static final class Builder {
        private final Path directory;
        private Path source = SOURCE_ROOT;
        private final List<String> pages = new ArrayList<>();
        private final Map<String, String> files = new LinkedHashMap<>();
        private final DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        private final Map<String, HttpServlet> servlets = new LinkedHashMap<>();

        private Builder(Path directory) {
            this.directory = Objects.requireNonNull(directory, "directory");
        }

        /**
         * Copies pages from {@code src/main/webapp} with everything they include by a literal path.
         * Any file of the web application may be named, with or without a leading slash.
         *
         * @param paths web application paths, for example {@code /WEB-INF/jsp/messenger/SentMessage.jsp}
         * @return Builder this builder
         */
        public Builder jsp(String... paths) {
            pages.addAll(List.of(paths));
            return this;
        }

        /**
         * Writes a test-only file into the web application, for example a wrapper page that declares
         * the scriptlet variables a fragment expects, or a stand-in for an included fragment. It
         * replaces the production file at that path, and its own literal includes are copied too.
         *
         * @param path the web application path
         * @param content the file's text, written as UTF-8
         * @return Builder this builder
         */
        public Builder file(String path, String content) {
            String normalized = normalize(path);
            if (normalized == null) {
                throw new IllegalArgumentException("Not a path inside the web application: " + path);
            }
            files.put(normalized, Objects.requireNonNull(content, "content"));
            return this;
        }

        /**
         * Registers a bean under the name {@code SpringUtils.getBean(type)} looks up first: the
         * type's simple name with its first letter in lower case.
         *
         * @param type the type pages ask {@link SpringUtils} for
         * @param bean the bean, usually a Mockito mock
         * @param <T> the bean type
         * @return Builder this builder
         */
        public <T> Builder bean(Class<T> type, T bean) {
            return bean(WordUtils.uncapitalize(type.getSimpleName()), bean);
        }

        /**
         * Registers a bean under an explicit name, for pages that call
         * {@code SpringUtils.getBean(String)}.
         *
         * @param name the bean name
         * @param bean the bean
         * @return Builder this builder
         */
        public Builder bean(String name, Object bean) {
            beans.registerSingleton(name, Objects.requireNonNull(bean, "bean"));
            return this;
        }

        /**
         * Maps a test servlet, for example a stand-in for a Struts route that a page includes with
         * {@code <jsp:include page="/some/Route"/>}.
         *
         * @param urlPattern the servlet URL pattern, for example {@code /demographic/ViewSomething}
         * @param servlet the servlet
         * @return Builder this builder
         */
        public Builder servlet(String urlPattern, HttpServlet servlet) {
            servlets.put(Objects.requireNonNull(urlPattern, "urlPattern"), Objects.requireNonNull(servlet, "servlet"));
            return this;
        }

        /** Copies from another web application root instead of {@code src/main/webapp}; for this fixture's own tests. */
        Builder source(Path root) {
            source = Objects.requireNonNull(root, "root");
            return this;
        }

        /**
         * Builds the web application, installs the beans and starts Tomcat. If any step fails,
         * everything already done is undone, including the bean factory swap.
         *
         * @return JspRenderServer the running server; close it when done
         * @throws Exception if the web application cannot be built or Tomcat cannot start
         */
        public JspRenderServer start() throws Exception {
            return new JspRenderServer(this);
        }
    }

    /** A request for one page; {@link #render(RequestSetup)} sends it. */
    public final class Request {
        private final String jsp;
        private final List<Map.Entry<String, String>> parameters = new ArrayList<>();
        private final Map<String, String> headers = new LinkedHashMap<>();
        private boolean post;

        private Request(String jsp) {
            this.jsp = jsp;
        }

        /**
         * Adds a request parameter, sent in the query string (or the body of a {@link #post()}).
         * A forward keeps the original request's parameters, so the page reads it with
         * {@code request.getParameter}.
         *
         * @param name the parameter name
         * @param value the parameter value, URL-encoded as UTF-8 by this method
         * @return Request this request
         */
        public Request parameter(String name, String value) {
            parameters.add(Map.entry(name, value));
            return this;
        }

        /**
         * Sets a request header, for example {@code Accept-Language}.
         *
         * @param name the header name
         * @param value the header value
         * @return Request this request
         */
        public Request header(String name, String value) {
            headers.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
            return this;
        }

        /**
         * Sends the request as a form POST instead of a GET.
         *
         * @return Request this request
         */
        public Request post() {
            post = true;
            return this;
        }

        /**
         * Sends the request and waits for the page.
         *
         * @param setup sets the request and session attributes the page reads
         * @return RenderedPage the response
         * @throws IOException if the request fails
         * @throws InterruptedException if interrupted while waiting for the response
         * @throws IllegalStateException if {@code setup} threw; the cause is its exception
         */
        public RenderedPage render(RequestSetup setup) throws IOException, InterruptedException {
            return send(this, setup);
        }
    }

    /** One response, with the server-side exception the page raised, if any. */
    public static final class RenderedPage {
        private static final int DESCRIBED_BODY_LENGTH = 8000;

        private final URI uri;
        private final int status;
        private final HttpHeaders headers;
        private final String body;
        private final Throwable failure;
        private Document document;

        private RenderedPage(URI uri, int status, HttpHeaders headers, String body, Throwable failure) {
            this.uri = uri;
            this.status = status;
            this.headers = headers;
            this.body = body;
            this.failure = failure;
        }

        /** @return int the HTTP status */
        public int status() {
            return status;
        }

        /** @return HttpHeaders all response headers */
        public HttpHeaders headers() {
            return headers;
        }

        /**
         * @param name a header name, in any case
         * @return Optional the header's first value
         */
        public Optional<String> header(String name) {
            return headers.firstValue(name);
        }

        /** @return String the body, decoded as UTF-8 */
        public String body() {
            return body;
        }

        /** @return Document the body parsed by Jsoup (parsed once, then shared) */
        public Document document() {
            if (document == null) {
                document = Jsoup.parse(body, uri.toString());
            }
            return document;
        }

        /** @return Optional the exception the page or its forward raised on the server, if any */
        public Optional<Throwable> failure() {
            return Optional.ofNullable(failure);
        }

        /**
         * Fails unless the page rendered with HTTP 200 and raised nothing. The error carries the
         * status, the server-side exception and the start of the body.
         *
         * @return RenderedPage this page
         */
        public RenderedPage assertOk() {
            if (status != HttpServletResponse.SC_OK || failure != null) {
                throw new AssertionError("The page did not render: " + this);
            }
            return this;
        }

        @Override
        public String toString() {
            StringBuilder description = new StringBuilder("HTTP ").append(status).append(" for ").append(uri);
            if (failure != null) {
                StringWriter trace = new StringWriter();
                failure.printStackTrace(new PrintWriter(trace));
                description.append(System.lineSeparator()).append("Server-side failure: ").append(trace);
            }
            description.append(System.lineSeparator())
                    .append(body.length() > DESCRIBED_BODY_LENGTH ? body.substring(0, DESCRIBED_BODY_LENGTH) + "..." : body);
            return description.toString();
        }
    }

    private static final class PendingRender {
        private final String page;
        private final RequestSetup setup;
        private volatile Throwable setupFailure;
        private volatile Throwable failure;

        private PendingRender(String page, RequestSetup setup) {
            this.page = page;
            this.setup = setup;
        }
    }

    /** Runs the test's setup on the request thread and forwards to the page, as a Struts result does. */
    private final class RenderServlet extends HttpServlet {
        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String token = request.getHeader(TOKEN_HEADER);
            PendingRender render = token == null ? null : pending.get(token);
            if (render == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Not a JspRenderServer request");
                return;
            }
            // What ResponseDefaultsFilter does for every production request before Struts forwards.
            if (request.getCharacterEncoding() == null) {
                request.setCharacterEncoding(StandardCharsets.UTF_8.name());
            }
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            // Mockito can cache classes generated by setup. Keep those classes in the test loader,
            // which stays available after the fixture stops Tomcat and discards its webapp loader.
            Thread requestThread = Thread.currentThread();
            ClassLoader webappLoader = requestThread.getContextClassLoader();
            try {
                requestThread.setContextClassLoader(JspRenderServer.class.getClassLoader());
                render.setup.prepare(request);
            } catch (Exception | Error failure) {
                render.setupFailure = failure;
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            } finally {
                requestThread.setContextClassLoader(webappLoader);
            }
            try {
                request.getRequestDispatcher(render.page).forward(request, response);
            } catch (ServletException | IOException | RuntimeException | Error failure) {
                render.failure = failure;
                throw failure;
            }
            // A page with an errorPage directive reaches the stand-in error page instead of throwing.
            // The response is only suspended by the forward, not finished, so this is recorded
            // before the client can have read the end of the body.
            if (request.getAttribute(FAILURE_ATTRIBUTE) instanceof Throwable failure) {
                render.failure = failure;
            }
        }
    }
}
