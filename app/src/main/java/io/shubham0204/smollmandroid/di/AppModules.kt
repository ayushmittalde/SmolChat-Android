package io.shubham0204.smollmandroid.di

import android.content.Context
import io.shubham0204.smollmandroid.ui.screens.sms_analysis.SmsListViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

// Import the generated module object (top-level val)
import org.koin.ksp.generated.io_shubham0204_smollmandroid_KoinAppModule

// Assign the generated module to a variable
val generatedModule: Module = io_shubham0204_smollmandroid_KoinAppModule

// Your manual override module, e.g. if you want a parameterized ViewModel
val smsListViewModelModule: Module = module {
    viewModel { (context: Context) ->
        SmsListViewModel(
            context = context,
            modelsRepository = get(),
            smolLMManager = get()
        )
    }
}

// Combine generated module and manual overrides
val appModules: List<Module> = listOf(
    generatedModule,
    smsListViewModelModule // Your manual override after generated module
)
