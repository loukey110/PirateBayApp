# 海盗湾安卓应用

一个用于搜索The Pirate Bay种子并获取磁力链接的安卓应用。

## 功能特性

- 搜索种子资源
- 分类筛选（视频、音频、应用、游戏等）
- 查看种子详情（大小、种子数、下载者数）
- 一键复制磁力链接
- 分享磁力链接
- 下拉刷新
- 深色主题

## 技术栈

- Kotlin
- Android SDK 34
- OkHttp - 网络请求
- Jsoup - HTML解析
- Coroutines - 异步处理
- ViewBinding - 视图绑定
- Material Design - UI组件

## 项目结构

```
app/
├── src/main/
│   ├── java/com/piratebay/app/
│   │   ├── MainActivity.kt          # 主界面
│   │   ├── model/
│   │   │   └── TorrentItem.kt       # 种子数据模型
│   │   ├── network/
│   │   │   └── TPBScraper.kt        # HTML解析器
│   │   └── adapter/
│   │       └── TorrentAdapter.kt    # RecyclerView适配器
│   ├── res/
│   │   ├── layout/                  # 布局文件
│   │   ├── drawable/                # 图标资源
│   │   └── values/                  # 字符串、颜色、主题
│   └── AndroidManifest.xml
└── build.gradle.kts
```

## 构建说明

### 前置要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 34
- Gradle 8.2

### 构建步骤

1. 克隆或下载项目
2. 用Android Studio打开项目根目录
3. 等待Gradle同步完成
4. 点击 Build > Make Project (或按 Ctrl+F9)
5. 连接安卓设备或启动模拟器
6. 点击 Run > Run 'app' (或按 Shift+F10)

### 手动构建APK

```bash
# Windows
gradlew.bat assembleDebug

# Linux/Mac
./gradlew assembleDebug
```

APK文件将生成在：`app/build/outputs/apk/debug/app-debug.apk`

## 使用方法

1. 打开应用，默认显示热门种子
2. 在搜索框输入关键词
3. 选择分类（可选）
4. 点击搜索按钮
5. 点击列表项查看详情
6. 点击"复制磁力链接"按钮复制链接
7. 在下载器中粘贴使用

## 注意事项

- 需要网络权限
- 建议使用VPN访问
- 仅供学习研究使用
- 请遵守当地法律法规

## 依赖库

- androidx.core:core-ktx:1.12.0
- androidx.appcompat:appcompat:1.6.1
- com.google.android.material:material:1.11.0
- com.squareup.okhttp3:okhttp:4.12.0
- org.jsoup:jsoup:1.17.2
- kotlinx-coroutines-android:1.7.3

## 许可证

本项目仅供学习交流使用。
