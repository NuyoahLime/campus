#!/usr/bin/env python3
"""v1.2-AI-FROZEN business spec validator v1.2.1"""
import yaml, re, os, sys, io
from pathlib import Path
from datetime import datetime

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
ROOT = Path(__file__).parent.parent
SPEC_DIR = ROOT / "docs" / "business-spec"
REG_PATH = ROOT / "docs" / "validation" / "business-spec-registry.yaml"
DEC_PATH = ROOT / "docs" / "decision" / "业务决策记录-v1.2.md"

errors = []; warnings = []

def err(msg): errors.append(msg)
def warn(msg): warnings.append(msg)

# ============================================================
# Load registry
# ============================================================
if not REG_PATH.exists(): err(f"MISSING: {REG_PATH}"); sys.exit(1)
with open(REG_PATH, encoding='utf-8') as f: reg = yaml.safe_load(f)
if not DEC_PATH.exists(): err(f"MISSING: {DEC_PATH}")
dec_content = DEC_PATH.read_text(encoding='utf-8') if DEC_PATH.exists() else ""

SPEC_FILES = [f"{i:02d}-" for i in range(1,12)] + ["业务冻结检查表"]
spec_paths = {}
for prefix in SPEC_FILES:
    for f in SPEC_DIR.glob(f"{prefix}*"):
        spec_paths[f.stem] = f

print(f"=== v1.2-AI-FROZEN Validator v1.2.1 ===")
print(f"Time: {datetime.now().isoformat()}")
print(f"Files checked: {len(spec_paths)}")

# ============================================================
# 1. DEC checks
# ============================================================
reg_decs = set(d['id'] for d in reg.get('decisions', []))
rec_decs = set(re.findall(r'DEC-[A-Z0-9-]+', dec_content))
spec_decs = set()
for sf in spec_paths.values():
    content = sf.read_text(encoding='utf-8')
    spec_decs.update(re.findall(r'DEC-[A-Z0-9-]+', content))

if len(reg_decs) != 25: err(f"Registry DEC count: {len(reg_decs)} (expected 25)")
else: print(f"[PASS] Registry DEC: 25")
if reg_decs != rec_decs: err(f"Decision record DEC mismatch: reg={len(reg_decs)} rec={len(rec_decs)}")
else: print(f"[PASS] Decision record DECs match registry")
p0_decs = {d for d in spec_decs if d.startswith('P0-')}
if p0_decs: err(f"P0- prefixed IDs in DEC context: {p0_decs}")

# Check for unknown DECs in spec files
unknown = spec_decs - reg_decs - {d for d in spec_decs if d.startswith('P0-')}
if unknown: err(f"Unknown DECs in specs: {unknown}")

# ============================================================
# 2. Decision package check
# ============================================================
packages = set(d.get('package', d.get('package_id', '')) for d in reg.get('decisions', []))
packages.discard('')
if len(packages) != 13: err(f"Decision packages: {len(packages)} (expected 13)")
else: print(f"[PASS] Decision packages: 13")

# ============================================================
# 3. DEC header/footer consistency
# ============================================================
for name, path in spec_paths.items():
    if '业务冻结检查表' in name: continue
    content = path.read_text(encoding='utf-8')
    dec_lines = re.findall(r'> \*\*关联决策\*\*: (.+)', content)
    if len(dec_lines) < 2: err(f"{name}: missing DEC header/footer"); continue
    top = [d.strip() for d in dec_lines[0].split(',')]
    bot = [d.strip() for d in dec_lines[-1].split(',')]
    if set(top) != set(bot): err(f"{name}: DEC header/bottom mismatch")
    if len(bot) != len(set(bot)): err(f"{name}: duplicate DECs: {[d for d in bot if bot.count(d)>1]}")

# ============================================================
# 4. P1/P2 checks
# ============================================================
p1_all = set(); p2_all = set()
for sf in spec_paths.values():
    content = sf.read_text(encoding='utf-8')
    p1_all.update(re.findall(r'P1-[A-Z0-9-]+', content))
    p2_all.update(re.findall(r'P2-[A-Z0-9-]+', content))
    bare = re.findall(r'【DEFERRED-P1】(?![\s]*:)', content)
    if bare: err(f"{sf.name}: {len(bare)} bare DEFERRED-P1 without ID")
    bare2 = re.findall(r'【DEFERRED-P2】(?![\s]*:)', content)
    if bare2: err(f"{sf.name}: {len(bare2)} bare DEFERRED-P2 without ID")

if len(p1_all) != 6: err(f"Unique P1: {len(p1_all)} (expected 6): {sorted(p1_all)}")
else: print(f"[PASS] P1: 6 unique IDs")
if len(p2_all) != 4: err(f"Unique P2: {len(p2_all)} (expected 4): {sorted(p2_all)}")
else: print(f"[PASS] P2: 4 unique IDs")

# ============================================================
# 5. Forbidden phrases
# ============================================================
forbidden = reg.get('forbidden_phrases', [])
for sf in spec_paths.values():
    content = sf.read_text(encoding='utf-8')
    for phrase in forbidden:
        if phrase and phrase in content:
            err(f"{sf.name}: forbidden phrase '{phrase}'")

# ============================================================
# 6. State machine rules (regex-based, proper patterns)
# ============================================================
for sf in spec_paths.values():
    content = sf.read_text(encoding='utf-8')
    name = sf.name

    # School: disable must not go directly to normal
    if '02-学校' in name:
        if re.search(r'停用\s*(?:→|->|->|→)\s*正常', content):
            err(f"{name}: school 停用→正常 prohibited")
        if re.search(r'正常.*前置.*停用|停用.*后续.*正常', content):
            warn(f"{name}: check school normal state doesn't list 停用 as prev")

    # Feedback: 已升级 must not go to 已处理
    if '09-反馈' in name:
        if re.search(r'已升级\s*(?:→|->)\s*已处理', content):
            err(f"{name}: feedback 已升级→已处理 prohibited")

    # Appeal: platform_decided must not go back to school
    if '07-成绩申诉' in name:
        if re.search(r'平台已裁决\s*(?:→|->)\s*平台退回学校', content):
            err(f"{name}: appeal 平台已裁决→平台退回学校 prohibited")
        if re.search(r'已升级\s*(?:→|->)\s*已解决', content):
            err(f"{name}: appeal 已升级→已解决 prohibited")

    # Activity: draft must not withdraw
    if '04-活动' in name:
        if re.search(r'草稿\s*(?:→|->)\s*已撤回', content):
            err(f"{name}: activity application 草稿→已撤回 prohibited")

# ============================================================
# 7. Code block check
# ============================================================
for sf in spec_paths.values():
    content = sf.read_text(encoding='utf-8')
    count = content.count('```')
    if count % 2 != 0:
        err(f"{sf.name}: unclosed code block ({count} backtick fences)")
        # Find line numbers
        lines = content.split('\n')
        for i, line in enumerate(lines, 1):
            if line.startswith('```'):
                print(f"  [{sf.name}:{i}] {line.strip()}")

# ============================================================
# 8. Ranking lifecycle check
# ============================================================
for sf in spec_paths.values():
    content = sf.read_text(encoding='utf-8')
    if '11-生命' in sf.name:
        if '排行榜定义' not in content: err(f"{sf.name}: missing 排行榜定义")
        if '排行榜版本' not in content: err(f"{sf.name}: missing 排行榜版本")
        if 'L3' not in content and '数据授权' not in content: err(f"{sf.name}: missing L3 auth lifecycle")

# ============================================================
# 9. Duplicate state check in Markdown tables
# ============================================================
for sf in spec_paths.values():
    content = sf.read_text(encoding='utf-8')
    # Find tables with state columns
    state_names_in_table = []
    in_table = False
    for line in content.split('\n'):
        if line.startswith('|') and '状态' in line and '含义' in line:
            in_table = True; state_names_in_table = []; continue
        if in_table and line.startswith('|') and not '|---' in line:
            parts = [p.strip() for p in line.split('|') if p.strip()]
            if parts: state_names_in_table.append(parts[0])
        if in_table and not line.startswith('|'):
            # End of table - check for duplicates
            seen = set()
            for s in state_names_in_table:
                if s in seen: err(f"{sf.name}: duplicate state '{s}' in table")
                seen.add(s)
            in_table = False

# ============================================================
# 10. Change log check
# ============================================================
for sf in spec_paths.values():
    content = sf.read_text(encoding='utf-8')
    name = sf.name
    if '11-生命' in name:
        if 'v1.2-AI-FROZEN' not in content:
            err(f"{name}: missing v1.2 change log")
    elif '业务冻结检查表' not in name:
        if 'v1.0-AI-FROZEN' not in content and 'v1.1-AI-FROZEN' not in content:
            warn(f"{name}: no v1.0/v1.1 change log entries")

# ============================================================
# 11. State machine registry validation
# ============================================================
sms = reg.get('state_machines', None)

# Negative: missing key
if sms is None:
    err("Registry missing 'state_machines' key")
    sms = []
# Negative: empty list
elif len(sms) == 0:
    err("Registry 'state_machines' is empty (must register all state machines)")

# Negative: duplicate IDs
sm_ids = [sm.get('id', '') for sm in sms]
dup_ids = [i for i in set(sm_ids) if sm_ids.count(i) > 1]
if dup_ids:
    err(f"Duplicate state machine IDs: {dup_ids}")

# Negative: count mismatch vs declared total
declared_sm_count = reg.get('total_state_machines', None)
if declared_sm_count is not None and len(sms) != declared_sm_count:
    err(f"State machine count mismatch: declared={declared_sm_count}, actual={len(sms)}")

# Per-machine validation
for sm in sms:
    sid = sm.get('id', 'unknown')
    states = sm.get('states', [])
    codes = {s['code'] for s in states}
    state_map = {s['code']: s for s in states}

    # Negative: empty states list
    if len(states) == 0:
        err(f"SM {sid}: empty states list")
        continue

    # Unique codes
    if len(codes) != len(states):
        err(f"SM {sid}: duplicate state codes")

    # Initial states
    initials = [s for s in states if s.get('previous', s.get('prev', [])) == []]
    if not initials:
        err(f"SM {sid}: no initial state")
    if len(initials) > 1:
        warn(f"SM {sid}: multiple initial states: {[s['code'] for s in initials]}")

    for s in states:
        code = s['code']
        prev = s.get('previous', s.get('prev', []))
        nxt = s.get('next', [])

        # Negative: undefined transitions
        for p in prev:
            if p not in codes:
                err(f"SM {sid}: {code}.prev.{p} undefined state")
        for n in nxt:
            if n not in codes:
                err(f"SM {sid}: {code}.next.{n} undefined state")

        # Terminal has no next
        if s.get('terminal', False) and len(nxt) > 0:
            err(f"SM {sid}: terminal {code} has next states {nxt}")

        # Symmetry: A→B implies B.prev includes A (skip initial states)
        for n in nxt:
            if n in state_map:
                tn = state_map[n]
                tn_prev = tn.get('previous', tn.get('prev', []))
                if code not in tn_prev and tn_prev:
                    err(f"SM {sid}: {code}→{n} asymmetric, {n}.prev missing {code}")

    # Negative: unreachable states (non-initial with no incoming transitions)
    all_targets = set()
    for s in states:
        all_targets.update(s.get('next', []))
    for s in states:
        if s.get('previous', s.get('prev', [])) != [] and s['code'] not in all_targets:
            warn(f"SM {sid}: state {s['code']} has prev but no incoming transitions (possibly unreachable)")

    # Negative: non-terminal dead ends
    for s in states:
        if not s.get('terminal', False) and len(s.get('next', [])) == 0:
            err(f"SM {sid}: non-terminal {s['code']} has no next states (dead end)")

declared = declared_sm_count if declared_sm_count is not None else 'not set'
print(f"[PASS] State machine check: {len(sms)} registered, declared={declared}")

# ============================================================
# 12. Version check
# ============================================================
for name, path in spec_paths.items():
    content = path.read_text(encoding='utf-8')
    if 'v1.2-AI-FROZEN' not in content:
        err(f"{name}: not v1.2-AI-FROZEN")

# ============================================================
# 13. Markdown table column counts
# ============================================================
for sf in spec_paths.values():
    content = sf.read_text(encoding='utf-8')
    lines = content.split('\n')
    in_table = False; col_count = 0
    for i, line in enumerate(lines):
        if line.startswith('|') and line.endswith('|'):
            if not in_table:
                in_table = True; col_count = len(line.split('|')) - 2
            else:
                if '|---' in line: continue
                actual = len(line.split('|')) - 2
                if actual != col_count:
                    err(f"{sf.name}:{i+1}: col mismatch ({col_count} vs {actual})")
        else:
            in_table = False

# ============================================================
# Summary
# ============================================================
print(f"\n{'='*50}")
print(f"Validator version: v1.2.1")
print(f"Errors:   {len(errors)}")
print(f"Warnings: {len(warnings)}")
print(f"Result:   {'PASS' if len(errors)==0 else 'FAIL'}")
for e in errors: print(f"  [FAIL] {e}")
for w in warnings: print(f"  [WARN] {w}")
print(f"Exit code: {1 if errors else 0}")
sys.exit(1 if errors else 0)
