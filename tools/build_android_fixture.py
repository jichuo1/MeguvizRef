"""Compile a public-loader fixture to DEX using the configured SDK's D8 CLI."""
import os
from pathlib import Path
import re
import subprocess
from build_gate import ROOT, save, sha

sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
if not sdk:
    match = re.search(r"^sdk.dir=(.+)$", (ROOT / "local.properties").read_text(), re.M)
    if match: sdk = match[1].replace("\\:", ":").replace("\\\\", "\\")
if not sdk: raise ValueError("SDK required for DEX fixture")
sdk = Path(sdk)
platforms = [path for path in (sdk / "platforms").glob("android-37*") if (path / "android.jar").exists()]
if not platforms: raise ValueError("Android37 platform missing")
platform = sorted(platforms)[0] / "android.jar"
source = ROOT / "samples/android-consumer/fixture-src/fixture/SameName.java"
classes = ROOT / "samples/android-consumer/build/dex-fixture-classes"
output = ROOT / "samples/android-consumer/build/generated/dex-assets"
classes.mkdir(parents=True, exist_ok=True)
output.mkdir(parents=True, exist_ok=True)
env = {k: v for k, v in os.environ.items() if k not in ("JDK_JAVA_OPTIONS", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS")}
commands = [["javac", "--release", "17", "-d", str(classes), str(source)],
    ["java", "-cp", str(sdk / "build-tools/37.0.0/lib/d8.jar"), "com.android.tools.r8.D8", "--min-api", "27", "--lib", str(platform), "--output", str(output), str(classes / "fixture/SameName.class")]]
for command in commands: subprocess.run(command, env=env, check=True)
save("verification/device-gate/dex-fixture.json", {"status": "compiled", "source_sha256": sha(source), "dex_sha256": sha(output / "classes.dex"), "commands": commands, "runtime": "not_run_until_device"})
