# Android 原生协议移植笔记

2026-10-25。依据本地 `ios/` 源码读取；对应上游固定提交 `5a87225190d6a79b1e3ad3b7d846edd407d416c7`。下文是**源码协议映射**，未使用真实百度账号做任何写操作，不代表这些非官方接口已在 Android 或当前服务端验收通过。

## 最小必要链路

当前 `submitContent` 只分流到 **web 新主题 / web 回复**；复杂的 `TiebaPostingBootstrap`、Sofire zID、`addPost` Protobuf 构造器目前没有进入实际提交调用链。不要为了支持当前文字发帖/回复先引入整套 Sofire 加密；也不要在最终 web POST 之后自动转投 Protobuf 端点。[入口：TiebaContentSubmissionAPI.swift:903–914](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaContentSubmissionAPI.swift#L903-L914)

当前写入前的最小 bootstrap 是**验证账号凭据并严格刷新 posting TBS**：POST `https://tiebac.baidu.com/c/s/login`，form 只有 `_client_version=22.5.1.0`、`bdusstoken=<BDUSS>`，再加客户端 form 签名；UA 为 `tieba/22.5.1.0 skin/default`。成功后必须取得非空 `anti.tbs`。这里的 `bdusstoken` **没有末尾竖线**，与普通账号 login 的 `<BDUSS>|` 不同；不能混用二者。[常量:525–529、刷新:916–968](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaContentSubmissionAPI.swift#L916-L968)、[普通 login:51–79](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaAPI.swift#L51-L79)。

## 公共请求规则

- **签名的客户端 form**：将尚未 URL 编码且不含 `sign` 的 `key=value` 字符串按字典序排序，无分隔符拼接，再拼公开协议常量 `tiebaclient!!!`，对 UTF-8 做 MD5，输出**大写**十六进制；加入 `sign` 后再做 `application/x-www-form-urlencoded` 编码。不是 HMAC，不先 percent-encode。`postForm` 不会自动补任何 common 字段，表中列出的字段即调用端实际提供的字段。[TiebaHTTPClient.swift:68–96、339–355](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaHTTPClient.swift#L339-L355)。
- web 发帖/回复最终 POST 没有上述客户端 `sign`，依靠新鲜 TBS 和 Cookie；不要擅自混入客户端 common 字段。UTF-8 form 编码必须保护正文中的 `&`、`+`、`=`，GET query 中字面 `+` 要编码为 `%2B`。[form 构造](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaHTTPClient.swift#L19-L34)、[web 调用](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaContentSubmissionAPI.swift#L996-L1090)。
- 客户端社交 UA 均为 `tieba/22.5.1.0`。本地品牌 TiebaPure 与用于兼容服务端的客户端协议版本是两个概念，不应把本地版本号替入上述协议版本。[TiebaRequestBuilder.swift:5–9](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaRequestBuilder.swift#L5-L9)。
- 以下客户端路径均为 HTTPS。`tiebac.baidu.com` 与 `c.tieba.baidu.com` 不是可任意替换的别名；路径映射的真实 host 以 endpoint 为准。[TiebaEndpoint.swift](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaEndpoint.swift#L4-L7)。

## 用户/贴吧关注与列表

下表除明确 GET 行外均为 POST signed form，响应 JSON；签名算法见上文。`fresh tbs` 对应 `refreshedClientTBS`，不是发布专用的严格刷新函数。

| 功能 | HTTPS host + path | form/query 字段 | 响应/边界 |
| --- | --- | --- | --- |
| 关注用户 | `tiebac.baidu.com/c/c/user/follow` | `BDUSS`, `portrait=<目标portrait>`, `tbs` | portrait 去空白后不可空，不能用 uid 替代 |
| 取消关注用户 | `tiebac.baidu.com/c/c/user/unfollow` | 同上 | 与关注同一 DTO |
| 用户关注列表 | `tiebac.baidu.com/c/u/follow/followList` | `BDUSS=<账号值或空串>`, `_client_version=22.5.1.0`, `uid=<被查看用户ID>`, `pn=<正页码>` | `follow_list[]`, `pn`, `total_follow_num`, `has_more` |
| 用户粉丝列表 | `tiebac.baidu.com/c/u/fans/page` | 同关注列表 | `user_list[]`, `page.current_page/total_count/has_more` |
| 贴吧关注状态 | `tiebac.baidu.com/c/f/forum/getUserForumLevelInfo` | `BDUSS`, `_client_version=22.5.1.0`, `forum_id=<fid>`, `friend_portrait=<当前账号portrait>` | 成功必须存在 `data.user_forum_info.is_follow` |
| 关注贴吧 | `tiebac.baidu.com/c/c/forum/like` | `BDUSS`, `fid`, `tbs` | `fid>0`，fresh tbs 非空 |
| 取消关注贴吧 | `tiebac.baidu.com/c/c/forum/unfavolike` | 同上 | 注意准确拼写为 **unfavolike** |
| 吧名解析 ID（GET、不签名） | `c.tieba.baidu.com/f/commit/share/fnameShareApi` | query `fname=<吧名>`, `ie=utf-8` | 状态 `no`，消息 `error`，ID `data.fid>0`；不要改为 www host 后跟随降级 HTTP |

来源：[endpoint:102–120](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaEndpoint.swift#L102-L120)、[字段:26–64](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaSocialAPI.swift#L26-L64)、[关注用户:74–93、317–333](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaUserProfileAPI.swift#L317-L333)、[执行:481–599](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaSocialAPI.swift#L481-L599)。

用户列表元素是 `id/name/name_show/portrait`；展示名前可做 fallback，但关注操作需要保留原 portrait token（iOS 去掉其 `?` 后缀）。列表 `uid>0`、`pn` 必须是正且可表示为 Int32 的值，不能靠负数或溢出后重试。[列表 DTO:139–241](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaSocialAPI.swift#L139-L241)。

社交 TBS 刷新顺序是普通 client login → webMyInfo 的 `data.tbs/itb_tbs` → 仅在允许时用已存 TBS；web 明确 `is_login=false` 时视为会话失效，不继续用旧 TBS。`strictlyRefreshedClientTBS` 禁止最后的 stored fallback。发帖/回复使用前述独立的 `strictlyRefreshedPostingTBS`，也没有 stored fallback。[TiebaSocialAPI.swift:333–429](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaSocialAPI.swift#L333-L429)。

社交变更成功不能只看 HTTP 200：要求顶层 `error_code` 可解析且为 0，并检查嵌套 `error.errno`，它非 0 时仍失败。消息分别为 `error_msg` / `error.errmsg`；不能把缺失状态码默认为成功。[TiebaMutationResponseDTO:95–136](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaSocialAPI.swift#L95-L136)。

## 个人资料与用户主题

**读取个人资料**：POST `https://tiebac.baidu.com/c/u/user/profile?cmd=303012&format=protobuf`，multipart 文件字段 `data`，filename `file`，MIME `application/octet-stream`，文件内容为 `tiebapure.profile.UserProfileRequest`；header `X-BD-DATA-TYPE: protobuf`，此次 multipart **不额外附 stoken 字段**，认证值在 common 中。[请求:215–244](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaUserProfileAPI.swift#L215-L244)、[multipart:142–158](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaRequestBuilder.swift#L142-L158)。

根 request `data=1`。内部字段映射：`uid=1` 是已登录当前用户 ID（若有）；查看他人时优先 `friend_uid=3`，没有其 ID 时用 `friend_uid_portrait=16`；`need_post_count=2 → 1`，`is_guest=4 → 本人0/他人1`，`pn=6 → 1`，`rn=7 → 20`，`has_plist=8 → 1`，`common=9`，`scr_w=10`、`scr_h=11`，`q_type=12 → 0`，`scr_dip=13`（double），`is_from_usercenter=14 → 1`，`page=15 → 1`。须保留 optional 的 presence 区别，不能重新编号。[factory:12–46](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaUserProfileAPI.swift#L12-L46)、[schema](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/Protos/TiebaPureProfile/UserProfile.proto#L13-L42)。

响应根 `error=1`、`data=2`，`data.user=1`。需显式存在 data/user，不能空对象显示成功。资料映射包含 `has_concerned`、`concern_num`、`fans_num`、`thread_num`、`display_intro/intro`、`like_forum`、`priv_sets.like` 等。用户设定的隐私与本机屏蔽列表必须分开：不要把本机过滤后空列表误判为“对方私密”。[mapper:3–61](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Domain/Mapping/UserProfileMapper.swift#L3-L61)。

**用户主题**：POST `https://tiebac.baidu.com/c/u/feed/userpost?cmd=303002&format=protobuf`，同类 multipart，额外 `stoken` 字段；请求根 `data=1`，内部 `uid=1`、`rn=2 → 20`、`is_thread=4 → 1`、`need_content=5 → 1`、`pn=26`、`common=27`、`scr_w=29`、`scr_h=30`、`scr_dip=31`、`q_type=32 → 1`、`is_view_card=33 → 1`。响应 `error=1/data=2`，`data.post_list=1`、`hide_post=2`。分页依据**原始服务端列表非空且 hide_post=0**，不能因为本地过滤后列表为空就停止翻页。[factory:49–71](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaUserProfileAPI.swift#L49-L71)、[schema:44–95](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/Protos/TiebaPureProfile/UserProfile.proto#L44-L95)、[分页:64–90](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Domain/Mapping/UserProfileMapper.swift#L64-L90)。

**编辑本人资料**：signed form POST `https://tiebac.baidu.com/c/c/profile/modify`，字段 `BDUSS`、`intro`、trim 后非空的 `nick_name`，可选 `sex=1/2`；本地“未设置”时**省略 sex**，因为发 0 可能被服务端改成男。源码该端点没有 tbs 字段，不应擅加。响应需要汇总根/data/error 中的状态别名 `result/error_code/err_code/errno/error_no/no`，所有已出现状态须是严格整数且相同，成功值为 0（这里不同于 web 发帖的 result=1）。最终 POST 后解析/网络/取消异常标记结果未知，禁止自动重试。[字段:95–113](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaUserProfileAPI.swift#L95-L113)、[执行和严格解码:274–286、336–450](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaUserProfileAPI.swift#L336-L450)。

## Web 发主题、回复与图片

**统一认证头**：先验证凭据。Cookie 仅 `BDUSS=<...>; STOKEN=<...>`，不把 BAIDUID 加入写请求。另有 `Origin: https://tieba.baidu.com`、`X-Requested-With: XMLHttpRequest`、`Accept: application/json, text/plain, */*`、中文语言、no-cache、移动 Safari UA（源码常量）。header 与 Cookie 中不拼接未经验证的输入。[headers:772–830](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaContentSubmissionAPI.swift#L772-L830)。

**新主题**：POST `https://tieba.baidu.com/f/commit/thread/add`，form 字段：

| 字段 | 值 |
| --- | --- |
| `ie` | `utf-8` |
| `fid` / `kw` | 正贴吧 ID / 吧名 |
| `tbs` | 严格刷新所得 |
| `title` / `content` | 标题 / 正文 |
| `nick_name` | 当前账号 displayName，空则 name |
| `bsk` | 毫秒时间戳 |

Referer 为 `https://tieba.baidu.com/f?kw=<编码吧名>`。此路径按当前 iOS 策略**只允许文字和贴吧表情标记**，不支持附图的新主题；不要声称已支持图文主题。[fields:661–686](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaContentSubmissionAPI.swift#L661-L686)。

**回复**：POST `https://tieba.baidu.com/mo/q/apubpost?_t=<毫秒时间戳>`，query 与 body 用同一时间戳。Referer 为 `https://tieba.baidu.com/p/<tid>?lp=5028&mo_device=1&is_jingpost=0&pn=1&`。基础 form：`co=<正文>`、`_t=<同时间戳>`、`tag=11`、`upload_img_info=<上传返回串或空>`、`fid=<fid>`、`src=1`、`word=<吧名>`、`tbs=<strict fresh>`、`z=<tid>`、`lp=6026`、`nick_name=<账号显示名>`、`_BSK=<同时间戳>`。回复帖子不带 pid；回复楼层额外 `pid=<父楼层postID>`、`floor=max(1,parentFloor或1)`；回复楼中楼再加 `lzl_id=<目标subpostID>`。楼中楼正文前缀为 `回复 <显示名> : <正文>`；显示名片段须去空白、最多512 UTF-8字节且无逗号、右括号或ASCII换行控制符，否则不加前缀。不能把帖子ID、楼层postID、楼中楼ID混为一值。[fields:688–744](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaContentSubmissionAPI.swift#L688-L744)、[正文前缀:871–892](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaContentSubmissionAPI.swift#L871-L892)。

**回复图片**：先逐张 POST `https://tieba.baidu.com/mo/q/cooluploadpic?type=ajax&r=<timestamp>_<index>`，同 web headers，form `pic=<原始图像字节的base64>`。每张先检查真实图像元数据，不能信用户提供的 MIME/尺寸。收集每张响应的 `image_info` 或 `imageInfo`，用 `|` 连接成 `upload_img_info`；全部上传完成后才严格刷新 TBS 并发最终回复。该字段最大 `9*16384` UTF-8字节且拒绝ASCII控制换行。[upload:1093–1130](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaContentSubmissionAPI.swift#L1093-L1130)、[校验:704–708](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaContentSubmissionAPI.swift#L704-L708)。

web 上传的响应允许 `error_code/err_code` 都缺失（但不能存在而不可解析/相互冲突），已知非0必须失败；最终还必须得到非空、≤16384 UTF-8字节且无ASCII控制换行的 image_info。它不同于最终发帖/回复的成功规则。[validatedImageInfo:293–334](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaContentSubmissionAPI.swift#L293-L334)。

本地提交策略：标题非空且最多64个用户感知字符（不是 UTF-16 code unit）；正文最多10000字符，trim后正文/图片至少一个；最多9图，单图≤10MiB、单边≤20000、像素总数≤80000000；发送前重新检查实际数据。新主题禁附图；目标 fid/吧名、tid/pid/lzl_id 的要求按 kind 区分。[ContentSubmissionPolicy:210–299](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Domain/Models/ContentSubmission.swift#L210-L299)。

## 成功、风控与结果未知：不能删减的行为

1. **会话失效**：110001/110002/110003/110004 明确失效；代码4只有消息匹配未登录/登录失效等语义才认定失效，不能把任何 code=4 都强制清号。[TiebaAPI.swift:975–1007](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaAPI.swift#L975-L1007)。
2. **web 提交状态**：根与 data 中 `result` 成功为1，`error_code/err_code` 成功为0；回复还检查 `no/errno/error_no`。存在的状态必须是整数JSON token或整数字符串，拒绝布尔、浮点token（含1.0）和冲突别名。缺失状态不是成功；若 result 与错误码相互矛盾则结果未知。[严格类型:1290–1348](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaContentSubmissionAPI.swift#L1290-L1348)、[thread判定:205–271](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaContentSubmissionAPI.swift#L205-L271)。
3. **风控先于成功收据**：扫描根/data 以及各自 info/anti 中 `need_vcode/vcode_md5/vcode_prev_type/vcode_type/pass_token/vcode_pic_url/vcode_extra/anti_stat/block_content` 等。`need_vcode` 非空且不属于0/false/no/off/none即要求验证；非空验证md5、pass_token、图片URL、anti_stat.vcode_stat、block_content或验证码/安全验证/captcha/verify消息也要求验证。不同位置的冲突 token 不能任取一个；记录冲突并停止。即使状态为0也不应跳过挑战。[TiebaSubmissionChallenge.swift:3–234](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaSubmissionChallenge.swift#L3-L234)。
4. **收据**：新主题必须得到且只得到一个正 Int64 tid；pid可选但多个返回必须一致。回复可以没有服务端 tid，使用目标 tid，但若返回 tid 则须与目标一致；pid/post_id 可选且必须互相一致。不要拿“收到了一个JSON”当成功。[thread:256–270](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaContentSubmissionAPI.swift#L256-L270)、[reply:466–520](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaContentSubmissionAPI.swift#L466-L520)。
5. **不可自动重试**：最终 POST 发出后，断网、取消、非2xx或解码失败都不能证明服务端未写入，必须提示“结果未知，请刷新后确认”，保留草稿，不自动重发、切换后备接口或静默删除草稿。预检/图片上传失败与最终写入未知是两类状态。[submitWebThread:1012–1031、submitWebReply:1072–1090](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaContentSubmissionAPI.swift#L1012-L1031)。
6. **账号隔离**：网络工作绑定发起账号及generation；账号切换/登出取消旧任务，并避免旧结果写回新账号UI/草稿。凭据只保存在安全存储，不记录日志、崩溃信息、导出备份。Cookie值必须非空、≤4096 UTF-8字节、全为可见ASCII且不含分号，拒绝 CR/LF；不把用户输入直接拼进 Cookie。[AuthSession.swift:17–44](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Auth/AuthSession.swift#L17-L44)、[本地工作区约定](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/AGENTS.md)。
7. **网络边界**：HTTPS且受信host、无userinfo、正常TLS校验；POST不跟随重定向，尤其不得向HTTP或第三方带出Cookie。一般API响应上限16MiB，实际流式读取也限长，不能只检查Content-Length；图像/媒体保持各自MIME与尺寸校验。挑战URL仅接受HTTPS、无userinfo且为baidu.com/bdimg.com/bdstatic.com及其子域，不把任意返回URL交给带Cookie的WebView。[TiebaHTTPClient.swift:5–8、168–224、283–313](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaHTTPClient.swift#L283-L313)、[challengeURL:335–348](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaSubmissionChallenge.swift#L335-L348)。

## 非当前必需的 app Protobuf bootstrap

保留源码研究入口，暂不以它替代现用web链路：`TiebaPostingBootstrap.bootstrap` 从安全持久的随机seed推导 cuidGalaxy2/c3AID，随后并发调用 `https://tiebac.baidu.com/c/s/sync` 与 `https://sofire.baidu.com/c/11/z/100/...`，获取 clientID/sampleID/zID；sync 用 **小写MD5**签名（与一般form大写不同）。sync响应≤1MiB、zID≤64KiB，全部禁止重定向；AES/RC442、压缩及opaque16-byte后缀处理必须按原实现及独立fixture移植，不能删掉校验来得到看似成功响应。若未来实际采用该端点，再完整移植并测试，不用新生成假zID代替。[TiebaPostingBootstrap.swift:241–343、389–502、696–701](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaPostingBootstrap.swift#L312-L343)。

## 适合 Android 端的最小验证清单

- 签名独立fixture覆盖中文、`+`、`&`、空值与排序；Protobuf用手写wire fixture覆盖tag/presence，不能只自编码再自解码。
- mutation解析覆盖缺失状态、布尔/浮点状态、冲突状态/ID、嵌套错误、code0但挑战、未知写入不可重试。
- WebView Cookie注入/读取只在受信HTTPS域进行；Cookie不含CR/LF/分号，POST拒绝重定向，读取限长；账号切换后旧请求不更新新账号。
- 未经明确授权，不为“验收”实际发帖、回复、关注、改资料；模拟fixture测试、编译成功与真实账号写入验收分别记录。

本笔记未运行Android代码或真实账号请求，未证明非官方服务端协议当前稳定；所有账户写操作仍需实际验收与风控处理。
