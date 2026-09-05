#!/bin/sh
# 仪器测试分流脚本（CI 专用，POSIX sh，runner 的 /bin/sh 为 dash）。
#
# 为什么存在：reactivecircus/android-emulator-runner@v2 会把 `script` 输入
# 按行拆开、逐行以 `sh -c <行>` 独立执行（见其 src/script-parser.ts）。
# 跨行的 if/then/fi、行尾续符 `\` 都会被拆碎，且上一行设置的变量对下一行
# 不可见——2026-09-04 起 main push 的 instrumented job 连续在脚本解析处
# 失败（dash: "Syntax error: end of file unexpected (expecting fi)"）。
# 因此分流逻辑必须放在真实脚本文件里，工作流里只保留单行调用。
#
# 事件上下文使用 GitHub 默认环境变量（所有 step 子进程均可见）：
#   GITHUB_EVENT_NAME: pull_request | push | ...
#   GITHUB_REF:        refs/heads/main | refs/tags/vX.Y.Z | ...
set -eu

case "$GITHUB_EVENT_NAME" in
  pull_request)
    # PR 只跑 smoke（主界面启动/课表渲染路径，分钟级）快速拦截
    ./gradlew :app:connectedDebugAndroidTest \
      -Pandroid.testInstrumentationRunnerArguments.class=com.polaris.timetable.ui.MainActivitySmokeTest \
      --no-daemon --console=plain
    ;;
  push)
    case "$GITHUB_REF" in
      refs/tags/v*)
        # U-P1-17：标签发布前必须通过更新入口与安装构件的仪器冒烟
        ./gradlew :app:connectedDebugAndroidTest \
          -Pandroid.testInstrumentationRunnerArguments.class=com.polaris.timetable.update.UpdateEntryTest \
          --no-daemon --console=plain
        ./gradlew :app:connectedDebugAndroidTest \
          -Pandroid.testInstrumentationRunnerArguments.class=com.polaris.timetable.update.UpdateInstallerTest \
          --no-daemon --console=plain
        ;;
      *)
        # push main 跑全量（周切换/旋转/字体缩放等）
        ./gradlew :app:connectedDebugAndroidTest --no-daemon --console=plain
        ;;
    esac
    ;;
  *)
    echo "::error::未预期的 GITHUB_EVENT_NAME=$GITHUB_EVENT_NAME，拒绝静默跑空" >&2
    exit 1
    ;;
esac
