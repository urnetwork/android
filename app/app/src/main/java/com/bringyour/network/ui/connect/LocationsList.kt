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
import android.graphics.Color as AndroidColor
import androidx.compose.ui.unit.dp
import com.bringyour.client.ConnectViewController

@Composable
fun LocationsList(
    // connectCountries: Map<String, ConnectLocation>,
    connectCountries: List<ConnectLocation>,
    promotedLocations: List<ConnectLocation>,
    // connectLocations: List<ConnectLocation>,
    connectVc: ConnectViewController?,
    onLocationSelect: () -> Unit,
    selectedLocation: ConnectLocation?
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

            val countryColor = connectVc?.getColorHex(location.connectLocationId.toString())

            ProviderRow(
                location = location.name,
                providerCount = location.providerCount,
                onClick = {
                    connectVc?.connect(location)
                    onLocationSelect()
                },
                isSelected = selectedLocation?.connectLocationId == location.connectLocationId,
                color = Color(AndroidColor.parseColor("#$countryColor"))
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

            val countryColor = connectVc?.getColorHex(country.countryCode)

            ProviderRow(
                location = country.name,
                providerCount = country.providerCount,
                onClick = {
                    connectVc?.connect(country)
                    onLocationSelect()
                },
                isSelected = selectedLocation?.connectLocationId == country.connectLocationId,
                color = Color(AndroidColor.parseColor("#$countryColor"))
            )
        }
    }
}
