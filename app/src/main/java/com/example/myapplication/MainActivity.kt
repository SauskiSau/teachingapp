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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                            fileList = fileListState, // ⬅️ Передаём список файлов
                            onFileSelected = { file ->
                                currentFileName = file.nameWithoutExtension
                                readQuestionsFromFile(file)
                                currentScreen = "question"
                            },
                            onUploadClick = {
                                openFilePickerAndReload()
                            },
                            onDeleteFile = {
                                refreshFileList()
                            },
                            modifier = Modifier.padding(innerPadding)
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
    onUploadClick: () -> Unit,
    onDeleteFile: () -> Unit,
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Выберите загруженный файл:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))

        fileList.forEach { file ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onFileSelected(file) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(file.name)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        file.parentFile?.deleteRecursively()
                        onDeleteFile() // 🟢 обновляем список в MainActivity
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("🗑", color = MaterialTheme.colorScheme.onError)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onUploadClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("📁 Загрузить новый файл")
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "⚠ Формат: вопрос — на одной строке, ответ — сразу на следующей строке. Без пустых строк между ними.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        )
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

        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Изучено ${studiedQuestions.size} из ${questions.size}", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isRandom, onCheckedChange = {
                    isRandom = it
                    currentIndex = 0
                })
                Text("🔀 Случайный порядок")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = hideAnswers, onCheckedChange = { hideAnswers = it })
                Text("Скрывать ответы")
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Вопрос ${currentIndex + 1} из ${remainingQuestions.size}", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))

            Box(   //бокс вопроса
                modifier = Modifier
                    .heightIn(min = 80.dp, max = 130.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = current.text,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Start
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ✅ Отображение ответа или кнопки
            if (hideAnswers && !showAnswer) {
                Button(onClick = { showAnswer = true }) {
                    Text("Показать ответ 👁️")
                }
            } else {
                Box(
                    modifier = Modifier
                        .heightIn(min = 80.dp, max = 150.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "Ответ: ${current.answer}",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Start
                    )
                }
            }

            // ✅ Все кнопки всегда отображаются
            Spacer(modifier = Modifier.height(32.dp))

            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                Button(
                    onClick = {
                        if (currentIndex > 0) {
                            currentIndex--
                            showAnswer = false
                        }
                    },
                    enabled = currentIndex > 0
                ) {
                    Text("⬅ Назад")
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = {
                        if (currentIndex < remainingQuestions.size - 1) {
                            currentIndex++
                            showAnswer = false
                        }
                    },
                    enabled = currentIndex < remainingQuestions.size - 1
                ) {
                    Text("Вперёд ➡")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val realIndex = questions.indexOf(current)
                    studiedQuestions = studiedQuestions + realIndex
                    prefs.edit()
                        .putStringSet("studied", studiedQuestions.map { it.toString() }.toSet())
                        .apply()
                    if (currentIndex >= remainingQuestions.size - 1) {
                        currentIndex = 0
                    }
                }
            ) {
                Text("Изучен ✅")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    prefs.edit().remove("studied").apply()
                    studiedQuestions = emptySet()
                    currentIndex = 0
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Text("🔄 Сбросить прогресс")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { onBack() }) {
                Text("⬅ Назад к выбору файла")
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
            Text("Все вопросы изучены 🎉", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { onBack() }) {
                Text("⬅ Назад к выбору файла")
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
            fileList = emptyList(), // Просто заглушка
            onFileSelected = {},
            onUploadClick = {},
            onDeleteFile = {},
            modifier = Modifier
        )
    }
}
