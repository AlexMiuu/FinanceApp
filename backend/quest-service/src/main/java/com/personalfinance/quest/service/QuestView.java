package com.personalfinance.quest.service;

import com.personalfinance.quest.entity.QuestEntity;

/**
 * A quest plus its freshly evaluated target/progress. Service-layer only — it
 * carries the entity, so it is the mapper's input, never a response body.
 */
public record QuestView(QuestEntity quest, long target, long progress, String kind) {
}
