package com.bringyour.network.ui.wallet

import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.viewModelScope
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.solana.publickey.SolanaPublicKey

@HiltViewModel
class SagaViewModel @Inject constructor(): ViewModel() {

    private var solanaWalletAdapter by mutableStateOf<MobileWalletAdapter?>(null)

    var isSolanaSaga by mutableStateOf(false)

    private var sender by mutableStateOf<ActivityResultSender?>(null)

    val initSolanaWalletAdapter = {
        val solanaUri = Uri.parse("https://ur.io")
        val iconUri = Uri.parse("favicon.svg")
        val identityName = "URnetwork"

        solanaWalletAdapter = MobileWalletAdapter(
            connectionIdentity = ConnectionIdentity(
                identityUri = solanaUri,
                iconUri = iconUri,
                identityName = identityName
            )
        )
    }

    val getSagaWalletAddress:  ((String?) -> Unit) -> Unit = { onResult ->

        solanaWalletAdapter?.let { walletAdapter ->

            sender?.let { activitySender ->

                viewModelScope.launch {
                    val address = getWalletAddress(walletAdapter, activitySender)
                    onResult(address)
                }
            } ?: onResult(null)
        } ?: onResult(null)
    }

    private suspend fun getWalletAddress(walletAdapter: MobileWalletAdapter, activitySender: ActivityResultSender): String? {
        return when (val result = walletAdapter.connect(activitySender)) {
            is TransactionResult.Success -> {
                val pubKey = SolanaPublicKey(result.authResult.publicKey)
                pubKey.base58()
            }
            is TransactionResult.NoWalletFound -> {
                Log.i("SolanaViewModel", "No MWA compatible wallet app found on device.")
                null
            }
            is TransactionResult.Failure -> {
                Log.i("SolanaViewModel", "Error connecting to wallet: " + result.e.message)
                null
            }
        }
    }

    val setSender: (ActivityResultSender) -> Unit = { newSender ->
        sender = newSender
    }

    init {
        isSolanaSaga = Build.MODEL.equals("SAGA", ignoreCase = true)

        if (isSolanaSaga) {
            initSolanaWalletAdapter()
        }
    }

}