package io.devnogari.gajaedeck.bridge

enum class FieldKind { TEXT, MULTILINE, BOOLEAN, ENUM, NUMBER, JSON }

data class FieldDescriptor(
    val name: String,
    val label: String,
    val kind: FieldKind,
    val required: Boolean = false,
    val options: List<String> = emptyList(),
    val placeholder: String? = null,
)
