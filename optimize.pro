-dontobfuscate
-dontshrink
-allowaccessmodification
-overloadaggressively
-dontskipnonpubliclibraryclasses
-mergeinterfacesaggressively
-verbose

-keepattributes SourceFile, LineNumberTable

-keep class com.jzbrooks.vat.MainKt {
  public static void main(java.lang.String[]);
}

## Conversion doesn't happen in this tool, so tools sdk classes are irrelevant
-dontwarn android.annotation.SuppressLint
-dontwarn com.android.ide.common.vectordrawable.Svg2Vector
-dontwarn kotlin.annotations.jvm.**
-dontwarn org.checkerframework.checker.nullness.qual.**
-dontwarn org.jetbrains.annotations.**
-dontwarn org.jetbrains.kotlin.com.google.**
-dontwarn org.jetbrains.kotlin.com.intellij.**
-dontwarn org.jetbrains.kotlin.org.apache.commons.compress.**
-dontwarn org.mozilla.universalchardet.**
