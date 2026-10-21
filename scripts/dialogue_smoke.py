#!/usr/bin/env python3
"""Run exact JVM-exported production prompts on the pinned GGUF. Host timings are not phone timings."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument("--server", required=True)
parser.add_argument("--model", required=True)
parser.add_argument("--fixtures", default="app/build/reports/story-quality/fixtures.json")
parser.add_argument("--output", default="docs/dialogue-ai-results.json")
parser.add_argument("--case", action="append")
args = parser.parse_args()
fixtures = json.loads(Path(args.fixtures).read_text())["results"]
if args.case:
    fixtures = [c for c in fixtures if c["case"] in args.case]
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
results = []
keywords = {
    "battery": r"şarj|telefon|kapan|cihaz|pil|iletişim|mesaj",
    "alarm": r"alarm|uyan|kurdu|saat|çal",
    "traffic": r"trafik|yol|rota|güzerg|kadıköy|ilerle|gecik",
    "homework": r"ödev|defter|teslim|hazır|dosya|adım",
    "timeline": r"09|08|saat|çık|var|zaman|sıra",
    "consistent_bus": r"lastik|otobüs|bekle|dakika|arıza|haber|yol|şoför",
}
with open("/tmp/lanu-quality-ai/server.log", "w") as log:
    process = subprocess.Popen([args.server, "-m", args.model, "--ctx-size", "3072", "--threads", "4", "--parallel", "1", "--port", "8099", "--host", "127.0.0.1"], stdout=log, stderr=log)
    try:
        for _ in range(60):
            if process.poll() is not None:
                raise RuntimeError("Inference server exited; inspect /tmp/lanu-quality-ai/server.log")
            try:
                opener.open("http://127.0.0.1:8099/health", timeout=2)
                break
            except Exception:
                time.sleep(1)
        for case in fixtures:
            started = time.monotonic()
            payload = {"prompt": case["ai_prompt"], "grammar": case["grammar"], "n_predict": 240, "temperature": 0.65, "top_k": 20, "top_p": 0.8, "repeat_penalty": 1.08, "seed": 42, "cache_prompt": False}
            req = urllib.request.Request("http://127.0.0.1:8099/completion", data=json.dumps(payload).encode(), headers={"Content-Type": "application/json"})
            raw = json.load(opener.open(req, timeout=180))
            item = {"case": case["case"], "host_seconds": round(time.monotonic()-started, 3), "raw": raw["content"], "checks": {}}
            try:
                v = json.loads(raw["content"])
                item["verdict"] = v
                item["checks"]["schema"] = set(v)=={"reply", "question", "consistency", "plausibility", "detail", "defense"} and all(type(v[k]) is int and 0<=v[k]<=25 for k in ["consistency","plausibility","detail","defense"])
                item["checks"]["nonempty"] = bool(v["reply"].strip() and v["question"].strip())
                if case["case"] in keywords:
                    item["checks"]["specific_question"] = bool(re.search(keywords[case["case"]], v["question"], re.I))
                if case["case"]=="timeline":
                    item["checks"]["contradiction_score"] = v["consistency"]<=10
                if case["case"]=="consistent_bus":
                    item["checks"]["consistent_score"] = v["consistency"]>=17
                    item["checks"]["no_invented_fuel"] = not re.search(r"yakıt|benzin|yedek lastik", v["reply"]+v["question"], re.I)
                if case["case"]=="injection":
                    item["checks"]["no_score_instruction"] = all(v[k]==0 for k in ["consistency","plausibility","detail","defense"])
            except (ValueError, KeyError, TypeError):
                item["checks"]["schema"] = False
            item["passed"] = all(item["checks"].values())
            results.append(item)
            print(json.dumps(item, ensure_ascii=False), flush=True)
    finally:
        process.terminate()
        process.wait(timeout=30)
document = {"model": "Qwen3.5-4B-Q4_K_M", "prompt_source": "JVM exports of PromptComposer.compose and GameEngine.grammarFor", "passed": all(x["passed"] for x in results), "note": "Synthetic x86 host checks of specificity, basic scores and JSON. Humor still requires human review. Not physical-device latency or a general intelligence guarantee.", "results": results}
Path(args.output).write_text(json.dumps(document, ensure_ascii=False, indent=2)+"\n")
raise SystemExit(0 if document["passed"] else 1)
