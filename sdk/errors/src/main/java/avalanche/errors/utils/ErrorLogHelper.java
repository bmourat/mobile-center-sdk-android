package avalanche.errors.utils;

import android.content.Context;
import android.os.Process;
import android.support.annotation.NonNull;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import avalanche.core.Constants;
import avalanche.core.utils.StorageHelper;
import avalanche.core.utils.UUIDUtils;
import avalanche.errors.ingestion.models.JavaErrorLog;
import avalanche.errors.ingestion.models.JavaException;
import avalanche.errors.ingestion.models.JavaStackFrame;
import avalanche.errors.ingestion.models.JavaThread;

/**
 * ErrorLogHelper to help constructing, serializing, and de-serializing locally stored error logs.
 */
public final class ErrorLogHelper {

    private static final String ERROR_DIRECTORY = "error";

    @NonNull
    public static JavaErrorLog createErrorLog(@NonNull Context context, @NonNull final Thread thread, @NonNull final Throwable exception, @NonNull final Map<Thread, StackTraceElement[]> allStackTraces, final long initializeTimestamp) {

        /* Build error log with a unique identifier. */
        JavaErrorLog errorLog = new JavaErrorLog();
        errorLog.setId(UUIDUtils.randomUUID());

        /* Set absolute current time. Will be correlated to session and converted to relative later. */
        errorLog.setToffset(System.currentTimeMillis());

        /* Process information. Parent one is not available on Android. */
        errorLog.setProcessId(Process.myPid());
//        FIXME ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
//        for (ActivityManager.RunningAppProcessInfo info : activityManager.getRunningAppProcesses())
//            if (info.pid == Process.myPid())
//                errorLog.setProcessName(info.processName);

        // TODO cpu types cannot be integer on Android, what to do?

        /* Thread in error information. */
        errorLog.setErrorThreadId(thread.getId());
        errorLog.setErrorThreadName(thread.getName());

        /* For now we monitor only uncaught exceptions: a crash, fatal. */
        errorLog.setFatal(true);

        /* Relative application launch time to error time. */
        errorLog.setAppLaunchTOffset(System.currentTimeMillis() - initializeTimestamp);

        /* Attach exceptions. */
        errorLog.setExceptions(getJavaExceptionsFromThrowable(exception));

        /* Attach thread states. */
        for (Map.Entry<Thread, StackTraceElement[]> entry : allStackTraces.entrySet()) {
            JavaThread t = new JavaThread();
            t.setId(entry.getKey().getId());
            t.setName(entry.getKey().getName());
            t.setFrames(getJavaStackFramesFromStackTrace(entry.getValue()));
        }
        return errorLog;
    }

    @NonNull
    public static File getErrorStorageDirectory() {
        File errorLogDirectory = new File(Constants.FILES_PATH, ERROR_DIRECTORY);
        StorageHelper.InternalStorage.mkdir(errorLogDirectory.getAbsolutePath());
        return errorLogDirectory;
    }

    @NonNull
    public static File[] getStoredErrorLogFiles() {
        File[] files = getErrorStorageDirectory().listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                return filename.endsWith(".json");
            }
        });

        return files != null ? files : new File[0];
    }

    @NonNull
    private static List<JavaException> getJavaExceptionsFromThrowable(@NonNull Throwable t) {
        List<JavaException> javaExceptions = new ArrayList<>();
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            JavaException javaException = new JavaException();
            javaException.setType(cause.getClass().getName());
            javaException.setMessage(cause.getMessage());
            javaException.setFrames(getJavaStackFramesFromStackTrace(cause.getStackTrace()));
            javaExceptions.add(javaException);
        }
        return javaExceptions;
    }

    @NonNull
    private static List<JavaStackFrame> getJavaStackFramesFromStackTrace(@NonNull StackTraceElement[] stackTrace) {
        List<JavaStackFrame> javaStackFrames = new ArrayList<>();
        for (StackTraceElement stackTraceElement : stackTrace) {
            JavaStackFrame javaStackFrame = new JavaStackFrame();
            javaStackFrame.setClassName(stackTraceElement.getClassName());
            javaStackFrame.setMethodName(stackTraceElement.getMethodName());
            javaStackFrame.setLineNumber(stackTraceElement.getLineNumber());
            javaStackFrame.setFileName(stackTraceElement.getFileName());
            javaStackFrames.add(javaStackFrame);
        }
        return javaStackFrames;
    }

}
