package com.propertymanager.reports;

import com.propertymanager.database.Database;
import com.propertymanager.model.Payment;
import com.propertymanager.model.Property;
import com.propertymanager.model.Tenant;
import com.propertymanager.model.Tenancy;
import com.propertymanager.model.Unit;
import com.propertymanager.repository.PaymentRepository;
import com.propertymanager.repository.PropertyRepository;
import com.propertymanager.repository.TenantRepository;
import com.propertymanager.repository.UnitRepository;
import com.propertymanager.service.BalanceService;
import com.propertymanager.service.TenancyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generates the data behind each report.
 *
 * Every report is returned as a generic {@link ReportData} — a title,
 * a set of column headers, and a list of rows. The viewer renders it
 * without knowing anything report-specific.
 *
 * This makes adding a new report a matter of:
 *   1. Adding an enum in ReportType
 *   2. Adding a build... method here
 */
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final TenantRepository tenantRepo = new TenantRepository();
    private final PropertyRepository propertyRepo = new PropertyRepository();
    private final UnitRepository unitRepo = new UnitRepository();
    private final PaymentRepository paymentRepo = new PaymentRepository();
    private final TenancyService tenancyService = new TenancyService();
    private final BalanceService balanceService = new BalanceService();

    /* =================================================================== */

    /**
     * Builds the report data for a given type and parameters.
     *
     * @param type       which report
     * @param params     filters — may include fromDate, toDate, month, propertyId
     */
    public ReportData build(ReportType type, ReportParams params) {
        if (type == null) return ReportData.empty();
        if (params == null) params = new ReportParams();

        return switch (type) {
            case TENANTS          -> buildTenants(params);
            case PROPERTIES       -> buildProperties();
            case OCCUPANCY        -> buildOccupancy();
            case RENT_COLLECTION  -> buildRentCollection(params);
            case ARREARS          -> buildArrears(params);
            case PAYMENTS         -> buildPayments(params);
        };
    }

    /* =================================================================== */
    /*  TENANT REPORT                                                      */
    /* =================================================================== */

    private ReportData buildTenants(ReportParams params) {
        List<String> cols = List.of(
            "TENANT NO.", "FULL NAME", "PHONE", "NATIONAL ID", "PROPERTY", "ROOM", "STATUS");
        List<List<String>> rows = new ArrayList<>();

        List<Tenant> tenants = tenantRepo.findAll();
        for (Tenant t : tenants) {
            String property = "—";
            String room = "—";

            var active = tenancyService.findActiveByTenant(t.getId());
            if (active.isPresent()) {
                Tenancy ten = active.get();
                room = ten.getUnitLabel();
                property = ten.getPropertyLabel();
            }

            rows.add(List.of(
                nz(t.getTenantNumber()),
                nz(t.getFullName()),
                nz(t.getPhone()),
                nz(t.getNationalId()),
                nz(property),
                nz(room),
                nz(t.getStatus())
            ));
        }

        return new ReportData(
            "Tenant Report",
            "All tenants in the system, with their current room assignment",
            cols,
            rows,
            "",
            LocalDate.now()
        );
    }

    /* =================================================================== */
    /*  PROPERTY REPORT                                                    */
    /* =================================================================== */

    private ReportData buildProperties() {
        List<String> cols = List.of(
            "CODE", "NAME", "LOCATION", "UNITS", "OCCUPIED", "VACANT", "STATUS");
        List<List<String>> rows = new ArrayList<>();

        for (Property p : propertyRepo.findAll()) {
            List<Unit> units = unitRepo.findByProperty(p.getId());
            long total = units.size();
            long occupied = units.stream()
                .filter(u -> Unit.STATUS_OCCUPIED.equalsIgnoreCase(u.getStatus()))
                .count();
            long vacant = units.stream()
                .filter(u -> Unit.STATUS_VACANT.equalsIgnoreCase(u.getStatus()))
                .count();

            rows.add(List.of(
                nz(p.getCode()),
                nz(p.getName()),
                nz(p.getLocation()),
                String.valueOf(total),
                String.valueOf(occupied),
                String.valueOf(vacant),
                nz(p.getStatus())
            ));
        }

        return new ReportData(
            "Property Report",
            "Every property with its unit count and occupancy",
            cols,
            rows,
            "",
            LocalDate.now()
        );
    }

    /* =================================================================== */
    /*  OCCUPANCY REPORT                                                   */
    /* =================================================================== */

    private ReportData buildOccupancy() {
        List<String> cols = List.of(
            "PROPERTY", "TOTAL UNITS", "OCCUPIED", "VACANT", "MAINTENANCE", "OCCUPANCY %");
        List<List<String>> rows = new ArrayList<>();

        long grandTotal = 0;
        long grandOccupied = 0;

        for (Property p : propertyRepo.findAll()) {
            List<Unit> units = unitRepo.findByProperty(p.getId());
            long total = units.size();
            long occupied = units.stream()
                .filter(u -> Unit.STATUS_OCCUPIED.equalsIgnoreCase(u.getStatus())).count();
            long vacant = units.stream()
                .filter(u -> Unit.STATUS_VACANT.equalsIgnoreCase(u.getStatus())).count();
            long maintenance = units.stream()
                .filter(u -> Unit.STATUS_MAINTENANCE.equalsIgnoreCase(u.getStatus())).count();

            String pct = total > 0
                ? String.format("%.0f%%", (occupied * 100.0 / total))
                : "—";

            rows.add(List.of(
                nz(p.getName()),
                String.valueOf(total),
                String.valueOf(occupied),
                String.valueOf(vacant),
                String.valueOf(maintenance),
                pct
            ));

            grandTotal += total;
            grandOccupied += occupied;
        }

        // Add a total row
        if (!rows.isEmpty()) {
            String overallPct = grandTotal > 0
                ? String.format("%.0f%%", (grandOccupied * 100.0 / grandTotal))
                : "—";
            rows.add(List.of(
                "TOTAL",
                String.valueOf(grandTotal),
                String.valueOf(grandOccupied),
                String.valueOf(grandTotal - grandOccupied),
                "—",
                overallPct
            ));
        }

        return new ReportData(
            "Occupancy Report",
            "How full your portfolio is, per property",
            cols,
            rows,
            "",
            LocalDate.now()
        );
    }

    /* =================================================================== */
    /*  RENT COLLECTION REPORT                                             */
    /* =================================================================== */

    private ReportData buildRentCollection(ReportParams params) {
        YearMonth month = params.monthOrDefault();
        List<String> cols = List.of(
            "PROPERTY", "OCCUPIED UNITS", "EXPECTED (KSh)", "COLLECTED (KSh)",
            "OUTSTANDING (KSh)", "COLLECTION %");
        List<List<String>> rows = new ArrayList<>();

        BigDecimal grandExpected = BigDecimal.ZERO;
        BigDecimal grandCollected = BigDecimal.ZERO;

        for (Property p : propertyRepo.findAll()) {
            List<Unit> units = unitRepo.findByProperty(p.getId());
            List<Unit> occupied = units.stream()
                .filter(u -> Unit.STATUS_OCCUPIED.equalsIgnoreCase(u.getStatus())).toList();

            BigDecimal expected = BigDecimal.ZERO;
            for (Unit u : occupied) {
                if (u.getMonthlyRent() != null) expected = expected.add(u.getMonthlyRent());
            }

            // Collected: sum of all payments for tenant+period, restricted to this property
            BigDecimal collected = BigDecimal.ZERO;
            for (Unit u : occupied) {
                var active = tenancyService.findActiveByUnit(u.getId());
                if (active.isPresent()) {
                    long tenantId = active.get().getTenantId();
                    collected = collected.add(paymentRepo.sumForTenantAndPeriod(
                        tenantId, month.getYear(), month.getMonthValue()));
                }
            }

            BigDecimal outstanding = expected.subtract(collected).max(BigDecimal.ZERO);
            String pct = expected.signum() > 0
                ? String.format("%.0f%%", collected.doubleValue() * 100.0 / expected.doubleValue())
                : "—";

            rows.add(List.of(
                nz(p.getName()),
                String.valueOf(occupied.size()),
                formatMoney(expected),
                formatMoney(collected),
                formatMoney(outstanding),
                pct
            ));

            grandExpected = grandExpected.add(expected);
            grandCollected = grandCollected.add(collected);
        }

        if (!rows.isEmpty()) {
            String overallPct = grandExpected.signum() > 0
                ? String.format("%.0f%%",
                    grandCollected.doubleValue() * 100.0 / grandExpected.doubleValue())
                : "—";
            rows.add(List.of(
                "TOTAL",
                "—",
                formatMoney(grandExpected),
                formatMoney(grandCollected),
                formatMoney(grandExpected.subtract(grandCollected).max(BigDecimal.ZERO)),
                overallPct
            ));
        }

        return new ReportData(
            "Rent Collection Report",
            "Expected vs collected for " + month.getMonth() + " " + month.getYear(),
            cols,
            rows,
            "Period: " + month.getMonth() + " " + month.getYear(),
            LocalDate.now()
        );
    }

    /* =================================================================== */
    /*  ARREARS REPORT                                                     */
    /* =================================================================== */

    private ReportData buildArrears(ReportParams params) {
        YearMonth month = params.monthOrDefault();
        List<String> cols = List.of(
            "TENANT NO.", "TENANT", "PHONE", "PROPERTY", "ROOM",
            "EXPECTED (KSh)", "PAID (KSh)", "BALANCE (KSh)");
        List<List<String>> rows = new ArrayList<>();

        List<BalanceService.BalanceRow> arrears = balanceService.arrearsForMonth(month);
        for (BalanceService.BalanceRow r : arrears) {
            rows.add(List.of(
                nz(r.tenantNumber),
                nz(r.tenantName),
                nz(r.phone),
                nz(r.property),
                nz(r.room),
                formatMoney(r.expected),
                formatMoney(r.paid),
                formatMoney(r.outstanding)
            ));
        }

        return new ReportData(
            "Outstanding Rent",
            "Tenants with unpaid balances for " + month.getMonth() + " " + month.getYear(),
            cols,
            rows,
            "Period: " + month.getMonth() + " " + month.getYear(),
            LocalDate.now()
        );
    }

    /* =================================================================== */
    /*  PAYMENTS REPORT                                                    */
    /* =================================================================== */

    private ReportData buildPayments(ReportParams params) {
        List<String> cols = List.of(
            "RECEIPT NO.", "DATE", "TENANT", "ROOM", "FOR MONTH",
            "AMOUNT (KSh)", "METHOD", "REFERENCE");
        List<List<String>> rows = new ArrayList<>();

        List<Payment> payments = paymentRepo.findAll();

        // Date filtering
        if (params.fromDate != null || params.toDate != null) {
            LocalDate f = params.fromDate != null ? params.fromDate : LocalDate.of(2000, 1, 1);
            LocalDate t = params.toDate   != null ? params.toDate   : LocalDate.of(2100, 12, 31);
            payments = payments.stream()
                .filter(p -> !p.getPaymentDate().isBefore(f)
                          && !p.getPaymentDate().isAfter(t))
                .toList();
        }

        BigDecimal total = BigDecimal.ZERO;
        for (Payment p : payments) {
            String tenant = p.getTenant() != null ? p.getTenant().getFullName() : "—";
            String room = (p.getTenancy() != null && p.getTenancy().getUnit() != null)
                ? p.getTenancy().getUnit().getHouseNumber()
                : "—";
            String date = p.getPaymentDate() != null ? p.getPaymentDate().format(DATE_FMT) : "—";
            String reference = p.getReferenceNumber() != null ? p.getReferenceNumber() : "—";

            rows.add(List.of(
                nz(p.getReceiptNumber()),
                date,
                tenant,
                room,
                p.getPeriodLabel(),
                formatMoney(p.getAmount()),
                p.getMethodDisplay(),
                reference
            ));

            if (p.getAmount() != null) total = total.add(p.getAmount());
        }

        if (!rows.isEmpty()) {
            rows.add(List.of("TOTAL", "", "", "", "",
                formatMoney(total), "", ""));
        }

        String period = "";
        if (params.fromDate != null && params.toDate != null) {
            period = "Period: " + params.fromDate.format(DATE_FMT)
                   + " – " + params.toDate.format(DATE_FMT);
        } else if (params.fromDate != null) {
            period = "From: " + params.fromDate.format(DATE_FMT);
        } else if (params.toDate != null) {
            period = "Up to: " + params.toDate.format(DATE_FMT);
        }

        return new ReportData(
            "Payment Report",
            "Every payment recorded" + (period.isEmpty() ? "" : " (" + period + ")"),
            cols,
            rows,
            period,
            LocalDate.now()
        );
    }

    /* =================================================================== */
    /*  HELPERS                                                            */
    /* =================================================================== */

    private String nz(String s) { return (s == null || s.isBlank()) ? "—" : s; }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) amount = BigDecimal.ZERO;
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMaximumFractionDigits(0);
        return nf.format(amount);
    }

    /* =================================================================== */
    /*  SUPPORTING TYPES                                                   */
    /* =================================================================== */

    /** Filters that can be passed into {@link #build}. */
    public static class ReportParams {
        public LocalDate fromDate;
        public LocalDate toDate;
        public YearMonth month;

        public YearMonth monthOrDefault() {
            return month != null ? month : YearMonth.now();
        }
    }

    /** A generic report — everything the viewer needs to render it. */
    public static class ReportData {
        public final String title;
        public final String subtitle;
        public final List<String> columns;
        public final List<List<String>> rows;
        public final String filterLabel;
        public final LocalDate generatedOn;

        public ReportData(String title, String subtitle,
                          List<String> columns, List<List<String>> rows,
                          String filterLabel, LocalDate generatedOn) {
            this.title = title;
            this.subtitle = subtitle;
            this.columns = columns;
            this.rows = rows;
            this.filterLabel = filterLabel;
            this.generatedOn = generatedOn;
        }

        public static ReportData empty() {
            return new ReportData("Report", "No data",
                List.of(), List.of(), "", LocalDate.now());
        }
    }
}
