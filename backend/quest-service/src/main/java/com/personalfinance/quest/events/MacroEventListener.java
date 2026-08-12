package com.personalfinance.quest.events;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.personalfinance.quest.service.QuestService;

/**
 * Re-shapes quests when the macro calendar turns (F1). CPI prints arrive on the
 * same macro.* binding but are a report-service projection concern — quest
 * templates key off the season alone, so they are acknowledged and dropped here.
 */
@Component
public class MacroEventListener {

    private static final Logger log = LoggerFactory.getLogger(MacroEventListener.class);

    private static final String SEASON_CHANGED = "macro.season.changed";

    private final QuestService questService;

    public MacroEventListener(QuestService questService) {
        this.questService = questService;
    }

    /**
     * Caught, not rethrown: with no DLQ configured, a rejected message would
     * requeue and redeliver immediately (Spring AMQP's default), looping forever
     * on one malformed payload and starving every later season change on this
     * queue. Mirrors {@link UserErasureRequestedListener}.
     */
    @RabbitListener(queues = EventsConfig.MACRO_QUEUE)
    public void onMacroEvent(Events.MacroEvent event, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        if (!SEASON_CHANGED.equals(routingKey)) {
            log.debug("Ignoring {} — quest templates are season-driven", routingKey);
            return;
        }
        try {
            questService.applySeasonChange(event.season(), event.source(), event.asOfDate(), LocalDate.now());
            log.info("Season {} applied — quest templates re-evaluated", event.season());
        } catch (Exception e) {
            log.error("Failed to apply season change {} — quest templates keep the previous season",
                    event.season(), e);
        }
    }
}
