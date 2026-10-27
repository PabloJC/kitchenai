package com.kitchenai.ui.di

import com.kitchenai.ui.presentation.reminder.ExpiryReminderReadsDelegate
import com.kitchenai.ui.presentation.reminder.ExpiryReminderViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Expiry reminders. The scheduler comes from the platform module, the use cases from `:shared`. */
val reminderPresentationModule: Module =
    module {
        factory { ExpiryReminderReadsDelegate(get(), get(), get(), get()) }
        viewModel { ExpiryReminderViewModel(get(), get(), get(), get()) }
    }
