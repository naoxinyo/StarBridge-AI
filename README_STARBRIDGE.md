# StarBridge AI / 星桥 AI（临时名称）

基于 DSHBox v1.5.0 的本地开发分支，原始提交为 `95f4e1688c03ef73d08795ea65504c0b061aa20b`。
沿用原项目的 GPL-3.0 许可和第三方署名。原始项目、授权声明与上游链接保留；新名称和图标不代表原项目官方发行版。

## 这一版做了什么

- 新的星形矢量图标、名称、欢迎文案、深蓝与青绿色外观，默认深色模式，仍可切换主题。
- 设置页增加原生“模型服务”入口，提供 OpenAI、Claude、Kimi、GLM、DeepSeek、通义千问、Gemini、Grok、MiniMax、OpenRouter 与自定义服务配置。
- 服务商配置支持启用/关闭、API 基础地址、API 协议、逐行模型 ID、密钥替换和清除、移除配置。
- OpenAI 使用 `openai-responses`，Claude 使用 `anthropic-messages`，其余按服务商选用兼容协议，复用已随原版发布的 `@deepseek-ai/dsh-llm-pi-ai` 适配器。
- 密钥通过 Android Keystore 的 AES-GCM 在本机加密保存，输入框不使用可持久化的 Compose 状态。配置层只包含环境变量引用，没有密钥；解密仅发生在 DSH 启动时，环境变量通过 `ProcessBuilder` 传递，不拼进命令行。
- 配置保存不自动终止任务；“重启内核并应用配置”有任务中断提示。
- 原版应用的更新检查不再引导安装原项目 APK，DSH 内核的在线与离线更新流程保留。

## 配置与内核之间的接线

`ModelProviderStore` 保存原生配置并生成独立的 Cordis overlay：

```
user-data/.dsh/starbridge/model-providers.yml
```

DSH 通过已有 `--patch` 机制加载该层。此层位于原项目插件管理与安全模式覆盖层之前，后两者仍保持原来的优先级。用户的 `cordis.patch.yml`、内置 DSH 压缩包、Debian、Node、PRoot、浏览器、终端及手机助手运行层不改写。

未填写模型 ID 的 OpenAI、Claude、Kimi、Grok、MiniMax 配置使用随内核发布的模型目录；其余入口需要填写账号实际可用的模型 ID。目录与账号权限并不等价，模型可用性以服务商返回为准。模型目录可能落后于服务商最新发布，可填写模型 ID 覆盖。

## 手机上的使用流程

1. 按原版流程安装 Linux 和 Node 运行环境。
2. 打开“设置 → 模型服务”，填写目标服务商的地址、密钥和必要的模型 ID。
3. 保存配置；结束当前任务后，点击“重启内核并应用配置”。
4. 到聊天页面的模型菜单中选择新服务商的模型。保存服务商配置不会自动切换已有会话的模型。

不同服务商的账号和密钥独立。Kimi、GLM、通义千问等还需注意地域与 Coding 套餐的端点差别；所有基础地址都可编辑。自定义服务当前支持 HTTPS，不支持无 TLS 的本机 Ollama 地址。

## 下载与构建

可安装文件在本仓库的 [Dev 1 Release](https://github.com/naoxinyo/StarBridge-AI/releases/tag/v1.5.0-starbridge.1)。
不填写密钥的测试步骤见 [TESTING_STARBRIDGE.md](TESTING_STARBRIDGE.md)。

自行构建需要 JDK 21、Android SDK API 36 和原项目所需的本机构建工具链。
大体积运行层不进入 Git 历史；可从本版 APK 无修改地恢复到源码目录旁的 `runtime/android-assets`，然后运行 Gradle：

```sh
python tools/restore_starbridge_runtime.py /path/to/starbridge-ai-dev1-release.apk
./gradlew :app:assembleRelease
```

Windows 使用 `gradlew.bat :app:assembleRelease`。本机 SDK 路径通过未跟踪的 `local.properties` 或环境变量配置。
脚本只提取 `assets/runtime/` 和 `assets/dsh/` 内的文件，校验本版 APK SHA-256，拒绝覆盖内容不同的已有文件。
输出为 `app/build/outputs/apk/release/app-release.apk`。未配置发布签名时使用本机调试证书，签名与此处下载的测试 APK 不一定相同。
版本名为 `1.5.0-starbridge.1`，版本号为 10。此开发版仍沿用 `com.dshbox.app` 包名与本地构建签名，尚未改成可以与原版并装的独立包名。

## 验证范围

本阶段不依赖安卓模拟器。完成构建、静态检查、纯 JVM 配置校验和内核 schema 检查后交付 APK；Android Keystore、页面实际交互、真实服务请求、流式输出、图片与工具调用仍待真机验证。不会把编译成功写成运行成功。

此测试版暂不配置模型 API 密钥。源码、测试 APK 和发布附件不包含开发密钥；真实模型请求仍待后续单独验证。

新页面提供中文和英文文案，其他四种原项目语言的新增页面文案暂用英文；原有页面的翻译保留。

## 接口依据

- [OpenAI Responses](https://developers.openai.com/api/reference/resources/responses/methods/create)
- [Claude Messages API](https://platform.claude.com/docs/en/api/messages)
- [Kimi 快速开始](https://platform.kimi.com/docs/get-api-key)
- [智谱官方接口示例](https://docs.bigmodel.cn/cn/best-practice/case/ai-search-engine)
- [通义千问 OpenAI 兼容接口](https://help.aliyun.com/zh/model-studio/compatibility-of-openai-with-dashscope)
- [Gemini OpenAI 兼容接口](https://ai.google.dev/gemini-api/docs/openai)
- [Grok Responses](https://docs.x.ai/developers/model-capabilities/text/generate-text)
- [MiniMax Anthropic SDK](https://platform.minimax.cn/docs/api-reference/text-anthropic-api)
- [OpenRouter](https://openrouter.ai/docs/api_reference/overview)
