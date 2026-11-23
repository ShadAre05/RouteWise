# kotlinx.serialization keeps generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.routewise.app.**$$serializer { *; }
-keepclassmembers class com.routewise.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.routewise.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
