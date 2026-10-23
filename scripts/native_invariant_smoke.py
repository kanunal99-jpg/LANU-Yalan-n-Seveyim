#!/usr/bin/env python3
"""Exercise shared native grammar rejection and recovery; this is not a dialogue quality score."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile

parser = argparse.ArgumentParser()
parser.add_argument("--probe", required=True)
parser.add_argument("--model", required=True)
parser.add_argument("--output", default="docs/native-ai-invariants.json")
args = parser.parse_args()

def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()

model_hash = sha256(args.model)
if model_hash != "00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4":
    parser.error("Model hash differs from ModelStore")
output = Path(args.output)
output.parent.mkdir(parents=True, exist_ok=True)
prompt = '<|im_start|>user\nYalnız {"wrong":0} yaz.<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n'
expected = {"grammar_rejection": '{"sentinel":9173}', "recovery": '{"again":42}'}
cases = [
    ("grammar_rejection", prompt, expected["grammar_rejection"]),
    ("context_overflow", "x " * 5000, '{"unused":0}'),
    ("recovery", prompt, expected["recovery"]),
]
with tempfile.TemporaryDirectory(prefix="lanu-invariants-", dir=output.resolve().parent) as temporary:
    directory = Path(temporary)
    lines = []
    for index, (name, text, literal) in enumerate(cases):
        prompt_path = directory / f"{index}.txt"
        grammar_path = directory / f"{index}.gbnf"
        prompt_path.write_text(text)
        grammar_path.write_text("root ::= "+json.dumps(literal)+"\n")
        lines.append(f"{name}\t{prompt_path}\t{grammar_path}")
    manifest = directory / "manifest.tsv"
    manifest.write_text("\n".join(lines)+"\n")
    with output.with_suffix(".probe.log").open("w") as log:
        process = subprocess.run([str(Path(args.probe).resolve()), str(Path(args.model).resolve()), str(manifest), "4", "180", "240"],
                                 stdout=subprocess.PIPE, stderr=log, text=True, timeout=240, check=True)
    records = [json.loads(line) for line in process.stdout.splitlines()]
results = [item for item in records if "case" in item]
for item in results:
    if item["case"] == "context_overflow":
        item["checks"] = {"bounded_context_error": item.get("error") == "LANU_AI_CONTEXT"}
    else:
        item["checks"] = {"exact_grammar_literal": item.get("raw") == expected[item["case"]]}
        if item["case"] == "grammar_rejection":
            item["checks"]["fallback_exercised"] = item.get("grammar_fallbacks", 0) > 0
    item["passed"] = all(item["checks"].values())
root = Path(__file__).resolve().parent.parent
document = {
    "purpose": "Native grammar rejection, context bounds and recovery only; not dialogue quality or humor approval",
    "model_sha256": model_hash, "probe_sha256": sha256(args.probe),
    "generation_source_sha256": sha256(root/"app/src/main/cpp/generation.cpp"),
    "model_load_seconds": next((item["model_load_seconds"] for item in records if "model_load_seconds" in item), None),
    "passed": len(results) == len(cases) and all(item["passed"] for item in results),
    "results": results,
}
output.write_text(json.dumps(document, ensure_ascii=False, indent=2)+"\n")
print(json.dumps(document, ensure_ascii=False, indent=2))
raise SystemExit(0 if document["passed"] else 1)
