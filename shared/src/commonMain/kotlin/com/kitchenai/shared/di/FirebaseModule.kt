package com.kitchenai.shared.di

import com.kitchenai.shared.data.remote.firebase.FirestorePaths
import com.kitchenai.shared.data.system.SystemTimeProvider
import com.kitchenai.shared.data.system.UuidIdGenerator
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.TimeProvider
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.firestoreSettings
import dev.gitlive.firebase.firestore.persistentCacheSettings
import org.koin.core.module.Module
import org.koin.dsl.module

/** Firestore plus the ambient platform singletons every repository needs: a clock and an id source. */
val firebaseModule: Module =
    module {
        single {
            Firebase.firestore.apply {
                // Persistence is the default on both platforms; it is stated here because the
                // shopping list reads its own writes while offline. Turning it off breaks that
                // screen, not just its latency. Settings must be applied before the first call.
                settings = firestoreSettings { cacheSettings = persistentCacheSettings { } }
            }
        }

        single { FirestorePaths(get()) }

        single<TimeProvider> { SystemTimeProvider() }

        single<IdGenerator> { UuidIdGenerator() }
    }
