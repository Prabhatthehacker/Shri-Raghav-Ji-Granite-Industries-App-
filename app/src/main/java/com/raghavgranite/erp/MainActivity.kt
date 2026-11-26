package com.raghavgranite.erp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

private val Ink = Color(0xFF101315)
private val Panel = Color(0xFF191E20)
private val Raised = Color(0xFF202729)
private val Border = Color(0xFF2A3334)
private val Warm = Color(0xFFD39A52)
private val Muted = Color(0xFF9BA7A4)
private val Teal = Color(0xFF66B5A0)
private val Red = Color(0xFFDB786C)
private val Good = Color(0xFF77BF91)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GraniteApp() }
    }
}

@Composable
private fun GraniteApp() {
    val context = LocalContext.current
    val db = remember { BusinessDb(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var stock by remember { mutableStateOf(emptyList<StockItem>()) }
    var batches by remember { mutableStateOf(emptyList<ProductionBatch>()) }
    var overview by remember { mutableStateOf(Overview(0,0,0,0,0,0,0,0,0)) }
    var refreshCount by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val refresh: suspend () -> Unit = {
        try {
            val result = withContext(Dispatchers.IO) { Triple(db.products(), db.batches(), db.overview()) }
            stock = result.first; batches = result.second; overview = result.third; error = null; loading = false
        } catch (e: Exception) { error = e.message ?: "Could not open local records"; loading = false }
    }
    LaunchedEffect(refreshCount) { refresh() }
    MaterialTheme(colorScheme = darkColorScheme(primary = Warm, secondary = Teal, background = Ink, surface = Panel, onSurface = Color(0xFFF2F3EE))) {
        Scaffold(containerColor = Ink, bottomBar = {
            NavigationBar(containerColor = Panel, contentColor = Muted) {
                listOf("Overview", "Stock", "Production", "Dispatch", "Settings").forEachIndexed { index, label ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Text(listOf("▦","▤","◈","⇢","⚙")[index], fontSize = 18.sp) }, label = { Text(label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Warm, selectedTextColor = Warm, indicatorColor = Color(0xFF382B1E), unselectedIconColor = Muted, unselectedTextColor = Muted))
                }
            }
        }) { padding ->
            if (loading) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Warm) }
            else if (error != null) Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LOCAL DATABASE ERROR", color = Red, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text(error.orEmpty(), color = Muted); Spacer(Modifier.height(16.dp)); Button(onClick = { loading = true; refreshCount++ }) { Text("Try again") }
            } else when (tab) {
                0 -> Dashboard(overview, batches, Modifier.padding(padding))
                1 -> InventoryScreen(stock, Modifier.padding(padding), onMovement = { item, direction, quantity, cost, note ->
                    scope.launch { try { withContext(Dispatchers.IO) { db.stockMovement(item.id, direction * DomainRules.quantityToMilli(quantity), note, if (direction > 0) DomainRules.rupeesToPaise(cost.ifBlank { "0" }) else 0) }; refreshCount++; Toast.makeText(context, "Stock movement saved", Toast.LENGTH_SHORT).show() } catch (e: Exception) { Toast.makeText(context, e.message ?: "Stock not changed", Toast.LENGTH_LONG).show() } }
                })
                2 -> ProductionScreen(batches, Modifier.padding(padding), onCreate = { number, raw, crushed, dust, granite, cost, operator ->
                    scope.launch { try { withContext(Dispatchers.IO) { db.createBatch(number, DomainRules.quantityToMilli(raw), if (crushed.isBlank()) 0 else DomainRules.quantityToMilli(crushed), if (dust.isBlank()) 0 else DomainRules.quantityToMilli(dust), if (granite.isBlank()) 0 else DomainRules.quantityToMilli(granite), DomainRules.rupeesToPaise(cost.ifBlank { "0" }), operator.takeIf { it.isNotBlank() }) }; refreshCount++; Toast.makeText(context, "Batch saved as pending approval", Toast.LENGTH_SHORT).show() } catch (e: Exception) { Toast.makeText(context, e.message ?: "Batch not saved", Toast.LENGTH_LONG).show() } }
                }, onApprove = { id -> scope.launch { try { withContext(Dispatchers.IO) { db.approveBatch(id) }; refreshCount++; Toast.makeText(context, "Approved; stock updated", Toast.LENGTH_SHORT).show() } catch (e: Exception) { Toast.makeText(context, e.message ?: "Approval failed", Toast.LENGTH_LONG).show() } } })
                3 -> DispatchScreen(stock.filter { it.category == "finished" }, Modifier.padding(padding), onSave = { registration, productId, quantity, gross, tare, customer ->
                    scope.launch { try { withContext(Dispatchers.IO) { db.saveBuyerDispatch(UUID.randomUUID().toString(), registration, productId, DomainRules.quantityToMilli(quantity), gross.takeIf { it.isNotBlank() }?.let { DomainRules.quantityToMilli(it) }, tare.takeIf { it.isNotBlank() }?.let { DomainRules.quantityToMilli(it) }, customer.takeIf { it.isNotBlank() }) }; refreshCount++; Toast.makeText(context, "Dispatch saved; stock deducted", Toast.LENGTH_SHORT).show() } catch (e: Exception) { Toast.makeText(context, e.message ?: "Dispatch not saved", Toast.LENGTH_LONG).show() } }
                })
                else -> SettingsScreen(db, Modifier.padding(padding), onSave = { values -> scope.launch { try { withContext(Dispatchers.IO) { db.saveSettings(values) }; Toast.makeText(context, "Settings saved on this device", Toast.LENGTH_SHORT).show() } catch (e: Exception) { Toast.makeText(context, e.message ?: "Settings not saved", Toast.LENGTH_LONG).show() } } })
            }
        }
    }
}

@Composable
private fun Dashboard(data: Overview, batches: List<ProductionBatch>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Header("SHRI RAGHAV GRANITE INDUSTRIES", "Overview", "LOCAL")
        Surface(color = Color(0xFF242923), shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3E402F)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(19.dp)) {
                Text("TODAY · INR", color = Warm, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp, letterSpacing = 1.5.sp)
                Text(inr(data.salesPaise), color = Color(0xFFF2F3EE), fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, modifier = Modifier.padding(top = 6.dp))
                Text("Sales recorded today", color = Muted, fontSize = 11.sp)
                HorizontalDivider(color = Border, modifier = Modifier.padding(vertical = 15.dp))
                Text("Received  ${inr(data.receivedPaise)}     •     Paid  ${inr(data.paidPaise)}", color = Color(0xFFD1D4CB), fontWeight = FontWeight.Medium, fontSize = 11.sp)
            }
        }
        Section("Business position")
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricCard("PURCHASES TODAY",inr(data.purchasesPaise),Teal,Modifier.weight(1f)); MetricCard("CUSTOMER DUES",inr(data.receivablePaise),Warm,Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.padding(top = 9.dp)) {
            MetricCard("SUPPLIER DUES",inr(data.payablePaise),Red,Modifier.weight(1f)); MetricCard("STOCK ON HAND","${fmtQty(data.totalStockMilli)} t",Color(0xFFF2F3EE),Modifier.weight(1f))
        }
        Surface(color = Panel, shape = RoundedCornerShape(17.dp), border = androidx.compose.foundation.BorderStroke(1.dp,Border), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("PLANT FLOOR",color=Warm,fontSize=9.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=1.3.sp); Text("Production today",color=Color(0xFFF2F3EE),fontSize=17.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=5.dp)) }; Text(data.productionToday.toString().padStart(2,'0'),color=Warm,fontSize=30.sp,fontWeight=FontWeight.ExtraBold) }
                LinearProgressIndicator(progress=if(data.productionToday>0)0.62f else 0.02f, color=Warm, trackColor=Raised, modifier=Modifier.fillMaxWidth().padding(top=14.dp,bottom=10.dp))
                Text("${batches.count { it.status == "pending" }} batches awaiting approval · ${data.lowStockCount} low-stock alerts",color=Muted,fontSize=10.sp)
            }
        }
        Section("Recent production")
        if (batches.isEmpty()) EmptyCard("No production recorded", "Batches are saved locally and require approval before stock changes.")
        else batches.take(3).forEach { BatchRow(it) }
        Text("Offline first · SQLite stored on this device",color=Muted,fontSize=9.sp,modifier=Modifier.fillMaxWidth().padding(18.dp), textAlign=androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable private fun Header(eyebrow:String,title:String,right:String?=null) { Row(Modifier.fillMaxWidth().padding(top=13.dp,bottom=17.dp),verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(eyebrow,color=Warm,fontSize=9.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=1.4.sp); Text(title,color=Color(0xFFF2F3EE),fontSize=26.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=(-0.4).sp,modifier=Modifier.padding(top=4.dp)) }; if(right!=null) Surface(color=Color(0xFF1D3029),shape=RoundedCornerShape(20.dp)) { Text("●  $right",color=Good,fontSize=9.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=0.6.sp,modifier=Modifier.padding(horizontal=10.dp,vertical=7.dp)) } } }
@Composable private fun Section(title:String) { Text(title,color=Color(0xFFF2F3EE),fontSize=15.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=18.dp,bottom=10.dp)) }
@Composable private fun MetricCard(label:String,value:String,tone:Color,modifier:Modifier=Modifier) { Surface(color=Panel,shape=RoundedCornerShape(15.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border),modifier=modifier.heightIn(min=83.dp)) { Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.SpaceBetween) { Text(label,color=Muted,fontSize=8.sp,fontWeight=FontWeight.Bold,letterSpacing=0.5.sp); Text(value,color=tone,fontSize=15.sp,fontWeight=FontWeight.ExtraBold,modifier=Modifier.padding(top=9.dp),maxLines=1) } } }
@Composable private fun BatchRow(batch:ProductionBatch) { Surface(color=Panel,shape=RoundedCornerShape(15.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth().padding(bottom=8.dp)) { Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(batch.batchNumber,color=Color(0xFFF2F3EE),fontSize=13.sp,fontWeight=FontWeight.Bold); Text(batch.date.take(10),color=Muted,fontSize=10.sp,modifier=Modifier.padding(top=4.dp)) }; Text(batch.status.uppercase(),color=if(batch.status=="approved")Good else Warm,fontSize=8.sp,fontWeight=FontWeight.ExtraBold,modifier=Modifier.background(if(batch.status=="approved") Color(0xFF20352A) else Color(0xFF382B1E),RoundedCornerShape(20.dp)).padding(horizontal=9.dp,vertical=6.dp)) } } }
@Composable private fun EmptyCard(title:String,detail:String) { Surface(color=Panel,shape=RoundedCornerShape(15.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally) { Text("◇",color=Warm,fontSize=25.sp); Text(title,color=Color.White,fontWeight=FontWeight.Bold,fontSize=13.sp); Text(detail,color=Muted,fontSize=10.sp,modifier=Modifier.padding(top=5.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center) } } }

@Composable
private fun InventoryScreen(items:List<StockItem>,modifier:Modifier=Modifier,onMovement:(StockItem,Int,String,String,String)->Unit) {
    var chosen by remember { mutableStateOf<StockItem?>(null) }; var sign by remember { mutableIntStateOf(1) }
    Column(modifier.fillMaxSize()) {
        Header("MATERIAL CONTROL","Inventory","WEIGHT UNITS")
        Surface(color=Color(0xFF202824),shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp)) { Column(Modifier.padding(13.dp)) { Text("Stock stays in its configured unit",color=Color.White,fontSize=12.sp,fontWeight=FontWeight.Bold); Text("No automatic conversion between weight and volume.",color=Muted,fontSize=10.sp,modifier=Modifier.padding(top=5.dp)) } }
        if(items.isEmpty()) EmptyCard("No materials found","The local database will show stock here.") else LazyColumn(contentPadding=PaddingValues(15.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            items(items,key={it.id}) { item -> Surface(color=Panel,shape=RoundedCornerShape(16.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(15.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) { Surface(color=Color(0xFF382B1E),shape=RoundedCornerShape(11.dp)) { Text(if(item.category=="raw")"R" else "P",color=Warm,fontWeight=FontWeight.ExtraBold,modifier=Modifier.padding(horizontal=13.dp,vertical=10.dp)) }; Column(Modifier.weight(1f).padding(start=10.dp)) { Text(item.name,color=Color.White,fontWeight=FontWeight.Bold,fontSize=14.sp); Text("${item.category.uppercase()} · ${item.unit.uppercase()}",color=Muted,fontSize=8.sp,modifier=Modifier.padding(top=3.dp)) }; Column(horizontalAlignment=Alignment.End) { Text(fmtQty(item.quantityMilli),color=if(item.lowStockMilli>0&&item.quantityMilli<=item.lowStockMilli)Red else Color.White,fontWeight=FontWeight.ExtraBold,fontSize=18.sp); Text(item.unit,color=Muted,fontSize=9.sp) } }
                HorizontalDivider(color=Border,modifier=Modifier.padding(vertical=12.dp)); Text("Weighted average cost  ${inr(item.avgCostPaise)} / unit",color=Muted,fontSize=10.sp)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(top=12.dp)) { Button(onClick={chosen=item;sign=1},modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=Warm,contentColor=Ink)) { Text("＋  Receive",fontWeight=FontWeight.Bold) }; OutlinedButton(onClick={chosen=item;sign=-1},modifier=Modifier.weight(1f),colors=ButtonDefaults.outlinedButtonColors(contentColor=Color.White)) { Text("−  Issue") } }
            } } }
        }
    }
    if(chosen!=null) StockDialog(chosen!!,sign,{chosen=null}) { qty,cost,note -> val item=chosen!!; chosen=null; onMovement(item,sign,qty,cost,note) }
}

@Composable private fun StockDialog(item:StockItem,sign:Int,onDismiss:()->Unit,onSave:(String,String,String)->Unit) {
    var quantity by remember { mutableStateOf("") }; var cost by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss,containerColor=Panel,title={Text(if(sign>0)"Receive stock" else "Issue stock",color=Color.White,fontWeight=FontWeight.Bold)},text={Column(verticalArrangement=Arrangement.spacedBy(9.dp)) { Text("${item.name} · ${item.unit}",color=Warm,fontSize=11.sp); AppField("QUANTITY (${item.unit})",quantity,{quantity=it},KeyboardType.Decimal); if(sign>0) AppField("UNIT COST (₹, OPTIONAL)",cost,{cost=it},KeyboardType.Decimal); AppField("REFERENCE / NOTE",note,{note=it}) }},confirmButton={TextButton(onClick={onSave(quantity,cost,note)}){Text("Save movement",color=Warm)}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel",color=Muted)}})
}

@Composable private fun ProductionScreen(batches:List<ProductionBatch>,modifier:Modifier=Modifier,onCreate:(String,String,String,String,String,String,String)->Unit,onApprove:(String)->Unit) {
    var show by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        Header("PLANT FLOOR","Production","BATCH LOG")
        Surface(color=Color(0xFF232923),shape=RoundedCornerShape(17.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF3B4033)),modifier=Modifier.fillMaxWidth().padding(horizontal=15.dp)) { Column(Modifier.padding(16.dp)) {
            Text("Stone crushing",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.ExtraBold); Text("Record production and costs. Finished stock changes only after approval.",color=Muted,fontSize=10.sp,modifier=Modifier.padding(top=6.dp))
            Row(Modifier.padding(vertical=14.dp),horizontalArrangement=Arrangement.spacedBy(25.dp)) { TinyMetric("PENDING",batches.count{it.status=="pending"}.toString().padStart(2,'0')); TinyMetric("APPROVED",batches.count{it.status=="approved"}.toString().padStart(2,'0')) }
            Button(onClick={show=true},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Warm,contentColor=Ink)) { Text("＋  Record production batch",fontWeight=FontWeight.Bold) }
        } }
        Section("Batch history")
        if(batches.isEmpty()) EmptyCard("No batches yet","Create a batch and approve it before it affects inventory.") else LazyColumn(contentPadding=PaddingValues(horizontal=15.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) { items(batches,key={it.id}) { batch -> Surface(color=Panel,shape=RoundedCornerShape(15.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(batch.batchNumber,color=Color.White,fontWeight=FontWeight.Bold,fontSize=13.sp); Text(batch.date.take(16).replace('T',' '),color=Muted,fontSize=9.sp,modifier=Modifier.padding(top=3.dp)) }; Text(batch.status.uppercase(),color=if(batch.status=="approved")Good else Warm,fontSize=8.sp,fontWeight=FontWeight.Bold) }
            HorizontalDivider(color=Border,modifier=Modifier.padding(vertical=10.dp)); Text("Input  ${fmtQty(batch.raw)} t     →     Output  ${fmtQty(batch.crushed+batch.dust+batch.granite)} t",color=Color.White,fontSize=11.sp); Text("Cost  ${inr(batch.costPaise)}",color=Muted,fontSize=10.sp,modifier=Modifier.padding(top=4.dp))
            if(batch.status=="pending") OutlinedButton(onClick={onApprove(batch.id)},modifier=Modifier.fillMaxWidth().padding(top=9.dp),colors=ButtonDefaults.outlinedButtonColors(contentColor=Warm)){Text("Approve and post stock")}
        } } } }
    }
    if(show) ProductionDialog({show=false}) { number,raw,crushed,dust,granite,cost,operator -> show=false; onCreate(number,raw,crushed,dust,granite,cost,operator) }
}
@Composable private fun TinyMetric(label:String,value:String) { Column { Text(label,color=Muted,fontSize=8.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp); Text(value,color=Warm,fontSize=22.sp,fontWeight=FontWeight.ExtraBold,modifier=Modifier.padding(top=3.dp)) } }

@Composable private fun ProductionDialog(onDismiss:()->Unit,onSave:(String,String,String,String,String,String,String)->Unit) {
    var number by remember { mutableStateOf("B-${System.currentTimeMillis().toString().takeLast(7)}") }; var raw by remember { mutableStateOf("") }; var crushed by remember { mutableStateOf("") }; var dust by remember { mutableStateOf("") }; var granite by remember { mutableStateOf("0") }; var cost by remember { mutableStateOf("") }; var operator by remember { mutableStateOf("") }
    val metrics = runCatching { DomainRules.production(DomainRules.quantityToMilli(raw),if(crushed.isBlank()) 0 else DomainRules.quantityToMilli(crushed),if(dust.isBlank()) 0 else DomainRules.quantityToMilli(dust),if(granite.isBlank()) 0 else DomainRules.quantityToMilli(granite),DomainRules.rupeesToPaise(cost.ifBlank{"0"})) }.getOrNull()
    AlertDialog(onDismissRequest=onDismiss,containerColor=Panel,title={Text("New production batch",color=Color.White,fontWeight=FontWeight.Bold)},text={Column(Modifier.heightIn(max=470.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(7.dp)) {
        Text("All quantities in tonnes · output cannot exceed input",color=Muted,fontSize=10.sp)
        AppField("BATCH NUMBER",number,{number=it}); AppField("RAW STONE INPUT (T)",raw,{raw=it},KeyboardType.Decimal); AppField("CRUSHED STONE OUTPUT (T)",crushed,{crushed=it},KeyboardType.Decimal); AppField("STONE DUST OUTPUT (T)",dust,{dust=it},KeyboardType.Decimal); AppField("GRANITE OUTPUT (T)",granite,{granite=it},KeyboardType.Decimal); AppField("OPERATING COST (₹)",cost,{cost=it},KeyboardType.Decimal); AppField("OPERATOR (OPTIONAL)",operator,{operator=it})
        Surface(color=Raised,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(11.dp)) { Text("YIELD PREVIEW",color=Warm,fontSize=9.sp,fontWeight=FontWeight.Bold); Text("${metrics?.yieldPercent?.let{"%.1f%%".format(it)}?:"—"} yield     ${metrics?.lossMilli?.let{fmtQty(it)+" t loss"}?:""}",color=Color.White,fontSize=11.sp,modifier=Modifier.padding(top=6.dp)); Text("${metrics?.let{inr(it.costPerOutputUnitPaise)}?:"—"} / output tonne",color=Muted,fontSize=10.sp,modifier=Modifier.padding(top=4.dp)) } }
    }},confirmButton={TextButton(enabled=metrics!=null&&number.isNotBlank(),onClick={onSave(number,raw,crushed,dust,granite,cost,operator)}){Text("Save pending",color=Warm)}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel",color=Muted)}})
}

@Composable private fun DispatchScreen(products:List<StockItem>,modifier:Modifier=Modifier,onSave:(String,String,String,String,String,String)->Unit) {
    var show by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        Header("BUYER COLLECTION","Dispatch","CUSTOMER VEHICLE")
        Surface(color=Color(0xFF202824),shape=RoundedCornerShape(15.dp),modifier=Modifier.fillMaxWidth().padding(horizontal=15.dp)) { Column(Modifier.padding(15.dp)) {
            Text("Buyer-arranged transport",color=Color.White,fontWeight=FontWeight.Bold,fontSize=14.sp)
            Text("Customer vehicles are recorded on the dispatch only. They are never added to company fleet or company vehicle expenses. Transport charges are not added automatically.",color=Muted,fontSize=10.sp,lineHeight=15.sp,modifier=Modifier.padding(top=7.dp))
        } }
        Section("Outgoing loads")
        Button(onClick={show=true},modifier=Modifier.fillMaxWidth().padding(horizontal=15.dp),colors=ButtonDefaults.buttonColors(containerColor=Warm,contentColor=Ink)){Text("＋  Record buyer collection",fontWeight=FontWeight.Bold)}
        Spacer(Modifier.height(18.dp)); EmptyCard("No dispatches recorded", "New outgoing loads deduct finished stock once, within the same database transaction.")
    }
    if(show) DispatchDialog(products,{show=false}) { reg,product,qty,gross,tare,customer -> show=false; onSave(reg,product,qty,gross,tare,customer) }
}

@Composable private fun DispatchDialog(products:List<StockItem>,onDismiss:()->Unit,onSave:(String,String,String,String,String,String)->Unit) {
    var reg by remember { mutableStateOf("") }; var customer by remember { mutableStateOf("") }; var quantity by remember { mutableStateOf("") }; var gross by remember { mutableStateOf("") }; var tare by remember { mutableStateOf("") }; var product by remember { mutableStateOf(products.firstOrNull()?.id.orEmpty()) }
    AlertDialog(onDismissRequest=onDismiss,containerColor=Panel,title={Text("Buyer collection",color=Color.White,fontWeight=FontWeight.Bold)},text={Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        AppField("BUYER VEHICLE REGISTRATION",reg,{reg=it.uppercase()}); AppField("CUSTOMER (OPTIONAL)",customer,{customer=it})
        Text("FINISHED PRODUCT",color=Muted,fontSize=9.sp,fontWeight=FontWeight.Bold)
        products.forEach { item -> FilterChip(selected=product==item.id,onClick={product=item.id},label={Text("${item.name} · ${fmtQty(item.quantityMilli)} t")},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Color(0xFF382B1E),selectedLabelColor=Warm)) }
        AppField("QUANTITY (T)",quantity,{quantity=it},KeyboardType.Decimal); AppField("GROSS WEIGHT (OPTIONAL)",gross,{gross=it},KeyboardType.Decimal); AppField("TARE WEIGHT (OPTIONAL)",tare,{tare=it},KeyboardType.Decimal)
        Text("No company vehicle expenses or automatic transport charges will be created.",color=Muted,fontSize=9.sp)
    }},confirmButton={TextButton(enabled=reg.isNotBlank()&&(quantity.toDoubleOrNull()?:0.0)>0&&product.isNotBlank(),onClick={onSave(reg,product,quantity,gross,tare,customer)}){Text("Save and deduct stock",color=Warm)}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel",color=Muted)}})
}

@Composable private fun SettingsScreen(db:BusinessDb,modifier:Modifier=Modifier,onSave:(Map<String,String>)->Unit) {
    var values by remember { mutableStateOf(mapOf<String,String>()) }
    LaunchedEffect(db) { values = withContext(Dispatchers.IO) { db.settings() } }
    fun update(key:String,value:String) { values=values+ (key to value) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp)) {
        Header("BUSINESS CONFIGURATION","Settings")
        Surface(color=Color(0xFF202824),shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(13.dp)) { Text("Business details are configurable",color=Color.White,fontSize=12.sp,fontWeight=FontWeight.Bold); Text("No address, GSTIN or contact details have been invented.",color=Muted,fontSize=10.sp,modifier=Modifier.padding(top=5.dp)) } }
        Section("Business profile")
        AppField("BUSINESS NAME",values["business_name"].orEmpty(),{update("business_name",it)})
        AppField("REGISTERED ADDRESS",values["business_address"].orEmpty(),{update("business_address",it)})
        AppField("GSTIN",values["gstin"].orEmpty(),{update("gstin",it.uppercase())})
        AppField("BUSINESS PHONE",values["business_phone"].orEmpty(),{update("business_phone",it)},KeyboardType.Phone)
        Section("Material controls")
        AppField("VERIFIED DENSITY (KG/M³)",values["default_density_kg_m3"].orEmpty(),{update("default_density_kg_m3",it)},KeyboardType.Decimal)
        Text("Density is stored as a setting only. The app does not convert weight into volume automatically.",color=Muted,fontSize=9.sp,modifier=Modifier.padding(bottom=14.dp))
        Section("Accounting defaults")
        listOf("Currency" to "INR · ₹","Financial year" to "April – March","GST accounting" to "Enabled","Inventory costing" to "Weighted average").forEach { (a,b) -> Text("$a      $b",color=Color.White,fontSize=11.sp,modifier=Modifier.padding(vertical=6.dp)) }
        Button(onClick={onSave(values)},modifier=Modifier.fillMaxWidth().padding(top=16.dp,bottom=28.dp),colors=ButtonDefaults.buttonColors(containerColor=Warm,contentColor=Ink)){Text("Save business settings",fontWeight=FontWeight.Bold)}
    }
}

@Composable private fun AppField(label:String,value:String,onChange:(String)->Unit,type:KeyboardType=KeyboardType.Text) {
    Column {
        Text(label,color=Muted,fontSize=8.sp,fontWeight=FontWeight.Bold,letterSpacing=0.7.sp,modifier=Modifier.padding(bottom=4.dp))
        OutlinedTextField(value=value,onValueChange=onChange,singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=type),modifier=Modifier.fillMaxWidth(),colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedBorderColor=Warm,unfocusedBorderColor=Border,focusedContainerColor=Raised,unfocusedContainerColor=Raised,cursorColor=Warm))
    }
}

private fun inr(paise:Long):String = NumberFormat.getCurrencyInstance(Locale("en","IN")).format(paise/100.0)
private fun fmtQty(milli:Long):String = String.format(Locale.US,"%,.3f",milli/1000.0)
