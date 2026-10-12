# Proguard rules — AIR KITA (release dengan R8 minify)

# Data layer (Room entities & DAOs)
-keep class com.daftech.airkita.data.** { *; }
-keep class com.daftech.airkita.petugas.data.** { *; }

# Room — keep generated implementations
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.**

# CameraX
-dontwarn androidx.camera.**
-keep class androidx.camera.** { *; }

# ML Kit
-dontwarn com.google.mlkit.**
-keep class com.google.mlkit.** { *; }

# ZXing
-dontwarn com.google.zxing.**
-keep class com.google.zxing.** { *; }

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**

# Compose (mostly handled by the plugin, but be safe)
-dontwarn androidx.compose.**

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
