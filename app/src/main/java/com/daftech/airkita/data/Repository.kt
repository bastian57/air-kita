package com.daftech.airkita.data

import com.daftech.airkita.data.entity.Bill
import com.daftech.airkita.data.entity.Customer
import com.daftech.airkita.data.entity.KasTransaction
import com.daftech.airkita.data.entity.Setting
import com.daftech.airkita.engine.TariffSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class Repository(private val db: AppDatabase) {

    // ---------- Pelanggan ----------
    fun customers(): Flow<List<Customer>> = db.customerDao().all()
    fun searchCustomers(q: String): Flow<List<Customer>> = db.customerDao().search(q)
    suspend fun getCustomer(id: Long): Customer? = db.customerDao().byId(id)
    suspend fun addCustomer(c: Customer): Long = db.customerDao().insert(c)
    suspend fun updateCustomer(c: Customer) = db.customerDao().update(c)
    suspend fun updateCustomerLastMeter(id: Long, meter: Int) = db.customerDao().updateLastMeter(id, meter)
    fun countActiveCustomers(): Flow<Int> = db.customerDao().countActive()

    // ---------- Tagihan ----------
    fun billsWithCustomer(period: String): Flow<List<BillWithCustomer>> = db.billDao().withCustomer(period)
    suspend fun billsWithCustomerOnce(period: String): List<BillWithCustomer> = db.billDao().withCustomerOnce(period)
    suspend fun getBill(id: Long): Bill? = db.billDao().byId(id)
    suspend fun saveBill(b: Bill): Long = db.billDao().insert(b)
    suspend fun unpaidBills(limit: Int = 50): List<BillWithCustomer> = db.billDao().unpaid(limit)
    suspend fun billSums(period: String): Pair<Long, Long> =
        db.billDao().sumTotal(period) to db.billDao().sumPaid(period)
    suspend fun countUnpaid(period: String): Int = db.billDao().countUnpaid(period)

    /** Tandai lunas + catat pemasukan kas otomatis. */
    suspend fun markBillPaid(billId: Long, periodLabel: String) {
        val bill = db.billDao().byId(billId) ?: return
        if (bill.paid) return
        db.billDao().markPaid(billId, System.currentTimeMillis())
        val customer = db.customerDao().byId(bill.customerId)
        db.kasDao().insert(
            KasTransaction(
                date = System.currentTimeMillis(),
                kind = "IN",
                category = "Iuran air",
                note = "Pembayaran ${customer?.name ?: "?"} — $periodLabel",
                amount = bill.total,
                auto = true
            )
        )
    }

    // ---------- Kas ----------
    fun kasForPeriod(period: String): Flow<List<KasTransaction>> = db.kasDao().forPeriod(period)
    suspend fun kasSums(period: String): Pair<Long, Long> =
        db.kasDao().sumIn(period) to db.kasDao().sumOut(period)
    suspend fun kasBalance(): Long {
        val opening = getSetting(PrefKeys.KAS_OPENING, "0").toLongOrNull() ?: 0L
        return opening + db.kasDao().balanceDelta()
    }
    suspend fun addKas(tx: KasTransaction) = db.kasDao().insert(tx)

    // ---------- Pengaturan ----------
    fun settingFlow(key: String, default: String): Flow<String> =
        db.settingDao().get(key).map { it ?: default }

    suspend fun getSetting(key: String, default: String = ""): String =
        db.settingDao().getOnce(key) ?: default

    suspend fun setSetting(key: String, value: String) =
        db.settingDao().upsert(Setting(key, value))

    fun tariffFlow(): Flow<TariffSettings> = combine(
        settingFlow(PrefKeys.BLOCK1_LIMIT, "10"),
        settingFlow(PrefKeys.BLOCK1_RATE, "2000"),
        settingFlow(PrefKeys.BLOCK2_LIMIT, "20"),
        settingFlow(PrefKeys.BLOCK2_RATE, "2500"),
        settingFlow(PrefKeys.BLOCK3_RATE, "3000"),
        settingFlow(PrefKeys.ABONEMEN, "5000"),
        settingFlow(PrefKeys.FINE_PERCENT, "2")
    ) { v ->
        TariffSettings(
            block1Limit = v[0].toIntOrNull() ?: 10,
            block1Rate = v[1].toLongOrNull() ?: 2000,
            block2Limit = v[2].toIntOrNull() ?: 20,
            block2Rate = v[3].toLongOrNull() ?: 2500,
            block3Rate = v[4].toLongOrNull() ?: 3000,
            abonemen = v[5].toLongOrNull() ?: 5000,
            finePercent = v[6].toDoubleOrNull() ?: 2.0
        )
    }

    suspend fun getTariff(): TariffSettings = TariffSettings(
        block1Limit = getSetting(PrefKeys.BLOCK1_LIMIT, "10").toIntOrNull() ?: 10,
        block1Rate = getSetting(PrefKeys.BLOCK1_RATE, "2000").toLongOrNull() ?: 2000,
        block2Limit = getSetting(PrefKeys.BLOCK2_LIMIT, "20").toIntOrNull() ?: 20,
        block2Rate = getSetting(PrefKeys.BLOCK2_RATE, "2500").toLongOrNull() ?: 2500,
        block3Rate = getSetting(PrefKeys.BLOCK3_RATE, "3000").toLongOrNull() ?: 3000,
        abonemen = getSetting(PrefKeys.ABONEMEN, "5000").toLongOrNull() ?: 5000,
        finePercent = getSetting(PrefKeys.FINE_PERCENT, "2").toDoubleOrNull() ?: 2.0
    )

    suspend fun saveTariff(t: TariffSettings) {
        setSetting(PrefKeys.BLOCK1_LIMIT, t.block1Limit.toString())
        setSetting(PrefKeys.BLOCK1_RATE, t.block1Rate.toString())
        setSetting(PrefKeys.BLOCK2_LIMIT, t.block2Limit.toString())
        setSetting(PrefKeys.BLOCK2_RATE, t.block2Rate.toString())
        setSetting(PrefKeys.BLOCK3_RATE, t.block3Rate.toString())
        setSetting(PrefKeys.ABONEMEN, t.abonemen.toString())
        setSetting(PrefKeys.FINE_PERCENT, t.finePercent.toString())
    }

    // ---------- Backup / Restore (JSON) ----------
    suspend fun exportJson(): String {
        val root = JSONObject()
        root.put("app", "air-kita")
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        val customers = JSONArray()
        db.customerDao().allOnce().forEach { c ->
            customers.put(JSONObject().put("id", c.id).put("name", c.name)
                .put("meterNo", c.meterNo).put("address", c.address)
                .put("lastMeter", c.lastMeter).put("active", c.active))
        }
        val bills = JSONArray()
        db.billDao().allOnce().forEach { b ->
            bills.put(JSONObject().put("id", b.id).put("customerId", b.customerId)
                .put("period", b.period).put("meterStart", b.meterStart).put("meterEnd", b.meterEnd)
                .put("usageM3", b.usageM3).put("block1m3", b.block1m3).put("block2m3", b.block2m3)
                .put("block3m3", b.block3m3).put("abonemen", b.abonemen).put("fine", b.fine)
                .put("total", b.total).put("paid", b.paid).put("paidAt", b.paidAt ?: JSONObject.NULL))
        }
        val kas = JSONArray()
        db.kasDao().allOnce().forEach { k ->
            kas.put(JSONObject().put("id", k.id).put("date", k.date).put("kind", k.kind)
                .put("category", k.category).put("note", k.note)
                .put("amount", k.amount).put("auto", k.auto))
        }
        val settings = JSONArray()
        db.settingDao().allOnce().forEach { s ->
            settings.put(JSONObject().put("key", s.key).put("value", s.value))
        }
        root.put("customers", customers)
        root.put("bills", bills)
        root.put("kas", kas)
        root.put("settings", settings)
        return root.toString()
    }

    suspend fun importJson(json: String) {
        val root = JSONObject(json)
        require(root.optString("app") == "air-kita") { "File bukan backup AIR KITA" }
        db.customerDao().clear()
        db.billDao().clear()
        db.kasDao().clear()
        db.settingDao().clear()
        val customers = mutableListOf<Customer>()
        val jc = root.getJSONArray("customers")
        for (i in 0 until jc.length()) {
            val o = jc.getJSONObject(i)
            customers.add(Customer(o.getLong("id"), o.getString("name"), o.getString("meterNo"),
                o.optString("address", ""), o.optInt("lastMeter", 0), o.optBoolean("active", true)))
        }
        val bills = mutableListOf<Bill>()
        val jb = root.getJSONArray("bills")
        for (i in 0 until jb.length()) {
            val o = jb.getJSONObject(i)
            bills.add(Bill(o.getLong("id"), o.getLong("customerId"), o.getString("period"),
                o.getInt("meterStart"), o.getInt("meterEnd"), o.getInt("usageM3"),
                o.getInt("block1m3"), o.getInt("block2m3"), o.getInt("block3m3"),
                o.getLong("abonemen"), o.getLong("fine"), o.getLong("total"),
                o.getBoolean("paid"), if (o.isNull("paidAt")) null else o.getLong("paidAt")))
        }
        val kas = mutableListOf<KasTransaction>()
        val jk = root.getJSONArray("kas")
        for (i in 0 until jk.length()) {
            val o = jk.getJSONObject(i)
            kas.add(KasTransaction(o.getLong("id"), o.getLong("date"), o.getString("kind"),
                o.getString("category"), o.getString("note"), o.getLong("amount"), o.getBoolean("auto")))
        }
        db.customerDao().insertAll(customers)
        db.billDao().insertAll(bills)
        db.kasDao().insertAll(kas)
        val js = root.getJSONArray("settings")
        for (i in 0 until js.length()) {
            val o = js.getJSONObject(i)
            db.settingDao().upsert(Setting(o.getString("key"), o.getString("value")))
        }
    }
}
