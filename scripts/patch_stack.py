#!/usr/bin/env python3
"""Explicit, local-only patch-stack inspection, reconstruction and export."""

import argparse
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys


class StackError(Exception):
    pass


MAX_PATCHES = 512
SHA = re.compile(r"[0-9a-f]{40}\Z")
RESERVED = {"CON", "PRN", "AUX", "NUL", "CLOCK$"} | {
    f"{prefix}{number}" for prefix in ("COM", "LPT") for number in range(1, 10)
}


def git(repo, *args, input=None):
    env = os.environ.copy()
    for key in ("GIT_DIR", "GIT_WORK_TREE", "GIT_INDEX_FILE", "GIT_COMMON_DIR",
                "GIT_OBJECT_DIRECTORY", "GIT_ALTERNATE_OBJECT_DIRECTORIES"):
        env.pop(key, None)
    env.update(GIT_TERMINAL_PROMPT="0", GIT_LFS_SKIP_SMUDGE="1",
               GIT_MERGE_AUTOEDIT="no", GIT_NO_LAZY_FETCH="1")
    env.setdefault("GIT_COMMITTER_NAME", "Patch Stack")
    env.setdefault("GIT_COMMITTER_EMAIL", "patch-stack@localhost")
    command = ["git", "-c", f"core.hooksPath={os.devnull}",
               "-c", "core.autocrlf=false", "-c", "commit.gpgsign=false",
               "-c", "core.fsmonitor=false", "-c", "protocol.allow=never",
               "-c", "protocol.file.allow=always", "-C", str(repo), *args]
    try:
        result = subprocess.run(command, input=input, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, env=env, timeout=300)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise StackError(f"Git invocation failed: {exc}") from exc
    if result.returncode:
        details = (result.stdout + result.stderr).decode("utf-8", "replace").strip()
        raise StackError(f"git {args[0]} failed: {details}")
    return result.stdout


def text(repo, *args):
    return git(repo, *args).decode("utf-8").strip()


def regular(path):
    try:
        mode = path.lstat().st_mode
    except OSError as exc:
        raise StackError(f"Missing or inaccessible file: {path}") from exc
    if not stat.S_ISREG(mode) or path.is_symlink():
        raise StackError(f"Expected regular non-symlink file: {path}")


def safe_filename(value):
    if (not isinstance(value, str)
            or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*\.patch", value)
            or ".." in value or len(value) > 160
            or value.split(".")[0].upper() in RESERVED):
        raise StackError(f"Unsafe patch filename: {value!r}")
    return value


def object_type(repo, oid, expected):
    if not isinstance(oid, str) or not SHA.fullmatch(oid):
        raise StackError(f"Invalid {expected} object ID: {oid!r}")
    if text(repo, "cat-file", "-t", oid) != expected:
        raise StackError(f"Expected {expected} object: {oid}")


def linear_commits(repo, baseline, source):
    git(repo, "merge-base", "--is-ancestor", baseline, source)
    commits = text(repo, "rev-list", "--reverse", f"--max-count={MAX_PATCHES + 1}",
                   f"{baseline}..{source}").splitlines()
    if len(commits) > MAX_PATCHES:
        raise StackError(f"Stack exceeds {MAX_PATCHES} patches")
    previous = baseline
    for commit in commits:
        parents = text(repo, "show", "-s", "--format=%P", commit).split()
        if parents != [previous]:
            raise StackError(f"Source is not a linear single-parent stack: {commit}")
        previous = commit
    if previous != source:
        raise StackError("Source does not end at the recorded stack head")
    return commits


def subject(repo, commit):
    value = text(repo, "show", "-s", "--format=%s", commit)
    if not value or any(ord(char) < 32 or ord(char) == 127 for char in value):
        raise StackError(f"Invalid commit subject: {commit}")
    return value


def inspect(repo, *, align_source=True):
    manifest_path = repo / "patch-stack.json"
    regular(manifest_path)
    if manifest_path.stat().st_size > 1024 * 1024:
        raise StackError("Manifest exceeds 1 MiB")
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (ValueError, UnicodeError) as exc:
        raise StackError(f"Invalid manifest JSON: {exc}") from exc
    if not isinstance(manifest, dict) or type(manifest.get("format")) is not int \
            or manifest["format"] != 1:
        raise StackError("Expected manifest format 1")
    if not isinstance(manifest.get("signal_version"), str) or not re.fullmatch(
            r"[0-9]+\.[0-9]+\.[0-9]+", manifest["signal_version"]):
        raise StackError("Invalid signal_version")
    branches = manifest.get("branches")
    if not isinstance(branches, dict) or set(branches) != {"baseline", "source", "patches"}:
        raise StackError("Expected baseline/source/patches branch names")
    for name in branches.values():
        if not isinstance(name, str) or not name or name.startswith("-"):
            raise StackError("Invalid branch name")
        git(repo, "check-ref-format", f"refs/heads/{name}")
    object_type(repo, manifest.get("baseline"), "commit")
    object_type(repo, manifest.get("tree"), "tree")
    source = manifest.get("source")
    if not isinstance(source, str) or not SHA.fullmatch(source):
        raise StackError("Invalid source object ID")
    entries = manifest.get("patches")
    if not isinstance(entries, list) or len(entries) > MAX_PATCHES:
        raise StackError(f"Expected at most {MAX_PATCHES} patches")
    patch_dir = repo / "patches"
    if (patch_dir.is_symlink() or not patch_dir.is_dir()
            or getattr(patch_dir, "is_junction", lambda: False)()):
        raise StackError("Expected non-symlink patches directory")
    files, subjects = set(), set()
    for entry in entries:
        if not isinstance(entry, dict) or set(entry) != {"file", "subject"}:
            raise StackError("Expected patch file/subject")
        filename = safe_filename(entry["file"])
        title = entry["subject"]
        if not isinstance(title, str) or not title or any(
                ord(char) < 32 or ord(char) == 127 for char in title):
            raise StackError("Invalid patch subject")
        if filename.casefold() in files or title in subjects:
            raise StackError("Duplicate patch filename or subject")
        files.add(filename.casefold())
        subjects.add(title)
        regular(patch_dir / filename)
    if {path.name.casefold() for path in patch_dir.iterdir()} != files:
        raise StackError("Unexpected files in patches directory")
    present = bool(text(repo, "rev-parse", "--disambiguate=" + source))
    if present:
        object_type(repo, source, "commit")
    if present and align_source:
        commits = linear_commits(repo, manifest["baseline"], source)
        if len(commits) != len(entries):
            raise StackError("Source commit count differs from patch count")
        if text(repo, "rev-parse", source + "^{tree}") != manifest["tree"]:
            raise StackError("Recorded source tree differs from manifest tree")
        if [subject(repo, commit) for commit in commits] != [
                entry["subject"] for entry in entries]:
            raise StackError("Source subjects differ from manifest order")
    return manifest


def new_destination(value):
    destination = Path(value)
    if not destination.is_absolute():
        raise StackError("Destination must be an absolute path")
    if os.path.lexists(destination):
        raise StackError(f"Destination already exists: {destination}")
    if not destination.parent.is_dir():
        raise StackError("Destination parent must already exist")
    return destination


def import_stack(repo, destination, propose=False):
    manifest = inspect(repo, align_source=not propose)
    destination = new_destination(destination)
    try:
        git(repo, "clone", "--shared", "--no-checkout", "--no-tags",
            "--no-hardlinks", "--", str(repo), str(destination))
        git(destination, "checkout", "--detach", manifest["baseline"])
        for entry in manifest["patches"]:
            git(destination, "am", "--no-gpg-sign", "--keep-cr", "--keep-non-patch",
                "--whitespace=nowarn", "--",
                str(repo / "patches" / entry["file"]))
        head = text(destination, "rev-parse", "HEAD")
        tree = text(destination, "rev-parse", "HEAD^{tree}")
        commits = linear_commits(destination, manifest["baseline"], head)
        if len(commits) != len(manifest["patches"]) or (
                tree != manifest["tree"] and not propose):
            raise StackError("Imported tree or commit count differs from manifest")
        if [subject(destination, commit) for commit in commits] != [
                entry["subject"] for entry in manifest["patches"]]:
            raise StackError("Imported subjects differ from manifest")
        return {"baseline": manifest["baseline"], "head": head, "tree": tree,
                "count": len(commits), "destination": str(destination),
                "status": "proposal" if propose else "verified",
                "tree_matches_manifest": tree == manifest["tree"]}
    except StackError as exc:
        raise StackError(f"Import aborted; output preserved at {destination}. {exc}") from exc


def resolve_source(repo, source_ref):
    if (not source_ref or source_ref.startswith("-")
            or any(ord(char) < 33 or ord(char) == 127 for char in source_ref)):
        raise StackError("Invalid source ref")
    # Accept a literal commit ID or a named ref, not arbitrary revision expressions.
    if not SHA.fullmatch(source_ref):
        git(repo, "check-ref-format", "--allow-onelevel", source_ref)
    return text(repo, "rev-parse", "--verify", "--end-of-options",
                source_ref + "^{commit}")


def check_stack(repo, destination, source_ref=None, baseline_ref=None):
    manifest = inspect(repo)
    source_ref = source_ref or "refs/remotes/origin/" + manifest["branches"]["source"]
    baseline_ref = baseline_ref or "refs/remotes/origin/" + manifest["branches"]["baseline"]
    source = resolve_source(repo, source_ref)
    baseline = resolve_source(repo, baseline_ref)
    if source != manifest["source"]:
        raise StackError("Source branch head differs from the recorded snapshot")
    if baseline != manifest["baseline"]:
        raise StackError("Baseline branch head differs from the recorded baseline")
    commits = linear_commits(repo, baseline, source)
    if len(commits) != len(manifest["patches"]):
        raise StackError("Source commit count differs from patch count")
    if text(repo, "rev-parse", source + "^{tree}") != manifest["tree"]:
        raise StackError("Source branch tree differs from the patch snapshot")
    result = import_stack(repo, destination)
    return dict(result, source=source, source_ref=source_ref,
                baseline_ref=baseline_ref, branch_heads_verified=True)


def export_stack(repo, source_ref, destination):
    manifest = inspect(repo, align_source=False)
    destination = new_destination(destination)
    if git(repo, "status", "--porcelain", "--untracked-files=all"):
        raise StackError("Export requires a clean repository; commit source changes first")
    source = resolve_source(repo, source_ref)
    commits = linear_commits(repo, manifest["baseline"], source)
    existing = {entry["subject"]: entry for entry in manifest["patches"]}
    used_files = {entry["file"].casefold() for entry in manifest["patches"]}
    entries, patches, seen = [], [], set()
    for commit in commits:
        title = subject(repo, commit)
        if title.startswith(("fixup!", "squash!", "amend!")):
            raise StackError(f"Normalize autosquash commit before export: {title}")
        if title in seen:
            raise StackError(f"Ambiguous duplicate source subject: {title}")
        seen.add(title)
        if title in existing:
            entry = dict(existing[title])
        else:
            slug = re.sub(r"[^a-z0-9]+", "-", title.lower()).strip("-")[:80].rstrip("-")
            slug = slug or "patch"
            if slug.upper() in RESERVED:
                slug = "patch-" + slug
            filename, suffix = slug + ".patch", 2
            while filename.casefold() in used_files:
                filename = f"{slug}-{suffix}.patch"
                suffix += 1
            entry = {"file": safe_filename(filename), "subject": title}
        used_files.add(entry["file"].casefold())
        entries.append(entry)
        patch = git(repo, "format-patch", "--stdout", "-1", "--zero-commit",
                    "--no-signature", "--no-stat", "--full-index", "--binary",
                    "--no-renames", commit, "--")
        if not patch:
            raise StackError(f"Empty commit cannot be represented as a patch: {commit}")
        patches.append(patch)
    output = dict(manifest, source=source,
                  tree=text(repo, "rev-parse", source + "^{tree}"), patches=entries)
    destination.mkdir()
    try:
        (destination / "patches").mkdir()
        (destination / "patch-stack.json").write_bytes(
            (json.dumps(output, indent=2, ensure_ascii=False) + "\n").encode("utf-8"))
        for entry, patch in zip(entries, patches):
            (destination / "patches" / entry["file"]).write_bytes(patch)
    except OSError as exc:
        raise StackError(f"Export aborted; output preserved at {destination}: {exc}") from exc
    return {"baseline": output["baseline"], "head": source, "tree": output["tree"],
            "count": len(entries), "destination": str(destination)}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parent.parent)
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("inspect", help="Validate metadata, not patch application")
    importer = subparsers.add_parser("import", help="Reconstruct into a new local shared clone")
    importer.add_argument("--destination", required=True)
    importer.add_argument("--propose", action="store_true",
                          help="Report an edited stack's actual tree, without strict tree approval")
    checker = subparsers.add_parser("check", help="Validate branch pins and reconstruct the patch stack")
    checker.add_argument("--destination", required=True)
    checker.add_argument("--source-ref")
    checker.add_argument("--baseline-ref")
    exporter = subparsers.add_parser("export", help="Export a committed linear source stack")
    exporter.add_argument("--source-ref", required=True)
    exporter.add_argument("--destination", required=True)
    args = parser.parse_args(argv)
    repo = args.repo.resolve()
    try:
        if args.command == "inspect":
            manifest = inspect(repo)
            result = {"baseline": manifest["baseline"], "source": manifest["source"],
                      "tree": manifest["tree"], "count": len(manifest["patches"]),
                      "application_verified": False}
        elif args.command == "import":
            result = import_stack(repo, args.destination, propose=args.propose)
        elif args.command == "check":
            result = check_stack(repo, args.destination, args.source_ref, args.baseline_ref)
        else:
            result = export_stack(repo, args.source_ref, args.destination)
        print(json.dumps(result, sort_keys=True))
        return 0
    except (StackError, OSError, UnicodeError) as exc:
        print(json.dumps({"error": str(exc)}), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
