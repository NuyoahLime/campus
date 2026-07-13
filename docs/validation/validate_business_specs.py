#!/usr/bin/env python3
"""v1.2-AI-FROZEN business spec validator"""
import yaml, re, os, sys, io
from pathlib import Path
from datetime import datetime

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
ROOT = Path(__file__).parent.parent
SPEC_DIR = ROOT / "docs" / "business-spec"
REG_PATH = ROOT / "docs" / "spec" / "business-spec-registry.yaml"
DEC_PATH = ROOT / "docs" / "decision" / "业务决策记录-v1.2.md"

errors = []

def err(msg): errors.append(msg)

# Load registry
if not REG_PATH.exists(): err(f"MISSING: {REG_PATH}"); sys.exit(1)
with open(REG_PATH, encoding='utf-8') as f: reg = yaml.safe_load(f)

# Load decision record
if not DEC_PATH.exists(): err(f"MISSING: {DEC_PATH}")
dec_content = DEC_PATH.read_text(encoding='utf-8') if DEC_PATH.exists() else ""

SPEC_FILES = [f"{i:02d}-" for i in range(1,12)] + ["业务冻结检查表"]
spec_paths = {}
for prefix in SPEC_FILES:
    for f in SPEC_DIR.glob(f"{prefix}*"):
        spec_paths[f.stem] = f

print(f"=== v1.2-AI-FROZEN Validation ===")
print(f"Time: {datetime.now().isoformat()}")
print(f"Files checked: {len(spec_paths)}")

# 1. Version check
for name, path in spec_paths.items():
    content = path.read_text(encoding='utf-8')
    if 'v1.2-AI-FROZEN' not in content:
        err(f"{name}: not v1.2-AI-FROZEN")

# 2. DEC count
dec_ids = [d['id'] for d in reg.get('decisions', [])]
if len(set(dec_ids)) != 25: err(f"DEC count: {len(set(dec_ids))} (expected 25)")
else: print(f"[PASS] DEC: 25")

# 3. Decision record DECs
dec_in_record = len(re.findall(r'DEC-[A-Z0-9-]+', dec_content))
print(f"[INFO] DECs in decision record: {dec_in_record}")

# 4. DEC header/footer consistency
for name, path in spec_paths.items():
    if '业务冻结检查表' in name: continue
    content = path.read_text(encoding='utf-8')
    decs = re.findall(r'> \*\*关联决策\*\*: (.+)', content)
    if len(decs) < 2:
        err(f"{name}: missing DEC header or footer")
        continue
    top = set(d.strip() for d in decs[0].split(','))
    bottom = set(d.strip() for d in decs[-1].split(','))
    if top != bottom:
        err(f"{name}: DEC mismatch top={top} bottom={bottom}")
    # check for dupes in bottom
    bottom_list = [d.strip() for d in decs[-1].split(',')]
    if len(bottom_list) != len(set(bottom_list)):
        err(f"{name}: duplicate DECs in footer: {bottom_list}")

# 5. P1/P2 unique IDs
for sf in spec_paths.values():
    content = sf.read_text(encoding='utf-8')
    p1s = set(re.findall(r'P1-[A-Z0-9-]+', content))
    p2s = set(re.findall(r'P2-[A-Z0-9-]+', content))
    bare_p1 = re.findall(r'【DEFERRED-P1】(?!:)', content)
    bare_p2 = re.findall(r'【DEFERRED-P2】(?!:)', content)
    if bare_p1: err(f"{sf.name}: bare DEFERRED-P1 without ID: {len(bare_p1)} instances")
    if bare_p2: err(f"{sf.name}: bare DEFERRED-P2 without ID: {len(bare_p2)} instances")

p1_all = set()
p2_all = set()
for sf in spec_paths.values():
    content = sf.read_text(encoding='utf-8')
    p1_all.update(re.findall(r'P1-[A-Z0-9-]+', content))
    p2_all.update(re.findall(r'P2-[A-Z0-9-]+', content))
if len(p1_all) != 6: err(f"Unique P1 IDs: {len(p1_all)} (expected 6): {sorted(p1_all)}")
else: print(f"[PASS] P1: 6 unique IDs")
if len(p2_all) != 4: err(f"Unique P2 IDs: {len(p2_all)} (expected 4): {sorted(p2_all)}")
else: print(f"[PASS] P2: 4 unique IDs")

# 6. Forbidden phrases
forbidden = reg.get('forbidden_phrases', [])
for sf in spec_paths.values():
    content = sf.read_text(encoding='utf-8')
    for phrase in forbidden:
        if phrase and phrase in content:
            err(f"{sf.name}: forbidden phrase '{phrase}'")

# 7. State machine checks in spec files
for sf in spec_paths.values():
    content = sf.read_text(encoding='utf-8')
    if '草稿' in content and '已撤回' in content:
        # Check school registration: draft can't go to withdrawn
        if '02-学校' in sf.name:
            if re.search(r'草稿.*→.*已撤回|草稿→已撤回', content):
                err(f"{sf.name}: 入驻申请草稿不应能进入已撤回")
        if '04-活动' in sf.name:
            if re.search(r'草稿.*→.*已撤回|草稿→已撤回', content):
                err(f"{sf.name}: 活动申请草稿不应能进入已撤回")
    if '停用.*正常' in content and '02-学校' in sf.name:
        err(f"{sf.name}: 学校停用不应直接进入正常")
    if '已升级.*已处理' in content and '09-反馈' in sf.name:
        err(f"{sf.name}: 已升级不应直接进入已处理")
    if '平台已裁决.*退回学校' in content and '07-成绩申诉' in sf.name:
        err(f"{sf.name}: 平台已裁决不应退回学校")

# 8. Project rule check
for sf in spec_paths.values():
    content = sf.read_text(encoding='utf-8')
    if '03-挑战' in sf.name:
        if re.search(r'持久化.*是否允许小数|字段.*是否允许小数', content):
            err(f"{sf.name}: 持久化字段'是否允许小数'应删除")
        if '兼容关系' not in content and 'compatibility' not in content:
            err(f"{sf.name}: 缺少项目规则版本兼容关系")

# 9. Ranking lifecycle
for sf in spec_paths.values():
    content = sf.read_text(encoding='utf-8')
    if '11-生命' in sf.name:
        if re.search(r'仅草稿计算状态且无关联版本的排行榜可物理删除', content):
            err(f"{sf.name}: 残留通用排行榜生命周期旧规则")

# 10. Activity public state split
for sf in spec_paths.values():
    content = sf.read_text(encoding='utf-8')
    if '04-活动' in sf.name:
        if '学校已撤回' not in content:
            err(f"{sf.name}: 缺少'学校已撤回'状态")
        if '平台已下架' not in content:
            err(f"{sf.name}: 缺少'平台已下架'状态")

# 11. Markdown checks
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
                    err(f"{sf.name}:{i+1}: table col mismatch ({col_count} vs {actual})")
        else:
            in_table = False

# 12. Chapter numbering
for sf in spec_paths.values():
    content = sf.read_text(encoding='utf-8')
    h1s = [int(n) for n in re.findall(r'^## (\d+)\.', content, re.MULTILINE)]
    for i in range(1, len(h1s)):
        if h1s[i] != h1s[i-1] + 1:
            err(f"{sf.name}: chapter gap {h1s[i-1]}→{h1s[i]}")

# Summary
print(f"\n{'='*50}")
print(f"Errors: {len(errors)}")
print(f"Result: {'PASS' if len(errors)==0 else 'FAIL'}")
for e in errors: print(f"  [FAIL] {e}")
sys.exit(0 if len(errors) == 0 else 1)
