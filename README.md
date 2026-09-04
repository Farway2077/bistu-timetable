# 纸间课表

一款面向北京信息科技大学学生的本地优先 Android 课表应用。用户在应用内直接登录学校统一认证，课表识别、确认与保存均在手机本地完成；导入后无需联网即可查看。

> [!IMPORTANT]
> 本项目是非官方学生工具，与北京信息科技大学及其教务系统运营方无隶属或合作关系。当前版本为 `0.1.0` MVP，学校页面调整后，自动导入规则可能需要同步更新。

## 功能

- “本周 / 今日”双视图：周一至周日的 1–14 节课表，以及按时间排列的今日课程
- 实时课程状态：正在上课、距下节课时间、今日无课或课程已结束
- 左右滑动或按钮切换教学周，支持分页吸附、边界回弹和一键返回本周
- 自定义“第 1 周周一”，自动重算教学周和每日日期
- 小、标准、大、特大四档课程卡片字号，设置保存在本机
- 解析连续周、离散周、单双周，并提示同一时间段的课程冲突
- 深浅色自适应的低饱和课程配色和课程详情
- 应用内学校统一认证；进入“当前课表”后自动尝试识别，也可手动触发
- 导入前预览课程、周次和异常提示；确认后才替换本学期数据
- Room 本地持久化，识别或导入失败不会清空已有课表

## 使用方法

1. 打开应用，点击“从教务系统导入”或右上角“同步”。
2. 在学校统一认证页面自行完成登录。
3. 进入教务系统的“当前课表”页面，等待七列课表加载完成。
4. 等待应用自动识别，或点击“识别当前页面”。
5. 核对课程数、教室、节次和周次，再点击“导入并替换本学期”。

如果课表页面已经完整显示但仍无法识别，请记录页面标题、课程卡片字段顺序和应用错误提示。请勿提交账号、密码、验证码、Cookie，或含个人信息的完整网页源码。

## 隐私与安全

- 账号、密码和验证码直接提交给学校页面，应用不监听、读取或保存这些内容。
- 登录会话由 Android System WebView 管理；应用不读取、导出或上传 Cookie 内容。
- 课程数据保存在本机 Room 数据库中，不上传到项目自建服务器。
- WebView 只允许 HTTPS 的北信科教务与认证域名，其他域名跳转会被阻止。
- 发布构建关闭 WebView 调试，并禁用明文 HTTP、文件访问和第三方 Cookie。
- 课表数据库和 WebView 偏好已从云备份中排除。

更多设计取舍见 [`docs/adr/0001-local-webview-import.md`](docs/adr/0001-local-webview-import.md)。

## 项目状态与限制

- 目前只适配北京信息科技大学教务系统，最低支持 Android 8.0（API 26）。
- 导入依赖学校登录后的页面与同源数据结构，学校改版可能导致识别失败。
- 解析器、单元测试、Android Lint 和 Debug APK 构建可在本地验证；仍建议在已登录真机上逐项核对实际课表后使用。
- 当前不提供云同步、桌面小组件、作业管理或后台代登录。
- 应用以竖屏课表体验为主。

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 界面 | Kotlin、Jetpack Compose、Material 3、Navigation Compose |
| 状态 | ViewModel、StateFlow、Kotlin Coroutines |
| 数据 | Room / SQLite |
| 导入 | Android WebView、同源结构化数据读取、DOM 七列网格兜底 |
| 测试 | JUnit 4、Android Lint |

## 本地构建

需要安装 Android SDK 34 和 JDK 17 或更高版本。项目已包含 Gradle Wrapper，无需单独安装 Gradle。

Windows：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

macOS / Linux：

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

构建成功后，Debug APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

若 Windows 环境无法写入默认 Gradle 缓存，可改用项目内缓存后重试：

```powershell
$env:GRADLE_USER_HOME = "$PWD\.gradle-user"
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

## 项目结构

```text
app/src/main/java/cn/edu/bistu/kebiao/
├─ data/            Room 数据库与事务化课表导入
├─ domain/          学期、课程、上课安排与周次规则
├─ importer/        域名策略、页面提取与纯 Kotlin 解析器
└─ ui/
   ├─ importer/     安全 WebView、识别状态与导入确认
   └─ timetable/    本周/今日课表、课程详情与显示设置

docs/
├─ adr/             架构决策记录
└─ plans/           设计与实施记录
```

## 参与开发

提交问题时，请附上 Android 版本、System WebView 版本、应用错误提示和已经脱敏的最小页面结构信息。不要在 Issue、日志或截图中公开任何登录凭据、会话信息和个人课表数据。
