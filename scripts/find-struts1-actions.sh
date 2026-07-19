#!/bin/bash

# Script to find Struts 1 style actions that have been renamed to *2Action.java
# but haven't actually been migrated to Struts 2 yet.

show_usage() {
    cat << 'EOF'
Usage: find-struts1-actions.sh [COMMAND]

Find *2Action.java files that are still using Struts 1 patterns

Commands:
  count    Show count of Struts 1 actions found
  names    Show file names of Struts 1 actions found
  help     Show this usage message

Default (no command): Show this usage message

Criteria for an unmigrated Struts 1 action:
  - ActionForm/DynaActionForm usage
  - ActionMapping/ActionForward usage
  - Apache Commons FileUpload (ServletFileUpload/FileItem)

Examples:
  ./find-struts1-actions.sh count
  ./find-struts1-actions.sh names
EOF
}

find_struts1_actions() {
    # Find *2Action.java files with definitive Struts 1 patterns
    find /workspace -name "*2Action.java" -type f -exec grep -l "ActionForm\|DynaActionForm\|ActionMapping\|ActionForward\|ServletFileUpload\|FileItem" {} + 2>/dev/null | \
    sort
}

case "${1:-}" in
    count)
        count=$(find_struts1_actions | wc -l)
        echo "Found $count Struts 1 actions"
        ;;
    names)
        echo "Struts 1 actions found:"
        echo "=========================================="
        find_struts1_actions | while read -r file; do
            basename "$file"
        done
        ;;
    help|--help|-h)
        show_usage
        ;;
    "")
        show_usage
        ;;
    *)
        echo "Error: Unknown command '$1'"
        echo ""
        show_usage
        exit 1
        ;;
esac