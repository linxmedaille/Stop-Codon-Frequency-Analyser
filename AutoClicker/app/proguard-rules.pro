# kotlinx.serialization keeps generated serializers for @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.tapflow.core.** {
    *** Companion;
}
-keepclasseswithmembers class com.tapflow.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.tapflow.core.**$$serializer { *; }
