/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.documentManager.htmlpdf.engine;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import org.openpdf.text.BadElementException;
import org.openpdf.text.Image;
import org.xhtmlrenderer.extend.FontResolver;
import org.xhtmlrenderer.pdf.ITextFSImage;
import org.xhtmlrenderer.pdf.ITextOutputDevice;
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.xhtmlrenderer.pdf.ITextUserAgent;
import org.xhtmlrenderer.resource.ImageResource;
import org.xhtmlrenderer.simple.extend.XhtmlCssOnlyNamespaceHandler;
import org.xhtmlrenderer.util.Configuration;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SSRF-safe {@link ITextUserAgent} for Flying Saucer: it fetches nothing over the network, reads
 * local files only from directories named by the caller, and refuses images that would be costly
 * or are in a format nobody has vetted.
 *
 * <p>Flying Saucer's default user agent opens unrestricted HTTP connections for every resource an
 * HTML document references ({@code <img src>}, {@code <link href>}, CSS {@code @import},
 * {@code url()} and so on), which is server-side request forgery if the HTML is user-controlled
 * (for example {@code http://169.254.169.254/} for cloud metadata) and local file disclosure if it
 * can name {@code file:///etc/passwd}. This subclass closes both:</p>
 *
 * <ol>
 *   <li>{@link #resolveAndOpenStream(String)} lets through only {@code data:} URIs, {@code file:}
 *       URIs and local paths; every other scheme ({@code http:}, {@code https:}, {@code ftp:},
 *       {@code jar:}, protocol-relative {@code //}) returns {@code null}.</li>
 *   <li>{@link #openStream(String)} opens a {@code file:} URI only when its canonical path lies
 *       inside one of the <b>allowed directories the caller passed in</b>. Nothing is implied:
 *       not the base URL's directory, not the JVM working directory, not the temp directory. Which
 *       directories are readable is the caller's explicit decision, so it cannot depend on where
 *       the server happens to be started.</li>
 *   <li>Exactly one other resource is readable: Flying Saucer's own default stylesheet, matched by
 *       its exact URI ({@link #DEFAULT_STYLESHEET_URI}). Without it every page prints its
 *       {@code <title>} and CSS text and loses block and table layout.</li>
 *   <li>{@link #getImageResource(String)} is a safety net for images that reach the renderer
 *       without having been prepared first (CSS backgrounds, and anything the pipeline missed).</li>
 * </ol>
 *
 * <p><b>Logging.</b> The documents are patients' records, so a blocked request is logged with its
 * scheme and, for the network schemes, its host, and never with a path or a query. Exception
 * messages are not logged, only their class names.</p>
 *
 * <p><b>Images.</b> A file over {@link #MAX_FILE_BYTES}, a header declaring more than
 * {@link ImageDecoding#MAX_SOURCE_PIXELS}, a PDF, and any format {@link ImageDecoding} does not read
 * (WebP, HEIC) come back as an empty {@link ImageResource}, which Flying Saucer draws as nothing.
 * A JPEG loads as it is (OpenPDF embeds the bytes without decoding them). Another image above
 * {@link ImageDecoding#MAX_DECODED_PIXELS} is decoded subsampled to fit and keeps its printed size.
 * The agent cannot tell a CSS background from an {@code <img>}, so this cap is the same absolute
 * cap the image planning applies, not a lower one: a lower cap would quietly shrink images the
 * planning has already approved. SVG is passed to Flying Saucer as it is; the planning cleans the
 * SVG files it places in {@code <img>}, and an SVG referenced from a stylesheet is not cleaned
 * (a known limitation).</p>
 *
 * <p>Usage: create renderers with {@link #createRestrictedRenderer(List, FontResolver)} instead of
 * {@code new ITextRenderer()}.</p>
 *
 * @see ITextUserAgent
 * @since 2026-03-04
 */
public final class LocalOnlyUserAgent extends ITextUserAgent {

    private static final Logger logger = MiscUtils.getLogger();

    /** Dots per point of {@link ITextRenderer}'s no-argument constructor. */
    private static final float DEFAULT_DOTS_PER_POINT = 26.666666f;

    /** Dots per pixel of {@link ITextRenderer}'s no-argument constructor. */
    private static final int DEFAULT_DOTS_PER_PIXEL = 20;

    /**
     * The largest image file the agent reads at all. It is above the largest image the pipeline
     * writes itself (a 25-megapixel PNG of noise is under 100 MB) and below what would be a
     * denial of service by file size (spec §Images).
     */
    static final long MAX_FILE_BYTES = 128L << 20;

    /**
     * The URI Flying Saucer loads its built-in stylesheet from, found the way Flying Saucer finds it
     * (its configured path, resolved with the class loader of its namespace handler), or
     * {@code null} if it cannot be found. It is a {@code jar:} URI, which is otherwise blocked, and
     * which the agent opens only on an exact match: any other entry of the same jar stays blocked.
     */
    static final String DEFAULT_STYLESHEET_URI = locateDefaultStylesheet();

    /** Control characters, replaced before a host name is logged (log forging). */
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x1f\\x7f]");

    /** A URI scheme at the start of a string. */
    private static final Pattern SCHEME = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]{0,15}):");

    private static final Set<String> NETWORK_SCHEMES = Set.of("http", "https", "ftp");

    /** Legacy mode only: cached system temp directory (stable after JVM startup). */
    private static final Path CACHED_TMPDIR;

    /** Legacy mode only: cached Catalina work directory (stable after JVM startup). */
    private static final Path CACHED_CATALINA_WORK;

    static {
        Path tmpDir = null;
        String tmp = System.getProperty("java.io.tmpdir");
        if (tmp != null) {
            try {
                tmpDir = Path.of(tmp).normalize().toAbsolutePath();
            } catch (RuntimeException e) {
                logger.warn("Could not parse java.io.tmpdir as an allowed directory ({})", e.getClass().getSimpleName());
            }
        }
        CACHED_TMPDIR = tmpDir;

        Path catalinaWork = null;
        String catalinaBase = System.getProperty("catalina.base");
        if (catalinaBase != null) {
            try {
                catalinaWork = Path.of(catalinaBase, "work").normalize().toAbsolutePath();
            } catch (RuntimeException e) {
                logger.warn("Could not parse the Catalina work directory as an allowed directory ({})",
                        e.getClass().getSimpleName());
            }
        }
        CACHED_CATALINA_WORK = catalinaWork;
    }

    private final int dotsPerPixel;

    /** The directories {@code file:} URIs may point into; {@code null} only in legacy mode. */
    private final List<Path> explicitDirectories;

    /**
     * Creates an agent that reads local files only from the given directories.
     *
     * @param outputDevice the PDF output device
     * @param dotsPerPixel the dots-per-pixel ratio for resource scaling
     * @param allowedDirectories the directories {@code file:} URIs may point into: absolute paths,
     *        checked by canonical path (symbolic links and {@code ..} resolved) on every open
     * @throws NullPointerException if an argument is {@code null}
     */
    public LocalOnlyUserAgent(ITextOutputDevice outputDevice, int dotsPerPixel, List<Path> allowedDirectories) {
        super(outputDevice, dotsPerPixel);
        this.dotsPerPixel = dotsPerPixel;
        this.explicitDirectories = List.copyOf(Objects.requireNonNull(allowedDirectories, "allowedDirectories"));
    }

    /** Legacy mode, for {@link #createRestrictedRenderer()} only: implicit roots, as before. */
    private LocalOnlyUserAgent(ITextOutputDevice outputDevice, int dotsPerPixel) {
        super(outputDevice, dotsPerPixel);
        this.dotsPerPixel = dotsPerPixel;
        this.explicitDirectories = null;
    }

    // ---- streams ----------------------------------------------------------------------------

    /**
     * Resolves and opens a stream for a URI, refusing every external scheme.
     *
     * <p>Allowed: {@code data:} URIs (inline content, no request), {@code file:} URIs and local
     * paths (absolute, relative, dot-relative), which {@link #openStream(String)} then holds to
     * the allowed directories, and the built-in stylesheet ({@link #DEFAULT_STYLESHEET_URI}).
     * Refused: {@code http:}, {@code https:}, {@code ftp:}, {@code jar:} (a {@code jar:https://}
     * URL makes an outbound request), protocol-relative {@code //}, and any other scheme.</p>
     *
     * @param uri the resource URI
     * @return the stream, or {@code null} if the URI is refused or cannot be opened
     */
    // FindSecBugs IMPROPER_UNICODE: case-fold in a trust path; locale-safe hardening tracked in #2496. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-fold in a trust path; locale-safe hardening tracked in #2496")
    @Override
    protected InputStream resolveAndOpenStream(String uri) {
        if (uri == null) {
            return null;
        }
        if (isDefaultStylesheet(uri)) {
            return super.resolveAndOpenStream(uri);
        }

        String lower = uri.toLowerCase(Locale.ROOT);

        // data: URIs hold their content inline; no request is made.
        if (lower.startsWith("data:")) {
            return super.resolveAndOpenStream(uri);
        }

        // A protocol-relative URL (//evil.com) would inherit the scheme of the base URL.
        if (uri.startsWith("//")) {
            logger.warn("Blocked external resource fetch during PDF rendering ({})", describeForLog(uri));
            return null;
        }

        // file: URIs, absolute paths, relative paths and dot-relative paths. A single letter before
        // the colon is a drive letter, not a scheme.
        if (lower.startsWith("file:") || uri.startsWith("/") || uri.startsWith(".")) {
            return super.resolveAndOpenStream(uri);
        }
        int colonIdx = uri.indexOf(':');
        if (colonIdx < 0 || colonIdx == 1) {
            return super.resolveAndOpenStream(uri);
        }

        logger.warn("Blocked external resource fetch during PDF rendering ({})", describeForLog(uri));
        return null;
    }

    /**
     * Opens a resolved URI, holding {@code file:} URIs to the allowed directories and refusing
     * every other scheme again (defence in depth: this is the method that really opens the stream).
     *
     * @param uri the resolved URI
     * @return the stream, or {@code null} if the URI is refused
     * @throws IOException if opening the stream fails
     */
    // FindSecBugs IMPROPER_UNICODE: case-fold in a trust path; locale-safe hardening tracked in #2496. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-fold in a trust path; locale-safe hardening tracked in #2496")
    @Override
    protected InputStream openStream(String uri) throws IOException {
        if (uri != null) {
            if (isDefaultStylesheet(uri)) {
                return super.openStream(uri);
            }
            String lower = uri.toLowerCase(Locale.ROOT);
            if (lower.contains(":") && !lower.startsWith("file:") && !lower.startsWith("data:")) {
                logger.warn("Blocked non-local URI in openStream during PDF rendering ({})", describeForLog(uri));
                return null;
            }
            if (lower.startsWith("file:") && !isAllowedLocalPath(uri)) {
                logger.warn("Blocked file access outside the allowed directories during PDF rendering ({})",
                        describeForLog(uri));
                return null;
            }
        }
        return super.openStream(uri);
    }

    /**
     * Rejects non-{@code file:} base URLs: with an {@code http:} base, a relative path that
     * {@link #resolveAndOpenStream(String)} lets through would resolve to an HTTP URL. Flying
     * Saucer also sets the base to the built-in stylesheet's {@code jar:} URI while it loads it;
     * that call is ignored without a warning and the document's base stays as it was.
     *
     * @param url the base URL ({@code file:} or {@code null})
     */
    // FindSecBugs IMPROPER_UNICODE: case-fold in a trust path; locale-safe hardening tracked in #2496. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-fold in a trust path; locale-safe hardening tracked in #2496")
    @Override
    public void setBaseURL(String url) {
        if (url != null && isDefaultStylesheet(url)) {
            return;
        }
        if (url != null && !url.toLowerCase(Locale.ROOT).startsWith("file:")) {
            logger.warn("Rejected a non-file base URL to prevent SSRF via relative URI resolution ({})",
                    describeForLog(url));
            return;
        }
        super.setBaseURL(url);
    }

    private static boolean isDefaultStylesheet(String uri) {
        return DEFAULT_STYLESHEET_URI != null && DEFAULT_STYLESHEET_URI.equals(uri);
    }

    // ---- containment ------------------------------------------------------------------------

    private boolean isAllowedLocalPath(String uri) {
        try {
            return isInAllowedDirectory(Path.of(new URI(uri)));
        } catch (URISyntaxException | RuntimeException e) {
            logger.warn("Could not parse a file URI for the containment check; the resource is blocked ({})",
                    e.getClass().getSimpleName());
            return false;
        }
    }

    /** Delegates to {@link AllowedDirectories}: canonical paths, fails closed, never logs a path. */
    private boolean isInAllowedDirectory(Path target) {
        return AllowedDirectories.contains(allowedDirectories(), target);
    }

    private List<Path> allowedDirectories() {
        return explicitDirectories != null ? explicitDirectories : legacyDirectories();
    }

    /** Legacy mode: the base URL's directory (set lazily by Flying Saucer), the temp and work directories. */
    private List<Path> legacyDirectories() {
        List<Path> dirs = new ArrayList<>(3);
        String baseUrl = getBaseURL();
        if (baseUrl != null) {
            try {
                dirs.add(Path.of(new URI(baseUrl)).normalize().toAbsolutePath());
            } catch (URISyntaxException | RuntimeException e) {
                logger.warn("Could not parse the base URL as an allowed directory ({})", e.getClass().getSimpleName());
            }
        }
        if (CACHED_TMPDIR != null) {
            dirs.add(CACHED_TMPDIR);
        }
        if (CACHED_CATALINA_WORK != null) {
            dirs.add(CACHED_CATALINA_WORK);
        }
        return Collections.unmodifiableList(dirs);
    }

    // ---- images -----------------------------------------------------------------------------

    /**
     * Loads an image for the renderer, after the checks described in the class summary.
     *
     * @param uriStr the image URI as written in the document ({@code data:}, absolute or relative)
     * @return the image, or an {@link ImageResource} without an image (drawn as nothing) when the
     *         image is refused or cannot be loaded; never {@code null} and never throws
     */
    // FindSecBugs IMPROPER_UNICODE: case-fold of a URI scheme to recognise data: URIs; not a security decision on user text
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-fold of a URI scheme to recognise data: URIs; the file and size checks decide, not this comparison")
    @Override
    public ImageResource getImageResource(String uriStr) {
        if (uriStr == null) {
            return new ImageResource(null, null);
        }
        try {
            ImageResource decided = uriStr.toLowerCase(Locale.ROOT).startsWith("data:")
                    ? vetImageBytes(uriStr, decodeDataUri(uriStr), null)
                    : vetImageFile(uriStr);
            return decided != null ? decided : super.getImageResource(uriStr);
        } catch (IOException | RuntimeException e) {
            logger.warn("An image could not be loaded and is left out of the PDF ({})", e.getClass().getSimpleName());
            return new ImageResource(uriStr, null);
        }
    }

    /** Returns the verdict for a local image file, or {@code null} to let Flying Saucer load it. */
    // FindSecBugs IMPROPER_UNICODE: case-fold of a URI scheme; the containment check decides access, not this comparison
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-fold of a URI scheme; the canonical-path containment check decides access, not this comparison")
    private ImageResource vetImageFile(String uriStr) throws IOException {
        String resolved = resolveURI(uriStr);
        if (resolved == null || !resolved.toLowerCase(Locale.ROOT).startsWith("file:")) {
            // Not a local file: the stream methods refuse it.
            return null;
        }
        Path file;
        try {
            file = Path.of(new URI(resolved));
        } catch (URISyntaxException | RuntimeException e) {
            return new ImageResource(uriStr, null);
        }
        if (!isInAllowedDirectory(file)) {
            logger.warn("Blocked image access outside the allowed directories during PDF rendering (scheme: file)");
            return new ImageResource(uriStr, null);
        }
        if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES) {
            // Missing, a directory, or too large to read at all.
            return new ImageResource(uriStr, null);
        }
        return vetImageBytes(uriStr, null, file);
    }

    /**
     * Decides what to do with an image that is either held in memory ({@code data}) or a file
     * ({@code file}); returns the verdict, or {@code null} to let Flying Saucer load it as it is.
     */
    private ImageResource vetImageBytes(String uriStr, byte[] data, Path file) throws IOException {
        if (data == null && file == null) {
            return new ImageResource(uriStr, null);
        }
        Optional<ImageHeader> header = data != null ? ImageDecoding.readHeader(data) : ImageDecoding.readHeader(file);
        if (header.isEmpty()) {
            // Not a vetted raster format. SVG is Flying Saucer's to draw; a PDF (which it would
            // render as an image), WebP, HEIC and everything else is refused.
            return file != null && looksLikeSvg(file) ? null : new ImageResource(uriStr, null);
        }
        ImageHeader h = header.get();
        if (h.pixels() > ImageDecoding.MAX_SOURCE_PIXELS) {
            logger.warn("Refused an image whose header declares more than {} pixels", ImageDecoding.MAX_SOURCE_PIXELS);
            return new ImageResource(uriStr, null);
        }
        if (h.isJpeg() || h.pixels() <= ImageDecoding.MAX_DECODED_PIXELS) {
            return null;
        }
        // Larger than anything the pipeline decodes: read every n-th pixel and keep the printed size.
        int subsampling = ImageDecoding.subsamplingToFit(h, ImageDecoding.MAX_DECODED_PIXELS);
        BufferedImage reduced = data != null
                ? ImageDecoding.readSubsampled(data, subsampling)
                : ImageDecoding.readSubsampled(file, subsampling);
        try {
            Image image = Image.getInstance(toPng(reduced));
            image.scaleAbsolute((float) h.width() * dotsPerPixel, (float) h.height() * dotsPerPixel);
            return new ImageResource(uriStr, new ITextFSImage(image));
        } catch (BadElementException e) {
            throw new IOException("the reduced image cannot be embedded", e);
        }
    }

    /** Decodes the payload of a {@code data:} URI, or returns {@code null} if it is not a usable base64 image. */
    private static byte[] decodeDataUri(String uri) {
        return DataUris.decodeBase64(uri, MAX_FILE_BYTES).orElse(null);
    }

    private static boolean looksLikeSvg(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            String head = new String(in.readNBytes(1024), StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
            return !head.startsWith("%pdf-") && head.contains("<svg");
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static byte[] toPng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out)) {
            // ImageIO has no PNG writer for some raster types (CMYK); redraw as RGB.
            BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = rgb.createGraphics();
            try {
                g.drawImage(image, 0, 0, null);
            } finally {
                g.dispose();
            }
            out.reset();
            if (!ImageIO.write(rgb, "png", out)) {
                throw new IOException("the reduced image cannot be encoded");
            }
        }
        return out.toByteArray();
    }

    // ---- factories --------------------------------------------------------------------------

    /**
     * Creates an {@link ITextRenderer} whose user agent reads local files only from the given
     * directories and uses the given font resolver.
     *
     * <p>The sizes match {@code ITextRenderer}'s no-argument constructor: 26.666666 dots per point
     * and 20 dots per pixel.</p>
     *
     * @param allowedDirectories the directories {@code file:} URIs may point into
     * @param fontResolver the font resolver; the pipeline's resolver maps every family to a bundled font
     * @return a renderer that makes no network request and reads no file outside the directories
     */
    public static ITextRenderer createRestrictedRenderer(List<Path> allowedDirectories, FontResolver fontResolver) {
        ITextOutputDevice outputDevice = new ITextOutputDevice(DEFAULT_DOTS_PER_POINT);
        LocalOnlyUserAgent userAgent = new LocalOnlyUserAgent(outputDevice, DEFAULT_DOTS_PER_PIXEL, allowedDirectories);
        return new ITextRenderer(DEFAULT_DOTS_PER_POINT, DEFAULT_DOTS_PER_PIXEL, outputDevice, userAgent, fontResolver);
    }

    /**
     * Legacy factory for the two callers that still build their own renderer
     * ({@code ConvertToEdoc} and {@code Doc2PDF}): local files are readable from the base URL's
     * directory, {@code java.io.tmpdir} and the Catalina work directory. The base URL is unknown at
     * this point, which is why these roots are implicit, and why they depend on where the server runs.
     *
     * @return a renderer that makes no network request
     * @deprecated use {@link #createRestrictedRenderer(List, FontResolver)} through
     *             {@code HtmlPdfRenderService}; this is removed with the last legacy caller
     */
    @Deprecated(forRemoval = true)
    public static ITextRenderer createRestrictedRenderer() {
        ITextOutputDevice outputDevice = new ITextOutputDevice(DEFAULT_DOTS_PER_POINT);
        LocalOnlyUserAgent userAgent = new LocalOnlyUserAgent(outputDevice, DEFAULT_DOTS_PER_PIXEL);
        return new ITextRenderer(DEFAULT_DOTS_PER_POINT, DEFAULT_DOTS_PER_PIXEL, outputDevice, userAgent);
    }

    // ---- helpers ----------------------------------------------------------------------------

    private static String locateDefaultStylesheet() {
        try {
            String path = Configuration.valueFor("xr.css.user-agent-default-css") + "XhtmlNamespaceHandler.css";
            URL url = XhtmlCssOnlyNamespaceHandler.class.getResource(path);
            return url == null ? null : url.toExternalForm();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Describes a refused URI for the log without revealing what it pointed at: the scheme, and for
     * the network schemes the host. Never a path, a query or a user name.
     */
    // FindSecBugs IMPROPER_UNICODE: case-fold of a URI scheme for a log message; not a security decision
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-fold of a URI scheme for a log message; not a security decision")
    static String describeForLog(String uri) {
        if (uri == null) {
            return "scheme: none";
        }
        if (uri.startsWith("//")) {
            return "scheme: //";
        }
        Matcher matcher = SCHEME.matcher(uri);
        if (!matcher.find()) {
            return "scheme: none";
        }
        String scheme = matcher.group(1).toLowerCase(Locale.ROOT);
        if (NETWORK_SCHEMES.contains(scheme)) {
            try {
                String host = new URI(uri).getHost();
                if (host != null) {
                    String safeHost = CONTROL_CHARS.matcher(host).replaceAll("_");
                    return "scheme: " + scheme + ", host: " + (safeHost.length() > 253 ? safeHost.substring(0, 253) : safeHost);
                }
            } catch (URISyntaxException e) {
                // fall through: the scheme alone is enough
            }
        }
        return "scheme: " + scheme;
    }
}
