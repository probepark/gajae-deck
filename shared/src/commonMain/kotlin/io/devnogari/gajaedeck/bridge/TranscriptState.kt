package io.devnogari.gajaedeck.bridge

class TranscriptState(private val capacity: Int = 500) {
    private val items = ArrayDeque<TranscriptItem>()

    init {
        require(capacity >= 1)
    }

    val size: Int
        get() = items.size

    fun add(item: TranscriptItem) {
        items.addLast(item)
        while (items.size > capacity) {
            items.removeFirst()
        }
    }

    fun snapshot(): List<TranscriptItem> = items.toList()
}
