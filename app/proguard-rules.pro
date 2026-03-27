# Preservation for reflection and GSON
-dontshrink
-dontoptimize
-dontobfuscate
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
-keep class * extends androidx.room.TypeConverter
-keep class com.owlsoda.pageportal.core.database.dao.**_Impl { *; }

# PagePortal Core Models & Database Entities
-keep class com.owlsoda.pageportal.core.database.entity.** { *; }
-keep class com.owlsoda.pageportal.services.** { *; }
-keep class com.owlsoda.pageportal.services.storyteller.** { *; }
-keep class com.owlsoda.pageportal.services.audiobookshelf.** { *; }
-keep class com.owlsoda.pageportal.services.booklore.** { *; }
-keep class com.owlsoda.pageportal.network.** { *; }
-keep class com.owlsoda.pageportal.data.preferences.** { *; }
-keep class com.owlsoda.pageportal.data.importer.** { *; }

# PagePortal Features (Broad protection for UI models and states)
-keep class com.owlsoda.pageportal.features.** { *; }

# ViewModels (Explicitly keep all ViewModels and their constructors/members)
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Hilt/Dagger (Broad protection for injected classes and modules)
-keep class dagger.hilt.** { *; }
-keep class com.google.dagger.** { *; }
-dontwarn com.google.dagger.**
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep class * {
    @javax.inject.Inject <fields>;
    @javax.inject.Inject <init>(...);
}

# Ensure data class constructors and fields are kept specifically
-keepclassmembers class com.owlsoda.pageportal.** {
    @com.google.gson.annotations.SerializedName <fields>;
    <init>(...);
}

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

# Tink / EncryptedSharedPreferences (Google Crypto)
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
-dontwarn com.google.crypto.tink.util.KeysDownloader
-keep class com.google.crypto.tink.** { *; }
-keep class androidx.security.crypto.** { *; }
