package com.kitchenai.admin.di

import com.kitchenai.admin.catalogue.CatalogueWriter
import com.kitchenai.admin.catalogue.FirebaseCatalogueWriter
import com.kitchenai.admin.catalogue.FirebasePictureStore
import com.kitchenai.admin.catalogue.PictureStore
import com.kitchenai.admin.catalogue.resizeToWebp
import com.kitchenai.admin.config.AdminFirebaseConfig
import com.kitchenai.admin.firebase.adminFirestore
import com.kitchenai.admin.ingredients.IngredientsModel
import com.kitchenai.admin.requests.FirebaseItemConverter
import com.kitchenai.admin.requests.FirestoreIngredientRequestStore
import com.kitchenai.admin.requests.IngredientRequestStore
import com.kitchenai.admin.requests.ItemConverter
import com.kitchenai.admin.requests.RequestsModel
import com.kitchenai.shared.core.DefaultDispatcherProvider
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.data.remote.firebase.FirestorePaths
import com.kitchenai.shared.data.repository.FirestoreIngredientRepository
import com.kitchenai.shared.data.repository.FirestoreTaxonomyRepository
import com.kitchenai.shared.data.system.SystemTimeProvider
import com.kitchenai.shared.domain.port.IngredientRepositoryContract
import com.kitchenai.shared.domain.port.TaxonomyRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.functions.functions
import dev.gitlive.firebase.storage.storage
import org.koin.core.module.Module
import org.koin.dsl.module

/** Reads through `:shared`'s own repositories; writes through the admin's, which the apps never get. */
fun adminModule(config: AdminFirebaseConfig): Module =
    module {
        single<DispatcherProvider> { DefaultDispatcherProvider() }
        single<TimeProvider> { SystemTimeProvider() }
        // Not `:shared`'s firebaseModule: the admin needs no offline cache, and owns its emulator wiring.
        single { adminFirestore(config) }
        single { FirestorePaths(get()) }
        single<IngredientRepositoryContract> { FirestoreIngredientRepository(get(), get()) }
        single<TaxonomyRepositoryContract> { FirestoreTaxonomyRepository(get(), get()) }
        single<CatalogueWriter> { FirebaseCatalogueWriter(Firebase.functions(config.functionsRegion), get()) }
        single<PictureStore> { FirebasePictureStore(Firebase.storage, get()) }
        single<IngredientRequestStore> { FirestoreIngredientRequestStore(get()) }
        single<ItemConverter> { FirebaseItemConverter(Firebase.functions(config.functionsRegion)) }
        factory { IngredientsModel(get(), get(), get(), get(), resize = ::resizeToWebp) }
        // Its own IngredientsModel: the editor it opens is not the ingredients section's.
        factory { RequestsModel(get(), get(), get(), get()) }
    }
