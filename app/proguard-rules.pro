# Add project-specific ProGuard rules here.
# Keep NewPipe Extractor from being stripped
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

# Keep BCrypt
-keep class at.favre.lib.crypto.bcrypt.** { *; }

# Keep Media3 service info
-keep class androidx.media3.** { *; }
