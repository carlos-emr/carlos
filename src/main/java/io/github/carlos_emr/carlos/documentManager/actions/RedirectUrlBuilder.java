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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.documentManager.actions;

import org.owasp.encoder.Encode;

/**
 * Fluent builder for redirect URLs in the documentManager POST actions.
 * Null-valued parameters are skipped; non-null values are URL-component
 * encoded via {@link Encode#forUriComponent(String)}.
 */
final class RedirectUrlBuilder {

    private final StringBuilder url;
    private String sep = "?";

    RedirectUrlBuilder(String basePath) {
        this.url = new StringBuilder(basePath);
    }

    RedirectUrlBuilder param(String name, String value) {
        if (value != null) {
            url.append(sep).append(name).append('=').append(Encode.forUriComponent(value));
            sep = "&";
        }
        return this;
    }

    @Override
    public String toString() {
        return url.toString();
    }
}
