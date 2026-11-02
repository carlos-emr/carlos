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

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Streaming classification metadata read from the same hash-pinned XSD bytes as validation.
 * This is deliberately not an XSD validator: the JDK validates structure, derivation and
 * values. Unsupported schema constructs fail closed; metadata never grows with input size.
 */
final class CdsSchemaModel {
    private static final String XSD = XMLConstants.W3C_XML_SCHEMA_NS_URI;
    private final Map<Name, Type> types;
    private final Map<Name, Type> elements;
    private final Map<Name, Type> attributes;

    private CdsSchemaModel(Builder builder) {
        types = Map.copyOf(builder.types);
        Map<Name, Type> global = new HashMap<>();
        builder.elements.forEach((name, element) -> global.put(name, builder.declaration(element)));
        elements = Map.copyOf(global);
        attributes = Map.copyOf(builder.attributes);
    }

    static CdsSchemaModel read(byte[] main, byte[] datatypes) {
        try {
            Builder builder = new Builder();
            builder.register(main);
            builder.register(datatypes);
            for (Element declaration : builder.declarations) builder.type(declaration);
            return new CdsSchemaModel(builder);
        } catch (Exception failure) {
            throw new IllegalStateException("CDS_PINNED_SCHEMA_METADATA_UNAVAILABLE", failure);
        }
    }

    Type type(String namespace, String local) { return types.get(new Name(namespace, local)); }
    Type element(String namespace, String local) { return elements.get(new Name(namespace, local)); }
    Type attribute(String namespace, String local) { return attributes.get(new Name(namespace, local)); }

    /** Only classification and descriptor data; no validator implementation objects escape. */
    static final class Type {
        private final Name name;
        private final boolean simple;
        private boolean atomic;
        private String builtIn = "";
        private String whitespace = "preserve";
        private boolean lengthFacet;
        private boolean otherFacet;
        private boolean identity;
        private Map<Name, Type> children = Map.of();
        private Map<Name, Type> attributes = Map.of();

        private Type(Name name, boolean simple) {
            this.name = name;
            this.simple = simple;
        }
        String getTypeName() { return name.local(); }
        String getTypeNamespace() { return name.namespace(); }
        boolean simple() { return simple; }
        boolean atomic() { return atomic; }
        String builtIn() { return builtIn; }
        String whitespace() { return whitespace; }
        boolean lengthFacet() { return lengthFacet; }
        boolean otherFacet() { return otherFacet; }
        boolean identity() { return identity; }
        Type child(String namespace, String local) { return children.get(new Name(namespace, local)); }
        Type attribute(String namespace, String local) { return attributes.get(new Name(namespace, local)); }
        boolean plainString() { return Set.of("string", "normalizedString", "token").contains(builtIn); }
    }

    private record Name(String namespace, String local) {
        Name { namespace = namespace == null ? "" : namespace; }
    }

    private static final class Builder {
        private final Map<Name, Type> types = new HashMap<>();
        private final Map<Name, Element> elements = new HashMap<>();
        private final Map<Name, Type> attributes = new HashMap<>();
        private final Map<Element, Type> declaredTypes = new IdentityHashMap<>();
        private final Set<Element> completed = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<Element> building = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        private final List<Element> declarations = new ArrayList<>();

        private Builder() {
            Type any = new Type(new Name(XSD, "anyType"), false);
            types.put(any.name, any);
            String names = "anySimpleType string boolean decimal float double duration dateTime time date "
                    + "gYearMonth gYear gMonthDay gDay gMonth hexBinary base64Binary anyURI QName NOTATION "
                    + "normalizedString token language NMTOKEN NMTOKENS Name NCName ID IDREF IDREFS ENTITY ENTITIES "
                    + "integer nonPositiveInteger negativeInteger long int short byte nonNegativeInteger "
                    + "unsignedLong unsignedInt unsignedShort unsignedByte positiveInteger";
            Set<String> lists = Set.of("NMTOKENS", "IDREFS", "ENTITIES");
            Set<String> bounded = Set.of("language", "NMTOKEN", "Name", "NCName", "integer",
                    "nonPositiveInteger", "negativeInteger", "long", "int", "short", "byte",
                    "nonNegativeInteger", "unsignedLong", "unsignedInt", "unsignedShort", "unsignedByte", "positiveInteger");
            for (String name : names.split(" ")) {
                Type type = new Type(new Name(XSD, name), true);
                type.atomic = !lists.contains(name) && !name.equals("anySimpleType");
                type.builtIn = name;
                type.whitespace = name.equals("string") || name.equals("anySimpleType") ? "preserve"
                        : name.equals("normalizedString") ? "replace" : "collapse";
                type.otherFacet = bounded.contains(name);
                type.lengthFacet = lists.contains(name);
                type.identity = Set.of("ID", "IDREF", "IDREFS").contains(name);
                types.put(type.name, type);
            }
            String xsi = XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI;
            attributes.put(new Name(xsi, "type"), required(new Name(XSD, "QName")));
            attributes.put(new Name(xsi, "nil"), required(new Name(XSD, "boolean")));
            attributes.put(new Name(xsi, "noNamespaceSchemaLocation"), required(new Name(XSD, "anyURI")));
            Type locations = new Type(new Name(xsi, "#AnonType_schemaLocation"), true);
            locations.whitespace = "collapse";
            attributes.put(new Name(xsi, "schemaLocation"), locations);
        }

        private void register(byte[] bytes) throws Exception {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Element schema = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)).getDocumentElement();
            if (!XSD.equals(schema.getNamespaceURI()) || !"schema".equals(schema.getLocalName())) throw unsupported();
            for (Element element : children(schema)) {
                switch (element.getLocalName()) {
                    case "simpleType", "complexType" -> {
                        Name name = new Name(namespace(element), element.getAttribute("name"));
                        Type type = new Type(name, element.getLocalName().equals("simpleType"));
                        if (name.local().isEmpty() || types.putIfAbsent(name, type) != null) throw unsupported();
                        declaredTypes.put(element, type);
                        declarations.add(element);
                    }
                    case "element" -> {
                        Name name = new Name(namespace(element), element.getAttribute("name"));
                        if (name.local().isEmpty() || elements.putIfAbsent(name, element) != null) throw unsupported();
                    }
                    case "annotation", "import" -> { /* Resolved only from the two verified local schemas. */ }
                    default -> throw unsupported();
                }
            }
        }

        private Type required(Name name) {
            Type result = types.get(name);
            if (result == null) throw unsupported();
            return result;
        }

        private Type declaration(Element element) {
            if (element.hasAttribute("ref")) {
                Element global = elements.get(qname(element, element.getAttribute("ref")));
                if (global == null) throw unsupported();
                return declaration(global);
            }
            if (element.hasAttribute("type")) return required(qname(element, element.getAttribute("type")));
            for (Element child : children(element)) {
                if (Set.of("simpleType", "complexType").contains(child.getLocalName())) return type(child);
            }
            return required(new Name(XSD, element.getLocalName().equals("attribute") ? "anySimpleType" : "anyType"));
        }

        private Type type(Element declaration) {
            Type type = declaredTypes.computeIfAbsent(declaration,
                    node -> new Type(new Name(namespace(node), anonymousName(node)), node.getLocalName().equals("simpleType")));
            if (completed.contains(declaration)) return type;
            if (!building.add(declaration)) {
                if (type.simple) throw unsupported();
                return type;
            }
            if (type.simple) readSimpleType(declaration, type);
            else readComplexType(declaration, type);
            completed.add(declaration);
            building.remove(declaration);
            return type;
        }

        private void readSimpleType(Element declaration, Type type) {
            List<Element> content = children(declaration).stream().filter(e -> !e.getLocalName().equals("annotation")).toList();
            if (content.size() != 1 || !content.getFirst().getLocalName().equals("restriction")) throw unsupported();
            Element restriction = content.getFirst();
            Type base = baseType(restriction);
            if (!base.simple) throw unsupported();
            type.atomic = base.atomic;
            type.builtIn = base.builtIn;
            type.whitespace = base.whitespace;
            type.lengthFacet = base.lengthFacet;
            type.otherFacet = base.otherFacet;
            type.identity = base.identity;
            for (Element facet : children(restriction)) readFacet(facet, type);
        }

        private Type baseType(Element restriction) {
            Type base = required(qname(restriction, restriction.getAttribute("base")));
            for (Element named : declarations) {
                if (declaredTypes.get(named) == base) return type(named);
            }
            return base;
        }

        private static void readFacet(Element facet, Type type) {
            switch (facet.getLocalName()) {
                case "annotation" -> { /* Documentation has no streaming classification. */ }
                case "whiteSpace" -> {
                    String value = facet.getAttribute("value");
                    if (!Set.of("preserve", "replace", "collapse").contains(value)) throw unsupported();
                    type.whitespace = value;
                }
                case "length", "minLength", "maxLength" -> type.lengthFacet = true;
                case "pattern", "enumeration", "minInclusive", "maxInclusive", "minExclusive", "maxExclusive",
                        "totalDigits", "fractionDigits" -> type.otherFacet = true;
                default -> throw unsupported();
            }
        }

        private void readComplexType(Element declaration, Type type) {
            Map<Name, Type> elements = new HashMap<>();
            Map<Name, Type> attributes = new HashMap<>();
            collect(declaration, elements, attributes);
            type.children = Map.copyOf(elements);
            type.attributes = Map.copyOf(attributes);
        }

        private void collect(Element parent, Map<Name, Type> elements, Map<Name, Type> attributes) {
            for (Element child : children(parent)) {
                switch (child.getLocalName()) {
                    case "sequence", "choice", "all" -> collect(child, elements, attributes);
                    case "element", "attribute" -> collectDeclaration(child, elements, attributes);
                    case "annotation" -> { /* Documentation does not declare fields. */ }
                    default -> throw unsupported();
                }
            }
        }

        private void collectDeclaration(Element child, Map<Name, Type> elements, Map<Name, Type> attributes) {
            Name name = declarationName(child);
            Map<Name, Type> destination = child.getLocalName().equals("attribute") ? attributes : elements;
            Type declared = declaration(child);
            Type prior = destination.putIfAbsent(name, declared);
            if (name.local().isEmpty() || prior != null && prior != declared) throw unsupported();
        }

        private static Name declarationName(Element child) {
            if (child.hasAttribute("ref")) return qname(child, child.getAttribute("ref"));
            Element schema = child.getOwnerDocument().getDocumentElement();
            boolean attribute = child.getLocalName().equals("attribute");
            String form = child.hasAttribute("form") ? child.getAttribute("form")
                    : schema.getAttribute(attribute ? "attributeFormDefault" : "elementFormDefault");
            String namespace = form.equals("qualified") ? namespace(child) : "";
            return new Name(namespace, child.getAttribute("name"));
        }

        private static String anonymousName(Element type) {
            StringBuilder name = new StringBuilder("#AnonType_");
            for (Node node = type.getParentNode(); node instanceof Element element; node = node.getParentNode()) {
                name.append(element.getAttribute("name"));
            }
            return name.toString();
        }

        private static String namespace(Element element) {
            return element.getOwnerDocument().getDocumentElement().getAttribute("targetNamespace");
        }

        private static Name qname(Element element, String value) {
            int colon = value.indexOf(':');
            String prefix = colon < 0 ? null : value.substring(0, colon);
            String uri = element.lookupNamespaceURI(prefix);
            return new Name(uri, colon < 0 ? value : value.substring(colon + 1));
        }

        private static List<Element> children(Element parent) {
            List<Element> result = new ArrayList<>();
            for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (node instanceof Element element) {
                    if (!XSD.equals(element.getNamespaceURI())) throw unsupported();
                    result.add(element);
                }
            }
            return result;
        }

        private static IllegalStateException unsupported() {
            return new IllegalStateException("CDS_PINNED_SCHEMA_METADATA_UNSUPPORTED");
        }
    }
}
