# TaskFlow - 多态悬浮胶囊任务流转系统

TaskFlow 是一款基于 **Jetpack Compose** 架构开发的跨应用多态悬浮交互系统。项目遵循人机交互（HCI）的“渐进式披露”与“最小认知负荷”原则，旨在解决大学生在移动端进行多任务并行时（如阅读文献时流转课程、查看导航、追踪多任务倒计时）频繁切换应用的痛点。

---

## 🌟 核心设计理念 (HCI Principles)

1. **多态轻量交互（Multi-state Presentation）**
   * **胶囊态 (Capsule)**：常驻于屏幕边缘，仅显示最核心的元数据（如倒计时、关键提醒），对当前主线应用实现“零遮挡”。
   * **展开态 (Expanded)**：点击胶囊后原位平滑放大，提供丰富的上下文信息（如导航路径、一键操作），遵循**渐进式披露原则**。
2. **物理动效与边缘吸附（Physics-based Animation）**
   * 系统采用基于物理特性的弹簧曲线（Spring Animation），手势拖拽反馈自然。
   * 松手后触发智能**边缘吸附（Edge Snapping）**，自动隐藏部分体积，最大化释放屏幕视野。
3. **前台流转引擎（Foreground Flow Engine）**
   * 采用全局 `Service` 配合 `WindowManager` 渲染，脱离单一 App 限制，可在桌面、微信、浏览器等任意第三方应用上方提供跨屏任务流转能力。

---

## 🛠️ 技术栈与依赖 (Tech Stack)

* **核心框架**：Android 13+ (API 33+) & Jetpack Compose
* **开发工具**：Android Studio (Recommended Koala 或更新版本)
* **编程语言**：Kotlin 1.9.22 / 2.0.20
* **构建工具**：Gradle 8.7+ / 9.4.1 (已适配国内腾讯云/阿里云镜像源)
* **核心组件**：
  * `androidx.compose.animation:animation` (控制胶囊弹性形变)
  * `androidx.compose.foundation:foundation` (处理手势拖拽 `detectDragGestures`)
  * `FloatingService` (基于 Android 全局 WindowManager 的前台悬浮窗服务)

---

## 👥 团队分工 (Team Division)

* **成员 A (UI/UX 视觉与场景模拟)**
  * 负责胶囊态和展开态的视觉规范设计（毛玻璃质感、渐变色圆角、无障碍色彩比对）。
  * 负责搭建多场景仿真的主界面（如模拟深度阅读 PDF 场景），用于演示悬浮窗在实际应用中的不干扰表现。
* **成员 B (手势交互与弹簧动效)**
  * 负责实现悬浮胶囊的全屏拖拽手势接收。
  * 负责精调弹簧阻尼比（Damping Ratio = `0.75f`），实现形态切换时的弹性动画。
  * 负责编写松手后的屏幕边缘智能吸附逻辑。
* **成员 C (后台引擎与状态机控制)**
  * 负责编写 `FloatingService`，将内嵌悬浮窗升级为全局跨应用悬浮窗。
  * 负责定义 `WidgetState`（多态状态机），根据虚拟时间引擎自动触发状态流转与前台服务类型声明。

---

## 🚀 快速开始与运行环境配置

由于国内网络环境对 Gradle 核心包及组件依赖下载存在限制，本项目通过自动化国内镜像配置。请按照以下步骤部署：

### 1. 克隆代码与路径配置
将项目默认存储路径统一对齐（例如 `E:\AndroidProjects\HCI_demo`），避免脚本路径冲突。

### 2. 国内环境加速配置 
项目根目录下的配置文件已优化为国内镜像，**请勿轻易修改**：
* **`gradle-wrapper.properties`**：分发包下载地址已重定向至腾讯云镜像，并移除了可能导致报错的 SHA-256 安全验证：
* pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 优先使用国内镜像
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/jcenter") }
        google()
        mavenCentral()
    }
}

rootProject.name = "HCI_demo"
include(":app")

---

## 📁 项目文件结构 (Project Structure)

```
HCI-CapsuleTable/
├── build.gradle.kts              # 根项目构建脚本
├── settings.gradle.kts           # 项目设置（含镜像源配置）
├── gradle.properties             # Gradle 全局属性
├── gradlew / gradlew.bat         # Gradle Wrapper 启动脚本
├── .gitignore                    # Git 忽略规则
│
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties  # Gradle 版本与下载地址（已配置腾讯云镜像）
│
└── app/                          # 主应用模块
    ├── build.gradle.kts          # 模块级构建脚本（依赖声明、编译配置）
    ├── proguard-rules.pro        # ProGuard 混淆规则
    │
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml           # 应用清单（权限声明、组件注册）
        │   │
        │   ├── java/com/example/hci_demo/
        │   │   ├── MainActivity.kt           # 主 Activity（入口、悬浮窗演示场景）
        │   │   │
        │   │   └── ui/theme/
        │   │       ├── Color.kt              # 全局颜色定义（渐变色、毛玻璃色板）
        │   │       ├── Theme.kt              # Material3 主题配置
        │   │       └── Type.kt              # 全局字体排版规范
        │   │
        │   └── res/
        │       ├── drawable/
        │       │   ├── ic_launcher_background.xml   # 启动图标背景
        │       │   └── ic_launcher_foreground.xml   # 启动图标前景
        │       ├── mipmap-anydpi-v26/
        │       │   ├── ic_launcher.xml              # 自适应图标（普通）
        │       │   └── ic_launcher_round.xml        # 自适应图标（圆形）
        │       ├── mipmap-{hdpi,mdpi,xhdpi,xxhdpi,xxxhdpi}/
        │       │   ├── ic_launcher.webp             # 各密度启动图标
        │       │   └── ic_launcher_round.webp       # 各密度圆形图标
        │       ├── values/
        │       │   ├── colors.xml                   # 颜色资源
        │       │   ├── strings.xml                  # 字符串资源
        │       │   └── themes.xml                   # XML 主题资源
        │       └── xml/
        │           ├── backup_rules.xml             # 数据备份规则
        │           └── data_extraction_rules.xml    # 数据提取规则
        │
        ├── test/                 # 本地单元测试
        └── androidTest/          # 设备端仪器化测试
```
