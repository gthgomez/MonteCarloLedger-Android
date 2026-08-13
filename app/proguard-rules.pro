# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keepclassmembers class * {
    @androidx.room.* <fields>;
}
-dontwarn androidx.room.paging.**

# --- Compose ---
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# --- Kotlin serialization (future-proofing) ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# --- ViewModels ---
-keep class * extends androidx.lifecycle.ViewModel { *; }

# --- AndroidX ---
-keep class androidx.lifecycle.** { *; }
-keep class androidx.room.** { *; }

# --- General Android ---
-keepattributes Signature
-keepattributes Exceptions
-keep class com.montecarlo.ledger.data.** { *; }

# --- Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
