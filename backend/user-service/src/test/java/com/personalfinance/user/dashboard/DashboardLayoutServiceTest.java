package com.personalfinance.user.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalfinance.user.dto.DashboardLayoutDto;
import com.personalfinance.user.dto.DashboardLayoutRequestDto;
import com.personalfinance.user.entity.DashboardLayoutEntity;
import com.personalfinance.user.mapper.DashboardLayoutMapper;
import com.personalfinance.user.repository.DashboardLayoutRepository;
import com.personalfinance.user.service.DashboardLayoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class DashboardLayoutServiceTest {

    private static final List<String> DEFAULT_MAIN = List.of("breakdown");
    private static final List<String> DEFAULT_SIDE = List.of("savings", "streak", "quests");

    private DashboardLayoutRepository layouts;
    private DashboardLayoutService service;

    @BeforeEach
    void setUp() {
        layouts = mock(DashboardLayoutRepository.class);
        service = new DashboardLayoutService(layouts, new DashboardLayoutMapper(new ObjectMapper()));
    }

    @Test
    void aUserWhoNeverRearrangedAnythingGetsTheDefaultArrangement() {
        UUID userId = UUID.randomUUID();
        when(layouts.findById(userId)).thenReturn(Optional.empty());

        DashboardLayoutDto layout = service.layoutFor(userId);

        assertThat(layout.getMain()).isEqualTo(DEFAULT_MAIN);
        assertThat(layout.getSide()).isEqualTo(DEFAULT_SIDE);
        assertThat(layout.getUpdatedAt()).isNull();
    }

    @Test
    void aSavedArrangementIsReadBackInTheOrderItWasStored() {
        UUID userId = UUID.randomUUID();
        when(layouts.findById(userId)).thenReturn(Optional.of(new DashboardLayoutEntity(userId,
                "{\"main\":[\"breakdown\"],\"side\":[\"quests\",\"savings\",\"streak\"]}")));

        DashboardLayoutDto layout = service.layoutFor(userId);

        assertThat(layout.getMain()).containsExactly("breakdown");
        assertThat(layout.getSide()).containsExactly("quests", "savings", "streak");
        assertThat(layout.getUpdatedAt()).isNotNull();
    }

    @Test
    void aWidgetMissingFromAStoredLayoutComesBackInItsDefaultColumn() {
        UUID userId = UUID.randomUUID();
        when(layouts.findById(userId)).thenReturn(Optional.of(new DashboardLayoutEntity(userId,
                "{\"main\":[],\"side\":[\"quests\"]}")));

        DashboardLayoutDto layout = service.layoutFor(userId);

        assertThat(layout.getMain()).containsExactly("breakdown");
        assertThat(layout.getSide()).containsExactly("quests", "savings", "streak");
    }

    @Test
    void aWidgetThisVersionNoLongerKnowsIsDroppedOnRead() {
        UUID userId = UUID.randomUUID();
        // "balance" (the Balance carried forward hero) was retired from the
        // arrangeable catalogue — a layout saved before that change should read
        // back with it silently dropped, exactly like any other retired widget.
        when(layouts.findById(userId)).thenReturn(Optional.of(new DashboardLayoutEntity(userId,
                "{\"main\":[\"balance\",\"retired-widget\",\"breakdown\"],"
                        + "\"side\":[\"savings\",\"streak\",\"quests\"]}")));

        DashboardLayoutDto layout = service.layoutFor(userId);

        assertThat(layout.getMain()).containsExactly("breakdown");
        assertThat(layout.getSide()).containsExactly("savings", "streak", "quests");
    }

    @Test
    void anUnreadableStoredDocumentStillRendersTheDefaultDashboard() {
        UUID userId = UUID.randomUUID();
        when(layouts.findById(userId))
                .thenReturn(Optional.of(new DashboardLayoutEntity(userId, "not json at all")));

        DashboardLayoutDto layout = service.layoutFor(userId);

        assertThat(layout.getMain()).isEqualTo(DEFAULT_MAIN);
        assertThat(layout.getSide()).isEqualTo(DEFAULT_SIDE);
    }

    @Test
    void readingRejectsAMissingPrincipal() {
        assertThatThrownBy(() -> service.layoutFor(null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    void savingAnArrangementPersistsItAgainstTheUser() {
        UUID userId = UUID.randomUUID();
        when(layouts.findById(userId)).thenReturn(Optional.empty());
        when(layouts.save(any(DashboardLayoutEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DashboardLayoutDto saved = service.saveLayout(userId, new DashboardLayoutRequestDto(
                List.of("breakdown"), List.of("quests", "streak", "savings")));

        assertThat(saved.getMain()).containsExactly("breakdown");
        assertThat(saved.getUpdatedAt()).isNotNull();

        ArgumentCaptor<DashboardLayoutEntity> persisted =
                ArgumentCaptor.forClass(DashboardLayoutEntity.class);
        verify(layouts).save(persisted.capture());
        assertThat(persisted.getValue().getUserId()).isEqualTo(userId);
        assertThat(persisted.getValue().getLayout())
                .isEqualTo("{\"main\":[\"breakdown\"],"
                        + "\"side\":[\"quests\",\"streak\",\"savings\"]}");
    }

    @Test
    void savingTwiceReplacesTheExistingRowRatherThanAddingAnother() {
        UUID userId = UUID.randomUUID();
        DashboardLayoutEntity existing = new DashboardLayoutEntity(userId,
                "{\"main\":[\"breakdown\"],\"side\":[\"savings\",\"streak\",\"quests\"]}");
        when(layouts.findById(userId)).thenReturn(Optional.of(existing));
        when(layouts.save(any(DashboardLayoutEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.saveLayout(userId, new DashboardLayoutRequestDto(
                List.of("breakdown"), List.of("quests", "streak", "savings")));

        ArgumentCaptor<DashboardLayoutEntity> saved =
                ArgumentCaptor.forClass(DashboardLayoutEntity.class);
        verify(layouts).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(existing);
        assertThat(existing.getLayout()).contains("\"quests\",\"streak\",\"savings\"");
    }

    @Test
    void savingRejectsALayoutThatWouldHideAWidget() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> service.saveLayout(userId,
                new DashboardLayoutRequestDto(List.of("breakdown"), List.of("savings"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("every known widget exactly once");

        verify(layouts, never()).save(any());
    }

    @Test
    void savingRejectsADuplicatedWidget() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> service.saveLayout(userId, new DashboardLayoutRequestDto(
                List.of("breakdown", "breakdown"), List.of("savings", "streak", "quests"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cannot appear twice");

        verify(layouts, never()).save(any());
    }

    @Test
    void savingRejectsAWidgetThisServiceDoesNotKnow() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> service.saveLayout(userId, new DashboardLayoutRequestDto(
                List.of("breakdown", "rm -rf"), List.of("savings", "streak", "quests"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("every known widget exactly once");

        verify(layouts, never()).save(any());
    }

    @Test
    void savingRejectsANullColumn() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> service.saveLayout(userId,
                new DashboardLayoutRequestDto(null, DEFAULT_SIDE)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Both dashboard columns are required");
    }

    @Test
    void savingRejectsAMissingBody() {
        assertThatThrownBy(() -> service.saveLayout(UUID.randomUUID(), null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Request body is required");
    }

    @Test
    void savingRejectsAMissingPrincipal() {
        assertThatThrownBy(() -> service.saveLayout(null,
                new DashboardLayoutRequestDto(DEFAULT_MAIN, DEFAULT_SIDE)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Authentication required");
    }
}
