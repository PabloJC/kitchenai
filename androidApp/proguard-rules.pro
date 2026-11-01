# Only what reflection or generated code reaches and R8 cannot see. Every library below ships
# its own consumer rules too (kotlinx.serialization, Room, Compose); these cover the rest.

# Firebase and GitLive: the native SDKs and the Kotlin wrapper look classes up by name.
-keep class com.google.firebase.** { *; }
-keep class dev.gitlive.firebase.** { *; }

# Koin: definitions are resolved by type at runtime.
-keep class org.koin.** { *; }

# kotlinx.serialization keeps its generated serializers through its own rules; stored keys are
# strings inside them, so renaming a field cannot change a document. Kept as it was before #375.
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
}
