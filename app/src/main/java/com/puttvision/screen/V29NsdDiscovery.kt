package com.puttvision.screen

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap


data class V29DiscoveredHost(
    val serviceName: String,
    val host: String,
    val port: Int,
    val pairCode: String
) {
    val label: String get() = "$serviceName · $host:$port · PAIR $pairCode"
}

object V29NsdRuntime {
    private const val TYPE = "_puttvision._tcp."
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var manager: NsdManager? = null
    @Volatile private var registration: NsdManager.RegistrationListener? = null

    @Synchronized
    fun advertise(context: Context, pairCode: String, port: Int): Boolean {
        stopAdvertising()
        val nsd = context.applicationContext.getSystemService(NsdManager::class.java) ?: return false
        val info = NsdServiceInfo().apply {
            serviceName = "PuttVision-${Build.MODEL.take(18).replace(' ', '-')}"
            serviceType = TYPE
            this.port = port
            setAttribute("pair", pairCode)
            setAttribute("pv", "29")
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
        }
        return runCatching {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            manager = nsd
            registration = listener
            true
        }.getOrDefault(false)
    }

    @Synchronized
    fun stopAdvertising() {
        val nsd = manager
        val listener = registration
        if (nsd != null && listener != null) runCatching { nsd.unregisterService(listener) }
        registration = null
        manager = null
    }

    fun discover(context: Context, durationMs: Long = 2600L, onDone: (List<V29DiscoveredHost>) -> Unit) {
        val nsd = context.applicationContext.getSystemService(NsdManager::class.java)
        if (nsd == null) { onDone(emptyList()); return }
        val found = ConcurrentHashMap<String, V29DiscoveredHost>()
        var finished = false
        lateinit var listener: NsdManager.DiscoveryListener

        fun complete() {
            if (finished) return
            finished = true
            runCatching { nsd.stopServiceDiscovery(listener) }
            onDone(found.values.sortedBy { it.serviceName })
        }

        listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = complete()
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceLost(serviceInfo: NsdServiceInfo) { found.remove(serviceInfo.serviceName) }
            @Suppress("DEPRECATION")
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.startsWith("_puttvision._tcp")) return
                runCatching {
                    nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            val address = if (Build.VERSION.SDK_INT >= 34) {
                                resolved.hostAddresses.firstOrNull()?.hostAddress
                            } else {
                                @Suppress("DEPRECATION") resolved.host?.hostAddress
                            } ?: return
                            val pair = resolved.attributes["pair"]?.toString(Charsets.UTF_8)?.trim()?.uppercase() ?: return
                            if (pair.length < 6 || resolved.port <= 0) return
                            found[resolved.serviceName] = V29DiscoveredHost(resolved.serviceName, address, resolved.port, pair)
                        }
                    })
                }
            }
        }

        runCatching { nsd.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { complete() }
        main.postDelayed({ complete() }, durationMs.coerceIn(1000L, 6000L))
    }
}
