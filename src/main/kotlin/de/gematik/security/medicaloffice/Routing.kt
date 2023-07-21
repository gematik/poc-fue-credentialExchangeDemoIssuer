package de.gematik.security.medicaloffice

import de.gematik.security.credentialExchangeLib.credentialSubjects.Gender
import de.gematik.security.insurance.Insurant
import de.gematik.security.insurance.insurants
import de.gematik.security.medicaloffice.AuthorizedVaccine
import de.gematik.security.medicaloffice.Patient
import de.gematik.security.medicaloffice.Vaccination
import de.gematik.security.medicaloffice.patients
import de.gematik.security.qrCode
import de.gematik.security.toDate
import de.gematik.security.url
import io.ktor.server.application.*
import io.ktor.server.freemarker.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*


fun Application.configureRouting() {

    routing {
        staticResources("/static", "files")
        get("/") {
            call.respondRedirect("/static")
        }
        route("medicaloffice") {
            get {
                call.respond(FreeMarkerContent("index_medicaloffice.ftl", mapOf("customers" to patients)))
            }
            get("new") {
                call.respond(FreeMarkerContent("new_patient.ftl", model = null))
            }
            post {
                val formParameters = call.receiveParameters()
                val name = formParameters.getOrFail("name")
                val givenName = formParameters.getOrFail("givenname")
                val gender = formParameters.getOrFail("gender")
                val birthDate = formParameters.getOrFail("birthdate")
                val email = formParameters.getOrFail("email")
                val newEntry = Patient(
                    name,
                    givenName,
                    birthDate.toDate(),
                    if (gender.isBlank()) Gender.Undefined else Gender.valueOf(gender),
                    email
                )
                patients.add(newEntry)
                call.respondRedirect("/medicaloffice/${newEntry.id}")
            }
            get("{id}") {
                val id = call.parameters.getOrFail<Int>("id").toInt()
                call.respond(
                    FreeMarkerContent(
                        "show_patient.ftl",
                        mapOf("customer" to patients.find { it.id == id })
                    )
                )
            }
            get("{id}/edit") {
                val id = call.parameters.getOrFail<Int>("id").toInt()
                call.respond(FreeMarkerContent("edit_patient.ftl", mapOf("customer" to patients.find { it.id == id })))
            }
            post("{id}/edit") {
                val id = call.parameters.getOrFail<Int>("id").toInt()
                val formParameters = call.receiveParameters()
                when (formParameters.getOrFail("_action")) {
                    "update" -> {
                        val index = patients.indexOf(patients.find { it.id == id })
                        val name = formParameters.getOrFail("name")
                        val givenName = formParameters.getOrFail("givenname")
                        val gender = formParameters.getOrFail("gender")
                        val birthDate = formParameters.getOrFail("birthdate")
                        val email = formParameters.get("email")
                        patients[index].name = name
                        patients[index].givenName = givenName
                        patients[index].birthDate = birthDate.toDate()
                        patients[index].gender = if (gender.isBlank()) Gender.Undefined else Gender.valueOf(gender)
                        patients[index].email = email
                        call.respondRedirect("/medicaloffice/$id")
                    }

                    "delete" -> {
                        insurants.removeIf { it.id == id }
                        call.respondRedirect("/medicaloffice")
                    }
                }
            }
            get("{id}/addVaccination") {
                val id = call.parameters.getOrFail<Int>("id").toInt()
                call.respond(FreeMarkerContent("addVaccination.ftl", mapOf("data" to object {
                    val customer = insurants.find { it.id == id }
                    val vaccines = AuthorizedVaccine.values().toList()
                })))
            }
            post("{id}/addVaccination") {
                val id = call.parameters.getOrFail<Int>("id").toInt()
                val formParameters = call.receiveParameters()
                val index = patients.indexOf(patients.find { it.id == id })
                val vaccination = Vaccination(
                    dateOfVaccination = formParameters.getOrFail("dateOfVaccination").toDate(),
                    atcCode = formParameters.getOrFail("atcCode"),
                    vaccine = AuthorizedVaccine.valueOf(formParameters.getOrFail("vaccine")),
                    batchNumber = formParameters.getOrFail("batchNumber"),
                    order = formParameters.get("order")!!.toInt()
                )
                patients[index].vaccinations.add(vaccination)
                call.respondRedirect("/medicaloffice/$id")
            }
            get("{id}/invitation") {
                val invitationId = call.parameters.getOrFail<String>("id")
                call.respond(FreeMarkerContent("showInvitationVaccination.ftl", mapOf("invitation" to object {
                    private val patient = patients.find { it.vaccinations.firstOrNull() { it.invitation.id == invitationId } != null }
                    private val vaccination = patient?.vaccinations?.firstOrNull { it.invitation.id == invitationId }
                    val givenName = patient?.givenName
                    val name = patient?.name
                    val dateOfVaccination = vaccination?.dateOfVaccination
                    val url = vaccination?.invitation?.url
                    val qrCode = vaccination?.invitation?.qrCode
                })))
            }
        }
    }
}