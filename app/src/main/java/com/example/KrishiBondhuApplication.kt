package com.example

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.ads.AdMonetizationManager
import java.lang.ref.WeakReference

class KrishiBondhuApplication : Application(), Application.ActivityLifecycleCallbacks {

    @Volatile
    private var currentActivityRef: WeakReference<Activity>? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val processName = getProcessName()
            if (processName != packageName) {
                runCatching {
                    val safeSuffix = processName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    WebView.setDataDirectorySuffix(safeSuffix)
                }
                return
            }
        }
        registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (AdMonetizationManager.isTestEnvironment()) return
                val activity = currentActivityRef?.get() ?: return
                AdMonetizationManager.maybeShowAppOpenOnForeground(activity)
            }
        })
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        currentActivityRef = WeakReference(activity)
        AdMonetizationManager.setCurrentActivity(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        currentActivityRef = WeakReference(activity)
        AdMonetizationManager.setCurrentActivity(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivityRef = WeakReference(activity)
        AdMonetizationManager.setCurrentActivity(activity)
    }

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivityRef?.get() === activity) {
            currentActivityRef = null
        }
        AdMonetizationManager.clearActivityIfMatches(activity)
    }
}
