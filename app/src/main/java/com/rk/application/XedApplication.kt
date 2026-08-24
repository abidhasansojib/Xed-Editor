package com.rk.application

import com.rk.App
import com.rk.feature.FeatureRegistry
import com.rk.runner.RunnerFeature

class XedApplication : App() {
    override fun onCreate() {
        super.onCreate()

        // Register pluggable features
        FeatureRegistry.register(RunnerFeature())

        // Initialize features
        FeatureRegistry.initFeatures(this)
    }
}
