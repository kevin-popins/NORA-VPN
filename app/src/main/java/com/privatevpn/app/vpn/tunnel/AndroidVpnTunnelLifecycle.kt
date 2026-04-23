package com.privatevpn.app.vpn.tunnel

import com.privatevpn.app.vpn.VpnManager

class AndroidVpnTunnelLifecycle(
    private val vpnManager: VpnManager
) : VpnTunnelLifecycle {
    override fun connect(request: TunnelConnectRequest): Result<Unit> {
        return vpnManager.connect(request)
    }

    override fun disconnect(): Result<Unit> {
        return vpnManager.disconnect()
    }
}
