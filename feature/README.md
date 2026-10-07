# feature/

The screens, one Android library module per feature. Which feature does what, and its parts: each
module's README. What a feature module may use, and how a new one is added:
[`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md#features).

| Module | Screens |
|---|---|
| [`home/`](home/README.md), `:feature:home` | the home screen: the top bar, the app tiles, arrange mode |
| [`settings/`](settings/README.md), `:feature:settings` | the settings panel, and Hide apps |

Each holds the same parts:

```
<feature>/
├── build.gradle.kts            luncher.android.library (build-logic/); depends on :domain and :ui,
│                               and on another feature only to start its activity by class
└── src/
    ├── main/AndroidManifest.xml   its activities, and how they behave
    ├── main/java/com/luncher/launcher/<feature>/
    │                           <Feature>Graph: the ports it needs; its activities and views,
    │                           all internal but the activities and the graph
    ├── main/res/               its resources, named <feature>_…
    └── test/                   JVM tests, with Test<Feature>Graph and the tests' Application;
                                screenshots/<feature>/: reference images
```

A feature's instrumented tests are in `:app` (`app/src/androidTest/…/<feature>/`), since they need
the installed app ([`../docs/TESTING.md`](../docs/TESTING.md#organizing-tests)).
