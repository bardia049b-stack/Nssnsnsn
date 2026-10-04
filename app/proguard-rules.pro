-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,AnnotationDefault,SourceFile,LineNumberTable

-keep class go.** { *; }
-keep class libv2ray.** { *; }
-keep class com.v2ray.ang.** { *; }

-keep class app.nebulabox.** { *; }

-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }

-dontwarn go.**
-dontwarn libv2ray.**
-dontwarn com.v2ray.ang.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn com.google.android.gms.**
-dontwarn com.google.mlkit.**
-dontwarn javax.annotation.**
-dontwarn kotlinx.**
-dontwarn java.lang.management.**

-optimizationpasses 5
