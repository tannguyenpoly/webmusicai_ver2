package com.fpoly.webmusicai.controller;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.time.temporal.WeekFields;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.fpoly.webmusicai.entity.Authority;
import com.fpoly.webmusicai.entity.Order;
import com.fpoly.webmusicai.entity.PaymentLog;
import com.fpoly.webmusicai.entity.Song;
import com.fpoly.webmusicai.entity.Transaction;
import com.fpoly.webmusicai.entity.Tag;
import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.entity.Role;
import com.fpoly.webmusicai.repository.OrderRepository;
import com.fpoly.webmusicai.repository.SongRepository;
import com.fpoly.webmusicai.repository.SongTagRepository;
import com.fpoly.webmusicai.repository.TagRepository;
import com.fpoly.webmusicai.repository.UserRepository;
import com.fpoly.webmusicai.repository.UserSpendingProjection;
import com.fpoly.webmusicai.repository.UserTokenUsageProjection;
import com.fpoly.webmusicai.repository.TransactionRepository;
import com.fpoly.webmusicai.repository.PaymentLogRepository;
import com.fpoly.webmusicai.repository.GenreRepository;
import com.fpoly.webmusicai.repository.PackageRepository;
import com.fpoly.webmusicai.repository.AuthorityRepository;
import com.fpoly.webmusicai.repository.RoleRepository;
import com.fpoly.webmusicai.service.PaymentCompletionResult;
import com.fpoly.webmusicai.service.PaymentService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.persistence.criteria.Predicate;

@RestController
@RequestMapping("/api/admin")
public class AdminRestController {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private AuthorityRepository authorityRepo;

    @Autowired
    private RoleRepository roleRepo;

    @Autowired
    private SongRepository songRepo;

    @Autowired
    private OrderRepository orderRepo;

    @Autowired
    private TagRepository tagRepo;

    @Autowired
    private SongTagRepository songTagRepo;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private TransactionRepository transactionRepo;

    @Autowired
    private PaymentLogRepository paymentLogRepo;

    @Autowired
    private GenreRepository genreRepo;

    @Autowired
    private PackageRepository packageRepo;



    @GetMapping("/genres/summary")
    public ResponseEntity<?> getGenreSummary(@RequestParam(defaultValue = "usage_desc") String sort) {
        List<Map<String, Object>> rows = genreRepo.findUsageSummary().stream().map(row -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", row[0]);
            item.put("name", row[1]);
            item.put("description", row[2]);
            item.put("createdAt", row[3]);
            item.put("usageCount", ((Number) row[4]).longValue());
            return item;
        }).collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        Comparator<Map<String, Object>> byName = Comparator.comparing(row -> String.valueOf(row.get("name")), String.CASE_INSENSITIVE_ORDER);
        Comparator<Map<String, Object>> byUsage = Comparator.comparingLong(row -> ((Number) row.get("usageCount")).longValue());
        if ("usage_asc".equals(sort)) rows.sort(byUsage.thenComparing(byName));
        else if ("name_asc".equals(sort)) rows.sort(byName);
        else if ("name_desc".equals(sort)) rows.sort(byName.reversed());
        else rows.sort(byUsage.reversed().thenComparing(byName));
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/packages/summary")
    public ResponseEntity<?> getPackageSummary(@RequestParam(defaultValue = "sales_desc") String sort) {
        List<Map<String, Object>> rows = packageRepo.findSalesSummary().stream().map(row -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", row[0]); item.put("tokens", row[1]); item.put("price", row[2]);
            item.put("description", row[3]); item.put("oldPrice", row[4]); item.put("badge", row[5]);
            item.put("tierCode", row[6]); item.put("durationDays", row[7]);
            item.put("successfulOrders", ((Number) row[8]).longValue());
            item.put("revenue", ((Number) row[9]).longValue());
            return item;
        }).collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        Comparator<Map<String, Object>> byTier = Comparator.comparing(row -> String.valueOf(row.get("tierCode")), String.CASE_INSENSITIVE_ORDER);
        Comparator<Map<String, Object>> bySales = Comparator.comparingLong(row -> ((Number) row.get("successfulOrders")).longValue());
        Comparator<Map<String, Object>> byRevenue = Comparator.comparingLong(row -> ((Number) row.get("revenue")).longValue());
        Comparator<Map<String, Object>> byPrice = Comparator.comparingLong(row -> ((Number) row.get("price")).longValue());
        Comparator<Map<String, Object>> byTokens = Comparator.comparingLong(row -> ((Number) row.get("tokens")).longValue());
        switch (sort) {
            case "sales_asc" -> rows.sort(bySales.thenComparing(byTier));
            case "revenue_desc" -> rows.sort(byRevenue.reversed().thenComparing(byTier));
            case "price_asc" -> rows.sort(byPrice.thenComparing(byTier));
            case "price_desc" -> rows.sort(byPrice.reversed().thenComparing(byTier));
            case "tokens_asc" -> rows.sort(byTokens.thenComparing(byTier));
            case "tokens_desc" -> rows.sort(byTokens.reversed().thenComparing(byTier));
            default -> rows.sort(bySales.reversed().thenComparing(byTier));
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/users")
    public ResponseEntity<?> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) String tier,
            @RequestParam(defaultValue = "ALL") String period,
            @RequestParam(defaultValue = "ALL") String roleFilter,
            @RequestParam(defaultValue = "newest") String tokenSort,
            @RequestParam(required = false) String registrationMode,
            @RequestParam(required = false) Integer registrationYear,
            @RequestParam(required = false) Integer registrationMonth,
            @RequestParam(required = false) Integer registrationWeek) {

        org.springframework.data.domain.Sort sort = switch (tokenSort) {
            case "token_asc" -> org.springframework.data.domain.Sort.by("tokenBalance").ascending();
            case "token_desc" -> org.springframework.data.domain.Sort.by("tokenBalance").descending();
            default -> org.springframework.data.domain.Sort.by("createdAt").descending();
        };
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<User> users;
        Date[] bounds = registrationMode == null || registrationMode.isBlank()
                ? getPeriodBounds(period)
                : getRegistrationBounds(registrationMode, registrationYear, registrationMonth, registrationWeek);
        users = userRepo.findForAdmin(
                keyword == null || keyword.isBlank() ? null : keyword.trim(),
                tier == null || tier.isBlank() ? null : tier.trim().toUpperCase(),
                bounds[0], bounds[1], normalizeRoleFilter(roleFilter), pageable);

        List<Map<String, Object>> userMaps = users.getContent().stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("username", u.getUsername());
            m.put("fullname", u.getFullname());
            m.put("email", u.getEmail());
            m.put("photo", u.getPhoto());
            m.put("tokenBalance", u.getTokenBalance());
            m.put("enabled", u.getEnabled());
            m.put("accountTier", u.getAccountTier());
            m.put("proExpiredAt", u.getProExpiredAt());
            m.put("createdAt", u.getCreatedAt());
            boolean isAdmin = u.getAuthorities() != null &&
                u.getAuthorities().stream().anyMatch(a -> a.getRole().getId().equals("ADMIN"));
            m.put("admin", isAdmin);
            return m;
        }).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("content", userMaps);
        result.put("totalPages", users.getTotalPages());
        result.put("totalElements", users.getTotalElements());
        result.put("number", users.getNumber());
        result.put("size", users.getSize());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/users/spending")
    public ResponseEntity<?> getUserSpending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) String tier,
            @RequestParam(defaultValue = "ALL") String roleFilter,
            @RequestParam(defaultValue = "ALL") String period,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer week,
            @RequestParam(defaultValue = "spent_desc") String spentSort) {
        Date[] bounds = getSpendingBounds(period, year, month, week);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        Page<UserSpendingProjection> spendingPage = userRepo.findSpendingForAdmin(
                keyword == null || keyword.isBlank() ? null : keyword.trim(),
                tier == null || tier.isBlank() ? null : tier.trim().toUpperCase(),
                normalizeRoleFilter(roleFilter), bounds[0], bounds[1], normalizeSpentSort(spentSort), pageable);
        Map<String, Long> usageByUser = getUsageByUser(bounds);
        List<Map<String, Object>> content = spendingPage.getContent().stream()
                .map(row -> toSpendingMap(row, usageByUser)).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("totalPages", spendingPage.getTotalPages());
        result.put("totalElements", spendingPage.getTotalElements());
        result.put("number", spendingPage.getNumber());
        result.put("size", spendingPage.getSize());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/users/spending/insights")
    public ResponseEntity<?> getUserSpendingInsights(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) String tier,
            @RequestParam(defaultValue = "ALL") String roleFilter,
            @RequestParam(defaultValue = "ALL") String period,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer week,
            @RequestParam(defaultValue = "spent_desc") String spentSort) {
        Date[] bounds = getSpendingBounds(period, year, month, week);
        Page<UserSpendingProjection> spendingRows = userRepo.findSpendingForAdmin(
                keyword == null || keyword.isBlank() ? null : keyword.trim(),
                tier == null || tier.isBlank() ? null : tier.trim().toUpperCase(),
                normalizeRoleFilter(roleFilter), bounds[0], bounds[1], normalizeSpentSort(spentSort), Pageable.unpaged());
        Map<String, Long> usageByUser = getUsageByUser(bounds);
        List<Map<String, Object>> allRows = spendingRows.getContent().stream()
                .map(row -> toSpendingMap(row, usageByUser)).toList();

        long payingCustomers = allRows.stream().filter(row -> longValue(row.get("totalSpent")) > 0).count();
        long activeUsers = allRows.stream().filter(row -> longValue(row.get("usedTokens")) > 0).count();
        long revenue = allRows.stream().mapToLong(row -> longValue(row.get("totalSpent"))).sum();

        return ResponseEntity.ok(Map.of(
                "payingCustomers", payingCustomers,
                "activeUsers", activeUsers,
                "revenue", revenue));
    }

    private Map<String, Long> getUsageByUser(Date[] bounds) {
        Map<String, Long> usageByUser = new HashMap<>();
        for (UserTokenUsageProjection row : transactionRepo.summarizeUsageForAdmin(bounds[0], bounds[1])) {
            usageByUser.put(row.getUsername(), row.getUsedTokens() == null ? 0L : row.getUsedTokens());
        }
        return usageByUser;
    }

    private Map<String, Object> toSpendingMap(UserSpendingProjection row, Map<String, Long> usageByUser) {
        long purchasedTokens = row.getPurchasedTokens() == null ? 0L : row.getPurchasedTokens();
        long usedTokens = usageByUser.getOrDefault(row.getUsername(), 0L);
        long tokenBalance = row.getTokenBalance() == null ? 0L : row.getTokenBalance();
        long totalSpent = row.getTotalSpent() == null ? 0L : row.getTotalSpent();
        Map<String, Object> item = new HashMap<>();
        item.put("username", row.getUsername());
        item.put("fullname", row.getFullname());
        item.put("email", row.getEmail());
        item.put("accountTier", row.getAccountTier());
        item.put("tokenBalance", tokenBalance);
        item.put("successfulOrderCount", row.getSuccessfulOrderCount() == null ? 0L : row.getSuccessfulOrderCount());
        item.put("totalSpent", totalSpent);
        item.put("purchasedTokens", purchasedTokens);
        item.put("usedTokens", usedTokens);
        item.put("lastPaidAt", row.getLastPaidAt());
        item.put("admin", authorityRepo.findByUserUsernameAndRoleId(row.getUsername(), "ADMIN").isPresent());
        return item;
    }

    private static long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    @PutMapping("/users/{username}/toggle-status")
    public ResponseEntity<?> toggleUserStatus(@PathVariable String username) {
        return userRepo.findById(username).map(user -> {
            user.setEnabled(!user.getEnabled());
            user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
            userRepo.save(user);
            String status = user.getEnabled() ? "mở khóa" : "khóa";
            return ResponseEntity.ok(Map.of(
                "message", "Đã " + status + " tài khoản " + username,
                "enabled", user.getEnabled()
            ));
        }).orElse(ResponseEntity.badRequest().body(Map.of("message", "Không tìm thấy người dùng: " + username)));
    }

    @GetMapping("/songs")
    public ResponseEntity<?> getAllSongs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "") String username) {

        Pageable pageable = PageRequest.of(page, size);
        Page<Song> songs;

        if (username != null && !username.trim().isEmpty()) {
            songs = songRepo.findByUserUsernameOrderByCreatedAtDesc(username.trim(), pageable);
        } else if (status != null && !status.trim().isEmpty()) {
            songs = songRepo.findByStatusOrderByCreatedAtDesc(status.trim(), pageable);
        } else {
            songs = songRepo.findAll(pageable);
        }

        return ResponseEntity.ok(songs);
    }

    @DeleteMapping("/songs/{id}")
    public ResponseEntity<?> deleteSong(@PathVariable Integer id) {
        if (!songRepo.existsById(id)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Bài nhạc không tồn tại!"));
        }
        songRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Đã xóa bài nhạc #" + id));
    }

    @PutMapping("/songs/{id}/toggle-public")
    public ResponseEntity<?> adminTogglePublic(@PathVariable Integer id) {
        return songRepo.findById(id).map(song -> {
            song.setIsPublic(!song.getIsPublic());
            songRepo.save(song);
            return ResponseEntity.ok(Map.of(
                "message", "Đã cập nhật trạng thái public",
                "id", song.getId(),
                "is_public", song.getIsPublic()
            ));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ============ QUẢN LÝ ORDERS ============

    @GetMapping("/orders")
    public ResponseEntity<?> getAllOrders(
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "ALL") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Date[] bounds = resolveDateBounds(from, to, period);
        String statusParam = (status != null && !status.isEmpty()) ? status : null;
        Pageable pageable = PageRequest.of(page, size, org.springframework.data.domain.Sort.by("createdAt").descending());
        return ResponseEntity.ok(orderRepo.findFiltered(bounds[0], bounds[1], statusParam, pageable));
    }

    /** Chỉ Admin hiện tại mới có thể cấp hoặc thu hồi quyền Admin của tài khoản khác. */
    @PutMapping("/users/{username}/toggle-admin")
    @Transactional
    public ResponseEntity<?> toggleAdminRole(@PathVariable String username) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && username.equals(auth.getName())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Không thể tự thay đổi quyền Admin của chính mình."));
        }
        User user = userRepo.findById(username).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();

        var existing = authorityRepo.findByUserUsernameAndRoleId(username, "ADMIN");
        if (existing.isPresent()) {
            authorityRepo.delete(existing.get());
            return ResponseEntity.ok(Map.of("message", "Đã thu hồi quyền Admin của " + username, "admin", false));
        }

        Role adminRole = roleRepo.findById("ADMIN").orElse(null);
        if (adminRole == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Chưa có role ADMIN trong cơ sở dữ liệu."));
        }
        Authority authority = new Authority();
        authority.setUser(user);
        authority.setRole(adminRole);
        authorityRepo.save(authority);
        return ResponseEntity.ok(Map.of("message", "Đã cấp quyền Admin cho " + username, "admin", true));
    }

    @PutMapping("/orders/{orderCode}/approve-review")
    public ResponseEntity<?> approveReviewOrder(@PathVariable String orderCode) {
        try {
            PaymentCompletionResult result = paymentService.approveReview(orderCode);
            return ResponseEntity.ok(Map.of("status", result.status(), "message", result.message()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
    // ============ DOANH THU ============

    @GetMapping("/revenue")
    public ResponseEntity<?> getRevenue(
            @RequestParam(defaultValue = "ALL") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        Map<String, Object> result = new HashMap<>();

        Date[] bounds = resolveDateBounds(from, to, period);
        List<Order> scopedOrders = orderRepo.findFiltered(bounds[0], bounds[1], null);
        long totalRevenue = scopedOrders.stream().filter(order -> "SUCCESS".equals(order.getStatus()))
                .mapToLong(Order::getTotalPrice).sum();
        result.put("totalRevenue", totalRevenue);
        result.put("completedOrders", scopedOrders.stream().filter(order -> "SUCCESS".equals(order.getStatus())).count());
        result.put("pendingOrders", scopedOrders.stream().filter(order -> "PENDING".equals(order.getStatus())).count());
        result.put("reviewOrders", scopedOrders.stream().filter(order -> "REVIEW".equals(order.getStatus())).count());
        result.put("failedOrders", scopedOrders.stream().filter(order -> "FAILED".equals(order.getStatus())).count());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/revenue/trend")
    public ResponseEntity<?> getRevenueTrend(
            @RequestParam(defaultValue = "ALL") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "DAY") String granularity) {
        Date[] bounds = resolveDateBounds(from, to, period);
        boolean byMonth = "MONTH".equalsIgnoreCase(granularity);
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        java.time.format.DateTimeFormatter keyFormatter = java.time.format.DateTimeFormatter
                .ofPattern(byMonth ? "yyyy-MM" : "yyyy-MM-dd");
        java.time.format.DateTimeFormatter labelFormatter = java.time.format.DateTimeFormatter
                .ofPattern(byMonth ? "MM/yyyy" : "dd/MM");
        java.util.Map<String, Long> totals = new java.util.TreeMap<>();
        for (Order order : orderRepo.findFiltered(bounds[0], bounds[1], null)) {
            if (!"SUCCESS".equals(order.getStatus())) continue;
            java.time.LocalDate date = order.getCreatedAt().toInstant().atZone(zone).toLocalDate();
            String key = byMonth ? date.withDayOfMonth(1).format(keyFormatter) : date.format(keyFormatter);
            totals.merge(key, order.getTotalPrice().longValue(), Long::sum);
        }
        List<String> labels = totals.keySet().stream()
                .map(key -> java.time.LocalDate.parse(key + (byMonth ? "-01" : ""))
                        .format(labelFormatter))
                .toList();
        return ResponseEntity.ok(Map.of("labels", labels, "amounts", totals.values()));
    }

    private static String normalizeRoleFilter(String roleFilter) {
        return "ADMIN".equalsIgnoreCase(roleFilter) ? "ADMIN"
                : "USER".equalsIgnoreCase(roleFilter) ? "USER" : "ALL";
    }

    /** Khoảng thời gian chọn sẵn để Admin không phải nhập ngày thủ công. */
    private static Date[] getPeriodBounds(String rawPeriod) {
        String period = rawPeriod == null ? "ALL" : rawPeriod.toUpperCase();
        java.util.Calendar start = java.util.Calendar.getInstance();
        java.util.Calendar end = java.util.Calendar.getInstance();
        if ("ALL".equals(period)) return new Date[] { null, null };
        start.set(java.util.Calendar.HOUR_OF_DAY, 0); start.set(java.util.Calendar.MINUTE, 0); start.set(java.util.Calendar.SECOND, 0); start.set(java.util.Calendar.MILLISECOND, 0);
        end.set(java.util.Calendar.HOUR_OF_DAY, 23); end.set(java.util.Calendar.MINUTE, 59); end.set(java.util.Calendar.SECOND, 59); end.set(java.util.Calendar.MILLISECOND, 999);
        if ("MONTH".equals(period)) start.set(java.util.Calendar.DAY_OF_MONTH, 1);
        else if ("QUARTER".equals(period)) { start.set(java.util.Calendar.MONTH, (start.get(java.util.Calendar.MONTH) / 3) * 3); start.set(java.util.Calendar.DAY_OF_MONTH, 1); }
        else if ("YEAR".equals(period)) { start.set(java.util.Calendar.MONTH, java.util.Calendar.JANUARY); start.set(java.util.Calendar.DAY_OF_MONTH, 1); }
        return new Date[] { start.getTime(), end.getTime() };
    }

    private static String normalizeSpentSort(String rawSort) {
        return "spent_asc".equalsIgnoreCase(rawSort) ? "spent_asc" : "spent_desc";
    }

    /** Khoảng tuần dùng chuẩn ISO: Thứ Hai đến Chủ nhật, có thể vắt qua tháng hoặc năm. */
    private static Date[] getRegistrationBounds(String rawMode, Integer rawYear, Integer rawMonth, Integer rawWeek) {
        String mode = rawMode == null ? "ALL" : rawMode.toUpperCase();
        if ("ALL".equals(mode)) return new Date[] { null, null };
        int year = rawYear == null ? LocalDate.now().getYear() : Math.max(2000, Math.min(rawYear, 2100));
        int month = rawMonth == null ? 1 : Math.max(1, Math.min(rawMonth, 12));
        LocalDate startDate;
        LocalDate endDate;
        if ("TODAY".equals(mode)) {
            startDate = LocalDate.now();
            endDate = startDate;
        } else if ("YEAR".equals(mode)) {
            startDate = LocalDate.of(year, 1, 1);
            endDate = startDate.withMonth(12).withDayOfMonth(31);
        } else if ("MONTH".equals(mode)) {
            startDate = LocalDate.of(year, month, 1);
            endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        } else if ("WEEK".equals(mode)) {
            return getIsoWeekBounds(year, rawWeek);
        } else {
            return new Date[] { null, null };
        }
        return toFullDayBounds(startDate, endDate);
    }

    private static Date[] getSpendingBounds(String rawPeriod, Integer rawYear, Integer rawMonth, Integer rawWeek) {
        String period = rawPeriod == null ? "ALL" : rawPeriod.toUpperCase();
        if ("ALL".equals(period)) return new Date[] { null, null };
        int year = rawYear == null ? LocalDate.now().getYear() : Math.max(2000, Math.min(rawYear, 2100));
        if ("TODAY".equals(period)) {
            LocalDate today = LocalDate.now();
            return toFullDayBounds(today, today);
        }
        if ("YEAR".equals(period)) {
            return toFullDayBounds(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        }
        if ("MONTH".equals(period)) {
            int month = rawMonth == null ? 1 : Math.max(1, Math.min(rawMonth, 12));
            LocalDate firstDay = LocalDate.of(year, month, 1);
            return toFullDayBounds(firstDay, firstDay.withDayOfMonth(firstDay.lengthOfMonth()));
        }
        if ("WEEK".equals(period)) {
            return getIsoWeekBounds(year, rawWeek);
        }
        return new Date[] { null, null };
    }

    private static Date[] getIsoWeekBounds(int year, Integer rawWeek) {
        int maxWeek = LocalDate.of(year, 12, 28).get(WeekFields.ISO.weekOfWeekBasedYear());
        int week = rawWeek == null ? 1 : Math.max(1, Math.min(rawWeek, maxWeek));
        LocalDate monday = LocalDate.of(year, 1, 4)
                .with(WeekFields.ISO.weekOfWeekBasedYear(), week)
                .with(ChronoField.DAY_OF_WEEK, 1);
        return toFullDayBounds(monday, monday.plusDays(6));
    }

    private static Date[] toFullDayBounds(LocalDate startDate, LocalDate endDate) {
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        return new Date[] {
                Date.from(startDate.atStartOfDay(zone).toInstant()),
                Date.from(endDate.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1))
        };
    }

    /** Ưu tiên khoảng ngày do UI tính từ năm/tháng/tuần; vẫn giữ period cho API cũ. */
    private static Date[] resolveDateBounds(String from, String to, String period) {
        if ((from == null || from.isBlank()) && (to == null || to.isBlank())) return getPeriodBounds(period);
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            sdf.setLenient(false);
            Date start = from == null || from.isBlank() ? null : sdf.parse(from);
            Date end = to == null || to.isBlank() ? null
                    : new Date(sdf.parse(to).getTime() + 86400000L - 1);
            return new Date[] { start, end };
        } catch (java.text.ParseException e) {
            throw new IllegalArgumentException("Khoảng ngày không hợp lệ", e);
        }
    }

    // ============ THỐNG KÊ CHI TIẾT (MỞ RỘNG) ============

    @GetMapping("/statistics")
    public ResponseEntity<?> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalUsers", userRepo.count());

        stats.put("totalSongs", songRepo.count());
        stats.put("completedSongs", songRepo.countByStatus("COMPLETED"));
        stats.put("pendingSongs", songRepo.countByStatus("PENDING"));
        stats.put("failedSongs", songRepo.countByStatus("FAILED"));
        stats.put("publicSongs", songRepo.countByIsPublicTrue());

        long total = songRepo.count();
        long completed = songRepo.countByStatus("COMPLETED");
        double successRate = total > 0 ? (completed * 100.0 / total) : 0;
        stats.put("successRate", Math.round(successRate * 10) / 10.0); // 1 chữ số sau phẩy

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/tags")
    public ResponseEntity<?> getAllTags() {
        return ResponseEntity.ok(tagRepo.findAll());
    }

    @PostMapping("/tags")
    public ResponseEntity<?> createTag(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.trim().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Tên tag không được để trống"));
        }
        if (tagRepo.findByNameIgnoreCase(name.trim()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Tag đã tồn tại"));
        }
        Tag tag = new Tag();
        tag.setName(name.trim());
        return ResponseEntity.ok(tagRepo.save(tag));
    }

    @DeleteMapping("/tags/{id}")
    @Transactional
    public ResponseEntity<?> deleteTag(@PathVariable Integer id) {
        if (!tagRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        songTagRepo.deleteByTagId(id);
        tagRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
