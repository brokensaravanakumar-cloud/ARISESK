package com.jinwoo.ai.core

/** Everything the assistant can do locally, before falling back to the AI model. */
sealed class Cmd {
    object Empty : Cmd()
    object Stop : Cmd()
    object Sleep : Cmd()
    data class OpenApp(val name: String) : Cmd()
    data class YouTubeSearch(val query: String, val play: Boolean) : Cmd()
    data class Call(val who: String) : Cmd()
    data class WhatsApp(val who: String, val body: String) : Cmd()
    data class Story(val request: String) : Cmd()
    data class ChromeSearch(val query: String) : Cmd()
    data class ChromeOpen(val url: String) : Cmd()
    data class InstallApp(val name: String) : Cmd()
    data class SetAlarm(val hour: Int, val minute: Int) : Cmd()
    data class SetTimer(val seconds: Int) : Cmd()
    object TellTime : Cmd()
    object TellDate : Cmd()
    data class CalcAnswer(val spoken: String, val result: String) : Cmd()
    object GmailOpen : Cmd()
    object GmailRead : Cmd()
    data class GmailCompose(val to: String, val subject: String, val body: String) : Cmd()
    data class BookTickets(val title: String) : Cmd()
    data class Tap(val target: String) : Cmd()
    data class Scroll(val down: Boolean) : Cmd()
    data class TypeText(val text: String) : Cmd()
    object PressEnter : Cmd()
    object Back : Cmd()
    object Home : Cmd()
    object ReadScreen : Cmd()
    data class Clarify(val question: String) : Cmd()
    data class Ask(val text: String) : Cmd()
}

/** Pure Kotlin (no Android deps) so it can be unit tested on a normal JVM. */
object CommandParser {

    fun normalize(raw: String): String {
        var t = raw.lowercase().replace("’", "'").trim()
        t = t.replace(Regex("[.?!,;]+$"), "").trim()
        t = t.replace(Regex("\\s+"), " ")
        t = t.replace(Regex("^(?:please|kindly|can you|could you|would you|will you|hey)\\s+"), "").trim()
        t = t.replace(Regex("\\s+please$"), "").trim()
        t = t.replace("book my show", "bookmyshow").replace("book my shows", "bookmyshow")
        return t
    }

    fun parse(raw: String, nowHour: Int = 12, nowMinute: Int = 0): Cmd {
        val t = normalize(raw)
        if (t.isEmpty()) return Cmd.Empty

        // --- control words ---
        if (t in setOf("stop", "stop talking", "be quiet", "quiet", "silence", "enough", "cancel")) return Cmd.Stop
        if (t in setOf("stop listening", "go to sleep", "sleep", "shut down", "shutdown", "power off", "goodbye", "good bye")) return Cmd.Sleep

        // --- alarms and timers ---
        Regex("^(?:set|create|make|add|start)?\\s*(?:an?\\s+)?alarm\\b(.*)$").find(t)?.let {
            val clock = TimeParse.parseClock(it.groupValues[1], nowHour, nowMinute)
            return if (clock != null) Cmd.SetAlarm(clock.first, clock.second)
            else Cmd.Clarify("What time should I set the alarm for, Master?")
        }
        Regex("^wake me(?: up)? (?:at|by|for) (.+)$").find(t)?.let {
            val clock = TimeParse.parseClock(it.groupValues[1], nowHour, nowMinute)
            return if (clock != null) Cmd.SetAlarm(clock.first, clock.second)
            else Cmd.Clarify("What time should I wake you, Master?")
        }
        Regex("^(?:set|start|create|make)?\\s*(?:an?\\s+)?timer\\b(.*)$").find(t)?.let {
            val secs = TimeParse.parseDurationSeconds(it.groupValues[1])
            return if (secs != null) Cmd.SetTimer(secs)
            else Cmd.Clarify("For how long should the timer run, Master?")
        }
        Regex("^(?:remind me|count down|countdown) (?:in|for) (.+)$").find(t)?.let {
            val secs = TimeParse.parseDurationSeconds(it.groupValues[1])
            if (secs != null) return Cmd.SetTimer(secs)
        }

        // --- date and time ---
        if (Regex("^(?:what(?:'s| is)(?: the)? (?:current )?time(?: now| is it)?|what time is it(?: now)?|tell me the time|current time|time now|whats the time|time please)$").matches(t)) return Cmd.TellTime
        if (Regex("^(?:what(?:'s| is)(?: the)?(?: today's| todays)? date(?: today)?|what day is it(?: today)?|what(?:'s| is) today|today's date|todays date|tell me the date|current date|what(?:'s| is) the day(?: today)?|whats the date)$").matches(t)) return Cmd.TellDate

        // --- screen control (only works when accessibility is enabled) ---
        if (Regex("^(?:go )?back$|^press back$|^navigate back$").matches(t)) return Cmd.Back
        if (Regex("^(?:go )?home(?: screen)?$|^go to (?:the )?home(?: screen)?$").matches(t)) return Cmd.Home
        if (Regex("^(?:read|what(?:'s| is) on|what do you see on)(?: the| my)? screen$|^what do you see$").matches(t)) return Cmd.ReadScreen
        if (Regex("^(?:scroll|swipe)(?: (down|up))?$").matches(t) || Regex("^(?:scroll|swipe) (down|up)").containsMatchIn(t)) {
            return Cmd.Scroll(!t.contains("up"))
        }
        if (Regex("^(?:press|hit|tap)?\\s*(?:enter|search|submit|go)$").matches(t)) return Cmd.PressEnter
        Regex("^(?:type|input|write in)\\s+(.+)$").find(t)?.let { return Cmd.TypeText(it.groupValues[1].trim()) }
        Regex("^(?:tap|click|press|select|choose|hit)\\s+(?:on\\s+)?(?:the\\s+)?(.+)$").find(t)?.let {
            return Cmd.Tap(it.groupValues[1].trim())
        }

        // --- stories (before "play ..." so "play a story" is not sent to YouTube) ---
        if (Regex("^(?:tell|read|narrate|say)\\s+(?:me\\s+)?(?:an?\\s+|the\\s+)?(?:\\w+\\s+){0,3}?(?:story|tale)\\b").containsMatchIn(t) ||
            Regex("^(?:play|start)\\s+(?:an?\\s+|the\\s+)?(?:\\w+\\s+){0,2}?story$").matches(t)
        ) return Cmd.Story(raw.trim())

        // --- WhatsApp message ---
        Regex("^(?:send\\s+)?(?:an?\\s+)?(?:whatsapp\\s+)?(?:message|msg|text)\\s+to\\s+(.+?)(?:\\s+on\\s+whatsapp)?\\s+(?:saying|say|that says|that)\\s+(.+)$").find(t)?.let {
            return Cmd.WhatsApp(it.groupValues[1].trim(), it.groupValues[2].trim())
        }
        Regex("^(?:open whatsapp and\\s+)?(?:whatsapp|message|text)\\s+(.+?)(?:\\s+on\\s+whatsapp)?\\s+(?:saying|that says|say)\\s+(.+)$").find(t)?.let {
            return Cmd.WhatsApp(it.groupValues[1].trim(), it.groupValues[2].trim())
        }
        Regex("^(?:send\\s+)?(?:an?\\s+)?(?:whatsapp\\s+)?(?:message|msg)\\s+to\\s+(.+?)(?:\\s+on\\s+whatsapp)?$").find(t)?.let {
            return Cmd.WhatsApp(it.groupValues[1].trim(), "")
        }
        Regex("^whatsapp\\s+(.+)$").find(t)?.let { return Cmd.WhatsApp(it.groupValues[1].trim(), "") }

        // --- phone calls ---
        Regex("^(?:make\\s+(?:a\\s+)?(?:phone\\s+)?call\\s+to|call|phone|dial|ring)\\s+(.+)$").find(t)?.let {
            return Cmd.Call(it.groupValues[1].trim().removePrefix("to ").trim())
        }

        // --- install apps (always routed through the Play Store) ---
        Regex("^(?:install|download)\\s+(?:the\\s+)?(.+?)(?:\\s+(?:app|application))?(?:\\s+(?:from|on|in|via|using|through)\\s+(?:the\\s+)?(?:google\\s+)?(?:play\\s?store|chrome|browser))?$")
            .find(t)?.let {
                val name = it.groupValues[1].trim()
                if (name.isNotEmpty() && !name.startsWith("http")) return Cmd.InstallApp(name)
            }

        // --- Gmail ---
        Regex("^(?:compose|write|draft|send)\\s+(?:an?\\s+)?(?:e-?mail|mail|gmail)\\s*(.*)$").find(t)?.let {
            val rest = it.groupValues[1].trim()
            return parseCompose(rest)
        }
        if (t in setOf("open gmail", "go to gmail", "launch gmail", "start gmail", "gmail")) return Cmd.GmailOpen
        if (Regex("\\b(?:e-?mails?|mails?|gmail|inbox)\\b").containsMatchIn(t) &&
            Regex("\\b(?:check|read|any|unread|new|latest|do i have|tell me|show)\\b").containsMatchIn(t)
        ) return Cmd.GmailRead

        // --- BookMyShow / movie tickets ---
        if (t.contains("bookmyshow") || (t.contains("ticket") && Regex("\\b(?:movie|film|show|cinema|book|buy|get)\\b").containsMatchIn(t))) {
            var title = ""
            Regex("(?:tickets?|shows?|movie)\\s+(?:for|of|to)\\s+(.+?)(?:\\s+(?:on|in|at|from|using|via)\\s+bookmyshow)?$").find(t)?.let {
                title = it.groupValues[1].trim()
            }
            if (title.isEmpty()) {
                Regex("^(?:book|buy|get)\\s+(?:me\\s+)?(?:a\\s+|the\\s+)?(?:movie\\s+)?tickets?\\s+(.+)$").find(t)?.let {
                    title = it.groupValues[1].replace(Regex("\\s+(?:on|in|using|via)\\s+bookmyshow$"), "").trim()
                }
            }
            return Cmd.BookTickets(title)
        }

        // --- YouTube: "play X" plays the first result, "search X on youtube" only shows results ---
        Regex("^(?:open youtube(?: and)?\\s+)?(?:play|put on)\\s+(.+?)(?:\\s+(?:on|in)\\s+youtube)?$").find(t)?.let {
            return Cmd.YouTubeSearch(it.groupValues[1].trim(), true)
        }
        Regex("^(?:search|find|look up)\\s+(.+?)\\s+(?:on|in)\\s+youtube$").find(t)?.let { return Cmd.YouTubeSearch(it.groupValues[1].trim(), false) }
        Regex("^(?:search|find|look up)\\s+youtube\\s+for\\s+(.+)$").find(t)?.let { return Cmd.YouTubeSearch(it.groupValues[1].trim(), false) }

        // --- Chrome ---
        Regex("^(?:open|go to|visit|launch)\\s+(?:the\\s+)?(?:website\\s+)?([a-z0-9][a-z0-9.\\- ]*?(?:\\.| dot )(?:com|in|org|net|io|co|app|ai|dev|edu|gov)(?:\\.[a-z]{2})?(?:/\\S*)?)(?:\\s+(?:in|on|using)\\s+chrome)?$")
            .find(t)?.let { return Cmd.ChromeOpen(spokenDomain(it.groupValues[1])) }
        Regex("^(?:search|google|look up|find)\\s+(?:for\\s+|the web for\\s+)?(.+?)(?:\\s+(?:in|on|using)\\s+(?:chrome|google))?$").find(t)?.let {
            val q = it.groupValues[1].trim()
            if (t.startsWith("google ") || t.contains("chrome") || t.startsWith("search")) {
                if (q.isNotEmpty()) return Cmd.ChromeSearch(q)
            }
        }

        // --- open an app ---
        Regex("^(?:open|launch|start|run|go to)\\s+(?:the\\s+)?(.+?)(?:\\s+app)?$").find(t)?.let {
            val name = it.groupValues[1].trim()
            if (name == "bookmyshow") return Cmd.BookTickets("")
            if (name.isNotEmpty()) return Cmd.OpenApp(name)
        }

        // --- maths ---
        val prefixed = Regex("^(?:calculate|compute|solve|what(?:'s| is)|whats|how much is|how many is|evaluate)\\s+(.+)$").find(t)
        val mathCandidate = prefixed?.groupValues?.get(1) ?: t
        if (prefixed != null || t.firstOrNull()?.isDigit() == true || t.startsWith("square root")) {
            val ans = Calc.answer(mathCandidate)
            if (ans != null) return Cmd.CalcAnswer(mathCandidate, ans)
        }

        return Cmd.Ask(raw.trim())
    }

    private fun parseCompose(rest: String): Cmd {
        var to = ""
        var subject = ""
        var body = ""
        val m1 = Regex("^to\\s+(.+?)\\s+(?:saying|say|that says|telling him|telling her|message)\\s+(.+)$").find(rest)
        val m2 = Regex("^to\\s+(.+?)\\s+(?:about|with subject|subject|regarding)\\s+(.+)$").find(rest)
        val m3 = Regex("^to\\s+(.+)$").find(rest)
        when {
            m1 != null -> { to = m1.groupValues[1]; body = m1.groupValues[2] }
            m2 != null -> { to = m2.groupValues[1]; subject = m2.groupValues[2] }
            m3 != null -> to = m3.groupValues[1]
        }
        return Cmd.GmailCompose(spokenEmail(to), subject.trim(), body.trim())
    }

    /** "john at gmail dot com" -> "john@gmail.com". Returns "" if it is not an email address (e.g. just a name). */
    fun spokenEmail(s: String): String {
        val t = s.trim().lowercase().replace(" at the rate of ", "@").replace(" at ", "@").replace(" dot ", ".")
        return if (t.contains("@")) t.replace(" ", "") else ""
    }

    fun spokenDomain(s: String): String {
        val t = s.trim().lowercase().replace(" dot ", ".").replace(" ", "")
        return if (t.startsWith("http")) t else "https://$t"
    }
}
