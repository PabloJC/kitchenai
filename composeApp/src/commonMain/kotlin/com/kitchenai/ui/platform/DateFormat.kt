package com.kitchenai.ui.platform

import kotlinx.datetime.LocalDate

/** A calendar date in the device locale's medium style ("14 oct 2026", "Oct 14, 2026"). */
expect fun platformFormatDate(date: LocalDate): String
