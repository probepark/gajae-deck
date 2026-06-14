package io.devnogari.gajaedeck.bridge

/** A bridge command type and the scope it requires (frozen registry, see protocol-v2.md). */
data class BridgeCommand(val type: String, val scope: BridgeScope)

/** The authoritative gjc Bridge v2 command catalog (37 commands) with required scopes. */
object CommandCatalog {
    val commands: List<BridgeCommand> = listOf(
        BridgeCommand("prompt", BridgeScope.PROMPT),
        BridgeCommand("steer", BridgeScope.PROMPT),
        BridgeCommand("follow_up", BridgeScope.PROMPT),
        BridgeCommand("abort", BridgeScope.PROMPT),
        BridgeCommand("abort_and_prompt", BridgeScope.PROMPT),
        BridgeCommand("new_session", BridgeScope.SESSION),
        BridgeCommand("get_state", BridgeScope.MESSAGE_READ),
        BridgeCommand("set_todos", BridgeScope.CONTROL),
        BridgeCommand("set_host_tools", BridgeScope.HOST_TOOLS),
        BridgeCommand("set_host_uri_schemes", BridgeScope.HOST_URI),
        BridgeCommand("set_model", BridgeScope.MODEL),
        BridgeCommand("cycle_model", BridgeScope.MODEL),
        BridgeCommand("get_available_models", BridgeScope.MODEL),
        BridgeCommand("set_thinking_level", BridgeScope.MODEL),
        BridgeCommand("cycle_thinking_level", BridgeScope.MODEL),
        BridgeCommand("set_steering_mode", BridgeScope.CONTROL),
        BridgeCommand("set_follow_up_mode", BridgeScope.CONTROL),
        BridgeCommand("set_interrupt_mode", BridgeScope.CONTROL),
        BridgeCommand("compact", BridgeScope.CONTROL),
        BridgeCommand("set_auto_compaction", BridgeScope.CONTROL),
        BridgeCommand("set_auto_retry", BridgeScope.CONTROL),
        BridgeCommand("abort_retry", BridgeScope.CONTROL),
        BridgeCommand("bash", BridgeScope.BASH),
        BridgeCommand("abort_bash", BridgeScope.BASH),
        BridgeCommand("get_session_stats", BridgeScope.MESSAGE_READ),
        BridgeCommand("export_html", BridgeScope.EXPORT),
        BridgeCommand("switch_session", BridgeScope.SESSION),
        BridgeCommand("branch", BridgeScope.SESSION),
        BridgeCommand("get_branch_messages", BridgeScope.SESSION),
        BridgeCommand("get_last_assistant_text", BridgeScope.MESSAGE_READ),
        BridgeCommand("set_session_name", BridgeScope.SESSION),
        BridgeCommand("handoff", BridgeScope.ADMIN),
        BridgeCommand("get_messages", BridgeScope.MESSAGE_READ),
        BridgeCommand("get_login_providers", BridgeScope.ADMIN),
        BridgeCommand("login", BridgeScope.ADMIN),
        BridgeCommand("negotiate_unattended", BridgeScope.CONTROL),
        BridgeCommand("workflow_gate_response", BridgeScope.PROMPT),
    )

    private val byType: Map<String, BridgeCommand> = commands.associateBy { it.type }

    val types: List<String> get() = commands.map { it.type }

    fun scopeFor(type: String): BridgeScope? = byType[type]?.scope

    fun isKnown(type: String): Boolean = byType.containsKey(type)

    /** Whether a command [type] is permitted given the [granted] scopes. */
    fun isAllowed(type: String, granted: Set<BridgeScope>): Boolean {
        val required = scopeFor(type) ?: return false
        return required in granted
    }
}
