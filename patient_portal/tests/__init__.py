"""Test package marker.

Present so the split test modules can share `tests.support` by absolute import rather than
depending on pytest's rootdir sys.path insertion. Excluded from the wheel by the packages-find
filter in pyproject.toml.
"""
