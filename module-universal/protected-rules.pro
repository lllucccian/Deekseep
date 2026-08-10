# Closed local-API build: obfuscate and optimize code while preserving the reflection-heavy
# Xposed/Android entry contracts. Shrinking remains off so optional host-version paths survive.
-dontshrink
-dontpreverify
-dontwarn **
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod,Exceptions

# Xposed entry and Android manifest components are looked up by their original binary names.
-keepnames class com.dsmod.probe.Main
-keepclassmembers class com.dsmod.probe.Main {
    public <init>();
    public void handleLoadPackage(de.robv.android.xposed.callbacks.XC_LoadPackage$LoadPackageParam);
}
-keepnames class com.dsmod.probe.SettingsActivity
-keepnames class com.dsmod.probe.PromptPickerActivity
-keepnames class com.dsmod.probe.LocalApiKeepAliveActivity
-keepnames class com.dsmod.probe.LocalApiKeepAliveService
-keepnames class com.dsmod.probe.AgentDelayActivity
-keepnames class com.dsmod.probe.AgentDelayReceiver
-keepnames class com.dsmod.probe.XposedActivationProvider
-keepnames class com.dsmod.probe.XposedActivationReceiver
-keepnames class com.dsmod.probe.ProactiveHeartbeatReceiver
-keepclassmembers class com.dsmod.probe.SettingsActivity,
        com.dsmod.probe.PromptPickerActivity,
        com.dsmod.probe.LocalApiKeepAliveActivity,
        com.dsmod.probe.LocalApiKeepAliveService,
        com.dsmod.probe.AgentDelayActivity,
        com.dsmod.probe.AgentDelayReceiver,
        com.dsmod.probe.XposedActivationProvider,
        com.dsmod.probe.XposedActivationReceiver,
        com.dsmod.probe.ProactiveHeartbeatReceiver {
    public <init>();
}

# Runtime reflection uses these exact module class names as stable feature identifiers.
-keepnames class com.dsmod.probe.AccountManager
-keepnames class com.dsmod.probe.AgentDeviceBridge
-keepnames class com.dsmod.probe.AgentQuestionUi
-keepnames class com.dsmod.probe.AgentSettingsUi
-keepnames class com.dsmod.probe.ChatAppearance
-keepnames class com.dsmod.probe.ChatAppearanceUi
-keepnames class com.dsmod.probe.ChatSearchUi
-keepnames class com.dsmod.probe.DeekseepTools
-keepnames class com.dsmod.probe.DeekseepUi
-keepnames class com.dsmod.probe.HeartbeatToolProtocol
-keepnames class com.dsmod.probe.HostCompat
-keepnames class com.dsmod.probe.LocalApiGateway
-keepnames class com.dsmod.probe.Main
-keepnames class com.dsmod.probe.OmniRouteToolBridge
-keepnames class com.dsmod.probe.OpenAiToolBridge
-keepnames class com.dsmod.probe.ProactiveHeartbeatReceiver
-keepnames class com.dsmod.probe.PublicTunnelManager
-keepnames class com.dsmod.probe.PinggyTunnelManager
-keepnames class com.dsmod.probe.ResponsePreserver
-keepnames class com.dsmod.probe.RichPanelRenderer
-keepnames class com.dsmod.probe.SpatialMotionController
-keepnames class com.dsmod.probe.SpatialMotionUi
-keepnames class com.dsmod.probe.UiLanguage

# JSch discovers algorithms/providers by class name.
-keep class com.github.mwiede.jsch.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
