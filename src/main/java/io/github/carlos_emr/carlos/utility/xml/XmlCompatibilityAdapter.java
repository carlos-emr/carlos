/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Licensed under the GNU General Public License, version 2 or later.
 */
package io.github.carlos_emr.carlos.utility.xml;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

/**
 * An XML wire-compatibility conversion. REST JSON retains its existing Jackson conversion;
 * {@code RestXmlAnnotationIntrospector} deliberately excludes only this adapter family.
 *
 * @param <V> XML value type
 * @param <B> application value type
 */
public abstract class XmlCompatibilityAdapter<V, B> extends XmlAdapter<V, B> {
}
