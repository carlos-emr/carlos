/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.appt.status.web;

/**
 * Test-only action that lets the real Struts interceptor stack bind inherited
 * appointment-status properties without executing production action logic.
 */
public class AppointmentStatusBindingTestAction extends AppointmentStatus2Action {

    @Override
    public String execute() {
        return NONE;
    }
}
