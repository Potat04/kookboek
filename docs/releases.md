# Making a release

The previous release, `v1.0.2`, used GitHub-generated notes. You can repeat that
without copying or editing Markdown by hand.

1. Update `versionName` and increase `versionCode` in `app/build.gradle.kts`.
2. Merge the changes you want to release into `master`.
3. Build the signed release APK with `./gradlew :app:assembleRelease`.
4. On GitHub, open **Releases → Draft a new release**.
5. Choose a tag matching `versionName`, such as `v1.0.3`, and the commit you built.
6. Set the title to **Kookboek v1.0.3** and click **Generate release notes**.
7. Review the notes and attach the signed APK from
   `app/build/outputs/apk/release/`, renamed to `kookboek-v1.0.3.apk`.
8. Publish it as a stable release and mark it as the latest release.

The generated notes list merged pull requests and link to the full changelog.
Once `.github/release.yml` is on the release's target branch, GitHub groups PRs by label:

| PR label | Release section |
| --- | --- |
| `feature` or `enhancement` | New features |
| `bug` or `fix` | Fixes |
| Anything else, including no label | Other changes |
| `skip-changelog` | Omitted |

Labels are optional. Unlabelled PRs still appear in the notes.

For handwritten notes, copy [.github/RELEASE_TEMPLATE.md](../.github/RELEASE_TEMPLATE.md)
into the release description, replace the `{{PLACEHOLDERS}}`, and add one bullet
per change. GitHub does not automatically prefill the release editor from that
Markdown file; use **Generate release notes** for the automatic route.

The in-app updater expects one APK per stable release, a numeric tag matching the
APK's version name, a higher version code, and the same release signing key.
Attach the release APK, not the debug APK or an AAB. Keep the signing key safe.

GitHub's configuration reference:
[Automatically generated release notes](https://docs.github.com/en/repositories/releasing-projects-on-github/automatically-generated-release-notes).
