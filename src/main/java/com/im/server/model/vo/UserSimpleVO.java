package com.im.server.model.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

/**
 * 用户简要信息 VO。
 * <p>
 * phone 和 email 字段默认脱敏（如 138****1234），
 * 避免通过好友列表等接口泄露联系方式。
 * 需要完整信息请调用 UserService.getSimpleUserFull()。
 * <p>
 * 面试点：敏感信息脱敏。通过框架层统一处理，而非在各 Controller 中
 * 逐个 mask，避免遗漏。Caffeine 缓存中永远只存脱敏版本。
 */
@Data
@Builder
public class UserSimpleVO {

    private Long userId;
    private String nickname;
    private String aliasName;
    private String avatar;
    private String phone;
    private String email;
    private List<Long> tagIds;

    /** 脱敏手机号：保留前 3 位和后 4 位，中间替换为 **** */
    public static String maskPhone(String phone) {
        if (StringUtils.isBlank(phone) || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 脱敏邮箱：保留用户名首字符和域名，如 j***@gmail.com */
    public static String maskEmail(String email) {
        if (StringUtils.isBlank(email) || !email.contains("@")) {
            return email;
        }
        String name = email.substring(0, email.indexOf('@'));
        String domain = email.substring(email.indexOf('@'));
        if (name.length() <= 2) {
            return name.charAt(0) + "***" + domain;
        }
        return name.charAt(0) + "***" + name.charAt(name.length() - 1) + domain;
    }
}
