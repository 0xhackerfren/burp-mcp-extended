package net.portswigger.mcp.security

import burp.api.montoya.MontoyaApi
import net.portswigger.extension.ui.Dialogs
import net.portswigger.mcp.config.McpConfig
import javax.swing.SwingUtilities
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

interface ScannerApprovalHandler {
    suspend fun requestScanApproval(operationDescription: String, config: McpConfig): Boolean
}

class SwingScannerApprovalHandler : ScannerApprovalHandler {
    override suspend fun requestScanApproval(
        operationDescription: String, config: McpConfig
    ): Boolean {
        return suspendCoroutine { continuation ->
            SwingUtilities.invokeLater {
                val message = buildString {
                    appendLine("An MCP client is requesting to perform a scanner operation:")
                    appendLine()
                    appendLine(operationDescription)
                    appendLine()
                    appendLine("This will actively scan the target. Choose how to respond:")
                }

                val options = arrayOf(
                    "Allow Once", "Always Allow Scanner", "Deny"
                )

                val burpFrame = findBurpFrame()

                val result = Dialogs.showOptionDialog(
                    burpFrame, message, options
                )

                when (result) {
                    0 -> continuation.resume(true)
                    1 -> {
                        config.alwaysAllowScanner = true
                        continuation.resume(true)
                    }
                    else -> continuation.resume(false)
                }
            }
        }
    }
}

object ScannerSecurity {

    var approvalHandler: ScannerApprovalHandler = SwingScannerApprovalHandler()

    suspend fun checkScanPermission(
        operationDescription: String, config: McpConfig, api: MontoyaApi
    ): Boolean {
        if (!config.requireScannerApproval) {
            return true
        }

        if (config.alwaysAllowScanner) {
            api.logging().logToOutput("MCP scanner operation auto-approved")
            return true
        }

        return approvalHandler.requestScanApproval(operationDescription, config)
    }
}
