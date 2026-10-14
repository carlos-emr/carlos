#!/usr/bin/env python3
# Copyright (C) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Check recursively included Struts action classes and named Spring components.

Includes are classpath resources relative to the root configuration directory.
The XML parser does not fetch external DTDs. Java sources are inspected only;
no application classes are loaded or executed.
"""

import argparse
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
IDENTIFIER = r"[A-Za-z_$][A-Za-z0-9_$]*"
CLASS_NAME = re.compile(rf"{IDENTIFIER}(?:\.{IDENTIFIER})+\Z")
JAVA_TOKEN = re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*.*?\*/', re.S)
COMPONENT = re.compile(
    rf'@(?:org\.springframework\.stereotype\.)?Component\s*\(\s*'
    rf'(?:value\s*=\s*)?(?:"([^"\\]+)"|(?:({IDENTIFIER})\.)?SPRING_BEAN_NAME)\s*\)')


def java_text(path):
    text = path.read_text(encoding="utf-8")
    return JAVA_TOKEN.sub(lambda m: " " if m.group().startswith(("//", "/*")) else m.group(), text)


def spring_components(java_root):
    beans = {}
    for path in java_root.rglob("*.java"):
        text = java_text(path)
        for match in COMPONENT.finditer(text):
            name, owner = match.groups()
            if name is None:
                if owner is not None and owner != path.stem:
                    continue
                constant = re.search(r'\bString\s+SPRING_BEAN_NAME\s*=\s*"([^"\\]+)"\s*;', text)
                if constant is None:
                    continue
                name = constant.group(1)
            beans.setdefault(name, set()).add(path)
    return beans


def action_definitions(config):
    resource_root = config.parent.resolve()
    visited, active = set(), set()
    actions = []

    def visit(path):
        path = path.resolve()
        if not path.is_relative_to(resource_root):
            raise ValueError(f"include escapes configuration directory: {path}")
        if path in active:
            raise ValueError(f"cyclic Struts include: {path}")
        if path in visited:
            return
        if not path.is_file():
            raise ValueError(f"missing Struts configuration/include: {path}")
        content = path.read_text(encoding="utf-8")
        if "<!ENTITY" in content:
            raise ValueError(f"entity declarations are not allowed: {path}")
        document = ET.fromstring(content)
        if document.tag != "struts":
            raise ValueError(f"expected struts root element: {path}")
        active.add(path)
        for node in document.iter():
            if node.tag == "include":
                name = (node.get("file") or "").strip()
                if not name:
                    raise ValueError(f"empty Struts include: {path}")
                visit(resource_root / name)
            elif node.tag == "action":
                actions.append((path, node.get("name", "(unnamed)"),
                                (node.get("class") or "").strip()))
        active.remove(path)
        visited.add(path)

    visit(config)
    if not actions:
        raise ValueError("no action elements found in the Struts configuration or its includes")
    return actions, visited


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path,
                        default=ROOT / "src/main/webapp/WEB-INF/classes/struts.xml")
    parser.add_argument("--java-source", type=Path, default=ROOT / "src/main/java")
    args = parser.parse_args(argv)
    errors = []
    try:
        if not args.java_source.is_dir():
            raise ValueError(f"missing Java source directory: {args.java_source}")
        actions, configs = action_definitions(args.config)
        aliases = {name for _, _, name in actions if name and not CLASS_NAME.fullmatch(name)}
        beans = spring_components(args.java_source) if aliases else {}
        for config, action, name in actions:
            if not name:
                errors.append(f"{config.name}: {action}: missing action class")
            elif CLASS_NAME.fullmatch(name):
                path = args.java_source.joinpath(*name.split(".")).with_suffix(".java")
                if not path.is_file():
                    errors.append(f"{config.name}: {action}: class not found: {name}")
            elif len(beans.get(name, set())) != 1:
                reason = "ambiguous" if beans.get(name) else "not found"
                errors.append(f"{config.name}: {action}: Spring component {reason}: {name}")
        print(f"Struts configuration files checked: {len(configs)}")
        print(f"Total actions found: {len(actions)}")
        print(f"Valid actions: {len(actions) - len(errors)}")
        print(f"Invalid actions: {len(errors)}")
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
    except (OSError, UnicodeError, ET.ParseError, ValueError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    if errors:
        return 1
    print("PASS all configured action classes and named Spring components exist")
    return 0


if __name__ == "__main__":
    sys.exit(main())
