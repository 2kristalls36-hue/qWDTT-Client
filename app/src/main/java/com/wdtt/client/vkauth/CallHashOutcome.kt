package com.wdtt.client.vkauth

// From ARDTT (bypass/CallHashOutcome.kt): only the call-link result types; the ARDTT
// call re-creation logic tied to its connection core is not carried over.

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import org.json.JSONException
import org.json.JSONObject

enum class CallHashPhase {
    Session,
    OAuth,
    CallsStart,
    Parse,
}

enum class CallHashErrorKind {
    TransientNetwork,
    AuthRequired,
    Captcha,
    ApiError,
    InvalidResponse,
}

data class CallHashFailure(
    val kind: CallHashErrorKind,
    val phase: CallHashPhase,
    val message: String,
    val httpCode: Int? = null,
    val apiCode: Int? = null,
    val retryAfterMs: Long? = null,
) {
    val userMessage: String
        get() = when (kind) {
            CallHashErrorKind.TransientNetwork -> TRANSIENT_NETWORK_MESSAGE
            CallHashErrorKind.AuthRequired -> AUTH_REQUIRED_MESSAGE
            CallHashErrorKind.Captcha ->
                "Требуется проверка капчи. Выберите способ «Капча» в настройках обхода."
            CallHashErrorKind.ApiError ->
                message.takeIf { it.isNotBlank() } ?: "ВКонтакте отклонил создание звонка"
            CallHashErrorKind.InvalidResponse -> INVALID_RESPONSE_MESSAGE
        }
}

sealed class CallHashOutcome {
    data class Success(val hash: String) : CallHashOutcome()
    data class Failure(val error: CallHashFailure) : CallHashOutcome()
}

fun classifyCallHashThrowable(error: Throwable, phase: CallHashPhase): CallHashFailure {
    if (error is CancellationException) throw error
    val kind = when (error) {
        is SocketTimeoutException,
        is UnknownHostException,
        is SSLException,
        is IOException,
        -> CallHashErrorKind.TransientNetwork
        is JSONException -> CallHashErrorKind.InvalidResponse
        else -> CallHashErrorKind.ApiError
    }
    return CallHashFailure(
        kind = kind,
        phase = phase,
        message = safeCallHashErrorMessage(error),
    )
}

internal fun parseCallsStartBody(body: String): CallHashOutcome {
    if (body.isBlank()) {
        return CallHashOutcome.Failure(
            CallHashFailure(
                kind = CallHashErrorKind.InvalidResponse,
                phase = CallHashPhase.CallsStart,
                message = INVALID_RESPONSE_MESSAGE,
            ),
        )
    }
    val json = try {
        JSONObject(body)
    } catch (_: JSONException) {
        return CallHashOutcome.Failure(
            CallHashFailure(
                kind = CallHashErrorKind.InvalidResponse,
                phase = CallHashPhase.Parse,
                message = INVALID_RESPONSE_MESSAGE,
            ),
        )
    }
    if (json.has("error")) {
        val err = json.optJSONObject("error")
        val apiCode = err?.optInt("error_code", 0)?.takeIf { it != 0 }
        val raw = err?.optString("error_msg").orEmpty()
        val kind = vkApiErrorKind(apiCode, raw)
        return CallHashOutcome.Failure(
            CallHashFailure(
                kind = kind,
                phase = CallHashPhase.CallsStart,
                message = raw.ifBlank { "VK API error" },
                apiCode = apiCode,
            ),
        )
    }
    val joinLink = json.optJSONObject("response")?.optString("join_link").orEmpty()
    if (joinLink.isBlank()) {
        return CallHashOutcome.Failure(
            CallHashFailure(
                kind = CallHashErrorKind.InvalidResponse,
                phase = CallHashPhase.Parse,
                message = INVALID_RESPONSE_MESSAGE,
            ),
        )
    }
    val hash = VkUrl.strip(joinLink)
    if (!VkUrl.isPlausibleHash(hash)) {
        return CallHashOutcome.Failure(
            CallHashFailure(
                kind = CallHashErrorKind.InvalidResponse,
                phase = CallHashPhase.Parse,
                message = INVALID_RESPONSE_MESSAGE,
            ),
        )
    }
    return CallHashOutcome.Success(hash)
}

internal fun vkApiErrorKind(apiCode: Int?, message: String): CallHashErrorKind {
    val lower = message.lowercase()
    if (apiCode == 14 || lower.contains("captcha")) return CallHashErrorKind.Captcha
    if (apiCode == 5 || apiCode == 1117 || lower.contains("authorization") || lower.contains("access_token")) {
        return CallHashErrorKind.AuthRequired
    }
    if (apiCode == 1 || apiCode == 6 || apiCode == 9 || apiCode == 10 || apiCode == 29) {
        return CallHashErrorKind.TransientNetwork
    }
    return CallHashErrorKind.ApiError
}

private fun safeCallHashErrorMessage(error: Throwable): String {
    val raw = error.message.orEmpty()
    val cut = raw.substringBefore('?').substringBefore('#')
    return cut.take(180).ifBlank { error.javaClass.simpleName }
}

internal const val TRANSIENT_NETWORK_MESSAGE = "Нет связи с ВКонтакте"
internal const val AUTH_REQUIRED_MESSAGE = "Нужна авторизация ВКонтакте"
internal const val INVALID_RESPONSE_MESSAGE = "Некорректный ответ ВКонтакте"
