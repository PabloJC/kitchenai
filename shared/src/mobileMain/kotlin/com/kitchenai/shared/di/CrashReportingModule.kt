package com.kitchenai.shared.di

import com.kitchenai.shared.data.system.CrashlyticsCrashReporter
import com.kitchenai.shared.domain.port.CrashReporter
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.crashlytics.crashlytics
import org.koin.core.module.Module
import org.koin.dsl.module

/** Out of [firebaseModule] because Crashlytics has no browser artefact (#413). */
val crashReportingModule: Module =
    module {
        single<CrashReporter> { CrashlyticsCrashReporter(Firebase.crashlytics) }
    }
