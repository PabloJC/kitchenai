package com.kitchenai.admin.auth

/** `true` exactly, as `isAdmin()` in the rules: a claim set to "true" or 1 is not an admin. */
fun isAdminClaims(claims: Map<String, Any?>): Boolean = claims["admin"] == true
