# ==============================================================================
# GDC Library — ProGuard / R8 Rules
# ==============================================================================

# ── Keep Room entities ────────────────────────────────────────────────────────
-keep class com.college.library.data.model.** { *; }

# ── Keep Hilt-generated code ──────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * {
    public <init>(...);
}
-keep class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}
-keepclassmembers class * {
    @javax.inject.Inject <fields>;
}

# ── Keep WorkManager + Hilt Worker factory ────────────────────────────────────
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keepclassmembers class * implements androidx.work.Configuration$Provider {
    public androidx.work.Configuration getWorkManagerConfiguration();
}

# ── Keep Apache POI (Excel import) ────────────────────────────────────────────
-dontwarn org.apache.poi.**
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.commons.**
-keep class org.apache.commons.** { *; }

# ── Keep ML Kit & ZXing (barcode scanning) ────────────────────────────────────
-dontwarn com.google.mlkit.**
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.zxing.**
-keep class com.google.zxing.** { *; }

# ── Keep Vico Charts ─────────────────────────────────────────────────────────
-dontwarn com.patrykandpatrick.vico.**
-keep class com.patrykandpatrick.vico.** { *; }

# ── Kotlin Serialization / Coroutines ─────────────────────────────────────────
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.** { volatile <fields>; }

# ── General Android ───────────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ── Prevent stripping of data class toString / copy ───────────────────────────
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ── Keep XMLBeans (for modern .xlsx import) ───────────────────────────────────
-dontwarn org.apache.xmlbeans.**
-keep class org.apache.xmlbeans.** { *; }

# ── Keep Room Database and DAO Implementations ───────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class *__Impl { *; }

# ── Keep Hilt worker assisted factories and components ───────────────────────
-keep class *__AssistedFactory { *; }
-keep interface *__AssistedFactory { *; }
-keep @dagger.Module class * { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @dagger.assisted.AssistedInject <init>(...);
    @dagger.Provides <methods>;
    @dagger.Binds <methods>;
}

# ── Keep Google Play Services Code Scanner ────────────────────────────────────
-dontwarn com.google.android.gms.code.scanner.**
-keep class com.google.android.gms.code.scanner.** { *; }
