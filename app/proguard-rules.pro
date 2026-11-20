# ================= Free PDF Manager — ProGuard =================
# Keep Room
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }

# Keep Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep class * extends dagger.hilt.android.internal.managers.ActivityRetainedComponentManager { *; }

# PDFBox
-keep class org.apache.pdfbox.** { *; }
-keep class com.tom_roush.** { *; }
-keepclassmembers class org.apache.pdfbox.** { *; }
-dontwarn org.apache.pdfbox.**
-dontwarn com.tom_roush.**
-dontwarn org.apache.fontbox.**
-dontwarn org.apache.pdfbox.pdmodel.font.**

# Apache POI (offline doc conversion)
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.apache.commons.**
-keep class org.openxmlformats.** { *; }
-dontwarn org.openxmlformats.**
-keep class com.ctc.w3c.** { *; }
-dontwarn com.ctc.w3c.**
-keep class org.xml.sax.** { *; }

# OpenCV native
-keepclasseswithmembernames class org.opencv.** {
    native <methods>;
}
-keep class org.opencv.** { *; }

# PDFium native (AndroidPdfViewer)
-keep class com.github.barteksc.pdfium.** { *; }
-keepclasseswithmembernames class com.github.barteksc.pdfium.** {
    native <methods>;
}
-dontwarn com.github.barteksc.pdfium.**

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { *; }
-keep public enum com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions { *; }
-keep class com.bumptech.glide.** { *; }
-dontwarn com.bumptech.glide.**

# Coroutines
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Kotlin
-keepattributes Annotation, Signature, InnerClasses, EnclosingMethod
-dontwarn kotlin.**

# General
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-renamesourcefileattribute SourceFile
-ignorewarnings
