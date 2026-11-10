package com.kitchenai.admin.config

private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "[::1]")

/** Deployed, Hosting serves the project's own config; locally, an untracked file stands in for it. */
fun configUrl(hostname: String): String =
    if (hostname in LOCAL_HOSTS) "firebase-config.local.json" else "/__/firebase/init.json"
