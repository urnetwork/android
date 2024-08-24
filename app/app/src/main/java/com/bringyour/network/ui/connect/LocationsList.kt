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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun LocationsList(
    connectCountries: List<ConnectLocation>,
    promotedLocations: List<ConnectLocation>,
    onLocationSelect: (ConnectLocation) -> Unit,
    selectedLocation: ConnectLocation?,
    getLocationColor: (String) -> Color,
) {

    LazyColumn {

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Text("Promoted Locations")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        items(promotedLocations) { location ->
            ProviderRow(
                location = location.name,
                providerCount = location.providerCount,
                onClick = {
                    onLocationSelect(location)
                },
                isSelected = selectedLocation?.connectLocationId == location.connectLocationId,
                color = getLocationColor(location.connectLocationId.toString())
            )
        }

        item {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Text("Countries")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        items(connectCountries) { country ->
            ProviderRow(
                location = country.name,
                providerCount = country.providerCount,
                onClick = {
                    onLocationSelect(country)
                },
                isSelected = selectedLocation?.connectLocationId == country.connectLocationId,
                color = getLocationColor(country.countryCode)
            )
        }
    }
}
