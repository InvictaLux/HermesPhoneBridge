# Proguard rules for Hermes Phone Bridge.
# Keep standard OkHttp and Gson descriptors.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
