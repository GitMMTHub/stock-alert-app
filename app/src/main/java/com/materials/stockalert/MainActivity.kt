package com.materials.stockalert

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.materials.stockalert.data.*
import com.materials.stockalert.schedule.OrderScheduler
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val requestSmsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "صلاحية SMS مطلوبة للإرسال النصي التلقائي", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestSmsPermission.launch(Manifest.permission.SEND_SMS)
        }

        val db = AppDatabase.getInstance(this)

        setContent {
            MaterialTheme {
                StockAlertApp(db)
            }
        }
    }
}

@Composable
fun StockAlertApp(db: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }

    val merchants by db.merchantDao().observeMerchants().collectAsState(initial = emptyList())
    val allItems by db.itemDao().observeAllItems().collectAsState(initial = emptyList())
    val orders by db.orderDao().observeOrders().collectAsState(initial = emptyList())

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("النواقص والجدولة") },
                    icon = {}
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("الأصناف") },
                    icon = {}
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    label = { Text("التجار") },
                    icon = {}
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize().padding(12.dp)) {
            when (selectedTab) {
                0 -> ShortagesAndScheduleScreen(
                    merchants = merchants,
                    allItems = allItems,
                    orders = orders,
                    onScheduleOrder = { merchant, message, triggerMillis, channel ->
                        scope.launch {
                            val orderId = db.orderDao().insert(
                                ScheduledOrder(
                                    merchantId = merchant.id,
                                    messageBody = message,
                                    triggerAtMillis = triggerMillis,
                                    channel = channel
                                )
                            )
                            OrderScheduler.scheduleExact(context, orderId, triggerMillis)
                            Toast.makeText(context, "تمت جدولة الإرسال بنجاح", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                1 -> ItemsScreen(
                    merchants = merchants,
                    items = allItems,
                    onAddItem = { name, unit, merchantId ->
                        scope.launch {
                            db.itemDao().insert(Item(name = name, unit = unit, merchantId = merchantId))
                        }
                    }
                )
                2 -> MerchantsScreen(
                    merchants = merchants,
                    onAddMerchant = { name, phone, channel ->
                        scope.launch {
                            db.merchantDao().insert(
                                Merchant(name = name, phone = phone, preferredChannel = channel)
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun MerchantsScreen(
    merchants: List<Merchant>,
    onAddMerchant: (String, String, SendChannel) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var channel by remember { mutableStateOf(SendChannel.WHATSAPP) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("إضافة تاجر جديد", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("اسم التاجر") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("رقم الهاتف (مثال: 771234567)") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = channel == SendChannel.WHATSAPP,
                onClick = { channel = SendChannel.WHATSAPP },
                label = { Text("واتساب") }
            )
            FilterChip(
                selected = channel == SendChannel.SMS,
                onClick = { channel = SendChannel.SMS },
                label = { Text("SMS") }
            )
        }

        Button(
            onClick = {
                if (name.isNotBlank() && phone.isNotBlank()) {
                    onAddMerchant(name.trim(), phone.trim(), channel)
                    name = ""
                    phone = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("حفظ التاجر")
        }

        HorizontalDivider()
        LazyColumn {
            items(merchants) { m ->
                ListItem(
                    headlineContent = { Text(m.name) },
                    supportingContent = { Text("${m.phone} - ${m.preferredChannel.name}") }
                )
            }
        }
    }
}

@Composable
fun ItemsScreen(
    merchants: List<Merchant>,
    items: List<Item>,
    onAddItem: (String, String, Long) -> Unit
) {
    var itemName by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("حبة") }
    var selectedMerchantId by remember(merchants) { mutableStateOf(merchants.firstOrNull()?.id ?: 0L) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("إضافة صنف وربطه بتاجر", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = itemName,
            onValueChange = { itemName = it },
            label = { Text("اسم الصنف") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = unit,
            onValueChange = { unit = it },
            label = { Text("الوحدة (كيس / لفة / كرتون / حبة)") },
            modifier = Modifier.fillMaxWidth()
        )

        Text("اختر التاجر:")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            merchants.take(3).forEach { m ->
                FilterChip(
                    selected = selectedMerchantId == m.id,
                    onClick = { selectedMerchantId = m.id },
                    label = { Text(m.name) }
                )
            }
        }

        Button(
            onClick = {
                if (itemName.isNotBlank() && selectedMerchantId != 0L) {
                    onAddItem(itemName.trim(), unit.trim().ifBlank { "حبة" }, selectedMerchantId)
                    itemName = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("إضافة الصنف")
        }

        HorizontalDivider()
        LazyColumn {
            items(items) { item ->
                val merchantName = merchants.find { it.id == item.merchantId }?.name ?: "تاجر"
                ListItem(
                    headlineContent = { Text("${item.name} (${item.unit})") },
                    supportingContent = { Text("التاجر: $merchantName") }
                )
            }
        }
    }
}

@Composable
fun ShortagesAndScheduleScreen(
    merchants: List<Merchant>,
    allItems: List<Item>,
    orders: List<ScheduledOrder>,
    onScheduleOrder: (Merchant, String, Long, SendChannel) -> Unit
) {
    val context = LocalContext.current
    var selectedMerchant by remember(merchants) { mutableStateOf(merchants.firstOrNull()) }
    val quantities = remember { mutableStateMapOf<Long, String>() }
    val calendar = remember { Calendar.getInstance().apply { add(Calendar.MINUTE, 5) } }
    var dateText by remember { mutableStateOf(formatDate(calendar.timeInMillis)) }
    var timeText by remember { mutableStateOf(formatTime(calendar.timeInMillis)) }
    val formatter = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }

    val merchantItems = selectedMerchant?.let { m ->
        allItems.filter { it.merchantId == m.id }
    } ?: emptyList()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("تحديد النواقص وجدولة الإرسال", style = MaterialTheme.typography.titleMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            merchants.forEach { m ->
                FilterChip(
                    selected = selectedMerchant?.id == m.id,
                    onClick = { selectedMerchant = m },
                    label = { Text(m.name) }
                )
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(merchantItems) { item ->
                val qty = quantities[item.id] ?: ""
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${item.name} (${item.unit})", modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = qty,
                        onValueChange = { quantities[item.id] = it },
                        label = { Text("الكمية") },
                        modifier = Modifier.width(110.dp),
                        singleLine = true
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    DatePickerDialog(
                        context,
                        { _, y, m, d ->
                            calendar.set(Calendar.YEAR, y)
                            calendar.set(Calendar.MONTH, m)
                            calendar.set(Calendar.DAY_OF_MONTH, d)
                            dateText = formatDate(calendar.timeInMillis)
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                    ).show()
                },
                modifier = Modifier.weight(1f)
            ) { Text("التاريخ: $dateText") }

            Button(
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            calendar.set(Calendar.HOUR_OF_DAY, hour)
                            calendar.set(Calendar.MINUTE, minute)
                            calendar.set(Calendar.SECOND, 0)
                            timeText = formatTime(calendar.timeInMillis)
                        },
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        true
                    ).show()
                },
                modifier = Modifier.weight(1f)
            ) { Text("الوقت: $timeText") }
        }

        Button(
            onClick = {
                val m = selectedMerchant ?: return@Button
                val lines = merchantItems.mapNotNull { item ->
                    val q = quantities[item.id]?.trim().orEmpty()
                    if (q.isNotEmpty() && q != "0") "- ${item.name}: $q ${item.unit}" else null
                }
                if (lines.isEmpty()) {
                    Toast.makeText(context, "أدخل كمية لصنف واحد على الأقل", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                val message = "السلام عليكم أستاذ ${m.name}، نرجو توفير النواقص التالية:\n" + lines.joinToString("\n")
                onScheduleOrder(m, message, calendar.timeInMillis, m.preferredChannel)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("جدولة الإرسال التلقائي")
        }

        Text("الطلبات المجدولة (${orders.size}):", style = MaterialTheme.typography.titleSmall)
        orders.takeLast(3).forEach { ord ->
            val status = if (ord.isSent) "تم الإرسال" else "بانتظار الموعد"
            Text("• ${formatter.format(Date(ord.triggerAtMillis))} - $status", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(millis))

private fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
