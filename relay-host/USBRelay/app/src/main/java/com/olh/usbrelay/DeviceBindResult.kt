package com.olh.usbrelay

import android.content.Context

sealed class DeviceBindResult {
    data class Success(val busid: String) : DeviceBindResult()
    sealed class Failure : DeviceBindResult() {
        object DeviceNotFound : Failure()
        object DeviceInUse : Failure()
        object DeviceOpenFailed : Failure()
        object GetDescriptorFailed : Failure()
        object GetConfigFailed : Failure()
        object ClaimInterfaceFailed : Failure()
        object UnknownError : Failure()

        fun getMessage(context: Context): String = when (this) {
            DeviceNotFound -> "Device not found"
            DeviceInUse -> "Device already in use"
            DeviceOpenFailed -> "Failed to open device"
            GetDescriptorFailed -> "Failed to get descriptor"
            GetConfigFailed -> "Failed to get config"
            ClaimInterfaceFailed -> "Failed to claim interface"
            UnknownError -> "Unknown error"
        }
    }

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getBusidOrNull(): String? = (this as? Success)?.busid
}
