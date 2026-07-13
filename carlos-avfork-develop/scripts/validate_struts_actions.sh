#!/bin/bash

# Struts Action Validator
# This script validates that all action classes defined in struts.xml exist in the codebase

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(dirname "${BASH_SOURCE[0]}")"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
STRUTS_CONFIG="$PROJECT_ROOT/src/main/webapp/WEB-INF/classes/struts.xml"
JAVA_SOURCE_DIR="$PROJECT_ROOT/src/main/java"

# Counters
TOTAL_ACTIONS=0
VALID_ACTIONS=0
INVALID_ACTIONS=0

# Arrays to store results
MISSING_CLASSES=()
FOUND_CLASSES=()

echo "=========================================="
echo "Struts Action Class Validator"
echo "=========================================="
echo "Project Root: $PROJECT_ROOT"
echo "Struts Config: $STRUTS_CONFIG"
echo "Java Source: $JAVA_SOURCE_DIR"
echo "=========================================="

# Check if struts config exists
if [[ ! -f "$STRUTS_CONFIG" ]]; then
    echo -e "${RED}ERROR: Struts configuration file not found at $STRUTS_CONFIG${NC}"
    exit 1
fi

# Check if java source directory exists
if [[ ! -d "$JAVA_SOURCE_DIR" ]]; then
    echo -e "${RED}ERROR: Java source directory not found at $JAVA_SOURCE_DIR${NC}"
    exit 1
fi

echo "Parsing struts.xml for action classes..."
echo

# Function to convert class name to file path
class_to_path() {
    local class_name="$1"
    echo "${class_name//./\/}.java"
}

# Function to check if class exists
check_class_exists() {
    local class_name="$1"
    local file_path="$JAVA_SOURCE_DIR/$(class_to_path "$class_name")"
    
    if [[ -f "$file_path" ]]; then
        return 0
    else
        return 1
    fi
}

# Extract action classes from struts.xml using grep and sed
while IFS= read -r line; do
    if [[ $line =~ class=\"([^\"]+)\" ]]; then
        class_name=$(echo "$line" | sed -n 's/.*class="\([^"]*\)".*/\1/p')
        
        if [[ -n "$class_name" && "$class_name" != *"Action"* ]]; then
            continue  # Skip if it doesn't look like an action class
        fi
        
        if [[ -n "$class_name" ]]; then
            ((TOTAL_ACTIONS++))
            
            # Extract action name for better reporting
            action_name=$(echo "$line" | sed -n 's/.*<action[^>]*name="\([^"]*\)".*/\1/p')
            if [[ -z "$action_name" ]]; then
                action_name="(unnamed)"
            fi
            
            if check_class_exists "$class_name"; then
                ((VALID_ACTIONS++))
                FOUND_CLASSES+=("$action_name -> $class_name")
                echo -e "${GREEN}✓${NC} $action_name -> $class_name"
            else
                ((INVALID_ACTIONS++))
                MISSING_CLASSES+=("$action_name -> $class_name")
                echo -e "${RED}✗${NC} $action_name -> $class_name (NOT FOUND)"
            fi
        fi
    fi
done < "$STRUTS_CONFIG"

echo
echo "=========================================="
echo "VALIDATION SUMMARY"
echo "=========================================="
echo "Total actions found: $TOTAL_ACTIONS"
echo -e "Valid actions: ${GREEN}$VALID_ACTIONS${NC}"
echo -e "Invalid actions: ${RED}$INVALID_ACTIONS${NC}"

if [[ $INVALID_ACTIONS -gt 0 ]]; then
    echo
    echo -e "${RED}MISSING ACTION CLASSES:${NC}"
    echo "----------------------------------------"
    for missing in "${MISSING_CLASSES[@]}"; do
        echo -e "${RED}• $missing${NC}"
    done
    echo
    echo -e "${YELLOW}Suggested actions:${NC}"
    echo "1. Check if the class names are correct in struts.xml"
    echo "2. Verify if the classes were moved or renamed"
    echo "3. Consider removing unused action definitions"
    echo "4. Check if classes are in a different source directory"
    exit 1
else
    echo
    echo -e "${GREEN}✓ All action classes exist and are valid!${NC}"
    exit 0
fi