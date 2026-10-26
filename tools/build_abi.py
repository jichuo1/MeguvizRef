"""Freeze once, then run old compiled Java/Kotlin clients against newly built jars."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
from build_gate import ROOT, save, sha, published_artifact


def main():
    p = argparse.ArgumentParser()
    p.add_argument("command", choices=("freeze", "verify"))
    p.add_argument("--version", required=True)
    args = p.parse_args()
    target = ROOT / "verification/compatibility-clients"
    if args.command == "freeze":
        if (target / "manifest.json").exists(): raise ValueError("Existing frozen clients must be reviewed, not replaced")
        target.mkdir(parents=True, exist_ok=True)
        subprocess.run(["jar", "--create", "--file", str(target / "java-client.jar"), "-C", str(ROOT / "samples/jvm-consumer/build/classes/java/main"), "."], check=True)
        kotlin = list((ROOT / "samples/kotlin-consumer/build/libs").glob("*.jar"))
        if len(kotlin) != 1: raise ValueError("Exactly one Kotlin client jar required")
        shutil.copy2(kotlin[0], target / "kotlin-client.jar")
        for module in ("core", "diagnostics"):
            output = ROOT / f"verification/api-baseline/ref-{module}.txt"
            output.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / f"build/api/ref-{module}.txt", output)
        save("verification/compatibility-clients/manifest.json", {"version": args.version, "java_client_sha256": sha(target / "java-client.jar"), "kotlin_client_sha256": sha(target / "kotlin-client.jar"), "api_major": 0, "spi_major": 1, "persistent_schema_major": 1, "generated_abi": "not_applicable_no_generator"})
    manifest = json.loads((target / "manifest.json").read_text())
    for kind in ("java", "kotlin"):
        if sha(target / f"{kind}-client.jar") != manifest[f"{kind}_client_sha256"]: raise ValueError("Frozen client modified")
    core = published_artifact("core", args.version)
    extra = [x for x in (ROOT / "samples/kotlin-consumer/build/runtime-classpath.txt").read_text().split(os.pathsep) if "meguviz-ref-core" not in x]
    env = {k: v for k, v in os.environ.items() if k not in ("JDK_JAVA_OPTIONS", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS")}
    directory = ROOT / "build/abi-run"
    directory.mkdir(parents=True, exist_ok=True)
    logs = []
    for kind, main_class in (("java", "example.Consumer"), ("kotlin", "example.KotlinConsumerKt")):
        cp = os.pathsep.join([str(target / f"{kind}-client.jar"), str(core), *extra])
        command = ["java", "-cp", cp, main_class]
        if kind == "java": command.append(str(directory / "old-java.json"))
        run = subprocess.run(command, cwd=directory, env=env, text=True, capture_output=True)
        logs.append({"client": kind, "exit_code": run.returncode, "stdout": run.stdout, "stderr": run.stderr})
        if run.returncode: raise ValueError(f"Old client failed: {run.stdout} {run.stderr}")
    save("verification/compatibility-clients/receipt.json", {"status": "passed", "tests": 5, "artifact_sha256": sha(core), "clients": manifest, "runs": logs})
    print("OLD COMPILED CLIENTS PASSED tests=5")


if __name__ == "__main__": main()
