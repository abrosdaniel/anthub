"""Package and publish the tested AntHub commit using the runner's GitHub CLI."""
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import zipfile
from pathlib import Path


def run(*args):
    return subprocess.check_output(args, text=True).strip()


def version(root):
    values = re.findall(r"^anthubVersion=(.+)$", (root / "gradle.properties").read_text(), re.M)
    if len(values) != 1 or not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?", values[0]):
        raise ValueError("anthubVersion must be a release version, for example 1.0.0")
    return values[0]


def package(root, artifact, output):
    number = version(root)
    name = f"anthub-{number}-mc1.21.1-neoforge.jar"
    jars = list(artifact.rglob(name))
    if len(jars) != 1:
        raise ValueError(f"Expected exactly one tested {name}, found {len(jars)}")
    with zipfile.ZipFile(jars[0]) as jar:
        if "anthub/game.jar" not in jar.namelist():
            raise ValueError("Expected the complete AntHub bundle")
    output.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(jars[0], output / name)
    tracked = subprocess.check_output(["git", "-C", str(root), "ls-files", "-z", "--", "template/"]).decode().split("\0")
    entries = [Path(p) for p in tracked if p]
    required = {Path("template/anthub.json"), Path("template/.github/workflows/release.yml"), Path("template/tooling/anthub.py")}
    if not required.issubset(entries):
        raise ValueError("The complete template must be committed before release")
    with zipfile.ZipFile(output / "template.zip", "w", zipfile.ZIP_DEFLATED) as archive:
        for relative in sorted(entries):
            source = root / relative
            if source.is_symlink():
                raise ValueError(f"Template symlinks are not supported: {relative}")
            entry = zipfile.ZipInfo(relative.relative_to("template").as_posix(), (1980, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o100644 << 16
            archive.writestr(entry, source.read_bytes())
    repository = os.environ.get("GITHUB_REPOSITORY", "abrosdaniel/anthub")
    neo = re.search(r"^neoVersion=(.+)$", (root / "gradle.properties").read_text(), re.M)
    if neo is None:
        raise ValueError("Missing neoVersion")
    descriptor = dict(schemaVersion=1, version=number, artifacts=[dict(
        minecraft="1.21.1", neoForge=neo[1], java=21,
        url=f"https://github.com/{repository}/releases/download/v{number}/{name}",
        sha256=hashlib.sha256((output / name).read_bytes()).hexdigest(),
        size=(output / name).stat().st_size, helperProtocolVersion=1)])
    core = output / "core.json"
    core.write_text(json.dumps(descriptor, indent=2) + "\n")
    files = [output / name, output / "template.zip", core]
    sums = output / "SHA256SUMS.txt"
    sums.write_text("".join(hashlib.sha256(p.read_bytes()).hexdigest() + "  " + p.name + "\n" for p in files))
    return files + [sums]


def publish(root, artifact, output):
    number = version(root)
    tag = "v" + number
    repository = os.environ["GITHUB_REPOSITORY"]
    commit = os.environ["GITHUB_SHA"]
    if run("git", "rev-parse", "HEAD") != commit:
        raise ValueError("Checkout does not match the tested commit")
    pages = json.loads(run("gh", "api", f"repos/{repository}/releases", "--paginate", "--slurp"))
    existing = next((r for page in pages for r in page if r["tag_name"] == tag), None)
    if existing and not existing["draft"]:
        print(f"{tag} is already published; nothing to do")
        return
    refs = run("git", "ls-remote", "origin", "refs/tags/" + tag)
    if refs:
        run("git", "fetch", "origin", "refs/tags/" + tag)
        if run("git", "rev-parse", "FETCH_HEAD^{commit}") != commit:
            raise ValueError("Existing release tag belongs to another commit; increase anthubVersion")
    if existing and existing["target_commitish"] != commit:
        raise ValueError("Draft belongs to another commit; retry its original run or use a new version")
    notes = root / "anthub/RELEASE_NOTES.md"
    if not notes.is_file() or not notes.read_text(encoding="utf-8").strip():
        raise ValueError("Fill anthub/RELEASE_NOTES.md before publishing")
    files = package(root, artifact, output)
    if not existing:
        args = ["gh", "release", "create", tag, "--repo", repository, "--target", commit,
                "--title", "AntHub " + number, "--draft", "--notes-file", str(notes)]
        if "-" in number:
            args.append("--prerelease")
        run(*args)
    run("gh", "release", "upload", tag, *map(str, files), "--repo", repository, "--clobber")
    # A retry can resume a draft, but published releases are never overwritten.
    import tempfile
    with tempfile.TemporaryDirectory() as temp:
        for path in files:
            run("gh", "release", "download", tag, "--repo", repository, "--pattern", path.name, "--dir", temp)
            if (Path(temp) / path.name).read_bytes() != path.read_bytes():
                raise ValueError("Uploaded asset differs: " + path.name)
    run("gh", "release", "edit", tag, "--repo", repository, "--draft=false")
    print(f"Published {tag}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifact", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=Path("dist"))
    parser.add_argument("--publish", action="store_true")
    args = parser.parse_args()
    root = Path.cwd()
    if args.publish:
        publish(root, args.artifact, args.output)
    else:
        package(root, args.artifact, args.output)


if __name__ == "__main__":
    main()
