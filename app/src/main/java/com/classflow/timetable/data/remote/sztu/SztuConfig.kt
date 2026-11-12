package com.classflow.timetable.data.remote.sztu

/**
 * 深圳技术大学教务系统的固定地址与常量。
 *
 * 为什么写死：App 只允许访问这三个域名（教务、统一认证、公文通），代码里连"可配置 baseUrl"都不给，
 * 从结构上保证课表数据不会被发到任何第三方服务器。
 */
object SztuConfig {

    const val JWXT_HOST = "jwxt.sztu.edu.cn"
    const val AUTH_HOST = "auth.sztu.edu.cn"

    /** 公文通。只在校内网/VPN 可达（域名解析到 10.x 内网地址），所以列表页要能离线缓存。 */
    const val NBW_HOST = "nbw.sztu.edu.cn"
    const val NBW_LIST = "https://nbw.sztu.edu.cn/list.jsp"

    /** 教务系统入口，未登录会被 302 到统一身份认证。 */
    const val JWXT_ENTRY = "https://jwxt.sztu.edu.cn/"

    /** 统一身份认证的认证链入口（就是浏览器打开教务系统后落到的那一页）。 */
    const val AUTH_CHAIN_ENTRY =
        "https://auth.sztu.edu.cn/idp/authcenter/ActionAuthChain?entityId=jiaowu"

    /** 提交账号密码的接口（页面里 doSubmit() 走的就是这个）。 */
    const val AUTH_CHAIN_POST = "https://auth.sztu.edu.cn/idp/authcenter/ActionAuthChain"

    /** 用户名密码认证引擎，登录成功后由它把票据发回教务系统。 */
    const val AUTH_ENGINE =
        "https://auth.sztu.edu.cn/idp/AuthnEngine" +
            "?currentAuth=urn_oasis_names_tc_SAML_2.0_ac_classes_BAMUsernamePassword"

    /** 图形验证码（和当前会话绑定，必须同一 session 取用）。 */
    const val CAPTCHA = "https://auth.sztu.edu.cn/idp/Kaptcha.jpg"

    /** 学期理论课表。带 ?xnxq01id=2026-2027-1 可指定学期。 */
    const val TIMETABLE = "https://jwxt.sztu.edu.cn/jsxsd/xskb/xskb_list.do"

    /** 教学周历：用来算真正的开学日期（第 1 周的星期一）。注意这条 URL 没有 .do 后缀。 */
    const val CALENDAR = "https://jwxt.sztu.edu.cn/jsxsd/jxzl/jxzl_query"

    /** 学生成绩查询。带 `?kksj=2025-2026-1` 只看一个学期，不带就是教务系统默认的那一档。 */
    const val GRADES = "https://jwxt.sztu.edu.cn/jsxsd/kscj/cjcx_list"

    /**
     * 学生考试查询（考试安排）。
     *
     * 两步：GET [EXAM_QUERY] 拿到查询页（里面是学期下拉框），
     * 再把表单 POST 到 [EXAM_LIST] 换结果表。
     * 不直接把 `?xnxqid=...` 拼在地址上 —— 有的部署只认表单提交。
     */
    const val EXAM_QUERY = "https://jwxt.sztu.edu.cn/jsxsd/xsks/xsksap_query"
    const val EXAM_LIST = "https://jwxt.sztu.edu.cn/jsxsd/xsks/xsksap_list"

    /** 密码用 DES/ECB/PKCS5 加密；前端 JS 里 `encryptByDES` 的 key 原文是 "PassB01Il71"，
     *  但 CryptoJS 的 DES 只吃前 8 字节，所以真正参与运算的就是 "PassB01I"。 */
    const val DES_KEY = "PassB01Il71"
    const val DES_KEY_BYTES = 8

    const val UA =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /** 白名单：所有请求都必须命中这里，否则直接抛异常。 */
    val ALLOWED_HOSTS = setOf(JWXT_HOST, AUTH_HOST, NBW_HOST)
}
