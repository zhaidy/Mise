# Mise

## 本地编译

- 云端会话启动时 `.claude/hooks/session-start.sh` 会在后台装好 Android SDK，推送前先在本地跑 `LANG=C.UTF-8 ./gradlew assembleDebug testDebugUnitTest`（测试名有中文，需要 UTF-8 locale）。
- 刚开会话时如果报找不到 SDK 或 `local.properties`，说明后台安装还没跑完，等一会再试。

## Pull request 流程

- 推送分支后创建 PR，CI（`build`）通过后自动合并，不需要再问。
- 优先开 GitHub 自动合并（squash）；PR 已经是 clean 状态、开不了自动合并时，直接 squash 合并。
- CI 失败先修，修好再合；不要跳过、禁用测试来让 CI 变绿。
- 合并后如果还有后续改动，从最新的 `main` 重新拉分支，不要在已合并的分支上继续提交。
