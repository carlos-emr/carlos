#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Installed-package rollback coverage for #3915. Uses a trigger scoped to the workflow's
 * synthetic accession to reject metadata after the checksum and raw lab have been inserted.
 * Verifies those writes roll back, then retries the same bytes and checks normal duplicates.
 * Uses the lab-upload workflow's CML_UPLOAD_KEY and LAB_UPLOAD_DOCUMENT_STORE settings.
 * Requires CREATE/DROP TRIGGER permission in the isolated test database.
 */
const { runWorkflow } = require('./lib/workflow-session');
const { workflow } = require('./lab-upload-playwright-checks');

if (require.main === module) {
  runWorkflow('lab-upload-rollback', (session) => workflow(session, { injectFailure: true }));
}
