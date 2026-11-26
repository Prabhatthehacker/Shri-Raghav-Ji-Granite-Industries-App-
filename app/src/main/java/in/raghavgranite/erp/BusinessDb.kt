package in.raghavgranite.erp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID
import java.math.BigDecimal
import java.math.RoundingMode

data class StockItem(val id: String, val name: String, val category: String, val unit: String, val quantityMilli: Long, val avgCostPaise: Long, val lowStockMilli: Long)
data class ProductionBatch(val id: String, val batchNumber: String, val date: String, val raw: Long, val crushed: Long, val dust: Long, val granite: Long, val costPaise: Long, val status: String, val operator: String?)
data class Overview(val salesPaise: Long, val purchasesPaise: Long, val receivedPaise: Long, val paidPaise: Long, val receivablePaise: Long, val payablePaise: Long, val productionToday: Int, val totalStockMilli: Long, val lowStockCount: Long)

class BusinessDb(context: Context) : SQLiteOpenHelper(context, "raghav-granite.db", null, 1) {
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }

    override fun onCreate(db: SQLiteDatabase) {
        val statements = listOf(
            "CREATE TABLE settings(key TEXT PRIMARY KEY NOT NULL,value TEXT NOT NULL)",
            "CREATE TABLE products(id TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL,category TEXT NOT NULL,unit TEXT NOT NULL,low_stock_milli INTEGER NOT NULL DEFAULT 0,density_kg_m3 REAL,active INTEGER NOT NULL DEFAULT 1)",
            "CREATE TABLE inventory(product_id TEXT PRIMARY KEY NOT NULL REFERENCES products(id),quantity_milli INTEGER NOT NULL DEFAULT 0 CHECK(quantity_milli>=0),avg_cost_paise INTEGER NOT NULL DEFAULT 0 CHECK(avg_cost_paise>=0),updated_at TEXT NOT NULL)",
            "CREATE TABLE stock_movements(id TEXT PRIMARY KEY NOT NULL,product_id TEXT NOT NULL REFERENCES products(id),movement_type TEXT NOT NULL,quantity_delta_milli INTEGER NOT NULL CHECK(quantity_delta_milli!=0),unit_cost_paise INTEGER NOT NULL DEFAULT 0,reference_type TEXT,reference_id TEXT,note TEXT,created_at TEXT NOT NULL)",
            "CREATE INDEX idx_movements_product_date ON stock_movements(product_id,created_at)",
            "CREATE TABLE production_batches(id TEXT PRIMARY KEY NOT NULL,batch_number TEXT NOT NULL UNIQUE,produced_at TEXT NOT NULL,raw_product_id TEXT NOT NULL REFERENCES products(id),raw_input_milli INTEGER NOT NULL CHECK(raw_input_milli>0),crushed_output_milli INTEGER NOT NULL DEFAULT 0,dust_output_milli INTEGER NOT NULL DEFAULT 0,granite_output_milli INTEGER NOT NULL DEFAULT 0,costs_paise INTEGER NOT NULL DEFAULT 0,machine_id TEXT,operator TEXT,shift TEXT,electricity_kwh_milli INTEGER,diesel_litres_milli INTEGER,operating_hours_milli INTEGER,downtime_minutes INTEGER,notes TEXT,approval_status TEXT NOT NULL DEFAULT 'pending' CHECK(approval_status IN ('pending','approved','rejected')),approved_at TEXT)",
            "CREATE TABLE machines(id TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL,serial_number TEXT,operator TEXT,last_service_at TEXT,next_service_at TEXT,operating_hours_milli INTEGER NOT NULL DEFAULT 0,active INTEGER NOT NULL DEFAULT 1)",
            "CREATE TABLE customers(id TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL,business_name TEXT,phone TEXT,address TEXT,gstin TEXT,opening_balance_paise INTEGER NOT NULL DEFAULT 0)",
            "CREATE TABLE suppliers(id TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL,phone TEXT,address TEXT,gstin TEXT,opening_balance_paise INTEGER NOT NULL DEFAULT 0)",
            "CREATE TABLE purchase_documents(id TEXT PRIMARY KEY NOT NULL,supplier_id TEXT,invoice_number TEXT,purchased_at TEXT NOT NULL,subtotal_paise INTEGER NOT NULL DEFAULT 0,gst_paise INTEGER NOT NULL DEFAULT 0,other_charges_paise INTEGER NOT NULL DEFAULT 0,total_paise INTEGER NOT NULL DEFAULT 0,paid_paise INTEGER NOT NULL DEFAULT 0,due_at TEXT,status TEXT NOT NULL DEFAULT 'draft')",
            "CREATE TABLE sales_documents(id TEXT PRIMARY KEY NOT NULL,customer_id TEXT,invoice_number TEXT,sold_at TEXT NOT NULL,subtotal_paise INTEGER NOT NULL DEFAULT 0,gst_paise INTEGER NOT NULL DEFAULT 0,other_charges_paise INTEGER NOT NULL DEFAULT 0,total_paise INTEGER NOT NULL DEFAULT 0,received_paise INTEGER NOT NULL DEFAULT 0,due_at TEXT,document_type TEXT NOT NULL DEFAULT 'invoice',status TEXT NOT NULL DEFAULT 'draft')",
            "CREATE TABLE payments(id TEXT PRIMARY KEY NOT NULL,direction TEXT NOT NULL CHECK(direction IN ('received','paid')),method TEXT NOT NULL,amount_paise INTEGER NOT NULL CHECK(amount_paise>0),reference_number TEXT,customer_id TEXT,supplier_id TEXT,sales_document_id TEXT,purchase_document_id TEXT,payment_date TEXT NOT NULL,verified_at TEXT,notes TEXT,created_at TEXT NOT NULL)",
            "CREATE UNIQUE INDEX idx_payment_ref ON payments(method,reference_number) WHERE reference_number IS NOT NULL AND reference_number!=''",
            "CREATE TABLE accounts(id TEXT PRIMARY KEY NOT NULL,code TEXT UNIQUE NOT NULL,name TEXT NOT NULL,account_type TEXT NOT NULL,parent_id TEXT REFERENCES accounts(id),active INTEGER NOT NULL DEFAULT 1)",
            "CREATE TABLE journal_entries(id TEXT PRIMARY KEY NOT NULL,entry_date TEXT NOT NULL,description TEXT NOT NULL,source_type TEXT,source_id TEXT,status TEXT NOT NULL DEFAULT 'draft' CHECK(status IN ('draft','posted','reversed')),reversal_of TEXT REFERENCES journal_entries(id),created_at TEXT NOT NULL)",
            "CREATE TABLE journal_lines(id TEXT PRIMARY KEY NOT NULL,entry_id TEXT NOT NULL REFERENCES journal_entries(id),account_id TEXT NOT NULL REFERENCES accounts(id),debit_paise INTEGER NOT NULL DEFAULT 0 CHECK(debit_paise>=0),credit_paise INTEGER NOT NULL DEFAULT 0 CHECK(credit_paise>=0),CHECK(NOT(debit_paise>0 AND credit_paise>0)))",
            "CREATE TABLE vehicles(id TEXT PRIMARY KEY NOT NULL,registration TEXT NOT NULL UNIQUE,vehicle_type TEXT,driver TEXT,driver_phone TEXT,insurance_due TEXT,permit_due TEXT,pollution_due TEXT,odometer_milli INTEGER NOT NULL DEFAULT 0,active INTEGER NOT NULL DEFAULT 1)",
            "CREATE TABLE trips(id TEXT PRIMARY KEY NOT NULL,vehicle_id TEXT NOT NULL REFERENCES vehicles(id),supplier_id TEXT,pickup_location TEXT,arrived_at TEXT NOT NULL,gross_weight_milli INTEGER,tare_weight_milli INTEGER,net_weight_milli INTEGER,fuel_litres_milli INTEGER,expense_paise INTEGER NOT NULL DEFAULT 0,purchase_document_id TEXT,slip_uri TEXT)",
            "CREATE TABLE dispatches(id TEXT PRIMARY KEY NOT NULL,customer_id TEXT,buyer_registration TEXT NOT NULL,driver_name TEXT,driver_phone TEXT,product_id TEXT NOT NULL REFERENCES products(id),quantity_milli INTEGER NOT NULL CHECK(quantity_milli>0),gross_weight_milli INTEGER,tare_weight_milli INTEGER,net_weight_milli INTEGER,loaded_at TEXT NOT NULL,sales_document_id TEXT,status TEXT NOT NULL DEFAULT 'draft',slip_uri TEXT,stock_applied INTEGER NOT NULL DEFAULT 0,created_at TEXT NOT NULL)",
            "CREATE TABLE expenses(id TEXT PRIMARY KEY NOT NULL,category TEXT NOT NULL,description TEXT NOT NULL,amount_paise INTEGER NOT NULL CHECK(amount_paise>=0),expense_date TEXT NOT NULL,vehicle_id TEXT,machine_id TEXT,document_uri TEXT)",
            "CREATE TABLE gst_rates(id TEXT PRIMARY KEY NOT NULL,hsn_code TEXT,rate_basis_points INTEGER NOT NULL CHECK(rate_basis_points BETWEEN 0 AND 10000),effective_from TEXT NOT NULL,effective_to TEXT)",
            "CREATE TABLE documents(id TEXT PRIMARY KEY NOT NULL,category TEXT NOT NULL,title TEXT NOT NULL,uri TEXT NOT NULL,mime_type TEXT,related_type TEXT,related_id TEXT,created_at TEXT NOT NULL)",
            "CREATE TABLE audit_logs(id TEXT PRIMARY KEY NOT NULL,entity_type TEXT NOT NULL,entity_id TEXT NOT NULL,action TEXT NOT NULL,before_json TEXT,after_json TEXT,actor TEXT,created_at TEXT NOT NULL)",
            "CREATE TABLE users(id TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL,role TEXT NOT NULL,pin_hash TEXT,active INTEGER NOT NULL DEFAULT 1)",
            "INSERT INTO products(id,name,category,unit) VALUES('raw-stone','Raw stone','raw','tonnes'),('crushed-stone','Crushed stone','finished','tonnes'),('stone-dust','Stone dust','finished','tonnes'),('granite','Granite','finished','tonnes')",
            "INSERT INTO settings(key,value) VALUES('business_name','Shri Raghav Granite Industries'),('country','India'),('currency','INR'),('financial_year_start_month','4'),('gst_enabled','true'),('costing_method','weighted_average')"
        )
        statements.forEach { statement -> db.execSQL(statement) }
        val stamp = now()
        listOf("raw-stone", "crushed-stone", "stone-dust", "granite").forEach { product ->
            db.execSQL("INSERT INTO inventory(product_id,quantity_milli,updated_at) VALUES(?,0,?)", arrayOf(product, stamp))
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { /* Add numbered migrations before increasing the DB version. */ }

    fun products(): List<StockItem> = readableDatabase.rawQuery("SELECT p.id,p.name,p.category,p.unit,i.quantity_milli,i.avg_cost_paise,p.low_stock_milli FROM products p JOIN inventory i ON p.id=i.product_id WHERE p.active=1 ORDER BY p.category,p.name", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(StockItem(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getLong(4), cursor.getLong(5), cursor.getLong(6))) }
    }

    fun batches(): List<ProductionBatch> = readableDatabase.rawQuery("SELECT id,batch_number,produced_at,raw_input_milli,crushed_output_milli,dust_output_milli,granite_output_milli,costs_paise,approval_status,operator FROM production_batches ORDER BY produced_at DESC LIMIT 50", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(ProductionBatch(cursor.getString(0),cursor.getString(1),cursor.getString(2),cursor.getLong(3),cursor.getLong(4),cursor.getLong(5),cursor.getLong(6),cursor.getLong(7),cursor.getString(8),cursor.getString(9))) }
    }

    fun overview(): Overview {
        fun sum(sql: String): Long = readableDatabase.rawQuery(sql, null).use { if (it.moveToFirst()) it.getLong(0) else 0L }
        val sales = sum("SELECT COALESCE(SUM(total_paise),0) FROM sales_documents WHERE date(sold_at)=date('now','localtime') AND status!='draft'")
        val purchases = sum("SELECT COALESCE(SUM(total_paise),0) FROM purchase_documents WHERE date(purchased_at)=date('now','localtime') AND status!='draft'")
        val received = sum("SELECT COALESCE(SUM(amount_paise),0) FROM payments WHERE direction='received' AND date(payment_date)=date('now','localtime')")
        val paid = sum("SELECT COALESCE(SUM(amount_paise),0) FROM payments WHERE direction='paid' AND date(payment_date)=date('now','localtime')")
        val recv = sum("SELECT COALESCE(SUM(total_paise-received_paise),0) FROM sales_documents WHERE status!='draft'")
        val pay = sum("SELECT COALESCE(SUM(total_paise-paid_paise),0) FROM purchase_documents WHERE status!='draft'")
        val stock = sum("SELECT COALESCE(SUM(quantity_milli),0) FROM inventory")
        val low = sum("SELECT COUNT(*) FROM products p JOIN inventory i ON p.id=i.product_id WHERE p.low_stock_milli>0 AND i.quantity_milli<=p.low_stock_milli")
        val today = sum("SELECT COUNT(*) FROM production_batches WHERE approval_status='approved' AND date(produced_at)=date('now','localtime')").coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return Overview(sales,purchases,received,paid,recv,pay,today,stock,low)
    }

    fun stockMovement(productId: String, deltaMilli: Long, note: String, unitCostPaise: Long = 0) {
        require(unitCostPaise >= 0)
        val db = writableDatabase
        db.beginTransaction()
        try {
            changeStock(db, productId, deltaMilli, if (deltaMilli > 0) "in" else "out", "manual", uuid(), note, unitCostPaise)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun createBatch(batchNumber: String, raw: Long, crushed: Long, dust: Long, granite: Long, costPaise: Long, operator: String?): String {
        DomainRules.production(raw,crushed,dust,granite,costPaise)
        val id = uuid(); val values = ContentValues().apply {
            put("id",id); put("batch_number",batchNumber); put("produced_at",now()); put("raw_product_id","raw-stone"); put("raw_input_milli",raw)
            put("crushed_output_milli",crushed); put("dust_output_milli",dust); put("granite_output_milli",granite); put("costs_paise",costPaise); put("operator",operator)
        }
        val inserted = writableDatabase.insertOrThrow("production_batches",null,values)
        check(inserted != -1L)
        return id
    }

    fun approveBatch(batchId: String) {
        val db = writableDatabase; db.beginTransaction()
        try {
            val row = db.rawQuery("SELECT raw_product_id,raw_input_milli,crushed_output_milli,dust_output_milli,granite_output_milli,costs_paise,approval_status FROM production_batches WHERE id=?", arrayOf(batchId)).use { c ->
                if (!c.moveToFirst()) error("Production batch was not found")
                Array(7) { c.getString(it) }
            }
            check(row[6] == "pending") { "Only a pending batch can be approved" }
            val rawProduct = row[0]; val raw = row[1].toLong(); val crushed = row[2].toLong(); val dust = row[3].toLong(); val granite = row[4].toLong(); val cost = row[5].toLong()
            val metrics = DomainRules.production(raw,crushed,dust,granite,cost)
            changeStock(db,rawProduct,-raw,"production_input","production",batchId,"Approved production batch",0)
            val unitCost = if (metrics.outputMilli == 0L) 0 else BigDecimal.valueOf(cost).multiply(BigDecimal.valueOf(1000)).divide(BigDecimal.valueOf(metrics.outputMilli), 0, RoundingMode.HALF_UP).longValueExact()
            listOf("crushed-stone" to crushed,"stone-dust" to dust,"granite" to granite).forEach { (product,qty) ->
                if (qty > 0) changeStock(db,product,qty,"production_output","production",batchId,"Production batch output",unitCost)
            }
            val updated = ContentValues().apply { put("approval_status","approved"); put("approved_at",now()) }
            check(db.update("production_batches",updated,"id=? AND approval_status='pending'",arrayOf(batchId)) == 1)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun saveBuyerDispatch(dispatchId: String, registration: String, productId: String, quantity: Long, gross: Long?, tare: Long?, customer: String?) {
        require(registration.isNotBlank() && quantity > 0)
        val net = if (gross != null && tare != null) DomainRules.netWeightMilli(gross,tare) else null
        val db = writableDatabase; db.beginTransaction()
        try {
            val previous = db.rawQuery("SELECT product_id,quantity_milli,stock_applied FROM dispatches WHERE id=?",arrayOf(dispatchId)).use { c ->
                if (c.moveToFirst()) Triple(c.getString(0),c.getLong(1),c.getInt(2) == 1) else null
            }
            if (previous?.third == true) changeStock(db,previous.first,-previous.second,"dispatch_edit_restore","dispatch_edit",uuid(),"Restore before editing dispatch",0)
            val values = ContentValues().apply {
                put("id",dispatchId); put("customer_id",customer); put("buyer_registration",registration.trim().uppercase()); put("product_id",productId); put("quantity_milli",quantity)
                put("gross_weight_milli",gross); put("tare_weight_milli",tare); put("net_weight_milli",net); put("loaded_at",now()); put("status","draft"); put("stock_applied",0); put("created_at",now())
            }
            db.insertWithOnConflict("dispatches",null,values,SQLiteDatabase.CONFLICT_REPLACE)
            changeStock(db,productId,-quantity,"dispatch","dispatch",uuid(),"Buyer collection dispatch",0)
            val posted = ContentValues().apply { put("stock_applied",1); put("status","loaded") }
            db.update("dispatches",posted,"id=?",arrayOf(dispatchId))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    private fun changeStock(db: SQLiteDatabase, productId: String, delta: Long, type: String, refType: String, refId: String, note: String, unitCostPaise: Long) {
        val current = db.rawQuery("SELECT quantity_milli,avg_cost_paise FROM inventory WHERE product_id=?",arrayOf(productId)).use { c ->
            if (!c.moveToFirst()) error("Product inventory was not found")
            c.getLong(0) to c.getLong(1)
        }
        val after = DomainRules.stockAfter(current.first,delta)
        val cost = if (delta > 0) unitCostPaise else current.second
        val avg = if (delta > 0 && after > 0 && (cost > 0 || current.second == 0L)) DomainRules.weightedAverageCostPaise(current.first, current.second, delta, cost)
            else if (delta > 0 && after > 0) DomainRules.weightedAverageCostPaise(current.first, current.second, delta, current.second)
            else current.second
        db.execSQL("UPDATE inventory SET quantity_milli=?,avg_cost_paise=?,updated_at=? WHERE product_id=?",arrayOf(after,avg,now(),productId))
        val values = ContentValues().apply { put("id",uuid());put("product_id",productId);put("movement_type",type);put("quantity_delta_milli",delta);put("unit_cost_paise",cost);put("reference_type",refType);put("reference_id",refId);put("note",note);put("created_at",now()) }
        db.insertOrThrow("stock_movements",null,values)
    }

    fun saveSettings(values: Map<String,String>) {
        val db=writableDatabase; db.beginTransaction()
        try { values.forEach { (key,value) -> db.execSQL("INSERT INTO settings(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",arrayOf(key,value)) }; db.setTransactionSuccessful() }
        finally { db.endTransaction() }
    }
    fun settings(): Map<String,String> = readableDatabase.rawQuery("SELECT key,value FROM settings",null).use { c -> buildMap { while(c.moveToNext()) put(c.getString(0),c.getString(1)) } }

    private fun now() = java.time.Instant.now().toString()
    private fun uuid() = UUID.randomUUID().toString()
}
