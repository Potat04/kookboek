# GitHub updates

- [x] Ground: MainActivity opens the app; KookboekApp owns work that survives screens.
- [x] Sketch: compare an application-owned manager with an activity ViewModel/repository.
- [x] Agree: proceed under the implementation request.
- [x] Implement and verify.
- [x] Reassess the design against implementation.

The caller observes `app.updates.state`, calls `check(manual)`, `download()` or
`cancel()`, and requests an `installIntent()` after the user chooses installation.
The UI owns the dialog and permission/installer launches. The manager never holds
an Activity and never starts installation by itself.

An application-owned manager matches the existing import lifetime and keeps one
download alive through navigation and rotation. An activity ViewModel plus a
repository would either cancel it on activity destruction or delegate the same
work back to the application. We choose the manager and retain the alternative's
separation of Activity effects from download state.

`AppRelease` parses stable GitHub releases and compares numeric versions.
`UpdateManager` owns throttling, cached release metadata, bounded HTTPS downloads,
checksum verification, and APK package/version/signing checks. Settings and the
startup notice share its state. All networking and stream cleanup happen on IO.

The repository must publish one APK per stable release, use numeric tags such as
`v1.0.3`, increment Android versionCode, and retain the release signing key.
The SHA-256 digest is checked when GitHub supplies it. Android remains the final
installer and signature authority. The user enables installation from Kookboek
and explicitly opens the installer; returning from settings never installs an APK.

Downloads survive navigation, not process death. An interrupted download can be
retried; partial files are never offered for installation. Updater bookkeeping
is device-local and excluded from Android backup.

Validation: debug assembly and JVM tests pass, including release selection,
numeric version ordering, daily throttling, checksum/partial-file rejection,
package/version checks, and signing compatibility. Android lint reports an
existing NonObservableLocale error in Labels.kt; the updater has no lint findings.
Installation and the unknown-source permission flow still require device testing.

Implementation review kept the chosen ownership. APK validation has an internal
plain-Kotlin seam so its rejection rules are tested without an Android installer.
Cancelled transfers must finish closing their files before a new operation starts.
An unsuccessful cached check must never become a successful "current" status.
