#!/bin/bash
# Find and add the FORCE_JAVASCRIPT_ACTIONS_TO_NODE24 env variable to all workflows
for file in .github/workflows/*.yml; do
  if grep -q "env:" "$file"; then
    sed -i '/env:/a \ \ FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: true' "$file"
  else
    sed -i '/jobs:/i env:\n  FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: true\n' "$file"
  fi
done
