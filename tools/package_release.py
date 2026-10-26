"""Validate and package the three local Maven publications from the tested build."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def package(version):
    if version != (ROOT / "gradle.properties").read_text().split("meguviz.version=", 1)[1].splitlines()[0]:
        raise ValueError("Release version differs from source")
    output = ROOT / "build/release"
    output.mkdir(parents=True, exist_ok=True)
    repository = ROOT / "build/local-maven"
    selected = []
    publications = {}
    for module in ("core", "diagnostics", "android"):
        artifact = f"meguviz-ref-{module}"
        base = repository / "io/github/jichuo1/meguviz" / artifact / version
        extension = "aar" if module == "android" else "jar"
        files = [base / f"{artifact}-{version}{suffix}" for suffix in (f".{extension}", "-sources.jar", ".pom", ".module")]
        if not all(p.is_file() for p in files):
            raise ValueError(f"Missing publication: {artifact}")
        pom = ET.parse(files[2])
        expected = [] if module == "core" else [("io.github.jichuo1.meguviz", "meguviz-ref-core", version)]
        actual = [(d.findtext("{*}groupId"), d.findtext("{*}artifactId"), d.findtext("{*}version")) for d in pom.findall(".//{*}dependency")]
        if actual != expected or not pom.findtext(".//{*}license/{*}name"):
            raise ValueError(f"Invalid POM metadata/dependencies: {artifact}: {actual}")
        with zipfile.ZipFile(files[0]) as archive:
            names = archive.namelist()
            if not ("classes.jar" in names if module == "android" else any(n.endswith(".class") for n in names)):
                raise ValueError(f"Empty publication: {artifact}")
        selected.extend(files)
        publications[artifact] = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    manifest = {"version": version, "commit": revision, "publications": publications,
                "runtime_scope": "JVM Java17+, Android API27+; no assertion of every host feature or device",
                "repository": "https://github.com/jichuo1/MeguvizRef"}
    (output / "release-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    archive_path = output / f"meguviz-ref-{version}-maven.zip"
    with zipfile.ZipFile(archive_path, "w", zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(selected):
            archive.write(path, path.relative_to(repository).as_posix())
    for path in selected:
        shutil.copy2(path, output / path.name)
    checksums = [f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}" for p in sorted(output.iterdir()) if p.is_file() and p.name != "SHA256SUMS"]
    (output / "SHA256SUMS").write_text("\n".join(checksums) + "\n")
    print(f"Packaged {len(selected)} Maven files for {version} at {revision}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", required=True)
    package(parser.parse_args().version)
