/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.sms.service;

import java.util.regex.Pattern;

/**
 * One login field an SMS provider needs, such as an API user or an API password, as Administration &gt; SMS
 * shows it. Every value is stored encrypted in {@code sms_config} and never shown back.
 *
 * @param name     the field's key: values are stored under it and posted as {@code credential.<PROVIDER>.<name>}; a letter
 *                 followed by up to 63 letters, digits or underscores
 * @param labelKey the {@code oscarResources} key of the field's label, which the provider adds to every bundle
 * @param required whether sending can be switched on while the field has no value
 * @since 2026-10-08
 */
public record SmsCredentialField(String name, String labelKey, boolean required) {
    private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    private static final Pattern MESSAGE_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+");

    public SmsCredentialField {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("SMS credential field name must be a letter followed by letters, "
                    + "digits or underscores, at most 64 in all");
        }
        if (labelKey == null || !MESSAGE_KEY.matcher(labelKey).matches()) {
            throw new IllegalArgumentException("SMS credential field label must be a dotted message key");
        }
    }
}
