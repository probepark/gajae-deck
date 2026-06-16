package io.devnogari.gajaedeck.bridge

enum class CommandGroup {
    SESSION,
    MODEL,
    CONTROL,
    EXECUTION,
    MESSAGE_READ,
    EXPORT,
    HOST_TOOLS,
    HOST_URI,
    ADMIN,
    PROMPT,
}

fun BridgeScope.toCommandGroup(): CommandGroup = when (this) {
    BridgeScope.PROMPT -> CommandGroup.PROMPT
    BridgeScope.CONTROL -> CommandGroup.CONTROL
    BridgeScope.BASH -> CommandGroup.EXECUTION
    BridgeScope.EXPORT -> CommandGroup.EXPORT
    BridgeScope.SESSION -> CommandGroup.SESSION
    BridgeScope.MODEL -> CommandGroup.MODEL
    BridgeScope.MESSAGE_READ -> CommandGroup.MESSAGE_READ
    BridgeScope.HOST_TOOLS -> CommandGroup.HOST_TOOLS
    BridgeScope.HOST_URI -> CommandGroup.HOST_URI
    BridgeScope.ADMIN -> CommandGroup.ADMIN
}
