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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.support;

/**
 * Numbers in HTML attributes ({@code rowspan}, {@code colspan}, {@code size}, {@code cols}), read
 * the way a browser reads them.
 *
 * @since 2026-10-29
 */
final class HtmlAttributeNumbers {

    private HtmlAttributeNumbers() {
    }

    /**
     * The value of a non-negative integer attribute: optional leading white space and plus sign,
     * then digits; anything after the digits is ignored ({@code "3px"} is 3). It saturates instead of
     * overflowing, so an absurd value stays absurd and a caller can clamp it.
     *
     * @param value the attribute value
     * @return the number, {@link Long#MAX_VALUE} when it does not fit, or {@code -1} when there is no
     *         number (empty, a minus sign, letters first)
     */
    static long leadingNumber(String value) {
        int i = 0;
        int length = value.length();
        while (i < length && isAsciiWhitespace(value.charAt(i))) {
            i++;
        }
        if (i < length && value.charAt(i) == '+') {
            i++;
        }
        if (i >= length || value.charAt(i) < '0' || value.charAt(i) > '9') {
            return -1;
        }
        long number = 0;
        for (; i < length && value.charAt(i) >= '0' && value.charAt(i) <= '9'; i++) {
            number = number > (Long.MAX_VALUE - 9) / 10 ? Long.MAX_VALUE : number * 10 + (value.charAt(i) - '0');
        }
        return number;
    }

    private static boolean isAsciiWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\f' || c == '\r';
    }
}
