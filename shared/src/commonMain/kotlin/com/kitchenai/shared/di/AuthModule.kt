package com.kitchenai.shared.di

import com.kitchenai.shared.data.remote.firebase.AccountRemoteDataSource
import com.kitchenai.shared.data.remote.firebase.FirebaseSessionAdapter
import com.kitchenai.shared.data.repository.AccountRepository
import com.kitchenai.shared.domain.port.AccountRepositoryContract
import com.kitchenai.shared.domain.port.SessionRepositoryContract
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
import org.koin.core.module.Module
import org.koin.dsl.module

/** Bound to the port, never to the adapter: no use case may reach the Firebase type through DI. */
fun authModule(emulators: FirebaseEmulators?): Module =
    module {
        single<FirebaseAuth> {
            Firebase.auth.apply { emulators?.let { useEmulator(it.host, it.authPort) } }
        }

        single<SessionRepositoryContract> { FirebaseSessionAdapter(get(), get()) }

        // FirebaseFunctions is bound by agentDataModule, with the region the deployment uses.
        single { AccountRemoteDataSource(get(), get()) }
        single<AccountRepositoryContract> { AccountRepository(get()) }
    }
