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
package io.github.carlos_emr.carlos.demographic.cds.contract;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import javax.xml.XMLConstants;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.NamespaceSupport;

/**
 * Bounded cursor over metadata from the pinned schemas. The JDK validator remains
 * responsible for structural, value and derivation constraints, including invalid input.
 */
public final class CdsSchemaTypeCursor {
    private final CdsSchemaModel model;
    private final NamespaceSupport namespaces = new NamespaceSupport();
    private final Map<String, String> pendingPrefixes = new HashMap<>();
    private final Deque<Frame> stack = new ArrayDeque<>();

    public CdsSchemaTypeCursor(CdsSchemaModel model) { this.model = model; }
    public void prefix(String prefix, String uri) { pendingPrefixes.put(prefix, uri); }

    public void start(String namespace, String local, Attributes attributes) {
        namespaces.pushContext();
        pendingPrefixes.forEach(namespaces::declarePrefix);
        pendingPrefixes.clear();
        CdsSchemaModel.Type parent = stack.isEmpty() ? null : stack.peek().type();
        boolean any = parent != null && XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(parent.getTypeNamespace())
                && "anyType".equals(parent.getTypeName());
        CdsSchemaModel.Type type = null;
        if (stack.isEmpty() || any) type = model.element(namespace, local);
        else if (parent != null) type = parent.child(namespace, local);
        // Follow global declarations even after invalid placement. Validation still rejects
        // the structure; known type metadata keeps invalid large values bounded as well.
        if (type == null) type = model.element(namespace, local);
        if (type == null && any) type = parent;
        String selected = attributes.getValue(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "type");
        if (selected != null) {
            selected = selected.strip();
            int colon = selected.indexOf(':');
            String prefix = colon < 0 ? "" : selected.substring(0, colon);
            String name = colon < 0 ? selected : selected.substring(colon + 1);
            CdsSchemaModel.Type override = model.type(namespaces.getURI(prefix), name);
            if (override != null) type = override;
        }
        stack.push(new Frame(type));
    }

    public CdsSchemaModel.Type elementType() { return stack.peek().type(); }
    public CdsSchemaModel.Type attributeType(String namespace, String local) {
        CdsSchemaModel.Type type = stack.peek().type();
        CdsSchemaModel.Type attribute = type == null ? null : type.attribute(namespace, local);
        return attribute == null ? model.attribute(namespace, local) : attribute;
    }
    public void end() { stack.pop(); namespaces.popContext(); }
    public static boolean identity(CdsSchemaModel.Type type) { return type != null && type.identity(); }
    private record Frame(CdsSchemaModel.Type type) { }
}
