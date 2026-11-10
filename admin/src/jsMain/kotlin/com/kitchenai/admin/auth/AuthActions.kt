package com.kitchenai.admin.auth

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.auth.js
import kotlinx.coroutines.await

suspend fun signInWithGoogle() {
    signInWithPopup(Firebase.auth.js, GoogleAuthProvider()).await()
}

suspend fun signOutOfAdmin() {
    Firebase.auth.signOut()
}
