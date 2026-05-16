package com.github.rashed94x.clockifyplugin.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

object ClockifyCredentials {

    private val attributes = CredentialAttributes(
        generateServiceName("ClockifyPlugin", "apiToken")
    )

    var apiToken: String?
        get() = PasswordSafe.instance.getPassword(attributes)
        set(value) = PasswordSafe.instance.set(attributes, value?.let { Credentials("apiToken", it) })
}
