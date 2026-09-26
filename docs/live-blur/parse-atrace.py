import re, sys, collections
pat = re.compile(r'^\s*(.+?)-(\d+)\s+\(\s*(\d+|-+)\)\s+\[\d+\]\s+\S+\s+([\d.]+): tracing_mark_write: ([BE])\|?(\d*)\|?(.*)$')
def parse(path):
    stacks = collections.defaultdict(list)
    tot = collections.defaultdict(float); cnt = collections.Counter()
    comms = {}
    app_pid = None
    for line in open(path, errors='replace'):
        if 'tracing_mark_write' not in line: continue
        m = pat.match(line)
        if not m: continue
        comm, tid, pid, ts, kind, mpid, name = m.groups()
        ts = float(ts)
        if kind == 'B':
            if 'bluroverlay' in comm or comm.startswith('RenderThread') or comm.startswith('mqt_') :
                pass
            stacks[tid].append((name.strip(), ts)); comms[tid] = comm
        else:
            if stacks[tid]:
                n, t0 = stacks[tid].pop()
                key = (comms.get(tid, comm), n)
                tot[key] += (ts - t0) * 1000; cnt[key] += 1
    return tot, cnt
tot, cnt = parse(sys.argv[1])
rows = [(k, tot[k], cnt[k]) for k in tot if k[0].startswith(('RenderThread', 'bluroverlayexa', 'mqt_'))]
rows.sort(key=lambda r: -r[1])
for (thr, name), t, c in rows[:int(sys.argv[2]) if len(sys.argv) > 2 else 25]:
    print(f'{thr[:16]:16} {t:9.1f}ms {c:6d}x {t/c:7.3f}ms  {name[:70]}')
