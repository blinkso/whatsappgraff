package ua.blink.whatsappgraff.filter

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.context.support.GenericApplicationContext
import ua.blink.whatsappgraff.component.ConversationApi
import ua.blink.whatsappgraff.dsl.*
import ua.blink.whatsappgraff.dto.*
import ua.blink.whatsappgraff.dto.request.MarkdownMessage
import java.lang.reflect.Proxy
import java.util.Locale

class HandlersFilterAnswerRoutingTest {
    @ParameterizedTest
    @ValueSource(strings = ["Пошук роботи", "Пошук", "Поиск работы", "Поиск",
        "Explore new roles", "Explore", "Szukaj pracy", "Szukaj", "Explorar oportunidades", "Explorar"])
    fun `answer text cannot replace an active registration`(answer: String) = runBlocking {
        val h = fixture()
        h.send("/register")
        h.send("Attendee")
        h.send(answer)
        h.send("Meet the community")

        assertEquals(mapOf("name" to "Attendee", "topic" to answer, "expectations" to "Meet the community"), h.completed)
        assertEquals(0, h.searches)
        assertEquals(0, h.unhandled)
    }

    @Test
    fun `an exact translated menu label works after registration completes`() = runBlocking {
        val h = fixture()
        h.send("/register")
        h.send("Attendee")
        h.send("Пошук")
        h.send("Meet the community")
        h.send("Пошук")
        assertEquals(1, h.searches)
        assertEquals("Пошук", h.completed?.get("topic"))
    }

    @Test
    fun `a menu label prefix is not a command while idle`() = runBlocking {
        val h = fixture()
        h.send("Пошук роботи")
        assertEquals(0, h.searches)
        assertEquals(1, h.unhandled)
        h.send("ПОШУК")
        assertEquals(1, h.searches)
    }

    @Test
    fun `an explicit command can leave an active registration`() = runBlocking {
        val h = fixture()
        h.send("/register")
        h.send("Attendee")
        h.send("/events")
        assertEquals(1, h.searches)
        assertNull(h.completed)
    }

    @ParameterizedTest
    @ValueSource(strings = ["/repeat=registration-id", "/repeatrepeatid123", "/repeat registration-id"])
    fun `existing slash command payloads still route`(command: String) = runBlocking {
        val h = fixture()
        h.send("/register")
        h.send("Attendee")
        h.send(command)
        assertEquals(1, h.repeats)
        assertEquals(0, h.searches)
        assertNull(h.completed)
    }

    @Test
    fun `overlapping slash commands select the most specific handler`() = runBlocking {
        val h = fixture()
        h.send("/promoPricepriceid123")
        assertEquals(1, h.promos)
        assertEquals(0, h.shortPromos)
    }

    private suspend fun fixture(): Harness {
        val h = Harness()
        GenericApplicationContext().use { context ->
            val register = handler("/register") {
                step<String>("name") { question { MarkdownMessage("Name?") } }
                step<String>("topic") { question { MarkdownMessage("Priority?") } }
                step<String>("expectations") { question { MarkdownMessage("Expectations?") } }
                process { _, answers -> h.completed = answers.toMap(); null }
            }(context)
            val search = handler("/events", "Пошук", "Поиск", "Explore", "Szukaj", "Explorar") {
                process { _, _ -> h.searches++; null }
            }(context)
            val repeat = handler("/repeat") { process { _, _ -> h.repeats++; null } }(context)
            val promo = handler("/promo") { process { _, _ -> h.shortPromos++; null } }(context)
            val promoPrice = handler("/promoPrice") { process { _, _ -> h.promos++; null } }(context)
            val factory = object : HandlersFactory {
                override fun getHandlers(): Map<String, Handler> =
                    listOf(register, search, repeat, promo, promoPrice).flatMap { handler ->
                        handler.commands.map { it.lowercase(Locale.ROOT) to handler }
                    }.toMap(LinkedHashMap())
            }
            val api = Proxy.newProxyInstance(ConversationApi::class.java.classLoader, arrayOf(ConversationApi::class.java)) { _, method, _ ->
                check(method.name == "sendMessage") { "Unexpected ConversationApi call: ${method.name}" }
                h.message("")
            } as ConversationApi
            val buttons = object : ButtonsFactory {
                override fun addButtonsRequest(sendRequest: ua.blink.whatsappgraff.dto.request.MessageSendRequest) = Unit
                override fun getButtonsRequests(chatId: String) = emptyList<ua.blink.whatsappgraff.dto.request.MessageSendRequest>()
            }
            h.filter = HandlersFilter(api, buttons, factory)
        }
        return h
    }

    private class Harness {
        lateinit var filter: HandlersFilter
        var completed: Map<String, Any>? = null
        var searches = 0
        var repeats = 0
        var promos = 0
        var shortPromos = 0
        var unhandled = 0

        suspend fun send(text: String) {
            filter.handleMessage(message(text), object : FilterChain {
                override suspend fun doFilter(message: Message) { unhandled++ }
            })
        }

        fun message(text: String): Message = Message(sid = "test-input", accountSid = "", date = "", serviceSid = null,
            chatId = "test-chat", user = "attendee", attributes = """{"webchat":{"intent":"action"}}""", photo = null, text = text)
    }
}
