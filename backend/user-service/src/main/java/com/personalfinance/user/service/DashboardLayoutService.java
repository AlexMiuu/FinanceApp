package com.personalfinance.user.service;

import static com.personalfinance.user.service.RequestGuards.requireBody;
import static com.personalfinance.user.service.RequestGuards.requireUser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.personalfinance.user.dto.DashboardLayoutDto;
import com.personalfinance.user.dto.DashboardLayoutRequestDto;
import com.personalfinance.user.entity.DashboardLayoutEntity;
import com.personalfinance.user.mapper.DashboardLayoutMapper;
import com.personalfinance.user.repository.DashboardLayoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The per-user dashboard widget arrangement. Lives here rather than in the
 * browser because it is a user choice nothing else can regenerate (D9), which
 * also makes it a data class the GDPR export has to carry.
 */
@Service
@RequiredArgsConstructor
public class DashboardLayoutService {

    // "balance" (the Balance carried forward hero) was retired from the arrangeable
    // catalogue — it's now a fixed section above the grid, not a widget a user can
    // move. reconcile() drops it from any layout saved before this change.
    private static final String WIDGET_BREAKDOWN = "breakdown";
    private static final String WIDGET_SAVINGS = "savings";
    private static final String WIDGET_STREAK = "streak";
    private static final String WIDGET_QUESTS = "quests";

    /** The arrangement a user sees before they have ever rearranged anything. */
    private static final List<String> DEFAULT_MAIN = List.of(WIDGET_BREAKDOWN);
    private static final List<String> DEFAULT_SIDE =
            List.of(WIDGET_SAVINGS, WIDGET_STREAK, WIDGET_QUESTS);

    private static final Set<String> KNOWN_WIDGETS = Set.of(
            WIDGET_BREAKDOWN, WIDGET_SAVINGS, WIDGET_STREAK, WIDGET_QUESTS);

    private final DashboardLayoutRepository layouts;
    private final DashboardLayoutMapper dashboardLayoutMapper;

    @Transactional(readOnly = true)
    public DashboardLayoutDto layoutFor(UUID userId) {
        requireUser(userId);

        return layouts.findById(userId)
                .map(entity -> dashboardLayoutMapper.toDto(
                        reconcile(dashboardLayoutMapper.fromJson(entity.getLayout())),
                        entity.getUpdatedAt()))
                .orElseGet(() -> dashboardLayoutMapper.toDto(defaultLayout(), null));
    }

    @Transactional
    public DashboardLayoutDto saveLayout(UUID userId, DashboardLayoutRequestDto request) {
        requireUser(userId);
        requireBody(request);
        requireEveryWidgetPlacedOnce(request);

        String json = dashboardLayoutMapper.toJson(request);
        DashboardLayoutEntity entity = layouts.findById(userId)
                .map(existing -> {
                    existing.replaceLayout(json);
                    return existing;
                })
                .orElseGet(() -> new DashboardLayoutEntity(userId, json));

        return dashboardLayoutMapper.toDto(request, layouts.save(entity).getUpdatedAt());
    }

    private static DashboardLayoutRequestDto defaultLayout() {
        return new DashboardLayoutRequestDto(DEFAULT_MAIN, DEFAULT_SIDE);
    }

    /**
     * A saved layout must be a permutation of the whole widget catalogue. Accepting
     * a partial one would let a client silently drop a widget from the dashboard
     * with no way for the user to get it back.
     */
    private static void requireEveryWidgetPlacedOnce(DashboardLayoutRequestDto request) {
        if (request.getMain() == null || request.getSide() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Both dashboard columns are required");
        }

        List<String> placed = new ArrayList<>(request.getMain());
        placed.addAll(request.getSide());

        Set<String> distinct = new LinkedHashSet<>(placed);
        if (distinct.size() != placed.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A widget cannot appear twice in a dashboard layout");
        }
        if (!distinct.equals(KNOWN_WIDGETS)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A dashboard layout must place every known widget exactly once");
        }
    }

    /**
     * Repairs a stored layout against the current widget catalogue: unknown ids are
     * dropped and widgets added since the layout was saved fall back to their default
     * column. Without this, shipping a new widget would hide it from everyone who had
     * already rearranged their dashboard.
     */
    private static DashboardLayoutRequestDto reconcile(DashboardLayoutRequestDto stored) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> main = keepKnown(stored.getMain(), seen);
        List<String> side = keepKnown(stored.getSide(), seen);

        DEFAULT_MAIN.stream().filter(widget -> !seen.contains(widget)).forEach(main::add);
        DEFAULT_SIDE.stream().filter(widget -> !seen.contains(widget)).forEach(side::add);

        return new DashboardLayoutRequestDto(List.copyOf(main), List.copyOf(side));
    }

    private static List<String> keepKnown(List<String> column, Set<String> seen) {
        List<String> kept = new ArrayList<>();
        if (column == null) {
            return kept;
        }
        for (String widget : column) {
            if (KNOWN_WIDGETS.contains(widget) && seen.add(widget)) {
                kept.add(widget);
            }
        }
        return kept;
    }
}
