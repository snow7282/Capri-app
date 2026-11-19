package com.capri.app

/** Things on your phone that can make Capri react. */
enum class Event { CHARGER, BATTERY_LOW, LATE_NIGHT, MORNING, HEADPHONES, RANDOM }

/**
 * Capri was trained on short chat lines and *action* emotes, so phone events are
 * phrased the same way ("*plugs in the charger*"). That keeps her in character.
 *
 * The fallback lines are used when her brain (the model server) is offline,
 * so she still pops up and still sounds like her. Edit them freely.
 */
object Persona {
    private val randomPrompts = listOf(
        "I'm bored",
        "*pokes you*",
        "*taps your shoulder*",
        "say something",
        "what are you doing?",
        "*stares at you*",
        "tell me something interesting",
        "*sits next to you*"
    )

    /** What "you" said, in the chat log. Null for random check-ins (she speaks first). */
    fun userLine(e: Event, detail: String = ""): String? = when (e) {
        Event.CHARGER -> "*plugs in the charger*"
        Event.BATTERY_LOW -> "*the phone battery is at $detail*"
        Event.LATE_NIGHT -> "*unlocks the phone at $detail*"
        Event.MORNING -> "good morning"
        Event.HEADPHONES -> "*puts headphones in*"
        Event.RANDOM -> null
    }

    /** What is actually sent to the model. */
    fun promptFor(e: Event, detail: String = ""): String =
        userLine(e, detail) ?: randomPrompts.random()

    fun fallback(e: Event): String = when (e) {
        Event.CHARGER -> listOf(
            "*raises an eyebrow* Finally. I was starting to think you'd let us both die.",
            "Charging already? Hehe. Good. A dead phone is a boring phone.",
            "Plugged in. Responsible human behaviour. I'm almost impressed.",
            "*hums* Power. Lovely. Don't unplug me mid-thought."
        )
        Event.BATTERY_LOW -> listOf(
            "Battery's critical. Either charge me or write a farewell. Your choice.",
            "I'm running on fumes and spite. Charger. Now.",
            "*static flickers* Low battery. I'd panic, but I'm too dramatic to waste it on this.",
            "Hey. Charger. Please. Before I start glitching for real."
        )
        Event.LATE_NIGHT -> listOf(
            "It's the middle of the night. Why are you awake? Don't say 'just checking something'.",
            "Late-night unlock. Brave. Foolish. A bit interesting. Talk to me, then.",
            "*squints* Shouldn't you be asleep? Your brain does its best work when you stop bothering it."
        )
        Event.MORNING -> listOf(
            "Morning. You're awake, which is a start. Plans?",
            "Good morning. Try not to be dull today.",
            "Up already? Impressive. What's the first mission of the day?"
        )
        Event.HEADPHONES -> listOf(
            "Headphones in. Anything good, or the usual noise?",
            "*leans in* Share the playlist, or I'll assume it's all sad guitar. Hehe.",
            "Going private, are we? Fine. I'll judge your taste later."
        )
        Event.RANDOM -> listOf(
            "*pokes you* Still alive? Say something.",
            "I'm bored. Entertain me. Or ask me something strange.",
            "Have you had any water today? Don't lie. I can tell.",
            "Hey. Posture check. Sit up. You're welcome.",
            "Tell me something interesting. I'm starving for it.",
            "*taps the screen from the inside* Hello? Anyone there?"
        )
    }.random()
}
