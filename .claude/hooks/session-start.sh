#!/bin/bash
# 云端会话启动时装好 Android SDK，让 Claude 能在本地编译和跑单元测试
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

# 后台运行，会话不用等安装完才启动；刚开会话就编译的话，可能要等它跑完
echo '{"async": true, "asyncTimeout": 600000}'

cd "$CLAUDE_PROJECT_DIR"

# 测试名有中文，默认 POSIX locale 下 Kotlin 编译器写不出这些 class 文件
export LANG=C.UTF-8 LC_ALL=C.UTF-8

SDK="$HOME/android-sdk"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-16111833_latest.zip"

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  tmp=$(mktemp -d)
  curl -sSfL "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP" -o "$tmp/tools.zip"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  mkdir -p "$SDK/cmdline-tools"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
fi

# SDK 平台和 build-tools 由 Gradle 按需下载，这里只需接受许可
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --licenses > /dev/null || true
"$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" "platform-tools" > /dev/null

echo "sdk.dir=$SDK" > local.properties

# 云端出口 IP 共享，Maven Central 经常返回 429；本机 Gradle 改走 Google 的镜像（不影响 CI）
mkdir -p "$HOME/.gradle/init.d"
cat > "$HOME/.gradle/init.d/maven-central-mirror.gradle" <<'GRADLE'
def mirror = 'https://maven-central.storage-download.googleapis.com/maven2/'
def useMirror = { RepositoryHandler repos ->
    repos.configureEach { repo ->
        if (repo instanceof MavenArtifactRepository && repo.url.toString().startsWith('https://repo.maven.apache.org/maven2')) {
            repo.url = mirror
        }
    }
}
beforeSettings { settings ->
    useMirror(settings.pluginManagement.repositories)
    useMirror(settings.dependencyResolutionManagement.repositories)
}
allprojects { useMirror(repositories) }
GRADLE
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  {
    echo "export ANDROID_HOME=$SDK"
    echo "export LANG=C.UTF-8 LC_ALL=C.UTF-8"
  } >> "$CLAUDE_ENV_FILE"
fi

# Firebase 配置不进仓库：有 GOOGLE_SERVICES_JSON 就用它，否则写一个占位文件，只为能编译
if [ ! -f app/google-services.json ]; then
  if [ -n "${GOOGLE_SERVICES_JSON:-}" ]; then
    printf '%s' "$GOOGLE_SERVICES_JSON" > app/google-services.json
  else
    cat > app/google-services.json <<'JSON'
{
  "project_info": {
    "project_number": "000000000000",
    "project_id": "mise-local-placeholder",
    "storage_bucket": "mise-local-placeholder.appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:000000000000:android:0000000000000000",
        "android_client_info": { "package_name": "com.personal.mise" }
      },
      "oauth_client": [],
      "api_key": [{ "current_key": "placeholder" }],
      "services": { "appinvite_service": { "other_platform_oauth_client": [] } }
    }
  ],
  "configuration_version": "1"
}
JSON
  fi
fi

# 预热：下载 Gradle、依赖和 SDK 平台，容器缓存后编译更快。
# Maven Central 偶尔返回 429，重试几次；还是失败也不挡住会话启动
for delay in 10 30 60 0; do
  if ./gradlew --quiet compileDebugKotlin > /dev/null; then
    break
  fi
  if [ "$delay" -eq 0 ]; then
    echo "Gradle 预热失败，之后编译时会再下载依赖" >&2
    break
  fi
  sleep "$delay"
done
