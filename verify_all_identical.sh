#!/bin/bash

count=0
identical_count=0
different_count=0
different_files=()

while IFS= read -r file; do
    count=$((count + 1))
    
    git diff 317823f0fb0e5a21d52f3fe772efc68e3c5176ee HEAD -- "$file" > /tmp/our_$count.txt 2>&1
    git diff 317823f0fb0e5a21d52f3fe772efc68e3c5176ee origin/develop -- "$file" > /tmp/dev_$count.txt 2>&1
    
    if cmp /tmp/our_$count.txt /tmp/dev_$count.txt 2>/dev/null; then
        identical_count=$((identical_count + 1))
    else
        different_count=$((different_count + 1))
        different_files+=("$file")
    fi
    
    # Clean up temp files to save space
    rm -f /tmp/our_$count.txt /tmp/dev_$count.txt
    
    # Report progress every 100 files
    if [ $((count % 100)) -eq 0 ]; then
        echo "Checked $count files: $identical_count identical, $different_count different"
    fi
    
done < /home/runner/work/carlos/carlos/intersection.txt

echo ""
echo "=== FINAL SUMMARY ==="
echo "Total files: $count"
echo "Identical changes: $identical_count"
echo "Different changes: $different_count"
echo ""

if [ ${#different_files[@]} -gt 0 ]; then
    echo "Files with DIFFERENT changes (potential conflicts):"
    for f in "${different_files[@]}"; do
        echo "  - $f"
    done
else
    echo "NO FILES WITH DIFFERENT CHANGES FOUND"
fi
