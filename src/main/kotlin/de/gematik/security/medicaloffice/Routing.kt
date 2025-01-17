/*
 * Copyright 2021-2024, gematik GmbH
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the
 * European Commission – subsequent versions of the EUPL (the "Licence").
 * You may not use this work except in compliance with the Licence.
 *
 * You find a copy of the Licence in the "Licence" file or at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.
 * In case of changes by gematik find details in the "Readme" file.
 *
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 */

package de.gematik.security.medicaloffice

import de.gematik.security.credentialExchangeLib.credentialSubjects.Gender
import de.gematik.security.credentialExchangeLib.extensions.toIsoInstantString
import de.gematik.security.credentialExchangeLib.json
import de.gematik.security.hostName
import de.gematik.security.qrCode
import de.gematik.security.url
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.freemarker.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import kotlinx.serialization.encodeToString
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter


fun Application.configureRouting() {

    routing {
        staticResources("/static", "files")
        get("/") {
            call.respond(FreeMarkerContent("index.ftl", mapOf("url" to object {
                val address = hostName
                val lastCallingRemoteAddress = Controller.lastCallingRemoteAddress ?: de.gematik.security.insurance.Controller.lastCallingRemoteAddress
            })))
        }
        route("medicaloffice") {
            get {
                call.respond(FreeMarkerContent("index_medicaloffice.ftl", mapOf("customers" to patients)))
                patientsDataStatus.update = false
            }
            get("update_status") {
                call.respondText(contentType = ContentType.defaultForFileExtension("json"), HttpStatusCode.OK){
                    json.encodeToString(patientsDataStatus)
                }
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
                    LocalDate.parse(birthDate, DateTimeFormatter.ISO_DATE)
                        .atTime(12,0)
                        .atZone(ZoneId.of("UTC"))
                        .toIsoInstantString(),
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
                        mapOf("patient" to patients.find { it.id == id })
                    )
                )
                patientsDataStatus.update = false
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
                        patients[index].birthDate = LocalDate.parse(birthDate, DateTimeFormatter.ISO_DATE)
                            .atTime(12,0)
                            .atZone(ZoneId.of("UTC"))
                            .toIsoInstantString()
                        patients[index].gender = if (gender.isBlank()) Gender.Undefined else Gender.valueOf(gender)
                        patients[index].email = email
                        call.respondRedirect("/medicaloffice/$id")
                    }

                    "delete" -> {
                        patients.removeIf { it.id == id }
                        call.respondRedirect("/medicaloffice")
                    }
                }
            }
            get("{id}/addVaccination") {
                val id = call.parameters.getOrFail<Int>("id").toInt()
                call.respond(FreeMarkerContent("addVaccination.ftl", mapOf("data" to object {
                    val customer = patients.find { it.id == id }
                    val vaccines = AuthorizedVaccine.values().toList()
                })))
            }
            post("{id}/addVaccination") {
                val id = call.parameters.getOrFail<Int>("id").toInt()
                val formParameters = call.receiveParameters()
                val index = patients.indexOf(patients.find { it.id == id })
                val vaccination = Vaccination(
                    dateOfVaccination = formParameters.getOrFail("dateOfVaccination").let {
                        if (it.isBlank()) {
                            ZonedDateTime.now().toIsoInstantString()
                        } else {
                            LocalDate.parse(it, DateTimeFormatter.ISO_DATE)
                                .atTime(12,0)
                                .atZone(ZoneId.of("UTC"))
                                .toIsoInstantString()
                        }
                    },
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
                    private val patient =
                        patients.find { it.vaccinations.firstOrNull() { it.invitation.id == invitationId } != null }
                    private val vaccination = patient?.vaccinations?.firstOrNull { it.invitation.id == invitationId }
                    val givenName = patient?.givenName
                    val name = patient?.name
                    val dateOfVaccination = vaccination?.dateOfVaccination
                    val url = vaccination?.invitation?.url
                    val qrCode = vaccination?.invitation?.qrCode
                })))
            }
            get("/checkin") {
                call.respond(
                    FreeMarkerContent(
                        "showInvitationCheckIn.ftl", mapOf(
                            "invitation" to object {
                                private val invitation = Controller.invitation
                                val url = invitation.url
                                val qrCode = invitation.qrCode
                            }
                        )
                    )
                )
            }
        }
    }
}
