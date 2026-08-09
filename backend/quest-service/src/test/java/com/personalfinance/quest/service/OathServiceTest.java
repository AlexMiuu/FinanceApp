package com.personalfinance.quest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.personalfinance.quest.entity.CategoryProjectionEntity;
import com.personalfinance.quest.entity.OathEntity;
import com.personalfinance.quest.events.Events;
import com.personalfinance.quest.exception.NotFoundException;
import com.personalfinance.quest.repository.CategoryProjectionRepository;
import com.personalfinance.quest.repository.OathRepository;

class OathServiceTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final UUID userId = UUID.randomUUID();
    private final UUID foodId = UUID.randomUUID();
    private final UUID groceriesId = UUID.randomUUID();
    private final UUID expenseId = UUID.randomUUID();

    private final LocalDate opened = LocalDate.of(2026, 7, 1);
    private final LocalDate deadline = LocalDate.of(2026, 7, 3);
    private final Instant createdAt = opened.atStartOfDay(ZONE).toInstant();
    private final Instant expiresAt = deadline.atTime(23, 0).atZone(ZONE).toInstant();
    private final Instant now = expiresAt;

    private OathRepository oaths;
    private CategoryProjectionRepository categories;
    private List<Object> published;
    private OathService service;

    @BeforeEach
    void setUp() {
        oaths = mock(OathRepository.class);
        categories = mock(CategoryProjectionRepository.class);
        published = new ArrayList<>();
        service = new OathService(oaths, categories, published::add);

        when(oaths.resolveIfOpen(any(), anyString(), any(), any())).thenReturn(1);
    }

    private OathEntity openOath(UUID categoryId, long pledgedAmount) {
        return new OathEntity(userId, categoryId, "Food", pledgedAmount, createdAt, expiresAt);
    }

    private void givenOpenOaths(OathEntity... candidates) {
        when(oaths.findByUserIdAndStatusAndCategoryIdInOrderByCreatedAtAsc(eq(userId), eq("OPEN"), any()))
                .thenReturn(List.of(candidates));
    }

    private String resolvedStatus() {
        ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
        verify(oaths).resolveIfOpen(any(), status.capture(), any(), any());
        return status.getValue();
    }

    // ---- create ----

    @Test
    void createStoresAnOpenOathNamedAfterThePledgedCategory() {
        // Given a category the user owns
        when(categories.findById(foodId))
                .thenReturn(Optional.of(new CategoryProjectionEntity(foodId, userId, "Food", null, false)));
        when(oaths.save(any())).thenAnswer(call -> call.getArgument(0));

        // When the user pledges 50 RON against it
        OathEntity oath = service.create(userId, foodId, 5000, expiresAt, createdAt);

        // Then it opens with the category name denormalized for display
        assertThat(oath.getStatus()).isEqualTo("OPEN");
        assertThat(oath.getCategoryName()).isEqualTo("Food");
        assertThat(oath.getPledgedAmount()).isEqualTo(5000);
        assertThat(oath.getResolvedAt()).isNull();
        assertThat(oath.getMatchedExpenseId()).isNull();
    }

    @Test
    void createRejectsANullUserId() {
        assertThatThrownBy(() -> service.create(null, foodId, 5000, expiresAt, createdAt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsANullCategoryId() {
        assertThatThrownBy(() -> service.create(userId, null, 5000, expiresAt, createdAt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsANonPositivePledge() {
        assertThatThrownBy(() -> service.create(userId, foodId, 0, expiresAt, createdAt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsADeadlineThatHasAlreadyPassed() {
        assertThatThrownBy(() -> service.create(userId, foodId, 5000, createdAt, expiresAt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsADeadlineExactlyNow() {
        // Given a window of zero length — a pledge nothing could ever satisfy
        assertThatThrownBy(() -> service.create(userId, foodId, 5000, createdAt, createdAt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsACategoryBelongingToSomebodyElse() {
        // Given the category exists but is owned by another user
        when(categories.findById(foodId)).thenReturn(
                Optional.of(new CategoryProjectionEntity(foodId, UUID.randomUUID(), "Food", null, false)));

        assertThatThrownBy(() -> service.create(userId, foodId, 5000, expiresAt, createdAt))
                .isInstanceOf(NotFoundException.class);
        verify(oaths, never()).save(any());
    }

    @Test
    void createRejectsACategoryTheProjectionHasNeverSeen() {
        assertThatThrownBy(() -> service.create(userId, foodId, 5000, expiresAt, createdAt))
                .isInstanceOf(NotFoundException.class);
    }

    // ---- reconcile: tolerance boundary ----

    @Test
    void anExpenseExactlyTenPercentOverThePledgeIsStillKept() {
        // Given a 100 RON pledge, where 10% (1000 bani) beats the 500-bani floor
        givenOpenOaths(openOath(foodId, 10000));

        // When 110 RON is spent — exactly on the tolerance edge
        service.reconcile(userId, expenseId, foodId, 11000, opened, now);

        // Then the edge is inclusive
        assertThat(resolvedStatus()).isEqualTo("KEPT");
    }

    @Test
    void oneBanOverTheTenPercentBandSlips() {
        givenOpenOaths(openOath(foodId, 10000));

        service.reconcile(userId, expenseId, foodId, 11001, opened, now);

        assertThat(resolvedStatus()).isEqualTo("SLIPPED");
    }

    @Test
    void anExpenseExactlyFiveHundredBaniOverASmallPledgeIsKept() {
        // Given a 10 RON pledge, where 10% is only 100 bani so the 500-bani floor governs
        givenOpenOaths(openOath(foodId, 1000));

        service.reconcile(userId, expenseId, foodId, 1500, opened, now);

        assertThat(resolvedStatus()).isEqualTo("KEPT");
    }

    @Test
    void oneBanOverTheFiveHundredBaniFloorSlips() {
        givenOpenOaths(openOath(foodId, 1000));

        service.reconcile(userId, expenseId, foodId, 1501, opened, now);

        assertThat(resolvedStatus()).isEqualTo("SLIPPED");
    }

    @Test
    void spendingFarUnderThePledgeAlsoSlipsBecauseToleranceIsTwoSided() {
        // Given a 100 RON pledge met by a 50 RON spend — not what was pledged either
        givenOpenOaths(openOath(foodId, 10000));

        service.reconcile(userId, expenseId, foodId, 5000, opened, now);

        assertThat(resolvedStatus()).isEqualTo("SLIPPED");
    }

    @Test
    void spendingUnderThePledgeButWithinToleranceIsKept() {
        givenOpenOaths(openOath(foodId, 10000));

        service.reconcile(userId, expenseId, foodId, 9000, opened, now);

        assertThat(resolvedStatus()).isEqualTo("KEPT");
    }

    // ---- reconcile: parent category ----

    @Test
    void anExpenseInAChildCategoryClosesAPledgeMadeAgainstTheParent() {
        // Given an oath on "Food" and an expense in "Food > Groceries"
        when(categories.findById(groceriesId)).thenReturn(
                Optional.of(new CategoryProjectionEntity(groceriesId, userId, "Groceries", foodId, false)));
        OathEntity foodOath = openOath(foodId, 10000);
        when(oaths.findByUserIdAndStatusAndCategoryIdInOrderByCreatedAtAsc(
                userId, "OPEN", List.of(groceriesId, foodId))).thenReturn(List.of(foodOath));

        // When the groceries expense lands
        service.reconcile(userId, expenseId, groceriesId, 10000, opened, now);

        // Then the parent's oath is the one that closes
        verify(oaths).resolveIfOpen(eq(foodOath.getId()), eq("KEPT"), eq(expenseId), any());
    }

    @Test
    void anExpenseInAnUnrelatedCategoryClosesNothing() {
        // Given the projection knows of no parent, so only an exact match is a candidate
        givenOpenOaths();

        service.reconcile(userId, expenseId, groceriesId, 10000, opened, now);

        verify(oaths, never()).resolveIfOpen(any(), anyString(), any(), any());
        assertThat(published).isEmpty();
    }

    // ---- reconcile: window edges ----

    @Test
    void anExpenseDatedExactlyOnTheDayTheOathOpenedMatches() {
        givenOpenOaths(openOath(foodId, 10000));

        service.reconcile(userId, expenseId, foodId, 10000, opened, now);

        assertThat(resolvedStatus()).isEqualTo("KEPT");
    }

    @Test
    void anExpenseDatedExactlyOnTheDeadlineMatches() {
        givenOpenOaths(openOath(foodId, 10000));

        service.reconcile(userId, expenseId, foodId, 10000, deadline, now);

        assertThat(resolvedStatus()).isEqualTo("KEPT");
    }

    @Test
    void anExpenseDatedTheDayBeforeTheOathOpenedIsIgnored() {
        // Given a spend that predates the pledge — it cannot be what the pledge was about
        givenOpenOaths(openOath(foodId, 10000));

        service.reconcile(userId, expenseId, foodId, 10000, opened.minusDays(1), now);

        verify(oaths, never()).resolveIfOpen(any(), anyString(), any(), any());
    }

    @Test
    void anExpenseDatedAfterTheDeadlineIsIgnored() {
        givenOpenOaths(openOath(foodId, 10000));

        service.reconcile(userId, expenseId, foodId, 10000, deadline.plusDays(1), now);

        verify(oaths, never()).resolveIfOpen(any(), anyString(), any(), any());
    }

    // ---- reconcile: first match wins ----

    @Test
    void onlyTheOldestMatchingOathIsClosedByASingleExpense() {
        // Given two open pledges the same expense would satisfy, oldest first
        OathEntity older = openOath(foodId, 10000);
        OathEntity newer = openOath(foodId, 10000);
        givenOpenOaths(older, newer);

        service.reconcile(userId, expenseId, foodId, 10000, opened, now);

        verify(oaths).resolveIfOpen(eq(older.getId()), anyString(), any(), any());
        verify(oaths, never()).resolveIfOpen(eq(newer.getId()), anyString(), any(), any());
        assertThat(published).hasSize(1);
    }

    @Test
    void anOathOutsideItsWindowIsSkippedInFavourOfTheNextCandidate() {
        // Given the oldest oath's window has nothing to do with this expense date
        OathEntity stale = new OathEntity(userId, foodId, "Food", 10000,
                opened.minusDays(30).atStartOfDay(ZONE).toInstant(),
                opened.minusDays(20).atStartOfDay(ZONE).toInstant());
        OathEntity live = openOath(foodId, 10000);
        givenOpenOaths(stale, live);

        service.reconcile(userId, expenseId, foodId, 10000, opened, now);

        verify(oaths).resolveIfOpen(eq(live.getId()), anyString(), any(), any());
    }

    // ---- reconcile: idempotency ----

    @Test
    void redeliveringAnExpenseThatAlreadyClosedAnOathChangesNothing() {
        // Given this expense id is already recorded against a resolved oath
        when(oaths.existsByMatchedExpenseId(expenseId)).thenReturn(true);
        givenOpenOaths(openOath(foodId, 10000));

        // When the same expense.created arrives a second time
        service.reconcile(userId, expenseId, foodId, 10000, opened, now);

        // Then no second oath is closed and no second notification is sent
        verify(oaths, never()).resolveIfOpen(any(), anyString(), any(), any());
        assertThat(published).isEmpty();
    }

    @Test
    void losingTheRaceToCloseAnOathPublishesNoEvent() {
        // Given a concurrent delivery already flipped the row out of OPEN
        givenOpenOaths(openOath(foodId, 10000));
        when(oaths.resolveIfOpen(any(), anyString(), any(), any())).thenReturn(0);

        service.reconcile(userId, expenseId, foodId, 10000, opened, now);

        assertThat(published).isEmpty();
    }

    @Test
    void reconcileIgnoresAnEventMissingItsIdentifiers() {
        service.reconcile(null, expenseId, foodId, 10000, opened, now);
        service.reconcile(userId, null, foodId, 10000, opened, now);
        service.reconcile(userId, expenseId, null, 10000, opened, now);
        service.reconcile(userId, expenseId, foodId, 10000, null, now);

        verify(oaths, never()).resolveIfOpen(any(), anyString(), any(), any());
        assertThat(published).isEmpty();
    }

    // ---- expiry ----

    @Test
    void anOpenOathPastItsDeadlineBecomesForgone() {
        // Given an open pledge whose window closed with no matching spend
        OathEntity oath = openOath(foodId, 10000);
        when(oaths.findByStatusAndExpiresAtBefore("OPEN", now)).thenReturn(List.of(oath));

        service.expireOpenOaths(now);

        // Then it closes as FORGONE with no matched expense — a distinct outcome, not a failure
        verify(oaths).resolveIfOpen(eq(oath.getId()), eq("FORGONE"), eq(null), eq(now));
        assertThat(published).singleElement().isInstanceOfSatisfying(Events.OathResolved.class,
                event -> assertThat(event.routingKey()).isEqualTo("oath.forgone"));
    }

    @Test
    void theSweepPublishesNothingForAnOathAnotherWorkerAlreadyClosed() {
        when(oaths.findByStatusAndExpiresAtBefore("OPEN", now)).thenReturn(List.of(openOath(foodId, 10000)));
        when(oaths.resolveIfOpen(any(), anyString(), any(), any())).thenReturn(0);

        service.expireOpenOaths(now);

        assertThat(published).isEmpty();
    }

    @Test
    void theSweepIsAQuietNoOpWhenNothingHasExpired() {
        when(oaths.findByStatusAndExpiresAtBefore("OPEN", now)).thenReturn(List.of());

        service.expireOpenOaths(now);

        assertThat(published).isEmpty();
    }

    // ---- outbound events ----

    @Test
    void aKeptOathAnnouncesItselfOnTheOathKeptRoutingKey() {
        OathEntity oath = openOath(foodId, 10000);
        givenOpenOaths(oath);

        service.reconcile(userId, expenseId, foodId, 10000, opened, now);

        assertThat(published).singleElement().isInstanceOfSatisfying(Events.OathResolved.class, event -> {
            assertThat(event.routingKey()).isEqualTo("oath.kept");
            assertThat(event.oathId()).isEqualTo(oath.getId());
            assertThat(event.userId()).isEqualTo(userId);
            assertThat(event.status()).isEqualTo("KEPT");
        });
    }

    @Test
    void aSlippedOathAnnouncesItselfOnTheOathSlippedRoutingKey() {
        givenOpenOaths(openOath(foodId, 10000));

        service.reconcile(userId, expenseId, foodId, 99999, opened, now);

        assertThat(published).singleElement().isInstanceOfSatisfying(Events.OathResolved.class,
                event -> assertThat(event.routingKey()).isEqualTo("oath.slipped"));
    }

    // ---- list and cancel ----

    @Test
    void listWithoutAFilterReturnsEveryOathNewestFirst() {
        OathEntity oath = openOath(foodId, 10000);
        when(oaths.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(oath));

        assertThat(service.list(userId, null)).containsExactly(oath);
        assertThat(service.list(userId, "  ")).containsExactly(oath);
    }

    @Test
    void listNormalisesTheStatusFilterToUpperCase() {
        OathEntity oath = openOath(foodId, 10000);
        when(oaths.findByUserIdAndStatusOrderByCreatedAtDesc(userId, "OPEN")).thenReturn(List.of(oath));

        assertThat(service.list(userId, "open")).containsExactly(oath);
    }

    @Test
    void cancellingWithdrawsAnOpenOathEntirely() {
        OathEntity oath = openOath(foodId, 10000);
        when(oaths.findByIdAndUserId(oath.getId(), userId)).thenReturn(Optional.of(oath));

        service.cancel(oath.getId(), userId);

        verify(oaths).delete(oath);
    }

    @Test
    void cancellingAnAlreadyResolvedOathIsRejected() {
        // Given an oath a spend already closed — its outcome is history, not a draft
        UUID id = UUID.randomUUID();
        OathEntity resolved = mock(OathEntity.class);
        when(resolved.getStatus()).thenReturn("KEPT");
        when(oaths.findByIdAndUserId(id, userId)).thenReturn(Optional.of(resolved));

        assertThatThrownBy(() -> service.cancel(id, userId)).isInstanceOf(NotFoundException.class);
        verify(oaths, never()).delete(any());
    }

    @Test
    void cancellingSomebodyElsesOathIsRejected() {
        UUID id = UUID.randomUUID();
        when(oaths.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(id, userId)).isInstanceOf(NotFoundException.class);
        verify(oaths, never()).delete(any());
    }
}
