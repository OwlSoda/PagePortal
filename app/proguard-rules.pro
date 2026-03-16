# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preserve all generic signatures for reflection
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, EnclosingMethod, SourceFile, LineNumberTable
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# Retrofit 2
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers interface * {
    @retrofit2.http.* <methods>;
}

# Kotlin Coroutines - Fix for suspend functions in Retrofit
-keepnames class kotlinx.coroutines.internal.MainDispatcherLoader { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }
-keepclassmembernames class kotlinx.coroutines.android.HandlerContext {
    val handler;
}

# Retrofit 2
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Gson
-keepattributes Signature
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.google.gson.annotations.SerializedName { *; }

# Room
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# PagePortal Core Models & Database Entities
-keep class com.owlsoda.pageportal.core.database.entity.** { *; }
-keep class com.owlsoda.pageportal.core.model.** { *; }
-keep class com.owlsoda.pageportal.services.** { *; }
-keep class com.owlsoda.pageportal.services.storyteller.** { *; }
-keep class com.owlsoda.pageportal.services.audiobookshelf.** { *; }
-keep class com.owlsoda.pageportal.services.booklore.** { *; }

# AppAuth
-keep class net.openid.appauth.** { *; }
-dontwarn net.openid.appauth.**

# Hilt/Dagger
-keep class dagger.hilt.** { *; }
-keep class com.google.dagger.** { *; }
-dontwarn com.google.dagger.**

# Logging
-dontwarn org.slf4j.**
-dontwarn com.squareup.okhttp3.logging.**
-keep class okhttp3.logging.** { *; }
