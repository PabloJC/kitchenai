package com.kitchenai.shared.di

import com.kitchenai.shared.data.remote.firebase.FirebaseSessionAdapter
import com.kitchenai.shared.domain.port.SessionRepositoryContract
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
import org.koin.core.module.Module
import org.koin.dsl.module

/** Bound to the port, never to the adapter: no use case may reach the Firebase type through DI. */
val authModule: Module =
    module {
        single<FirebaseAuth> { Firebase.auth }

        single<SessionRepositoryContract> { FirebaseSessionAdapter(get(), get()) }
    }
