"""
Find Kotlin declarations where a trailing lambda binds to the WRONG parameter.

Shape: the LAST parameter has a default value, but an earlier parameter is a
required function type. Kotlin binds a trailing lambda to the last parameter,
so the required lambda is left unfilled and the compiler says
"No value passed for parameter 'x'".
"""
import re, pathlib, sys, collections

ROOTS = ["app/src/main", "shared/src"]
files = [p for r in ROOTS for p in pathlib.Path(r).rglob("*.kt")]

decl_re = re.compile(
    r'^[ \t]*(?:(?:private|internal|public|inline|suspend)\s+)*fun\s+([A-Za-z_]\w*)\s*\(', re.M)

ARROW = "\x00ARROW\x00"

def split_params(text):
    """Split on top-level commas. `->` is masked first: its '>' would otherwise
    be read as closing a generic and throw the depth count off — which is
    exactly the mistake that made the first version of this scanner report a
    false all-clear."""
    text = text.replace("->", ARROW)
    parts, depth, cur = [], 0, ""
    for ch in text:
        if ch in "(<[": depth += 1
        elif ch in ")>]": depth -= 1
        if ch == "," and depth == 0:
            parts.append(cur); cur = ""
        else:
            cur += ch
    if cur.strip(): parts.append(cur)
    return [p.strip().replace(ARROW, "->") for p in parts if p.strip()]

def balanced_end(src, open_idx):
    depth, j = 0, open_idx
    while j < len(src):
        if src[j] == "(": depth += 1
        elif src[j] == ")":
            depth -= 1
            if depth == 0: return j
        j += 1
    return -1

def has_default(param):
    """A default value, ignoring '=' inside a nested lambda body."""
    body = param.split(":", 1)[-1]
    return "=" in body.replace("==", "").replace("<=", "").replace(">=", "")

def is_required_lambda(param):
    return "->" in param and not has_default(param)

suspects = {}
for path in files:
    src = path.read_text(encoding="utf-8", errors="replace")
    for m in decl_re.finditer(src):
        name = m.group(1)
        end = balanced_end(src, m.end() - 1)
        if end < 0: continue
        params = split_params(src[m.end():end])
        if len(params) < 2: continue
        if has_default(params[-1]) and any(is_required_lambda(p) for p in params[:-1]):
            suspects[name] = (str(path), src[:m.start()].count("\n") + 1, params)

# Which suspects are actually CALLED with a trailing lambda?
broken = collections.defaultdict(list)
for name in suspects:
    call_re = re.compile(r'(?<![\w.])' + re.escape(name) + r'\s*\(')
    for path in files:
        src = path.read_text(encoding="utf-8", errors="replace")
        for m in call_re.finditer(src):
            line_start = src.rfind("\n", 0, m.start()) + 1
            if re.search(r'\bfun\s+$', src[line_start:m.start()]):
                continue                      # the declaration itself
            end = balanced_end(src, m.end() - 1)
            if end < 0: continue
            after = src[end + 1:end + 60].lstrip()
            if not after.startswith("{"):
                continue                      # no trailing lambda
            args = src[m.end():end]
            # A named argument for the required lambda means the call is fine.
            lambda_names = [p.split(":")[0].strip() for p in suspects[name][2] if is_required_lambda(p)]
            if any(re.search(r'\b' + re.escape(ln) + r'\s*=', args) for ln in lambda_names):
                continue
            broken[name].append((str(path), src[:m.start()].count("\n") + 1))

print(f"Scanned {len(files)} Kotlin files.\n")
if not suspects:
    print("No declarations with this shape.")
for name, (f, line, params) in suspects.items():
    sites = broken.get(name)
    print(f"{'BROKEN ' if sites else 'ok     '} {name}()   {f}:{line}")
    print(f"          params: {', '.join(p.split(':')[0].strip() for p in params)}")
    for cf, cl in sites or []:
        print(f"          call site (trailing lambda): {cf}:{cl}")
    print()
sys.exit(1 if broken else 0)
