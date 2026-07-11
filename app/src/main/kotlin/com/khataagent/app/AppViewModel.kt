package com.khataagent.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.khataagent.di.AppContainer

/**
 * App-lifetime holder (survives config changes). Phase 2: the fakes are gone from the local loop —
 * [AppContainer] exposes the real Room repository + AgentOrchestrator (Gemma on-device). Escalation
 * and connectivity remain on the container's fakes for the Insights demo.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val container = AppContainer(app, viewModelScope)

    val repository = container.repository
    val orchestrator = container.orchestrator
    val audioRecorder = container.audioRecorder
    val voiceAvailable = container.voiceAvailable
    val cloudAvailable = container.cloudAvailable
    val backendLabel = container.backendLabel

    val connectivity = container.connectivity
    val escalationClient = container.escalationClient
    val statusController = container.statusController
}
