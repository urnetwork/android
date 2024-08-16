package com.bringyour.network.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import com.bringyour.client.ConnectLocation
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bringyour.client.ConnectViewController

@Composable
fun LocationsList(
    connectCountries: Map<String, ConnectLocation>,
    connectVc: ConnectViewController?,
    onLocationSelect: () -> Unit,
    selectedLocation: ConnectLocation?
) {

    val countryList = connectCountries.entries.toList()

    LazyColumn {

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Text("Countries")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        items(countryList) { country ->
            ProviderRow(
                location = country.value.name,
                providerCount = country.value.providerCount,
                onClick = {
                    connectVc?.connect(country.value)
                    onLocationSelect()
                },
                isSelected = selectedLocation?.connectLocationId == country.value.connectLocationId
            )
        }
    }
}
