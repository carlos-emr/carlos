#!/bin/bash
# run_ai.sh - Shell script wrapper for AI tools
AI_TOOL=$1
FILE_PATH=$2
shift 2

case "$AI_TOOL" in
    "aider")
        aider "$FILE_PATH" "$@" --yes --subtree-only

        exit_code=$?
        if [ $exit_code -ne 0 ]; then
            echo "Error: aider failed with exit code $exit_code" >&2
            exit $exit_code
        fi
        ;;
    "claude-code")
        claude --print "$FILE_PATH" "$@" --permission-mode acceptEdits

        exit_code=$?
        if [ $exit_code -ne 0 ]; then
            echo "Error: claude-code failed with exit code $exit_code" >&2
            exit $exit_code
        fi
        ;;
esac