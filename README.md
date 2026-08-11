# Polaris课程表 Android

这是一个原生 Android 课程表应用，可以从 PDF 课表中自动提取课程、节次、周次、地点和教师，并以适配手机和平板的横向课程表展示。

## 功能

- 从系统文件选择器导入 PDF 课表
- 从系统文件选择器导入课表图片，使用本地 ML Kit OCR 识别
- 使用 PDF 文字坐标识别星期与节次
- 自动展示 1-11 节、周一到周五课程
- 手机端支持横向滚动，平板端优先铺满屏幕

## 打开方式

1. 用 Android Studio 打开当前文件夹 `D:\Polaris课程表`
2. 等待 Gradle 同步完成
3. 连接手机或启动模拟器
4. 运行 `app`
5. 在应用中选择“导入 PDF”或“导入课表图片”

## 说明

文字型 PDF 优先使用 PDF 坐标解析；截图或拍照可使用本地 OCR 图片导入。真实图片的回归测试方法见 [docs/ocr-real-image-testing.md](docs/ocr-real-image-testing.md)。

## Release 构建

1. 复制 `keystore.properties.example` 为 `keystore.properties`。
2. 填写发布证书路径、密码和别名。该文件与 `*.jks`、`*.keystore` 已被 Git 忽略。
3. 运行 `./gradlew assembleRelease`（Windows 使用 `.\gradlew.bat assembleRelease`）。

CI 也可通过 `POLARIS_RELEASE_STORE_FILE`、`POLARIS_RELEASE_STORE_PASSWORD`、
`POLARIS_RELEASE_KEY_ALIAS`、`POLARIS_RELEASE_KEY_PASSWORD` 四个环境变量提供签名信息。
缺少任意一项时，release 任务会直接失败，不会生成未签名安装包。
