import os
import re

directory = "src/main/java"
pattern = re.compile(r'^(\s*(?://|\*|/\*)\s*(?:Clinical\s*)?)Context:\s*(.*)', re.IGNORECASE)

count = 0
for root, _, files in os.walk(directory):
    for file in files:
        if file.endswith(".java"):
            filepath = os.path.join(root, file)
            try:
                with open(filepath, 'r') as f:
                    lines = f.readlines()
            except UnicodeDecodeError:
                continue
                
            changed = False
            for i in range(len(lines)):
                match = pattern.match(lines[i])
                if match:
                    # lines[i] = match.group(1) + match.group(2) + "\n"
                    # But wait! If the original was `// Context: Blah`, `group(1)` is `// ` (if we didn't capture the Clinical part, wait, the regex is:
                    # `^(\s*(?://|\*|/\*)\s*(?:Clinical\s*)?)Context:\s*(.*)`
                    # So group(1) will be `    // ` or `    * `
                    # So `match.group(1) + match.group(2)` is perfect.
                    lines[i] = match.group(1) + match.group(2)
                    if not lines[i].endswith("\n"):
                        lines[i] += "\n"
                    changed = True
            
            if changed:
                with open(filepath, 'w') as f:
                    f.writelines(lines)
                count += 1
                print(f"Fixed {filepath}")

print(f"Total files fixed: {count}")
