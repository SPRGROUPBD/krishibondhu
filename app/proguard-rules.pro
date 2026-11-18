# KrishiBondhu (com.krishibondhu.bd) Release ProGuard / R8 Rules

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature,InnerClasses,EnclosingMethod

# Firebase Realtime Database & Auth models / snapshots
-keepclassmembers class com.example.data.local.** { *; }
-keepclassmembers class com.example.data.model.** { *; }
-keepclassmembers class * {
    @com.google.firebase.database.PropertyName <fields>;
    @com.google.firebase.database.PropertyName <methods>;
}

# Moshi JSON codegen & Retrofit interfaces
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
    @com.squareup.moshi.JsonClass *;
}
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class * extends retrofit2.CallBack
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# Unity LevelPlay & Approved Mediation Adapters (Zero AdMob)
-keep class com.unity3d.mediation.** { *; }
-keep class com.ironsource.** { *; }
-keepclassmembers class * implements com.ironsource.mediationsdk.sdk.InitializationListener { *; }
-keepclassmembers class * implements com.unity3d.mediation.LevelPlayInitListener { *; }
-keepclassmembers class * implements com.unity3d.mediation.impression.LevelPlayImpressionDataListener { *; }
-dontwarn com.unity3d.mediation.**
-dontwarn com.ironsource.**

# Unity Ads, AppLovin, Meta, Mintegral, Pangle, InMobi, Liftoff Vungle, DT Exchange, Chartboost, Start.io
-keep class com.unity3d.ads.** { *; }
-keep class com.unity3d.services.** { *; }
-keep class com.applovin.** { *; }
-keep class com.facebook.ads.** { *; }
-keep class com.mbridge.msdk.** { *; }
-keep class com.bytedance.sdk.openadsdk.** { *; }
-keep class com.inmobi.** { *; }
-keep class com.vungle.ads.** { *; }
-keep class com.fyber.inneractive.sdk.** { *; }
-keep class com.chartboost.** { *; }
-dontwarn com.chartboost.**
-keep class com.startapp.** { *; }
-dontwarn com.startapp.**
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# BuildConfig, Advertising ID, AppSet, Coroutines & DataStore
-keep class com.example.BuildConfig { *; }
-keep class com.google.android.gms.ads.identifier.** { *; }
-keep class com.google.android.gms.appset.** { *; }
-keep class com.google.firebase.database.** { *; }
-dontwarn com.google.firebase.**
-keepclassnames class kotlinx.coroutines.android.AndroidExceptionPreHandler
-keepclassnames class kotlinx.coroutines.android.AndroidDispatcherFactory
-dontwarn kotlinx.coroutines.**
-keepclassmembers class androidx.datastore.** { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
