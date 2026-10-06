package com.application.myvet.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.application.myvet.data.network.AppointmentDto
import com.application.myvet.data.network.MyVetApi
import com.application.myvet.data.network.PetDto
import com.application.myvet.data.network.VeterinarianDto
import kotlinx.coroutines.launch

private const val VETERINARIAN_NAME = "Dra. Valentina Ruiz"
private val appointmentTimes = listOf("09:00", "09:15", "09:30", "09:45", "10:00", "10:15", "10:30", "10:45", "11:00", "11:15", "11:30", "11:45")
private val availableDates = listOf("2026-10-06", "2026-10-07", "2026-10-08")

private data class Appointment(
    val id: Int,
    val petName: String,
    val species: String,
    val ownerName: String,
    val date: String,
    val time: String,
    val reason: String,
    val veterinarian: String,
    val status: String,
    val accent: Color,
)

private data class Veterinarian(val id: Int, val name: String, val specialty: String)

private fun VeterinarianDto.toUiVeterinarian() = Veterinarian(id, name, "Veterinaria")

private fun AppointmentDto.toUiAppointment(pets: List<PetDto>, veterinarians: List<VeterinarianDto>): Appointment {
    val pet = pets.firstOrNull { it.id == pet_id }
    val veterinarian = veterinarians.firstOrNull { it.id == veterinarian_id }
    return Appointment(
        id = id,
        petName = pet?.name ?: "Paciente #$pet_id",
        species = pet?.species ?: "Paciente",
        ownerName = "Propietario",
        date = appointment_date,
        time = appointment_time,
        reason = reason,
        veterinarian = veterinarian?.name ?: VETERINARIAN_NAME,
        status = status,
        accent = if (status == "pending") Color(0xFFE2A33A) else Color(0xFF63A98B),
    )
}

private enum class AppRole(val label: String) { OWNER("Paciente / propietario"), VETERINARIAN("Veterinario") }
private enum class AppScreen { HOME, CREATE_CONSULTATION }
private enum class DrawerDestination(val label: String, val emoji: String) {
    HOME("Inicio", "⌂"), PETS("Mis mascotas", "🐾"), CONSULTATIONS("Mis consultas", "▤"), VETERINARIANS("Mis veterinarios", "✚"), INVITATIONS("Invitaciones", "⌁")
}

private fun itemVisibleForRole(item: DrawerDestination, role: AppRole) = when (role) {
    AppRole.OWNER -> item != DrawerDestination.INVITATIONS
    AppRole.VETERINARIAN -> item != DrawerDestination.VETERINARIANS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen() {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val api = remember { MyVetApi() }
    val appointments = remember { mutableStateListOf<Appointment>() }
    val veterinarians = remember { mutableStateListOf<Veterinarian>() }
    val pets = remember { mutableStateListOf<PetDto>() }
    var role by remember { mutableStateOf(AppRole.OWNER) }
    var destination by remember { mutableStateOf(DrawerDestination.HOME) }
    var screen by remember { mutableStateOf(AppScreen.HOME) }
    var sessionToken by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var networkError by remember { mutableStateOf<String?>(null) }

    suspend fun refreshAgenda() {
        loading = true
        networkError = null
        runCatching {
            val credentials = if (role == AppRole.OWNER) "owner@myvet.test" to "owner123" else "vet@myvet.test" to "vet123"
            val session = api.login(credentials.first, credentials.second)
            sessionToken = session.token
            val apiPets = if (role == AppRole.OWNER) api.pets(session.token) else emptyList()
            val apiVeterinarians = if (role == AppRole.OWNER) api.veterinarians(session.token) else emptyList()
            pets.clear(); pets.addAll(apiPets)
            veterinarians.clear(); veterinarians.addAll(apiVeterinarians.map { it.toUiVeterinarian() })
            appointments.clear(); appointments.addAll(api.appointments(session.token).map { it.toUiAppointment(apiPets, apiVeterinarians) })
        }.onFailure { networkError = "No pudimos conectar con la API. Verificá que XAMPP esté activo." }
        loading = false
    }
    LaunchedEffect(role) { refreshAgenda() }

    if (screen == AppScreen.CREATE_CONSULTATION) {
        CreateConsultationScreen(
            appointments = appointments,
            pets = pets,
            veterinarians = veterinarians,
            api = api,
            token = sessionToken,
            onBack = { screen = AppScreen.HOME },
            onSave = { scope.launch { refreshAgenda() }; screen = AppScreen.HOME },
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 24.dp)) {
                    Text("🐾  MyVet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(role.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = { role = if (role == AppRole.OWNER) AppRole.VETERINARIAN else AppRole.OWNER },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Cambiar a ${if (role == AppRole.OWNER) "veterinario" else "propietario"}") }
                    Spacer(Modifier.height(20.dp))
                    DrawerDestination.entries.filter { itemVisibleForRole(it, role) }.forEach { item ->
                        NavigationDrawerItem(
                            label = { Text(item.label) }, icon = { Text(item.emoji) }, selected = destination == item,
                            onClick = { destination = item; scope.launch { drawerState.close() } },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text("API conectada", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (destination == DrawerDestination.HOME) if (role == AppRole.OWNER) "Buenos días 👋" else "Mi jornada"
                            else if (role == AppRole.VETERINARIAN && destination == DrawerDestination.PETS) "Mis pacientes"
                            else destination.label,
                        )
                    },
                    navigationIcon = { TextButton(onClick = { scope.launch { drawerState.open() } }) { Text("☰", style = MaterialTheme.typography.titleLarge) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
        ) { padding ->
            when (destination) {
                DrawerDestination.HOME -> HomeContent(appointments, role, loading, networkError, { screen = AppScreen.CREATE_CONSULTATION }, Modifier.padding(padding))
                DrawerDestination.PETS -> if (role == AppRole.OWNER) {
                    PlaceholderContent("Mis mascotas", "Encontrá fichas, antecedentes y próximos controles de cada paciente.", "🐾", Modifier.padding(padding))
                } else {
                    PatientsContent(Modifier.padding(padding))
                }
                DrawerDestination.CONSULTATIONS -> PlaceholderContent("Mis consultas", "Próximamente: filtros para pendientes, aprobadas, efectuadas y canceladas.", "▤", Modifier.padding(padding))
                DrawerDestination.VETERINARIANS -> VeterinariansContent(veterinarians, Modifier.padding(padding))
                DrawerDestination.INVITATIONS -> InvitationContent(Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun HomeContent(appointments: List<Appointment>, role: AppRole, loading: Boolean, networkError: String?, onCreate: () -> Unit, modifier: Modifier) {
    val heading = if (role == AppRole.OWNER) "Tu agenda de hoy" else "Consultas a realizar"
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(heading, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Lunes, 6 de octubre", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (loading) item { Text("Actualizando agenda...") }
        networkError?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        item { NextAppointmentCard(appointments.firstOrNull(), role) }
        if (role == AppRole.OWNER) item {
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("+  Reservar consulta", fontWeight = FontWeight.SemiBold) }
        }
        item { Text(if (role == AppRole.OWNER) "Próximos turnos" else "Agenda de hoy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(appointments.drop(1)) { AppointmentRow(it, role) }
    }
}

@Composable
private fun NextAppointmentCard(appointment: Appointment?, role: AppRole) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        if (appointment == null) Text("No hay turnos reservados para hoy.", Modifier.padding(24.dp)) else Column(Modifier.padding(24.dp)) {
            Text(if (role == AppRole.OWNER) "PRÓXIMO TURNO" else "PRÓXIMO PACIENTE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp)); Row(verticalAlignment = Alignment.CenterVertically) {
                PetAvatar(appointment); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) {
                    Text(appointment.petName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(if (role == AppRole.OWNER) appointment.veterinarian else appointment.ownerName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }; Text(appointment.time, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp)); Surface(color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f), shape = RoundedCornerShape(10.dp)) { Text(appointment.reason, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun AppointmentRow(appointment: Appointment, role: AppRole) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            PetAvatar(appointment); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) {
                Text(appointment.petName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(if (role == AppRole.OWNER) "${appointment.reason} · ${appointment.veterinarian}" else "${appointment.reason} · ${appointment.ownerName}", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }; Text(appointment.time, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateConsultationScreen(
    appointments: List<Appointment>,
    pets: List<PetDto>,
    veterinarians: List<Veterinarian>,
    api: MyVetApi,
    token: String?,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    var selectedPet by remember { mutableStateOf<PetDto?>(null) }
    var reason by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(availableDates.first()) }
    var selectedTime by remember { mutableStateOf<String?>(null) }
    var selectedVeterinarian by remember { mutableStateOf<Veterinarian?>(null) }
    var veterinarianMenuExpanded by remember { mutableStateOf(false) }
    var petMenuExpanded by remember { mutableStateOf(false) }
    var availableTimes by remember { mutableStateOf<List<String>>(emptyList()) }
    var submissionError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(selectedVeterinarian?.id, selectedDate, token) {
        selectedTime = null
        availableTimes = runCatching { if (token == null || selectedVeterinarian == null) emptyList() else api.availability(token, selectedVeterinarian!!.id, selectedDate).slots.filter { it.available }.map { it.time } }.getOrElse { emptyList() }
    }
    val canSave = selectedPet != null && reason.isNotBlank() && selectedVeterinarian != null && selectedTime != null && token != null

    Scaffold(topBar = { TopAppBar(title = { Text("Reservar consulta") }, navigationIcon = { TextButton(onClick = onBack) { Text("‹ Volver") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("Elegí un profesional y un horario disponible.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                Text("Veterinario agendado", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Box {
                    OutlinedButton(onClick = { veterinarianMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) { Text(selectedVeterinarian?.name ?: "Seleccioná un veterinario"); Text(selectedVeterinarian?.specialty ?: "", style = MaterialTheme.typography.labelSmall) }
                        Text("⌄")
                    }
                    DropdownMenu(expanded = veterinarianMenuExpanded, onDismissRequest = { veterinarianMenuExpanded = false }) {
                        veterinarians.forEach { veterinarian ->
                            DropdownMenuItem(
                                text = { Column { Text(veterinarian.name); Text(veterinarian.specialty, style = MaterialTheme.typography.labelSmall) } },
                                onClick = { selectedVeterinarian = veterinarian; selectedTime = null; veterinarianMenuExpanded = false },
                            )
                        }
                    }
                }
                Text("Solo se muestran profesionales que ya agregaste a tu lista.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            item {
                Text("Mascota", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Box {
                    OutlinedButton(onClick = { petMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selectedPet?.name ?: "Seleccioná una mascota", modifier = Modifier.weight(1f)); Text("⌄") }
                    DropdownMenu(expanded = petMenuExpanded, onDismissRequest = { petMenuExpanded = false }) { pets.forEach { pet -> DropdownMenuItem(text = { Text("${pet.name} · ${pet.species}") }, onClick = { selectedPet = pet; petMenuExpanded = false }) } }
                }
            }
            item { OutlinedTextField(reason, { reason = it }, Modifier.fillMaxWidth(), label = { Text("Motivo de la consulta") }, singleLine = true, isError = reason.isBlank()) }
            item { Text("Fecha", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { availableDates.forEach { date -> OutlinedButton(onClick = { selectedDate = date; selectedTime = null }, modifier = Modifier.weight(1f)) { Text(date.takeLast(2)) } } } }
            item { Text("Horario · intervalos de 15 min", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(appointmentTimes.chunked(3)) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { time ->
                        val available = time in availableTimes
                        Button(onClick = { selectedTime = time }, enabled = available, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text(if (available) time else "Ocupado") }
                    }
                }
            }
            item { Text(if (selectedTime == null) "Elegí un horario disponible." else "Horario seleccionado: $selectedDate a las $selectedTime", color = if (selectedTime == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
            submissionError?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            item { Button(onClick = { scope.launch { runCatching { api.createAppointment(token!!, com.application.myvet.data.network.CreateAppointmentRequest(selectedPet!!.id, selectedVeterinarian!!.id, selectedDate, selectedTime!!, reason)) }.onSuccess { onSave() }.onFailure { submissionError = "No fue posible reservar ese turno. Actualizá la disponibilidad e intentá otra vez." } } }, enabled = canSave, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("Solicitar consulta") } }
        }
    }
}

@Composable
private fun VeterinariansContent(veterinarians: List<Veterinarian>, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Mis veterinarios", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        veterinarians.forEach { veterinarian ->
            Card { Column(Modifier.padding(18.dp)) { Text(veterinarian.name, fontWeight = FontWeight.Bold); Text(veterinarian.specialty, color = MaterialTheme.colorScheme.primary); Text("Agregado a tu lista") } }
        }
        Text("Los códigos de invitación son privados, de un solo uso y no se muestran en tu agenda. Al reservar, elegís directamente un profesional de esta lista.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PatientsContent(modifier: Modifier) {
    val patients = listOf(
        Triple("Milo", "Sofía Gómez", "4 años"),
        Triple("Luna", "Martín Pérez", "2 años"),
        Triple("Simón", "Lucía Fernández", "6 años"),
    )
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Mis pacientes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item { Text("Accedé a sus próximas consultas e historial clínico.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(patients) { (name, owner, age) ->
            Card(shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).background(Color(0xFFDCE8F7), CircleShape), contentAlignment = Alignment.Center) { Text("🐾") }
                    Spacer(Modifier.width(12.dp))
                    Column { Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text("Propietario: $owner", color = MaterialTheme.colorScheme.onSurfaceVariant); Text(age, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}

@Composable
private fun InvitationContent(modifier: Modifier) {
    var invitationCode by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Invitaciones", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Generá un código de un solo uso para que un propietario te agregue a su lista de veterinarios.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (invitationCode == null) {
            Button(onClick = { invitationCode = "INV-8M2Q" }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("Generar invitación") }
        } else {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Código activo", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(invitationCode!!, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Compartilo personalmente. Cuando el propietario lo canjee, dejará de ser válido.")
                }
            }
            OutlinedButton(onClick = { invitationCode = null }, modifier = Modifier.fillMaxWidth()) { Text("Invalidar invitación") }
        }
    }
}

@Composable
private fun PetAvatar(appointment: Appointment) { Box(Modifier.size(48.dp).background(appointment.accent, CircleShape), contentAlignment = Alignment.Center) { Text("🐾") } }

@Composable
private fun PlaceholderContent(title: String, description: String, emoji: String, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(emoji, style = MaterialTheme.typography.displayMedium); Spacer(Modifier.height(16.dp)); Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
