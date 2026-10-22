package com.example.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

@JvmInline
value class Money(val paise: Long) : Comparable<Money> {
    operator fun plus(other: Money): Money = Money(paise + other.paise)
    operator fun minus(other: Money): Money = Money(paise - other.paise)
    operator fun unaryMinus(): Money = Money(-paise)

    operator fun times(qtyMilli: Long): Money {
        return MoneyMath.calculateLineTotal(this, qtyMilli)
    }

    override fun compareTo(other: Money): Int = paise.compareTo(other.paise)

    fun toRupeesDouble(): Double {
        return BigDecimal(paise)
            .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
            .toDouble()
    }

    fun format(currencySymbol: String = "₹"): String {
        return MoneyMath.formatMoney(this, currencySymbol)
    }

    companion object {
        val ZERO = Money(0L)

        fun fromRupeesDouble(rupees: Double): Money {
            return MoneyMath.fromRupeesDouble(rupees)
        }

        fun fromRupeesString(input: String): Money {
            return MoneyMath.parseMoneyFromUserInput(input)
        }
    }
}

object MoneyMath {

    private val indianFormatter: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }

    fun fromRupeesDouble(rupees: Double): Money {
        if (rupees.isNaN() || rupees.isInfinite()) return Money.ZERO
        val bd = BigDecimal(rupees.toString()).setScale(2, RoundingMode.HALF_UP)
        val paise = bd.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong()
        return Money(paise)
    }

    fun parseMoneyFromUserInput(input: String): Money {
        val clean = input.replace("₹", "").replace("$", "").replace(",", "").trim()
        if (clean.isBlank()) return Money.ZERO
        return try {
            val bd = BigDecimal(clean).setScale(2, RoundingMode.HALF_UP)
            val paise = bd.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong()
            Money(paise)
        } catch (e: Exception) {
            Money.ZERO
        }
    }

    fun parseQuantityMilli(input: String): Long {
        val clean = input.replace(",", "").trim()
        if (clean.isBlank()) return 1000L // 1 unit default
        return try {
            val bd = BigDecimal(clean).setScale(3, RoundingMode.HALF_UP)
            bd.multiply(BigDecimal(1000)).setScale(0, RoundingMode.HALF_UP).toLong()
        } catch (e: Exception) {
            1000L
        }
    }

    fun formatQuantityMilli(qtyMilli: Long): String {
        if (qtyMilli % 1000L == 0L) {
            return (qtyMilli / 1000L).toString()
        }
        val bd = BigDecimal(qtyMilli).divide(BigDecimal(1000), 3, RoundingMode.HALF_UP).stripTrailingZeros()
        return bd.toPlainString()
    }

    /**
     * Line total = price.paise * qtyMilli / 1000, rounded HALF_UP
     */
    fun calculateLineTotal(unitPrice: Money, qtyMilli: Long): Money {
        val bdPrice = BigDecimal(unitPrice.paise)
        val bdQty = BigDecimal(qtyMilli)
        val bdTotal = bdPrice.multiply(bdQty).divide(BigDecimal(1000), 0, RoundingMode.HALF_UP)
        return Money(bdTotal.toLong())
    }

    /**
     * Percentage calculation in basis points (18% = 1800, 0.25% = 25).
     * result = amount.paise * basisPoints / 10000, rounded HALF_UP
     */
    fun calculatePercentAmount(amount: Money, basisPoints: Int): Money {
        val bdAmount = BigDecimal(amount.paise)
        val bdBps = BigDecimal(basisPoints)
        val bdResult = bdAmount.multiply(bdBps).divide(BigDecimal(10000), 0, RoundingMode.HALF_UP)
        return Money(bdResult.toLong())
    }

    /**
     * CGST and SGST exact 50/50 split that guarantees CGST + SGST == totalTax.
     * Odd paisa goes to CGST.
     */
    fun splitGstHalf(totalTax: Money): Pair<Money, Money> {
        val half = totalTax.paise / 2L
        val remainder = totalTax.paise % 2L
        val cgst = Money(half + remainder)
        val sgst = Money(half)
        return Pair(cgst, sgst)
    }

    /**
     * Display formatting:
     * - Show "₹565" when paise % 100 == 0
     * - Show "₹82.50" when fractional (exact 2 decimal places)
     * - Never show artifacts like "565.0" or "82.5"
     */
    fun formatMoney(money: Money, currencySymbol: String = "₹"): String {
        val paise = money.paise
        val isNegative = paise < 0
        val absPaise = Math.abs(paise)
        val rupees = absPaise / 100L
        val cents = absPaise % 100L

        val prefix = if (isNegative) "-" else ""
        val formattedRupees = synchronized(indianFormatter) {
            indianFormatter.format(rupees)
        }

        return if (cents == 0L) {
            "$prefix$currencySymbol$formattedRupees"
        } else {
            val centsStr = String.format(Locale.US, "%02d", cents)
            "$prefix$currencySymbol$formattedRupees.$centsStr"
        }
    }

    /**
     * Format clean numeric string without currency symbol for inputs/tables
     */
    fun formatMoneyPlain(money: Money): String {
        val paise = money.paise
        val isNegative = paise < 0
        val absPaise = Math.abs(paise)
        val rupees = absPaise / 100L
        val cents = absPaise % 100L
        val prefix = if (isNegative) "-" else ""
        return if (cents == 0L) {
            "$prefix$rupees"
        } else {
            val centsStr = String.format(Locale.US, "%02d", cents)
            "$prefix$rupees.$centsStr"
        }
    }

    /**
     * Dealer Discount & MRP calculations using integer basis points
     */
    fun calculateDiscountedPrice(mrp: Money, discountPercentBps: Int): Money {
        val discount = calculatePercentAmount(mrp, discountPercentBps)
        val finalPaise = Math.max(0L, mrp.paise - discount.paise)
        return Money(finalPaise)
    }

    fun calculateMarginBps(buyRate: Money, sellPrice: Money): Int {
        if (buyRate.paise <= 0L || sellPrice.paise <= 0L) return 0
        val profitPaise = sellPrice.paise - buyRate.paise
        val bdProfit = BigDecimal(profitPaise)
        val bdBuy = BigDecimal(buyRate.paise)
        return bdProfit.multiply(BigDecimal(10000)).divide(bdBuy, 0, RoundingMode.HALF_UP).toInt()
    }
}
