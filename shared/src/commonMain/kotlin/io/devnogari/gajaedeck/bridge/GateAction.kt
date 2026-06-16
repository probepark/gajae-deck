package io.devnogari.gajaedeck.bridge

enum class GateActionStyle { PRIMARY, NEUTRAL, DESTRUCTIVE }

data class GateAction(val id: String, val label: String, val style: GateActionStyle)
