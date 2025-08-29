package com.bringyour.network.utils

import com.bringyour.sdk.Float64List
import com.bringyour.sdk.IntList

val sdkIntListToArray: (IntList) -> MutableList<Long> = { list ->

    val n = list.len()
    var arr = mutableListOf<Long>()

    for (i in 0 until n) {

        val item = list.get(i)
        arr.add(item)

    }

    arr
}

val sdkFloat64ListToArray: (Float64List) -> MutableList<Double> = { list ->

    val n = list.len()
    var arr = mutableListOf<Double>()

    for (i in 0 until n) {

        val item = list.get(i)
        arr.add(item)

    }

    arr
}