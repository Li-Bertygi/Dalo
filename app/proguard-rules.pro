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

# 파이썬(Chaquopy)에서 호출하는 진행률 콜백 인터페이스 보호
-keep interface com.Libertygi.dalo.DownloadService$ProgressCallback {
    <methods>;
}

# DownloadService 내부 클래스 및 메서드 난독화 방지
-keep class com.Libertygi.dalo.DownloadService** { *; }

# Chaquopy 관련 클래스 보호
-keep class com.chaquo.python.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# 파이썬에서 호출할 가능성이 있는 모든 메서드 보호
-keepclassmembers class * {
    @com.chaquo.python.PyMethod *;
}