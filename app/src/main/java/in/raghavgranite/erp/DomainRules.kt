package in.raghavgranite.erp

import java.math.BigDecimal
import java.math.RoundingMode

object DomainRules {
    fun rupeesToPaise(rupees: String): Long = BigDecimal(rupees).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
    fun quantityToMilli(quantity: String): Long = BigDecimal(quantity).movePointRight(3).setScale(0, RoundingMode.HALF_UP).longValueExact()

    fun gstPaise(basePaise: Long, rateBasisPoints: Int): Long {
        require(basePaise >= 0 && rateBasisPoints in 0..10_000)
        return BigDecimal.valueOf(basePaise).multiply(BigDecimal.valueOf(rateBasisPoints.toLong()))
            .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP).longValueExact()
    }

    fun invoiceTotalPaise(lines: List<InvoiceLine>, extraChargesPaise: Long = 0): Long {
        require(extraChargesPaise >= 0)
        return lines.sumOf { line ->
            require(line.quantityMilli >= 0 && line.ratePaisePerUnit >= 0 && line.discountPaise >= 0)
            val gross = BigDecimal.valueOf(line.quantityMilli).multiply(BigDecimal.valueOf(line.ratePaisePerUnit))
                .divide(BigDecimal.valueOf(1000), 0, RoundingMode.HALF_UP).longValueExact()
            require(line.discountPaise <= gross)
            val taxable = gross - line.discountPaise
            Math.addExact(taxable, gstPaise(taxable, line.gstBasisPoints))
        }.let { Math.addExact(it, extraChargesPaise) }
    }

    fun production(rawInputMilli: Long, crushedMilli: Long, dustMilli: Long, graniteMilli: Long, costPaise: Long): ProductionMetrics {
        require(rawInputMilli > 0 && crushedMilli >= 0 && dustMilli >= 0 && graniteMilli >= 0 && costPaise >= 0)
        val output = Math.addExact(Math.addExact(crushedMilli, dustMilli), graniteMilli)
        require(output <= rawInputMilli) { "Output cannot exceed raw input" }
        return ProductionMetrics(output, rawInputMilli - output,
            if (output == 0L) 0.0 else output.toDouble() * 100.0 / rawInputMilli,
            if (output == 0L) 0L else BigDecimal.valueOf(costPaise).multiply(BigDecimal.valueOf(1000))
                .divide(BigDecimal.valueOf(output), 0, RoundingMode.HALF_UP).longValueExact())
    }

    fun stockAfter(currentMilli: Long, deltaMilli: Long): Long {
        require(currentMilli >= 0 && deltaMilli != 0L)
        val result = Math.addExact(currentMilli, deltaMilli)
        require(result >= 0) { "This movement would make stock negative" }
        return result
    }

    fun weightedAverageCostPaise(oldQuantityMilli: Long, oldCostPaise: Long, inwardQuantityMilli: Long, inwardCostPaise: Long): Long {
        require(oldQuantityMilli >= 0 && oldCostPaise >= 0 && inwardQuantityMilli > 0 && inwardCostPaise >= 0)
        val totalQuantity = Math.addExact(oldQuantityMilli, inwardQuantityMilli)
        return BigDecimal.valueOf(oldQuantityMilli).multiply(BigDecimal.valueOf(oldCostPaise))
            .add(BigDecimal.valueOf(inwardQuantityMilli).multiply(BigDecimal.valueOf(inwardCostPaise)))
            .divide(BigDecimal.valueOf(totalQuantity), 0, RoundingMode.HALF_UP).longValueExact()
    }

    fun outstandingPaise(invoicePaise: Long, allocatedPaymentsPaise: Long): Long {
        require(invoicePaise >= 0 && allocatedPaymentsPaise >= 0)
        return Math.subtractExact(invoicePaise, allocatedPaymentsPaise)
    }

    fun netWeightMilli(grossMilli: Long, tareMilli: Long): Long {
        require(grossMilli >= 0 && tareMilli >= 0 && tareMilli <= grossMilli)
        return grossMilli - tareMilli
    }

    fun assertBalancedJournal(lines: List<JournalLine>) {
        require(lines.size >= 2)
        require(lines.all { it.debitPaise >= 0 && it.creditPaise >= 0 && !(it.debitPaise > 0 && it.creditPaise > 0) })
        val debit = lines.fold(0L) { total, line -> Math.addExact(total, line.debitPaise) }
        val credit = lines.fold(0L) { total, line -> Math.addExact(total, line.creditPaise) }
        require(debit > 0 && debit == credit) { "Journal entry is not balanced" }
    }

    fun assertPaymentReferenceAvailable(existing: Set<Pair<String, String>>, method: String, reference: String?) {
        if (!reference.isNullOrBlank()) require((method to reference.trim()) !in existing) { "Duplicate payment reference" }
    }
}

data class InvoiceLine(val quantityMilli: Long, val ratePaisePerUnit: Long, val discountPaise: Long, val gstBasisPoints: Int)
data class ProductionMetrics(val outputMilli: Long, val lossMilli: Long, val yieldPercent: Double, val costPerOutputUnitPaise: Long)
data class JournalLine(val account: String, val debitPaise: Long, val creditPaise: Long)
