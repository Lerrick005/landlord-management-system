package com.propertymanager.service;

import com.propertymanager.model.Tenancy;
import com.propertymanager.model.Tenant;
import com.propertymanager.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Computes tenant balances and arrears across the whole portfolio.
 *
 * Called from:
 *   - The dashboard (total outstanding for the current month)
 *   - The arrears / outstanding-rent screen
 *   - The tenant profile card
 */
public class BalanceService {

    private static final Logger log = LoggerFactory.getLogger(BalanceService.class);

    private final PaymentRepository paymentRepo;
    private final TenancyService tenancyService;
    private final TenantService tenantService;

    public BalanceService() {
        this.paymentRepo = new PaymentRepository();
        this.tenancyService = new TenancyService();
        this.tenantService = new TenantService();
    }

    /* =================================================================== */
    /*  SINGLE TENANT                                                      */
    /* =================================================================== */

    /**
     * Returns a tenant's balance for a specific month.
     * The expected rent comes from their ACTIVE tenancy, if any.
     * If the tenant has no active tenancy, everything is zero and status is N/A.
     */
    public BalanceRow balanceFor(Tenant tenant, YearMonth month) {
        if (tenant == null) return BalanceRow.empty();

        BigDecimal expected = BigDecimal.ZERO;
        String room = "—";
        String property = "—";

        var activeOpt = tenancyService.findActiveByTenant(tenant.getId());
        if (activeOpt.isPresent()) {
            Tenancy t = activeOpt.get();
            expected = t.getMonthlyRent() != null ? t.getMonthlyRent() : BigDecimal.ZERO;
            room = t.getUnitLabel();
            property = t.getPropertyLabel();
        }

        BigDecimal paid = paymentRepo.sumForTenantAndPeriod(
            tenant.getId(), month.getYear(), month.getMonthValue());

        BigDecimal outstanding = expected.subtract(paid);
        if (outstanding.signum() < 0) outstanding = BigDecimal.ZERO;

        String status = classifyStatus(expected, paid);

        return new BalanceRow(
            tenant.getId(),
            tenant.getTenantNumber(),
            tenant.getFullName(),
            tenant.getPhone(),
            property,
            room,
            expected,
            paid,
            outstanding,
            status,
            month
        );
    }

    /* =================================================================== */
    /*  ALL TENANTS FOR A MONTH                                            */
    /* =================================================================== */

    /**
     * Computes a balance row for every ACTIVE tenant, for the given month.
     * Tenants with no active tenancy are included with zero expected/paid.
     */
    public List<BalanceRow> balancesForMonth(YearMonth month) {
        List<BalanceRow> rows = new ArrayList<>();
        for (Tenant t : tenantService.listActive()) {
            rows.add(balanceFor(t, month));
        }
        return rows;
    }

    /**
     * Only tenants with an outstanding balance for the given month.
     * Sorted by outstanding descending (biggest debtors first).
     */
    public List<BalanceRow> arrearsForMonth(YearMonth month) {
        List<BalanceRow> all = balancesForMonth(month);
        List<BalanceRow> arrears = new ArrayList<>();
        for (BalanceRow r : all) {
            if (r.outstanding.signum() > 0) arrears.add(r);
        }
        arrears.sort(Comparator.comparing((BalanceRow r) -> r.outstanding).reversed());
        return arrears;
    }

    /* =================================================================== */
    /*  TOTALS                                                             */
    /* =================================================================== */

    /** Sum of expected rent across all tenants with an active tenancy. */
    public BigDecimal totalExpectedForMonth(YearMonth month) {
        BigDecimal total = BigDecimal.ZERO;
        for (BalanceRow r : balancesForMonth(month)) {
            total = total.add(r.expected);
        }
        return total;
    }

    /** Total paid across all tenants for the given month. */
    public BigDecimal totalPaidForMonth(YearMonth month) {
        return paymentRepo.sumForPeriod(month.getYear(), month.getMonthValue());
    }

    /** Total outstanding across all tenants for the given month. */
    public BigDecimal totalOutstandingForMonth(YearMonth month) {
        return totalExpectedForMonth(month).subtract(totalPaidForMonth(month))
            .max(BigDecimal.ZERO);
    }

    /* =================================================================== */
    /*  MULTI-MONTH HISTORY                                                */
    /* =================================================================== */

    /**
     * Balance for a tenant for every month from move-in to the current month.
     * Used by the tenant profile's payment history panel.
     */
    public List<BalanceRow> historyFor(Tenant tenant) {
        List<BalanceRow> history = new ArrayList<>();
        if (tenant == null) return history;

        var activeOpt = tenancyService.findActiveByTenant(tenant.getId());
        if (activeOpt.isEmpty()) return history;

        Tenancy t = activeOpt.get();
        if (t.getMoveInDate() == null) return history;

        YearMonth start = YearMonth.from(t.getMoveInDate());
        YearMonth end = YearMonth.now();
        if (start.isAfter(end)) start = end;

        YearMonth cursor = start;
        while (!cursor.isAfter(end)) {
            history.add(balanceFor(tenant, cursor));
            cursor = cursor.plusMonths(1);
        }
        return history;
    }

    /* =================================================================== */
    /*  HELPERS                                                            */
    /* =================================================================== */

    private String classifyStatus(BigDecimal expected, BigDecimal paid) {
        if (expected.signum() == 0 && paid.signum() == 0) return "N/A";
        if (expected.signum() == 0) return "N/A";
        if (paid.signum() == 0) return "UNPAID";
        int cmp = paid.compareTo(expected);
        if (cmp < 0) return "PARTIAL";
        if (cmp == 0) return "PAID";
        return "OVERPAID";
    }

    /* =================================================================== */
    /*  ROW TYPE                                                           */
    /* =================================================================== */

    /** One row of balance data — a snapshot for a tenant for one month. */
    public static class BalanceRow {
        public final long tenantId;
        public final String tenantNumber;
        public final String tenantName;
        public final String phone;
        public final String property;
        public final String room;

        public final BigDecimal expected;
        public final BigDecimal paid;
        public final BigDecimal outstanding;
        public final String status;         // PAID / PARTIAL / UNPAID / OVERPAID / N/A
        public final YearMonth month;

        public BalanceRow(long tenantId, String tenantNumber, String tenantName, String phone,
                          String property, String room,
                          BigDecimal expected, BigDecimal paid, BigDecimal outstanding,
                          String status, YearMonth month) {
            this.tenantId = tenantId;
            this.tenantNumber = tenantNumber;
            this.tenantName = tenantName;
            this.phone = phone;
            this.property = property;
            this.room = room;
            this.expected = expected;
            this.paid = paid;
            this.outstanding = outstanding;
            this.status = status;
            this.month = month;
        }

        public String getPeriodLabel() {
            return month.getMonth().getDisplayName(
                java.time.format.TextStyle.FULL,
                java.util.Locale.ENGLISH) + " " + month.getYear();
        }

        public static BalanceRow empty() {
            return new BalanceRow(0, "", "", "", "—", "—",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "N/A", YearMonth.now());
        }
    }
}
