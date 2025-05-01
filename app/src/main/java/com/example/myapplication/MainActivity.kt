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



class MainActivity : ComponentActivity() {

    private val PICK_FILE_REQUEST = 1
    private val questionsState = mutableStateOf<List<Question>>(emptyList())
    private lateinit var sharedPreferences: android.content.SharedPreferences
    private var currentFileName: String = "default"
    private var currentScreen by mutableStateOf("main") // или "question"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (currentScreen) {
                        "main" -> MainScreen(
                            onFileSelected = { file ->
                                currentFileName = file.nameWithoutExtension
                                readQuestionsFromFile(file)
                                currentScreen = "question"
                            },
                            onUploadClick = {
                                openFilePickerAndReload()
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


    private fun openFilePickerAndReload() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        startActivityForResult(Intent.createChooser(intent, "Выберите файл"), PICK_FILE_REQUEST)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK) {
            val uri: Uri? = data?.data
            uri?.let {
                val fileName = getFileName(it)
                saveFileToInternalStorage(it, fileName)

                // ✅ возвращаемся на главный экран, чтобы список обновился
                currentScreen = "main"
            }
        }
    }

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
            readQuestionsFromFile(destinationFile)
            currentScreen = "main"


        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка при сохранении", Toast.LENGTH_SHORT).show()
        }
    }

    private fun readQuestionsFromFile(file: File) {
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

                // Формат 1: вопрос и ответ на одной строке (через ?)
                if ('?' in line && !line.endsWith("?") && line.count { it == '?' } == 1) {
                    val parts = line.split("?")
                    val questionText = parts[0].trim() + "?"
                    val answerText = parts[1].trim()
                    questions.add(Question(questionText, answerText))
                    i++
                }

                // Формат 2: две строки — вопрос и сразу за ним ответ
                else if (i + 1 < lines.size && lines[i].endsWith("?")) {
                    val questionText = lines[i].trim()
                    val answerText = lines[i + 1].trim()
                    questions.add(Question(questionText, answerText))
                    i += 2
                }

                // Неизвестный формат — пропускаем
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
}

@Composable
fun MainScreen(
    onFileSelected: (File) -> Unit,
    onUploadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val filesDir = context.filesDir

    // ✅ Теперь fileList может обновляться
    val fileList = getFileList(filesDir)


    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Выберите загруженный файл:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))

        val fileList = getFileList(filesDir)


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
    }
}


private fun getFileList(dir: File): List<File> {
    return dir.listFiles()?.filter { it.isDirectory }?.mapNotNull { it.listFiles()?.firstOrNull() } ?: emptyList()
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
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Изучено ${studiedQuestions.size} из ${questions.size}", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = isRandom,
                    onCheckedChange = {
                        isRandom = it
                        currentIndex = 0
                    }
                )
                Text("🔀 Случайный порядок")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = hideAnswers,
                    onCheckedChange = { hideAnswers = it }
                )
                Text("Скрывать ответы")
            }


            Spacer(modifier = Modifier.height(12.dp))
            Text("Вопрос ${currentIndex + 1} из ${remainingQuestions.size}", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text(current.text, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(12.dp))
            if (hideAnswers && !showAnswer) {
                Button(onClick = { showAnswer = true }) {
                    Text("Показать ответ 👁️")
                }
            } else {
                Text("Ответ: ${current.answer}", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(32.dp))

            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                Button(
                    onClick = {
                        if (currentIndex > 0) {
                            currentIndex--
                            showAnswer = false // 👈 тоже сбрасываем
                        }
                    },
                    enabled = currentIndex > 0
                ) {
                    Text("Назад")
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = {
                        if (currentIndex < remainingQuestions.size - 1) {
                            currentIndex++
                            showAnswer = false // 👈 сбрасываем, чтобы ответ снова был скрыт
                        }
                    },
                    enabled = currentIndex < remainingQuestions.size - 1
                ) {
                    Text("Вперёд")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val realIndex = questions.indexOf(current)
                    studiedQuestions = studiedQuestions + realIndex
                    prefs.edit().putStringSet("studied", studiedQuestions.map { it.toString() }.toSet()).apply()
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
        Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Все вопросы изучены 🎉")
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
            onFileSelected = {},
            onUploadClick = {},
            modifier = Modifier
        )
    }
}




data class Question(
    val text: String,
    val answer: String
)
