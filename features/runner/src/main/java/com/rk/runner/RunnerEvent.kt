package com.rk.runner

import com.rk.events.Event

sealed interface RunnerEvent : Event {

    data class RunnerRun(val runner: Runner) : RunnerEvent
}
