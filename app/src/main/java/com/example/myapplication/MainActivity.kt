package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.MyApplicationTheme
import java.io.*
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.TextField
import java.io.File
import androidx.compose.material.icons.filled.Edit
import com.google.accompanist.insets.navigationBarsWithImePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.filled.Language
import com.example.myapplication.setAppLocale
import com.example.myapplication.restartApp
import android.app.Activity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R


data class Question(
    val text: String,
    val answer: String
)

class MainActivity : ComponentActivity() {


    // Код состояния
    private val PICK_FILE_REQUEST = 1
    private val questionsState = mutableStateOf<List<Question>>(emptyList())
    private var fileListState by mutableStateOf<List<File>>(emptyList())
    private var currentFileName: String = "default"
    private var currentScreen by mutableStateOf("main") // или "question"


    private fun getFileList(dir: File): List<File> {
        return dir.listFiles()
            ?.filter { it.isDirectory }
            ?.flatMap { folder -> folder.listFiles()?.filter { it.isFile } ?: emptyList() }
            ?: emptyList()
    }

    // 📄 Чтение вопросов из файла
    fun readQuestionsFromFile(file: File) {
        val questions = mutableListOf<Question>()

        try {
            val lines = file.readLines()
            var i = 0
            while (i < lines.size) {
                val line = lines[i].trim()
                if (line.isEmpty()) {
                    i++
                    continue
                }

                // Формат 1: вопрос и ответ на одной строке через ?
                if ('?' in line && !line.endsWith("?") && line.count { it == '?' } == 1) {
                    val parts = line.split("?")
                    val questionText = parts[0].trim() + "?"
                    val answerText = parts[1].trim()
                    questions.add(Question(questionText, answerText))
                    i++
                }

                // Формат 2: вопрос и ответ на следующей строке (возможно с "Ответ:")
                else if (i + 1 < lines.size && lines[i].trim().endsWith("?")) {
                    val questionText = lines[i].trim()
                    val nextLine = lines[i + 1].trim()

                    val answerText = if (nextLine.lowercase().startsWith("ответ:")) {
                        nextLine.removePrefix("Ответ:").removePrefix("ответ:").trim()
                    } else {
                        nextLine
                    }

                    questions.add(Question(questionText, answerText))
                    i += 2
                }

                // Неизвестный формат
                else {
                    i++
                }
            }

            questionsState.value = questions
            Log.d("DEBUG", "Вопросов загружено: ${questions.size}")

        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка чтения файла", Toast.LENGTH_SHORT).show()
        }
    }
    // 📂 Обновление списка файлов
    private fun refreshFileList() {
        fileListState = getFileList(filesDir)
    }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val language = prefs.getString("app_language", "ru") ?: "ru"
        val contextWithLocale = setAppLocale(newBase, language)
        super.attachBaseContext(contextWithLocale)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val language = prefs.getString("app_language", "ru") ?: "ru"
        setAppLocale(this, language)

        questionsState.value = emptyList()
        currentScreen = "main"

        enableEdgeToEdge()

        refreshFileList() // ✅ Загружаем список файлов при старте



        setContent {
            MyApplicationTheme {
                // 🔙 Обработка кнопки "Назад"
                BackHandler(enabled = currentScreen != "main") {
                    questionsState.value = emptyList()
                    currentScreen = "main"
                }
                // 🧱 Основная оболочка интерфейса
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (currentScreen) {
                        "main" -> MainScreen(
                        fileList = fileListState,
                        onFileSelected = { file ->
                            currentFileName = file.nameWithoutExtension
                            readQuestionsFromFile(file)
                            currentScreen = "question"
                        },
                        onCreateManual = { currentScreen = "editor" },   // ← здесь
                        onUploadClick = { openFilePickerAndReload() },
                        onDeleteFile = { refreshFileList() },
                        modifier = Modifier.padding(innerPadding)
                    )


                        "editor" -> TextEditorScreen(
                            onRunTest = { file ->
                                currentFileName = file.nameWithoutExtension
                                readQuestionsFromFile(file)
                                refreshFileList()
                                currentScreen = "question"
                            },
                            onCancel = {
                                currentScreen = "main"
                            }
                        )

                        "question" -> QuestionViewer(
                            questions = questionsState.value,
                            fileKey = currentFileName,
                            modifier = Modifier.padding(innerPadding),
                            onBack = {
                                questionsState.value = emptyList()
                                currentScreen = "main"
                            }
                        )
                    }
                }
            }
        }

    }
    // 📥 Открытие выбора файла
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        startActivityForResult(Intent.createChooser(intent, "Выберите файл"), PICK_FILE_REQUEST)
    }

    private fun openFilePickerAndReload() {
        questionsState.value = emptyList()
        openFilePicker()
    }
    // 🔁 После выбора файла
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK) {
            val uri: Uri? = data?.data
            uri?.let {
                val fileName = getFileName(it)
                saveFileToInternalStorage(it, fileName)
            }
        }
    }
    // 🔎 Получаем имя файла
    private fun getFileName(uri: Uri): String {
        var name = "неизвестно.txt"
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
        return name
    }
    // 💾 Сохраняем выбранный файл во внутреннее хранилище
    private fun saveFileToInternalStorage(uri: Uri, fileName: String) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val folderName = fileName.substringBeforeLast('.')
            val folder = File(filesDir, folderName)
            if (!folder.exists()) folder.mkdirs()

            val destinationFile = File(folder, fileName)
            val outputStream = FileOutputStream(destinationFile)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()

            Toast.makeText(this, "Файл сохранён", Toast.LENGTH_SHORT).show()
            readQuestionsFromFile(destinationFile) // 📘 Загружаем вопросы из файла
            refreshFileList()

            currentScreen = "main"

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка при сохранении", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun MainScreen(
    fileList: List<File>,
    onFileSelected: (File) -> Unit,
    onCreateManual: () -> Unit,
    onUploadClick: () -> Unit,
    onDeleteFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var languageMenuExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(id = R.string.choose_file),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(16.dp))

            if (fileList.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.no_files),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(24.dp))
            }

            fileList.forEach { file ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = file.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        IconButton(onClick = { onFileSelected(file) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Open")
                        }
                        IconButton(
                            onClick = {
                                file.parentFile?.deleteRecursively()
                                onDeleteFile()
                            }
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                Button(
                    onClick = onCreateManual,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(text = stringResource(id = R.string.create_test))
                }
                Button(
                    onClick = onUploadClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(text = stringResource(id = R.string.import_txt))
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(onClick = { languageMenuExpanded = true }) {
                    Icon(Icons.Default.Language, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(text = stringResource(id = R.string.language))
                }

                DropdownMenu(
                    expanded = languageMenuExpanded,
                    onDismissRequest = { languageMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Қазақша") },
                        onClick = {
                            languageMenuExpanded = false
                            saveLanguagePreference(context, "kk")
                            setAppLocale(context, "kk")
                            Toast.makeText(context, "Қазақ тілі таңдалды", Toast.LENGTH_SHORT).show()
                            activity?.let { restartApp(it) }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("English") },
                        onClick = {
                            languageMenuExpanded = false
                            saveLanguagePreference(context, "en")
                            setAppLocale(context, "en")
                            Toast.makeText(context, "English selected", Toast.LENGTH_SHORT).show()
                            activity?.let { restartApp(it) }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Русский") },
                        onClick = {
                            languageMenuExpanded = false
                            saveLanguagePreference(context, "ru")
                            setAppLocale(context, "ru")
                            Toast.makeText(context, "Выбран русский язык", Toast.LENGTH_SHORT).show()
                            activity?.let { restartApp(it) }
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(id = R.string.format_tip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}



@Composable
fun TextEditorScreen(
    onRunTest: (File) -> Unit,
    onCancel: () -> Unit
) {
    var filename by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()) // 🔧 добавили прокрутку
            .navigationBarsWithImePadding() // учитываем клавиатуру
    ) {
        Text("✍️ Введите вопросы в формате:", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("Вопрос?\nОтвет", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))

        TextField(
            value = filename,
            onValueChange = { filename = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            placeholder = { Text("Название файла (без .txt)") },
            label = { Text("Название файла") },
            singleLine = true
        )


        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp, max = 400.dp), // ✅ ограничиваем высоту
            placeholder = { Text("Вопрос 1?\nОтвет 1\n\nВопрос 2?\nОтвет 2") }
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = { onCancel() }) {
                Text("Отмена")
            }

            Button(
                onClick = {
                    val safeName = if (filename.isBlank()) {
                        "user_file_${System.currentTimeMillis()}"
                    } else {
                        filename.trim()
                    }

                    val folder = File(context.filesDir, safeName)
                    if (!folder.exists()) folder.mkdirs()

                    val file = File(folder, "$safeName.txt")
                    file.writeText(text)

                    onRunTest(file)
                },
                enabled = text.isNotBlank()
            )

            {
                Text("Сохранить")
            }
        }
    }
}



@Composable
fun QuestionViewer(
    questions: List<Question>,
    fileKey: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("progress_$fileKey", ComponentActivity.MODE_PRIVATE)
    val savedSet = prefs.getStringSet("studied", emptySet()) ?: emptySet()
    var studiedQuestions by remember { mutableStateOf(savedSet.mapNotNull { it.toIntOrNull() }.toSet()) }

    var currentIndex by remember { mutableStateOf(0) }
    var isRandom by remember { mutableStateOf(false) }
    var hideAnswers by remember { mutableStateOf(true) }
    var showAnswer by remember { mutableStateOf(false) }

    val remainingQuestions = questions
        .filterIndexed { index, _ -> index !in studiedQuestions }
        .let { if (isRandom) it.shuffled() else it }

    if (remainingQuestions.isNotEmpty()) {
        val current = remainingQuestions[currentIndex.coerceIn(0, remainingQuestions.lastIndex)]

        Box(modifier = Modifier.fillMaxSize()) {
            // 📌 Верхняя прокручиваемая часть
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 180.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(20.dp)) // ✅ вот эта строка опустит всё чуть ниже

                // 🧾 Заголовок и настройки
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.question_number, currentIndex + 1, remainingQuestions.size))
                        Text(stringResource(R.string.studied_questions, studiedQuestions.size, questions.size))

                        Divider(modifier = Modifier.padding(vertical = 8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isRandom, onCheckedChange = {
                                isRandom = it
                                currentIndex = 0
                            })
                            Text(stringResource(R.string.shuffle_order))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = hideAnswers, onCheckedChange = { hideAnswers = it })
                            Text(stringResource(R.string.hide_answers))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp)) //равстояние между боксом вопроса и верхнего бокса

                // ❓ Вопрос
                Text("❓ " + stringResource(R.string.question))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .heightIn(min = 80.dp, max = 160.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text(text = current.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 🔐 Ответ или кнопка
                if (hideAnswers && !showAnswer) {
                    Button(
                        onClick = { showAnswer = true },
                        modifier = Modifier.padding(vertical = 16.dp)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.show_answer))  // кнопка
                    }
                } else {
                    Text("💬 " + stringResource(R.string.answer))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .heightIn(min = 80.dp, max = 160.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            Text(text = current.answer, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // 📌 Нижняя часть: кнопки
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { currentIndex--; showAnswer = false },
                        enabled = currentIndex > 0,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.back))
                    }

                    Spacer(Modifier.width(12.dp))

                    Button(
                        onClick = { currentIndex++; showAnswer = false },
                        enabled = currentIndex < remainingQuestions.size - 1,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.forward))
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = {
                        val realIndex = questions.indexOf(current)
                        studiedQuestions = studiedQuestions + realIndex
                        prefs.edit().putStringSet("studied", studiedQuestions.map { it.toString() }.toSet()).apply()
                        showAnswer = false
                        if (currentIndex < remainingQuestions.size - 1) {
                            currentIndex++
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Text(stringResource(R.string.mark_as_learned))
                    }

                    Button(
                        onClick = {
                            prefs.edit().remove("studied").apply()
                            studiedQuestions = emptySet()
                            currentIndex = 0
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Text(stringResource(R.string.reset))
                    }
                }

                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.back_to_files))
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.all_questions_learned))
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { onBack() }) {
                Text("⬅ " + stringResource(R.string.back_to_files))
            }
        }
    }
}





private fun getStoredSet(context: Context, fileKey: String): Set<Int> {
    val prefs = context.getSharedPreferences("progress_$fileKey", ComponentActivity.MODE_PRIVATE)
    return prefs.getStringSet("studied", emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
}




@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        MainScreen(
            fileList        = emptyList(),
            onFileSelected  = { },
            onCreateManual  = { /* ничего */ },
            onUploadClick   = { },
            onDeleteFile    = { },
            modifier        = Modifier
        )
    }
}
