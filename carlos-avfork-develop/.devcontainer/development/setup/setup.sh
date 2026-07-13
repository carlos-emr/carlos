#!/bin/bash

# Define the directory and the script name
SCRIPTS_DIR="/scripts"

# Make the script executable
chmod +x "$SCRIPTS_DIR/*"

# Add the scripts directory to the PATH if it's not already included
SHELL_CONFIG="$HOME/.bashrc"
if [[ "$SHELL" == *"zsh"* ]]; then
    SHELL_CONFIG="$HOME/.zshrc"
fi

# Check if the PATH update is already in the config file
if ! grep -q "export PATH=\"\$PATH:$SCRIPTS_DIR\"" "$SHELL_CONFIG"; then
    echo "export PATH=\"\$PATH:$SCRIPTS_DIR\"" >> "$SHELL_CONFIG"
    echo "Added $SCRIPTS_DIR to PATH in $SHELL_CONFIG"
else
    echo "$SCRIPTS_DIR is already in PATH"
fi

# Source the shell configuration file to apply changes immediately
# shellcheck disable=SC1090
source "$SHELL_CONFIG"
