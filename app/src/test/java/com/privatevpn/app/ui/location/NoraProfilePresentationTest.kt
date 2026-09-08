package com.privatevpn.app.ui.location

import com.privatevpn.app.profiles.importer.ProfileImportParser
import com.privatevpn.app.profiles.model.VpnProfile
import com.privatevpn.app.profiles.repository.toDomain
import com.privatevpn.app.profiles.repository.toEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class NoraProfilePresentationTest {
    @Test
    fun `imported and reloaded Antarctica presentation leaves identity and transport intact`() {
        val source = """
            {
              "remarks": "NORA 🇸🇴 Антарктида 01",
              "Country": "Somalia",
              "dns": {"servers": ["1.1.1.1"]},
              "inbounds": [],
              "outbounds": [{"protocol": "freedom", "tag": "direct"}]
            }
        """.trimIndent()
        val parsed = ProfileImportParser().parse(source)
        val profile = VpnProfile(
            id = "persisted-test-profile",
            displayName = parsed.displayName,
            type = parsed.type,
            sourceRaw = parsed.sourceRaw,
            normalizedJson = parsed.normalizedJson,
            dnsServers = parsed.dnsServers,
            dnsFallbackApplied = parsed.dnsFallbackApplied,
            isPartialImport = parsed.isPartialImport,
            importWarnings = parsed.importWarnings,
            importedAtMs = 123L,
            parentSubscriptionId = "test-subscription",
            sourceOrder = 7
        )
        val selectedProfileId = profile.id
        val reloaded = profile.toEntity().toDomain()

        listOf(profile, reloaded).forEach {
            assertEquals("AQ", resolveNoraRegion(it.displayName)?.isoCode)
            assertEquals("NORA 🇦🇶 Антарктида 01", noraProfileDisplayName(it.displayName))
            assertEquals("NORA 🇸🇴 Антарктида 01", it.displayName)
            assertEquals(source, it.sourceRaw)
            assertEquals(parsed.normalizedJson, it.normalizedJson)
            assertEquals(selectedProfileId, it.id)
        }
        assertEquals(profile, reloaded)
        assertEquals(parsed, ProfileImportParser().parse(source))
    }
}
