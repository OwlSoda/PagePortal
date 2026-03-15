# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Keep Gson
-keepattributes Signature
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.google.gson.annotations.SerializedName { *; }

# Keep PagePortal models and entities for serialization/Room
-keep class com.owlsoda.pageportal.core.database.entity.** { *; }
-keep class com.owlsoda.pageportal.services.** { *; }
-keep class com.owlsoda.pageportal.services.storyteller.** { *; }
-keep class com.owlsoda.pageportal.services.audiobookshelf.** { *; }
-keep class com.owlsoda.pageportal.services.booklore.** { *; }

# Keep AppAuth
-keep class net.openid.appauth.** { *; }
-dontwarn net.openid.appauth.**

# Keep Hilt/Dagger (usually handled by AAR but just in case)
-keep class dagger.hilt.** { *; }

-dontwarn org.slf4j.**
