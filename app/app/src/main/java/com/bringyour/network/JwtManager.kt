package com.bringyour.network

import android.util.Log
import com.bringyour.sdk.ByJwt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JwtManager @Inject constructor() {

    private val _jwtFlow = MutableStateFlow<ByJwt?>(null)
    val jwtFlow: StateFlow<ByJwt?> = _jwtFlow.asStateFlow()

    fun updateJwt(newJwt: ByJwt) {
        _jwtFlow.value = newJwt
    }

}