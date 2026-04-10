#!/bin/bash

conflict_count=0
identical_count=0

# Read first 100 files from intersection
while IFS= read -r file; do
    our_diff=$(git diff 317823f0fb0e5a21d52f3fe772efc68e3c5176ee HEAD -- "$file" 2>/dev/null)
    develop_diff=$(git diff 317823f0fb0e5a21d52f3fe772efc68e3c5176ee origin/develop -- "$file" 2>/dev/null)
    
    if [ "$our_diff" = "$develop_diff" ]; then
        ((identical_count++))
    else
        ((conflict_count++))
        if [ $conflict_count -le 10 ]; then
            echo "DIFFERENT: $file"
        fi
    fi
done < <(head -100 /home/runner/work/carlos/carlos/intersection.txt)

echo ""
echo "Summary of first 100 files:"
echo "  Identical changes: $identical_count"
echo "  Different changes (potential conflicts): $conflict_count"
