package com.im.server.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PublicAuthConfigVO {

    private boolean verifyOnRegister;
    /** 已配置 SMTP（spring.mail.host）时可发邮件验证码 */
    private boolean emailDeliveryAvailable;
    /** true：手机号验证码仍为日志桩，未接真实短信网关 */
    private boolean smsStubMode;
    /** true：允许使用手机号注册/登录 */
    private boolean phoneAuthEnabled;
}
