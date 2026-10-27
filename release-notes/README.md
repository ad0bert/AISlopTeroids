# Release notes

One Markdown file per release, named after its tag: `v0.1.0.md`. The release workflow
(`.github/workflows/release.yml`) uses it for the GitHub release:

- the first line, a `# ` heading, is the release title (`# v0.1.0: What changed`);
- everything after it is the release description.

Write the file before pushing the tag. To publish a release again for an existing tag (after fixing its notes, or when
a run failed), run the workflow by hand: Actions → release → Run workflow, with the tag. It replaces the release's
title, notes and files.
