<!-- 一次 PR 只做一个明确任务（解析 / UI / 存储 / 导出分开），遵循 AGENTS.md -->

## 目标

<!-- 这个 PR 要解决什么问题、为什么现在做 -->

## 修改内容

- 涉及文件与关键改动点：

## 自测

- [ ] `.\gradlew :app:testDebugUnitTest` 通过
- [ ] `.\gradlew :app:lintDebug` 0 error（新增 lint error 会阻塞合并）
- [ ] 涉及 UI / 解析行为的改动已在模拟器或真机验证
- [ ] 若构建交付 APK：versionName / versionCode 已按映射递增

## 风险与影响面

<!-- 可能影响的流程、兼容性、回退方式 -->
