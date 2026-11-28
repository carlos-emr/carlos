/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.form;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Advisory startup check for {@code form_record_path} when {@code save_as_xml} is enabled.
 *
 * <p>Probes {@code form_record_path} once during Spring context initialization so operators are
 * warned immediately when {@code save_as_xml=true} is configured on storage that supports neither
 * POSIX hard links nor atomic Linux {@code renameat2(RENAME_NOREPLACE)}, without aborting clinical
 * startup.</p>
 *
 * @since 2026-11-27
 */
@Component
public class FormXmlArchiveStartupValidator {

    /**
     * Verifies configured XML archive storage capabilities when {@code save_as_xml} is enabled.
     */
    @PostConstruct
    public void verifyArchiveStorageAtStartup() {
        FormXmlArchive.verifyConfiguredStorageIfEnabled();
    }
}
