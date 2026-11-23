package br.com.organizauni.ui

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.organizauni.BuildConfig
import br.com.organizauni.data.*
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class StudyUiState(
    val student: Student? = null,
    val subjects: List<Subject> = emptyList(),
    val tasks: List<StudyTask> = emptyList(),
    val busy: Boolean = false,
    val loading: Boolean = false,
    val demo: Boolean = false,
    val dataError: String? = null,
    val message: String? = null,
)

class StudyViewModel : ViewModel() {
    private val firebaseRepository = if (BuildConfig.FIREBASE_CONFIGURED) FirebaseStudyRepository() else null
    private var repository: StudyRepository? = firebaseRepository
    private val mutableState = MutableStateFlow(StudyUiState())
    val state = mutableState.asStateFlow()
    private var observation: Job? = null

    init {
        firebaseRepository?.currentStudent?.let { observe(it) }
    }

    fun clearMessage() = mutableState.update { it.copy(message = null) }

    fun message(value: String) = mutableState.update { it.copy(message = value) }

    fun login(email: String, password: String) {
        if (!validEmail(email) || password.isEmpty()) {
            message("Informe um e-mail válido e sua senha.")
            return
        }
        authenticate { it.login(email.trim(), password) }
    }

    fun register(name: String, email: String, password: String, confirmation: String) {
        when {
            name.trim().length < 2 -> message("Informe seu nome com pelo menos dois caracteres.")
            !validEmail(email) -> message("Informe um e-mail válido.")
            password.length < 6 -> message("A senha deve ter pelo menos seis caracteres.")
            password != confirmation -> message("As senhas precisam ser iguais.")
            else -> authenticate(createProfile = true) { it.register(name.trim(), email.trim(), password) }
        }
    }

    private fun validEmail(email: String) = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()

    private fun authenticate(createProfile: Boolean = false, action: suspend (StudyRepository) -> Student) {
        val backend = firebaseRepository
        if (backend == null) {
            message("Conecte o Firebase seguindo o README ou entre na demonstração para conhecer o app.")
            return
        }
        repository = backend
        runOperation {
            val student = action(backend)
            observe(student)
            // Gravação idempotente. Se falhar, o login continua válido e o perfil pode ser salvo novamente.
            if (createProfile) backend.saveProfile(student)
        }
    }

    fun enterDemo() {
        if (state.value.busy) return
        val demoRepository = DemoStudyRepository()
        repository = demoRepository
        mutableState.value = StudyUiState(demo = true)
        observe(demoRepository.currentStudent)
    }

    private fun observe(student: Student) {
        observation?.cancel()
        mutableState.update { it.copy(student = student, loading = true, dataError = null) }
        val backend = checkNotNull(repository)
        observation = viewModelScope.launch {
            try {
                backend.observe(student).collect { data ->
                    mutableState.update {
                        it.copy(student = data.student, subjects = data.subjects, tasks = data.tasks, loading = false, dataError = null)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.update { it.copy(loading = false, dataError = errorText(error)) }
            }
        }
    }

    fun retry() {
        state.value.student?.let { observe(it) }
    }

    fun saveSubject(id: String, name: String, teacher: String, onSuccess: () -> Unit) {
        val normalizedName = name.trim()
        when {
            normalizedName.isBlank() -> message("Informe o nome da disciplina.")
            state.value.subjects.any { it.name.equals(normalizedName, ignoreCase = true) } -> message("Essa disciplina já está cadastrada.")
            else -> withStudent { backend, student ->
                backend.saveSubject(student.id, Subject(id, normalizedName, teacher.trim()))
                onSuccess()
            }
        }
    }

    fun deleteSubject(subject: Subject) {
        if (state.value.tasks.any { it.subjectId == subject.id }) {
            message("Exclua ou mova as atividades dessa disciplina antes de removê-la.")
            return
        }
        withStudent { backend, student -> backend.deleteSubject(student.id, subject.id) }
    }

    fun saveTask(task: StudyTask, onSuccess: () -> Unit) {
        when {
            task.title.isBlank() -> message("Dê um título para a atividade.")
            task.dueDay !in 0..2932896L -> message("Escolha uma data entre 01/01/1970 e 31/12/9999.")
            state.value.subjects.none { it.id == task.subjectId } -> message("Escolha uma disciplina cadastrada.")
            else -> withStudent { backend, student ->
                backend.saveTask(student.id, task.copy(title = task.title.trim(), description = task.description.trim()))
                onSuccess()
            }
        }
    }

    fun toggleTask(task: StudyTask) = withStudent { backend, student ->
        backend.saveTask(student.id, task.copy(completed = !task.completed))
    }

    fun deleteTask(task: StudyTask, onSuccess: () -> Unit) = withStudent { backend, student ->
        backend.deleteTask(student.id, task.id)
        onSuccess()
    }

    fun saveProfile(name: String, course: String) {
        if (name.trim().length < 2) {
            message("Informe seu nome com pelo menos dois caracteres.")
            return
        }
        withStudent { backend, student ->
            backend.saveProfile(student.copy(name = name.trim(), course = course.trim()))
            message("Perfil atualizado.")
        }
    }

    fun logout() {
        if (state.value.busy) return
        observation?.cancel()
        repository?.logout()
        repository = firebaseRepository
        mutableState.value = StudyUiState()
    }

    private fun withStudent(action: suspend (StudyRepository, Student) -> Unit) {
        val student = state.value.student ?: return
        val backend = repository ?: return
        runOperation { action(backend, student) }
    }

    private fun runOperation(action: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                withTimeout(30_000) { action() }
            } catch (error: TimeoutCancellationException) {
                message("A confirmação demorou. Verifique sua conexão e confira os dados antes de tentar novamente.")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                message(errorText(error))
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    private fun errorText(error: Exception): String = when (error) {
        is FirebaseNetworkException -> "Verifique sua conexão com a internet e tente novamente."
        is FirebaseAuthException -> when (error.errorCode) {
            "ERROR_EMAIL_ALREADY_IN_USE" -> "Este e-mail já tem uma conta. Entre pela tela de login."
            "ERROR_WEAK_PASSWORD" -> "Escolha uma senha com pelo menos seis caracteres."
            "ERROR_TOO_MANY_REQUESTS" -> "Muitas tentativas. Aguarde um pouco antes de tentar novamente."
            "ERROR_OPERATION_NOT_ALLOWED" -> "Habilite o acesso por e-mail e senha no Firebase Authentication."
            else -> "Não foi possível acessar a conta. Verifique e-mail e senha e tente novamente."
        }
        is FirebaseFirestoreException -> when (error.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Acesso ao banco negado. Confira as regras do Firestore indicadas no README."
            FirebaseFirestoreException.Code.UNAVAILABLE -> "O banco está indisponível. Verifique sua conexão e tente novamente."
            else -> "Não foi possível atualizar os dados. Tente novamente."
        }
        else -> "Não foi possível concluir a operação. Tente novamente."
    }
}
