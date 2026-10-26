"""Fail-closed host/release checks; receipts bind current inputs and local artifacts."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import urllib.parse
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def source_digest():
    paths = []
    for folder in ("ref-core", "ref-diagnostics", "ref-android", "samples", "tools", "benchmarks", "gradle", "test-fixtures"):
        for path in (ROOT / folder).rglob("*"):
            if path.is_file() and not any(p in ("build", ".gradle", ".kotlin", "__pycache__") for p in path.relative_to(ROOT).parts):
                if path.suffix in (".java", ".kt", ".kts", ".py", ".toml", ".properties", ".pro", ".xml", ".jar", ".lockfile"):
                    paths.append(path)
    paths += [ROOT / name for name in ("build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat")]
    paths += list(ROOT.glob("*.lockfile"))
    paths += list((ROOT / "verification/api-baseline").glob("*.txt"))
    paths += list((ROOT / "verification").rglob("*.py"))
    for name in ("manifest.json", "java-client.jar", "kotlin-client.jar"):
        candidate = ROOT / "verification/compatibility-clients" / name
        if candidate.exists(): paths.append(candidate)
    digest = hashlib.sha256()
    for path in sorted(set(paths)):
        digest.update(path.relative_to(ROOT).as_posix().encode())
        digest.update(bytes.fromhex(sha(path)))
    return digest.hexdigest()


def save(path, value):
    path = ROOT / path
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def verify_inputs_unchanged():
    before = json.loads((ROOT / "build/verification-start.json").read_text(encoding="utf-8"))
    if before["source_sha256"] != source_digest():
        raise ValueError("Source inputs changed during verification; rerun after writers finish")


def checked_suite(path):
    root = ET.parse(path).getroot()
    suites = [root] if root.tag == "testsuite" else list(root.iter("testsuite"))
    count = sum(int(s.get("tests", "0")) for s in suites)
    failures = sum(int(s.get(k, "0")) for s in suites for k in ("failures", "errors", "skipped"))
    actual_cases = len(list(root.iter("testcase")))
    failures += len(list(root.iter("failure"))) + len(list(root.iter("error"))) + len(list(root.iter("skipped")))
    if count <= 0 or count != actual_cases or failures:
        raise ValueError(f"Nonzero passing tests required: {path}; tests={count}, cases={actual_cases}, bad={failures}")
    return count


def published_artifact(module, version, suffix=".jar"):
    folder = ROOT / f"build/local-maven/io/github/jichuo1/meguviz/meguviz-ref-{module}/{version}"
    resolved = version
    if version.endswith("-SNAPSHOT"):
        metadata = ET.parse(folder / "maven-metadata.xml")
        extension = suffix.rsplit(".", 1)[-1]
        classifier = "sources" if suffix == "-sources.jar" else ""
        matches = [entry.findtext("value") for entry in metadata.findall(".//snapshotVersion")
                   if entry.findtext("extension") == extension and entry.findtext("classifier", "") == classifier]
        if len(matches) != 1: raise ValueError(f"Ambiguous snapshot metadata: {module} {suffix}")
        resolved = matches[0]
    return folder / f"meguviz-ref-{module}-{resolved}{suffix}"


def artifacts(version):
    base = ROOT / "build/local-maven/io/github/jichuo1/meguviz"
    result = {}
    for module in ("core", "diagnostics"):
        folder = base / f"meguviz-ref-{module}" / version
        for suffix in (".jar", "-sources.jar", ".pom", ".module"):
            path = published_artifact(module, version, suffix)
            if not path.is_file():
                raise ValueError(f"Missing current artifact {path}")
            result[path.relative_to(ROOT).as_posix()] = sha(path)
        pom = ET.parse(published_artifact(module, version, ".pom"))
        for dep in pom.findall(".//{*}dependency"):
            group = dep.findtext("{*}groupId", "")
            artifact = dep.findtext("{*}artifactId", "")
            if (group, artifact) != ("io.github.jichuo1.meguviz", "meguviz-ref-core") or module == "core":
                raise ValueError(f"Unexpected production dependency {group}:{artifact}")
        jar = published_artifact(module, version)
        with zipfile.ZipFile(jar) as archive:
            classes = [n for n in archive.namelist() if n.endswith(".class")]
            if not classes:
                raise ValueError(f"Empty library {jar}")
            for name in classes:
                data = archive.read(name)
                if int.from_bytes(data[6:8], "big") > 61:
                    raise ValueError(f"Bytecode above Java17: {name}")
                for forbidden in (b"android/content/Context", b"com/highcapable/kavaref", b"org/lsposed", b"io/github/libxposed"):
                    if forbidden in data:
                        raise ValueError(f"Forbidden core/diagnostics reference: {name}")
    return result


def api_dump(version, update_baseline=False):
    for module in ("core", "diagnostics"):
        jar = ROOT / f"ref-{module}/build/libs/ref-{module}-{version}.jar"
        with zipfile.ZipFile(jar) as archive:
            classes = sorted(n[:-6].replace("/", ".") for n in archive.namelist() if n.endswith(".class"))
        raw = subprocess.run(["javap", "-classpath", str(jar), "-public", *classes], capture_output=True, text=True, check=True).stdout
        # Include public nested types and omit non-public implementation/anonymous classes.
        blocks = raw.split("Compiled from ")
        output = "".join("Compiled from " + block for block in blocks if any(line.startswith("public ") for line in block.splitlines()))
        target = ROOT / f"build/api/ref-{module}.txt"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(output, encoding="utf-8")
        baseline = ROOT / f"verification/api-baseline/ref-{module}.txt"
        if update_baseline:
            baseline.parent.mkdir(parents=True, exist_ok=True)
            baseline.write_text(output, encoding="utf-8")
        if baseline.exists() and baseline.read_text(encoding="utf-8") != output:
            raise ValueError(f"API differs from frozen baseline: {module}; explicit review required")


def host(version):
    verify_inputs_unchanged()
    reports = [ROOT / f"ref-{m}/build/test-results/contracts/TEST-{m}.xml" for m in ("core", "diagnostics")]
    counts = {p.relative_to(ROOT).as_posix(): checked_suite(p) for p in reports}
    consumer_path = ROOT / "samples/jvm-consumer/build/consumer.json"
    consumer = json.loads(consumer_path.read_text(encoding="utf-8"))
    if consumer.get("tests", 0) <= 0 or consumer.get("java") != 17 or consumer.get("status") != "passed":
        raise ValueError("Independent Java17 consumer did not pass")
    kotlin_path = ROOT / "samples/kotlin-consumer/build/kotlin-consumer.json"
    kotlin = json.loads(kotlin_path.read_text(encoding="utf-8"))
    if kotlin.get("tests") != 2 or kotlin.get("java") != 17 or kotlin.get("status") != "passed":
        raise ValueError("Independent Kotlin consumer did not pass on Java17")
    origin = Path(urllib.parse.unquote(urllib.parse.urlparse(consumer["origin"]).path).lstrip("/") if os.name == "nt" else urllib.parse.unquote(urllib.parse.urlparse(consumer["origin"]).path))
    expected = published_artifact("core", version)
    if not origin.is_file() or sha(origin) != sha(expected):
        raise ValueError("Consumer resolved a different or stale Maven artifact")
    record = {"status": "passed", "scope": "Java17+ JVM", "source_sha256": source_digest(), "version": version,
              "test_counts": counts, "consumer_tests": consumer["tests"], "kotlin_consumer_tests": kotlin["tests"], "artifacts": artifacts(version),
              "reports": {p.relative_to(ROOT).as_posix(): sha(p) for p in reports}, "consumer_sha256": sha(consumer_path)}
    record["api_baselines"] = {p.relative_to(ROOT).as_posix(): sha(p) for p in (ROOT / "verification/api-baseline").glob("*.txt")}
    save("verification/host-gate/receipt.json", record)
    print(f"HOST PASSED contract/consumer tests={sum(counts.values()) + consumer['tests'] + kotlin['tests']} source={record['source_sha256']}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("begin", "api", "host"))
    parser.add_argument("--version", required=True)
    args = parser.parse_args()
    {"begin": lambda: save("build/verification-start.json", {"source_sha256": source_digest()}),
     "api": lambda: api_dump(args.version), "host": lambda: host(args.version)}[args.command]()
