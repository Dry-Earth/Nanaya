/*
 * Host-side PTY for the container terminal.
 *
 * The app process is not a terminal, so `droidspaces enter` used to start with
 * a 0x0 window and only pipes on stdin/stdout. Here we allocate a real host PTY,
 * make the child a session leader with that PTY as its controlling terminal,
 * and hand the master fd back to Java. The app then resizes it with TIOCSWINSZ
 * and the guest PTY follows through droidspaces' SIGWINCH relay.
 */

#include <jni.h>
#include <errno.h>
#include <limits.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

static int set_window_size(int fd, int cols, int rows) {
    if (fd < 0 || cols <= 0 || rows <= 0) {
        return -1;
    }
    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_col = (unsigned short) cols;
    ws.ws_row = (unsigned short) rows;
    return ioctl(fd, TIOCSWINSZ, &ws);
}

/*
 * Returns [masterFd, childPid], or null on failure. The child execs
 * suPath -c command with the PTY slave as stdin/stdout/stderr.
 */
JNIEXPORT jintArray JNICALL
Java_io_github_dryearth_Nanaya_core_PtyBridge_nativeOpen(
        JNIEnv *env, jobject thiz, jstring suPath, jstring command,
        jint cols, jint rows) {
    (void) thiz;
    if (suPath == NULL || command == NULL) {
        return NULL;
    }

    const char *su_chars = (*env)->GetStringUTFChars(env, suPath, NULL);
    const char *cmd_chars = (*env)->GetStringUTFChars(env, command, NULL);
    if (su_chars == NULL || cmd_chars == NULL) {
        if (su_chars != NULL) (*env)->ReleaseStringUTFChars(env, suPath, su_chars);
        if (cmd_chars != NULL) (*env)->ReleaseStringUTFChars(env, command, cmd_chars);
        return NULL;
    }

    /* Copy before fork: no allocator work in the child before exec. */
    char su[PATH_MAX];
    char cmd[4096];
    snprintf(su, sizeof(su), "%s", su_chars);
    snprintf(cmd, sizeof(cmd), "%s", cmd_chars);
    (*env)->ReleaseStringUTFChars(env, suPath, su_chars);
    (*env)->ReleaseStringUTFChars(env, command, cmd_chars);

    int master = posix_openpt(O_RDWR | O_NOCTTY | O_CLOEXEC);
    if (master < 0) {
        return NULL;
    }
    /* devpts normally already grants the slave to the opener; best effort. */
    (void) grantpt(master);
    if (unlockpt(master) < 0) {
        close(master);
        return NULL;
    }

    const char *slave_name = ptsname(master);
    if (slave_name == NULL) {
        close(master);
        return NULL;
    }
    char slave_path[PATH_MAX];
    snprintf(slave_path, sizeof(slave_path), "%s", slave_name);

    set_window_size(master, (int) cols, (int) rows);

    pid_t child = fork();
    if (child < 0) {
        close(master);
        return NULL;
    }

    if (child == 0) {
        setsid();
        int slave = open(slave_path, O_RDWR);
        if (slave < 0) {
            _exit(126);
        }
        (void) ioctl(slave, TIOCSCTTY, 0);
        if (dup2(slave, STDIN_FILENO) < 0 ||
            dup2(slave, STDOUT_FILENO) < 0 ||
            dup2(slave, STDERR_FILENO) < 0) {
            _exit(126);
        }
        if (slave > STDERR_FILENO) {
            close(slave);
        }
        close(master);
        execl(su, su, "-c", cmd, (char *) NULL);
        _exit(127);
    }

    /* Never leak the master into other subprocesses spawned by the app. */
    int flags = fcntl(master, F_GETFD);
    if (flags >= 0) {
        (void) fcntl(master, F_SETFD, flags | FD_CLOEXEC);
    }

    jint values[2];
    values[0] = (jint) master;
    values[1] = (jint) child;
    jintArray result = (*env)->NewIntArray(env, 2);
    if (result == NULL) {
        close(master);
        (void) kill(child, SIGKILL);
        return NULL;
    }
    (*env)->SetIntArrayRegion(env, result, 0, 2, values);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_io_github_dryearth_Nanaya_core_PtyBridge_nativeResize(
        JNIEnv *env, jobject thiz, jint fd, jint cols, jint rows) {
    (void) env;
    (void) thiz;
    return set_window_size((int) fd, (int) cols, (int) rows) == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_io_github_dryearth_Nanaya_core_PtyBridge_nativeWait(
        JNIEnv *env, jobject thiz, jint pid) {
    (void) env;
    (void) thiz;
    if (pid <= 0) {
        return -1;
    }
    int status = 0;
    if (waitpid((pid_t) pid, &status, 0) < 0) {
        return -1;
    }
    return status;
}

JNIEXPORT jint JNICALL
Java_io_github_dryearth_Nanaya_core_PtyBridge_nativeSignal(
        JNIEnv *env, jobject thiz, jint pid, jint signal_number) {
    (void) env;
    (void) thiz;
    if (pid <= 0) {
        return -1;
    }
    return kill((pid_t) pid, (int) signal_number);
}
