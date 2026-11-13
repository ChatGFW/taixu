package com.ai.assistance.shower;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 落盘到工作目录旁的 shower.log（与旧 server 同路径约定）。 */
final class ServerLog {
    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private static PrintWriter out;

    private ServerLog() {}

    static synchronized void d(String msg, Throwable t) {
        try {
            if (out == null) {
                out = new PrintWriter(new FileWriter(new File("/data/local/tmp/shower.log"), true), true);
            }
            out.println(FMT.format(new Date()) + " " + msg);
            if (t != null) t.printStackTrace(out);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
