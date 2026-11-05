# kotlinx.serialization keeps generated serializers through R8.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class io.github.satoru2727.miteru.**$$serializer { *; }
-keepclassmembers class io.github.satoru2727.miteru.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.satoru2727.miteru.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Media3 / ExoPlayer uses reflection for extension renderers.
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }
