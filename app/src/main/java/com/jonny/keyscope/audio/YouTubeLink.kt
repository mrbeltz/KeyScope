package com.jonny.keyscope.audio

/**
 * Parsing for the links you can paste or share in from the YouTube app.
 *
 * Nothing here downloads anything. Extracting audio from YouTube is against their terms, so the
 * app plays the official embed out loud and reads it with the microphone like any other sound in
 * the room. That costs accuracy against the file path, and the UI says so.
 */
object YouTubeLink {

    private const val ID = "[A-Za-z0-9_-]{11}"

    private val PATTERNS = listOf(
        Regex("""youtu\.be/($ID)"""),
        Regex("""[?&]v=($ID)"""),
        Regex("""youtube\.com/embed/($ID)"""),
        Regex("""youtube\.com/shorts/($ID)"""),
        Regex("""youtube\.com/live/($ID)""")
    )

    /** Pulls a video id out of a pasted link, a shared link, or a bare id. */
    fun extractVideoId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.matches(Regex("^$ID$"))) return trimmed
        for (pattern in PATTERNS) {
            pattern.find(trimmed)?.let { return it.groupValues[1] }
        }
        return null
    }
}
