# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Room database rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Kotlinx Serialization
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Hilt rules
-keep class com.google.dagger.hilt.** { *; }

# Accompanist permissions
-keep class com.google.accompanist.permissions.** { *; }

# ML Kit Barcode scanning
-keep class com.google.mlkit.vision.barcode.** { *; }
