# Mise

## Pull request 流程

- 推送分支后创建 PR，CI（`build`）通过后自动合并，不需要再问。
- 优先开 GitHub 自动合并（squash）；PR 已经是 clean 状态、开不了自动合并时，直接 squash 合并。
- CI 失败先修，修好再合；不要跳过、禁用测试来让 CI 变绿。
- 合并后如果还有后续改动，从最新的 `main` 重新拉分支，不要在已合并的分支上继续提交。
