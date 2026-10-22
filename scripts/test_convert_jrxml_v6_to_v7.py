#!/usr/bin/env python3
"""Regression coverage for the JR6 declarations rejected by JasperReports 7."""
# Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
import tempfile
import unittest
from pathlib import Path
from xml.etree import ElementTree as ET

from convert_jrxml_v6_to_v7 import convert_jrxml


class JasperConversionTest(unittest.TestCase):
    def convert(self, body, attributes=""):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "report.jrxml"
            source.write_text(f'<jasperReport xmlns="http://jasperreports.sourceforge.net/jasperreports" name="test" {attributes}>{body}</jasperReport>')
            return ET.fromstring(convert_jrxml(source))

    def test_parameter_query_import_and_description(self):
        root = self.convert('''<import value="java.text.SimpleDateFormat"/>
            <parameter name="id" class="java.lang.String" isForPrompting="false">
                <parameterDescription>Choose an ID</parameterDescription>
                <defaultValueExpression><![CDATA["A&B"]]></defaultValueExpression>
            </parameter><queryString language="sql"><![CDATA[SELECT * FROM billing WHERE id=$P{id}]]></queryString>''')
        self.assertEqual("java.text.SimpleDateFormat", root.find("import").text)
        self.assertEqual({}, root.find("import").attrib)
        self.assertEqual("false", root.find("parameter").get("forPrompting"))
        self.assertEqual("Choose an ID", root.findtext("parameter/description"))
        self.assertEqual('"A&B"', root.findtext("parameter/defaultValueExpression"))
        self.assertEqual("sql", root.find("query").get("language"))
        self.assertIn("$P{id}", root.findtext("query"))

    def test_variable_and_group_expressions(self):
        root = self.convert('''<variable name="sum" class="java.lang.Integer" calculation="Sum">
            <variableExpression><![CDATA[$F{amount}]]></variableExpression>
            <initialValueExpression><![CDATA[0]]></initialValueExpression></variable>
            <group name="invoice"><groupExpression><![CDATA[$F{id}]]></groupExpression></group>''')
        self.assertEqual("$F{amount}", root.findtext("variable/expression"))
        self.assertEqual("0", root.findtext("variable/initialValueExpression"))
        self.assertEqual("$F{id}", root.findtext("group/expression"))

    def test_report_flags(self):
        root = self.convert("", 'isTitleNewPage="true" isSummaryNewPage="false"')
        self.assertEqual("true", root.get("titleNewPage"))
        self.assertEqual("false", root.get("summaryNewPage"))
        self.assertNotIn("isTitleNewPage", root.attrib)

    def test_graphic_pen_and_fill_are_preserved_without_wrapper(self):
        root = self.convert('''<detail><band height="20"><rectangle>
            <reportElement x="0" y="0" width="20" height="20"/>
            <graphicElement fill="Solid"><pen lineWidth="2.0" lineColor="#123456"/></graphicElement>
            </rectangle></band></detail>''')
        element = root.find("detail/band/element")
        self.assertEqual("rectangle", element.get("kind"))
        self.assertEqual("Solid", element.get("fill"))
        self.assertEqual("2.0", element.find("pen").get("lineWidth"))
        self.assertEqual("#123456", element.find("pen").get("lineColor"))
        self.assertIsNone(element.find("graphicElement"))

    def test_stretch_and_overflow_flags(self):
        for old, new in [("RelativeToBandHeight", "ContainerHeight"), ("RelativeToTallestObject", "ElementGroupHeight")]:
            with self.subTest(stretch=old):
                root = self.convert(f'''<detail><band height="20"><textField>
                    <reportElement x="0" y="0" width="20" height="20" stretchType="{old}"
                        isRemoveLineWhenBlank="true" isPrintWhenDetailOverflows="true"/>
                    <textFieldExpression><![CDATA[$F{{name}}]]></textFieldExpression>
                    </textField></band></detail>''')
                element = root.find("detail/band/element")
                self.assertEqual(new, element.get("stretchType"))
                self.assertEqual("true", element.get("removeLineWhenBlank"))
                self.assertEqual("true", element.get("printWhenDetailOverflows"))
                self.assertEqual("$F{name}", element.findtext("expression"))


if __name__ == "__main__":
    unittest.main()
