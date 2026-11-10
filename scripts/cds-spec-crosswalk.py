#!/usr/bin/env python3
"""Inventory the pinned CDS wire contract; verify its separately reviewed crosswalk.

Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
This development-time tool never consumes a patient upload or accesses a database.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import sys
from xml.etree.ElementTree import Element, SubElement, indent, register_namespace, tostring

from defusedxml import ElementTree as ET

XS = "http://www.w3.org/2001/XMLSchema"
NS = {"xs": XS, "cds": "cds", "dt": "cds_dt", "": ""}
ROOT = Path(__file__).resolve().parents[1]
SCHEMA = ROOT / "src/main/resources/cds-import/schema/EMR_Data_Migration_Schema.xsd"
REVIEW = ROOT / "docs/cds-spec-review.json"
OUTPUT = ROOT / "docs/cds-spec-crosswalk.json"
FIXTURES = ROOT / "src/test/resources/cds-spec"
PINS = {
    "EMR_Data_Migration_Schema.xsd": "2bffde70ae84137624db888611e8a3b919e868789b726ee012d39513ce846726",
    "EMR_Data_Migration_Schema_DT.xsd": "e999fafd945d13a7a2866482e06029cea86b902ce7f30139377a3600c3d9ce31",
}
FACETS = {"enumeration", "length", "minLength", "maxLength", "pattern", "minInclusive", "whiteSpace"}
ALLOWED = FACETS | {"schema", "import", "element", "complexType", "simpleType", "restriction",
                    "sequence", "all", "choice", "attribute", "annotation", "documentation"}
ATTRIBUTES = {
    "schema": {"targetNamespace", "elementFormDefault", "attributeFormDefault"},
    "import": {"namespace", "schemaLocation"},
    "element": {"name", "ref", "type", "minOccurs", "maxOccurs", "default", "fixed", "nillable", "form"},
    "attribute": {"name", "type", "use", "default", "fixed", "form"},
    "complexType": {"name"}, "simpleType": {"name", "final"}, "restriction": {"base"},
    "sequence": {"minOccurs", "maxOccurs"}, "all": {"minOccurs", "maxOccurs"},
    "choice": {"minOccurs", "maxOccurs"}, "annotation": set(), "documentation": set(),
    **{name: {"value"} for name in FACETS},
}


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def canonical(value) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf-8")


def tag(node) -> str:
    return node.tag.rsplit("}", 1)[-1]


def children(node):
    return [child for child in node if tag(child) not in {"annotation", "documentation"}]


def short(uri: str, name: str) -> str:
    reverse = {value: key for key, value in NS.items()}
    if uri not in reverse:
        raise ValueError(f"Unreviewed namespace: {uri}")
    return (reverse[uri] + ":" if uri else "") + name


class SchemaSet:
    """Finite resolver for the XSD constructs actually present in the pinned contract.

    A new construct, recursive type or external import fails rather than disappearing
    from the inventory. Synthetic tests exercise imports and anonymous/named types.
    """

    def __init__(self, main: Path, pins=None):
        self.main = main.resolve()
        self.pins = pins
        self.documents = {}
        self.owner = {}
        self.locations = {}
        self.types = {}
        self.elements = {}
        self.fields = {}
        self.choices = []
        self.compositors = []
        self.used_types = set()
        self._load(self.main)
        if pins is not None and set(pins) != {p.name for p in self.documents}:
            raise ValueError("Pinned import inventory changed")

    def _load(self, path):
        path = path.resolve()
        if path.parent != self.main.parent:
            raise ValueError("External schema import is forbidden")
        if path in self.documents:
            return
        raw = path.read_bytes()
        if self.pins is not None and digest(raw) != self.pins.get(path.name):
            raise ValueError(f"Pinned schema mismatch: {path.name}")
        prefix = {}
        for _, (alias, uri) in ET.iterparse(path, events=("start-ns",), forbid_dtd=True):
            alias = alias or ""
            if alias in prefix and prefix[alias] != uri:
                raise ValueError("Scoped namespace rebinding requires an explicit resolver review")
            prefix[alias] = uri
        root = ET.fromstring(raw, forbid_dtd=True, forbid_entities=True, forbid_external=True)
        if tag(root) != "schema":
            raise ValueError("Not an XML Schema")
        self.documents[path] = (root, prefix, digest(raw))

        def index(node, location):
            if not node.tag.startswith("{" + XS + "}") or tag(node) not in ALLOWED:
                raise ValueError(f"Unsupported XSD construct at {path.name}:{location}")
            if set(node.attrib) - ATTRIBUTES[tag(node)]:
                raise ValueError(f"Unsupported XSD attribute at {path.name}:{location}")
            self.owner[node] = path
            self.locations[node] = path.name + location
            counters = {}
            for child in node:
                kind = tag(child)
                counters[kind] = counters.get(kind, 0) + 1
                index(child, f"{location}/{kind}[{counters[kind]}]")

        index(root, "/schema")
        namespace = root.get("targetNamespace", "")
        for node in children(root):
            kind = tag(node)
            if kind in {"simpleType", "complexType", "element"}:
                table = self.elements if kind == "element" else self.types
                key = (namespace, node.attrib["name"])
                if key in table:
                    raise ValueError("Duplicate global declaration")
                table[key] = node
            elif kind == "import":
                location = node.get("schemaLocation", "")
                if not location or "/" in location or "\\" in location or ":" in location:
                    raise ValueError("Nonlocal schema import")
                self._load(path.parent / location)
                imported = self.documents[(path.parent / location).resolve()][0]
                if imported.get("targetNamespace", "") != node.get("namespace", ""):
                    raise ValueError("Import namespace mismatch")

    def qname(self, value, node):
        _, prefixes, _ = self.documents[self.owner[node]]
        if ":" in value:
            prefix, local = value.split(":", 1)
            if prefix not in prefixes:
                raise ValueError("Unresolved QName")
            return prefixes[prefix], local
        return prefixes.get("", ""), value

    def name(self, node, attribute=False):
        root, _, _ = self.documents[self.owner[node]]
        form = node.get("form", root.get("attributeFormDefault" if attribute else "elementFormDefault", "unqualified"))
        uri = root.get("targetNamespace", "") if form == "qualified" else ""
        return short(uri, node.attrib["name"])

    def type(self, node):
        if "type" in node.attrib:
            key = self.qname(node.attrib["type"], node)
            if key[0] == XS:
                return key
            if key not in self.types:
                raise ValueError(f"Unresolved type {key}")
            return self.types[key]
        inline = [child for child in children(node) if tag(child) in {"simpleType", "complexType"}]
        if len(inline) != 1:
            raise ValueError(f"Missing/ambiguous type: {self.locations[node]}")
        return inline[0]

    def scalar(self, type_node, stack=()):
        if isinstance(type_node, tuple):
            uri, local = type_node
            if uri != XS:
                raise ValueError("Nonbuiltin terminal scalar")
            return {"base": short(uri, local), "facets": [], "typeChain": [short(uri, local)],
                    "whiteSpace": "preserve" if local == "string" else "replace" if local == "normalizedString" else "collapse"}
        if type_node in stack or tag(type_node) != "simpleType":
            raise ValueError("Recursive/nonsimple scalar")
        self.used_types.add(type_node)
        nodes = children(type_node)
        if len(nodes) != 1 or tag(nodes[0]) != "restriction":
            raise ValueError("Unsupported simple type derivation")
        restriction = nodes[0]
        key = self.qname(restriction.attrib["base"], restriction)
        parent = key if key[0] == XS else self.types[key]
        value = self.scalar(parent, stack + (type_node,))
        value["typeChain"] = [self.type_label(type_node)] + value["typeChain"]
        for facet in children(restriction):
            if tag(facet) not in FACETS:
                raise ValueError("Unsupported scalar facet")
            value["facets"].append({"kind": tag(facet), "value": facet.attrib["value"],
                                    "declaration": self.locations[facet]})
            if tag(facet) == "whiteSpace":
                value["whiteSpace"] = facet.attrib["value"]
        return value

    def type_label(self, node):
        root, _, _ = self.documents[self.owner[node]]
        return short(root.get("targetNamespace", ""), node.attrib["name"]) if "name" in node.attrib else self.locations[node]

    def inventory(self):
        self.walk_element(self.elements[("cds", "OmdCds")], "", [], [], (), global_element=True)
        fields = sorted(self.fields.values(), key=lambda row: row["path"])
        for row in fields:
            row["variants"].sort(key=lambda value: (value["declaration"], json.dumps(value["choiceArms"])))
        return {"formatVersion": 1, "profiles": ["pcon-2024-02", "omd-5.2"],
                "namespaces": NS, "root": "cds:OmdCds", "schemas": [
                    {"file": path.name, "namespace": root.get("targetNamespace", ""), "sha256": sha}
                    for path, (root, _, sha) in sorted(self.documents.items())],
                "counts": {"fields": len(fields), "choices": len(self.choices),
                           "choiceArms": sum(len(row["arms"]) for row in self.choices),
                           "compositors": len(self.compositors)},
                "fields": fields, "choices": sorted(self.choices, key=lambda row: row["id"]),
                "compositors": sorted(self.compositors, key=lambda row: row["id"]),
                "globalTypes": [{"name": short(*key), "declaration": self.locations[node],
                                 "reachableFromEnvelope": node in self.used_types}
                                for key, node in sorted(self.types.items())]}

    def walk_element(self, node, parent, lineage, arms, stack, global_element=False):
        declaration = node
        if "ref" in node.attrib:
            key = self.qname(node.attrib["ref"], node)
            declaration = self.elements[key]
            name = short(*key)
        elif global_element:
            root, _, _ = self.documents[self.owner[node]]
            name = short(root.get("targetNamespace", ""), node.attrib["name"])
        else:
            name = self.name(node)
        path = parent + "/" + name
        current = {"path": path, "min": node.get("minOccurs", "1"), "max": node.get("maxOccurs", "1")}
        lineage = lineage + [current]
        datatype = self.type(declaration)
        if isinstance(datatype, tuple) or tag(datatype) == "simpleType":
            self.add_field(path, "element", declaration, datatype, lineage, arms)
            return
        if datatype in stack:
            raise ValueError("Recursive complex type cannot be finitely inventoried")
        self.used_types.add(datatype)
        for child in children(datatype):
            if tag(child) == "attribute":
                attribute_path = path + "/@" + self.name(child, attribute=True)
                attribute_lineage = lineage + [{"path": attribute_path, "min": "1" if child.get("use") == "required" else "0", "max": "1"}]
                self.add_field(attribute_path, "attribute", child, self.type(child), attribute_lineage, arms)
            elif tag(child) in {"sequence", "all", "choice"}:
                self.walk_particle(child, path, lineage, arms, stack + (datatype,))
            else:
                raise ValueError(f"Unsupported complex type child {tag(child)}")

    def walk_particle(self, node, parent, lineage, arms, stack):
        kind = tag(node)
        if kind == "element":
            self.walk_element(node, parent, lineage, arms, stack)
            return
        if kind not in {"sequence", "all", "choice"}:
            raise ValueError("Unsupported particle")
        identifier = parent + "#" + self.locations[node]
        terms = children(node)
        model = {"id": identifier, "path": parent, "model": kind, "min": node.get("minOccurs", "1"), "max": node.get("maxOccurs", "1")}
        self.compositors.append(model)
        lineage = lineage + [{"path": identifier, "min": model["min"], "max": model["max"]}]
        if kind == "choice":
            branch_rows = [{"id": identifier + f"/arm[{index + 1}]", "declaration": self.locations[term]}
                           for index, term in enumerate(terms)]
            self.choices.append({**model, "ancestorArms": arms, "arms": branch_rows})
            for term, branch in zip(terms, branch_rows):
                self.walk_particle(term, parent, lineage, arms + [branch["id"]], stack)
        else:
            for term in terms:
                self.walk_particle(term, parent, lineage, arms, stack)

    def add_field(self, path, kind, node, datatype, lineage, arms):
        category = path.split("/")[3].split(":", 1)[-1]
        row = self.fields.setdefault(path, {"path": path, "kind": kind, "category": category, "variants": []})
        row["variants"].append({"declaration": self.locations[node], "scalar": self.scalar(datatype),
                                "occurrences": lineage, "choiceArms": arms,
                                "default": node.get("default"), "fixed": node.get("fixed"),
                                "nillable": node.get("nillable", "false") == "true"})


def read_review(path):
    def unique(pairs):
        value = {}
        for key, item in pairs:
            if key in value:
                raise ValueError(f"Duplicate crosswalk review key: {key}")
            value[key] = item
        return value
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique)


def expanded(name):
    prefix, separator, local = name.partition(":")
    return "{" + NS[prefix] + "}" + local if separator else name


class FixtureBuilder:
    """Synthetic structural corpus, not clinical import decisions or a native fixture."""

    def __init__(self, schemas, overrides):
        self.schemas = schemas
        self.overrides = overrides

    def element(self, node, parent=None, path="", global_element=False):
        declaration = node
        if "ref" in node.attrib:
            key = self.schemas.qname(node.attrib["ref"], node)
            declaration = self.schemas.elements[key]
            name = short(*key)
        elif global_element:
            root, _, _ = self.schemas.documents[self.schemas.owner[node]]
            name = short(root.get("targetNamespace", ""), node.attrib["name"])
        else:
            name = self.schemas.name(node)
        current = Element(expanded(name)) if parent is None else SubElement(parent, expanded(name))
        path += "/" + name
        datatype = self.schemas.type(declaration)
        if isinstance(datatype, tuple) or tag(datatype) == "simpleType":
            current.text = declaration.get("fixed", self.sample(self.schemas.scalar(datatype)))
        else:
            for child in children(datatype):
                if tag(child) == "attribute":
                    current.set(expanded(self.schemas.name(child, attribute=True)),
                                child.get("fixed", self.sample(self.schemas.scalar(self.schemas.type(child)))))
                else:
                    self.particle(child, current, path)
        return current

    def particle(self, node, parent, path):
        kind = tag(node)
        if kind == "element":
            self.element(node, parent, path)
        elif kind == "choice":
            identifier = path + "#" + self.schemas.locations[node]
            terms = children(node)
            self.particle(terms[self.overrides.get(identifier, 0)], parent, path)
        elif kind in {"all", "sequence"}:
            for child in children(node):
                self.particle(child, parent, path)
        else:
            raise ValueError("Unsupported fixture particle")

    @staticmethod
    def sample(scalar):
        facets = scalar["facets"]
        enums = [facet["value"] for facet in facets if facet["kind"] == "enumeration"]
        if enums:
            return enums[0]
        base = scalar["base"]
        fixed = {"xs:date": "2024-02-29", "xs:dateTime": "2024-02-29T10:11:12.125Z",
                 "xs:gYear": "2024", "xs:gYearMonth": "2024-02", "xs:time": "10:11:12Z",
                 "xs:boolean": "false", "xs:integer": "1", "xs:positiveInteger": "1",
                 "xs:nonNegativeInteger": "1", "xs:decimal": "1.25", "xs:base64Binary": "U1BFQw=="}
        if base in fixed:
            return fixed[base]
        if base not in {"xs:string", "xs:token", "xs:normalizedString"}:
            raise ValueError(f"No reviewed synthetic scalar for {base}")
        patterns = [facet["value"] for facet in facets if facet["kind"] == "pattern"]
        if patterns:
            pattern_samples = {"([a-zA-Z0-9-])*": "K1A0B1", "([0-9-])*": "12345"}
            if len(patterns) != 1:
                raise ValueError("Unreviewed compound pattern")
            if "@" in patterns[0]:
                return "synthetic@example.invalid"
            if patterns[0] not in pattern_samples:
                raise ValueError("Unreviewed pattern sample")
            return pattern_samples[patterns[0]]
        exact = [int(f["value"]) for f in facets if f["kind"] == "length"]
        minimum = max([1] + [int(f["value"]) for f in facets if f["kind"] == "minLength"])
        maximum = min([1024] + [int(f["value"]) for f in facets if f["kind"] == "maxLength"])
        return "S" * (exact[-1] if exact else min(max(4, minimum), maximum))


def instance_fields(root):
    """Independent XML instance walk; prefixes/namespace declarations are not attributes."""
    result = {}

    def display(qname):
        if qname.startswith("{"):
            uri, name = qname[1:].split("}", 1)
            return short(uri, name)
        return qname

    def visit(node, parent):
        path = parent + "/" + display(node.tag)
        if not len(node):
            result[path] = {"kind": "element", "value": node.text or ""}
        for key, value in node.attrib.items():
            result[path + "/@" + display(key)] = {"kind": "attribute", "value": value}
        for child in node:
            visit(child, path)

    visit(root, "")
    return result


def fixture_plan(inventory):
    scenarios = []
    for choice in inventory["choices"]:
        for index, arm in enumerate(choice["arms"]):
            selection = {choice["id"]: index}
            for ancestor in choice["ancestorArms"]:
                identifier, _, branch = ancestor.rpartition("/arm[")
                selection[identifier] = int(branch[:-1]) - 1
            compatible = next((scenario for scenario in scenarios if all(
                key not in scenario or scenario[key] == value for key, value in selection.items())), None)
            if compatible is None:
                scenarios.append(dict(selection))
            else:
                compatible.update(selection)
    return scenarios


def generate_fixtures(schemas, inventory):
    register_namespace("cds", "cds")
    register_namespace("dt", "cds_dt")
    outputs = {}
    for index, selection in enumerate(fixture_plan(inventory), 1):
        root = FixtureBuilder(schemas, selection).element(schemas.elements[("cds", "OmdCds")], global_element=True)
        indent(root, space="  ")
        data = b'<?xml version="1.0" encoding="UTF-8"?>\n<!-- Synthetic schema-structure coverage only; GPL-2.0-or-later; CARLOS Contributors 2026. -->\n' + tostring(root, encoding="utf-8") + b"\n"
        outputs[f"choices-{index:02}.xml"] = data
    return outputs


def fixture_coverage(inventory, fixture_bytes):
    actual = {name: instance_fields(ET.fromstring(raw, forbid_dtd=True)) for name, raw in sorted(fixture_bytes.items())}
    known = {row["path"] for row in inventory["fields"]}
    scalars = {row["path"]: row["variants"][0]["scalar"] for row in inventory["fields"]}
    supplied = set().union(*(set(fields) for fields in actual.values()))
    if supplied != known:
        raise ValueError(f"Synthetic field coverage differs: missing={sorted(known - supplied)}, extra={sorted(supplied - known)}")
    for fixture in actual.values():
        for path, field in fixture.items():
            field["schemaBase"] = scalars[path]["base"]
            field["whiteSpace"] = scalars[path]["whiteSpace"]
    choices = []
    for choice in inventory["choices"]:
        branch_sets = {arm["id"]: {field["path"] for field in inventory["fields"] for variant in field["variants"]
                                     if arm["id"] in variant["choiceArms"]} for arm in choice["arms"]}
        for arm in choice["arms"]:
            # A branch needs an exclusive witness; a shared phone extension cannot prove which sequence ran.
            own = branch_sets[arm["id"]]
            others = set().union(*(paths for key, paths in branch_sets.items() if key != arm["id"]))
            witnesses = own - others
            if not witnesses:
                raise ValueError("Choice arm has no unique field witness; extend the verifier")
            covered = [name for name, fields in actual.items() if witnesses & set(fields)]
            if not covered:
                raise ValueError(f"Unexercised choice arm: {arm['id']}")
            choices.append({"arm": arm["id"], "fixtures": covered, "witnesses": sorted(witnesses)})
    return {"kind": "synthetic-schema-and-parser-structure", "notClaimed": "native semantic round-trip for every leaf",
            "fixtures": [{"file": name, "sha256": digest(fixture_bytes[name]), "fields": actual[name]}
                         for name in actual], "choices": choices}


def apply_review(inventory, review, repo=ROOT):
    """Exact path classification is a reviewed input, never inferred from Java names."""
    groups = review["groups"]
    by_path = {}
    schemas = {row["file"]: row["sha256"] for row in inventory["schemas"]}
    if review.get("schemaPins") != schemas:
        raise ValueError("Reviewed schema pins changed; review new facets/types/defaults before regeneration")
    implementation_hashes = {}
    for identifier, group in groups.items():
        for name in ("disposition", "destination", "normalization", "original", "reader", "errorPolicy", "transaction", "tests"):
            if not group.get(name):
                raise ValueError(f"Incomplete reviewed group {identifier}: {name}")
        if group["disposition"] not in {"native-and-source", "source-only"}:
            raise ValueError(f"Unknown disposition {identifier}")
        for ref in group["reader"] + group["tests"] + group.get("implementation", []):
            file, _, symbol = ref.partition("#")
            target = (repo / file).resolve()
            if not target.is_relative_to(repo.resolve()) or not target.is_file():
                raise ValueError(f"Missing crosswalk reference: {ref}")
            if symbol and symbol not in target.read_text(encoding="utf-8"):
                raise ValueError(f"Missing crosswalk symbol: {ref}")
            if ref in group["reader"] or ref in group.get("implementation", []):
                implementation_hashes[file] = digest(target.read_bytes())
        for path in group["paths"]:
            if path in by_path:
                raise ValueError(f"Multiply classified path: {path}")
            by_path[path] = identifier
    actual = {row["path"] for row in inventory["fields"]}
    if actual != set(by_path):
        raise ValueError(f"Unclassified/stale paths: missing={sorted(actual - set(by_path))}, stale={sorted(set(by_path) - actual)}")
    if review.get("reviewedImplementationSha256") != implementation_hashes:
        raise ValueError("Reviewed implementation/reader bytes changed; verify their mapping before regeneration")
    for field in inventory["fields"]:
        field["reviewGroup"] = by_path[field["path"]]
    expected_choices = {choice["id"] for choice in inventory["choices"]}
    if set(review.get("choices", {})) != expected_choices:
        raise ValueError("Unclassified/stale choice paths")
    for choice in inventory["choices"]:
        reviewed = review["choices"][choice["id"]]
        if set(reviewed) != {arm["id"] for arm in choice["arms"]}:
            raise ValueError("Unclassified/stale choice arms")
        for arm in choice["arms"]:
            mapping = reviewed[arm["id"]]
            applicable = sorted({by_path[field["path"]] for field in inventory["fields"]
                                 for variant in field["variants"] if arm["id"] in variant["choiceArms"]})
            if mapping.get("fieldGroups") != applicable or not mapping.get("evidence") or not mapping.get("evidenceScope"):
                raise ValueError("Incomplete reviewed choice disposition/evidence")
            for ref in mapping["evidence"]:
                file, _, symbol = ref.partition("#")
                target = (repo / file).resolve()
                if not target.is_relative_to(repo.resolve()) or not target.is_file() or (symbol and symbol not in target.read_text(encoding="utf-8")):
                    raise ValueError(f"Missing choice evidence reference: {ref}")
            arm["review"] = mapping
    inventory["review"] = {"file": "docs/cds-spec-review.json", "sha256": digest(canonical(review)),
                           "scope": review["scope"], "groups": {key: {k: v for k, v in value.items() if k != "paths"}
                                                                      for key, value in sorted(groups.items())}}
    return inventory


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Fail on incomplete review or stale checked-in inventory")
    parser.add_argument("--inventory-only", action="store_true", help="Print the schema inventory before review; does not write files")
    parser.add_argument("--write-fixtures", action="store_true", help="Regenerate synthetic structural fixtures from the pinned schemas")
    args = parser.parse_args(argv)
    schemas = SchemaSet(SCHEMA, PINS)
    inventory = schemas.inventory()
    if args.inventory_only:
        sys.stdout.buffer.write(canonical(inventory))
        return 0
    fixture_bytes = generate_fixtures(schemas, inventory)
    coverage = fixture_coverage(inventory, fixture_bytes)
    if args.write_fixtures:
        FIXTURES.mkdir(parents=True, exist_ok=True)
        for name, data in fixture_bytes.items():
            (FIXTURES / name).write_bytes(data)
        (FIXTURES / "coverage.json").write_bytes(canonical(coverage))
    elif any(not (FIXTURES / name).is_file() or (FIXTURES / name).read_bytes() != data for name, data in fixture_bytes.items()) or not (FIXTURES / "coverage.json").is_file() or (FIXTURES / "coverage.json").read_bytes() != canonical(coverage):
        raise ValueError("Synthetic structural fixtures/coverage are stale")
    inventory = apply_review(inventory, read_review(REVIEW))
    inventory["fixtureCoverage"] = {"manifest": "src/test/resources/cds-spec/coverage.json",
                                   "sha256": digest(canonical(coverage)), "fixtures": len(fixture_bytes),
                                   "fields": len(inventory["fields"]), "choiceArms": len(coverage["choices"])}
    data = canonical(inventory)
    if args.check:
        if not OUTPUT.is_file() or OUTPUT.read_bytes() != data:
            raise ValueError("Crosswalk is stale; review the schema/mapping change and regenerate")
    else:
        OUTPUT.write_bytes(data)
    print(json.dumps({"crosswalk": str(OUTPUT.relative_to(ROOT)), "sha256": digest(data), **inventory["counts"]}, sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, KeyError, OSError) as failure:
        print(f"CDS crosswalk verification failed: {failure}", file=sys.stderr)
        sys.exit(1)
