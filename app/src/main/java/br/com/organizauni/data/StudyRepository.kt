package br.com.organizauni.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.util.UUID

data class Student(val id: String, val name: String, val email: String, val course: String = "")

data class Subject(val id: String = "", val name: String = "", val teacher: String = "")

data class StudyTask(
    val id: String = "",
    val title: String = "",
    val subjectId: String = "",
    val description: String = "",
    val dueDay: Long = LocalDate.now().toEpochDay(),
    val type: String = "Trabalho",
    val completed: Boolean = false,
)

data class StudyData(
    val student: Student,
    val subjects: List<Subject> = emptyList(),
    val tasks: List<StudyTask> = emptyList(),
)

interface StudyRepository {
    val currentStudent: Student?
    suspend fun login(email: String, password: String): Student
    suspend fun register(name: String, email: String, password: String): Student
    fun observe(student: Student): Flow<StudyData>
    suspend fun saveSubject(studentId: String, subject: Subject)
    suspend fun deleteSubject(studentId: String, subjectId: String)
    suspend fun saveTask(studentId: String, task: StudyTask)
    suspend fun deleteTask(studentId: String, taskId: String)
    suspend fun saveProfile(student: Student)
    fun logout()
}

class FirebaseStudyRepository : StudyRepository {
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseFirestore.getInstance()

    override val currentStudent: Student?
        get() = auth.currentUser?.let {
            Student(it.uid, it.displayName.orEmpty().ifBlank { "Estudante" }, it.email.orEmpty())
        }

    override suspend fun login(email: String, password: String): Student {
        auth.signInWithEmailAndPassword(email, password).await()
        return checkNotNull(currentStudent)
    }

    override suspend fun register(name: String, email: String, password: String): Student {
        val user = checkNotNull(auth.createUserWithEmailAndPassword(email, password).await().user)
        // O nome também fica no Auth para recuperar o perfil se a gravação no Firestore falhar.
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build()).await()
        return Student(user.uid, name, user.email.orEmpty())
    }

    override fun observe(student: Student): Flow<StudyData> = callbackFlow {
        val userDocument = database.collection("users").document(student.id)
        var data = StudyData(student)
        var profileLoaded = false
        var subjectsLoaded = false
        var tasksLoaded = false
        fun publishWhenReady() {
            if (profileLoaded && subjectsLoaded && tasksLoaded) trySend(data)
        }
        val profileListener = userDocument.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            data = data.copy(student = student.copy(
                name = snapshot?.getString("name") ?: student.name,
                course = snapshot?.getString("course").orEmpty(),
            ))
            profileLoaded = true
            publishWhenReady()
        }
        val subjectsListener = userDocument.collection("subjects").addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            data = data.copy(subjects = snapshot?.documents.orEmpty().map {
                Subject(it.id, it.getString("name").orEmpty(), it.getString("teacher").orEmpty())
            }.sortedBy { it.name.lowercase() })
            subjectsLoaded = true
            publishWhenReady()
        }
        val tasksListener = userDocument.collection("tasks").addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            data = data.copy(tasks = snapshot?.documents.orEmpty().map {
                StudyTask(
                    id = it.id,
                    title = it.getString("title").orEmpty(),
                    subjectId = it.getString("subjectId").orEmpty(),
                    description = it.getString("description").orEmpty(),
                    dueDay = it.getLong("dueDay") ?: LocalDate.now().toEpochDay(),
                    type = it.getString("type") ?: "Trabalho",
                    completed = it.getBoolean("completed") ?: false,
                )
            }.sortedBy { it.dueDay })
            tasksLoaded = true
            publishWhenReady()
        }
        awaitClose {
            profileListener.remove()
            subjectsListener.remove()
            tasksListener.remove()
        }
    }

    override suspend fun saveSubject(studentId: String, subject: Subject) {
        database.collection("users").document(studentId).collection("subjects").document(subject.id)
            .set(mapOf("name" to subject.name, "teacher" to subject.teacher)).await()
    }

    override suspend fun deleteSubject(studentId: String, subjectId: String) {
        database.collection("users").document(studentId).collection("subjects").document(subjectId)
            .delete().await()
    }

    override suspend fun saveTask(studentId: String, task: StudyTask) {
        database.collection("users").document(studentId).collection("tasks").document(task.id)
            .set(mapOf(
                "title" to task.title,
                "subjectId" to task.subjectId,
                "description" to task.description,
                "dueDay" to task.dueDay,
                "type" to task.type,
                "completed" to task.completed,
            )).await()
    }

    override suspend fun deleteTask(studentId: String, taskId: String) {
        database.collection("users").document(studentId).collection("tasks").document(taskId)
            .delete().await()
    }

    override suspend fun saveProfile(student: Student) {
        database.collection("users").document(student.id).set(
            mapOf("name" to student.name, "email" to student.email, "course" to student.course),
            SetOptions.merge(),
        ).await()
    }

    override fun logout() = auth.signOut()
}

/** Sessão de demonstração isolada. Seus dados duram somente enquanto este processo estiver aberto. */
class DemoStudyRepository : StudyRepository {
    private val student = Student("demo", "Marina Costa", "marina@exemplo.com", "Análise e Desenvolvimento de Sistemas")
    private val today = LocalDate.now().toEpochDay()
    private val data = MutableStateFlow(StudyData(
        student,
        subjects = listOf(
            Subject("android", "Dispositivos Móveis", "Prof. Crispim"),
            Subject("database", "Banco de Dados", "Profa. Ana"),
            Subject("engineering", "Engenharia de Software", "Prof. Lucas"),
        ),
        tasks = listOf(
            StudyTask("1", "Desenvolver o OrganizaUni", "android", "Criar o aplicativo com sete telas e integração com Firebase.", today + 3),
            StudyTask("2", "Revisar consultas SQL", "database", "Revisar JOIN, GROUP BY e subconsultas para a avaliação.", today + 1, "Prova"),
            StudyTask("3", "Desenhar o diagrama de classes", "engineering", "Modelar as entidades do projeto da disciplina.", today + 5, "Exercício"),
            StudyTask("4", "Ler sobre arquitetura MVVM", "android", "Anotar as responsabilidades de Model, View e ViewModel.", today, "Leitura", true),
        ),
    ))
    override val currentStudent: Student get() = data.value.student
    override suspend fun login(email: String, password: String): Student = student
    override suspend fun register(name: String, email: String, password: String): Student = student
    override fun observe(student: Student): Flow<StudyData> = data
    override suspend fun saveSubject(studentId: String, subject: Subject) {
        data.value = data.value.copy(subjects = (data.value.subjects.filterNot { it.id == subject.id } + subject).sortedBy { it.name })
    }
    override suspend fun deleteSubject(studentId: String, subjectId: String) {
        data.value = data.value.copy(subjects = data.value.subjects.filterNot { it.id == subjectId })
    }
    override suspend fun saveTask(studentId: String, task: StudyTask) {
        data.value = data.value.copy(tasks = (data.value.tasks.filterNot { it.id == task.id } + task).sortedBy { it.dueDay })
    }
    override suspend fun deleteTask(studentId: String, taskId: String) {
        data.value = data.value.copy(tasks = data.value.tasks.filterNot { it.id == taskId })
    }
    override suspend fun saveProfile(student: Student) {
        data.value = data.value.copy(student = student)
    }
    override fun logout() = Unit
}

fun newId(): String = UUID.randomUUID().toString()
