# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.learnpaper.**$$serializer { *; }
-keepclassmembers class com.learnpaper.** { *** Companion; }
-keepclasseswithmembers class com.learnpaper.** { kotlinx.serialization.KSerializer serializer(...); }
