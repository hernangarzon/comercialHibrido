package com.comercialhibrido.service;

import com.comercialhibrido.domain.entity.Conversation;
import com.comercialhibrido.domain.enums.ConversationStatus;
import com.comercialhibrido.domain.enums.MessageSender;
import com.comercialhibrido.repository.ConversationRepository;
import com.comercialhibrido.repository.MessageRepository;
import com.comercialhibrido.repository.MessageStat;
import com.comercialhibrido.repository.ResponseSample;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Métricas comerciales de una empresa para un período, comparadas con el período anterior.
 *
 * Definiciones (las mismas que muestra el panel):
 * - Conversaciones activas: con al menos un mensaje del cliente en el período.
 * - Resueltas por el bot: activas en las que ningún asesor escribió en el período.
 * - Leads calientes: activas con lead score actual >= 70.
 * - Escalaciones: conversaciones cuya última escalación cayó en el período.
 * - Tiempo de respuesta del asesor: mediana entre la escalación y el primer mensaje
 *   de un asesor posterior a ella.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    static final int HOT_LEAD_SCORE = 70;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    @Transactional(readOnly = true)
    public Dashboard calcular(UUID companyId, int days, ZoneId zone) {
        Instant now = Instant.now();
        Instant from = now.minus(Duration.ofDays(days));
        Instant previousFrom = from.minus(Duration.ofDays(days));

        List<Conversation> conversations = conversationRepository.findByCompanyIdOrderByUpdatedAtDesc(companyId);
        Map<UUID, Conversation> byId = new HashMap<>();
        conversations.forEach(c -> byId.put(c.getId(), c));

        List<MessageStat> messages = messageRepository.statsByCompany(companyId, previousFrom, now);
        List<MessageStat> current = messages.stream().filter(m -> !m.createdAt().isBefore(from)).toList();
        List<MessageStat> previous = messages.stream().filter(m -> m.createdAt().isBefore(from)).toList();

        List<ResponseSample> responses = messageRepository.firstAgentReplyAfterEscalation(companyId, previousFrom, now);

        Kpis kpis = kpis(conversations, byId, current, responses, from, now);
        Kpis previousKpis = kpis(conversations, byId, previous, responses, previousFrom, from);

        Map<ConversationStatus, Long> statusNow = new EnumMap<>(ConversationStatus.class);
        for (ConversationStatus s : ConversationStatus.values()) statusNow.put(s, 0L);
        conversations.forEach(c -> statusNow.merge(c.getStatus(), 1L, Long::sum));

        List<TopLead> topLeads = conversations.stream()
            .filter(c -> c.getStatus() != ConversationStatus.ARCHIVADO && c.getLeadScore() != null && c.getLeadScore() > 0)
            .sorted(Comparator.comparing(Conversation::getLeadScore).reversed()
                .thenComparing(Conversation::getUpdatedAt, Comparator.reverseOrder()))
            .limit(5)
            .map(TopLead::from)
            .toList();

        return new Dashboard(days, zone.getId(), kpis, previousKpis, series(current, from, now, zone), statusNow, topLeads);
    }

    private Kpis kpis(List<Conversation> conversations, Map<UUID, Conversation> byId, List<MessageStat> messages,
                      List<ResponseSample> responses, Instant from, Instant to) {
        Set<UUID> active = new HashSet<>();
        Set<UUID> withAgent = new HashSet<>();
        long fromClient = 0, fromBot = 0, fromAgent = 0;
        for (MessageStat m : messages) {
            switch (m.sender()) {
                case CLIENTE -> { active.add(m.conversationId()); fromClient++; }
                case BOT -> fromBot++;
                case COMERCIAL -> { withAgent.add(m.conversationId()); fromAgent++; }
            }
        }

        long newConversations = conversations.stream()
            .filter(c -> !c.getCreatedAt().isBefore(from) && c.getCreatedAt().isBefore(to)).count();
        long resolvedByBot = active.stream().filter(id -> !withAgent.contains(id)).count();
        long hotLeads = active.stream().map(byId::get)
            .filter(c -> c != null && c.getLeadScore() != null && c.getLeadScore() >= HOT_LEAD_SCORE).count();
        long escalations = conversations.stream()
            .filter(c -> c.getEscalatedAt() != null && !c.getEscalatedAt().isBefore(from) && c.getEscalatedAt().isBefore(to))
            .count();

        List<Long> waits = responses.stream()
            .filter(r -> !r.escalatedAt().isBefore(from) && r.escalatedAt().isBefore(to))
            .map(r -> Duration.between(r.escalatedAt(), r.firstReplyAt()).toSeconds())
            .sorted()
            .toList();

        return new Kpis(
            active.size(),
            newConversations,
            active.isEmpty() ? null : (double) resolvedByBot / active.size(),
            hotLeads,
            escalations,
            median(waits),
            waits.size(),
            fromClient,
            fromBot,
            fromAgent
        );
    }

    private static Long median(List<Long> sorted) {
        if (sorted.isEmpty()) return null;
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(mid) : (sorted.get(mid - 1) + sorted.get(mid)) / 2;
    }

    private static List<DayPoint> series(List<MessageStat> messages, Instant from, Instant to, ZoneId zone) {
        Map<LocalDate, long[]> perDay = new LinkedHashMap<>();
        LocalDate first = from.atZone(zone).toLocalDate();
        LocalDate last = to.atZone(zone).toLocalDate();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) perDay.put(d, new long[3]);
        for (MessageStat m : messages) {
            long[] counts = perDay.get(m.createdAt().atZone(zone).toLocalDate());
            if (counts != null) counts[m.sender().ordinal()]++;
        }
        List<DayPoint> points = new ArrayList<>();
        perDay.forEach((day, c) -> points.add(new DayPoint(day,
            c[MessageSender.CLIENTE.ordinal()], c[MessageSender.BOT.ordinal()], c[MessageSender.COMERCIAL.ordinal()])));
        return points;
    }

    public record Dashboard(
        int days,
        String timezone,
        Kpis kpis,
        Kpis previous,
        List<DayPoint> series,
        Map<ConversationStatus, Long> statusNow,
        List<TopLead> topLeads
    ) {}

    public record Kpis(
        long activeConversations,
        long newConversations,
        Double botResolutionRate,
        long hotLeads,
        long escalations,
        Long medianAgentResponseSeconds,
        long agentResponsesMeasured,
        long clientMessages,
        long botMessages,
        long agentMessages
    ) {}

    public record DayPoint(LocalDate date, long client, long bot, long agent) {}

    public record TopLead(UUID id, String customerName, String customerPhone, int leadScore,
                          ConversationStatus status, Instant updatedAt) {
        static TopLead from(Conversation c) {
            return new TopLead(c.getId(), c.getCustomer().getDisplayName(), c.getCustomer().getPhoneNumber(),
                c.getLeadScore(), c.getStatus(), c.getUpdatedAt());
        }
    }
}
