package com.rk.application

import com.rk.App
import com.rk.TerminalFeature
import com.rk.feature.FeatureRegistry
import com.rk.runner.RunnerFeature

class XedApplication : App() {
    override fun onCreate() {
        super.onCreate()

        // Register pluggable features
        FeatureRegistry.register(RunnerFeature())
        FeatureRegistry.register(TerminalFeature())

        // Initialize features
        FeatureRegistry.initFeatures(this)
    }
}
