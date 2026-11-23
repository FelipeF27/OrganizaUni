package br.com.organizauni.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import br.com.organizauni.BuildConfig
import br.com.organizauni.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val taskTypes = listOf("Trabalho", "Prova", "Exercício", "Leitura")

@Composable
fun OrganizaUniApp(model: StudyViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            model.clearMessage()
        }
    }
    key(state.student?.id) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (state.student != null && route in listOf("home", "subjects", "profile")) {
                    NavigationBar(containerColor = Color.White) {
                        listOf(
                            Triple("home", "Início", Icons.Outlined.Home),
                            Triple("subjects", "Disciplinas", Icons.AutoMirrored.Outlined.MenuBook),
                            Triple("profile", "Perfil", Icons.Outlined.PersonOutline),
                        ).forEach { (destination, label, icon) ->
                            NavigationBarItem(
                                selected = route == destination,
                                onClick = {
                                    nav.navigate(destination) {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(icon, contentDescription = null) },
                                label = { Text(label) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = Color.Transparent,
                                    selectedIconColor = Cobalt,
                                    selectedTextColor = Cobalt,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                if (state.demo) {
                    Surface(color = PaleBlue, contentColor = Cobalt) {
                        Text("Demonstração • dados temporários", style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 7.dp))
                    }
                }
                if (state.busy || state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.dataError?.let { error ->
                    Surface(color = MaterialTheme.colorScheme.errorContainer) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(error)
                            TextButton(onClick = model::retry) { Text("Tentar novamente") }
                        }
                    }
                }
                if (state.student == null) {
                    NavHost(nav, startDestination = "login", modifier = Modifier.weight(1f)) {
                        composable("login") {
                            AuthScreen(false, state.busy, model, { nav.navigate("register") }, {})
                        }
                        composable("register") {
                            AuthScreen(true, state.busy, model, {}, { nav.popBackStack() })
                        }
                    }
                } else {
                    NavHost(nav, startDestination = "home", modifier = Modifier.weight(1f)) {
                        composable("home") {
                            HomeScreen(state, { nav.navigate("task/new") }, { nav.navigate("details/$it") }, model::toggleTask)
                        }
                        composable("subjects") { SubjectsScreen(state, model) }
                        composable("profile") { ProfileScreen(state, model) }
                        composable("task/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { backStack ->
                            val id = backStack.arguments?.getString("id")
                            val task = state.tasks.find { it.id == id }
                            if (id == "new" || task != null) {
                                TaskFormScreen(task, state, model,
                                    { if (nav.currentBackStackEntry == backStack) nav.popBackStack() },
                                    { nav.navigate("subjects") })
                            } else if (!state.loading) {
                                MissingTask { nav.popBackStack() }
                            }
                        }
                        composable("details/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { backStack ->
                            val task = state.tasks.find { it.id == backStack.arguments?.getString("id") }
                            if (task != null) {
                                TaskDetailsScreen(task, state, model,
                                    { if (nav.currentBackStackEntry == backStack) nav.popBackStack() },
                                    { nav.navigate("task/${task.id}") })
                            } else if (!state.loading) {
                                MissingTask { nav.popBackStack() }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Brand(onDark: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = MaterialTheme.shapes.small, color = Cobalt) {
            Icon(Icons.Outlined.EventAvailable, null, tint = Color.White, modifier = Modifier.padding(9.dp).size(22.dp))
        }
        Text("OrganizaUni", style = MaterialTheme.typography.titleLarge, color = if (onDark) Color.White else Ink)
    }
}

@Composable
private fun AuthScreen(register: Boolean, busy: Boolean, model: StudyViewModel, openRegister: () -> Unit, back: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    // Senhas permanecem somente na memória da tela.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().background(Color.White).verticalScroll(rememberScrollState()),
    ) {
        Column(Modifier.fillMaxWidth().background(Midnight).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            if (register) {
                IconButton(onClick = back, enabled = !busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar ao login", tint = Color.White) }
            }
            Brand(onDark = true)
            if (!register) {
                Text("Seu semestre\nem ordem.", color = Color.White, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 38.sp, lineHeight = 42.sp))
                Text("A agenda dos seus trabalhos, provas e leituras.", color = Color(0xFFBBC9ED))
            }
        }
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (register) "Criar conta" else "Acesse sua agenda", style = MaterialTheme.typography.headlineMedium)
            Text(if (register) "Use seu e-mail para começar." else "Entre com seu e-mail e senha.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (register) AppField(name, { name = it }, "Nome completo", Icons.Outlined.PersonOutline, enabled = !busy)
            AppField(email, { email = it }, "E-mail", Icons.Outlined.MailOutline, keyboard = KeyboardType.Email, enabled = !busy)
            PasswordField(password, { password = it }, "Senha", !busy)
            if (register) PasswordField(confirmation, { confirmation = it }, "Confirmar senha", !busy)
            Button(
                shape = MaterialTheme.shapes.small,
                onClick = {
                    if (register) model.register(name, email, password, confirmation) else model.login(email, password)
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            ) { Text(if (busy) "Aguarde…" else if (register) "Criar minha conta" else "Entrar") }
            TextButton(onClick = if (register) back else openRegister, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (register) "Já tenho uma conta" else "Ainda não tenho conta • Cadastrar")
            }
            if (!register) {
                OutlinedButton(shape = MaterialTheme.shapes.small, onClick = model::enterDemo, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Conhecer o app em demonstração")
                }
                if (!BuildConfig.FIREBASE_CONFIGURED) {
                    Text("Para usar sua conta, conecte o Firebase seguindo o README do projeto.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AppField(value: String, onChange: (String) -> Unit, label: String, icon: ImageVector? = null,
    keyboard: KeyboardType = KeyboardType.Text, enabled: Boolean = true, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, enabled = enabled,
        leadingIcon = icon?.let { { Icon(it, contentDescription = null) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard), singleLine = singleLine,
        minLines = if (singleLine) 1 else 4,
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String, enabled: Boolean) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true, enabled = enabled,
        leadingIcon = { Icon(Icons.Outlined.Lock, null) },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "Ocultar senha" else "Mostrar senha")
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun HomeScreen(state: StudyUiState, newTask: () -> Unit, details: (String) -> Unit, toggle: (StudyTask) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("Pendentes") }
    var search by rememberSaveable { mutableStateOf("") }
    val today = LocalDate.now().toEpochDay()
    val completed = state.tasks.count { it.completed }
    val pending = state.tasks.size - completed
    val upcoming = state.tasks.count { !it.completed && it.dueDay in today..today + 6 }
    val visibleTasks = state.tasks.filter {
        (filter == "Todas" || it.completed == (filter == "Concluídas")) &&
            (it.title.contains(search, ignoreCase = true) || state.subjects.find { subject -> subject.id == it.subjectId }?.name?.contains(search, ignoreCase = true) == true)
    }.sortedBy { it.dueDay }
    LazyColumn(contentPadding = PaddingValues(24.dp)) {
        item {
            Brand()
            Spacer(Modifier.height(28.dp))
            Text("Olá, ${state.student?.name?.substringBefore(' ')}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sua agenda", style = MaterialTheme.typography.headlineLarge)
                Button(shape = MaterialTheme.shapes.small, onClick = newTask,
                    modifier = Modifier.semantics { contentDescription = "Nova atividade" }) {
                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Nova atividade")
                }
            }
            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM", Locale.forLanguageTag("pt-BR"))),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                AgendaCount(pending, "Pendentes", Ink)
                AgendaCount(upcoming, "Próximos 7 dias", Amber)
                AgendaCount(completed, "Concluídas", Cobalt)
            }
            HorizontalDivider()
            Spacer(Modifier.height(22.dp))
            AppField(search, { search = it }, "Buscar atividade ou disciplina", Icons.Outlined.Search)
            FlowRow(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Pendentes", "Concluídas", "Todas").forEach { option ->
                    FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option) }, shape = MaterialTheme.shapes.small)
                }
            }
            Text("Suas atividades", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 18.dp))
        }
        if (visibleTasks.isEmpty() && !state.loading) item {
            EmptyState(Icons.Outlined.EventAvailable, "Agenda livre por aqui",
                if (state.tasks.isEmpty()) "Toque em Nova atividade para começar." else "Nenhuma atividade corresponde à sua busca.")
        }
        visibleTasks.groupBy { it.dueDay }.forEach { (day, tasks) ->
            item(key = "date-$day") {
                val date = LocalDate.ofEpochDay(day)
                Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(date.format(DateTimeFormatter.ofPattern("dd")), style = MaterialTheme.typography.headlineMedium,
                        color = if (day < today && tasks.any { !it.completed }) MaterialTheme.colorScheme.error else Cobalt)
                    Column {
                        Text(when (day) {
                            today -> "Hoje"
                            today + 1 -> "Amanhã"
                            else -> date.format(DateTimeFormatter.ofPattern("MMMM", Locale.forLanguageTag("pt-BR")))
                        }, style = MaterialTheme.typography.titleMedium)
                        Text(date.format(DateTimeFormatter.ofPattern("EEEE", Locale.forLanguageTag("pt-BR"))),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(tasks, key = { it.id }) { task ->
                TaskCard(task, state.subjects.find { it.id == task.subjectId }?.name ?: "Disciplina removida", !state.busy, { details(task.id) }, { toggle(task) })
            }
        }
    }
}

@Composable
private fun AgendaCount(count: Int, label: String, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(count.toString(), style = MaterialTheme.typography.headlineMedium, color = color)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TaskCard(task: StudyTask, subjectName: String, enabled: Boolean, details: () -> Unit, toggle: () -> Unit) {
    Surface(onClick = details, color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().padding(end = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = task.completed, onCheckedChange = { toggle() }, enabled = enabled,
                    modifier = Modifier.semantics {
                        contentDescription = if (task.completed) "Marcar ${task.title} como pendente" else "Concluir ${task.title}"
                    })
                Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(subjectName, style = MaterialTheme.typography.labelMedium, color = Cobalt)
                    Text(task.title, style = MaterialTheme.typography.titleMedium, textDecoration = if (task.completed) TextDecoration.LineThrough else null)
                    Text(task.type, style = MaterialTheme.typography.bodySmall,
                        color = if (task.type == "Prova") Amber else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ChevronRight, "Ver detalhes", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String? = null, back: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (back != null) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar") }
        Text(title, style = MaterialTheme.typography.headlineLarge)
        if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SubjectsScreen(state: StudyUiState, model: StudyViewModel) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Subject?>(null) }
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageTitle("Disciplinas", "Matérias e professores do seu semestre.") }
        item {
            Button(shape = MaterialTheme.shapes.small, onClick = { showDialog = true }, enabled = !state.busy && !state.loading) {
                Icon(Icons.Outlined.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Adicionar disciplina")
            }
        }
        if (state.subjects.isEmpty() && !state.loading) item {
            EmptyState(Icons.AutoMirrored.Outlined.MenuBook, "Seu semestre começa aqui.", "Adicione uma disciplina para criar atividades.")
        }
        items(state.subjects, key = { it.id }) { subject ->
            OutlinedCard(colors = CardDefaults.outlinedCardColors(containerColor = Color.White)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(subject.name, style = MaterialTheme.typography.titleMedium)
                        if (subject.teacher.isNotBlank()) Text(subject.teacher, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${state.tasks.count { it.subjectId == subject.id && !it.completed }} atividades pendentes", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { deleting = subject }, enabled = !state.busy) { Icon(Icons.Outlined.DeleteOutline, "Excluir ${subject.name}") }
                }
            }
        }
    }
    if (showDialog) {
        val subjectId = rememberSaveable { newId() }
        var name by rememberSaveable { mutableStateOf("") }
        var teacher by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!state.busy) showDialog = false }, title = { Text("Nova disciplina") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppField(name, { name = it }, "Nome da disciplina", enabled = !state.busy)
                    AppField(teacher, { teacher = it }, "Professor(a), opcional", enabled = !state.busy)
                }
            },
            confirmButton = { TextButton(onClick = { model.saveSubject(subjectId, name, teacher) { showDialog = false } }, enabled = !state.busy) { Text("Adicionar") } },
            dismissButton = { TextButton(onClick = { showDialog = false }, enabled = !state.busy) { Text("Cancelar") } },
        )
    }
    deleting?.let { subject ->
        ConfirmDelete("Excluir ${subject.name}?", "A disciplina precisa estar sem atividades para ser removida.", !state.busy,
            { model.deleteSubject(subject); deleting = null }, { deleting = null })
    }
}

@Composable
private fun TaskFormScreen(existing: StudyTask?, state: StudyUiState, model: StudyViewModel, back: () -> Unit, openSubjects: () -> Unit) {
    val taskId = rememberSaveable(existing?.id) { existing?.id ?: newId() }
    var title by rememberSaveable(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var description by rememberSaveable(existing?.id) { mutableStateOf(existing?.description.orEmpty()) }
    var subjectId by rememberSaveable(existing?.id) { mutableStateOf(existing?.subjectId.orEmpty()) }
    var dueDay by rememberSaveable(existing?.id) { mutableLongStateOf(existing?.dueDay ?: LocalDate.now().toEpochDay()) }
    var type by rememberSaveable(existing?.id) { mutableStateOf(existing?.type ?: "Trabalho") }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        PageTitle(if (existing == null) "Nova atividade" else "Editar atividade", back = back)
        AppField(title, { title = it }, "Título da atividade", enabled = !state.busy)
        ChoiceField("Disciplina", state.subjects.find { it.id == subjectId }?.name ?: "Selecione uma disciplina", state.subjects.map { it.id to it.name }, !state.busy) { subjectId = it }
        if (state.subjects.isEmpty()) {
            TextButton(onClick = openSubjects) { Text("Cadastrar minha primeira disciplina") }
        }
        ChoiceField("Tipo de atividade", type, taskTypes.map { it to it }, !state.busy) { type = it }
        Text("Data de entrega", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(shape = MaterialTheme.shapes.small,
            onClick = {
                val selected = LocalDate.ofEpochDay(dueDay)
                DatePickerDialog(context, { _, year, month, day -> dueDay = LocalDate.of(year, month + 1, day).toEpochDay() },
                    selected.year, selected.monthValue - 1, selected.dayOfMonth).show()
            }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
        ) {
            Icon(Icons.Outlined.CalendarMonth, null)
            Spacer(Modifier.width(10.dp))
            Text(LocalDate.ofEpochDay(dueDay).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
        }
        AppField(description, { description = it }, "Descrição, opcional", enabled = !state.busy, singleLine = false)
        Button(shape = MaterialTheme.shapes.small, onClick = {
            model.saveTask(StudyTask(taskId, title, subjectId, description, dueDay, type, existing?.completed ?: false), back)
        }, enabled = !state.busy && !state.loading && state.subjects.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
            Text(if (state.busy) "Salvando…" else "Salvar atividade")
        }
    }
}

@Composable
private fun ChoiceField(label: String, selected: String, choices: List<Pair<String, String>>, enabled: Boolean, choose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Box {
            OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { expanded = true }, enabled = enabled && choices.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                Text(selected, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ExpandMore, null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { (id, name) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { choose(id); expanded = false })
                }
            }
        }
    }
}

@Composable
private fun TaskDetailsScreen(task: StudyTask, state: StudyUiState, model: StudyViewModel, back: () -> Unit, edit: () -> Unit) {
    var deleting by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        PageTitle("Detalhes da atividade", back = back)
        Surface(color = Color.White, shape = MaterialTheme.shapes.small) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(task.type.uppercase(Locale.forLanguageTag("pt-BR")), style = MaterialTheme.typography.labelLarge)
                Text(task.title, style = MaterialTheme.typography.headlineMedium)
                Text(state.subjects.find { it.id == task.subjectId }?.name ?: "Disciplina removida")
            }
        }
        DetailRow(Icons.Outlined.CalendarMonth, "Entrega", LocalDate.ofEpochDay(task.dueDay).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
        DetailRow(Icons.Outlined.CheckCircleOutline, "Situação", if (task.completed) "Concluída" else if (task.dueDay < LocalDate.now().toEpochDay()) "Pendente • prazo encerrado" else "Pendente")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Descrição", style = MaterialTheme.typography.titleMedium)
            Text(task.description.ifBlank { "Nenhuma descrição adicionada." })
        }
        Button(shape = MaterialTheme.shapes.small, onClick = { model.toggleTask(task) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
            Text(if (task.completed) "Marcar como pendente" else "Concluir atividade")
        }
        OutlinedButton(shape = MaterialTheme.shapes.small, onClick = edit, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.Outlined.Edit, null)
            Spacer(Modifier.width(8.dp))
            Text("Editar atividade")
        }
        TextButton(onClick = { deleting = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text("Excluir atividade", color = MaterialTheme.colorScheme.error)
        }
    }
    if (deleting) ConfirmDelete("Excluir atividade?", "A atividade será removida da sua agenda.", !state.busy,
        { model.deleteTask(task, back) }, { deleting = false })
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Cobalt)
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ProfileScreen(state: StudyUiState, model: StudyViewModel) {
    val student = state.student ?: return
    var name by rememberSaveable(student.id, student.name) { mutableStateOf(student.name) }
    var course by rememberSaveable(student.id, student.course) { mutableStateOf(student.course) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageTitle("Meu perfil")
        Surface(shape = MaterialTheme.shapes.small, color = PaleBlue, modifier = Modifier.size(64.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(student.name.take(1).uppercase(), style = MaterialTheme.typography.headlineLarge)
            }
        }
        DetailRow(Icons.Outlined.MailOutline, "E-mail da conta", student.email)
        AppField(name, { name = it }, "Nome completo", Icons.Outlined.PersonOutline, enabled = !state.busy)
        AppField(course, { course = it }, "Curso, opcional", Icons.Outlined.School, enabled = !state.busy)
        Button(shape = MaterialTheme.shapes.small, onClick = { model.saveProfile(name, course) }, enabled = !state.busy && !state.loading, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
            Text("Salvar perfil")
        }
        HorizontalDivider()
        DetailRow(Icons.AutoMirrored.Outlined.MenuBook, "Disciplinas cadastradas", state.subjects.size.toString())
        DetailRow(Icons.Outlined.CheckCircleOutline, "Atividades concluídas", state.tasks.count { it.completed }.toString())
        OutlinedButton(shape = MaterialTheme.shapes.small, onClick = model::logout, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
            Icon(Icons.AutoMirrored.Outlined.Logout, null)
            Spacer(Modifier.width(8.dp))
            Text(if (state.demo) "Sair da demonstração" else "Sair da conta")
        }
    }
}

@Composable
private fun ConfirmDelete(title: String, text: String, enabled: Boolean, confirm: () -> Unit, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = { if (enabled) dismiss() }, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = confirm, enabled = enabled) { Text("Excluir", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = dismiss, enabled = enabled) { Text("Cancelar") } })
}

@Composable
private fun MissingTask(back: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        PageTitle("Atividade indisponível", "Ela pode ter sido excluída em outro dispositivo.", back)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun HomePreview() {
    val repository = remember { DemoStudyRepository() }
    val data by repository.observe(repository.currentStudent).collectAsState(initial = StudyData(repository.currentStudent))
    OrganizaUniTheme {
        Surface {
            HomeScreen(StudyUiState(data.student, data.subjects, data.tasks), {}, {}, {})
        }
    }
}
