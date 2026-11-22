package com.wdtt.client.ui.profiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wdtt.client.vkauth.VkSession

/** Whether a VK account is signed in; re-read when the screen resumes (after the login page). */
@Composable
fun rememberVkSignedIn(): MutableState<Boolean> {
    val state = remember { mutableStateOf(VkSession.hasSessionCookie()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) state.value = VkSession.hasSessionCookie()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return state
}

/** Formal texts of the VK account features. */
object VkAccountText {
    const val SIGNED_IN = "Вход в аккаунт VK выполнен.\nПриложение создаёт ссылку на звонок, если она не указана в профиле."
    const val SIGNED_OUT = "Вход в аккаунт VK не выполнен.\nСсылку на звонок можно указать в профиле вручную."
    const val SIGN_IN = "Войти через VK"
    const val SIGN_OUT = "Выйти из аккаунта"
    const val SIGN_OUT_TITLE = "Выйти из аккаунта VK?"
    const val SIGN_OUT_BODY = "Сессия будет удалена с этого устройства.\nСсылки на звонок перестанут создаваться автоматически."
    const val CREATE_LINK = "Создать ссылку через VK"
    const val CREATE_NEW_LINK = "Создать новую ссылку через VK"
    const val CREATING = "Создание ссылки…"
    const val CREATED = "Ссылка на звонок создана."
    const val LOGIN_CANCELLED = "Вход не выполнен."
}
