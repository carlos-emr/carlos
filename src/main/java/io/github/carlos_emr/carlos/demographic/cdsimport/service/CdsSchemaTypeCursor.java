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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.xml.XMLConstants;
import org.apache.xerces.xs.XSAttributeUse;
import org.apache.xerces.xs.XSComplexTypeDefinition;
import org.apache.xerces.xs.XSConstants;
import org.apache.xerces.xs.XSElementDeclaration;
import org.apache.xerces.xs.XSModel;
import org.apache.xerces.xs.XSModelGroup;
import org.apache.xerces.xs.XSObjectList;
import org.apache.xerces.xs.XSParticle;
import org.apache.xerces.xs.XSSimpleTypeDefinition;
import org.apache.xerces.xs.XSTypeDefinition;
import org.w3c.dom.TypeInfo;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.NamespaceSupport;

/**
 * Type metadata from the exact compiled validation grammar, independent of PSVI error lists.
 * The XML validator remains responsible for all structural/value/derivation constraints.
 * Cursor state grows with bounded XML depth; child maps grow only with the pinned schema.
 */
final class CdsSchemaTypeCursor {
    private final XSModel model;
    private final NamespaceSupport namespaces = new NamespaceSupport();
    private final Map<String, String> pendingPrefixes = new HashMap<>();
    private final Deque<Frame> stack = new ArrayDeque<>();
    private final Map<XSTypeDefinition, Map<Name, XSElementDeclaration>> children = new IdentityHashMap<>();

    CdsSchemaTypeCursor(XSModel model) { this.model = model; }

    void prefix(String prefix, String uri) { pendingPrefixes.put(prefix, uri); }

    void start(String namespace, String local, Attributes attributes) {
        namespaces.pushContext();
        pendingPrefixes.forEach(namespaces::declarePrefix);
        pendingPrefixes.clear();
        Name name = new Name(namespace, local);
        XSTypeDefinition parent = stack.isEmpty() ? null : stack.peek().type();
        boolean any = parent != null && XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(parent.getNamespace())
                && "anyType".equals(parent.getName());
        XSElementDeclaration declaration = stack.isEmpty() || any
                ? model.getElementDeclaration(local, namespace) : children(parent).get(name);
        // After an invalid placement, Xerces still tries the global declaration.
        // Follow that type for bounded value handling without replacing a local
        // declaration; the validator continues to reject the invalid structure.
        if (declaration == null) declaration = model.getElementDeclaration(local, namespace);
        XSTypeDefinition type = declaration == null ? (any ? parent : null) : declaration.getTypeDefinition();
        String selected = attributes.getValue(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "type");
        if (selected != null) {
            selected = selected.strip();
            int colon = selected.indexOf(':');
            String prefix = colon < 0 ? "" : selected.substring(0, colon);
            String typeName = colon < 0 ? selected : selected.substring(colon + 1);
            String uri = namespaces.getURI(prefix);
            XSTypeDefinition override = model.getTypeDefinition(typeName, uri);
            // Xerces continues with a recognized override even after reporting
            // an invalid derivation or an undeclared element. Follow its type
            // for streaming safety; the validator alone decides validity.
            if (override != null) type = override;
        }
        stack.push(new Frame(type));
    }

    TypeInfo elementType() { return info(stack.peek().type()); }

    TypeInfo attributeType(String namespace, String local) {
        if (stack.peek().type() instanceof XSComplexTypeDefinition complex) {
            XSObjectList uses = complex.getAttributeUses();
            for (int i = 0; i < uses.getLength(); i++) {
                var declaration = ((XSAttributeUse) uses.item(i)).getAttrDeclaration();
                if (new Name(namespace, local).equals(new Name(declaration.getNamespace(), declaration.getName()))) {
                    return info(declaration.getTypeDefinition());
                }
            }
        }
        var global = model.getAttributeDeclaration(local, namespace);
        return global == null ? null : info(global.getTypeDefinition());
    }

    void end() {
        stack.pop();
        namespaces.popContext();
    }

    static boolean identity(TypeInfo info) {
        if (!(info instanceof XSSimpleTypeDefinition simple)) return false;
        if (simple.getBuiltInKind() == XSConstants.ID_DT || simple.getBuiltInKind() == XSConstants.IDREF_DT) return true;
        if (simple.getVariety() == XSSimpleTypeDefinition.VARIETY_LIST) return identity(info(simple.getItemType()));
        if (simple.getVariety() == XSSimpleTypeDefinition.VARIETY_UNION) {
            XSObjectList members = simple.getMemberTypes();
            for (int i = 0; i < members.getLength(); i++) {
                if (identity(info((XSTypeDefinition) members.item(i)))) return true;
            }
        }
        return false;
    }

    private Map<Name, XSElementDeclaration> children(XSTypeDefinition type) {
        if (!(type instanceof XSComplexTypeDefinition complex)) return Map.of();
        return children.computeIfAbsent(type, ignored -> {
            Map<Name, XSElementDeclaration> result = new HashMap<>();
            collect(complex.getParticle(), result);
            return Map.copyOf(result);
        });
    }

    private static void collect(XSParticle particle, Map<Name, XSElementDeclaration> result) {
        if (particle == null) return;
        if (particle.getTerm() instanceof XSElementDeclaration declaration) {
            Name name = new Name(declaration.getNamespace(), declaration.getName());
            XSElementDeclaration prior = result.putIfAbsent(name, declaration);
            if (prior != null && prior.getTypeDefinition() != declaration.getTypeDefinition()) {
                throw new IllegalStateException("CDS_PINNED_SCHEMA_TYPE_AMBIGUOUS");
            }
        } else if (particle.getTerm() instanceof XSModelGroup group) {
            XSObjectList particles = group.getParticles();
            for (int i = 0; i < particles.getLength(); i++) collect((XSParticle) particles.item(i), result);
        } else {
            // The pinned CDS contract contains no wildcards. A changed contract
            // must explicitly add their typing semantics before accepting data.
            throw new IllegalStateException("CDS_PINNED_SCHEMA_WILDCARD_UNSUPPORTED");
        }
    }

    private static TypeInfo info(XSTypeDefinition type) {
        if (type == null) return null;
        if (type instanceof TypeInfo info) return info;
        throw new IllegalStateException("CDS_PINNED_SCHEMA_TYPES_UNAVAILABLE");
    }

    private record Frame(XSTypeDefinition type) { }
    private record Name(String namespace, String local) {
        Name { namespace = namespace == null ? "" : namespace; }
    }
}
