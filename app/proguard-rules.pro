# ProGuard / R8 Rules for LedgerMate

# Preserve attributes for reflection, annotations, and debugging
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# Kotlin Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Moshi JSON Codegen & Data Models
-keep class com.example.data.** { *; }
-keepclassmembers class com.example.data.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
    @com.squareup.moshi.JsonClass *;
}

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclassmembers enum * { *; }

# Firebase / Firestore
-dontwarn com.google.firebase.**
-keepclassmembers class * {
    @com.google.firebase.firestore.** *;
}

