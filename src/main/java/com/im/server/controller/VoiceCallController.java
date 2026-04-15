package com.im.server.controller;

import com.im.server.common.ApiResponse;
import com.im.server.common.CurrentUser;
import com.im.server.model.dto.StartVoiceCallRequest;
import com.im.server.model.vo.AgoraRtcTokenVO;
import com.im.server.model.vo.VoiceCallVO;
import com.im.server.security.LoginUser;
import com.im.server.service.VoiceCallService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calls/voice")
@RequiredArgsConstructor
public class VoiceCallController {

    private final VoiceCallService voiceCallService;

    @GetMapping("/current")
    public ApiResponse<VoiceCallVO> current(@CurrentUser LoginUser loginUser) {
        return ApiResponse.success(voiceCallService.current(loginUser.getUserId()));
    }

    @PostMapping("/start")
    public ApiResponse<VoiceCallVO> start(@CurrentUser LoginUser loginUser,
                                          @Valid @RequestBody StartVoiceCallRequest request) {
        return ApiResponse.success(voiceCallService.start(loginUser.getUserId(), request.getConversationId()));
    }

    @PostMapping("/{callId}/accept")
    public ApiResponse<VoiceCallVO> accept(@CurrentUser LoginUser loginUser,
                                           @PathVariable String callId) {
        return ApiResponse.success(voiceCallService.accept(loginUser.getUserId(), callId));
    }

    @PostMapping("/{callId}/reject")
    public ApiResponse<VoiceCallVO> reject(@CurrentUser LoginUser loginUser,
                                           @PathVariable String callId) {
        return ApiResponse.success(voiceCallService.reject(loginUser.getUserId(), callId));
    }

    @PostMapping("/{callId}/end")
    public ApiResponse<VoiceCallVO> end(@CurrentUser LoginUser loginUser,
                                        @PathVariable String callId) {
        return ApiResponse.success(voiceCallService.end(loginUser.getUserId(), callId));
    }

    @GetMapping("/{callId}/agora-token")
    public ApiResponse<AgoraRtcTokenVO> agoraToken(@CurrentUser LoginUser loginUser,
                                                   @PathVariable String callId) {
        return ApiResponse.success(voiceCallService.token(loginUser.getUserId(), callId));
    }
}
