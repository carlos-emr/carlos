/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
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
'use strict';

/** Pin unbilled report coverage to the configured appointment's single-day window. */
function unbilledReportParams(providerNo, appointmentDate) {
  return new URLSearchParams({
    reportAction: 'unbilled',
    providerview: providerNo,
    xml_vdate: appointmentDate,
    xml_appointment_date: appointmentDate,
  });
}

/** An unrelated or ambiguous link cannot prove coverage for the configured visit. */
function isConfiguredUnbilledLink(rawUrl, baseUrl, fixture) {
  try {
    const base = new URL(baseUrl);
    const link = new URL(rawUrl, base);
    if (link.origin !== base.origin || link.pathname !== `${base.pathname.replace(/\/$/, '')}/billing`) return false;
    const expected = {
      billRegion: 'ON',
      appointment_no: fixture.appointmentNo,
      demographic_no: fixture.demographicNo,
      apptProvider_no: fixture.providerNo,
      appointment_date: fixture.appointmentDate,
    };
    return Object.entries(expected).every(([key, value]) => {
      const actual = link.searchParams.getAll(key);
      return actual.length === 1 && actual[0] === String(value);
    });
  } catch {
    return false;
  }
}

module.exports = { unbilledReportParams, isConfiguredUnbilledLink };
