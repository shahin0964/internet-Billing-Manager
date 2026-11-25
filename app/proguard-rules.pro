# Add project specific ProGuard rules here.

# Keep Room database and DAO classes
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep data models and entities
-keep class com.example.data.model.** { *; }
-keep class com.example.data.dao.** { *; }
-keep class com.example.data.database.** { *; }
-keep class com.example.data.remote.** { *; }

# Keep Moshi & Gson serialization
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-dontwarn sun.misc.**
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep WorkManager workers
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
