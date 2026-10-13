package com.kitchenai.ui.platform

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.AppleIdToken
import com.kitchenai.shared.domain.model.AppleRawNonce
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationAppleIDButton
import platform.AuthenticationServices.ASAuthorizationAppleIDButtonStyleBlack
import platform.AuthenticationServices.ASAuthorizationAppleIDButtonStyleWhite
import platform.AuthenticationServices.ASAuthorizationAppleIDButtonTypeSignIn
import platform.AuthenticationServices.ASAuthorizationAppleIDCredential
import platform.AuthenticationServices.ASAuthorizationAppleIDProvider
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationScopeFullName
import platform.AuthenticationServices.ASPresentationAnchor
import platform.CoreCrypto.CC_SHA256
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSPersonNameComponents
import platform.Foundation.NSPersonNameComponentsFormatter
import platform.Foundation.NSPersonNameComponentsFormatterStyleDefault
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.UIKit.UIApplication
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume

private const val SHA256_BYTES = 32
private const val HEX_RADIX = 16
private const val HEX_DIGITS_PER_BYTE = 2

@Composable
actual fun rememberAppleSignInLauncher(): AppleSignInLauncher? = remember { AuthorizationControllerLauncher() }

private class AuthorizationControllerLauncher : AppleSignInLauncher {
    // ASAuthorizationController holds its delegate weakly: this keeps it alive until Apple answers.
    private var inFlight: AppleAuthorizationDelegate? = null

    override suspend fun launch(): AppResult<AppleSignInResult> {
        val rawNonce = NSUUID().UUIDString + NSUUID().UUIDString
        return suspendCancellableCoroutine { continuation ->
            val request = ASAuthorizationAppleIDProvider().createRequest()
            request.requestedScopes = listOf(ASAuthorizationScopeFullName)
            request.nonce = sha256Hex(rawNonce)
            val delegate =
                AppleAuthorizationDelegate(rawNonce) { result ->
                    inFlight = null
                    if (continuation.isActive) continuation.resume(result)
                }
            inFlight = delegate
            val controller = ASAuthorizationController(authorizationRequests = listOf(request))
            controller.delegate = delegate
            controller.presentationContextProvider = delegate
            controller.performRequests()
        }
    }
}

private class AppleAuthorizationDelegate(
    private val rawNonce: String,
    private val onResult: (AppResult<AppleSignInResult>) -> Unit,
) : NSObject(),
    ASAuthorizationControllerDelegateProtocol,
    ASAuthorizationControllerPresentationContextProvidingProtocol {
    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization: ASAuthorization,
    ) {
        val credential = didCompleteWithAuthorization.credential as? ASAuthorizationAppleIDCredential
        val token = credential?.identityToken?.utf8()
        if (credential == null || token == null) {
            onResult(AppResult.Failure(AppError.Unknown(IllegalStateException("Apple returned no identity token"))))
            return
        }
        val name = credential.fullName?.let(::formatted)?.takeUnless(String::isBlank)
        onResult(AppResult.Success(AppleSignInResult(AppleIdToken(token), AppleRawNonce(rawNonce), name)))
    }

    // Covers the user dismissing the sheet too, reported like the Google launcher's cancel.
    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError: NSError,
    ) {
        onResult(AppResult.Failure(AppError.Unknown(IllegalStateException(didCompleteWithError.localizedDescription))))
    }

    @Suppress("DEPRECATION")
    override fun presentationAnchorForAuthorizationController(
        controller: ASAuthorizationController,
    ): ASPresentationAnchor = UIApplication.sharedApplication.keyWindow ?: UIWindow()
}

private fun formatted(parts: NSPersonNameComponents): String =
    NSPersonNameComponentsFormatter.localizedStringFromPersonNameComponents(
        parts,
        NSPersonNameComponentsFormatterStyleDefault,
        0uL,
    )

@OptIn(BetaInteropApi::class)
private fun NSData.utf8(): String? = NSString.create(data = this, encoding = NSUTF8StringEncoding)?.toString()

/** Apple receives the hash, Firebase the raw value, so a stolen token cannot be replayed with another nonce. */
@OptIn(ExperimentalForeignApi::class)
private fun sha256Hex(input: String): String {
    val bytes = input.encodeToByteArray()
    val digest = UByteArray(SHA256_BYTES)
    bytes.usePinned { pinnedInput ->
        digest.usePinned { pinnedDigest ->
            CC_SHA256(pinnedInput.addressOf(0), bytes.size.convert(), pinnedDigest.addressOf(0))
        }
    }
    return digest.joinToString("") { byte -> byte.toString(HEX_RADIX).padStart(HEX_DIGITS_PER_BYTE, '0') }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun AppleSignInButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier,
) {
    val tap by rememberUpdatedState(onClick)
    val target = remember { TapTarget { tap() } }
    // Apple's black button on light backgrounds and white on dark: the style is fixed at creation.
    val dark = isSystemInDarkTheme()
    key(dark) {
        UIKitView(
            factory = {
                ASAuthorizationAppleIDButton(
                    authorizationButtonType = ASAuthorizationAppleIDButtonTypeSignIn,
                    authorizationButtonStyle = appleButtonStyle(dark),
                ).apply {
                    // A pill at the Material button's height, so it lines up with the Google button below it.
                    cornerRadius = ButtonDefaults.MinHeight.value / 2.0
                    addTarget(target, NSSelectorFromString("tap"), UIControlEventTouchUpInside)
                }
            },
            update = { button -> button.enabled = enabled },
            modifier = modifier.fillMaxWidth().height(ButtonDefaults.MinHeight),
        )
    }
}

private fun appleButtonStyle(dark: Boolean) =
    if (dark) ASAuthorizationAppleIDButtonStyleWhite else ASAuthorizationAppleIDButtonStyleBlack

/** A UIKit target-action receiver; Compose lambdas cannot be handed to `addTarget` directly. */
@OptIn(BetaInteropApi::class)
private class TapTarget(
    private val onTap: () -> Unit,
) : NSObject() {
    @ObjCAction
    fun tap() = onTap()
}
