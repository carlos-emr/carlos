/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.eform.util;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

import jakarta.servlet.ServletRequest;

import io.github.carlos_emr.carlos.utility.LocaleUtils;

/**
 * The messages staff see when an eForm cannot be fully rendered, or its PDF cannot be produced: on the
 * missing-content pages (Add to Documents, Download, Fax), in the document preview, and in the eForm page's
 * error and warning alerts. Each is a key in {@code oscarResources} (#6162); they were English-only before.
 *
 * <p>The text is resolved with {@link LocaleUtils#resolveBundleLocale}: the first Accept-Language preference that
 * has a shipped bundle, otherwise English. For every shipped language this picks the same bundle as the JSPs'
 * own JSTL negotiation, so a message appears in the same language as the labels around it. The English here is
 * identical to the {@code oscarResources_en} value and is what a lookup that cannot find the key returns.</p>
 *
 * @since 2026-11-12
 */
public enum EFormRenderMessage {
    /** The missing-content page after Add to Documents. */
    NOT_ADDED_TO_DOCUMENTS("eform.renderMissingContent.msgNotAddedToDocuments",
            "This eForm could not be fully rendered, so it was not added to the patient's documents."
                    + " Review the omissions below before archiving it."),
    /** The missing-content page after Download. */
    NOT_DOWNLOADED("eform.renderMissingContent.msgNotDownloaded",
            "Some content of this eForm could not be rendered. Review the omissions below before downloading it."),
    /** The fax missing-content page. */
    NOT_FAXED("fax.eformMissingContent.msgNotFaxed",
            "This eForm could not be fully rendered because required content or behavior is missing. "
                    + "You can fax it only after approving the listed issues, but the document may be incomplete."),
    /** Add to Documents failed (for example, the render browser could not be reached). */
    EDOC_FAILURE("eform.edoc.failure",
            "This eForm (and attachments, if applicable) could not be added to this patient’s documents."),
    /** Download failed. */
    DOWNLOAD_FAILURE("eform.download.failure",
            "This eForm (and attachments, if applicable) could not be downloaded."),
    /** The eForm was saved, but its PDF preview failed. */
    PREVIEW_NOT_GENERATED("eform.save.previewWarning",
            "This eForm was saved, but its PDF preview could not be generated."),
    /** Document preview: the render is incomplete and needs approval. */
    PREVIEW_MISSING_CONTENT("eform.preview.msgMissingContent",
            "This eForm could not be fully rendered because required content or behavior is missing. "
                    + "You can render it only after approving the listed issues, but the document may be incomplete."),
    /** Document preview: the render failed. */
    PREVIEW_RENDER_FAILED("eform.preview.msgRenderFailed", "Failed to render eForm PDF."),
    /** Document preview: no render slot was free. */
    PREVIEW_RENDER_BUSY("eform.preview.msgRenderBusy", "The eForm renderer is busy. Waiting for a preview slot."),
    /** Document preview: the approval token was invalid or expired. */
    PREVIEW_APPROVAL_INVALID("eform.preview.msgApprovalInvalid",
            "The incomplete-render approval is invalid or expired. Render the preview again.");

    private final String key;
    private final String english;

    EFormRenderMessage(String key, String english) {
        this.key = key;
        this.english = english;
    }

    /** The resource-bundle key. */
    public String key() {
        return key;
    }

    /** The English text, as in {@code oscarResources_en}. */
    public String english() {
        return english;
    }

    /**
     * The message in the language the request's pages are shown in.
     *
     * @param request the current request; {@code null} gives English
     * @return the translated text, or the English when the key or bundle cannot be found
     */
    public String text(ServletRequest request) {
        // The bundle LocaleUtils negotiates against (its static initializer names it), so the two cannot drift.
        String bundle = LocaleUtils.BASE_NAME;
        if (bundle == null) {
            return english;
        }
        try {
            return ResourceBundle.getBundle(bundle, LocaleUtils.resolveBundleLocale(request)).getString(key);
        } catch (MissingResourceException _) {
            return english;
        }
    }
}
