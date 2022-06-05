package net.ltm.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.dp
import net.ltm.chatHistoryQQ
import net.ltm.contactListQQ
import net.ltm.contactsMap
import net.ltm.groupListQQ
import io.appoutlet.karavel.Page
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.ltm.nav
import net.ltm.contact.Contacts
import net.ltm.datas.MessagesQQ
import net.ltm.datas.RenderMessages
import net.ltm.datas.calculateRelationIDQQ
import net.ltm.message.sendMessage
import net.ltm.message.simpleMessageListenerForChatUI
import net.mamoe.mirai.contact.nameCardOrNick
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import net.ltm.userQQBot

//左侧边栏
@Composable
fun LeftSidebar() {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(60.dp)
            .background(Color(247, 242, 243))
            .padding(10.dp)
    ) {
        IconButton(
            onClick = {
                nav.navigate(MainPage())
            }
        ) {
            Image(
                imageVector = Icons.Default.Email,
                contentDescription = "Chats",
                modifier = Modifier.size(32.dp)
            )
        }
        /*Image(
            contentDescription = "Contact Lists",
            painter = painterResource("")
        )*/
        Column(
            modifier = Modifier.weight(10F),
            verticalArrangement = Arrangement.Bottom
        ) {
            IconButton(
                onClick = {
                    nav.navigate(SettingsPage())
                }
            ) {
                Image(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Settings",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

//联系人列表
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionList() {
    val selected = remember { mutableStateOf(Pair("None", "")) }
    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxHeight().width(250.dp).background(color = Color(180, 180, 180)).padding(10.dp)
        ) {
            val state = rememberLazyListState()
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                state = state
            ) {
                stickyHeader {
                    Box {
                        Text("联系人列表", modifier = Modifier.padding(4.dp).clip(shape = RoundedCornerShape(4.dp)))
                    }
                }
                items(groupListQQ) {
                    Box(modifier = Modifier.align(Alignment.Center).fillMaxSize().clip(RoundedCornerShape(5.dp))
                        .clickable {
                            selected.value = Pair("QQ_Group", it.id.toString())
                        }) {
                        Row {
                            AsyncImage(
                                load = { loadImageBitmap(it.avatarUrl) },
                                painterFor = { remember { BitmapPainter(it) } },
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(5.dp)),
                                contentDescription = "Group Avatar"
                            )
                            Box(modifier = Modifier.padding(4.dp).fillMaxSize()) {
                                Text(text = it.name)
                            }
                        }
                    }
                }
                items(contactListQQ) {
                    Box(modifier = Modifier.align(Alignment.Center).fillMaxSize().clip(RoundedCornerShape(5.dp))
                        .clickable {
                            selected.value = Pair("QQ_Friend", it.id.toString())
                        }) {
                        Row {
                            AsyncImage(
                                load = { loadImageBitmap(it.avatarUrl) },
                                painterFor = { remember { BitmapPainter(it) } },
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(5.dp)),
                                contentDescription = "Friend Avatar"
                            )
                            Box(modifier = Modifier.padding(4.dp).fillMaxSize()) {
                                Text(text = it.nameCardOrNick)
                            }
                        }
                    }
                }
            }
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(), adapter = rememberScrollbarAdapter(
                    scrollState = state
                )
            )
        }
        if (selected.value.first != "None") {
            ChatUI(selected.value.first, selected.value.second)
        }
    }
}

//聊天界面
@Composable
fun ChatUI(type: String, id: String) {
    val contact = contactsMap[type]!![id]!!
    val history = mutableStateListOf<RenderMessages>()
    val content = mutableStateOf("")
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ContactBar(contact)
            ChatBar(contact, type, history)
            TypeBar(contact, content,history)
        }
    }
}

@Composable
fun ContactBar(contact: Contacts) {
    Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.10f)) {
        Row {
            Box {
                Row {
                    AsyncImage(
                        load = { loadImageBitmap(contact.avatar) },
                        painterFor = { remember { BitmapPainter(it) } },
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(5.dp)),
                        contentDescription = "Contacts Avatar"
                    )
                    Text(contact.name, modifier = Modifier.padding(10.dp))
                }

            }
            Text(contact.type, modifier = Modifier.padding(10.dp))
            Text(contact.id, modifier = Modifier.padding(10.dp))
        }
    }
}


@Composable
fun ChatBar(contact: Contacts, type: String, history: SnapshotStateList<RenderMessages>) {
    val id = when (type) {
        "QQ_Friend" -> "QID"
        "QQ_Group" -> "GID"
        else -> ""
    }
    val relation = calculateRelationIDQQ(userQQBot.userBot.id, contact.id + id)
    CoroutineScope(Dispatchers.IO).launch {
        transaction(chatHistoryQQ) {
            MessagesQQ
                .select { (MessagesQQ.relationID eq relation) and (MessagesQQ.contactID eq contact.id + id) }
                .orderBy(MessagesQQ.timeStamp to SortOrder.DESC)
                .limit(50, 0).forEach {
                    history.add(
                        RenderMessages(
                            it[MessagesQQ.msgID],
                            it[MessagesQQ.relationID],
                            it[MessagesQQ.contactID],
                            it[MessagesQQ.timeStamp],
                            it[MessagesQQ.messageContent]
                        )
                    )
                }
        }
        history.sortBy { it.timeStamp }
        simpleMessageListenerForChatUI(contact, history)
    }
    Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.70f)) {
        val state = rememberLazyListState()
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxSize(), state = state
        ) {
            items(history) {
                buildMessageCard(it)
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(), adapter = rememberScrollbarAdapter(
                scrollState = state
            )
        )
    }
}

@Composable
fun TypeBar(contact: Contacts, content: MutableState<String>, history: SnapshotStateList<RenderMessages>) {
    Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.20f)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                OutlinedTextField(
                    value = content.value,
                    onValueChange = { content.value = it },
                    placeholder = { Text("聊天内容") },
                    modifier = Modifier.fillMaxHeight()
                )
                IconButton(
                    onClick = {
                        CoroutineScope(Dispatchers.IO).launch {
                            sendMessage(contact, content.value,history)
                            history.sortBy { it.timeStamp }
                            content.value = ""
                        }
                    }
                ) {
                    Image(
                        imageVector = Icons.Default.Send,
                        contentDescription = "发送消息",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

class MainPage : Page() {
    @Composable
    override fun content() {
        Card(
            modifier = Modifier.fillMaxSize(), backgroundColor = Color(255, 255, 255), elevation = 0.dp
        ) {
            Scaffold {
                Row(modifier = Modifier.fillMaxSize()) {
                    LeftSidebar()
                    SessionList()
                }
            }
        }
    }
}

@Composable
fun App() {
    MaterialTheme {
        nav.currentPage().content()
    }
}
