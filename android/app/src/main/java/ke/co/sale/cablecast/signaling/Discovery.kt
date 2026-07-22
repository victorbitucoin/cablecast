package ke.co.sale.cablecast.signaling

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

/** Advertises the TV receiver as _cablecast._tcp so PCs/phones auto-discover it on Wi-Fi. */
class Discovery(context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var listener: NsdManager.RegistrationListener? = null

    fun advertise(name: String, port: Int = 47800) {
        val info = NsdServiceInfo().apply {
            serviceName = name
            serviceType = "_cablecast._tcp."
            setPort(port)
        }
        listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(s: NsdServiceInfo) {}
            override fun onRegistrationFailed(s: NsdServiceInfo, e: Int) {}
            override fun onServiceUnregistered(s: NsdServiceInfo) {}
            override fun onUnregistrationFailed(s: NsdServiceInfo, e: Int) {}
        }
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stop() { listener?.let { runCatching { nsd.unregisterService(it) } }; listener = null }
}
