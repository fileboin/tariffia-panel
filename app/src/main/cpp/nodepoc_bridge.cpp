// Minimal JNI bridge for the fogtape/nodejs-mobile libnode.so (Node 24.21.0).
// Ported from the proven PoC: it sets environment variables, redirects
// stdout/stderr to files, chdir()s to a writable directory and runs
// node::Start() on a dedicated thread.

#include <jni.h>

#include <android/log.h>

#include <fcntl.h>
#include <unistd.h>

#include <memory>
#include <string>
#include <thread>
#include <vector>

#include <node.h>
#include <node_version.h>

#define LOG_TAG "TariffiaRouterRuntime"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

bool g_started = false;

std::string jstr(JNIEnv* env, jstring s) {
    if (s == nullptr) return std::string();
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out(c != nullptr ? c : "");
    if (c != nullptr) env->ReleaseStringUTFChars(s, c);
    return out;
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_tariffia_panel_data_router_RouterRuntime_nativeStart(
    JNIEnv* env, jobject /*thiz*/,
    jstring jWorkDir, jstring jScriptPath,
    jstring jStdoutPath, jstring jStderrPath,
    jobjectArray jEnvPairs, jobjectArray jNodeArgs) {

    if (g_started) {
        LOGE("node already started in this process (single instance only)");
        return JNI_FALSE;
    }
    g_started = true;

    const std::string workDir = jstr(env, jWorkDir);
    const std::string scriptPath = jstr(env, jScriptPath);
    const std::string stdoutPath = jstr(env, jStdoutPath);
    const std::string stderrPath = jstr(env, jStderrPath);

    // Environment must be set before node bootstraps.
    const jsize np = env->GetArrayLength(jEnvPairs);
    for (jsize i = 0; i + 1 < np; i += 2) {
        auto* k = static_cast<jstring>(env->GetObjectArrayElement(jEnvPairs, i));
        auto* v = static_cast<jstring>(env->GetObjectArrayElement(jEnvPairs, i + 1));
        const std::string ks = jstr(env, k);
        const std::string vs = jstr(env, v);
        setenv(ks.c_str(), vs.c_str(), 1);
        env->DeleteLocalRef(k);
        env->DeleteLocalRef(v);
    }

    // Redirect node's stdout/stderr into files so the UI can show them.
    const int ofd = open(stdoutPath.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if (ofd >= 0) {
        dup2(ofd, STDOUT_FILENO);
        dup2(ofd, STDERR_FILENO);
        close(ofd);
    } else {
        LOGE("could not open stdout file: %s", stdoutPath.c_str());
    }
    (void)stderrPath;

    if (!workDir.empty() && chdir(workDir.c_str()) != 0) {
        LOGE("chdir failed: %s", workDir.c_str());
    }

    // Build argv = ["node", <node args...>] on the heap so it outlives this call.
    auto argvStore = std::make_shared<std::vector<std::string>>();
    argvStore->push_back("node");
    const jsize na = env->GetArrayLength(jNodeArgs);
    for (jsize i = 0; i < na; i++) {
        auto* a = static_cast<jstring>(env->GetObjectArrayElement(jNodeArgs, i));
        argvStore->push_back(jstr(env, a));
        env->DeleteLocalRef(a);
    }
    auto argvPtrs = std::make_shared<std::vector<char*>>();
    for (auto& s : *argvStore) argvPtrs->push_back(const_cast<char*>(s.c_str()));

    LOGI("node::Start argc=%d script=%s", static_cast<int>(argvPtrs->size()), scriptPath.c_str());

    std::thread([argvPtrs]() {
        const int code = node::Start(static_cast<int>(argvPtrs->size()), argvPtrs->data());
        LOGI("node::Start returned %d", code);
    }).detach();

    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_tariffia_panel_data_router_RouterRuntime_nativeNodeVersion(JNIEnv* env, jobject /*thiz*/) {
#ifdef NODE_VERSION_STRING
    return env->NewStringUTF(NODE_VERSION_STRING);
#else
    return env->NewStringUTF("unknown");
#endif
}
