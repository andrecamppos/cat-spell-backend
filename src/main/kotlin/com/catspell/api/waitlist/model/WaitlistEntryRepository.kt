package com.catspell.api.waitlist.model

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface WaitlistEntryRepository : JpaRepository<WaitlistEntry, UUID> {

    /**
     * Race-free insert of a new PENDING entry. The UNIQUE(normalized_email) constraint is the arbiter: of any
     * concurrent callers for the same normalized email exactly one inserts and the rest are silently skipped by
     * `ON CONFLICT DO NOTHING`, so no DataIntegrityViolationException can mark the transaction rollback-only and
     * surface as a 500 that differs from the 202 (RESEARCH Pitfall 2, T-17-01). Every NOT NULL column is supplied
     * explicitly because the create-drop test schema has no DB defaults (Pitfall 6).
     * Returns the number of rows inserted (1 = new entry, 0 = already present).
     */
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO waitlist_entries (id, email, normalized_email, status, created_at, updated_at)
            VALUES (gen_random_uuid(), :email, :normalizedEmail, 'PENDING', :now, :now)
            ON CONFLICT (normalized_email) DO NOTHING
        """
    )
    fun insertIfAbsent(
        @Param("email") email: String,
        @Param("normalizedEmail") normalizedEmail: String,
        @Param("now") now: Instant
    ): Int

    /**
     * Atomically rotate the confirm token of a still-PENDING entry in a single conditional UPDATE, gated by the
     * resend cooldown (D-07, WR-03, WR-11). A fresh entry (NULL hash) always rotates. An entry that already holds a
     * token rotates only when its `updatedAt` is at or before [resendCutoff] (now minus the cooldown), so a re-join
     * inside the cooldown matches 0 rows and changes nothing. The status and cutoff guards are evaluated under the
     * row lock, so concurrent re-joins block, re-check the updated row, and rotate at most once. A CONFIRMED or
     * INVITED entry is never reset or re-tokened (D-04). Overwriting the hash invalidates any previously issued link
     * by construction. The email column is never written after insert (D-08), so a `+suffix` or case variant cannot
     * redirect the confirm link or the later invite. `<=` makes a cooldown of 0 mean "always rotate".
     * Returns 1 = token rotated, 0 = inside the cooldown or CONFIRMED/INVITED (silent no-op).
     */
    @Modifying
    @Query(
        """
        UPDATE WaitlistEntry e SET e.confirmTokenHash = :hash,
               e.confirmTokenExpiresAt = :expiresAt, e.updatedAt = :now
        WHERE e.normalizedEmail = :normalizedEmail AND e.status = :pending
          AND (e.confirmTokenHash IS NULL OR e.updatedAt <= :resendCutoff)
        """
    )
    fun rotatePendingToken(
        @Param("normalizedEmail") normalizedEmail: String,
        @Param("hash") hash: String,
        @Param("expiresAt") expiresAt: Instant,
        @Param("now") now: Instant,
        @Param("resendCutoff") resendCutoff: Instant,
        @Param("pending") pending: WaitlistStatus
    ): Int

    /**
     * The delivery address stored at first insert for the D-03 normalized key (pinned, D-08), or null when no row
     * exists. A scalar read, so it does not depend on the persistence context after the bulk rotate UPDATE.
     */
    @Query("SELECT e.email FROM WaitlistEntry e WHERE e.normalizedEmail = :normalizedEmail")
    fun findStoredEmail(@Param("normalizedEmail") normalizedEmail: String): String?

    /**
     * Single-use confirm claim (WAIT-02, D-08). One conditional UPDATE, evaluated under the row lock, rejects unknown,
     * expired (strict `expiresAt > now`), already-used and rotated-away tokens. This is the same guarantee as
     * InviteRepository.markConsumed, so of any concurrent claims for one token exactly one sees 1 row. The hash is
     * deliberately left on the row after confirm. Returns 1 = confirmed now, 0 = invalid/expired/already used.
     */
    @Modifying
    @Query(
        """
        UPDATE WaitlistEntry e SET e.status = :confirmed, e.confirmedAt = :now, e.updatedAt = :now
        WHERE e.confirmTokenHash = :hash AND e.status = :pending AND e.confirmTokenExpiresAt > :now
        """
    )
    fun claimConfirm(
        @Param("hash") hash: String,
        @Param("now") now: Instant,
        @Param("pending") pending: WaitlistStatus,
        @Param("confirmed") confirmed: WaitlistStatus
    ): Int

    /**
     * Single-winner conversion claim (WAIT-04, D-10). One conditional UPDATE, evaluated by the database under the
     * row lock, moves a CONFIRMED entry to INVITED; concurrent claims for the same id block on the lock and then
     * re-check `status = :confirmed`, so exactly one of them sees 1 row. Not `clearAutomatically`: callers must not
     * re-read the entity's status afterwards. Returns 1 = claimed; 0 = PENDING, already INVITED, or lost a race.
     */
    @Modifying
    @Query(
        """
        UPDATE WaitlistEntry e SET e.status = :invited, e.invitedAt = :now, e.updatedAt = :now
        WHERE e.id = :id AND e.status = :confirmed
        """
    )
    fun markInvited(
        @Param("id") id: UUID,
        @Param("now") now: Instant,
        @Param("confirmed") confirmed: WaitlistStatus,
        @Param("invited") invited: WaitlistStatus
    ): Int

    /**
     * Operator list (D-09): entries in [status], oldest confirmation first, `created_at` as the tiebreak. Postgres
     * sorts NULL `confirmed_at` last for ASC (PENDING rows), and `idx_waitlist_entries_status_confirmed_at` serves
     * the query. The caller bounds the page size.
     */
    fun findByStatusOrderByConfirmedAtAscCreatedAtAsc(status: WaitlistStatus, pageable: Pageable): List<WaitlistEntry>
}
