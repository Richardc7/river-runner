package com.riverrunner.ui

import com.riverrunner.engine.HandRecord
import com.riverrunner.engine.SessionSummary

sealed class Screen {
    data object Home : Screen()
    data object Table : Screen()
    data class Result(val summary: SessionSummary) : Screen()
    data object History : Screen()
    data class Replay(val record: HandRecord, val blind: Boolean = false) : Screen()
    data object Stats : Screen()
    data object Settings : Screen()
    data object Appearance : Screen()
    data class EditionSettings(val showCashRules: Boolean = false) : Screen()
    data object Training : Screen()
    data object Guide : Screen()
    data object Saves : Screen()
    data object Achievements : Screen()
    data object Setup : Screen()
    data object TrainingSetup : Screen()
}
