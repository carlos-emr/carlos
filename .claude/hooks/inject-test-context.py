#!/usr/bin/env python3
"""
Injects modern test framework context when working on test files.

Trigger: Read, Edit, or Write operations on files in src/test-modern/ ending with Test.java
Purpose: Provides BDD naming conventions, tag hierarchy, and test structure guidance to Claude
Source: docs/test/claude-test-context.md (single source of truth)

Note: Assumes CLAUDE_PROJECT_DIR environment variable is set, falls back to /workspace
for devcontainer environments.
"""
import json
import sys
import os


def main():
    try:
        data = json.load(sys.stdin)
    except json.JSONDecodeError:
        sys.exit(0)

    tool_input = data.get("tool_input", {})

    # All three tools (Read, Edit, Write) use file_path for the target file
    file_path = tool_input.get("file_path", "")

    # Only trigger for modern test files
    if "src/test-modern" not in file_path or not file_path.endswith("Test.java"):
        sys.exit(0)

    # Read context from the docs file (single source of truth)
    project_dir = os.environ.get("CLAUDE_PROJECT_DIR", "/workspace")
    context_file = os.path.join(project_dir, "docs/test/claude-test-context.md")

    try:
        with open(context_file, 'r') as f:
            context = f.read()
        print(context)
    except FileNotFoundError:
        print("Warning: Test context file not found: docs/test/claude-test-context.md", file=sys.stderr)
        print("Please read docs/test/test-writing-best-practices.md for test conventions.", file=sys.stderr)
    except Exception as e:
        print(f"Warning: Error reading test context: {e}", file=sys.stderr)

    sys.exit(0)


if __name__ == "__main__":
    main()
