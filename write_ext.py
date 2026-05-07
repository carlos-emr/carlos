import sys

with open(sys.argv[1], 'w') as f:
    f.write(sys.stdin.read())