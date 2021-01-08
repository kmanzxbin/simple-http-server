import java.text.SimpleDateFormat;
import java.util.Date;

/*
 * @(#)LogUtil.java Nov 19, 2013
 * Copyright (c), 2013 深圳业拓讯通信科技有限公司（Shenzhen Yetelcom Communication Tech. Co.,Ltd.）,  
 * 著作权人保留一切权利，任何使用需经授权。
 */

/**
 * description
 * @author  Benjamin
 * @version 1.0.0
 * @see     
 * @since   R01V00
 */
public class Logger {

    int logLevel = 1;
    static final int LOG_LEVEL_ERROR = 4;
    static final int LOG_LEVEL_WARN = 3;
    static final int LOG_LEVEL_INFO = 2;
    static final int LOG_LEVEL_DEBUG = 1;

    public int getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(int logLevel) {
        this.logLevel = logLevel;
    }

    public void debug(String msg) {
        log("DEBUG", msg, LOG_LEVEL_DEBUG);
    }

    public void info(String msg) {
        log("INFO", msg, LOG_LEVEL_INFO);
    }

    public void warn(String msg) {
        log("WARN", msg, LOG_LEVEL_WARN);
    }

    public void error(String msg) {
        log("ERROR", msg, LOG_LEVEL_ERROR);
    }
    
    /**
     * 时间/级别/线程名称/消息内容
     */
    String pattern = "%s [%-5s] [%s] %s";
    
    boolean isDebugEnabled() {
        return this.logLevel <= LOG_LEVEL_DEBUG;
    }

    private void log(String level, String msg, int logLevel) {
        if (logLevel >= this.logLevel) {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss,SSS");
            System.out.println(String.format(pattern, simpleDateFormat.format(new Date()), level, Thread.currentThread().getName(), msg));

        }
    }

}
