/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal.web;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import jakarta.servlet.ServletContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * The version query ({@code ?v=}) on the "Send to myCarlos" scripts and stylesheet, wherever a page
 * loads them.
 *
 * <p>Static files carry no {@code Cache-Control}, so a browser may keep using its copy of an unchanged
 * URL after an upgrade. On 10 Nov 2026 a browser kept the previous {@code mycarlos-send.js} after
 * the dialog shipped: the dialog's script found no {@code CarlosMyCarlosSend}, and the Send button fell
 * back to opening the standalone page in a new tab. The query is a short hash of the three files' own
 * bytes, so it changes exactly when one of them does. A browser then fetches them again after any
 * change, and keeps its cached copies otherwise.</p>
 *
 * <p>The hash is worked out once per web application start and kept as a context attribute.</p>
 *
 * @since 2026-11-10
 */
public final class MyCarlosSendAssets {

    /** The assets, as the pages reference them under the context path. */
    static final List<String> PATHS = List.of(
            "/share/javascript/demographic/mycarlos-send.js",
            "/share/javascript/demographic/mycarlos-send-dialog.js",
            "/share/css/mycarlos-send.css");

    static final String ATTRIBUTE = MyCarlosSendAssets.class.getName() + ".version";

    /** Hex characters kept from the SHA-256: 48 bits tell builds apart. */
    private static final int LENGTH = 12;

    private MyCarlosSendAssets() {
    }

    /**
     * @param context the web application's context
     * @return 12 lowercase hex characters, the same for every page until the application restarts
     */
    public static String version(ServletContext context) {
        Object kept = context.getAttribute(ATTRIBUTE);
        if (kept instanceof String version) {
            return version;
        }
        String version = hash(context);
        context.setAttribute(ATTRIBUTE, version);
        return version;
    }

    /** SHA-256 over each asset's path and bytes; a missing or unreadable one counts as empty. */
    static String hash(ServletContext context) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", impossible);
        }
        for (String path : PATHS) {
            digest.update(path.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            try (InputStream in = context.getResourceAsStream(path)) {
                if (in != null) {
                    digest.update(in.readAllBytes());
                }
            } catch (IOException unreadable) {
                MiscUtils.getLogger().warn("Could not read {} for its version query", path, unreadable);
            }
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest()).substring(0, LENGTH);
    }
}
