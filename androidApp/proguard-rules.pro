# Firebase / GitLive
-keep class com.google.firebase.** { *; }
-keep class dev.gitlive.firebase.** { *; }

# Koin
-keep class org.koin.** { *; }

# Modelos serializables de kotlinx
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
}
