package com.im.server.controller;

import com.im.server.common.ApiResponse;
import com.im.server.common.CurrentUser;
import com.im.server.model.dto.CreateStickerItemRequest;
import com.im.server.model.dto.CreateStickerPackRequest;
import com.im.server.model.vo.StickerPackDetailVO;
import com.im.server.security.LoginUser;
import com.im.server.service.StickerService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stickers")
@RequiredArgsConstructor
public class StickerController {

    private final StickerService stickerService;

    @GetMapping("/packs")
    public ApiResponse<List<StickerPackDetailVO>> packs() {
        return ApiResponse.success(stickerService.listEnabledPacks());
    }

    @PostMapping("/packs")
    public ApiResponse<StickerPackDetailVO> createPack(@CurrentUser LoginUser loginUser,
                                                       @Valid @RequestBody CreateStickerPackRequest request) {
        assertAdmin(loginUser);
        return ApiResponse.success(stickerService.createPack(request));
    }

    @PostMapping("/items")
    public ApiResponse<Void> createItem(@CurrentUser LoginUser loginUser,
                                        @Valid @RequestBody CreateStickerItemRequest request) {
        assertAdmin(loginUser);
        stickerService.addItem(request);
        return ApiResponse.success("表情已添加", null);
    }

    private void assertAdmin(LoginUser loginUser) {
        if (!loginUser.isAdmin()) {
            throw new com.im.server.common.BusinessException("需要管理员权限");
        }
    }
}
