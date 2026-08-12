package com.puttvision.screen

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Non-blocking runtime cache for the expensive physics inverse solver.
 * UI/rendering code only peeks at completed results; settings changes schedule
 * work on a single background worker so the TV and phone stay responsive.
 */
object GreenReadRuntime {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "puttvision-green-read").apply { isDaemon = true }
    }
    private val ready = ConcurrentHashMap<GreenReadKey, GreenRead>()
    private val pending = ConcurrentHashMap.newKeySet<GreenReadKey>()

    fun prefetch(settings: GreenSettings) {
        val snapshot = settings.copy()
        val key = GreenReadAdvisor.key(snapshot)
        GreenReadAdvisor.peekCached(snapshot)?.let {
            ready[key] = it
            return
        }
        if (ready.containsKey(key) || !pending.add(key)) return
        executor.execute {
            try {
                ready[key] = GreenReadAdvisor.read(snapshot)
            } finally {
                pending.remove(key)
            }
        }
    }

    fun peek(settings: GreenSettings): GreenRead? {
        val key = GreenReadAdvisor.key(settings)
        return ready[key] ?: GreenReadAdvisor.peekCached(settings)?.also { ready[key] = it }
    }

    fun peekOrSchedule(settings: GreenSettings): GreenRead? {
        val cached = peek(settings)
        if (cached == null) prefetch(settings)
        return cached
    }

    fun isPending(settings: GreenSettings): Boolean = pending.contains(GreenReadAdvisor.key(settings))

    fun warm(settings: Iterable<GreenSettings>) {
        settings.forEach(::prefetch)
    }

    fun clearRuntimeCache() {
        ready.clear()
        pending.clear()
    }
}
