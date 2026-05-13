package com.im.server.service;

import com.im.server.model.vo.ChatMessageVO;
import com.im.server.model.vo.WsEvent;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * 消息异步推送服务。
 * 将 WebSocket 推送、离线推送从事务中分离，异步执行。
 *
 * <p>面试点：
 * <ul>
 *   <li>串行 + 并行双模式：小群串行避免线程开销，大群（>100 人）分批并行扇出</li>
 *   <li>双线程池隔离：{@code messagePushExecutor} 与 {@code parallelBatchExecutor} 独立，
 *       避免 @Async 方法内再向同一池提交任务造成线程池自阻塞</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MessagePushService {

    private static final Logger log = LoggerFactory.getLogger(MessagePushService.class);

    /** 大群阈值：超过此数量的成员启用并行扇出 */
    private static final int LARGE_GROUP_THRESHOLD = 100;

    /** 并行扇出每批大小 */
    private static final int BATCH_SIZE = 20;

    private final WsPushService wsPushService;
    private final UnreadCacheService unreadCacheService;
    private final OfflinePushService offlinePushService;
    private final BlacklistService blacklistService;
    private final NotificationService notificationService;

    @Resource(name = "parallelBatchExecutor")
    private Executor parallelBatchExecutor;

    /**
     * 异步推送消息给会话成员。
     * <ul>
     *   <li>未读数增加</li>
     *   <li>WebSocket 推送</li>
     *   <li>离线推送</li>
     *   <li>@提及通知</li>
     * </ul>
     *
     * <p>对于大群（>100 人），将成员分批提交到 {@code parallelBatchExecutor} 并行执行，
     * 避免串行 O(N) 阻塞。小群保持串行，避免线程池开销。
     */
    @Async("messagePushExecutor")
    public void asyncPushToMembers(Long senderId, ChatMessageVO messageVO,
                                   List<Long> memberIds, boolean isGroup,
                                   List<Long> mentionTargetIds, boolean mentionAll) {
        try {
            if (memberIds.size() > LARGE_GROUP_THRESHOLD) {
                parallelFanout(senderId, messageVO, memberIds, isGroup, mentionTargetIds, mentionAll);
            } else {
                serialPush(senderId, messageVO, memberIds, isGroup, mentionTargetIds, mentionAll);
            }
            log.debug("[AsyncPush] 消息推送完成 conversationId={} members={}",
                    messageVO.getConversationId(), memberIds.size());
        } catch (Exception e) {
            log.warn("[AsyncPush] 消息推送异常 conversationId={} error={}",
                    messageVO.getConversationId(), e.getMessage());
        }
    }

    /**
     * 大群并行扇出：分批提交到独立线程池，避免串行 O(N)。
     * 消息推送和 @提及推送分别分批，两者无先后依赖。
     */
    private void parallelFanout(Long senderId, ChatMessageVO messageVO,
                                List<Long> memberIds, boolean isGroup,
                                List<Long> mentionTargetIds, boolean mentionAll) {
        // 1) 消息推送分批并行
        List<List<Long>> batches = partition(memberIds, BATCH_SIZE);
        for (List<Long> batch : batches) {
            parallelBatchExecutor.execute(() -> {
                for (Long memberId : batch) {
                    if (memberId.equals(senderId)) continue;
                    if (blacklistService.isBlockedEitherWay(senderId, memberId)) continue;
                    pushToMember(memberId, messageVO);
                }
            });
        }
        // 2) @提及推送（大群中可能涉及上百人，也并行）
        List<Long> mentionTargets = resolveMentionTargets(senderId, memberIds, mentionTargetIds, mentionAll);
        if (!mentionTargets.isEmpty()) {
            List<List<Long>> mentionBatches = partition(mentionTargets, BATCH_SIZE);
            for (List<Long> batch : mentionBatches) {
                parallelBatchExecutor.execute(() -> {
                    for (Long targetId : batch) {
                        try {
                            notificationService.notifyMention(
                                targetId, senderId, messageVO.getContent(),
                                messageVO.getConversationId(), messageVO.getId()
                            );
                        } catch (Exception e) {
                            log.warn("[AsyncPush] @提及推送失败 userId={} error={}", targetId, e.getMessage());
                        }
                    }
                });
            }
        }
    }

    /**
     * 小群串行推送。
     */
    private void serialPush(Long senderId, ChatMessageVO messageVO,
                            List<Long> memberIds, boolean isGroup,
                            List<Long> mentionTargetIds, boolean mentionAll) {
        for (Long memberId : memberIds) {
            if (memberId.equals(senderId)) continue;
            if (blacklistService.isBlockedEitherWay(senderId, memberId)) continue;
            pushToMember(memberId, messageVO);
        }
        // @提及推送（小群直接串行）
        pushMentionsSerial(senderId, memberIds, mentionTargetIds, mentionAll, messageVO);
    }

    /**
     * 向单个成员推送（未读数 + WS + 离线）。
     */
    private void pushToMember(Long memberId, ChatMessageVO messageVO) {
        try {
            unreadCacheService.incrementUnread(memberId, messageVO.getConversationId());
            wsPushService.pushToUser(memberId, new WsEvent<>("MESSAGE", messageVO));
            offlinePushService.notifyNewChatMessage(memberId, messageVO);
        } catch (Exception e) {
            log.warn("[AsyncPush] 成员推送失败 userId={} error={}", memberId, e.getMessage());
        }
    }

    /**
     * 解析 @提及目标用户列表。
     */
    private List<Long> resolveMentionTargets(Long senderId, List<Long> memberIds,
                                              List<Long> mentionTargetIds, boolean mentionAll) {
        if (mentionAll) {
            List<Long> targets = new ArrayList<>(memberIds);
            targets.remove(senderId);
            return targets;
        }
        if (mentionTargetIds == null || mentionTargetIds.isEmpty()) {
            return List.of();
        }
        List<Long> targets = new ArrayList<>(mentionTargetIds);
        targets.remove(senderId);
        return targets;
    }

    /**
     * 串行 @提及推送。
     */
    private void pushMentionsSerial(Long senderId, List<Long> memberIds,
                                     List<Long> mentionTargetIds, boolean mentionAll,
                                     ChatMessageVO messageVO) {
        List<Long> targets = resolveMentionTargets(senderId, memberIds, mentionTargetIds, mentionAll);
        for (Long targetId : targets) {
            try {
                notificationService.notifyMention(
                    targetId, senderId, messageVO.getContent(),
                    messageVO.getConversationId(), messageVO.getId()
                );
            } catch (Exception e) {
                log.warn("[AsyncPush] @提及推送失败 userId={} error={}", targetId, e.getMessage());
            }
        }
    }

    /**
     * 将列表切分为固定大小的子列表。
     */
    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }
}
