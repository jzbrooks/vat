-dontobfuscate
-dontshrink
-allowaccessmodification
-overloadaggressively
-dontskipnonpubliclibraryclasses
-mergeinterfacesaggressively
-verbose

-keep class com.jzbrooks.vat.MainKt {
  public static void main(java.lang.String[]);
}

## Conversion doesn't happen in this tool, so tools sdk classes are irrelevant
-dontwarn android.annotation.SuppressLint
-dontwarn com.android.ide.common.vectordrawable.Svg2Vector
