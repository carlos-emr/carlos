#!/usr/bin/env python3
"""
JRXML v6-to-v7 Converter for CARLOS EMR

Converts JasperReports 6.x JRXML files to 7.x format.
JR7 replaced Apache Commons Digester with Jackson XML serialization,
breaking backward compatibility of .jrxml files.

Key transformations:
  1. Remove XML namespace declarations from root element
  2. Convert element tags (staticText, textField, etc.) to <element kind="...">
  3. Flatten <reportElement> attributes onto parent element
  4. Flatten <textElement> and <font> attributes onto parent element
  5. Rename expression tags (textFieldExpression -> expression, etc.)
  6. Rename boolean attribute prefixes (isBold -> bold, etc.)
  7. Restructure band sections (single-band sections lose <band> wrapper)
  8. Rename text alignment attributes (textAlignment -> hTextAlign, etc.)

Usage:
    python3 scripts/convert_jrxml_v6_to_v7.py [--dry-run] [file_or_directory...]

    If no arguments given, converts all .jrxml files under src/.
    --dry-run shows what would change without modifying files.

References:
    - https://github.com/TIBCOSoftware/jasperreports/issues/442
    - https://jasperreports.sourceforge.net/README.html
"""

import sys
import os
import re
import glob
import argparse
import copy
from xml.etree import ElementTree as ET

# JR6 namespace
JR_NS = "http://jasperreports.sourceforge.net/jasperreports"
JR_NS_URI = f"{{{JR_NS}}}"

# Element types that become <element kind="...">
ELEMENT_TYPES = {
    "staticText", "textField", "image", "line", "rectangle",
    "subreport", "frame", "ellipse", "break", "genericElement",
    "componentElement", "crosstab",
}

# Sections with single bands (band wrapper removed in JR7, height promoted)
SINGLE_BAND_SECTIONS = {
    "title", "pageHeader", "columnHeader", "columnFooter",
    "pageFooter", "lastPageFooter", "summary", "noData", "background",
}

# Expression tags that become just <expression>
EXPRESSION_RENAMES = {
    "textFieldExpression": "expression",
    "imageExpression": "expression",
    "subreportExpression": "expression",
    "subreportParameterExpression": "expression",
    "anchorNameExpression": "anchorExpression",
    "hyperlinkReferenceExpression": "hyperlinkReferenceExpression",
    "hyperlinkAnchorExpression": "hyperlinkAnchorExpression",
    "hyperlinkPageExpression": "hyperlinkPageExpression",
    "hyperlinkTooltipExpression": "hyperlinkTooltipExpression",
    "printWhenExpression": "printWhenExpression",
    "patternExpression": "patternExpression",
    "connectionExpression": "connectionExpression",
    "dataSourceExpression": "dataSourceExpression",
}

# Boolean attribute prefixes to strip ("is" prefix removed)
BOOL_ATTR_RENAMES = {
    "isBold": "bold",
    "isItalic": "italic",
    "isUnderline": "underline",
    "isStrikeThrough": "strikeThrough",
    "isPdfEmbedded": "pdfEmbedded",
    "isDefault": "default",
    "isBlankWhenNull": "blankWhenNull",
    "isSplitAllowed": "splitAllowed",
    "isStretchWithOverflow": "textAdjust",  # "true" -> textAdjust="StretchHeight"; "false" -> dropped
}

# Text alignment renames
ALIGNMENT_RENAMES = {
    "textAlignment": "hTextAlign",
    "verticalAlignment": "vTextAlign",
}

# Font attribute renames (from <font> element to parent attributes)
FONT_ATTR_RENAMES = {
    "size": "fontSize",
    "fontName": "fontName",
    "pdfFontName": "pdfFontName",
    "pdfEncoding": "pdfEncoding",
    # boolean attrs handled by BOOL_ATTR_RENAMES
}

# Subreport child renames
SUBREPORT_CHILD_RENAMES = {
    "subreportParameter": "parameter",
}


def strip_ns(tag):
    """Remove namespace prefix from tag name."""
    if tag.startswith(JR_NS_URI):
        return tag[len(JR_NS_URI):]
    return tag


def make_tag(local_name):
    """Create a tag with namespace (for input matching)."""
    return f"{JR_NS_URI}{local_name}"


def convert_font_size(value):
    """Convert integer font size to decimal (e.g., '14' -> '14.0')."""
    if value and '.' not in value:
        try:
            return f"{float(value):.1f}"
        except ValueError:
            pass
    return value


def process_report_element(elem, parent_attribs):
    """Extract reportElement attributes and add to parent_attribs dict."""
    for key, value in elem.attrib.items():
        attr_name = strip_ns(key)
        # Rename boolean attributes
        if attr_name in BOOL_ATTR_RENAMES:
            renamed = BOOL_ATTR_RENAMES[attr_name]
            if renamed == "textAdjust":
                if value == "true":
                    parent_attribs[renamed] = "StretchHeight"
                continue
            parent_attribs[renamed] = value
        else:
            parent_attribs[attr_name] = value


def process_text_element(elem, parent_attribs):
    """Extract textElement attributes and font child attributes."""
    for key, value in elem.attrib.items():
        attr_name = strip_ns(key)
        if attr_name in ALIGNMENT_RENAMES:
            parent_attribs[ALIGNMENT_RENAMES[attr_name]] = value
        elif attr_name == "markup":
            parent_attribs["markup"] = value
        elif attr_name == "rotation":
            parent_attribs["rotation"] = value
        elif attr_name in BOOL_ATTR_RENAMES:
            parent_attribs[BOOL_ATTR_RENAMES[attr_name]] = value
        else:
            parent_attribs[attr_name] = value

    # Process <font> child
    font_elem = elem.find(make_tag("font"))
    if font_elem is None:
        font_elem = elem.find("font")
    if font_elem is not None:
        for key, value in font_elem.attrib.items():
            attr_name = strip_ns(key)
            if attr_name in FONT_ATTR_RENAMES:
                new_name = FONT_ATTR_RENAMES[attr_name]
                if new_name == "fontSize":
                    value = convert_font_size(value)
                parent_attribs[new_name] = value
            elif attr_name in BOOL_ATTR_RENAMES:
                parent_attribs[BOOL_ATTR_RENAMES[attr_name]] = value
            else:
                parent_attribs[attr_name] = value

    # Process <paragraph> child - keep as nested element (returned)
    para_elem = elem.find(make_tag("paragraph"))
    if para_elem is None:
        para_elem = elem.find("paragraph")
    return para_elem


def process_element(elem, ns_aware=True):
    """Convert a JR6 element (staticText, textField, etc.) to JR7 format.

    Returns a new Element in JR7 format, or None if not a convertible element.
    """
    tag = strip_ns(elem.tag)
    if tag not in ELEMENT_TYPES:
        return None

    # Start building new element attributes
    new_attribs = {"kind": tag}

    # Get attributes from the element tag itself (e.g., textField's isBlankWhenNull)
    for key, value in elem.attrib.items():
        attr_name = strip_ns(key)
        if attr_name in BOOL_ATTR_RENAMES:
            renamed = BOOL_ATTR_RENAMES[attr_name]
            if renamed == "textAdjust":
                if value == "true":
                    new_attribs[renamed] = "StretchHeight"
                # Drop "false" — it's the default
                continue
            new_attribs[renamed] = value
        elif attr_name == "class":
            pass  # Drop class attribute (removed in JR7)
        elif attr_name == "isUsingCache":
            new_attribs["usingCache"] = value
        else:
            new_attribs[attr_name] = value

    # Children to keep in the new element
    kept_children = []
    paragraph_elem = None

    for child in elem:
        child_tag = strip_ns(child.tag)

        if child_tag == "reportElement":
            process_report_element(child, new_attribs)
            # reportElement may have printWhenExpression child
            for sub in child:
                sub_tag = strip_ns(sub.tag)
                if sub_tag == "printWhenExpression":
                    new_sub = ET.Element("printWhenExpression")
                    new_sub.text = sub.text
                    new_sub.tail = sub.tail
                    kept_children.append(new_sub)
                elif sub_tag == "property":
                    new_sub = ET.Element("property")
                    for k, v in sub.attrib.items():
                        new_sub.set(strip_ns(k), v)
                    new_sub.text = sub.text
                    new_sub.tail = sub.tail
                    kept_children.append(new_sub)

        elif child_tag == "textElement":
            paragraph_elem = process_text_element(child, new_attribs)

        elif child_tag == "font":
            # Font at element level (not inside textElement)
            for key, value in child.attrib.items():
                attr_name = strip_ns(key)
                if attr_name in FONT_ATTR_RENAMES:
                    new_name = FONT_ATTR_RENAMES[attr_name]
                    if new_name == "fontSize":
                        value = convert_font_size(value)
                    new_attribs[new_name] = value
                elif attr_name in BOOL_ATTR_RENAMES:
                    new_attribs[BOOL_ATTR_RENAMES[attr_name]] = value
                else:
                    new_attribs[attr_name] = value

        elif child_tag in EXPRESSION_RENAMES:
            new_child = ET.Element(EXPRESSION_RENAMES[child_tag])
            new_child.text = child.text
            new_child.tail = child.tail
            # Drop class attribute from expressions
            for k, v in child.attrib.items():
                if strip_ns(k) != "class":
                    new_child.set(strip_ns(k), v)
            kept_children.append(new_child)

        elif child_tag in SUBREPORT_CHILD_RENAMES:
            # subreportParameter -> parameter
            new_child = ET.Element(SUBREPORT_CHILD_RENAMES[child_tag])
            for k, v in child.attrib.items():
                new_child.set(strip_ns(k), v)
            new_child.text = child.text
            new_child.tail = child.tail
            # Process children of subreportParameter
            for sub in child:
                sub_tag = strip_ns(sub.tag)
                if sub_tag in EXPRESSION_RENAMES:
                    new_sub = ET.Element(EXPRESSION_RENAMES[sub_tag])
                    new_sub.text = sub.text
                    new_sub.tail = sub.tail
                    new_child.append(new_sub)
                else:
                    new_sub = copy_element_stripped(sub)
                    new_child.append(new_sub)
            kept_children.append(new_child)

        elif child_tag == "box":
            kept_children.append(copy_element_stripped(child))

        elif child_tag == "text":
            kept_children.append(copy_element_stripped(child))

        elif child_tag in ELEMENT_TYPES:
            # Nested element (e.g., inside frame)
            converted = process_element(child, ns_aware)
            if converted is not None:
                kept_children.append(converted)

        elif child_tag == "returnValue":
            kept_children.append(copy_element_stripped(child))

        elif child_tag == "property":
            new_child = ET.Element("property")
            for k, v in child.attrib.items():
                new_child.set(strip_ns(k), v)
            new_child.text = child.text
            new_child.tail = child.tail
            kept_children.append(new_child)

        elif child_tag == "propertyExpression":
            kept_children.append(copy_element_stripped(child))

        else:
            # Keep unknown children as-is (stripped of namespace)
            kept_children.append(copy_element_stripped(child))

    # Build the new <element> tag
    new_elem = ET.Element("element")
    # Set attributes in a reasonable order
    attr_order = ["kind", "uuid", "positionType", "stretchType", "mode",
                  "x", "y", "width", "height",
                  "forecolor", "backcolor", "key",
                  "hTextAlign", "vTextAlign",
                  "fontName", "fontSize", "bold", "italic", "underline", "strikeThrough",
                  "pdfFontName", "pdfEncoding", "pdfEmbedded",
                  "markup", "rotation", "textAdjust", "blankWhenNull",
                  "pattern", "usingCache",
                  "scaleImage", "hAlign", "vAlign", "onErrorType",
                  "evaluationTime", "evaluationGroup",
                  "removeLineWhenBlank", "printRepeatedValues",
                  "printWhenDetailOverflows", "printInFirstWholeBand",
                  "printWhenGroupChanges", "isRemoveLineWhenBlank",
                  "splitAllowed"]

    ordered_attribs = {}
    for key in attr_order:
        if key in new_attribs:
            ordered_attribs[key] = new_attribs.pop(key)
    # Add remaining attributes
    ordered_attribs.update(new_attribs)

    for k, v in ordered_attribs.items():
        new_elem.set(k, v)

    # Add paragraph if present
    if paragraph_elem is not None:
        new_para = copy_element_stripped(paragraph_elem)
        new_elem.append(new_para)

    # Add kept children
    for child in kept_children:
        new_elem.append(child)

    return new_elem


def copy_element_stripped(elem):
    """Deep copy an element, stripping namespace from all tags."""
    new_tag = strip_ns(elem.tag)
    new_elem = ET.Element(new_tag)
    for k, v in elem.attrib.items():
        new_elem.set(strip_ns(k), v)
    new_elem.text = elem.text
    new_elem.tail = elem.tail
    for child in elem:
        new_elem.append(copy_element_stripped(child))
    return new_elem


def process_style(elem):
    """Convert a <style> element to JR7 format."""
    new_elem = ET.Element("style")
    for key, value in elem.attrib.items():
        attr_name = strip_ns(key)
        if attr_name in BOOL_ATTR_RENAMES:
            attr_name = BOOL_ATTR_RENAMES[attr_name]
        elif attr_name in FONT_ATTR_RENAMES:
            attr_name = FONT_ATTR_RENAMES[attr_name]
            if attr_name == "fontSize":
                value = convert_font_size(value)
        elif attr_name == "size":
            attr_name = "fontSize"
            value = convert_font_size(value)
        new_elem.set(attr_name, value)

    # Process style children
    for child in elem:
        child_tag = strip_ns(child.tag)
        if child_tag == "box":
            new_elem.append(copy_element_stripped(child))
        elif child_tag == "paragraph":
            new_elem.append(copy_element_stripped(child))
        elif child_tag == "pen":
            new_elem.append(copy_element_stripped(child))
        elif child_tag == "conditionalStyle":
            new_elem.append(process_conditional_style(child))
        else:
            new_elem.append(copy_element_stripped(child))

    new_elem.text = elem.text
    new_elem.tail = elem.tail
    return new_elem


def process_conditional_style(elem):
    """Convert a <conditionalStyle> element."""
    new_elem = ET.Element("conditionalStyle")
    for k, v in elem.attrib.items():
        new_elem.set(strip_ns(k), v)
    new_elem.text = elem.text
    new_elem.tail = elem.tail
    for child in elem:
        child_tag = strip_ns(child.tag)
        if child_tag == "conditionExpression":
            new_child = ET.Element("conditionExpression")
            new_child.text = child.text
            new_child.tail = child.tail
            new_elem.append(new_child)
        elif child_tag == "style":
            new_elem.append(process_style(child))
        else:
            new_elem.append(copy_element_stripped(child))
    return new_elem


def process_band_section(section_elem, section_tag):
    """Process a band section (title, pageHeader, etc.)

    For single-band sections: remove <band> wrapper, promote height.
    For detail: keep <band> wrapper.
    """
    new_section = ET.Element(section_tag)
    new_section.text = section_elem.text
    new_section.tail = section_elem.tail

    # Copy section-level attributes
    for k, v in section_elem.attrib.items():
        new_section.set(strip_ns(k), v)

    if section_tag in SINGLE_BAND_SECTIONS:
        # Find the band child and promote its height
        band = section_elem.find(make_tag("band"))
        if band is None:
            band = section_elem.find("band")
        if band is not None:
            height = band.get("height")
            if height:
                new_section.set("height", height)
            # Handle isSplitAllowed / splitType
            split = band.get("isSplitAllowed") or band.get("splitType")
            if split:
                if split in ("true", "false"):
                    # isSplitAllowed="true" -> don't add (default)
                    # isSplitAllowed="false" -> splitType="Prevent"
                    if split == "false":
                        new_section.set("splitType", "Prevent")
                else:
                    new_section.set("splitType", split)

            # Process band children (elements)
            process_band_children(band, new_section)
        else:
            # No band found, copy children as-is
            for child in section_elem:
                new_section.append(copy_element_stripped(child))
    elif section_tag == "detail":
        # Detail keeps its band(s)
        for child in section_elem:
            child_tag = strip_ns(child.tag)
            if child_tag == "band":
                new_band = ET.Element("band")
                height = child.get("height")
                if height:
                    new_band.set("height", height)
                split = child.get("isSplitAllowed") or child.get("splitType")
                if split:
                    if split == "false":
                        new_band.set("splitType", "Prevent")
                    elif split not in ("true",):
                        new_band.set("splitType", split)
                new_band.text = child.text
                new_band.tail = child.tail
                process_band_children(child, new_band)
                new_section.append(new_band)
            else:
                new_section.append(copy_element_stripped(child))
    else:
        # group header/footer bands - keep <band> wrapper
        for child in section_elem:
            child_tag = strip_ns(child.tag)
            if child_tag == "band":
                new_band = ET.Element("band")
                height = child.get("height")
                if height:
                    new_band.set("height", height)
                new_band.text = child.text
                new_band.tail = child.tail
                process_band_children(child, new_band)
                new_section.append(new_band)
            else:
                new_section.append(copy_element_stripped(child))

    return new_section


def process_band_children(band_elem, target):
    """Process children of a band element, converting report elements."""
    for child in band_elem:
        child_tag = strip_ns(child.tag)
        if child_tag in ELEMENT_TYPES:
            converted = process_element(child)
            if converted is not None:
                target.append(converted)
            else:
                target.append(copy_element_stripped(child))
        elif child_tag == "printWhenExpression":
            target.append(copy_element_stripped(child))
        elif child_tag == "property":
            target.append(copy_element_stripped(child))
        else:
            target.append(copy_element_stripped(child))


def process_group(group_elem):
    """Process a <group> element with its header/footer bands."""
    new_group = ET.Element("group")
    for k, v in group_elem.attrib.items():
        new_group.set(strip_ns(k), v)
    new_group.text = group_elem.text
    new_group.tail = group_elem.tail

    for child in group_elem:
        child_tag = strip_ns(child.tag)
        if child_tag == "groupExpression":
            new_child = ET.Element("groupExpression")
            new_child.text = child.text
            new_child.tail = child.tail
            new_group.append(new_child)
        elif child_tag in ("groupHeader", "groupFooter"):
            new_section = process_band_section(child, child_tag)
            new_group.append(new_section)
        else:
            new_group.append(copy_element_stripped(child))

    return new_group


def convert_jrxml(input_path):
    """Convert a single JRXML file from v6 to v7 format.

    Returns the converted XML as a string, or None on error.
    """
    # Read raw content to preserve CDATA sections
    with open(input_path, 'r', encoding='utf-8') as f:
        raw_content = f.read()

    # Check if already JR7 format
    if 'kind=' in raw_content and '<element ' in raw_content:
        return None  # Already converted

    # Preserve CDATA sections by replacing them with placeholders
    cdata_sections = []
    def replace_cdata(match):
        idx = len(cdata_sections)
        cdata_sections.append(match.group(1))
        return f"__CDATA_PLACEHOLDER_{idx}__"

    content = re.sub(r'<!\[CDATA\[(.*?)\]\]>', replace_cdata, raw_content, flags=re.DOTALL)

    # Strip DOCTYPE declaration (causes ET parse errors with external DTD)
    content = re.sub(r'<!DOCTYPE[^>]*>', '', content)

    # Strip XML comments (<!-- ... -->)  that may contain problematic content
    # (Keep them by default, only strip if parse fails)

    # Parse XML
    try:
        # Register namespace to avoid ns0: prefixes
        ET.register_namespace('', JR_NS)
        root = ET.fromstring(content)
    except ET.ParseError as e:
        print(f"  ERROR parsing {input_path}: {e}", file=sys.stderr)
        return None

    # Build new root element without namespace
    new_root = ET.Element("jasperReport")

    # Copy root attributes, removing namespace-related ones
    for key, value in root.attrib.items():
        attr_name = strip_ns(key)
        if attr_name.startswith('{') or attr_name in ('schemaLocation',):
            continue
        if attr_name in BOOL_ATTR_RENAMES:
            attr_name = BOOL_ATTR_RENAMES[attr_name]
        new_root.set(attr_name, value)

    new_root.text = root.text
    new_root.tail = root.tail

    # Band section names
    all_band_sections = SINGLE_BAND_SECTIONS | {"detail"}

    # Process children
    for child in root:
        child_tag = strip_ns(child.tag)

        if child_tag == "property":
            new_root.append(copy_element_stripped(child))
        elif child_tag == "import":
            new_root.append(copy_element_stripped(child))
        elif child_tag == "style":
            new_root.append(process_style(child))
        elif child_tag == "parameter":
            new_param = copy_element_stripped(child)
            # Rename defaultValueExpression children
            for sub in list(new_param):
                if sub.tag == "defaultValueExpression":
                    pass  # Keep as-is
            new_root.append(new_param)
        elif child_tag == "queryString":
            new_root.append(copy_element_stripped(child))
        elif child_tag == "field":
            new_root.append(copy_element_stripped(child))
        elif child_tag == "variable":
            new_root.append(copy_element_stripped(child))
        elif child_tag == "sortField":
            new_root.append(copy_element_stripped(child))
        elif child_tag == "filterExpression":
            new_root.append(copy_element_stripped(child))
        elif child_tag == "group":
            new_root.append(process_group(child))
        elif child_tag in all_band_sections:
            new_root.append(process_band_section(child, child_tag))
        elif child_tag in ("groupHeader", "groupFooter"):
            new_root.append(process_band_section(child, child_tag))
        else:
            new_root.append(copy_element_stripped(child))

    # Pretty-print using ET.indent (Python 3.9+)
    ET.indent(new_root, space='\t')

    # Serialize to string
    output = ET.tostring(new_root, encoding='unicode', xml_declaration=False)

    # Add XML declaration
    output = '<?xml version="1.0" encoding="UTF-8"?>\n' + output

    # Restore CDATA sections (AFTER serialization to preserve them)
    for idx, cdata_content in enumerate(cdata_sections):
        output = output.replace(f"__CDATA_PLACEHOLDER_{idx}__",
                               f"<![CDATA[{cdata_content}]]>")

    return output



def find_jrxml_files(paths):
    """Find all JRXML files from given paths.

    Accepts both .jrxml and .xml files when explicitly specified.
    When scanning directories, only .jrxml files are included by default.
    """
    files = []
    for path in paths:
        if os.path.isfile(path) and (path.endswith('.jrxml') or path.endswith('.xml')):
            files.append(path)
        elif os.path.isdir(path):
            for root_dir, _, filenames in os.walk(path):
                for fn in filenames:
                    if fn.endswith('.jrxml'):
                        files.append(os.path.join(root_dir, fn))
    return sorted(files)


def main():
    parser = argparse.ArgumentParser(
        description='Convert JasperReports JRXML files from v6 to v7 format')
    parser.add_argument('paths', nargs='*', default=['src/'],
                        help='Files or directories to convert (default: src/)')
    parser.add_argument('--dry-run', action='store_true',
                        help='Show what would change without modifying files')
    args = parser.parse_args()

    files = find_jrxml_files(args.paths)
    if not files:
        print("No JRXML files found.")
        return 1

    print(f"Found {len(files)} JRXML files")

    converted = 0
    skipped = 0
    errors = 0

    for filepath in files:
        rel_path = os.path.relpath(filepath)
        result = convert_jrxml(filepath)
        if result is None:
            print(f"  SKIP {rel_path} (already v7 format or parse error)")
            skipped += 1
            continue

        if args.dry_run:
            print(f"  WOULD CONVERT {rel_path}")
            converted += 1
        else:
            try:
                with open(filepath, 'w', encoding='utf-8') as f:
                    f.write(result)
                print(f"  CONVERTED {rel_path}")
                converted += 1
            except IOError as e:
                print(f"  ERROR writing {rel_path}: {e}", file=sys.stderr)
                errors += 1

    print(f"\nDone: {converted} converted, {skipped} skipped, {errors} errors")
    return 0 if errors == 0 else 1


if __name__ == '__main__':
    sys.exit(main())
