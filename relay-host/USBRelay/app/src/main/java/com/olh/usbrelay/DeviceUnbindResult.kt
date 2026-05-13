package com.olh.usbrelay

import android.content.Context

sealed class DeviceUnbindResult {
    object Success : DeviceUnbindResult()
    sealed class Failure : DeviceUnbindResult() {
        object DeviceNotFound : Failure()
        object DeviceInUse : Failure()
        object UnknownError : Failure()

        fun getMessage(context: Context): String = when (this) {
            DeviceNotFound -> "Device not found"
            DeviceInUse -> "Device in use"
            UnknownError -> "Unknown error"
        }
    }

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure
}
