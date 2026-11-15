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
package io.github.carlos_emr.carlos.demographic.cdsimport.support;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The namespace and name accounting of one import parse. It keeps the parser's memory from
 * growing with hostile name variety, but counts only what a schema-valid file fixes:
 *
 * <ul>
 *   <li>distinct namespace URIs: a CDS file uses three (cds, cds_dt and the XML Schema instance);
 *   <li>prefixes in scope at one time, along the current element path: a writer may declare its
 *       prefixes on the root, redeclare one on every element or invent a new one for each
 *       declaration, since none of that adds a prefix in scope at once;
 *   <li>distinct expanded names (namespace URI and local name, and processing-instruction
 *       targets): the schema defines a few hundred, and prefixes do not multiply them.
 * </ul>
 *
 * <p>The prefixes a document declares over its whole length are not counted: the XSD does not
 * constrain them. Each name and prefix is still held to the configured length.
 *
 * @since 2026-11-14
 */
public final class CdsXmlNameAccount {
    private final int maximumAliases;
    private final int maximumNames;
    private final int maximumNameCharacters;
    private final int maximumUriCharacters;
    private final Map<String, Integer> inScope = new HashMap<>();
    private final Set<String> uris = new HashSet<>();
    private final Set<String> names = new HashSet<>();

    /**
     * @param maximumAliases prefixes in scope at one time, and distinct namespace URIs
     * @param maximumNames distinct expanded names and processing-instruction targets
     * @param maximumNameCharacters one qualified name, prefix or target
     * @param maximumUriCharacters one namespace URI
     */
    public CdsXmlNameAccount(
            int maximumAliases,
            int maximumNames,
            int maximumNameCharacters,
            int maximumUriCharacters) {
        if (maximumAliases < 1
                || maximumNames < 1
                || maximumNameCharacters < 1
                || maximumUriCharacters < 1)
            throw new IllegalArgumentException("CDS_XML_NAME_ACCOUNT_INVALID");
        this.maximumAliases = maximumAliases;
        this.maximumNames = maximumNames;
        this.maximumNameCharacters = maximumNameCharacters;
        this.maximumUriCharacters = maximumUriCharacters;
    }

    /**
     * A prefix mapping comes into scope (before the element that declares it).
     *
     * @return false when it passes a bound ({@code CDS_XML_NAMESPACE_RESOURCE_LIMIT})
     */
    public boolean declare(String prefix, String uri) {
        if (prefix.length() > maximumNameCharacters || uri.length() > maximumUriCharacters)
            return false;
        // A prefix redeclared on a nested element is one prefix in scope, not two.
        inScope.merge(prefix, 1, Integer::sum);
        if (uris.add(uri) && uris.size() > maximumAliases) return false;
        return inScope.size() <= maximumAliases;
    }

    /** A prefix mapping goes out of scope (after the element that declared it ends). */
    public void undeclare(String prefix) {
        inScope.computeIfPresent(prefix, (ignored, count) -> count == 1 ? null : count - 1);
    }

    /**
     * An element or attribute name, or a processing-instruction target (empty namespace, the
     * target as its local and qualified name).
     *
     * @return false when it passes a bound ({@code CDS_XML_NAME_RESOURCE_LIMIT})
     */
    public boolean name(String uri, String local, String qualified) {
        if (qualified.length() > maximumNameCharacters) return false;
        names.add(uri + '\u0000' + local);
        return names.size() <= maximumNames;
    }
}
