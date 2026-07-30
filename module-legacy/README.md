# Traditional-Xposed Compatibility Adapter

This project retains the traditional Xposed callback adapter and its regression
fixtures. Deekseep 1.7.3 no longer publishes a separate Legacy APK; the public
`module-universal/` package uses this adapter to support API 82 / 100 / 101 /
102 environments.

The adapter does not maintain a second feature fork. `build.sh`
compiles the canonical sources under `../module/src/com/dsmod/probe`, generates
a traditional `handleLoadPackage` entry from canonical `Main.java`, and bridges
its around-hook contract through `compat/LegacyXposedModule.java`.

`src/de/robv/android/xposed` contains compile-only signatures supplied by the
real framework at runtime. The historical files under `src/com/dsmod/probe`
are excluded from compilation and retained only for repository history.

```bash
cd module-legacy
bash build.sh
bash test-adapter-regression.sh
```

The standalone output is an internal compatibility artifact and is not copied
to the release directory. Use `scripts/build-all.sh` from the repository root
to run the canonical regressions and produce only the signed 1.7.3 universal
release APK.
