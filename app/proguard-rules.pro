# Readable stack traces from a release build, without leaking the original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------- kotlinx.serialization
# The whole cookbook is stored through generated serializers. R8 cannot see that the
# Companion objects are reachable, so without these the app starts with an empty library.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-if @kotlinx.serialization.Serializable class *
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class * {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class * {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# ------------------------------------------------------------------------------- jsoup
# jsoup pulls in optional NodeVisitor/SAX pieces that are not on Android.
-dontwarn org.jsoup.**
