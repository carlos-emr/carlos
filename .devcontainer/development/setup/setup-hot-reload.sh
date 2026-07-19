#!/bin/bash

# ============================================================================
# Configuration - Use environment variables with sensible defaults
# ============================================================================
SOURCE_DIR="${SOURCE_DIR:-/workspace/src/main/webapp}"
DEPLOY_DIR="${DEPLOY_DIR:-/usr/local/tomcat/webapps/oscar}"
LOG_FILE="${LOG_FILE:-/tmp/webapp-watcher.log}"
PID_FILE="${PID_FILE:-/tmp/webapp-watcher.pid}"

# Deployment wait timeout (seconds)
DEPLOY_TIMEOUT="${DEPLOY_TIMEOUT:-300}"  # 5 minutes

# Log rotation settings
MAX_LOG_LINES="${MAX_LOG_LINES:-10000}"

# Internal state
INOTIFY_PID=""

# ============================================================================
# Singleton Check - Ensure only one instance runs
# ============================================================================
check_already_running() {
  if [ -f "$PID_FILE" ]; then
    OLD_PID=$(cat "$PID_FILE")
    if kill -0 "$OLD_PID" 2>/dev/null; then
      echo "Hot reload watcher already running (PID: $OLD_PID)" | tee -a "$LOG_FILE"
      exit 0
    else
      echo "Stale PID file found, starting fresh watcher..."
      rm -f "$PID_FILE"
    fi
  fi

  # Backup check: look for ANY running instance
  EXISTING=$(pgrep -f "setup-hot-reload.sh" | grep -v $$)
  if [ -n "$EXISTING" ]; then
    echo "Found instances: $EXISTING"
    echo "Cleaning up..."
    kill -9 $EXISTING 2>/dev/null
    sleep 1
  fi
}

# ============================================================================
# Utility Functions
# ============================================================================

# Rotate log file if it exceeds MAX_LOG_LINES
rotate_log() {
  if [ -f "$LOG_FILE" ]; then
    local line_count=$(wc -l < "$LOG_FILE" 2>/dev/null || echo 0)
    if [ "$line_count" -gt "$MAX_LOG_LINES" ]; then
      echo "[$(date +'%H:%M:%S')] Log rotation: keeping last $MAX_LOG_LINES lines" >> "$LOG_FILE"
      tail -n "$MAX_LOG_LINES" "$LOG_FILE" > "$LOG_FILE.tmp" && mv "$LOG_FILE.tmp" "$LOG_FILE"
    fi
  fi
}

# Cleanup function for graceful shutdown
cleanup() {
  # Clean up script PID file
  if [ -f "$PID_FILE" ]; then
    rm -f "$PID_FILE"
    echo "[$(date +'%H:%M:%S')] Removed PID file" | tee -a "$LOG_FILE"
  fi

  echo "" | tee -a "$LOG_FILE"
  echo "[$(date +'%H:%M:%S')] Shutting down hot-reload watcher..." | tee -a "$LOG_FILE"
  
  # Stop all child processes (inotifywait and any background jobs)
  if [[ -n "$INOTIFY_PID" ]] && kill -0 "$INOTIFY_PID" 2>/dev/null; then
    kill "$INOTIFY_PID" 2>/dev/null
    echo "[$(date +'%H:%M:%S')] Stopped file monitoring (PID: $INOTIFY_PID)" | tee -a "$LOG_FILE"
  fi
  
  # Wait for all child processes to finish
  wait
  
  echo "=========================================" | tee -a "$LOG_FILE"
  exit 0
}

# ============================================================================
# Main File Processing Logic
# ============================================================================
process_file_event() {
  local full_path="$1"
  local events="$2"
  
  # Use realpath to resolve symlinks and get absolute paths
  local abs_source_dir=$(realpath "$SOURCE_DIR")
  local abs_full_path=$(realpath -m "$full_path")  # -m doesn't require file to exist
  
  # Extract directory and filename
  local directory=$(dirname "$abs_full_path")
  local filename=$(basename "$abs_full_path")
  
  # Skip if it's a WEB-INF/classes or WEB-INF/lib change
  if [[ "$directory" == "$abs_source_dir/WEB-INF/classes"* ]] || \
     [[ "$directory" == "$abs_source_dir/WEB-INF/lib"* ]]; then
    return
  fi
  
  # Make sure changes are inside of source directory (use realpath comparison)
  local abs_deploy_dir=$(realpath "$DEPLOY_DIR")
  
  if [[ ! "$abs_full_path" == "$abs_source_dir"* ]]; then
    echo "[$(date +'%H:%M:%S')] Ignoring change outside source directory: $abs_full_path" >> "$LOG_FILE"
    return
  fi
  
  # Calculate relative path
  local RELATIVE_PATH="${directory#$abs_source_dir}"
  RELATIVE_PATH="${RELATIVE_PATH#/}"
  
  local SOURCE_FILE="$abs_full_path"
  
  # Proper path joining with slash handling
  if [ -z "$RELATIVE_PATH" ]; then
    local DEST_FILE="$abs_deploy_dir/$filename"
  else
    local DEST_FILE="$abs_deploy_dir/$RELATIVE_PATH/$filename"
  fi
  
  # Handle DELETE events
  if grep -qw "DELETE" <<< "$events"; then
    if [ -f "$DEST_FILE" ] || [ -d "$DEST_FILE" ]; then
      rm -rf "$DEST_FILE"
      if [ -z "$RELATIVE_PATH" ]; then
        echo "[$(date +'%H:%M:%S')] Deleted: $filename" | tee -a "$LOG_FILE"
      else
        echo "[$(date +'%H:%M:%S')] Deleted: $RELATIVE_PATH/$filename" | tee -a "$LOG_FILE"
      fi
    fi
  # Handle file updates (close_write or moved_to)
  elif [ -f "$SOURCE_FILE" ]; then
    mkdir -p "$(dirname "$DEST_FILE")"
    if cp "$SOURCE_FILE" "$DEST_FILE" 2>/dev/null; then
      if [ -z "$RELATIVE_PATH" ]; then
        echo "[$(date +'%H:%M:%S')] Updated: $filename" | tee -a "$LOG_FILE"
      else
        echo "[$(date +'%H:%M:%S')] Updated: $RELATIVE_PATH/$filename" | tee -a "$LOG_FILE"
      fi
    else
      if [ -z "$RELATIVE_PATH" ]; then
        echo "[$(date +'%H:%M:%S')] ERROR: Failed to copy $filename" >> "$LOG_FILE"
      else
        echo "[$(date +'%H:%M:%S')] ERROR: Failed to copy $RELATIVE_PATH/$filename" >> "$LOG_FILE"
      fi
    fi
  # Handle directory creation
  elif [ -d "$SOURCE_FILE" ]; then
    mkdir -p "$DEST_FILE"
    if [ -z "$RELATIVE_PATH" ]; then
      echo "[$(date +'%H:%M:%S')] Created dir: $filename" | tee -a "$LOG_FILE"
    else
      echo "[$(date +'%H:%M:%S')] Created dir: $RELATIVE_PATH/$filename" | tee -a "$LOG_FILE"
    fi
  else
    # Handle a missing source
    if [ -z "$RELATIVE_PATH" ]; then
      echo "[$(date +'%H:%M:%S')] WARN: Source is missing for event: $filename (events: $events)" >> "$LOG_FILE"
    else
      echo "[$(date +'%H:%M:%S')] WARN: Source is missing for event: $RELATIVE_PATH/$filename (events: $events)" >> "$LOG_FILE"
    fi
  fi
  
  # Rotate log periodically
  rotate_log
}

# ============================================================================
# Main Execution
# ============================================================================
main() {
  # Check if already running (singleton behavior)
  check_already_running

  # Set up signal handlers
  trap cleanup INT TERM EXIT

  > "$LOG_FILE"  # Clear log file on start
  
  # Write this script's PID to the PID file for tracking
  echo $$ > "$PID_FILE"
  
  echo "=========================================" | tee -a "$LOG_FILE"
  echo "[$(date +'%H:%M:%S')] Starting webapp file watcher..." | tee -a "$LOG_FILE"
  echo "Source: $SOURCE_DIR" | tee -a "$LOG_FILE"
  echo "Deploy: $DEPLOY_DIR" | tee -a "$LOG_FILE"
  echo "Log:    $LOG_FILE" | tee -a "$LOG_FILE"
  echo "Timeout: ${DEPLOY_TIMEOUT}s" | tee -a "$LOG_FILE"
  echo "Script PID: $$" | tee -a "$LOG_FILE"
  echo "=========================================" | tee -a "$LOG_FILE"
  echo "" | tee -a "$LOG_FILE"
  
  # Wait for initial deployment with timeout
  local elapsed=0
  local interval=2
  echo "Waiting for webapp deployment..." | tee -a "$LOG_FILE"
  
  while [ ! -d "$DEPLOY_DIR" ]; do
    if [ "$elapsed" -ge "$DEPLOY_TIMEOUT" ]; then
      echo "ERROR: $DEPLOY_DIR did not appear after ${DEPLOY_TIMEOUT}s" | tee -a "$LOG_FILE"
      echo "Check Tomcat logs for deployment issues:" | tee -a "$LOG_FILE"
      echo "  - docker logs <container_name>" | tee -a "$LOG_FILE"
      echo "  - Look in /usr/local/tomcat/logs/catalina.out" | tee -a "$LOG_FILE"
      exit 1
    fi
    sleep "$interval"
    elapsed=$((elapsed + interval))
    
    # Show progress every 10 seconds
    if [ $((elapsed % 10)) -eq 0 ]; then
      echo "  Still waiting... (${elapsed}s elapsed)" | tee -a "$LOG_FILE"
    fi
  done
  
  echo "Webapp deployed, watching for changes..." | tee -a "$LOG_FILE"
  echo "" | tee -a "$LOG_FILE"
  
  # Start file monitoring in background
  # Use --format to properly handle filenames with spaces
  inotifywait -m -r -e close_write,moved_to,delete \
    --format '%w%f %e' \
    --exclude '(^|/)(\.git|\.svn|node_modules|target|WEB-INF/classes|WEB-INF/lib)(/|$)|\.class$'  \
    "$SOURCE_DIR" 2>> "$LOG_FILE" |
  while read -r full_path events; do
    process_file_event "$full_path" "$events"
  done &
  
  # Capture the PID of the background pipeline
  INOTIFY_PID=$!
  echo "[$(date +'%H:%M:%S')] File monitoring started (PID: $INOTIFY_PID)" | tee -a "$LOG_FILE"
  
  # Wait for all child processes (keeps script running)
  wait
}

# Run main function
main