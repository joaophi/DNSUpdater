package me.pedro.dnsupdater

import com.squareup.moshi.JsonClass
import kotlinx.coroutines.delay
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

    while (true) {
        try {
            val ip = iPify.getIP().ip
            log("Got ip $ip")

            nameCom
                .listRecords(authorization, domain)
                .records
                .filter { it.answer != ip }
                .map { it.copy(answer = ip) }
                .onEach { record ->
                    nameCom.updateRecords(authorization, domain, record.id, record)
                    log("Updated $record")
                }
        } catch (e: Exception) {
            log("error ${e.message ?: e}")
        }
        delay(5.minutes)
    }
}