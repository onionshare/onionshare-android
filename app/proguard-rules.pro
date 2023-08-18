# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-dontobfuscate
-keepattributes SourceFile, LineNumberTable, *Annotation*, Signature, InnerClasses, EnclosingMethod

-keep class org.onionshare.android.** { *; }
-keep class org.torproject.jni.** { *; }

# Keep logging
-keep public class org.slf4j.** { *; }
-keep public class ch.qos.logback.** { *; }

# Keep Netty classes that are loaded via reflection
-keep class io.netty.util.ReferenceCountUtil { *; }
-keep class io.netty.buffer.WrappedByteBuf { *; }

-dontwarn com.fasterxml.jackson.databind.ext.Java7SupportImpl
-dontwarn io.netty.internal.tcnative.*
-dontwarn java.lang.management.*
-dontwarn org.apache.log4j.*
-dontwarn org.apache.logging.log4j.**
-dontwarn org.conscrypt.*
-dontwarn org.eclipse.jetty.npn.*
-dontwarn org.jetbrains.annotations.*
-dontwarn reactor.blockhound.integration.BlockHoundIntegration
-dontwarn javax.mail.**
