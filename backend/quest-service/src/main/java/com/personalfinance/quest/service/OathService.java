package com.personalfinance.quest.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personalfinance.quest.entity.CategoryProjectionEntity;
import com.personalfinance.quest.entity.OathEntity;
import com.personalfinance.quest.events.Events;
import com.personalfinance.quest.exception.NotFoundException;
import com.personalfinance.quest.repository.CategoryProjectionRepository;
import com.personalfinance.quest.repository.OathRepository;

/**
 * Tally oaths (F2): a pledge to spend roughly a given amount in a given
 * category before a deadline, reconciled against the expense stream Quest
 * already consumes. An oath is never written to the expense ledger — an
 * intention is not an expense (docs/roadmap.md D2).
 */
@Service
public class OathService {

    static final String OPEN = "OPEN";
    static final String KEPT = "KEPT";
    static final String SLIPPED = "SLIPPED";
    static final String FORGONE = "FORGONE";

    /** Floor under the 10% band so small pledges keep a usable margin. */
    static final long MIN_TOLERANCE_BANI = 500;

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final OathRepository oaths;
    private final CategoryProjectionRepository categories;
    private final ApplicationEventPublisher events;

    public OathService(OathRepository oaths, CategoryProjectionRepository categories,
            ApplicationEventPublisher events) {
        this.oaths = oaths;
        this.categories = categories;
        this.events = events;
    }

    @Transactional
    public OathEntity create(UUID userId, UUID categoryId, long pledgedAmount, Instant expiresAt, Instant now) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (categoryId == null) {
            throw new IllegalArgumentException("categoryId is required");
        }
        if (pledgedAmount <= 0) {
            throw new IllegalArgumentException("pledgedAmount must be positive");
        }
        if (expiresAt == null || !expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("expiresAt must be in the future");
        }
        CategoryProjectionEntity category = categories.findById(categoryId)
                .filter(row -> userId.equals(row.getUserId()))
                .orElseThrow(() -> new NotFoundException("Category not found"));

        return oaths.save(new OathEntity(userId, categoryId, category.getName(), pledgedAmount, now, expiresAt));
    }

    @Transactional(readOnly = true)
    public List<OathEntity> list(UUID userId, String status) {
        return status == null || status.isBlank()
                ? oaths.findByUserIdOrderByCreatedAtDesc(userId)
                : oaths.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status.toUpperCase(Locale.ROOT));
    }

    /**
     * Cancelling withdraws the pledge entirely rather than recording a fifth
     * status: a withdrawn oath was never kept, slipped, or forgone, and leaving
     * it in the ledger would misreport what the user actually did. A resolved
     * oath is not an open oath and so cannot be found to cancel.
     */
    @Transactional
    public void cancel(UUID id, UUID userId) {
        OathEntity oath = oaths.findByIdAndUserId(id, userId)
                .filter(row -> OPEN.equals(row.getStatus()))
                .orElseThrow(() -> new NotFoundException("Open oath not found"));
        oaths.delete(oath);
    }

    /**
     * Closes the first open oath this expense satisfies: same user, the pledged
     * category or a child of it, an amount within tolerance of the pledge, and
     * an expense date inside the oath's window. Oldest oath first, one oath per
     * expense.
     */
    @Transactional
    public void reconcile(UUID userId, UUID expenseId, UUID categoryId, long amount,
            LocalDate expenseDate, Instant now) {
        if (userId == null || expenseId == null || categoryId == null || expenseDate == null) {
            return;
        }
        // The expense may already have closed an oath on a previous delivery.
        if (oaths.existsByMatchedExpenseId(expenseId)) {
            return;
        }
        oaths.findByUserIdAndStatusAndCategoryIdInOrderByCreatedAtAsc(userId, OPEN, pledgeableCategories(categoryId))
                .stream()
                .filter(oath -> withinWindow(oath, expenseDate))
                .findFirst()
                .ifPresent(oath -> resolve(oath,
                        withinTolerance(oath.getPledgedAmount(), amount) ? KEPT : SLIPPED, expenseId, now));
    }

    /** Open oaths whose deadline has passed close as FORGONE — a distinct, positive outcome. */
    @Transactional
    public void expireOpenOaths(Instant now) {
        oaths.findByStatusAndExpiresAtBefore(OPEN, now)
                .forEach(oath -> resolve(oath, FORGONE, null, now));
    }

    /**
     * An oath pledged against a parent must match spending in its children, so
     * an expense in "Food &gt; Groceries" is a candidate for a "Food" pledge.
     */
    private List<UUID> pledgeableCategories(UUID categoryId) {
        List<UUID> candidates = new ArrayList<>(2);
        candidates.add(categoryId);
        categories.findById(categoryId)
                .map(CategoryProjectionEntity::getParentId)
                .ifPresent(candidates::add);
        return candidates;
    }

    private boolean withinWindow(OathEntity oath, LocalDate expenseDate) {
        LocalDate from = oath.getCreatedAt().atZone(ZONE).toLocalDate();
        LocalDate to = oath.getExpiresAt().atZone(ZONE).toLocalDate();
        return !expenseDate.isBefore(from) && !expenseDate.isAfter(to);
    }

    static boolean withinTolerance(long pledgedAmount, long actualAmount) {
        long tolerance = Math.max(Math.round(pledgedAmount * 0.10), MIN_TOLERANCE_BANI);
        return Math.abs(actualAmount - pledgedAmount) <= tolerance;
    }

    /**
     * The conditional update is what makes redelivery safe: a second attempt to
     * close an already-closed oath touches no rows, so no second event is sent.
     */
    private void resolve(OathEntity oath, String status, UUID matchedExpenseId, Instant now) {
        if (oaths.resolveIfOpen(oath.getId(), status, matchedExpenseId, now) == 1) {
            events.publishEvent(new Events.OathResolved(oath.getId(), oath.getUserId(),
                    title(oath), status, now));
        }
    }

    private static String title(OathEntity oath) {
        return "Pledged " + (oath.getPledgedAmount() / 100) + " RON on " + oath.getCategoryName();
    }
}
