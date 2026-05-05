# 🎲 3D 物理骰子 (Dice3D)

一款基于 Android 平台的 3D 物理骰子模拟应用，采用真实物理引擎驱动骰子运动，为用户提供高度拟真、可交互的投掷体验。

## ✨ 功能特性

### 🎯 物理引擎与运动模拟
- **真实物理驱动**：基于自定义刚体物理引擎，重力、碰撞、弹跳及旋转均符合物理规律
- **全可视面追踪**：45° 俯视视角，可清晰观察骰子每个面的运动轨迹与朝向变化
- **完全随机性**：每次投掷轨迹与落点为真随机结果
- **连续投掷机制**：骰子在空中时点击按钮可叠加速度，实现空中加速
- **多次落地弹跳**：精确模拟动能衰减过程，直至骰子完全稳定
- **结果判定**：所有骰子完全静止后才计算并显示点数总和

### 👆 交互方式
- **按钮投掷**：「🔀 掷骰子」按钮，单次点击触发
- **陀螺仪感应**：检测手机甩动手势触发投掷，含防误触机制

### 🎥 3D 视角系统
- **自由视角**：单指旋转、双指缩放、双指平移
- **一键回正**：快捷按钮平滑恢复至默认 45° 俯视角度
- **视角边界限制**：防止穿模或过度偏离

### ⚙️ 骰子自定义
- **数量配置**：1-10 颗骰子同时投掷
- **面数配置**：D4 / D6 / D8 / D10 / D12 / D20 / D100

### 🔧 高级设置
- **骰子外观**：自定义主体颜色，数字颜色自动适配（对比色算法）
- **总和显示**：可选显示/隐藏点数总和
- **音效开关**：投掷、碰撞、落地音效
- **触觉反馈**：碰撞时震动，强度与碰撞力度正相关
- **模拟速度**：0.1× ~ 5.0× 可调，默认 1.0×
- **深色模式**：跟随系统 / 浅色 / 深色三种选项，含平滑过渡动画

### 📊 数据与记录
- **投掷历史**：自动保存时间戳、配置、各骰子点数及总和
- **统计分布**：按骰子类型生成点数分布柱状图

## 🛠️ 技术栈

| 组件 | 技术 |
|------|------|
| UI 框架 | Jetpack Compose + Material Design 3 |
| 3D 渲染 | OpenGL ES 2.0 |
| 物理引擎 | 自定义刚体动力学引擎 |
| 数据持久化 | Room + DataStore Preferences |
| 架构 | MVVM (ViewModel + Repository) |
| 导航 | Navigation Compose |
| 构建 | Gradle Kotlin DSL |
| CI/CD | GitHub Actions |

## 📱 系统要求

- **最低 SDK**: Android 7.0 (API 24)
- **目标 SDK**: Android 16 (API 36)
- **编译 SDK**: Android 16 (API 36)

## 🏗️ 项目结构

```
app/src/main/java/com/dice3d/app/
├── data/                    # 数据层
│   ├── DiceType.kt         # 骰子类型枚举
│   ├── RollResult.kt       # 投掷结果实体
│   ├── HistoryDao.kt       # Room DAO
│   ├── HistoryDatabase.kt  # Room 数据库
│   ├── HistoryRepository.kt# 历史记录仓库
│   └── SettingsRepository.kt# 设置仓库 (DataStore)
├── engine/                  # 3D 引擎层
│   ├── DiceMeshGenerator.kt# 骰子网格生成器
│   ├── PhysicsWorld.kt     # 物理引擎 (刚体/碰撞/弹跳)
│   ├── GLRenderer.kt       # OpenGL ES 渲染器
│   └── CameraController.kt # 相机控制系统
├── sensor/                  # 传感器层
│   └── GyroThrowDetector.kt# 陀螺仪投掷检测
├── audio/                   # 音频层
│   ├── DiceAudioManager.kt # 音效管理
│   └── HapticManager.kt    # 触觉反馈管理
├── viewmodel/               # ViewModel 层
│   ├── DiceViewModel.kt    # 主界面 ViewModel
│   ├── SettingsViewModel.kt# 设置 ViewModel
│   └── HistoryViewModel.kt # 历史 ViewModel
├── ui/                      # UI 层
│   ├── theme/              # Material 3 主题
│   ├── screens/            # 页面 (Dice/Settings/History)
│   └── components/         # 组件 (GLSurfaceView)
├── DiceApplication.kt      # Application
└── MainActivity.kt         # 主 Activity
```

## 🔨 构建与运行

1. 克隆仓库：
   ```bash
   git clone https://github.com/YOUR_USERNAME/Dice3D.git
   cd Dice3D
   ```

2. 使用 Android Studio 打开项目，或使用命令行构建：
   ```bash
   ./gradlew assembleDebug
   ```

3. 安装到设备：
   ```bash
   ./gradlew installDebug
   ```

## 📦 下载

从 [Releases](../../releases) 页面下载最新版 APK。

## 📄 许可证

MIT License
