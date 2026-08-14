package com.puttvision.screen

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Non-blocking runtime cache for the expensive physics inverse solver.
 * UI/rendering code only peeks at completed results; settings changes schedule
 * work on a single background worker so the TV and phone stay responsive.
 * V13 adds a persistent solved-read layer so common greens survive app restarts.
 */
object GreenReadRuntime {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "puttvision-green-read").apply { isDaemon = true }
    }
    private val ready = ConcurrentHashMap<GreenReadKey, GreenRead>()
    private val pending = ConcurrentHashMap.newKeySet<GreenReadKey>()
    private val callbacks = ConcurrentHashMap<GreenReadKey, CopyOnWriteArrayList<(GreenRead) -> Unit>>()
    @Volatile private var diskCache: GreenReadDiskCache? = null

    fun install(context: Context) {
        if (diskCache == null) {
            synchronized(this) {
                if (diskCache == null) diskCache = GreenReadDiskCache(context.applicationContext)
            }
        }
        V31TrainingSessionRuntime.install(context)
        V33OnlineOutbox.install(context)
        V36OnlinePresenceRuntime.install(context)
    }

    fun prefetch(settings: GreenSettings) {
        val snapshot = settings.copy()
        val key = GreenReadAdvisor.key(snapshot)
        GreenReadAdvisor.peekCached(snapshot)?.let {
            ready[key] = it
            dispatch(key, it)
            return
        }
        ready[key]?.let {
            dispatch(key, it)
            return
        }
        diskCache?.get(key)?.let {
            ready[key] = it
            dispatch(key, it)
            return
        }
        if (!pending.add(key)) return
        executor.execute {
            try {
                val solved = GreenReadAdvisor.read(snapshot)
                ready[key] = solved
                runCatching { diskCache?.put(key, solved) }
                dispatch(key, solved)
            } finally {
                pending.remove(key)
            }
        }
    }

    /** Callback runs on the solver worker; Android callers should hop to main. */
    fun request(settings: GreenSettings, onReady: (GreenRead) -> Unit) {
        val snapshot = settings.copy()
        val key = GreenReadAdvisor.key(snapshot)
        peek(snapshot)?.let {
            onReady(it)
            return
        }
        callbacks.computeIfAbsent(key) { CopyOnWriteArrayList() }.add(onReady)
        peek(snapshot)?.let {
            dispatch(key, it)
            return
        }
        prefetch(snapshot)
    }

    fun peek(settings: GreenSettings): GreenRead? {
        val key = GreenReadAdvisor.key(settings)
        ready[key]?.let { return it }
        GreenReadAdvisor.peekCached(settings)?.let {
            ready[key] = it
            return it
        }
        diskCache?.get(key)?.let {
            ready[key] = it
            return it
        }
        return null
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

    private fun dispatch(key: GreenReadKey, read: GreenRead) {
        callbacks.remove(key)?.forEach { callback ->
            runCatching { callback(read) }
        }
    }

    fun clearRuntimeCache() {
        ready.clear()
        callbacks.clear()
    }

    fun clearPersistentCache() {
        clearRuntimeCache()
        runCatching { diskCache?.clear() }
    }
}
