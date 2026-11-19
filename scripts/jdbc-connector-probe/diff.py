#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
"""Diff two probe outputs. usage: diff.py A.tsv B.tsv SECTION [mode] [filter-substring]

modes:
  list     : every differing key (default)
  summary  : counts per section
  rsmeta   : group RSMETA differences by (column type, attribute, a->b)
  values   : group VALUES/EXPR differences by (column, getter), classify
"""
import re
import sys
from collections import Counter, OrderedDict, defaultdict


def load(path):
    d = OrderedDict()
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            parts = line.split("\t")
            if len(parts) < 3:
                continue
            d[(parts[0], parts[1])] = "\t".join(parts[2:])
    return d


def kind(v):
    return "EX" if v.startswith("EX:") else "VAL"


def main():
    a = load(sys.argv[1])
    b = load(sys.argv[2])
    section = sys.argv[3] if len(sys.argv) > 3 else "ALL"
    mode = sys.argv[4] if len(sys.argv) > 4 else "list"
    flt = sys.argv[5] if len(sys.argv) > 5 else None
    keys = [k for k in a if section == "ALL" or k[0] == section or k[0].startswith(section)]
    if mode == "summary":
        tot = Counter()
        dif = Counter()
        for k in a:
            tot[k[0]] += 1
            if a[k] != b.get(k):
                dif[k[0]] += 1
        for s in tot:
            print("%-12s total=%-7d differing=%d" % (s, tot[s], dif[s]))
        missing = [k for k in b if k not in a]
        print("keys only in B:", len(missing))
        return
    if mode == "rsmeta":
        groups = defaultdict(Counter)
        examples = {}
        for k in keys:
            va, vb = a[k], b.get(k)
            if va == vb:
                continue
            m = re.match(r"(.*)\[(.*)\]#(.*)", k[1])
            if not m:
                print("??", k, va, vb)
                continue
            col, ctype, attr = m.groups()
            ctype_n = re.sub(r"varchar\(\d+\)", "varchar(N)", ctype)
            ctype_n = re.sub(r"char\(\d+\)", "char(N)", ctype_n) if not ctype_n.startswith("varchar") else ctype_n
            ctype_n = re.sub(r"^int\(\d+\)", "int(N)", ctype_n)
            ctype_n = re.sub(r"^bigint\(\d+\)", "bigint(N)", ctype_n)
            g = (attr, ctype_n, va, vb)
            groups[attr][(ctype_n, va, vb)] += 1
            examples.setdefault(g, col)
        for attr in groups:
            print("== attribute:", attr)
            for (ctype, va, vb), n in sorted(groups[attr].items(), key=lambda x: (-x[1], x[0])):
                if flt and flt not in ctype:
                    continue
                print("   %-28s  cj=%-34s adapter=%-34s x%d  e.g. %s" % (ctype, va, vb, n, examples[(attr, ctype, va, vb)]))
        return
    if mode == "values":
        # key = col@row#getter
        per = defaultdict(list)
        for k in keys:
            va, vb = a[k], b.get(k)
            if va == vb:
                continue
            m = re.match(r"(.*)@([^#@]*)#(.*)$", k[1])
            if not m:
                per[("?", k[1])].append(("", va, vb))
                continue
            col, row, getter = m.groups()
            per[(col, getter)].append((row, va, vb))
        cls_count = Counter()
        for (col, getter), rows in per.items():
            if flt and flt not in col and flt not in getter:
                continue
            classes = Counter()
            for row, va, vb in rows:
                classes[kind(va) + "->" + kind(vb)] += 1
                cls_count[kind(va) + "->" + kind(vb)] += 1
            print("%s # %s   %s" % (col, getter, dict(classes)))
            shown = set()
            for row, va, vb in rows:
                sig = (va, vb)
                if sig in shown:
                    continue
                shown.add(sig)
                if len(shown) > 6:
                    print("      ...")
                    break
                print("      row %-3s cj=%s  |  adapter=%s" % (row, va, vb))
        print("TOTAL classes:", dict(cls_count))
        return
    for k in keys:
        va, vb = a[k], b.get(k)
        if va != vb and (not flt or flt in k[1]):
            print("[%s] %s\n      cj      = %s\n      adapter = %s" % (k[0], k[1], va, vb))


if __name__ == "__main__":
    main()
