# Preservation for reflection and GSON
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, SourceFile, LineNumberTable
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# Retrofit 2
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keepclassmembers interface * {
    @retrofit2.http.* <methods>;
}

# Gson
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.google.gson.annotations.SerializedName { *; }

# Room
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase
-keep class * implements androidx.room.Entity
-keep class * implements androidx.room.Dao

# PagePortal Data Models (Protect from stripping and obfuscation)
# We use allowoptimization but explicitly keep fields and constructors
-keep,allowoptimization class com.owlsoda.pageportal.core.database.entity.** { *; }
-keep,allowoptimization class com.owlsoda.pageportal.services.** { *; }
-keep,allowoptimization class com.owlsoda.pageportal.services.storyteller.** { *; }
-keep,allowoptimization class com.owlsoda.pageportal.services.audiobookshelf.** { *; }
-keep,allowoptimization class com.owlsoda.pageportal.services.booklore.** { *; }
-keep,allowoptimization class com.owlsoda.pageportal.network.** { *; }
-keep,allowoptimization class com.owlsoda.pageportal.data.preferences.** { *; }
-keep,allowoptimization class com.owlsoda.pageportal.data.importer.** { *; }

# Ensure data class constructors and fields are kept specifically
-keepclassmembers class com.owlsoda.pageportal.** {
    @com.google.gson.annotations.SerializedName <fields>;
    <init>(...);
}

# Keep the models themselves even if not explicitly referenced in code
-keep class com.owlsoda.pageportal.services.storyteller.* { *; }
-keep class com.owlsoda.pageportal.network.GitHub* { *; }

# Hilt/Dagger
-keep class dagger.hilt.** { *; }
-keep class com.google.dagger.** { *; }
-dontwarn com.google.dagger.**

# Logging
-dontwarn org.slf4j.**
-dontwarn com.squareup.okhttp3.logging.**
-keep class okhttp3.logging.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherLoader { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }
-keepclassmembernames class kotlinx.coroutines.android.HandlerContext {
    val handler;
}
