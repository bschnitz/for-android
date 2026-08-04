package chat.stoat.di

import chat.stoat.instances.InstanceStore
import chat.stoat.instances.InstanceSwitcher
import chat.stoat.persistence.KVStorage
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

//
val appModule = module {
    single { KVStorage(androidContext()) }
    single { InstanceStore(androidContext()) }
    single { InstanceSwitcher(get(), get()) }
}
