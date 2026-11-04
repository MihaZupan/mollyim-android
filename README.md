# Molly patch management: Signal 8.19.2

This branch contains the maintained patch series, its tooling and documentation.
It is not the application source branch.

| Branch | Purpose |
|---|---|
| `v8.19.2/signal-baseline` | Official Signal v8.19.2, `c47a74ba0961903eeaeda6d22347a990056c3e83`. |
| `v8.19.2/molly-patches` | This independent, ordinary patch-management history. |
| `v8.19.2/molly` | The accepted36 semantic commits, HEAD `35cefb3bbf4ef3f2e6e96a5ddf7f7565aad8c13d`. |

The source tree is exactly pinned Molly reference
`892d6264ba007b5458a8b74cf9183c665f1f17a1`:
`b1e8f33458c50620137c332d0569cca67b799ad6`.
The original curated branch preserves the pre-rewrite source history.
The known timeout backup/restore defect is **deferred, not fixed**. Its excluded
commit37 remains on `migration/archive/signal-8.19.2-timeout-repair-037-20261104`;
none of its four production changes or added test is in this series.

## Files and acceptance

- `patch-stack.json` records exact upstream/source/tree identities, branch names
  and stable patch IDs.
- `patches/.patch-order` is the explicit application order. It must match the
  manifest; filenames do not encode sequence numbers.
- `patches/*.patch` are binary-capable Git mail patches, one per semantic commit.
  Their bytes must not undergo line-ending conversion (`.gitattributes` sets
  `-text`). Authorship, rationale and attribution trailers are retained.
- `scripts/patch_stack.py` provides deterministic import/export and validation.
  Only Python's standard library and Git are required.

The acceptance invariant is:

```text
apply(exact baseline, exact ordered patches).tree == accepted source tree
```

The imported stack must also have one linear commit per patch. Reconstructed
commit IDs need not equal the original IDs: committer metadata may differ.
Git tree equality is not an APK, runtime, signing, native reproducibility or
whole-product security guarantee. Existing accepted M1/reference evidence is
reused in its actual scope; no Android preservation replay is required here.

## Local operations

Run these commands from this patch-management worktree. Every destination must
be a **new, nonexistent path**. Existing directories, application data and other
worktrees are never reset, overwritten or automatically cleaned.

```powershell
python .\scripts\patch_stack.py inspect
python -m unittest discover -s scripts -p 'test_*.py'
python .\scripts\patch_stack.py import --destination D:\molly\migration-worktrees\molly-8192-import-review
python .\scripts\patch_stack.py export --source-ref v8.19.2/molly --destination D:\molly\migration-worktrees\molly-8192-export-review
```

`import` creates an isolated local clone, applies all patches and requires the
recorded final tree. It never advances an existing branch. A failed operation
retains its new destination for diagnosis rather than deleting or retrying it.
Local object sharing does not fetch LFS payloads or submodules; this validates
tracked Git objects, not availability or provenance of external payloads.

### Source-first changes

Develop on a separate source worktree/topic branch. Fold fixups into their owning
logical commits before exporting; unnormalized fixup/squash commits and merges
are rejected. Export the source ref to a new snapshot, review its managed files,
then update `patches/` and `patch-stack.json` on this branch. Existing exact
subjects preserve patch IDs/filenames; subject changes need an explicit identity
decision. AI may propose grouping or resolve semantic conflicts, but import and
export are mechanical Git operations.

### Patch-first changes

Edit the patch files/order and corresponding manifest entries. To derive a
proposed source tree when the recorded tree is intentionally stale:

```powershell
python .\scripts\patch_stack.py import --propose --destination D:\molly\migration-worktrees\molly-8192-patch-proposal
```

This is explicitly a **proposal**, not a strict validation PASS. Review the
generated source, update the manifest's `source` and `tree` to the accepted
result, and rerun strict validation. The generated source commits must be
transferred into the main repository before referring to their IDs there.
Source files and patches remain editable, but accepted releases must agree.

### Publishing a synchronized state

Capture both input branch SHAs. Before publishing, verify neither changed; if
one did, reconcile rather than overwrite concurrent edits. Review counterpart
changes, validate the resulting tree and only then promote the pair. Never use
unconditional force pushes or move existing release tags.

Release tags identify immutable source commits and should record the exact
patch-management commit and upstream SHA. Intermediate development commits may
temporarily exist; the accepted source stack is normalized to one commit per
patch. The tools do not push, merge, rewrite canonical refs or install anything.

## GitHub workflow

`.github/workflows/patch-stack.yml` validates patch-branch pushes/PRs and offers
manual source-to-patches or patches-to-source **proposal artifacts**. It does not
silently publish either counterpart or start application/device tests.

Nothing is active until branches are published. GitHub manual dispatch also
requires the workflow to be registered on the repository's default branch;
selecting a patch-management default branch or adding a reviewed launcher is a
separate publication decision. The unchanged source branch does not contain this
workflow, so source-only pushes are not automatically observed by it. Use the
local exporter or an enabled manual dispatch; no bidirectional webhook/bot is
claimed to be installed.

## Next Signal version

Create `vNEXT/molly-patches` **from an accepted commit of this branch**, not from
an empty tree or the new Signal baseline. Update the baseline/branch metadata,
port the ordered patches and retain ordinary commits for those changes.
The new source stack starts separately from the exact new Signal release.
Account explicitly for retained, adapted, upstreamed or retired patches; clean
application alone does not establish semantic compatibility.

Old patch branches may continue receiving maintenance commits. Cherry-pick
selected backports rather than merging a newer baseline configuration wholesale.
Keep published release tags immutable. A two-dot GitHub comparison between
patch-management branches compares their snapshots, including intentional tooling
changes; clean patch-only comparisons are not a current requirement.
