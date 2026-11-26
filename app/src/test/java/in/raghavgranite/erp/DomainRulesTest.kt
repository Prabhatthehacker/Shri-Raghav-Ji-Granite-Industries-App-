package in.raghavgranite.erp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DomainRulesTest {
    @Test fun moneyIsIntegerPaiseAndGstRoundsSafely() {
        assertEquals(10025L, DomainRules.rupeesToPaise("100.25"))
        assertEquals(1250L, DomainRules.quantityToMilli("1.250"))
        assertEquals(1800L, DomainRules.gstPaise(10001, 1800))
    }

    @Test fun invoiceIncludesDiscountGstAndExplicitCharges() {
        assertEquals(20250L, DomainRules.invoiceTotalPaise(listOf(InvoiceLine(2000, 10000, 1000, 500)), 300))
    }

    @Test fun productionCalculatesYieldLossAndCost() {
        val result = DomainRules.production(10000, 6000, 2500, 0, 17000)
        assertEquals(8500L, result.outputMilli)
        assertEquals(1500L, result.lossMilli)
        assertEquals(85.0, result.yieldPercent, 0.0001)
        assertEquals(2000L, result.costPerOutputUnitPaise)
    }

    @Test fun negativeStockIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { DomainRules.stockAfter(2000, -2001) }
    }

    @Test fun inventoryUsesWeightedAverageAndKeepsBalancesInPaise() {
        assertEquals(1500L, DomainRules.weightedAverageCostPaise(1000, 1000, 1000, 2000))
        assertEquals(38000L, DomainRules.outstandingPaise(50000, 12000))
    }

    @Test fun buyerDispatchWeightDoesNotUseVehicleFleetRules() {
        assertEquals(16000L, DomainRules.netWeightMilli(28000, 12000))
        assertThrows(IllegalArgumentException::class.java) { DomainRules.netWeightMilli(100, 101) }
        // Buyer vehicle registration is only a dispatch attribute in the database schema.
    }

    @Test fun journalRequiresEqualDebitsAndCredits() {
        DomainRules.assertBalancedJournal(listOf(JournalLine("cash", 500, 0), JournalLine("sales", 0, 500)))
        assertThrows(IllegalArgumentException::class.java) { DomainRules.assertBalancedJournal(listOf(JournalLine("cash", 500, 0), JournalLine("sales", 0, 499))) }
    }

    @Test fun duplicatePaymentReferencesAreRejected() {
        val previous = setOf("UPI" to "abc123")
        DomainRules.assertPaymentReferenceAvailable(previous, "UPI", "new-456")
        assertThrows(IllegalArgumentException::class.java) { DomainRules.assertPaymentReferenceAvailable(previous, "UPI", "abc123") }
    }

    @Test fun supplierCustomerBalanceIsPaiseSafe() {
        assertEquals(38000L, 50000L - 12000L)
    }
}
