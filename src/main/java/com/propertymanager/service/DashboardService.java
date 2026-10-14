package com.propertymanager.service;

import com.propertymanager.database.Database;
import com.propertymanager.model.Payment;
import com.propertymanager.model.Unit;
import com.propertymanager.repository.PaymentRepository;
import com.propertymanager.repository.PropertyRepository;
import com.propertymanager.repository.UnitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Aggregates everything the dashboard needs into a single DashboardData object.
 *
 * All queries target the current month by default. The service is efficient:
 * one COUNT per stat, one SUM per financial figure, one list query per panel.
 */
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);

    private final PropertyRepository propertyRepo = new PropertyRepository();
    private final UnitRepository unitRepo = new UnitRepository();
    private final PaymentRepository paymentRepo = new PaymentRepository();
    private final BalanceService balanceService = new BalanceService();

    /* =================================================================== */

    /** Builds the whole dashboard payload for the current month. */
    public DashboardData load() {
        YearMonth month = YearMonth.now();

        DashboardData d = new DashboardData();
        d.month = month;
        d.monthLabel = month.getMonth().getDisplayName(
                java.time.format.TextStyle.FULL, Locale.ENGLISH)
            + " " + month.getYear();

        // ---- Primary stats ----
        d.totalProperties = propertyRepo.count();
        d.totalUnits      = unitRepo.count();
        d.occupiedUnits   = unitRepo.countByStatus(Unit.STATUS_OCCUPIED);
        d.vacantUnits     = unitRepo.countByStatus(Unit.STATUS_VACANT);
        d.maintenanceUnits= unitRepo.countByStatus(Unit.STATUS_MAINTENANCE);

        // ---- Occupancy percentage ----
        d.occupancyPct = d.totalUnits > 0
            ? (d.occupiedUnits * 100.0 / d.totalUnits)
            : 0.0;

        // ---- Financials (for the current month) ----
        d.expectedRent = unitRepo.sumMonthlyRentByStatus(Unit.STATUS_OCCUPIED);
        d.collected    = paymentRepo.sumForPeriod(month.getYear(), month.getMonthValue());
        d.outstanding  = d.expectedRent.subtract(d.collected);
        if (d.outstanding.signum() < 0) d.outstanding = BigDecimal.ZERO;

        // ---- Collection percentage ----
        d.collectionPct = d.expectedRent.signum() > 0
            ? d.collected.doubleValue() * 100.0 / d.expectedRent.doubleValue()
            : 0.0;
        if (d.collectionPct > 100) d.collectionPct = 100;

        // ---- Recent payments (last 5) ----
        d.recentPayments = loadRecentPayments(5);

        // ---- Outstanding tenants (top 5 debtors) ----
        d.outstandingTenants = loadTopDebtors(month, 5);

        return d;
    }

    /* =================================================================== */
    /*  RECENT PAYMENTS                                                    */
    /* =================================================================== */

    private List<RecentPayment> loadRecentPayments(int limit) {
        List<RecentPayment> list = new ArrayList<>();

        String sql = """
            SELECT p.receipt_number, p.payment_date, p.amount, p.method,
                   t.full_name,
                   u.house_number
            FROM payments p
            JOIN tenants t ON t.id = p.tenant_id
            LEFT JOIN tenancies te ON te.id = p.tenancy_id
            LEFT JOIN units u ON u.id = te.unit_id
            ORDER BY p.created_at DESC
            LIMIT ?
            """;

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RecentPayment r = new RecentPayment();
                    r.receiptNumber = rs.getString("receipt_number");
                    r.tenantName    = rs.getString("full_name");
                    r.room          = rs.getString("house_number");
                    r.amount        = rs.getBigDecimal("amount");
                    r.method        = methodDisplay(rs.getString("method"));

                    java.sql.Date pd = rs.getDate("payment_date");
                    r.date = pd != null ? pd.toLocalDate() : null;

                    list.add(r);
                }
            }
        } catch (SQLException e) {
            log.error("loadRecentPayments failed", e);
        }
        return list;
    }

    /* =================================================================== */
    /*  OUTSTANDING TENANTS (TOP DEBTORS)                                  */
    /* =================================================================== */

    private List<OutstandingTenant> loadTopDebtors(YearMonth month, int limit) {
        List<OutstandingTenant> list = new ArrayList<>();

        try {
            var rows = balanceService.arrearsForMonth(month);
            for (int i = 0; i < Math.min(limit, rows.size()); i++) {
                var r = rows.get(i);
                OutstandingTenant o = new OutstandingTenant();
                o.tenantName    = r.tenantName;
                o.tenantNumber  = r.tenantNumber;
                o.phone         = r.phone;
                o.room          = r.room;
                o.property      = r.property;
                o.expected      = r.expected;
                o.paid          = r.paid;
                o.outstanding   = r.outstanding;
                list.add(o);
            }
        } catch (RuntimeException e) {
            log.error("loadTopDebtors failed", e);
        }
        return list;
    }

    /* =================================================================== */
    /*  HELPERS                                                            */
    /* =================================================================== */

    private String methodDisplay(String method) {
        if (method == null) return "";
        return switch (method) {
            case "CASH"  -> "Cash";
            case "MPESA" -> "M-Pesa";
            case "BANK"  -> "Bank";
            default      -> "Other";
        };
    }

    /* =================================================================== */
    /*  DATA MODEL                                                         */
    /* =================================================================== */

    /** Everything the dashboard displays, in one object. */
    public static class DashboardData {
        // Period
        public YearMonth month;
        public String monthLabel;

        // Primary stats
        public long totalProperties;
        public long totalUnits;
        public long occupiedUnits;
        public long vacantUnits;
        public long maintenanceUnits;
        public double occupancyPct;

        // Financials
        public BigDecimal expectedRent = BigDecimal.ZERO;
        public BigDecimal collected    = BigDecimal.ZERO;
        public BigDecimal outstanding  = BigDecimal.ZERO;
        public double collectionPct;

        // Lists
        public List<RecentPayment> recentPayments = new ArrayList<>();
        public List<OutstandingTenant> outstandingTenants = new ArrayList<>();
    }

    public static class RecentPayment {
        public String receiptNumber;
        public String tenantName;
        public String room;
        public BigDecimal amount;
        public String method;
        public LocalDate date;

        public String getDateLabel() {
            return date != null ? date.format(DATE_FMT) : "—";
        }
    }

    public static class OutstandingTenant {
        public String tenantNumber;
        public String tenantName;
        public String phone;
        public String room;
        public String property;
        public BigDecimal expected;
        public BigDecimal paid;
        public BigDecimal outstanding;
    }
}
