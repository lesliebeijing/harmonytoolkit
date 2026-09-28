# 鸿蒙开发助手 (harmonytoolkit)

Kotlin Multiplatform + Compose Desktop 实现的鸿蒙安装包查看/操作工具（当前为第一阶段骨架）。

## 功能（第一阶段）

- 拖入 `.app` `.hap` `.hsp` `.har` `.tgz` `.abc` `resources.index`，每种包一个 tab（按绝对路径去重）
- app / hsp 发布 tgz 内第一层的 hap / hsp / har 自动递归展开（只展开一层）
- 树形文件列表：名称 / 大小 / 占顶层包百分比；单击选中（右栏看信息），双击查看内容
- 查看器：图片（png/jpg/bmp/webp）、JSON（格式化，含 sourceMaps.map）、文本、resources.index（结构化资源表，RestoolV2）
- abc / so / 未知类型：占位提示，下一阶段接入 ark_disasm 反汇编
- 底部日志栏：每 tab 独立会话日志，实时显示解包过程（后续重打包/重签名日志也走这里）
- 只读查看，不修改任何包内容

## 运行

```bash
./gradlew :desktopApp:run
```

## 工程结构

- `shared/src/jvmMain/kotlin/.../core/` 核心逻辑（纯 Kotlin，可单测）
  - `model/` 包类型、文件树、文件分类注册表
  - `extract/` 解包引擎（zip / tar.gz、zip-slip 防护、按路径+大小+mtime 哈希缓存、嵌套展开）
  - `resource/` resources.index (RestoolV2) 解析器
  - `tools/` SDK 工具定位与子进程执行（流式输出进日志）
  - `log/` 每 tab 会话日志
- `desktopApp/` Compose Desktop UI（tab / 三栏 / 日志 / 各查看器）
- `tools/mac` `tools/windows` 随工程分发的 SDK 工具（hdc、restool、ark_disasm、hap-sign-tool.jar 等）
  - 可用 `-Dharmonytoolkit.tools.dir=<目录>` / 环境变量 `HARMONY_TOOLKIT_TOOLS` / `~/.harmonytoolkit/tools/<平台>/` 覆盖

## 其它说明

- 解包缓存在 `~/.harmonytoolkit/work/`，命中缓存的包再次打开免解包
- 冒烟测试（真实包）：`./gradlew :shared:jvmTest`（样本目录默认 `~/Desktop/测试包`，可用 `-PtestPackages=` 覆盖，目录不存在时自动跳过）

## 路线图

- 第二阶段：abc 反汇编（ark_disasm）、重打包、重签名、hdc 推送
- Windows 平台工具与验证
# harmonytoolkit
