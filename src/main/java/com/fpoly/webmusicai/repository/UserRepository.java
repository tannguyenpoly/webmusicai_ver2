package com.fpoly.webmusicai.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import com.fpoly.webmusicai.entity.User;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Date;

public interface UserRepository extends JpaRepository<User, String> {
    Page<User> findByUsernameContainingIgnoreCaseOrFullnameContainingIgnoreCase(String username, String fullname, Pageable pageable);

    @Query("SELECT u FROM User u WHERE "
            + "(:keyword IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(u.fullname) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "AND (:tier IS NULL OR u.accountTier = :tier) "
            + "AND (:createdFrom IS NULL OR u.createdAt >= :createdFrom) "
            + "AND (:createdTo IS NULL OR u.createdAt <= :createdTo) "
            + "AND (:roleFilter = 'ALL' "
            + "OR (:roleFilter = 'ADMIN' AND EXISTS (SELECT a FROM Authority a WHERE a.user = u AND a.role.id = 'ADMIN')) "
            + "OR (:roleFilter = 'USER' AND NOT EXISTS (SELECT a FROM Authority a WHERE a.user = u AND a.role.id = 'ADMIN')))")
    Page<User> findForAdmin(@Param("keyword") String keyword, @Param("tier") String tier,
            @Param("createdFrom") Date createdFrom, @Param("createdTo") Date createdTo,
            @Param("roleFilter") String roleFilter, Pageable pageable);

    @Query(value = """
            SELECT u.username AS username, u.fullname AS fullname, u.email AS email,
                   u.accountTier AS accountTier, u.tokenBalance AS tokenBalance,
                   COUNT(o.id) AS successfulOrderCount,
                   COALESCE(SUM(o.totalPrice), 0) AS totalSpent,
                   COALESCE(SUM(o.pkg.tokens), 0) AS purchasedTokens,
                   MAX(o.createdAt) AS lastPaidAt
            FROM User u
            LEFT JOIN Order o ON o.user = u
                AND o.status = 'SUCCESS'
                AND (:spentFrom IS NULL OR o.createdAt >= :spentFrom)
                AND (:spentTo IS NULL OR o.createdAt <= :spentTo)
            WHERE (:keyword IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(u.fullname) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:tier IS NULL OR u.accountTier = :tier)
              AND (:roleFilter = 'ALL'
                   OR (:roleFilter = 'ADMIN' AND EXISTS (SELECT a FROM Authority a WHERE a.user = u AND a.role.id = 'ADMIN'))
                   OR (:roleFilter = 'USER' AND NOT EXISTS (SELECT a FROM Authority a WHERE a.user = u AND a.role.id = 'ADMIN')))
            GROUP BY u.username, u.fullname, u.email, u.accountTier, u.tokenBalance
            ORDER BY
                CASE WHEN :spentSort = 'spent_asc' THEN COALESCE(SUM(o.totalPrice), 0) END ASC,
                CASE WHEN :spentSort <> 'spent_asc' THEN COALESCE(SUM(o.totalPrice), 0) END DESC,
                u.username ASC
            """, countQuery = """
            SELECT COUNT(u)
            FROM User u
            WHERE (:keyword IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(u.fullname) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:tier IS NULL OR u.accountTier = :tier)
              AND (:roleFilter = 'ALL'
                   OR (:roleFilter = 'ADMIN' AND EXISTS (SELECT a FROM Authority a WHERE a.user = u AND a.role.id = 'ADMIN'))
                   OR (:roleFilter = 'USER' AND NOT EXISTS (SELECT a FROM Authority a WHERE a.user = u AND a.role.id = 'ADMIN')))
            """)
    Page<UserSpendingProjection> findSpendingForAdmin(
            @Param("keyword") String keyword,
            @Param("tier") String tier,
            @Param("roleFilter") String roleFilter,
            @Param("spentFrom") Date spentFrom,
            @Param("spentTo") Date spentTo,
            @Param("spentSort") String spentSort,
            Pageable pageable);
    List<User> findByEmail(String email);
    Optional<User> findFirstByEmail(String email);
    List<User> findByEmailIgnoreCase(String email);
    Optional<User> findFirstByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.username = :username")
    Optional<User> findByUsernameForUpdate(@Param("username") String username);

    @Modifying
    @Query("UPDATE User u SET u.lastSeenAt = :lastSeenAt WHERE u.username = :username")
    int touchLastSeen(@Param("username") String username, @Param("lastSeenAt") Date lastSeenAt);
}
