# Keep Room entities & sync models
-keep class com.derycode.deryaccount.data.local.entity.** { *; }
-keep class com.derycode.deryaccount.data.remote.** { *; }
-keep class org.json.** { *; }
# Retrofit/OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
