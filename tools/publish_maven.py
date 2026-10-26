"""Append an immutable release to the public Maven branch using Actions credentials."""
import argparse
import hashlib
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def run(*args, cwd=ROOT, check=True):
    return subprocess.run(args, cwd=cwd, check=check, text=True, capture_output=True)

def publish(version):
    target = ROOT / ".publish-maven"
    if target.exists():
        raise ValueError("Refuse to reuse a Maven publish directory")
    target.mkdir()
    run("git", "init", "-b", "maven", cwd=target)
    run("git", "remote", "add", "origin", "https://github.com/jichuo1/MeguvizRef.git", cwd=target)
    exists = run("git", "ls-remote", "--exit-code", "origin", "refs/heads/maven", cwd=target, check=False)
    if exists.returncode not in (0, 2):
        raise ValueError("Could not inspect existing Maven branch")
    if exists.returncode == 0:
        run("git", "fetch", "--depth=1", "origin", "maven", cwd=target)
        run("git", "checkout", "-B", "maven", "FETCH_HEAD", cwd=target)
    with zipfile.ZipFile(ROOT / "build/release" / f"meguviz-ref-{version}-maven.zip") as archive:
        for item in archive.infolist():
            path = target / item.filename
            if not path.resolve().is_relative_to(target.resolve()) or item.is_dir():
                raise ValueError("Invalid release archive entry")
            content = archive.read(item)
            if path.exists() and path.read_bytes() != content:
                raise ValueError(f"Refuse to overwrite published version: {item.filename}")
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
            for algorithm in ("sha256", "sha512"):
                path.with_name(path.name + "." + algorithm).write_text(hashlib.new(algorithm, content).hexdigest() + "\n")
    (target / "README.md").write_text("# MeguvizRef Maven repository\n\nRepository URL: https://raw.githubusercontent.com/jichuo1/MeguvizRef/maven/\n\nGroup: io.github.jichuo1.meguviz\n\nReleases are immutable. Source and release notes: https://github.com/jichuo1/MeguvizRef\n")
    run("git", "config", "user.name", "github-actions[bot]", cwd=target)
    run("git", "config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com", cwd=target)
    run("git", "add", "--", "io", "README.md", cwd=target)
    if run("git", "diff", "--cached", "--quiet", cwd=target, check=False).returncode:
        run("git", "commit", "-m", f"chore(maven): publish {version}", cwd=target)
        run("git", "push", "origin", "maven", cwd=target)
    print(f"Public Maven repository contains immutable {version}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", required=True)
    publish(parser.parse_args().version)
