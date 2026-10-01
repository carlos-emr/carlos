/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
package io.github.carlos_emr.carlos.email.core;

import java.io.Serializable;
import java.util.Objects;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.util.WebUtils;

/**
 * Publishes a complete eForm email draft in one session slot. Unlike separate session attributes,
 * the snapshot cannot combine fields from overlapping saves or unrelated patient navigation.
 * This is still a single slot: a later save may replace an earlier draft before preparation.
 * It guarantees consistency, not independent ownership by each browser window.
 */
public final class EmailComposeStaging {
    private static final String ATTRIBUTE = EmailComposeStaging.class.getName() + ".draft";

    private EmailComposeStaging() { }

    /** Immutable, serializable session value; settings defensively copy all attachment ID arrays. */
    public record Draft(String fid, EmailAttachmentSettings settings) implements Serializable {
        public Draft {
            Objects.requireNonNull(settings, "settings");
        }

        @Override
        public String toString() {
            return "EmailComposeStaging.Draft";
        }
    }

    /** Replaces the slot atomically. No database, PDF or network work occurs under this lock. */
    public static void stage(HttpSession session, String fid, EmailAttachmentSettings settings) {
        Draft draft = new Draft(fid, settings);
        synchronized (WebUtils.getSessionMutex(session)) {
            session.setAttribute(ATTRIBUTE, draft);
        }
    }

    /** Takes the whole draft once. Competing preparations cannot each take the same draft. */
    public static Draft take(HttpSession session) {
        synchronized (WebUtils.getSessionMutex(session)) {
            Draft draft = (Draft) session.getAttribute(ATTRIBUTE);
            session.removeAttribute(ATTRIBUTE);
            return draft;
        }
    }
}
