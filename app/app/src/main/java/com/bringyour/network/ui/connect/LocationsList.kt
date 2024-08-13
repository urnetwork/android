package com.bringyour.network.ui.connect

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import com.bringyour.client.ConnectLocation
import androidx.compose.foundation.lazy.items
import com.bringyour.client.ConnectViewController

@Composable
fun LocationsList(
    connectCountries: Map<String, ConnectLocation>,
    connectVc: ConnectViewController?,
    onLocationSelect: () -> Unit
) {

    val countryList = connectCountries.entries.toList()

    LazyColumn {

        items(countryList) { country ->
            ProviderRow(
                location = country.value.name,
                providerCount = country.value.providerCount,
                onClick = {
                    connectVc?.connect(country.value)
                    onLocationSelect()
                }
            )
        }
    }
}
