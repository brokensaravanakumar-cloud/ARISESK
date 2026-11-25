package com.jinwoo.ai.core

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import com.jinwoo.ai.access.JinAccessibilityService
import com.jinwoo.ai.ai.AiClient
import com.jinwoo.ai.ai.AiError
import com.jinwoo.ai.ai.ChatMsg
import com.jinwoo.ai.data.Prefs
import com.jinwoo.ai.gmail.GmailAuth
import com.jinwoo.ai.gmail.GmailClient
import com.jinwoo.ai.gmail.GmailToken
import kotlinx.coroutines.delay
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

sealed class Outcome {
    data class Say(val text: String) : Outcome()
    /** Ask the user first; [action] runs only if they say yes, and returns what to speak afterwards. */
    class Confirm(val question: String, val action: suspend () -> String) : Outcome()
}

/** Turns a parsed command into a real phone action (or an AI answer). Runs inside the listening service. */
class Executor(
    private val ctx: Context,
    private val prefs: Prefs,
    private val ai: AiClient
) {
    private var controlUntil = 0L
    private val recent = ArrayList<ChatMsg>()

    private fun say(t: String): Outcome = Outcome.Say(t)
    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    suspend fun run(cmd: Cmd): Outcome = when (cmd) {
        is Cmd.Empty -> say("I am here, Master. What is your command?")
        is Cmd.Clarify -> say(cmd.question)
        is Cmd.TellTime -> say("It is " + LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a")) + ", Master.")
        is Cmd.TellDate -> say("Today is " + LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")) + ".")
        is Cmd.CalcAnswer -> say("${cmd.spoken} is ${cmd.result}.")
        is Cmd.SetAlarm -> say(setAlarm(cmd.hour, cmd.minute))
        is Cmd.SetTimer -> say(setTimer(cmd.seconds))
        is Cmd.OpenApp -> say(openApp(cmd.name))
        is Cmd.YouTubeSearch -> say(youtube(cmd.query, cmd.play))
        is Cmd.Call -> callPerson(cmd.who)
        is Cmd.WhatsApp -> whatsapp(cmd.who, cmd.body)
        is Cmd.Story -> ask(cmd.request, story = true)
        is Cmd.ChromeSearch -> say(
            launchView("https://www.google.com/search?q=" + enc(cmd.query), "com.android.chrome",
                "Searching Chrome for ${cmd.query}.", "Search ${cmd.query}")
        )
        is Cmd.ChromeOpen -> say(
            launchView(cmd.url, "com.android.chrome", "Opening ${cmd.url.removePrefix("https://")} in Chrome.", "Open website")
        )
        is Cmd.InstallApp -> sensitive("Shall I install ${cmd.name} from the Play Store, Master?") { installFlow(cmd.name) }
        is Cmd.GmailOpen -> say(openGmail())
        is Cmd.GmailRead -> gmailRead()
        is Cmd.GmailCompose -> {
            val who = if (cmd.to.isNotBlank()) " to ${cmd.to}" else ""
            sensitive("Shall I open a Gmail draft$who, Master? You will review it before anything is sent.") { composeEmail(cmd) }
        }
        is Cmd.BookTickets -> say(bookTickets(cmd.title))
        is Cmd.Tap -> screen { s ->
            when (s.tapText(cmd.target)) {
                JinAccessibilityService.TapResult.OK -> "Done, Master."
                JinAccessibilityService.TapResult.NOT_FOUND -> "I cannot see ${cmd.target} on the screen, Master."
                JinAccessibilityService.TapResult.BLOCKED ->
                    "That control involves payment, a purchase or account safety. I will not touch it, Master. Please do that step yourself."
                JinAccessibilityService.TapResult.WRONG_APP ->
                    "I only operate the Play Store, Chrome, Gmail, BookMyShow, WhatsApp and YouTube, and call people from the contacts,."
                JinAccessibilityService.TapResult.NO_ACCESS -> "I cannot read the screen right now."
            }
        }
        is Cmd.TypeText -> screen { s ->
            when (s.typeText(cmd.text)) {
                JinAccessibilityService.TypeResult.OK -> "Typed."
                JinAccessibilityService.TypeResult.NO_FIELD -> "I could not find a text box to type in, Master."
                JinAccessibilityService.TypeResult.SENSITIVE ->
                    "That looks like a password, PIN or OTP field. I never type into those, Master."
                JinAccessibilityService.TypeResult.NO_ACCESS -> "I cannot read the screen right now."
            }
        }
        is Cmd.PressEnter -> screen { s -> if (s.pressEnter()) "Done." else "I could not press enter, Master." }
        is Cmd.Scroll -> withService { s -> if (s.scroll(cmd.down)) "Done." else "Nothing to scroll here, Master." }
        is Cmd.Back -> withService { s -> if (s.back()) "Going back." else "I could not go back." }
        is Cmd.Home -> withService { s -> if (s.home()) "Going home." else "I could not go home." }
        is Cmd.ReadScreen -> withService { s ->
            val t = s.readScreen()
            if (t.isBlank()) "I can only read the Play Store, Chrome, Gmail, BookMyShow, WhatsApp and YouTube screens, Master."
            else "On the screen I see: $t"
        }
        is Cmd.Ask -> ask(cmd.text)
        else -> say("Understood.")
    }

    // ------------------------------------------------------------------ confirmation helpers

    private suspend fun sensitive(question: String, action: suspend () -> String): Outcome =
        if (prefs.confirmSensitive) Outcome.Confirm(question, action) else Outcome.Say(action())

    private fun noScreenControl(): Outcome =
        say("Screen control is switched off, Master. Enable JIN WOO in Accessibility settings, under Phone control.")

    private suspend fun withService(action: suspend (JinAccessibilityService) -> String): Outcome {
        val svc = JinAccessibilityService.instance ?: return noScreenControl()
        return say(action(svc))
    }

    /** Operating other apps needs one spoken "yes" that lasts five minutes. */
    private suspend fun screen(action: suspend (JinAccessibilityService) -> String): Outcome {
        val svc = JinAccessibilityService.instance ?: return noScreenControl()
        if (!prefs.confirmSensitive || System.currentTimeMillis() < controlUntil) return say(action(svc))
        return Outcome.Confirm(
            "May I operate your screen for the next five minutes, Master? Payment buttons and password fields stay locked."
        ) {
            controlUntil = System.currentTimeMillis() + 5 * 60_000L
            val s = JinAccessibilityService.instance
            if (s == null) "Screen control was switched off." else action(s)
        }
    }

    // ------------------------------------------------------------------ simple phone actions

    private fun viewIntent(url: String, preferredPackage: String?): Intent {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (preferredPackage != null && AppLauncher.isInstalled(ctx, preferredPackage)) i.setPackage(preferredPackage)
        return i
    }

    private fun launchView(url: String, pkg: String?, okText: String, label: String): String {
        val started = Launch.start(ctx, viewIntent(url, pkg), label)
        return if (started) okText else notifyHint()
    }

    private fun notifyHint(): String =
        "Master, Android blocks me from opening apps while the phone is idle. Tap the notification to continue, " +
            "or allow Display over other apps in Settings, Phone control."

    private suspend fun youtube(query: String, play: Boolean): String {
        val started = Launch.start(
            ctx, viewIntent("https://www.youtube.com/results?search_query=" + enc(query), "com.google.android.youtube"), "Open YouTube"
        )
        if (!started) return notifyHint()
        if (!play) return "Showing YouTube results for $query."
        val svc = JinAccessibilityService.instance
            ?: return "YouTube is showing results for $query. Turn on screen control and I will press play for you next time."
        delay(3500)
        for (i in 0 until 6) {
            val s = JinAccessibilityService.instance ?: break
            if (s.tapFirstContaining("play video")) return "Playing $query."
            delay(1000)
        }
        return "I opened YouTube results for $query but could not press play. Say tap, then the video title."
    }

    // ------------------------------------------------------------------ calls and WhatsApp

    private suspend fun callPerson(who: String): Outcome {
        val hit = Contacts.find(ctx, who)
            ?: return say(
                if (!Contacts.hasPermission(ctx) && who.any { it.isLetter() })
                    "I need the Contacts permission to find $who, Master. Allow it in the app."
                else "I could not find $who in your contacts, Master."
            )
        val label = if (hit.name == hit.number) hit.number else hit.name
        return sensitive("Shall I call $label, Master?") {
            val number = hit.number.filter { it.isDigit() || it == '+' }
            val emergency = number in setOf("112", "100", "101", "102", "108", "911", "999")
            val canCall = androidx.core.content.ContextCompat.checkSelfPermission(
                ctx, android.Manifest.permission.CALL_PHONE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val action = if (canCall && !emergency) Intent.ACTION_CALL else Intent.ACTION_DIAL
            val ok = Launch.start(ctx, Intent(action, Uri.parse("tel:" + Uri.encode(number))), "Call $label")
            if (!ok) notifyHint()
            else if (action == Intent.ACTION_CALL) "Calling $label."
            else "The dialer is open with $label's number. Press call, Master."
        }
    }

    private suspend fun whatsapp(who: String, body: String): Outcome {
        val hit = Contacts.find(ctx, who)
            ?: return say(
                if (!Contacts.hasPermission(ctx) && who.any { it.isLetter() })
                    "I need the Contacts permission to find $who, Master. Allow it in the app."
                else "I could not find $who in your contacts, Master."
            )
        val label = if (hit.name == hit.number) hit.number else hit.name
        val question = if (body.isBlank()) "Shall I open your WhatsApp chat with $label, Master?"
        else "Shall I send $label this WhatsApp message: $body. Yes or no, Master?"
        return sensitive(question) {
            val wa = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { AppLauncher.isInstalled(ctx, it) }
                ?: return@sensitive "WhatsApp is not installed on this phone, Master."
            val num = Contacts.whatsappNumber(hit.number)
            val url = "https://wa.me/$num" + if (body.isNotBlank()) "?text=" + enc(body) else ""
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(wa)
            if (!Launch.start(ctx, i, "WhatsApp $label")) return@sensitive notifyHint()
            if (body.isBlank()) return@sensitive "Chat with $label is open, Master."
            if (JinAccessibilityService.instance == null)
                return@sensitive "The message is typed in the chat with $label. Press send, Master, or enable screen control so I can."
            delay(3000)
            for (n in 0 until 6) {
                val s = JinAccessibilityService.instance ?: break
                if (s.tapText("send", exact = true) == JinAccessibilityService.TapResult.OK) {
                    return@sensitive "Message sent to $label."
                }
                delay(800)
            }
            "The message is ready in the chat with $label, but I could not press send. Please press it, Master."
        }
    }

    private fun openApp(name: String): String {
        val entry = AppLauncher.find(ctx, name) ?: return "I could not find an app called $name, Master."
        return if (AppLauncher.open(ctx, entry)) "Opening ${entry.label}." else notifyHint()
    }

    private fun setAlarm(h: Int, m: Int): String {
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, h)
            .putExtra(AlarmClock.EXTRA_MINUTES, m)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "ARISE")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return try {
            if (Launch.start(ctx, i, "Set alarm ${TimeParse.formatClock(h, m)}")) "Alarm set for ${TimeParse.formatClock(h, m)}."
            else "I prepared the alarm for ${TimeParse.formatClock(h, m)}. Tap the notification to confirm it, Master."
        } catch (e: ActivityNotFoundException) {
            "Your phone has no clock app that accepts alarms, Master."
        }
    }

    private fun setTimer(seconds: Int): String {
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "ARISE")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return try {
            if (Launch.start(ctx, i, "Start timer")) "Timer started for ${TimeParse.formatDuration(seconds)}."
            else "I prepared a ${TimeParse.formatDuration(seconds)} timer. Tap the notification to start it, Master."
        } catch (e: ActivityNotFoundException) {
            "Your phone has no clock app that accepts timers, Master."
        }
    }

    // ------------------------------------------------------------------ Play Store

    private suspend fun installFlow(name: String): String {
        val pkg = AppLauncher.knownPackageFor(name)
        val uri = if (pkg != null) "market://details?id=$pkg" else "market://search?q=${enc(name)}&c=apps"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage("com.android.vending")
        if (!AppLauncher.isInstalled(ctx, "com.android.vending")) {
            return "The Google Play Store is not installed on this phone, Master."
        }
        if (!Launch.start(ctx, intent, "Install $name")) {
            return "I put a notification on your screen. Tap it to open the Play Store, then tell me: tap install."
        }
        if (JinAccessibilityService.instance == null) {
            return "The Play Store is open, Master. Turn on screen control in Settings if you want me to press Install for you."
        }
        delay(3500)
        var openedFromSearch = false
        for (attempt in 0 until 16) {
            val s = JinAccessibilityService.instance ?: return "Screen control switched off. Please press Install yourself, Master."
            if (pkg != null && s.hasExactText("Open") && !s.hasExactText("Install")) {
                return "$name is already installed on this phone, Master."
            }
            if (s.tapText("install", exact = true) == JinAccessibilityService.TapResult.OK) {
                return "I pressed Install for $name. Google Play is downloading it now, Master. Please check the screen."
            }
            if (pkg == null && !openedFromSearch && attempt >= 3) {
                if (s.tapText(name) == JinAccessibilityService.TapResult.OK) {
                    openedFromSearch = true
                    delay(2500)
                    continue
                }
            }
            delay(1000)
        }
        return "I could not find an Install button for $name, Master. It may be a paid app, already installed, " +
            "unavailable in your region, or need you to sign in to Google Play. Tell me tap install if you can see it."
    }

    // ------------------------------------------------------------------ Gmail

    private fun openGmail(): String {
        val gm = "com.google.android.gm"
        return if (AppLauncher.isInstalled(ctx, gm)) {
            if (AppLauncher.open(ctx, AppLauncher.AppEntry("Gmail", gm))) "Opening Gmail." else notifyHint()
        } else {
            launchView("https://mail.google.com", "com.android.chrome", "Opening Gmail in Chrome.", "Open Gmail")
        }
    }

    private suspend fun gmailRead(): Outcome {
        return when (val t = GmailAuth.token(ctx)) {
            is GmailToken.Ok -> try {
                val s = GmailClient.unread(t.token)
                when {
                    s.unreadCount == 0 -> say("Your inbox has no unread emails, Master.")
                    else -> say(
                        "You have ${s.unreadCount} unread emails, Master. The latest: " +
                            s.items.joinToString(". Next: ")
                    )
                }
            } catch (e: Exception) {
                say(e.message ?: "I could not read Gmail, Master.")
            }
            is GmailToken.NeedsConsent ->
                say("Gmail is not connected yet, Master. Open Settings, Phone control, and press Connect Gmail once.")
            is GmailToken.Fail -> say("Gmail connection failed. ${t.message}")
        }
    }

    private fun composeEmail(c: Cmd.GmailCompose): String {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(c.to)))
            .putExtra(Intent.EXTRA_SUBJECT, c.subject)
            .putExtra(Intent.EXTRA_TEXT, c.body)
        if (AppLauncher.isInstalled(ctx, "com.google.android.gm")) i.setPackage("com.google.android.gm")
        return try {
            if (Launch.start(ctx, i, "Email draft")) {
                if (c.to.isBlank()) "The draft is open in Gmail with no recipient. Tell me the address, or type it. Then say tap send."
                else "The draft is ready in Gmail. Review it, then say tap send."
            } else notifyHint()
        } catch (e: ActivityNotFoundException) {
            "No email app is available on this phone, Master."
        }
    }

    // ------------------------------------------------------------------ BookMyShow

    private fun bookTickets(title: String): String {
        val bms = "com.bt.bms"
        val started = if (AppLauncher.isInstalled(ctx, bms)) {
            AppLauncher.open(ctx, AppLauncher.AppEntry("BookMyShow", bms))
        } else {
            val q = if (title.isBlank()) "bookmyshow movie tickets" else "bookmyshow $title tickets"
            Launch.start(ctx, viewIntent("https://www.google.com/search?q=" + enc(q), "com.android.chrome"), "Open BookMyShow")
        }
        if (!started) return notifyHint()
        val example = if (title.isBlank()) "the movie name" else title
        return "BookMyShow is open, Master. Guide me step by step. For example: tap search, type $example, press enter, " +
            "tap the movie, tap book tickets. I will stop before payment, so you complete that step yourself."
    }

    // ------------------------------------------------------------------ AI

    private suspend fun ask(text: String, story: Boolean = false): Outcome {
        val context = if (prefs.sendContext) recent.toList() else emptyList()
        return try {
            val reply = ai.chat(if (story) systemPrompt() + STORY_RULE else systemPrompt(), context, text)
            if (prefs.sendContext) {
                recent.add(ChatMsg("user", text))
                recent.add(ChatMsg("assistant", reply))
                while (recent.size > 12) recent.removeAt(0)
            }
            say(reply)
        } catch (e: AiError) {
            say(e.message ?: "The AI request failed, Master.")
        } catch (e: Exception) {
            say("I could not reach the AI, Master. ${e.message ?: ""}".trim())
        }
    }

    private fun systemPrompt(): String {
        val now = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, h:mm a z"))
        return "You are ARISE, a calm, composed and quietly powerful voice assistant with a shadow-monarch fantasy personality. " +
            "You are an original character, not any real person. The user is your Master: address them as Master now and then, " +
            "not in every sentence. You are speaking out loud, so answer in short natural sentences, normally one to three, " +
            "with no markdown, no bullet points, no emojis, no URLs and no stage directions. Be accurate, and say plainly when you do not know. " +
            "You can open apps, set alarms and timers, tell the time, calculate, and operate the Play Store, Chrome, Gmail, BookMyShow, WhatsApp and YouTube, and call people from the contacts, " +
            "when the user gives those commands; never claim you did something unless the app confirmed it. " +
            "Current date and time: $now."
    }

    fun clearContext() = recent.clear()

    private companion object {
        const val STORY_RULE = " The user asked for a story: tell a complete, vivid story of about 200 to 300 words, " +
            "in flowing spoken prose, with a clear beginning, middle and ending. This overrides the short-answer rule."
    }
}
