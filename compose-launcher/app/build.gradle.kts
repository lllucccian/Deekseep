import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val protectedDistribution = providers.gradleProperty("protectedBuild")
    .orElse(providers.environmentVariable("PROTECTED_BUILD"))
    .orElse("true")
    .map { it.equals("true", ignoreCase = true) }
    .get()
val localApiIncluded = providers.gradleProperty("localApiIncluded")
    .orElse("true")
    .map { it.equals("true", ignoreCase = true) }
    .get()
val shiV5V241 = providers.gradleProperty("shiV5V241")
    .orElse(providers.environmentVariable("SHI_V5_V241"))
    .orElse("false")
    .map { it.equals("true", ignoreCase = true) }
    .get()
check(!shiV5V241 || protectedDistribution) {
    "shiV5V241 is Closed-only and must never enter an Open build"
}
val moduleSourceRoot = providers.gradleProperty("moduleSourceRoot")
    .orElse("../../module/src")
    .get()
val modulePayloadSourceRoot = providers.gradleProperty("modulePayloadSourceRoot")
    .orElse("../../module-universal/protected-payload-src")
    .get()
val moduleResourceRoot = providers.gradleProperty("moduleResourceRoot")
    .orElse("../../module-universal/res")
    .get()
val moduleAssetsRoot = providers.gradleProperty("moduleAssetsRoot")
    .orElse("../../module-universal/assets")
    .get()
val moduleCompatRoot = providers.gradleProperty("moduleCompatRoot")
    .orElse("../../module-legacy/compat")
    .get()
val googleV241Basic = providers.gradleProperty("googleV241Basic")
    .orElse("false")
    .map { it.equals("true", ignoreCase = true) }
    .get()
val shiV5HostVersionCode = providers.gradleProperty("shiV5HostVersionCode")
    .orElse(providers.environmentVariable("SHI_V5_HOST_VERSION_CODE"))
    .orElse("257")
    .get()
val exactV241HostBuild = shiV5HostVersionCode == "257"
val shiV5HostCertSha256Alt = providers.gradleProperty("shiV5HostCertSha256Alt")
    .orElse(providers.environmentVariable("SHI_V5_HOST_CERT_SHA256_ALT"))
    .orElse("")
    .get()
val moduleManifestFile = providers.gradleProperty("moduleManifestFile")
    .orElse("src/main/AndroidManifest.xml")
    .get()
// Closed artifacts have an independent release identity.  Do not silently fall back to the
// public Android debug key: a missing private signing configuration must fail the Closed build.
// Open remains intentionally signed with the ordinary debug key for reproducible public builds.
val closedSigningProperties = Properties()
val closedSigningPropertiesFile = file("../../.private/closed/release-signing.properties")
if (protectedDistribution) {
    check(closedSigningPropertiesFile.isFile) {
        "Closed signing configuration is missing: ${closedSigningPropertiesFile.path}"
    }
    FileInputStream(closedSigningPropertiesFile).use { closedSigningProperties.load(it) }
}
fun closedSigningValue(name: String): String {
    val value = closedSigningProperties.getProperty(name)?.trim().orEmpty()
    check(value.isNotEmpty()) { "Closed signing configuration has no $name" }
    return value
}
val signingKeyFile = if (protectedDistribution) {
    File(closedSigningPropertiesFile.parentFile, closedSigningValue("storeFile")).path
} else {
    providers.gradleProperty("signingKeyFile").orElse("../../module/debug.keystore").get()
}
val signingStorePassword = if (protectedDistribution) {
    closedSigningValue("storePassword")
} else "android"
val signingKeyAlias = if (protectedDistribution) {
    closedSigningValue("keyAlias")
} else "androiddebugkey"
val signingKeyPassword = if (protectedDistribution) {
    closedSigningValue("keyPassword")
} else "android"

// The official Material extended-icons AAR contains more than eleven thousand generated icon
// classes. This launcher uses exactly six of them. Keep the original Google-compiled bytecode for
// those six icons (so their paths and rendering remain byte-for-byte identical), but do not ship
// the other unused classes. The core icon AAR remains a normal dependency below.
val materialIconsExtendedSource = configurations.create("materialIconsExtendedSource") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
val trimmedMaterialIconsJar = layout.buildDirectory.file(
    "generated/trimmedMaterialIcons/material-icons-extended-used.jar"
)
val trimMaterialIcons = tasks.register("trimMaterialIcons") {
    inputs.files(materialIconsExtendedSource)
    outputs.file(trimmedMaterialIconsJar)
    doLast {
        val wanted = setOf(
            "androidx/compose/material/icons/outlined/ArrowDownwardKt.class",
            "androidx/compose/material/icons/outlined/ArrowUpwardKt.class",
            "androidx/compose/material/icons/outlined/CodeKt.class",
            "androidx/compose/material/icons/outlined/DevicesKt.class",
            "androidx/compose/material/icons/outlined/ForumKt.class",
            "androidx/compose/material/icons/outlined/GavelKt.class",
            "androidx/compose/material/icons/outlined/OpenInNewKt.class",
            "META-INF/material-icons-extended_release.kotlin_module",
            "META-INF/androidx.compose.material_material-icons-extended.version",
        )
        val sourceAar = materialIconsExtendedSource.singleFile
        val classesJar = ZipFile(sourceAar).use { aar ->
            val entry = aar.getEntry("classes.jar")
                ?: error("Official Material extended-icons AAR has no classes.jar")
            aar.getInputStream(entry).use { it.readBytes() }
        }
        val output = trimmedMaterialIconsJar.get().asFile
        output.parentFile.mkdirs()
        val copied = linkedSetOf<String>()
        ZipInputStream(ByteArrayInputStream(classesJar)).use { input ->
            ZipOutputStream(output.outputStream().buffered()).use { jar ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    if (!entry.isDirectory && entry.name in wanted) {
                        val exactEntry = ZipEntry(entry.name).apply { time = 0L }
                        jar.putNextEntry(exactEntry)
                        input.copyTo(jar)
                        jar.closeEntry()
                        copied += entry.name
                    }
                    input.closeEntry()
                }
            }
        }
        check(copied == wanted) {
            "Material icon trim mismatch: missing=${wanted - copied}, unexpected=${copied - wanted}"
        }
    }
}

val stagedModuleSources = layout.buildDirectory.dir("generated/moduleSources")
val stageModuleSources = tasks.register<Sync>("stageModuleSources") {
    from(moduleSourceRoot) {
        exclude("com/dsmod/probe/SettingsActivity.java")
        exclude("com/dsmod/probe/BuildInfo.java")
        exclude("com/dsmod/probe/internal/**")
        // CloudPromptClient contains the Closed service URL, request protocol and Android
        // Keystore grant implementation.  Open builds receive only the inert compile-time
        // compatibility shell emitted by prepare-development-inputs.sh; the real client must
        // never be staged into an Open artifact.
        if (!protectedDistribution) {
            exclude("com/dsmod/probe/CloudPromptClient.java")
            // Community UI requires the real CloudPromptClient; Open builds ship a no-op stub.
            exclude("com/dsmod/probe/CommunityUi.java")
            // Open has no loader admission, anti-root, or anti-Frida policy.
            exclude("com/dsmod/probe/LoaderSecurityGuard.java")
            exclude("com/dsmod/probe/z16.java")
            exclude("com/dsmod/probe/RuntimeProofPartA.java")
            exclude("com/dsmod/probe/RuntimeProofPartB.java")
        }
    }
    into(stagedModuleSources)
}
val protectedInputs = layout.buildDirectory.dir("generated/protectedInputs")
val prepareVariantInputs = tasks.register<Exec>("prepareVariantInputs") {
    val script = if (protectedDistribution) {
        "../prepare-protected-inputs.sh"
    } else {
        "../prepare-development-inputs.sh"
    }
    commandLine("bash", script)
    environment("SHI_V5_V241", shiV5V241.toString())
    environment("LOCAL_API_INCLUDED", localApiIncluded.toString())
    environment("GOOGLE_V241_BASIC", googleV241Basic.toString())
    environment("SHI_V5_HOST_VERSION_CODE", shiV5HostVersionCode)
    environment("SHI_V5_HOST_CERT_SHA256_ALT", shiV5HostCertSha256Alt)
    environment("MODULE_PROTECTED_PAYLOAD_SOURCE", file(modulePayloadSourceRoot).absolutePath)
    inputs.file(file(script))
    inputs.files(fileTree(modulePayloadSourceRoot))
    if (shiV5V241) {
        inputs.file(file("../../editions/closed/v241-protection/runtime-proof-compat.env"))
    }
    outputs.dir(protectedInputs)
    // The preparation scripts embed the current build timestamp in BuildInfo.java. Gradle cannot
    // infer that dynamic input from the script contents, so never reuse a stale generated date.
    outputs.upToDateWhen { false }
}
val stagedModuleResources = layout.buildDirectory.dir("generated/moduleResources")
val stageModuleResources = tasks.register<Sync>("stageModuleResources") {
    from(moduleResourceRoot) {
        exclude("raw/gpl_3_0.txt")
    }
    into(stagedModuleResources)
}
val compileProtectedPayload = tasks.register<Exec>("compileProtectedPayload") {
    val classesDir = layout.buildDirectory.dir(
        "intermediates/javac/release/compileReleaseJavaWithJavac/classes")
    dependsOn("compileReleaseJavaWithJavac")
    commandLine("bash", "../compile-protected-payload.sh",
        classesDir.get().asFile.absolutePath)
    environment("SHI_V5_V241", shiV5V241.toString())
    environment("GOOGLE_V241_BASIC", googleV241Basic.toString())
    environment("MODULE_PROTECTED_PAYLOAD_SOURCE", file(modulePayloadSourceRoot).absolutePath)
    inputs.file(file("../compile-protected-payload.sh"))
    inputs.files(fileTree(modulePayloadSourceRoot))
    inputs.dir(classesDir)
    outputs.dir(protectedInputs.map { it.dir("resources") })
    outputs.upToDateWhen { false }
}
val stagedRuntimeResources = layout.buildDirectory.dir("generated/runtimeResources")
val stageRuntimeResources = tasks.register<Sync>("stageRuntimeResources") {
    from("$moduleResourceRoot/drawable-nodpi") {
        include("ic_category_*.png")
        into("META-INF/com.dsmod.probe.icons")
    }
    from("$moduleResourceRoot/drawable-nodpi/sponsor_qr.png") {
        into("META-INF/com.dsmod.probe.project")
    }
    from("../../third_party/shizuku/rish_shizuku.dex") {
        into("META-INF/com.dsmod.probe.agent")
        // Java-resource merging drops dot-prefixed files; use a normal archive entry here.
        rename { "rish_shizuku_rt.dat" }
    }
    into(stagedRuntimeResources)
}


android {
    namespace = "com.dsmod.probe"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dsmod.probe"
        minSdk = 24
        targetSdk = 34
        versionCode = 36
        versionName = "1.8"
        manifestPlaceholders["runtimeProofEnabled"] = shiV5V241.toString()
        // Activation and the localhost API service are Closed-only components.  Open keeps
        // neither endpoint enabled nor a startable API foreground service.
        manifestPlaceholders["closedActivationEnabled"] = protectedDistribution.toString()
        manifestPlaceholders["localApiComponentEnabled"] =
            (protectedDistribution && localApiIncluded).toString()
        // Reverted 360-Jiagu-style camouflage (com.stub.StubApp): its shell mechanics require an
        // (effectively) empty classes.dex, which deep scanners like MT Manager cross-check and
        // flag as fake when the real dex is present. Yidun-style SDK markers (see
        // prepare-protected-inputs.sh) don't have that requirement, so camouflage now targets
        // that instead. Literal default, behaviorally identical to omitting the attribute.
        manifestPlaceholders["applicationClassName"] = "android.app.Application"
        if (shiV5V241) {
            ndk { abiFilters += "arm64-v8a" }
        }
    }

    sourceSets["main"].apply {
        manifest.srcFile(moduleManifestFile)
        java.directories += setOf(
            stagedModuleSources.get().asFile.path,
            protectedInputs.get().dir("src").asFile.path,
            file(moduleCompatRoot).path,
            file("src/main/java").path,
            file("../../editions/closed/full-backup-src").path,
        )
        res.directories += stagedModuleResources.get().asFile.path
        assets.directories += file(moduleAssetsRoot).path
        if (protectedDistribution) {
            // .so decoy files (e.g. the assets/libnesec.so camouflage copy) must go through the
            // real assets sourceSet: the Java-resource merger used for protectedInputs/resources
            // silently drops .so entries (and would also drop dot-prefixed entries -- see
            // stageRuntimeResources' rish_shizuku rename above).
            assets.directories += protectedInputs.get().dir("assets").asFile.path
        }
        resources.directories += stagedRuntimeResources.get().asFile.path
        resources.directories += protectedInputs.get().dir("resources").asFile.path
        jniLibs.directories += file("src/main/jniLibs").path
        if (localApiIncluded && !shiV5V241) {
            // Every maintained Closed host provisions the pinned Cloudflare executable on
            // demand after explicit confirmation. The legacy bundled 28MB copy must not enter
            // any current artifact.
            jniLibs.directories += file("../../third_party/shi-native/android").path
        }
        // Shi V5 is the Closed runtime-protection envelope, not a Local API feature. The exact
        // Google code258 basic branch intentionally excludes Local API while retaining the same
        // encrypted payload and native environment/thread checks as the domestic Closed build.
        if (shiV5V241) {
            jniLibs.directories += file("../../third_party/shi-v5-v241/android").path
        }
        if (protectedDistribution) {
            // Static-scanner camouflage decoys (inert, never loaded) generated alongside the
            // real payload in prepare-protected-inputs.sh. Wired for every Closed variant, not
            // just shiV5V241, since it ships regardless of which native envelope is active.
            jniLibs.directories += protectedInputs.get().dir("native").asFile.path
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        resources.excludes += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
        )
    }

    androidResources {
        // Defense in depth for dirty build caches: code257 contains only the retained
        // higher-quality 2.84MB clip even if an obsolete legacy asset reappears locally.
        if (exactV241HostBuild) ignoreAssetsPattern = "rickroll.mp4"
    }

    signingConfigs {
        create("module") {
            storeFile = file(signingKeyFile)
            storePassword = signingStorePassword
            keyAlias = signingKeyAlias
            keyPassword = signingKeyPassword
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("module")
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("module")
            // The closed distribution keeps its R8/protection pipeline. The open distribution
            // uses R8 with -dontobfuscate to discard unused Compose/AndroidX bloat while
            // keeping all module classes and methods completely unobfuscated and transparent.
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                if (protectedDistribution) "proguard-protected-rules.pro"
                else "proguard-rules.pro",
            )
        }
    }
}

tasks.configureEach {
    if (name != "trimMaterialIcons" && name != "clean" && !name.startsWith("stage")) {
        dependsOn(trimMaterialIcons)
    }
    if (name.startsWith("compile") || name.startsWith("ksp") || name.startsWith("kapt")) {
        dependsOn(stageModuleSources)
        dependsOn(prepareVariantInputs)
    }
    if (name.contains("JavaRes") || name.startsWith("merge") && name.contains("Resource")) {
        dependsOn(stageRuntimeResources)
        dependsOn(prepareVariantInputs)
        if (protectedDistribution) dependsOn(compileProtectedPayload)
    }
    if (name.contains("NativeLib") || name.contains("JniLib")) {
        // prepareVariantInputs rebuilds libshi.so with the fresh per-build CONST; make sure the
        // native-libs merge captures that freshly built .so, not a stale one.
        if (protectedDistribution) dependsOn(prepareVariantInputs)
        if (shiV5V241) dependsOn(compileProtectedPayload)
    }
    if (name.startsWith("merge") && name.contains("Assets")) {
        // protectedInputs/assets (the libnesec.so camouflage decoy) is only wired for the closed
        // distribution, but declare the dependency unconditionally since it is cheap and keeps
        // this branch symmetric with the resource/native-lib merge wiring above.
        if (protectedDistribution) dependsOn(prepareVariantInputs)
    }
    if (name != "stageModuleResources" && name != "trimMaterialIcons" && name != "clean") {
        dependsOn(stageModuleResources)
    }
    if (name == "stageModuleResources") mustRunAfter("clean")
    if (name == "prepareVariantInputs") mustRunAfter("clean")
}

dependencies {
    materialIconsExtendedSource(
        "androidx.compose.material:material-icons-extended-android:1.7.8@aar"
    )
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core:1.18.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3:1.5.0-alpha19")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("com.github.rikkahub:hugeicons-compose:1.3")
    implementation(files(trimmedMaterialIconsJar))
    compileOnly("de.robv.android.xposed:api:82")
}

