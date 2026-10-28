/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.io.IOException;

/**
 * AUTHORIZATION EXCEPTION (Ben's decision D141, clinic code v1, carlos-emr/mycarlos#141).
 *
 * <p>The myCarlos device-code job emails a patient the code that approves their new device. It sends
 * as the built-in system provider ({@value Provider#SYSTEM_PROVIDER_NO}), which has email rights but
 * no eDoc rights, and every sent email is archived as a patient eDoc. This scope lets exactly that
 * email be archived without giving the system provider any right: only the device-code job opens it,
 * on its own thread, around its one send, and it covers an archive only when all of these hold:
 * <ul>
 * <li>the scope is open, on the thread that opened it;</li>
 * <li>the login is the system provider;</li>
 * <li>the stored email log is a {@link EmailLog.TransactionType#MYCARLOS_CODE} email, a type no
 * compose request can choose ({@code EmailData.parseTransactionType} never returns it);</li>
 * <li>the email is for the patient the scope was opened for;</li>
 * <li>the email has no attachments.</li>
 * </ul>
 * The archive's patient-record access check still applies. Document storage accepts the scope only
 * while that archive call is storing its own document. Every other email, login and caller is
 * unchanged.
 */
public final class DeviceCodeEmailScope implements AutoCloseable {
    private static final ThreadLocal<DeviceCodeEmailScope> CURRENT = new ThreadLocal<>();

    private final int demographicNo;
    private final Thread owner;
    private boolean archiving;
    private boolean closed;

    private DeviceCodeEmailScope(int demographicNo) {
        this.demographicNo = demographicNo;
        this.owner = Thread.currentThread();
    }

    /**
     * Opens the scope on this thread for one device code email to one patient. Only the device-code
     * job calls this, with try-with-resources around its send.
     *
     * @throws IllegalStateException when a scope is already open on this thread
     */
    public static DeviceCodeEmailScope open(int demographicNo) {
        if (demographicNo <= 0) {
            throw new IllegalArgumentException("device code email scope needs a patient");
        }
        if (CURRENT.get() != null) {
            throw new IllegalStateException("device code email scope is already open on this thread");
        }
        DeviceCodeEmailScope scope = new DeviceCodeEmailScope(demographicNo);
        CURRENT.set(scope);
        return scope;
    }

    /** @return whether a scope is open on this thread; the job checks it is not, before and after each send */
    public static boolean isOpen() {
        return current() != null;
    }

    @Override
    public void close() {
        closed = true;
        archiving = false;
        if (CURRENT.get() == this) {
            CURRENT.remove();
        }
    }

    private static DeviceCodeEmailScope current() {
        DeviceCodeEmailScope scope = CURRENT.get();
        return scope != null && !scope.closed && scope.owner == Thread.currentThread() ? scope : null;
    }

    private static boolean system(LoggedInInfo login) {
        return login != null && Provider.SYSTEM_PROVIDER_NO.equals(login.getLoggedInProviderNo());
    }

    /** Archive side: whether this stored email may be archived without the eDoc write right. */
    static boolean coversArchive(LoggedInInfo login, EmailLog emailLog, boolean hasAttachments) {
        DeviceCodeEmailScope scope = current();
        return scope != null && system(login) && emailLog != null
                && emailLog.getTransactionType() == EmailLog.TransactionType.MYCARLOS_CODE
                && emailLog.getDemographic() != null
                && Integer.valueOf(scope.demographicNo).equals(emailLog.getDemographic().getDemographicNo())
                && !hasAttachments;
    }

    @FunctionalInterface
    interface DocumentStore<T> {
        T store() throws IOException;
    }

    /** Runs the archive's own document store, the only time document storage accepts the scope. */
    static <T> T archiving(DocumentStore<T> store) throws IOException {
        DeviceCodeEmailScope scope = current();
        if (scope == null) {
            throw new IllegalStateException("device code email scope is not open on this thread");
        }
        scope.archiving = true;
        try {
            return store.store();
        } finally {
            scope.archiving = false;
        }
    }

    /** Document side: whether this is the device code email's archived copy being stored now. */
    static boolean coversDocument(LoggedInInfo login, Document document) {
        DeviceCodeEmailScope scope = current();
        return scope != null && scope.archiving && system(login) && document != null
                && OutboundEmailArchiveServiceImpl.DOCUMENT_DOCTYPE.equals(document.getDoctype())
                && OutboundEmailArchiveServiceImpl.DOCUMENT_CLASS.equals(document.getDocClass())
                && OutboundEmailArchiveServiceImpl.DOCUMENT_SOURCE.equals(document.getSource());
    }
}
