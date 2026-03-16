package net.portswigger.extension

import burp.api.montoya.BurpExtension
import burp.api.montoya.MontoyaApi
import net.portswigger.extension.providers.ClaudeDesktopProvider
import net.portswigger.extension.providers.ManualProxyInstallerProvider
import net.portswigger.extension.providers.ProxyJarManager
import net.portswigger.extension.ui.ConfigUi
import net.portswigger.mcp.BuildInfo
import net.portswigger.mcp.KtorServerManager
import net.portswigger.mcp.config.McpConfig

@Suppress("unused")
class ExtensionBase : BurpExtension {

    override fun initialize(api: MontoyaApi) {
        api.extension().setName(BuildInfo.DISPLAY_NAME)

        val config = McpConfig(api.persistence().extensionData(), api.logging())
        val serverManager = KtorServerManager(api)

        val proxyJarManager = ProxyJarManager(api.logging())

        val configUi = ConfigUi(
            config = config, providers = listOf(
                ClaudeDesktopProvider(api.logging(), proxyJarManager),
                ManualProxyInstallerProvider(api.logging(), proxyJarManager),
            )
        )

        configUi.onEnabledToggled { enabled ->
            configUi.getConfig()

            if (enabled) {
                serverManager.start(config) { state ->
                    configUi.updateServerState(state)
                }
            } else {
                serverManager.stop { state ->
                    configUi.updateServerState(state)
                }
            }
        }

        api.userInterface().registerSuiteTab("MCP", configUi.component)

        api.extension().registerUnloadingHandler {
            serverManager.shutdown()
            configUi.cleanup()
            config.cleanup()
        }

        if (config.enabled) {
            serverManager.start(config) { state ->
                configUi.updateServerState(state)
            }
        }
    }
}
