package com.bringyour.network.ui.account

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.buttonTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun URNodeCarousel() {

    data class CarouselItem(
        val id: Int,
        @DrawableRes val imageResId: Int,
        val contentDescription: String
    )

    val items = remember() {
        listOf(
            CarouselItem(0, R.drawable.urnode_01, "URnode 1"),
            CarouselItem(1, R.drawable.urnode_02, "URnode 2"),
            CarouselItem(2, R.drawable.urnode_03, "URnode 3"),
        )
    }

    Column() {

        Text(
            "URnode",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        HorizontalUncontainedCarousel(
            state = rememberCarouselState { items.count() },
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(top = 16.dp, bottom = 16.dp),
            itemWidth = 346.dp,
            itemSpacing = 8.dp,
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) { i ->
            val item = items[i]
            Image(
                modifier = Modifier
                    .height(300.dp)
                    .maskClip(MaterialTheme.shapes.extraLarge),
                painter = painterResource(id = item.imageResId),
                contentDescription = item.contentDescription,
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        URButton(
            onClick = {}
        ) { buttonTextStyle ->
            Text(
                "Preorder Now",
                style = buttonTextStyle
            )
        }

    }

}