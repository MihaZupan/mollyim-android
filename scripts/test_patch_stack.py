import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import unittest
import uuid

import patch_stack


def remove_owned(path):
    def writable_retry(function, target, exception):
        os.chmod(target, 0o700)
        function(target)
    shutil.rmtree(path, onerror=writable_retry)


class PatchStackTests(unittest.TestCase):
    def setUp(self):
        self.owned = Path(__file__).resolve().parent / (".patch-stack-test-" + uuid.uuid4().hex)
        self.owned.mkdir()
        self.addCleanup(remove_owned, self.owned)
        self.repo = self.owned / "repo"
        self.repo.mkdir()
        self.run_git("init", "--quiet")
        self.run_git("config", "user.name", "Stack Test")
        self.run_git("config", "user.email", "stack-test@example.invalid")
        self.run_git("config", "core.autocrlf", "false")
        self.run_git("config", "core.filemode", "false" if os.name == "nt" else "true")
        (self.repo / "delete.txt").write_bytes(b"delete me\n")
        (self.repo / "program").write_bytes(b"#!/bin/sh\nexit 0\n")
        self.commit("Baseline")
        self.baseline = self.run_git("rev-parse", "HEAD")
        (self.repo / "data.bin").write_bytes(bytes(range(256)) * 4)
        (self.repo / "crlf.txt").write_bytes(b"first\r\nsecond\r\n")
        self.commit("[Feature] Add binary payload", body="A body.\n\nReviewed-by: Example <example@example.invalid>")
        first = self.run_git("rev-parse", "HEAD")
        (self.repo / "delete.txt").unlink()
        (self.repo / "crlf.txt").write_bytes(b"first\r\nchanged\r\n")
        self.run_git("add", "--", "crlf.txt")
        self.run_git("update-index", "--chmod=+x", "program")
        self.run_git("add", "-u", "--", "delete.txt")
        self.run_git("commit", "--quiet", "-m", "Remove obsolete file")
        self.source = self.run_git("rev-parse", "HEAD")
        self.tree = self.run_git("rev-parse", "HEAD^{tree}")
        entries = [
            {"id": "binary-feature", "file": "binary.patch", "subject": "[Feature] Add binary payload"},
            {"id": "cleanup-feature", "file": "cleanup.patch", "subject": "Remove obsolete file"},
        ]
        self.manifest = {
            "format": 1, "signal_version": "8.19.2", "baseline": self.baseline,
            "source": self.source, "tree": self.tree,
            "branches": {"baseline": "v8.19.2/signal-baseline",
                         "source": "v8.19.2/molly", "patches": "v8.19.2/molly-patches"},
            "patches": entries,
        }
        (self.repo / "patches").mkdir()
        for entry, commit in zip(entries, [first, self.source]):
            (self.repo / "patches" / entry["file"]).write_bytes(
                patch_stack.git(self.repo, "format-patch", "--stdout", "-1", "--zero-commit",
                                "--no-signature", "--no-stat", "--full-index", "--binary",
                                "--no-renames", commit, "--"))
        self.write_manifest()
        self.write_order()
        # A patch-management branch need not be the source being exported.
        self.run_git("checkout", "--quiet", "-b", "management", self.baseline)
        self.commit("Manage patch stack")

    def run_git(self, *args):
        return patch_stack.text(self.repo, *args)

    def commit(self, title, body=None):
        self.run_git("add", "--all")
        args = ["commit", "--quiet", "-m", title]
        if body:
            args += ["-m", body]
        self.run_git(*args)

    def write_manifest(self):
        (self.repo / "patch-stack.json").write_bytes(
            (json.dumps(self.manifest) + "\n").encode("utf-8"))

    def write_order(self):
        (self.repo / "patches" / ".patch-order").write_bytes(
            "".join(e["file"] + "\n" for e in self.manifest["patches"]).encode("utf-8"))

    def snapshot(self):
        return (self.run_git("show-ref"), self.run_git("status", "--porcelain"),
                self.run_git("rev-parse", "HEAD"), self.run_git("worktree", "list", "--porcelain"))

    def test_roundtrip_binary_deletion_mode_and_unchanged_original(self):
        before = self.snapshot()
        exported = self.owned / "export"
        result = patch_stack.export_stack(self.repo, self.source, exported)
        self.assertEqual(result["count"], 2)
        metadata = json.loads((exported / "patch-stack.json").read_text())
        self.assertEqual(metadata["patches"], self.manifest["patches"])
        self.assertEqual({p.name for p in exported.iterdir()}, {"patch-stack.json", "patches"})
        # Move the exported managed snapshot to a new repository's working files.
        carrier = self.owned / "carrier"
        patch_stack.git(self.repo, "clone", "--shared", "--no-checkout", "--", str(self.repo), str(carrier))
        patch_stack.git(carrier, "checkout", "--detach", self.baseline)
        shutil.copytree(exported / "patches", carrier / "patches")
        shutil.copyfile(exported / "patch-stack.json", carrier / "patch-stack.json")
        imported = self.owned / "import"
        actual = patch_stack.import_stack(carrier, imported)
        self.assertEqual(actual["tree"], self.tree)
        self.assertEqual(actual["count"], 2)
        self.assertFalse((imported / "delete.txt").exists())
        self.assertEqual((imported / "data.bin").read_bytes(), bytes(range(256)) * 4)
        self.assertEqual((imported / "crlf.txt").read_bytes(), b"first\r\nchanged\r\n")
        self.assertIn("100755", patch_stack.text(imported, "ls-tree", "HEAD", "program"))
        commits = patch_stack.linear_commits(imported, self.baseline, actual["head"])
        self.assertIn("Reviewed-by: Example", patch_stack.text(imported, "show", "-s", "--format=%B", commits[0]))
        self.assertEqual(patch_stack.text(imported, "show", "-s", "--format=%an <%ae>", commits[0]),
                         "Stack Test <stack-test@example.invalid>")
        self.assertEqual(before, self.snapshot())
        second = self.owned / "second-export"
        patch_stack.export_stack(self.repo, self.source, second)
        for path in exported.rglob("*"):
            if path.is_file():
                self.assertEqual(path.read_bytes(), (second / path.relative_to(exported)).read_bytes())

    def test_existing_destination_and_dirty_export_rejected(self):
        before = self.snapshot()
        for operation in (lambda: patch_stack.import_stack(self.repo, self.owned),
                          lambda: patch_stack.export_stack(self.repo, self.source, self.owned)):
            with self.assertRaisesRegex(patch_stack.StackError, "already exists"):
                operation()
        (self.repo / "uncommitted").write_text("dirty", encoding="utf-8")
        with self.assertRaisesRegex(patch_stack.StackError, "clean"):
            patch_stack.export_stack(self.repo, self.source, self.owned / "dirty-export")
        self.assertFalse((self.owned / "dirty-export").exists())
        (self.repo / "uncommitted").unlink()
        self.assertEqual(before, self.snapshot())

    def test_unsafe_duplicate_and_order_metadata(self):
        for filename in ("../escape.patch", "a\\b.patch", "a/b.patch", "NUL.patch",
                         "con.test.patch", "bad..patch", "bad\n.patch", "-option.patch"):
            with self.subTest(filename=filename):
                original = self.manifest["patches"][0]["file"]
                self.manifest["patches"][0]["file"] = filename
                self.write_manifest()
                with self.assertRaises(patch_stack.StackError):
                    patch_stack.inspect(self.repo)
                self.manifest["patches"][0]["file"] = original
        self.write_manifest()
        for field in ("id", "file", "subject"):
            with self.subTest(duplicate=field):
                original = self.manifest["patches"][1][field]
                self.manifest["patches"][1][field] = self.manifest["patches"][0][field]
                self.write_manifest()
                with self.assertRaisesRegex(patch_stack.StackError, "Duplicate"):
                    patch_stack.inspect(self.repo)
                self.manifest["patches"][1][field] = original
        self.write_manifest()
        for order in (b"cleanup.patch\nbinary.patch\n", b"binary.patch\n",
                      b"binary.patch\r\ncleanup.patch\r\n"):
            (self.repo / "patches" / ".patch-order").write_bytes(order)
            with self.assertRaisesRegex(patch_stack.StackError, "patch-order"):
                patch_stack.inspect(self.repo)
        self.write_order()
        (self.repo / "patches" / ".patch-order").unlink()
        with self.assertRaisesRegex(patch_stack.StackError, "Missing"):
            patch_stack.inspect(self.repo)

    def test_tampered_patch_preserves_failed_import_and_tree_rejected(self):
        patch = self.repo / "patches" / "cleanup.patch"
        patch.write_bytes(b"not an email patch\n")
        destination = self.owned / "failed-import"
        before = self.snapshot()
        with self.assertRaisesRegex(patch_stack.StackError, "preserved"):
            patch_stack.import_stack(self.repo, destination)
        self.assertTrue((destination / ".git").is_dir())
        self.assertEqual(before, self.snapshot())
        self.manifest["tree"] = self.run_git("rev-parse", self.baseline + "^{tree}")
        self.write_manifest()
        with self.assertRaisesRegex(patch_stack.StackError, "tree"):
            patch_stack.inspect(self.repo)

    def test_applied_wrong_tree_rejected_without_recorded_source(self):
        self.manifest["source"] = "1" * 40
        self.manifest["tree"] = self.run_git("rev-parse", self.baseline + "^{tree}")
        self.write_manifest()
        destination = self.owned / "wrong-tree"
        with self.assertRaisesRegex(patch_stack.StackError, "Imported tree"):
            patch_stack.import_stack(self.repo, destination)
        self.assertTrue(destination.is_dir())

    def test_explicit_proposal_reports_edited_tree_without_mutating_manifest(self):
        patch = self.repo / "patches" / "cleanup.patch"
        original = patch.read_bytes()
        self.assertIn(b"+changed\r\n", original)
        patch.write_bytes(original.replace(b"+changed\r\n", b"+proposal\r\n"))
        before = self.snapshot()
        manifest_before = (self.repo / "patch-stack.json").read_bytes()
        strict = self.owned / "strict-edited"
        with self.assertRaisesRegex(patch_stack.StackError, "Imported tree"):
            patch_stack.import_stack(self.repo, strict)
        proposal = patch_stack.import_stack(self.repo, self.owned / "proposal", propose=True)
        self.assertEqual(proposal["status"], "proposal")
        self.assertFalse(proposal["tree_matches_manifest"])
        self.assertNotEqual(proposal["tree"], self.tree)
        self.assertEqual(proposal["count"], 2)
        self.assertEqual(proposal["baseline"], self.baseline)
        self.assertEqual((self.repo / "patch-stack.json").read_bytes(), manifest_before)
        self.assertEqual(before, self.snapshot())
        with self.assertRaisesRegex(patch_stack.StackError, "already exists"):
            patch_stack.import_stack(self.repo, self.owned / "proposal", propose=True)

    def test_patch_first_addition_proposes_while_recorded_source_remains_pinned(self):
        edited_source = self.owned / "edited-source"
        patch_stack.import_stack(self.repo, edited_source)
        patch_stack.git(edited_source, "config", "user.name", "Stack Test")
        patch_stack.git(edited_source, "config", "user.email", "stack-test@example.invalid")
        (edited_source / "added.txt").write_bytes(b"new patch-first feature\n")
        patch_stack.git(edited_source, "add", "--", "added.txt")
        patch_stack.git(edited_source, "commit", "--quiet", "-m", "Add patch-first feature")
        expected_tree = patch_stack.text(edited_source, "rev-parse", "HEAD^{tree}")
        filename = "added-feature.patch"
        (self.repo / "patches" / filename).write_bytes(
            patch_stack.git(edited_source, "format-patch", "--stdout", "-1", "--zero-commit",
                            "--no-signature", "--no-stat", "--full-index", "--binary",
                            "--no-renames", "HEAD", "--"))
        self.manifest["patches"].append(
            {"id": "added-feature", "file": filename, "subject": "Add patch-first feature"})
        self.write_manifest()
        self.write_order()
        before = self.snapshot()
        paths = [self.repo / "patch-stack.json", *sorted((self.repo / "patches").iterdir())]
        canonical_before = {path: path.read_bytes() for path in paths}
        self.assertEqual(self.manifest["source"], self.source)
        self.assertEqual(self.manifest["tree"], self.tree)
        with self.assertRaisesRegex(patch_stack.StackError, "Source commit count"):
            patch_stack.inspect(self.repo)
        strict_destination = self.owned / "strict-added"
        with self.assertRaisesRegex(patch_stack.StackError, "Source commit count"):
            patch_stack.import_stack(self.repo, strict_destination)
        self.assertFalse(strict_destination.exists())
        destination = self.owned / "proposed-added"
        result = subprocess.run(
            [sys.executable, str(Path(patch_stack.__file__)), "--repo", str(self.repo),
             "import", "--propose", "--destination", str(destination)],
            capture_output=True, check=True)
        proposal = json.loads(result.stdout)
        self.assertEqual(proposal["status"], "proposal")
        self.assertEqual(proposal["baseline"], self.baseline)
        self.assertEqual(proposal["count"], 3)
        self.assertEqual(proposal["tree"], expected_tree)
        self.assertFalse(proposal["tree_matches_manifest"])
        self.assertEqual((destination / "added.txt").read_bytes(), b"new patch-first feature\n")
        self.assertEqual(canonical_before, {path: path.read_bytes() for path in paths})
        self.assertEqual(before, self.snapshot())

    def test_merge_fixup_duplicate_subject_and_option_injection_rejected(self):
        self.run_git("checkout", "--quiet", "-b", "fixup-source", self.source)
        self.run_git("commit", "--allow-empty", "--quiet", "-m", "fixup! Add binary payload")
        fixup = self.run_git("rev-parse", "HEAD")
        self.run_git("checkout", "--quiet", "-b", "duplicate-source", self.source)
        self.run_git("commit", "--allow-empty", "--quiet", "-m", "[Feature] Add binary payload")
        duplicate = self.run_git("rev-parse", "HEAD")
        self.run_git("checkout", "--quiet", "-b", "merge-source", self.source)
        self.run_git("merge", "--no-ff", "--quiet", "-m", "Merge source", fixup)
        merge = self.run_git("rev-parse", "HEAD")
        # These source branches omit management files; restore management before exporting.
        self.run_git("checkout", "--quiet", "management")
        for ref, message in ((merge, "linear"), (fixup, "autosquash"),
                             (duplicate, "duplicate"), ("--help", "Invalid source ref")):
            with self.subTest(ref=ref):
                with self.assertRaisesRegex(patch_stack.StackError, message):
                    patch_stack.export_stack(self.repo, ref, self.owned / "rejected")

    def test_new_subject_gets_safe_stable_slug_and_rebased_subject_keeps_id(self):
        self.run_git("checkout", "--quiet", "-b", "new-source", self.source)
        (self.repo / "new.txt").write_bytes(b"new feature\n")
        self.commit("CON")
        source = self.run_git("rev-parse", "HEAD")
        self.run_git("checkout", "--quiet", "management")
        destination = self.owned / "new-export"
        patch_stack.export_stack(self.repo, source, destination)
        entries = json.loads((destination / "patch-stack.json").read_text())["patches"]
        self.assertEqual(entries[:2], self.manifest["patches"])
        self.assertEqual(entries[2]["id"], "patch-con")

    def test_rewritten_commit_ids_keep_exact_subject_mapping(self):
        destination = self.owned / "recreated"
        recreated = patch_stack.import_stack(self.repo, destination)
        patch_stack.git(destination, "config", "user.name", "Stack Test")
        patch_stack.git(destination, "config", "user.email", "stack-test@example.invalid")
        patch_stack.git(destination, "commit", "--amend", "--no-edit",
                        "--date=2001-01-01T00:00:00Z")
        changed_source = patch_stack.text(destination, "rev-parse", "HEAD")
        self.assertNotEqual(changed_source, self.source)
        patch_stack.git(self.repo, "fetch", "--no-tags", "--", str(destination), changed_source)
        output = self.owned / "rewritten-export"
        patch_stack.export_stack(self.repo, changed_source, output)
        manifest = json.loads((output / "patch-stack.json").read_text())
        self.assertEqual(manifest["patches"], self.manifest["patches"])
        self.assertEqual(manifest["tree"], recreated["tree"])

    def test_missing_patch_and_relative_destination_rejected(self):
        for operation in (lambda: patch_stack.import_stack(self.repo, "relative"),
                          lambda: patch_stack.export_stack(self.repo, self.source, "relative")):
            with self.assertRaisesRegex(patch_stack.StackError, "absolute"):
                operation()
        (self.repo / "patches" / "binary.patch").unlink()
        with self.assertRaisesRegex(patch_stack.StackError, "Missing"):
            patch_stack.inspect(self.repo)

    def test_cli_inspect_reports_no_application_proof(self):
        result = subprocess.run(
            [sys.executable, str(Path(patch_stack.__file__)), "--repo", str(self.repo), "inspect"],
            capture_output=True, check=True)
        output = json.loads(result.stdout)
        self.assertFalse(output["application_verified"])
        self.assertEqual(output["count"], 2)

    def test_symlink_patch_rejected_when_supported(self):
        patch = self.repo / "patches" / "binary.patch"
        saved = self.owned / "saved.patch"
        patch.rename(saved)
        try:
            patch.symlink_to(saved)
        except OSError:
            saved.rename(patch)
            self.skipTest("Creating symlinks requires platform privileges")
        with self.assertRaisesRegex(patch_stack.StackError, "non-symlink"):
            patch_stack.inspect(self.repo)


if __name__ == "__main__":
    unittest.main()
