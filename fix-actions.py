import os
import glob

def fix_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # Clean file first
    lines = content.split('\n')
    cleaned_lines = []
    for line in lines:
        if 'FORCE_JAVASCRIPT_ACTIONS_TO_NODE24' not in line:
            cleaned_lines.append(line)

    content = '\n'.join(cleaned_lines)
    lines = content.split('\n')

    has_env = False
    for line in lines:
        if line == 'env:':
            has_env = True
            break

    if has_env:
        # insert right after env:
        new_lines = []
        for line in lines:
            new_lines.append(line)
            if line == 'env:':
                new_lines.append('  FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: true')
    else:
        # insert right before jobs:
        new_lines = []
        for line in lines:
            if line == 'jobs:':
                new_lines.append('env:')
                new_lines.append('  FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: true')
                new_lines.append('')
            new_lines.append(line)

    with open(filepath, 'w') as f:
        f.write('\n'.join(new_lines))

for filepath in glob.glob('.github/workflows/*.yml'):
    fix_file(filepath)
