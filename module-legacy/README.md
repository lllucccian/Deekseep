# Universal Xposed Adapter

This internal adapter builds the domestic and Google Play universal APKs for
traditional Xposed-compatible environments.

The universal APKs do not maintain a second feature fork. `build.sh`
compiles the canonical sources under `../module/src/com/dsmod/probe`, generates
a traditional `handleLoadPackage` entry from canonical `Main.java`, and bridges
its around-hook contract through `compat/LegacyXposedModule.java`.

`src/de/robv/android/xposed` contains compile-only signatures supplied by the
real framework at runtime.

```bash
cd module-legacy
bash build.sh
bash test-adapter-regression.sh
```

The unrenamed output is an internal adapter APK. Use `scripts/build-all.sh` from
the repository root to produce the two signed universal release filenames, run
the canonical regressions and verify both channel layouts.
