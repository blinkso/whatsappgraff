package ua.blink.whatsappgraff.dto.request

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import ua.blink.whatsappgraff.dto.request.keyboard.ActionReplyKeyboard
import ua.blink.whatsappgraff.dto.request.keyboard.InlineUrlReplyKeyboard
import ua.blink.whatsappgraff.dto.request.keyboard.MarkupInlinedReplyKeyboard
import ua.blink.whatsappgraff.dto.request.keyboard.ReplyKeyboard

open class MessageSendRequest(
    chatId: String,

    to: String,

    @get:JsonProperty("Body")
    val text: String,

    replyMarkup: ReplyKeyboard? = null,
) : SendRequest(chatId = chatId, to = to, buttons = replyMarkup) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MessageSendRequest) return false
        if (!super.equals(other)) return false

        if (text != other.text) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + text.hashCode()
        return result
    }

    override fun toString(): String {
        return "MessageSendRequest(text='$text')"
    }

    fun formAttributes(): String? {
        return when (buttons) {
            is MarkupInlinedReplyKeyboard -> {
                val actionButton =
                    buttons.buttons.firstOrNull { it is ActionReplyKeyboard } as? ActionReplyKeyboard
                val buttons = buttons.buttons.let { buttons ->
                    actionButton?.let { buttons.minusElement(it) } ?: buttons
                }
                when {
                    this is MarkdownTemplateMessage -> {
                        serializeJsonEntries(
                            buttons.withIndex().associate { (index, button) ->
                                button as InlineUrlReplyKeyboard
                                "${index + 1}" to (button.callbackData ?: "")
                            }
                        )
                    }

                    this is MarkdownInlinedButtonsTemplateMessage -> {
                        serializeJsonEntries(
                            buttons.withIndex().associate { (index, button) ->
                                button as InlineUrlReplyKeyboard
                                "${index + 1}" to (button.url ?: "")
                            }
                        )
                    }

                    buttons.any { (it as? InlineUrlReplyKeyboard)?.url != null } -> {
                        null
                    }

                    buttons.size <= 3 && this !is MarkdownMessage -> {
                        serializeJsonEntries(
                            buttons.withIndex().associate { (index, button) ->
                                button as InlineUrlReplyKeyboard
                                "${index + 2}" to (button.callbackData ?: "")
                            }
                        )
                    }

                    else -> {
                        serializeJsonEntries(
                            buttons.withIndex().associate { (index, button) ->
                                button as InlineUrlReplyKeyboard
                                "${index + 3}" to (button.callbackData ?: "")
                            }
                        )
                    }
                }
            }

            else -> {
                null
            }
        }
    }

    fun formBody(): String? {
        return when (buttons) {
            is MarkupInlinedReplyKeyboard -> {
                val actionButton =
                    buttons.buttons.firstOrNull { it is ActionReplyKeyboard } as? ActionReplyKeyboard
                val buttons = buttons.buttons.let { buttons ->
                    actionButton?.let { buttons.minusElement(it) } ?: buttons
                }
                when {
                    buttons.any { (it as? InlineUrlReplyKeyboard)?.url != null } && this !is MarkdownInlinedButtonsTemplateMessage -> {
                        val text = buildString {
                            append(text)
                            append("\n")
                            buttons.forEach { button ->
                                button as InlineUrlReplyKeyboard
                                append("\n${button.text}: ${button.url}")
                            }
                        }

                        text
                    }

                    buttons.size <= 3 && this !is MarkdownMessage -> {
                        null
                    }

                    else -> {
                        null
                    }
                }
            }

            else -> {
                text
            }
        }
    }

    fun formContent(
        buttonTemplate: List<String>,
        listTemplate: List<String>
    ): Pair<String, String>? {
        return when (buttons) {
            is MarkupInlinedReplyKeyboard -> {
                val actionButton =
                    buttons.buttons.firstOrNull { it is ActionReplyKeyboard } as? ActionReplyKeyboard
                val buttons = buttons.buttons.let { buttons ->
                    actionButton?.let { buttons.minusElement(it) } ?: buttons
                }
                when {
                    this is MarkdownTemplateMessage -> {
                        contentSid to serializeContentVariables(
                            buttons.withIndex().associate { (index, button) ->
                                button as InlineUrlReplyKeyboard
                                "${index + 1}" to button.text
                            }
                        )
                    }

                    this is MarkdownInlinedButtonsTemplateMessage -> {
                        contentSid to serializeContentVariables(
                            buttons.withIndex().associate { (index, button) ->
                                button as InlineUrlReplyKeyboard
                                "${index + 1}" to button.text
                            }
                        )
                    }

                    buttons.any { (it as? InlineUrlReplyKeyboard)?.url != null } -> {
                        null
                    }

                    buttons.size <= 3 && this !is MarkdownMessage -> {
                        val contentSid = buttonTemplate[buttons.size.minus(1)]
                        contentSid to serializeContentVariables(
                            linkedMapOf<String, String>().apply {
                                put("1", text)
                                buttons.forEachIndexed { index, button ->
                                    button as InlineUrlReplyKeyboard
                                    put("${index + 2}", button.text.take(BUTTON_MAX_LENGTH))
                                }
                            }
                        )
                    }

                    else -> {
                        val contentSid = listTemplate[buttons.size.minus(1)]
                        contentSid to serializeContentVariables(
                            linkedMapOf<String, String>().apply {
                                put("1", text)
                                put("2", actionButton?.text?.take(BUTTON_MAX_LENGTH) ?: "")
                                buttons.forEachIndexed { index, button ->
                                    button as InlineUrlReplyKeyboard
                                    put("${index + 3}", button.text.take(LIST_ITEM_MAX_LENGTH))
                                }
                            }
                        )
                    }
                }
            }

            else -> {
                null
            }
        }
    }

    fun formShortenUrls(): Boolean? {
        return when (buttons) {
            is MarkupInlinedReplyKeyboard -> {
                val actionButton =
                    buttons.buttons.firstOrNull { it is ActionReplyKeyboard } as? ActionReplyKeyboard
                val buttons = buttons.buttons.let { buttons ->
                    actionButton?.let { buttons.minusElement(it) } ?: buttons
                }
                when {
                    buttons.any { (it as? InlineUrlReplyKeyboard)?.url != null } -> {
                        true
                    }

                    buttons.size <= 3 && this !is MarkdownMessage -> {
                        false
                    }

                    else -> {
                        false
                    }
                }
            }

            else -> {
                null
            }
        }
    }

    private companion object {
        private val OBJECT_MAPPER = ObjectMapper()
        private val NEW_LINE_REGEX = "\\r?\\n|\\r".toRegex()
        private const val BUTTON_MAX_LENGTH = 20
        private const val LIST_ITEM_MAX_LENGTH = 24

        private fun serializeJsonEntries(entries: Map<String, String>): String {
            return OBJECT_MAPPER.writeValueAsString(entries)
        }

        private fun serializeContentVariables(entries: Map<String, String>): String {
            return serializeJsonEntries(
                entries.mapValues { (_, value) ->
                    value.replace(NEW_LINE_REGEX, "  ")
                }
            )
        }
    }
}
