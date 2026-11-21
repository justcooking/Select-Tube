# Add project-specific ProGuard rules here.
# Keep NewPipe Extractor from being stripped
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**

# Keep BCrypt
-keep class at.favre.lib.crypto.bcrypt.** { *; }

# Keep Media3 service info
-keep class androidx.media3.** { *; }

# Keep nanojson for NewPipe Extractor runtime responses
-keep class com.grack.nanojson.** { *; }
-dontwarn com.grack.nanojson.**
