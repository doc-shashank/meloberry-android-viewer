# Keep kotlinx.serialization serializers & metadata for release R8 builds
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt

-keep,includedescriptorclasses class com.meloberry.android.**$$serializer { *; }
-keepclassmembers class com.meloberry.android.** {
    *** Companion;
}
-keepclasseswithmembers class com.meloberry.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room entities, DAOs, and generated database classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class com.meloberry.android.** { *; }
-keep @androidx.room.Dao interface com.meloberry.android.** { *; }

# Hilt / Dagger generated factories & entry points
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# JLatexMath-Android native LaTeX renderer (reflection & asset fonts)
-keep class ru.noties.jlatexmath.** { *; }
-keep class org.scilab.forge.jlatexmath.** { *; }
-keep class ru.noties.jlatexmath.font.** { *; }
-dontwarn org.scilab.forge.jlatexmath.**
