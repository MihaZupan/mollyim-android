# Molly patch management: Signal 8.34.2

This branch contains the maintained patch series, tooling and documentation.

| Branch | Purpose |
|---|---|
| `v8.34.2/signal-baseline` | Official Signal v8.34.2, `515db93fb48206d63406cbfb48c5816f6af1d9e2`. |
| `v8.34.2/molly-patches` | Ordinary history forked from `73f836adf03a20f6a2641676d18f907712e29197`, retaining ancestry to fixed 8.19.2 commit `644daeb9f02cba0203baef5f9eee4ef4cde7345d`. |
| `v8.34.2/molly` | The direct source port; current source/tree pins are recorded in the manifest. |

**Port in progress, not an accepted version checkpoint.** The regenerated
35-patch series reconstructs the recorded 8.34.2 candidate source tree.
A synchronized series establishes tree agreement, not runtime acceptance.
The 8.19.2 and 8.20.4 refs and original reference/source histories remain unchanged.
The legacy-search deletion is upstreamed and no longer needs a patch. The selected
native forks are Molly libsignal 0.105.4-1 and RingRTC 2.73.0-1.
The explicitly reauthorized lockout-time backup/export overflow repair is adapted
in the current series, including encrypted Long/legacy Int compatibility, checked
32-bit backup encoding and nonfatal job rejection before any backup frames.
The original archived repair remains unchanged at
`migration/archive/signal-8.19.2-timeout-repair-037-20261104`.

## Files and acceptance

- `patch-stack.json` records upstream/source/tree identities, branch names and
  `file`/`subject` entries in application order.
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
reused only in its actual scope; changed 8.34.2 behavior requires affected checks
and bounded upgrade, startup and linked-shaped restore acceptance.

## Local operations

Run these commands from this patch-management worktree. Every destination must
be a **new, nonexistent path**. Existing directories, application data and other
worktrees are never reset, overwritten or automatically cleaned.

```powershell
python .\scripts\patch_stack.py inspect
python -m unittest discover -s scripts -p 'test_*.py'
python .\scripts\patch_stack.py import --destination D:\molly\migration-worktrees\molly-8342-import-review
python .\scripts\patch_stack.py export --source-ref v8.34.2/molly --destination D:\molly\migration-worktrees\molly-8342-export-review
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
subjects preserve patch filenames; subject changes need an explicit identity
decision. AI may propose grouping or resolve semantic conflicts, but import and
export are mechanical Git operations.

### Patch-first changes

Edit the patch files and corresponding manifest entries/order. To derive a
proposed source tree when the recorded tree is intentionally stale:

```powershell
python .\scripts\patch_stack.py import --propose --destination D:\molly\migration-worktrees\molly-8342-patch-proposal
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
The `check` operation validates an already synchronized pair, including the exact
source-branch head recorded in the manifest. An intentional pending edit can fail
that steady-state gate until its counterpart proposal is reviewed and promoted.

Nothing is active until branches are published. GitHub manual dispatch also
requires the workflow to be registered on the repository's default branch;
selecting a patch-management default branch or adding a reviewed launcher is a
separate publication decision. The unchanged source branch does not contain this
workflow, so source-only pushes are not automatically observed by it. Use the
local exporter or an enabled manual dispatch.

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
patch-management branches compares their snapshots, including tooling changes.
