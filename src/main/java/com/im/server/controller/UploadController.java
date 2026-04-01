package com.im.server.controller;

import com.im.server.common.ApiResponse;
import com.im.server.common.CurrentUser;
import com.im.server.model.entity.MediaFile;
import com.im.server.security.LoginUser;
import com.im.server.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    @PostMapping("/media")
    public ApiResponse<MediaFile> upload(@CurrentUser LoginUser loginUser,
                                         @RequestParam("file") MultipartFile file,
                                         @RequestParam(required = false) String mediaType,
                                         @RequestParam(required = false) Integer width,
                                         @RequestParam(required = false) Integer height,
                                         @RequestParam(required = false) Integer durationSeconds,
                                         @RequestParam(required = false) String coverUrl) throws Exception {
        return ApiResponse.success(uploadService.upload(
            loginUser.getUserId(),
            file,
            mediaType,
            width,
            height,
            durationSeconds,
            coverUrl
        ));
    }
}
