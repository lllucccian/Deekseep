import java.io.ByteArrayInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

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
    from("../../module/src") {
        exclude("com/dsmod/probe/SettingsActivity.java")
        exclude("com/dsmod/probe/internal/**")
    }
    into(stagedModuleSources)
}
val stagedModuleResources = layout.buildDirectory.dir("generated/moduleResources")
val stageModuleResources = tasks.register<Sync>("stageModuleResources") {
    from("../../module-universal/res")
    into(stagedModuleResources)
}
val stagedRuntimeResources = layout.buildDirectory.dir("generated/runtimeResources")
val stageRuntimeResources = tasks.register<Sync>("stageRuntimeResources") {
    from("../../module-universal/res/drawable-nodpi") {
        include("ic_category_*.png")
        into("META-INF/com.dsmod.probe.icons")
    }
    from("../../module-universal/res/drawable-nodpi/sponsor_qr.png") {
        into("META-INF/com.dsmod.probe.project")
    }
    from("../../module-universal/res/raw/gpl_3_0.txt") {
        into("META-INF/com.dsmod.probe.project")
    }
    from("../../third_party/shizuku/rish_shizuku.dex") {
        into("META-INF/com.dsmod.probe.agent")
        // Java-resource merging drops dot-prefixed files; use a normal archive entry here.
        rename { "rish_shizuku_runtime_payload.dat" }
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
        versionCode = 33
        versionName = "1.7.4"
    }

    sourceSets["main"].apply {
        manifest.srcFile("src/main/AndroidManifest.xml")
        java.directories += setOf(
            stagedModuleSources.get().asFile.path,
            file("../../module-legacy/compat").path,
            file("src/main/java").path,
        )
        res.directories += stagedModuleResources.get().asFile.path
        assets.directories += file("../../module-universal/assets").path
        resources.directories += stagedRuntimeResources.get().asFile.path
        jniLibs.directories += file("src/main/jniLibs").path
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

    signingConfigs {
        create("module") {
            storeFile = file("../../module/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("module")
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("module")
            // Preserve every module/reflection entry while removing unreachable dependency code.
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
    }
    if (name.contains("JavaRes") || name.startsWith("merge") && name.contains("Resource")) {
        dependsOn(stageRuntimeResources)
    }
    if (name != "stageModuleResources" && name != "trimMaterialIcons" && name != "clean") {
        dependsOn(stageModuleResources)
    }
    if (name == "stageModuleResources") mustRunAfter("clean")
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
    implementation(files(trimmedMaterialIconsJar))
    compileOnly("de.robv.android.xposed:api:82")
}
