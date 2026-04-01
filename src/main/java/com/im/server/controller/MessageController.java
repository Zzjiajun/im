package com.im.server.controller;

import com.im.server.common.ApiResponse;
import com.im.server.common.CurrentUser;
import com.im.server.model.dto.BatchFavoriteRequest;
import com.im.server.model.dto.BatchForwardMessagesRequest;
import com.im.server.model.dto.DeleteMessagesForSelfRequest;
import com.im.server.model.dto.EditMessageRequest;
import com.im.server.model.dto.FavoriteMessageRequest;
import com.im.server.model.dto.ForwardMessageRequest;
import com.im.server.model.dto.MarkDeliveredRequest;
import com.im.server.model.dto.MarkReadRequest;
import com.im.server.model.dto.MergeForwardMessagesRequest;
import com.im.server.model.dto.PinMessageRequest;
import com.im.server.model.dto.ReactMessageRequest;
import com.im.server.model.dto.RecallMessageRequest;
import com.im.server.model.dto.ReportMessageRequest;
import com.im.server.model.dto.SendMessageRequest;
import com.im.server.model.vo.ChatMessageVO;
import com.im.server.model.vo.FavoriteMessageVO;
import com.im.server.model.vo.MessageReceiptVO;
import com.im.server.model.vo.MessageSearchPageVO;
import com.im.server.security.LoginUser;
import com.im.server.service.MessageService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping("/send")
    public ApiResponse<ChatMessageVO> send(@CurrentUser LoginUser loginUser,
                                           @Valid @RequestBody SendMessageRequest request) {
        return ApiResponse.success(messageService.sendMessage(loginUser.getUserId(), request));
    }

    @GetMapping("/conversation/{conversationId}")
    public ApiResponse<List<ChatMessageVO>> list(@CurrentUser LoginUser loginUser,
                                                 @PathVariable Long conversationId,
                                                 @RequestParam(required = false) Long beforeMessageId,
                                                 @RequestParam(required = false) Long afterMessageId,
                                                 @RequestParam(required = false) Integer size) {
        return ApiResponse.success(messageService.listMessages(loginUser.getUserId(), conversationId, beforeMessageId,
            afterMessageId, size));
    }

    @GetMapping("/search")
    public ApiResponse<MessageSearchPageVO> search(@CurrentUser LoginUser loginUser,
                                                   @RequestParam String keyword,
                                                   @RequestParam(required = false) Long conversationId,
                                                   @RequestParam(required = false) Long beforeMessageId,
                                                   @RequestParam(required = false) Integer size) {
        return ApiResponse.success(messageService.searchMessages(loginUser.getUserId(), keyword, conversationId,
            beforeMessageId, size));
    }

    @PostMapping("/favorite")
    public ApiResponse<Void> favorite(@CurrentUser LoginUser loginUser,
                                      @Valid @RequestBody FavoriteMessageRequest request) {
        messageService.favoriteMessage(loginUser.getUserId(), request);
        return ApiResponse.success("收藏成功", null);
    }

    @PostMapping("/favorite/batch")
    public ApiResponse<Void> batchFavorite(@CurrentUser LoginUser loginUser,
                                           @Valid @RequestBody BatchFavoriteRequest request) {
        messageService.batchFavoriteMessages(loginUser.getUserId(), request);
        return ApiResponse.success("批量收藏已处理", null);
    }

    @DeleteMapping("/favorite/{messageId}")
    public ApiResponse<Void> cancelFavorite(@CurrentUser LoginUser loginUser,
                                            @PathVariable Long messageId) {
        messageService.cancelFavorite(loginUser.getUserId(), messageId);
        return ApiResponse.success("已取消收藏", null);
    }

    @PostMapping("/favorite/update")
    public ApiResponse<Void> updateFavorite(@CurrentUser LoginUser loginUser,
                                            @Valid @RequestBody FavoriteMessageRequest request) {
        messageService.updateFavorite(loginUser.getUserId(), request);
        return ApiResponse.success("收藏信息已更新", null);
    }

    @PostMapping("/forward")
    public ApiResponse<List<ChatMessageVO>> forward(@CurrentUser LoginUser loginUser,
                                                    @Valid @RequestBody ForwardMessageRequest request) {
        return ApiResponse.success(messageService.forwardMessages(loginUser.getUserId(), request));
    }

    @PostMapping("/forward/batch")
    public ApiResponse<List<ChatMessageVO>> batchForward(@CurrentUser LoginUser loginUser,
                                                         @Valid @RequestBody BatchForwardMessagesRequest request) {
        return ApiResponse.success(messageService.batchForwardMessages(loginUser.getUserId(), request));
    }

    @PostMapping("/forward/merge")
    public ApiResponse<List<ChatMessageVO>> mergeForward(@CurrentUser LoginUser loginUser,
                                                         @Valid @RequestBody MergeForwardMessagesRequest request) {
        return ApiResponse.success(messageService.mergeForwardMessages(loginUser.getUserId(), request));
    }

    @PostMapping("/deliver")
    public ApiResponse<Void> deliver(@CurrentUser LoginUser loginUser,
                                     @Valid @RequestBody MarkDeliveredRequest request) {
        messageService.markDelivered(loginUser.getUserId(), request);
        return ApiResponse.success("已回执送达状态", null);
    }

    @PostMapping("/delete-self")
    public ApiResponse<Void> deleteForSelf(@CurrentUser LoginUser loginUser,
                                           @Valid @RequestBody DeleteMessagesForSelfRequest request) {
        messageService.deleteMessagesForSelf(loginUser.getUserId(), request);
        return ApiResponse.success("已删除自己的消息视图", null);
    }

    @GetMapping("/favorites")
    public ApiResponse<List<FavoriteMessageVO>> favorites(@CurrentUser LoginUser loginUser,
                                                          @RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) String categoryName) {
        return ApiResponse.success(messageService.listFavorites(loginUser.getUserId(), keyword, categoryName));
    }

    @PostMapping("/pin")
    public ApiResponse<Void> pin(@CurrentUser LoginUser loginUser,
                                 @Valid @RequestBody PinMessageRequest request) {
        messageService.pinMessage(loginUser.getUserId(), request.getMessageId());
        return ApiResponse.success("已置顶消息", null);
    }

    @PostMapping("/unpin")
    public ApiResponse<Void> unpin(@CurrentUser LoginUser loginUser,
                                   @Valid @RequestBody PinMessageRequest request) {
        messageService.unpinMessage(loginUser.getUserId(), request.getMessageId());
        return ApiResponse.success("已取消置顶消息", null);
    }

    @GetMapping("/pinned")
    public ApiResponse<List<ChatMessageVO>> pinned(@CurrentUser LoginUser loginUser,
                                                   @RequestParam(required = false) Long conversationId) {
        return ApiResponse.success(messageService.listPinnedMessages(loginUser.getUserId(), conversationId));
    }

    @GetMapping("/{messageId}/reads")
    public ApiResponse<List<MessageReceiptVO>> reads(@CurrentUser LoginUser loginUser,
                                                     @PathVariable Long messageId) {
        return ApiResponse.success(messageService.listReadReceipts(loginUser.getUserId(), messageId));
    }

    @GetMapping("/{messageId}/delivers")
    public ApiResponse<List<MessageReceiptVO>> delivers(@CurrentUser LoginUser loginUser,
                                                        @PathVariable Long messageId) {
        return ApiResponse.success(messageService.listDeliverReceipts(loginUser.getUserId(), messageId));
    }

    @PostMapping("/read")
    public ApiResponse<Void> markRead(@CurrentUser LoginUser loginUser,
                                      @Valid @RequestBody MarkReadRequest request) {
        messageService.markRead(loginUser.getUserId(), request);
        return ApiResponse.success("已更新消息已读状态", null);
    }

    @PostMapping("/recall")
    public ApiResponse<Void> recall(@CurrentUser LoginUser loginUser,
                                    @Valid @RequestBody RecallMessageRequest request) {
        messageService.recallMessage(loginUser.getUserId(), request);
        return ApiResponse.success("撤回成功", null);
    }

    @PostMapping("/edit")
    public ApiResponse<ChatMessageVO> edit(@CurrentUser LoginUser loginUser,
                                           @Valid @RequestBody EditMessageRequest request) {
        return ApiResponse.success(messageService.editMessage(loginUser.getUserId(), request));
    }

    @PostMapping("/report")
    public ApiResponse<Void> report(@CurrentUser LoginUser loginUser,
                                    @Valid @RequestBody ReportMessageRequest request) {
        messageService.reportMessage(loginUser.getUserId(), request);
        return ApiResponse.success("举报成功", null);
    }

    @PostMapping("/react")
    public ApiResponse<Void> react(@CurrentUser LoginUser loginUser,
                                   @Valid @RequestBody ReactMessageRequest request) {
        messageService.reactMessage(loginUser.getUserId(), request);
        return ApiResponse.success("已添加消息反应", null);
    }

    @PostMapping("/react/remove")
    public ApiResponse<Void> removeReaction(@CurrentUser LoginUser loginUser,
                                            @Valid @RequestBody ReactMessageRequest request) {
        messageService.removeReaction(loginUser.getUserId(), request);
        return ApiResponse.success("已取消消息反应", null);
    }
}
