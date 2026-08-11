# Module code is reached through Xposed entry points, Android components, reflection, JNI-style
# protocol names, and host callbacks. Keep it byte-for-byte addressable while allowing R8 to
# discard only unreachable third-party library code.
-keep class com.dsmod.probe.** { *; }
-keep interface com.dsmod.probe.** { *; }

-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod
