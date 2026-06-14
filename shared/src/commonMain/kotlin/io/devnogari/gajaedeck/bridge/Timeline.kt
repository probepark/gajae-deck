package io.devnogari.gajaedeck.bridge

/** How a frame was classified against the committed server-seq timeline. */
enum class FrameDisposition { APPLIED, DUPLICATE, GAP, RESET, UNSEQUENCED }

data class TimelineState(
    val lastSeq: Long = 0,
    val desynced: Boolean = false,
    val applied: Int = 0,
    val duplicates: Int = 0,
    val gaps: Int = 0,
)

/**
 * Reduces the event stream onto a committed timeline ordered solely by server `seq`.
 * - seq <= lastSeq: duplicate (ignored, retained in diagnostics)
 * - seq == lastSeq + 1: applied
 * - seq > lastSeq + 1: gap -> desynced until a reset/replay fills it
 * - reset (replay_window_exceeded): timeline cleared, resumes from reset marker
 */
class TimelineReducer(initial: TimelineState = TimelineState()) {
    var state: TimelineState = initial
        private set

    fun accept(frame: BridgeFrame): FrameDisposition {
        if (frame.type == BridgeFrameType.RESET) {
            state = TimelineState(lastSeq = frame.seq ?: 0, desynced = false)
            return FrameDisposition.RESET
        }
        val seq = frame.seq ?: return FrameDisposition.UNSEQUENCED
        return when {
            seq <= state.lastSeq -> {
                state = state.copy(duplicates = state.duplicates + 1)
                FrameDisposition.DUPLICATE
            }
            seq == state.lastSeq + 1 -> {
                state = state.copy(lastSeq = seq, applied = state.applied + 1, desynced = false)
                FrameDisposition.APPLIED
            }
            else -> {
                state = state.copy(lastSeq = seq, gaps = state.gaps + 1, desynced = true, applied = state.applied + 1)
                FrameDisposition.GAP
            }
        }
    }
}
