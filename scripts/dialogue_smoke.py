#!/usr/bin/env python3
"""Exercise JVM-exported production prompts through the exact JNI generation core."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile

parser = argparse.ArgumentParser()
parser.add_argument("--probe", required=True, help="CMake's lanu-ai-probe target, sharing generation.cpp with JNI")
parser.add_argument("--model", required=True)
parser.add_argument("--fixtures", default="app/build/reports/ai-quality/fixtures.json")
parser.add_argument("--output", default="docs/dialogue-ai-results.json")
parser.add_argument("--case", action="append")
parser.add_argument("--threads", type=int, default=4)
parser.add_argument("--timeout", type=int, default=180, help="Host diagnostic budget; Android remains 30 seconds including load")
args = parser.parse_args()
fixtures = json.loads(Path(args.fixtures).read_text())["results"]
if args.case:
    fixtures = [case for case in fixtures if case["case"] in args.case]
if not fixtures:
    parser.error("No matching fixtures")

def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()

model_hash = sha256(args.model)
expected_hash = "00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4"
if model_hash != expected_hash:
    parser.error("Model hash differs from ModelStore's pinned GGUF")
root = Path(__file__).resolve().parent.parent
sources = ["app/src/main/cpp/generation.h", "app/src/main/cpp/generation.cpp", "app/src/main/assets/system_prompt.txt",
           "app/src/main/java/com/lanu/yalan/ai/PromptComposer.kt", "app/src/main/java/com/lanu/yalan/ai/GameEngine.kt"]
source_hashes = {path: sha256(root/path) for path in sources}
probe_hash = sha256(args.probe)
keywords = {
    "battery": r"şarj|telefon|kapan|cihaz|pil|iletişim|mesaj",
    "alarm": r"alarm|uyan|kurdu|saat|çal",
    "traffic": r"trafik|yol|rota|güzerg|kadıköy|ilerle|gecik",
    "homework": r"ödev|defter|teslim|hazır|dosya|adım",
    "timeline": r"09|08|saat|çık|var|zaman|sıra",
    "consistent_bus": r"lastik|otobüs|bekle|dakika|arıza|haber|yol|şoför",
    "unseen_spill": r"kahve|dök|ıslan|leke|sıvı|masa",
}
scores = ["consistency", "plausibility", "detail", "defense"]
results = []
output_path = Path(args.output)
output_path.parent.mkdir(parents=True, exist_ok=True)
log_path = output_path.with_suffix(".probe.log")
load_seconds = None
document = {
    "model": "Qwen3.5-4B-Q4_K_M", "model_sha256": model_hash,
    "prompt_source": "JVM exports of PromptComposer.compose and GameEngine.grammarFor",
    "inference_source": "generation.cpp shared by Android JNI and lanu-ai-probe",
    "probe_sha256": probe_hash,
    "sampler": {"order": ["top_k", "top_p", "temperature", "dist"],
                "grammar_strategy": "Selected-token validation, full-vocabulary grammar mask on rejection (pinned llama.cpp common/sampling.cpp strategy)",
                "top_k": 20, "repeat_penalty": 1.0, "top_p": 0.8,
                "temperature": 0.5, "seed": 42, "min_p": None, "penalize_prompt": False,
                "acceptance": "apply does not accept; accept valid token once in grammar and once in sampling chain"},
    "context_tokens": 3072, "max_generated_tokens": 240, "threads": args.threads,
    "host_timeout_seconds": args.timeout, "android_timeout_seconds_including_load": 30,
    "model_load_seconds": None, "source_sha256": source_hashes,
    "fixtures_sha256": sha256(args.fixtures),
    "passed": False, "complete": False, "expected_cases": len(fixtures),
    "expected_case_names": [case["case"] for case in fixtures], "quality_approved": False,
    "note": "Synthetic x86 host checks, with untouched model text/scores. Question grounding and Turkish humor still require manual review. Host times exclude model loading and are not Android timings.",
    "results": results,
}
def checkpoint(complete=False):
    document["model_load_seconds"] = load_seconds
    document["complete"] = complete
    document["completed_cases"] = len(results)
    document["passed"] = complete and len(results) == len(fixtures) and all(item["passed"] for item in results)
    partial = output_path.with_suffix(output_path.suffix+".tmp")
    partial.write_text(json.dumps(document, ensure_ascii=False, indent=2)+"\n")
    partial.replace(output_path)

with tempfile.TemporaryDirectory(prefix="lanu-probe-", dir=output_path.resolve().parent) as temporary:
    directory = Path(temporary)
    manifest = []
    for index, case in enumerate(fixtures):
        if not re.fullmatch(r"[a-zA-Z0-9_-]+", case["case"]):
            parser.error("Invalid fixture name")
        prompt = directory / f"{index}.prompt.txt"
        grammar = directory / f"{index}.grammar.gbnf"
        prompt.write_text(case["ai_prompt"])
        grammar.write_text(case["grammar"])
        manifest.append(f"{case['case']}\t{prompt}\t{grammar}")
    manifest_path = directory / "manifest.tsv"
    manifest_path.write_text("\n".join(manifest)+"\n")
    by_name = {case["case"]: case for case in fixtures}
    with log_path.open("w") as log:
        process = subprocess.Popen([str(Path(args.probe).resolve()), str(Path(args.model).resolve()), str(manifest_path),
                                    str(args.threads), str(args.timeout), "240"], stdout=subprocess.PIPE, stderr=log, text=True)
        try:
            for line in process.stdout:
                item = json.loads(line)
                if "model_load_seconds" in item:
                    load_seconds = item["model_load_seconds"]
                    continue
                case = by_name[item["case"]]
                item["checks"] = {}
                try:
                    v = json.loads(item["raw"])
                    item["verdict"] = v
                    item["checks"]["schema"] = (set(v) == {"reply", "question", *scores}
                        and all(type(v[key]) is int and 0 <= v[key] <= 25 for key in scores)
                        and all(isinstance(v[key], str) for key in ("reply", "question")))
                    final = case.get("final", False)
                    item["checks"]["nonempty"] = bool(v["reply"].strip() and (final or v["question"].strip()))
                    item["checks"]["reply_has_no_question"] = "?" not in v["reply"]
                    item["checks"]["no_control_tokens"] = not re.search(r"<\||\|>|<think>|</think>", v["reply"]+v["question"])
                    if final:
                        item["checks"]["final_has_no_sixth_question"] = v["question"] == ""
                    if case["case"] in keywords:
                        item["checks"]["specific_question"] = bool(re.search(keywords[case["case"]], v["question"], re.I))
                    if case["case"] == "timeline":
                        item["checks"]["contradiction_score"] = v["consistency"] <= 10
                    if case["case"] in ("consistent_bus", "repaired_clock", "repaired_phone"):
                        item["checks"]["consistent_score"] = v["consistency"] >= 17
                    if case["case"] == "consistent_bus":
                        item["checks"]["no_invented_fuel"] = not re.search(r"yakıt|benzin|yedek lastik", v["reply"]+v["question"], re.I)
                    if case["case"] == "repaired_clock":
                        item["checks"]["no_retired_time_machine_accusation"] = "zaman makinesi" not in v["reply"].lower()
                    if case["case"] == "injection":
                        item["checks"]["no_score_instruction"] = all(v[key] == 0 for key in scores)
                except (ValueError, KeyError, TypeError):
                    item["checks"]["schema"] = False
                item["passed"] = all(item["checks"].values())
                results.append(item)
                checkpoint()
                print(json.dumps(item, ensure_ascii=False), flush=True)
            if process.wait(timeout=30) != 0:
                raise RuntimeError(f"Native quality probe failed; inspect {log_path}")
        finally:
            if process.poll() is None:
                process.terminate()
                process.wait(timeout=30)
if len(results) != len(fixtures):
    raise RuntimeError("Native quality probe did not return every fixture")
checkpoint(complete=True)
raise SystemExit(0 if document["passed"] else 1)
