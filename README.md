# 鸿蒙开发助手 (harmonytoolkit)

Kotlin Multiplatform + Compose Desktop 实现的鸿蒙安装包查看/操作工具，支持 macOS（Apple Silicon）与 Windows。

## 功能

### 包查看

- 拖入 `.app` `.hap` `.hsp` `.har` `.tgz` `.abc` `resources.index`，每种包一个 tab（按绝对路径去重）
- app / hsp 发布 tgz 内第一层的 hap / hsp / har 自动递归展开（只展开一层）
- 树形文件列表：名称 / 大小 / 占顶层包百分比；单击选中（右栏看信息），双击查看内容
- 查看器：图片（png/jpg/bmp/webp）、JSON（格式化，含 sourceMaps.map）、文本、resources.index（结构化资源表，RestoolV2）、abc 反汇编（内置 ark_disasm，大文本虚拟滚动展示）
- 右侧信息栏：包基本信息（pack.info 元数据）与签名信息（证书链、指纹等）
- 内容区搜索（Cmd/Ctrl+F）：文本 / abc / resources.index 查看器内查找
- 底部日志栏：每 tab 独立会话日志，实时显示解包、反汇编、重打包、签名过程
- 查看全程只读，不修改源包

### 重打包与签名

- 重打包：把解包缓存目录（可先在外部工具里改完文件）重新打成与源包同格式的包，支持 HAR（tar.gz）与 app/hap/hsp（zip），产物另存为新文件
- 签名：同一套材料（p12 + 密码 + 别名 + cer + p7b）覆盖两类包——HAR 走 SDK 目录签名（sdk-sign-tool），app/hap/hsp 走 hap-sign-tool sign-app
- 签名前材料当场校验：列出 p12 别名与证书摘要、p7b 的 debug/release 与 bundleName
- 非敏感材料字段（路径 / 别名 / 算法）本地记忆，密码仅存在于内存、每次由用户输入
- 签名密码加解密：DevEco 本地加密存储的签名密码（`00000019...` 形式 hex 串）解密查看 / 重新加密（移植自 DevEco passwordtool 并修复其中两处问题）

### 分发

- GitHub Actions 打 tag（`v*`）触发双平台打包发布：macOS dmg（Apple Silicon）+ Windows msi

## 运行

```bash
./gradlew :desktopApp:run
```

## 工程结构

- `shared/src/jvmMain/kotlin/.../core/` 核心逻辑（纯 Kotlin，可单测）
  - `model/` 包类型、文件树、文件分类注册表
  - `extract/` 解包引擎（zip / tar.gz、zip-slip 防护、按路径+大小+mtime 哈希缓存、嵌套展开）
  - `resource/` resources.index (RestoolV2) 解析器
  - `repack/` 重打包（zip / tar.gz）
  - `sign/` 签名执行与材料校验（sdk-sign-tool 隔离类加载 / hap-sign-tool 子进程）
  - `signature/` HAP 签名块解析（证书信息展示）
  - `password/` 签名密码加解密
  - `tools/` SDK 工具定位与子进程执行（流式输出进日志）
  - `log/` 每 tab 会话日志
- `desktopApp/` Compose Desktop UI（tab / 三栏 / 日志 / 各查看器 / 签名与密码工具弹窗）
- `tools/mac` `tools/windows` `tools/lib` 随工程分发的工具（ark_disasm、hap-sign-tool.jar、sdk-sign-tool-full.jar 等）
  - 可用 `-Dharmonytoolkit.tools.dir=<目录>` / 环境变量 `HARMONY_TOOLKIT_TOOLS` / `~/.harmonytoolkit/tools/<平台>/` 覆盖

## 其它说明

- 解包缓存在 `~/.harmonytoolkit/work/`，命中缓存的包再次打开免解包
- 冒烟测试（真实包）：`./gradlew :shared:jvmTest`（样本目录默认 `~/Desktop/测试包`，可用 `-PtestPackages=` 覆盖，目录不存在时自动跳过）
