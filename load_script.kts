#!/usr/bin/env kscript
//DEPS org.jetbrains.kotlin:kotlin-stdlib:1.3.11,com.github.kittinunf.fuel:fuel:1.16.0,com.xenomachina:kotlin-argparser:2.0.7,com.jakewharton.fliptables:fliptables:1.0.2,org.jetbrains.kotlinx:kotlinx-coroutines-core:1.0.1
//INCLUDE LoadScriptConfig.kt
//INCLUDE HttpMethodType.kt

import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.interceptors.cUrlLoggingRequestInterceptor
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.httpDelete
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpHead
import com.github.kittinunf.fuel.httpPatch
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.httpPut
import com.github.kittinunf.fuel.core.Deserializable
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.awaitResponse
import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default
import com.jakewharton.fliptables.FlipTable
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

val config = ArgParser(args).parseInto(::LoadScriptConfig)

FuelManager.instance.basePath = config.websiteUrl
FuelManager.instance.baseHeaders = config.headers
if(config.isVerbose) {
    FuelManager.instance.addRequestInterceptor(cUrlLoggingRequestInterceptor())
    println("Sending HTTP [${config.httpMethod.name}] requests to [${config.websiteUrl}] using [${config.threads}] workers sending [${config.count}] requests each and a " +
        "delay of [${config.delay}] milliseconds. Headers sent with each request [${config.headers}] Body [${config.body}].")
}

val results = sendRequests()
                .map { arrayOf(it.key.toString(), it.value.toString()) }
                .toTypedArray()

fun sendRequests(): Map<Int, Int> {
    return runBlocking {
        (1..config.threads).map {
            async {
                config.startNewJob()
            }
        }.map {
            it.await()
        }.fold(mutableMapOf<Int, Int>()) { first, second ->
            first.mergeWith(second)
        }.toSortedMap()
    }
}

println(FlipTable.of(arrayOf("HTTP Code", "Count"), results))

fun MutableMap<Int, Int>.mergeWith(mergedMap: Map<Int, Int>): MutableMap<Int, Int> {
    mergedMap.forEach { (key, value) ->
        val newValue = (get(key) ?: 0) + value
        put(key, newValue)
    }
    return this
}
