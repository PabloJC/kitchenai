package com.kitchenai.shared.data.repository

import com.kitchenai.shared.core.getOrElse
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.UserId

/**
 * Interim only: `FirestorePaths` still builds `users/{uid}/...` paths, unaware `KitchenId`
 * exists — #192 replaces this once `kitchens/{kitchenId}/...` paths and rules land, and every
 * call site below is deleted with it. Both id types share the same non-blank validation, so this
 * can never actually fail; it exists purely to satisfy the type system while #191's renamed
 * contracts and #192's paths are mid-migration.
 */
internal fun KitchenId.asUserId(): UserId =
    UserId.of(value).getOrElse {
        error("KitchenId already validated this: $value")
    }
