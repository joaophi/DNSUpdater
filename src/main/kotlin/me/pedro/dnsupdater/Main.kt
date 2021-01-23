package me.pedro.dnsupdater

import com.squareup.moshi.JsonClass
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import okhttp3.Credentials
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import retrofit2.http.*
import java.time.LocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.minutes

interface IPify {
    @JsonClass(generateAdapter = true)
    data class IP(
        val ip: String
    )

    @GET("https://api.ipify.org?format=json")
    suspend fun getIP(): IP
}

interface NameCom {
    @JsonClass(generateAdapter = true)
    data class Record(
        val id: Int,
        val type: String,
        val answer: String,
    )

    @JsonClass(generateAdapter = true)
    data class Records(
        val records: List<Record>,
    )

    @GET("/v4/domains/{domainName}/records")
    suspend fun listRecords(
        @Header("Authorization") authorization: String,
        @Path("domainName") domainName: String,
    ): Records

    @PUT("/v4/domains/{domainName}/records/{id}")
    suspend fun updateRecords(
        @Header("Authorization") authorization: String,
        @Path("domainName") domain: String,
        @Path("id") id: Int,
        @Body record: Record,
    )
}

fun log(msg: String) = println("${LocalDateTime.now()} - $msg")

@ExperimentalTime
suspend fun main() {
    val domain = System.getenv("DOMAIN").orEmpty().ifBlank { throw Exception("DOMAIN required") }
    val username = System.getenv("USERNAME").orEmpty().ifBlank { throw Exception("USERNAME required") }
    val token = System.getenv("TOKEN").orEmpty().ifBlank { throw Exception("TOKEN required") }
    val authorization = Credentials.basic(username, token)

    val retrofit = Retrofit.Builder()
        .addConverterFactory(MoshiConverterFactory.create())
        .baseUrl("https://api.name.com/")
        .build()

    val iPify: IPify = retrofit.create()

    val nameCom: NameCom = retrofit.create()

    val ipFlow: Flow<String> = flow {
        while (true) {
            val ip = iPify.getIP().ip
            log("Got ip $ip")
            emit(ip)
            delay(5.minutes)
        }
    }

    log("Starting")
    ipFlow
        .map { ip ->
            nameCom
                .listRecords(authorization, domain)
                .records
                .filter { it.answer != ip }
                .map { it.copy(answer = ip) }
        }
        .onEach { changes ->
            if (changes.isEmpty()) {
                log("No changes")
                return@onEach
            }

            log("Changes $changes")
            changes.onEach { nameCom.updateRecords(authorization, domain, it.id, it) }
            log("Updated")
        }
        .retry {
            log("error ${it.message ?: it}")
            delay(2.minutes)
            true
        }
        .collect()
}