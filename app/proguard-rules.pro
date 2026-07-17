# Keep MapLibre native bindings.
-keep class org.maplibre.android.** { *; }

# Keep kotlinx.serialization metadata.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# BRouter engine internals.
-keep class btools.** { *; }
