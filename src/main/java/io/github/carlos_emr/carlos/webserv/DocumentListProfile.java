/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv;

import io.github.carlos_emr.carlos.utility.AsciiStrings;
import java.util.List;
import java.util.Map;
import org.apache.cxf.binding.soap.SoapFault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptorChain;

/**
 * What the three bulk {@code DocumentWs} operations put in a page.
 *
 * <p>A request without the {@value #HEADER} header is an existing client and keeps the published
 * contract: every document carries its file content inline, and a document whose stored file cannot
 * be read is left out of the page, exactly as before the header existed. The header is an opt-in
 * for clients written against it; it never changes what an unmodified client receives.</p>
 */
public enum DocumentListProfile {
    /** No header sent: inline content; an unreadable document is left out of the page, as it always was. */
    PUBLISHED_DEFAULT(null, true, false),
    /** Opt-in: document metadata only; no stored file is read. */
    METADATA("metadata", false, false),
    /** Opt-in: inline content, and the whole response faults rather than leave an unreadable document out. */
    LEGACY_INLINE("legacy-inline", true, true);

    public static final String HEADER = "X-Carlos-Document-List-Profile";
    private static final String KEY = DocumentListProfile.class.getName();
    private final String wireName;
    private final boolean inline;
    private final boolean faultsOnUnreadableContent;

    DocumentListProfile(String wireName, boolean inline, boolean faultsOnUnreadableContent) {
        this.wireName = wireName;
        this.inline = inline;
        this.faultsOnUnreadableContent = faultsOnUnreadableContent;
    }

    public boolean inline() { return inline; }

    /** Whether one unreadable stored file fails the whole page instead of removing that document from it. */
    public boolean faultsOnUnreadableContent() { return faultsOnUnreadableContent; }

    /** The header value that selects this profile; {@code null} for the header-less default, which has none. */
    public String wireName() { return wireName; }

    public static DocumentListProfile selected(Message message) {
        if (message == null || message.getExchange() == null) return null;
        Object value = message.getExchange().get(KEY);
        return value instanceof DocumentListProfile profile ? profile : null;
    }

    /**
     * @param values the header's values, or {@code null} when the request did not carry the header
     * @return the header-less default for {@code null}; otherwise the one named profile
     * @throws SoapFault (Client) for a blank, unknown, combined or repeated value
     */
    public static DocumentListProfile parse(List<String> values) {
        if (values == null) return PUBLISHED_DEFAULT;
        if (values.size() == 1) {
            for (DocumentListProfile profile : values()) {
                if (profile.wireName != null && profile.wireName.equals(values.getFirst())) return profile;
            }
        }
        throw new SoapFault("Unsupported document list profile", SoapFault.FAULT_CODE_CLIENT);
    }

    public static DocumentListProfile current() {
        Message message = PhaseInterceptorChain.getCurrentMessage();
        if (message == null) return PUBLISHED_DEFAULT;
        @SuppressWarnings("unchecked")
        Map<String, List<String>> headers = (Map<String, List<String>>) message.get(Message.PROTOCOL_HEADERS);
        List<String> values = null;
        if (headers != null) {
            for (var header : headers.entrySet()) {
                if (!AsciiStrings.equalsIgnoreCase(HEADER, header.getKey())) continue;
                if (values != null || header.getValue() == null) return parse(List.of());
                values = header.getValue();
            }
        }
        DocumentListProfile profile = parse(values);
        if (message.getExchange() != null) message.getExchange().put(KEY, profile);
        return profile;
    }
}
