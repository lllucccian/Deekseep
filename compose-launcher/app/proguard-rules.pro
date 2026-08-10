# Module code is reached through Xposed entry points, Android components, reflection, JNI-style
# protocol names, and host callbacks. Keep it byte-for-byte addressable while allowing R8 to
# discard only unreachable third-party library code.
-keep class com.dsmod.probe.** { *; }
-keep interface com.dsmod.probe.** { *; }

# JSch selects algorithms and implementations from string-valued configuration entries.
-keep class com.jcraft.jsch.** { *; }

# These are optional desktop/provider integrations referenced by JSch but intentionally absent on
# Android. They were absent in the non-minified APK too; Pinggy uses Android's built-in crypto path.
-dontwarn com.sun.jna.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.bouncycastle.**
-dontwarn org.ietf.jgss.**
-dontwarn org.newsclub.net.unix.**
-dontwarn org.slf4j.**

-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod
