# PDF Toolkit ProGuard / R8 Rules

# Keep Kotlin Serialization metadata & generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.Annotations**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.example.**$$serializer { *; }
-keepclassmembers class com.example.** { *** Companion; }

# Keep Room Entities & DAOs
-keep class com.example.core.database.** { *; }

# PdfBox-Android & BouncyCastle
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }

# Apache POI, XmlBeans, Aalto XML, Commons Compress
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.apache.commons.**
-dontwarn com.fasterxml.aalto.**
-dontwarn javax.xml.**
-dontwarn java.awt.**
-dontwarn org.openxmlformats.schemas.**
-keep class org.openxmlformats.schemas.** { *; }
-keep class org.apache.xmlbeans.** { *; }

# ML Kit on-device modules
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# Jetpack Glance
-keep class com.example.system.PdfToolkitWidgetReceiver { *; }
-keep class com.example.system.PdfToolkitGlanceWidget { *; }
