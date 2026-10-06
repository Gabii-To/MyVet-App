package com.application.myvet.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class LoginResponse(val token: String, val user: UserDto)
@Serializable data class UserDto(val id: Int, val name: String, val email: String, val role: String)
@Serializable data class DataResponse<T>(val data: T)
@Serializable data class PetDto(val id: Int, val name: String, val species: String, val breed: String? = null, val birth_date: String)
@Serializable data class VeterinarianDto(val id: Int, val name: String, val email: String)
@Serializable data class AppointmentDto(
    val id: Int,
    val pet_id: Int,
    val owner_id: Int,
    val veterinarian_id: Int,
    val appointment_date: String,
    val appointment_time: String,
    val duration_minutes: Int = 15,
    val reason: String,
    val status: String,
    val cancel_reason: String? = null,
)
@Serializable data class SlotDto(val time: String, val available: Boolean)
@Serializable data class AvailabilityResponse(val veterinarian_id: Int, val date: String, val slots: List<SlotDto>)
@Serializable data class CreateAppointmentRequest(val pet_id: Int, val veterinarian_id: Int, val appointment_date: String, val appointment_time: String, val reason: String)
@Serializable data class RescheduleRequest(val appointment_date: String, val appointment_time: String)
@Serializable data class CancelRequest(val reason: String? = null)
@Serializable data class ExtendRequest(val minutes: Int)
@Serializable data class InvitationDto(val id: Int, val veterinarian_id: Int, val code: String, val status: String, val used_by_owner_id: Int? = null)
@Serializable data class RedeemInvitationRequest(val code: String)
@Serializable data class PatientDto(val id: Int, val name: String, val species: String, val breed: String? = null, val owner_name: String, val age: String)
@Serializable data class ErrorResponse(val error: String? = null, val message: String? = null)

/** Error devuelto por la API con un mensaje legible para mostrar en pantalla. */
class ApiException(message: String) : Exception(message)

class MyVetApi(private val baseUrl: String = ApiConfig.baseUrl) {
    private val client = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) }
        // Si la API no responde, falla en 10 s con un mensaje en vez de quedarse cargando.
        install(HttpTimeout) { requestTimeoutMillis = 10_000 }
    }

    suspend fun login(email: String, password: String): LoginResponse = client.post("$baseUrl/auth/login") {
        contentType(ContentType.Application.Json); setBody(LoginRequest(email, password))
    }.body()

    suspend fun appointments(token: String, status: String? = null): List<AppointmentDto> = client.get("$baseUrl/appointments" + (status?.let { "?status=$it" } ?: "")) { bearerAuth(token) }.body<DataResponse<List<AppointmentDto>>>().data
    suspend fun pets(token: String): List<PetDto> = client.get("$baseUrl/pets") { bearerAuth(token) }.body<DataResponse<List<PetDto>>>().data
    suspend fun veterinarians(token: String): List<VeterinarianDto> = client.get("$baseUrl/veterinarians") { bearerAuth(token) }.body<DataResponse<List<VeterinarianDto>>>().data
    suspend fun availability(token: String, veterinarianId: Int, date: String): AvailabilityResponse = client.get("$baseUrl/veterinarians/$veterinarianId/availability?date=$date") { bearerAuth(token) }.body()
    suspend fun createAppointment(token: String, request: CreateAppointmentRequest): AppointmentDto = client.post("$baseUrl/appointments") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(request) }.body<DataResponse<AppointmentDto>>().data
    suspend fun reschedule(token: String, id: Int, request: RescheduleRequest): AppointmentDto = client.put("$baseUrl/appointments/$id") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(request) }.body<DataResponse<AppointmentDto>>().data
    suspend fun cancel(token: String, id: Int, reason: String? = null): AppointmentDto = client.post("$baseUrl/appointments/$id/cancel") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(CancelRequest(reason)) }.body<DataResponse<AppointmentDto>>().data
    suspend fun extend(token: String, id: Int, minutes: Int): AppointmentDto = client.post("$baseUrl/appointments/$id/extend") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(ExtendRequest(minutes)) }.body<DataResponse<AppointmentDto>>().data

    // Veterinario: pacientes e invitaciones de un solo uso.
    suspend fun patients(token: String): List<PatientDto> = client.get("$baseUrl/veterinarian/patients") { bearerAuth(token) }.body<DataResponse<List<PatientDto>>>().data
    suspend fun invitations(token: String): List<InvitationDto> = client.get("$baseUrl/veterinarian/invitations") { bearerAuth(token) }.body<DataResponse<List<InvitationDto>>>().data
    suspend fun createInvitation(token: String): InvitationDto = client.post("$baseUrl/veterinarian/invitations") { bearerAuth(token) }.body<DataResponse<InvitationDto>>().data

    // Propietario: canjea el código para agregar al veterinario a su lista.
    suspend fun redeemInvitation(token: String, code: String): InvitationDto {
        val response = client.post("$baseUrl/veterinarians/redeem-invitation") {
            bearerAuth(token); contentType(ContentType.Application.Json); setBody(RedeemInvitationRequest(code.trim()))
        }
        if (!response.status.isSuccess()) {
            val message = runCatching { response.body<ErrorResponse>().message }.getOrNull()
            throw ApiException(message ?: "No se pudo canjear el código (error ${response.status.value}).")
        }
        return response.body<DataResponse<InvitationDto>>().data
    }
}