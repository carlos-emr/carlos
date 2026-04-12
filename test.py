import subprocess
import os

with open("src/main/java/io/github/carlos_emr/carlos/managers/AuditLogManager.java") as f:
    content = f.read()

# test against semgrep rule
rule = """
rules:
  - id: process-builder-concatenation
    message: Concatenation found
    languages: [java]
    severity: ERROR
    patterns:
      - pattern-either:
          - pattern: new ProcessBuilder(..., $X + $Y, ...);
          - pattern: $PB.command(..., $X + $Y, ...);
"""

with open("rule.yaml", "w") as f:
    f.write(rule)

os.system("semgrep --config rule.yaml src/main/java/io/github/carlos_emr/carlos/managers/AuditLogManager.java")
