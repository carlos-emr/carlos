# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
import re,sys
from collections import defaultdict, OrderedDict
def load(p):
    d=OrderedDict()
    for line in open(p,encoding='utf-8'):
        parts=line.rstrip('\n').split('\t')
        if len(parts)>=3: d[(parts[0],parts[1])]='\t'.join(parts[2:])
    return d
A,B,section=sys.argv[1:4]
a=load(A); b=load(B)
def kind(v): return 'E' if v.startswith('EX:') else 'V'
cells=defaultdict(set); cols=[]; getters=[]
for k in a:
    if k[0]!=section: continue
    m=re.match(r'(.*)@([^#@]*)#(.*)$',k[1])
    if not m: continue
    col,row,g=m.groups()
    if col not in cols: cols.append(col)
    if g not in getters: getters.append(g)
    va,vb=a[k],b.get(k,'MISSING')
    if va==vb: cells[(col,g)].add('=')
    else:
        ka,kb=kind(va),kind(vb)
        cells[(col,g)].add({'VV':'V!','VE':'VE','EV':'EV','EE':'e'}[ka+kb])
want=sys.argv[4].split(',') if len(sys.argv)>4 else getters
short={'getString':'Str','getObject':'Obj','getBoolean':'Bool','getByte':'Byte','getShort':'Shrt','getInt':'Int','getLong':'Long','getFloat':'Flt','getDouble':'Dbl','getBigDecimal':'BigD','getBytes':'Bytes','getDate':'Date','getTime':'Time','getTimestamp':'Tstmp','getCharacterStream':'ChStr','getBinaryStream':'BinStr','getAsciiStream':'AscStr','getClob':'Clob','getBlob':'Blob','getNString':'NStr'}
hdr='%-34s' % 'column'
for g in want: hdr+=' %-6s' % short.get(g,g.replace('getObject(','o(').replace(')',''))[:6]
print(hdr)
for col in cols:
    line='%-34s' % col[:34]
    for g in want:
        s=cells.get((col,g),set())
        code=''.join(sorted(x for x in s if x!='=' and x!='e')) or ('e' if 'e' in s else '.')
        line+=' %-6s' % code[:6]
    print(line)
print("legend: . same | e both throw (different exception details) | V! both return values, values differ | VE cj value -> adapter exception | EV cj exception -> adapter value")
