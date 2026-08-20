package com.example.voiceagent.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.voiceagent.viewmodel.ChatMessage
import com.example.voiceagent.viewmodel.ConversationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: ConversationViewModel, onRequestProjection: () -> Unit) {
    val messages by vm.messages.collectAsState()
    val listening by vm.listening.collectAsState()
    val needAsk by vm.needAsk.collectAsState()
    val running by vm.running.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("语音助手", color = MaterialTheme.colorScheme.onPrimary) },
            actions = {
                if (running) {
                    TextButton(onClick = { vm.stopAgent() }) {
                        Text("停止", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                IconButton(onClick = onRequestProjection) {
                    Icon(
                        Icons.AutoMirrored.Filled.ScreenShare,
                        contentDescription = "开启录屏截图兜底",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg -> MessageBubble(msg) }
        }

        if (needAsk != null) {
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        needAsk ?: "",
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { vm.answerAsk(true) }) { Text("允许", color = GreenOk) }
                    TextButton(onClick = { vm.answerAsk(false) }) { Text("拒绝", color = Color.Red) }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入指令…", color = Color.Gray) },
                singleLine = true,
                enabled = !running
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                enabled = !running,
                onClick = {
                    if (input.isNotBlank()) {
                        vm.sendTextCommand(input)
                        input = ""
                    }
                }
            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送") }

            FloatingActionButton(
                onClick = { vm.startVoiceCommand() },
                containerColor = if (listening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(Icons.Default.Mic, contentDescription = "语音", tint = Color.White)
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val align = if (msg.role == "user") Alignment.End else Alignment.Start
    val color = when (msg.role) {
        "user" -> GreenOk
        "agent" -> MaterialTheme.colorScheme.surface
        else -> Color(0xFF333333)
    }
    val textColor = when (msg.role) {
        "user" -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = align) {
        Surface(color = color, shape = RoundedCornerShape(12.dp)) {
            Text(
                msg.text,
                color = textColor,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

private val GreenOk = Color(0xFF1BA784)
