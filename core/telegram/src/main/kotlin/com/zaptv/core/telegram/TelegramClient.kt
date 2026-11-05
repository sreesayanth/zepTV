package com.zaptv.core.telegram

import android.content.Context
import android.os.Build
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.AuthorizationState
import dev.g000sha256.tdl.dto.AuthorizationStateClosed
import dev.g000sha256.tdl.dto.AuthorizationStateClosing
import dev.g000sha256.tdl.dto.AuthorizationStateLoggingOut
import dev.g000sha256.tdl.dto.AuthorizationStateReady
import dev.g000sha256.tdl.dto.AuthorizationStateWaitCode
import dev.g000sha256.tdl.dto.AuthorizationStateWaitOtherDeviceConfirmation
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPassword
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPhoneNumber
import dev.g000sha256.tdl.dto.AuthorizationStateWaitTdlibParameters
import dev.g000sha256.tdl.dto.File
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.withLock as withJavaLock
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton

/** Where sign-in stands, in the terms the library screens need. */
sealed interface TelegramAuth {
    /** This build has no Telegram API credentials (see README). */
    data object NotConfigured : TelegramAuth
    /** TDLib is not running. It starts only when the library is first opened. */
    data object Stopped : TelegramAuth
    data object Starting : TelegramAuth
    /** Phone sign-in: ask for the number (the TV asks for a QR code instead). */
    data object WaitPhoneNumber : TelegramAuth
    data object WaitCode : TelegramAuth
    /** Show [link] as a QR code; Telegram on the phone scans it. TDLib refreshes it when it expires. */
    data class WaitQrScan(val link: String) : TelegramAuth
    data class WaitPassword(val hint: String) : TelegramAuth
    data object Ready : TelegramAuth
    data object LoggingOut : TelegramAuth
}

/** A failed TDLib request. [code] 404 means "not found", 400 a bad request, 429 too many requests. */
class TelegramException(val code: Int, message: String) : IOException("Telegram error $code: $message")

/**
 * Owns the app's single TDLib client. Nothing Telegram-related runs until [start] is called, so
 * live TV pays nothing for the feature: no native library loaded, no database opened.
 */
@Singleton
class TelegramClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val isConfigured: Boolean = BuildConfig.TG_API_ID != 0 && BuildConfig.TG_API_HASH.isNotEmpty()

    private val _auth = MutableStateFlow<TelegramAuth>(if (isConfigured) TelegramAuth.Stopped else TelegramAuth.NotConfigured)
    val auth: StateFlow<TelegramAuth> = _auth.asStateFlow()

    private val _lastError = MutableStateFlow<TelegramException?>(null)
    /** The last sign-in error, e.g. a wrong password, for the screen to show. */
    val lastError: StateFlow<TelegramException?> = _lastError.asStateFlow()

    /** True on a TV: sign in by QR code instead of typing a phone number. */
    @Volatile var preferQrSignIn: Boolean = true

    private val startLock = Mutex()
    private var scope: CoroutineScope? = null
    @Volatile private var tdl: TdlClient? = null

    // Latest state of every file TDLib told us about, for the streaming reader.
    private val files = ConcurrentHashMap<Int, File>()
    private val fileLock = ReentrantLock()
    private val fileChanged = fileLock.newCondition()

    suspend fun start() {
        if (!isConfigured) return
        startLock.withLock {
            if (tdl != null) return
            _auth.value = TelegramAuth.Starting
            val client = TdlClient.create()
            val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            tdl = client
            scope = clientScope
            clientScope.launch { client.setLogVerbosityLevel(1) }
            clientScope.launch {
                client.authorizationStateUpdates.collect { onAuthorizationState(it.authorizationState) }
            }
            clientScope.launch {
                client.fileUpdates.collect { update -> onFile(update.file) }
            }
        }
    }

    /** Closes TDLib to free memory (e.g. long after leaving the library). [start] reopens it. */
    suspend fun stop() {
        startLock.withLock {
            val client = tdl ?: return
            client.close()
            scope?.cancel()
            tdl = null
            scope = null
            files.clear()
            if (isConfigured) _auth.value = TelegramAuth.Stopped
        }
    }

    suspend fun requestQrCode() = authCall { it.requestQrCodeAuthentication(LongArray(0)) }

    suspend fun submitPhoneNumber(phoneNumber: String) = authCall { it.setAuthenticationPhoneNumber(phoneNumber, null) }

    suspend fun submitCode(code: String) = authCall { it.checkAuthenticationCode(code.trim()) }

    suspend fun submitPassword(password: String) = authCall { it.checkAuthenticationPassword(password) }

    suspend fun logOut() = authCall { it.logOut() }

    /** The running client, for repositories. Throws if [start] was not called. */
    internal fun requireClient(): TdlClient =
        tdl ?: throw TelegramException(0, "Telegram is not started")

    internal fun cachedFile(fileId: Int): File? = files[fileId]

    /** Blocks the calling (loader) thread until any file update arrives or [timeoutMs] passes. */
    internal fun awaitFileUpdate(timeoutMs: Long) {
        fileLock.withJavaLock { fileChanged.await(timeoutMs, TimeUnit.MILLISECONDS) }
    }

    internal fun onFile(file: File) {
        files[file.id] = file
        fileLock.withJavaLock { fileChanged.signalAll() }
    }

    private suspend fun authCall(block: suspend (TdlClient) -> TdlResult<*>) {
        _lastError.value = null
        val result = block(requireClient())
        if (result is TdlResult.Failure) _lastError.value = TelegramException(result.code, result.message)
    }

    private suspend fun onAuthorizationState(state: AuthorizationState) {
        _auth.value = when (state) {
            is AuthorizationStateWaitTdlibParameters -> {
                setParameters()
                TelegramAuth.Starting
            }
            is AuthorizationStateWaitPhoneNumber -> {
                if (preferQrSignIn) {
                    requestQrCode()
                    TelegramAuth.Starting
                } else {
                    TelegramAuth.WaitPhoneNumber
                }
            }
            is AuthorizationStateWaitOtherDeviceConfirmation -> TelegramAuth.WaitQrScan(state.link)
            is AuthorizationStateWaitCode -> TelegramAuth.WaitCode
            is AuthorizationStateWaitPassword -> TelegramAuth.WaitPassword(state.passwordHint)
            is AuthorizationStateReady -> TelegramAuth.Ready
            is AuthorizationStateLoggingOut, is AuthorizationStateClosing -> TelegramAuth.LoggingOut
            is AuthorizationStateClosed -> {
                // After a log out TDLib closes itself; drop the client so start() makes a fresh one.
                tdl = null
                scope?.cancel()
                scope = null
                TelegramAuth.Stopped
            }
            // Email sign-in, registration and premium purchase are not offered from a TV app.
            else -> TelegramAuth.WaitPhoneNumber
        }
    }

    private suspend fun setParameters() {
        val root = context.noBackupFilesDir
        val result = requireClient().setTdlibParameters(
            false, // useTestDc
            java.io.File(root, "tdlib").absolutePath, // databaseDirectory
            java.io.File(context.cacheDir, "tdlib-files").absolutePath, // filesDirectory: the OS may clear it
            ByteArray(0), // databaseEncryptionKey
            true, // useFileDatabase
            true, // useChatInfoDatabase
            true, // useMessageDatabase
            false, // useSecretChats
            BuildConfig.TG_API_ID,
            BuildConfig.TG_API_HASH,
            Locale.getDefault().language.ifEmpty { "en" },
            Build.MODEL ?: "Android TV",
            "Android ${Build.VERSION.RELEASE}",
            "ZapTV",
        )
        if (result is TdlResult.Failure) _lastError.value = TelegramException(result.code, result.message)
    }
}

/** Unwraps a TDLib result or throws [TelegramException]. */
internal fun <T> TdlResult<T>.getOrThrow(): T = when (this) {
    is TdlResult.Success -> result
    is TdlResult.Failure -> throw TelegramException(code, message)
}
