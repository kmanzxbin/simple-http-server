import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/*
 * @(#)SocketTool.java Dec 6, 2012
 * Copyright (c), 2012 深圳业拓讯通信科技有限公司（Shenzhen Yetelcom Communication Tech. Co.,Ltd.）,  
 * 著作权人保留一切权利，任何使用需经授权。
 */

/**
 * description
 * @author  Benjamin
 * @version 1.0.0
 * @see     
 * @since   R01V00
 */
public class SocketServer {

    /**
     * 
     */
    static final String CONTENT_LENGTH = "Content-Length: ";

    public static final String DEFAULT = "default";

    /**
     * 
     */
    public static final String SO_TIMEOUT = "soTimeout";

    public static final String THREAD_POOL_SIZE = "threadPoolSize";

    /**
     * 
     */
    public static final String SOTIMEOUT = SO_TIMEOUT;

    static Logger log = new Logger();

    public static void main(String[] args) {
        SocketServer st = new SocketServer();
        st.init();
        st.startNIO();
    }

    LinkedHashMap<String, ContentBean> responseMap = new LinkedHashMap<String, ContentBean>();

    int threadPoolSize = 100;

    FileMonitor responseMonitor = new FileMonitor();
    FileMonitor confMonitor = new FileMonitor();

    Properties conf = new Properties();
    
    ScheduledExecutorService executorService;
    
    int serverPort = 60000;
    
    int bufferSize = 1024 * 4;

    int soTimeout;

    String encode;

    void init() {
        
        loadConf(new File("./conf/conf.properties"));
        if ("1".equals(conf.getProperty("isUseRT"))) {
            loadRTConf();
        }
        encode = "UTF-8";
        String encodeStr = conf.getProperty("requestEncode");
        if (encodeStr != null && !"".equals(encodeStr)) {
            encode = encodeStr;
        }
        serverPort = Integer.parseInt(conf.getProperty("port").trim());
        
        responseMonitor.listener = new ResponseFileListener();
        responseMonitor.fileLister = new FileListerImpl();
        responseMonitor.start();

        File confFile = new File("./conf/conf.properties");
        confMonitor.listener = new ConfListener();
        confMonitor.files.put(confFile, null);
        confMonitor.start();
    }
    
   
    public void startNIO() {
        
        log.info("\n" + "Java Socket Server" + "\n" + "version: 2020.11.23(NIO)");
        ServerSocketChannel serverSocketChannel = null;
        //创建serverSocketChannel，监听8888端口
        try {
            serverSocketChannel = ServerSocketChannel.open();
            Selector selector = Selector.open();
            serverSocketChannel.socket().bind(new InetSocketAddress(serverPort));
            //设置为非阻塞模式
            serverSocketChannel.configureBlocking(false);
            //为serverChannel注册selector
            
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            
            executorService =  Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

            log.info("JavaSocketServer is started! linstening on port: " + serverPort);

            while (true) {
                try {
                    
                selector.select();
                //获取selectionKeys并处理
                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    try {
                        if (!key.isValid()) {
                            continue;
                        }
                        //连接请求
                        if (key.isAcceptable()) {
                            handleAccept(key);
                        }
                        //读请求
                        if (key.isReadable()) {
                            handleRead(key);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    //处理完后移除当前使用的key
                    keyIterator.remove();
                }
//                System.out.println("完成请求处理。");
                } catch (Exception e) {
                    e.printStackTrace();
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e1) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void handleAccept(SelectionKey selectionKey) throws IOException {
        //获取channel
        SocketChannel socketChannel = ((ServerSocketChannel) selectionKey.channel()).accept();
        //非阻塞
        socketChannel.configureBlocking(false);
        //注册selector
        socketChannel.register(selectionKey.selector(), SelectionKey.OP_READ, ByteBuffer.allocate(bufferSize));
        
        socketChannel.socket().setSoTimeout(getSoTimeout());

        log.debug("accept socket from " +  socketChannel.socket().getRemoteSocketAddress());
    }
    
    int getSoTimeout() {
        int soTimeout = 100;
        if ("1".equals(conf.getProperty("isUseRT"))) {
            soTimeout = getRTSoTime();
        } else if (conf.containsKey(SO_TIMEOUT)) {
            soTimeout = getSoTime(null);
        }
        return soTimeout;
        
    }
    
    public void handleRead(SelectionKey selectionKey) throws IOException {
        
        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
        if (!socketChannel.isOpen()) {
            return;
        }
        
        ByteBuffer buffer = (ByteBuffer) selectionKey.attachment();

        String receivedStr = "";
        
        try {
            
    
            if (socketChannel.read(buffer) == -1) {
                log.warn("===============");
                //没读到内容关闭
    //            socketChannel.shutdownOutput();
    //            socketChannel.shutdownInput();
                if (socketChannel.isOpen()) {
                    log.info("close connection "+ socketChannel.socket().getRemoteSocketAddress());
                    socketChannel.close();
                }
            } else {
                //将channel改为读取状态
                buffer.flip();
                //按照编码读取数据
                receivedStr = Charset.forName(encode).newDecoder().decode(buffer).toString();
                try {
                    processReadContent(receivedStr, socketChannel);
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                    socketChannel.close();
                    return;
                }
                
                buffer.clear();
                // 丢给调度器等待处理
                
    //            socketChannel.write(buffer);
                //读取模式
                buffer.flip();
                //注册selector 继续读取数据
    //            socketChannel.register(selectionKey.selector(), SelectionKey.OP_READ, ByteBuffer.allocate(bufferSize));
            }
            
        } catch (IOException e) {
            log.debug("socketChannel was closed when reading " + socketChannel.socket().getRemoteSocketAddress());
        }
    }
    
    void processReadContent(String content, SocketChannel socketChannel) throws Exception {
//        Socket socket = socketChannel.socket();
//        String acceptTimeString = new SimpleDateFormat(
//                "yyyy-MM-dd HH:mm:ss").format(new Date());
        if (content == null) {
            return;
        }
        content = content.trim();
        if (content.length() == 0) {
            return;
        }
        log.debug(String.format("accept request from %s%n%s", socketChannel.socket().getRemoteSocketAddress(),
                content));
        
        String[] lines = content.split("\n");
        
        String url = lines[0].split(" ")[1];
        if (url.startsWith("/")) {
            url = url.substring(1);
        }
        int pos = url.indexOf('?');
        if (pos > 0) {
            url = url.substring(0, pos);
        }

            // 注意，不使用bufferdReader的readLine是因为如果最后一行没有回车换行的话会导致读取不到最后一行的内容
            // 也不能强制客户端在最后增加\n
            
        soTimeout = getSoTime(url) ;
        
        socketChannel.socket().setSoTimeout(soTimeout * 2);
        
        
        final String response= makeResponse(url);
        if (response == null) {
            log.warn("no matched response");
            return;
        }

        executorService.schedule(new ResponseRunnable(response, socketChannel), soTimeout, TimeUnit.MILLISECONDS);
}
    
    String makeResponse(String url) throws Exception {
        String response = "";
        if (url != null) {
            String key = null;
            for (String tmpKey : responseMap.keySet()) {
                if (url.matches(tmpKey)) {
                    key = tmpKey;
                    break;
                }
            }
            if ( key!= null) {
                log.debug("url : " + url + " matchd key " + key);
                
                response = responseMap.get(key).getNextContent();
                
            } else if (responseMap.containsKey("404")) {
                response = responseMap.get("404").getNextContent();
                // 2020-10-22T07:17:28.576+0000
                response = response.replace("${timestamp}", 
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()));
                response = response.replace("${path}", url);
            }

            // if (matchLine.endsWith("\r") || matchLine.endsWith("\n"))
            // {
            //
            // log.debug("*** matchLine: "
            // + matchLine
            // .substring(0, matchLine.length() - 1));
            // } else {
            //
            // log.debug("*** matchLine: " + matchLine);
            // }
        } else {
            response = responseMap.get(DEFAULT).getNextContent();
        }
        
        Date currDate = new Date();
        response = response.replace("${CurrentTimeMilliSecond}",
                new SimpleDateFormat("yyyyMMddHHmmssSSS")
                        .format(currDate));
        response = response.replace("${CurrentTimeSecond}",
                new SimpleDateFormat("yyyyMMddHHmmss")
                        .format(currDate));

        response = setContentLength(response, encode);
        
        return response;

    }
    
    class ResponseRunnable implements Runnable {
        
        String response;
        SocketChannel socketChannel;
        
        
        
        public ResponseRunnable(String response, SocketChannel socketChannel) {
            super();
            this.response = response;
            this.socketChannel = socketChannel;
        }



        @Override
        public void run() {
            ByteBuffer byteBuffer = null;
            try {// 写响应
                byteBuffer = ByteBuffer.wrap(response.getBytes(encode));
                socketChannel.write(byteBuffer);
//              log.debug("response:\n" + response);
              log.debug(String.format("reply response to %s%n%s", socketChannel.socket().getRemoteSocketAddress(), response));
              
            } catch (Exception e) {
                // TODO: handle exception
            } finally {

                try {
                    log.debug("close socket " + socketChannel.socket().getRemoteSocketAddress());
                    socketChannel.close();
                } catch (Exception e2) {
                    // TODO: handle exception
                }
            }
                
        }
    }
    

    static class ContentBean {
        String keyName;
        List<String> list;
        int index;
    
        public String getKeyName() {
            return keyName;
        }
    
        public void setKeyName(String keyName) {
            this.keyName = keyName;
        }
    
        public synchronized List<String> getList() {
            return list;
        }
    
        public synchronized void setList(List<String> list) {
            this.list = list;
        }
    
        public synchronized int getIndex() {
            return index;
        }
    
        public synchronized void setIndex(int index) {
            this.index = index;
        }
    
        public synchronized String getNextContent() {
            if (list == null || list.size() == 0) {
                return null;
            }
            if (index >= list.size()) {
                index = 0;
            }
            log.debug("get " + keyName + " content by index: " + index);
            return list.get(index++);
        }
    
    }

    public static interface FileLister {
        public File[] listFile();
    }

    static class FileListerImpl implements FileLister {

        public File[] listFile() {
            return new File("./conf").listFiles(new FilenameFilter() {

                @Override
                public boolean accept(File dir, String name) {
                    return name.startsWith("response_")
                            && name.endsWith(".txt");
                }

            });

        }
    }

    public String getResponseKeyByFileName(File file) {
        String key = file.getName().substring("response_".length());
        key = key.substring(0, key.length() - ".txt".length());
        key = key.replace('~', '/');
        key = key.replace("{}", "\\w+");

        return key;
    }

    public List<String> readFileContent(File file) {
        log.debug("readFileContent: " + file);
        BufferedReader bufferedReader = null;

        try {

            List<String> list = null;
            bufferedReader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), "utf-8"));

            String content = null;
            String line = null;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.trim().startsWith("HTTP/1.1 ")) {
                    // 文件首行
                    if (content == null) {
                        content = line + "\n";

                        // 再次出现 将上一个完整消息存入list
                    } else {
                        if (list == null) {
                            list = new ArrayList<String>();
                        }
                        list.add(content);
                        content = null;
                        content = line + "\n";
                    }
                } else {
                    if (content == null) {
                        content = line + "\n";

                    } else {
                        content += line + "\n";
                    }
                }
            }

            if (content != null) {
                if (list == null) {
                    list = new ArrayList<String>();
                }
                list.add(content);
            }
            return list;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    interface FileChangeListener {

        public void fileChanged(File file);

        public void fileDeleted(File file);
    }

    enum ParseFlag {
        findConentLength, findBlankLine, calculateRealLength
    };

    String setContentLength(String response, String encode)
            throws Exception {

        if (!response.startsWith("HTTP/1.1 ")) {
            return response;
        }

        String[] lines = response.split("\n");

        int contentLengthIndex = -1;
        // String contengLengthStr = null;
        // int blankIndex = -1;
        int length = 0;
        ParseFlag flag = ParseFlag.findConentLength;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            // 寻找content length
            if (flag == ParseFlag.findConentLength) {
                if (line.toLowerCase()
                        .startsWith(CONTENT_LENGTH.toLowerCase())) {

                    contentLengthIndex = i;
                    // contengLengthStr = string;
                    flag = ParseFlag.findBlankLine;
                } else if (line.toLowerCase()
                        .startsWith("Content-Type:".toLowerCase())
                        && line.toLowerCase()
                                .contains("charset=".toLowerCase())) {
                    line = line.substring(0,
                            line.toLowerCase().indexOf("charset=")
                                    + "charset=".length())
                            + encode;
                    lines[i] = line;

                }
                // 寻找空行
            } else if (flag == ParseFlag.findBlankLine) {
                if ("".equals(line.trim())) {
                    flag = ParseFlag.findBlankLine;
                    // blankIndex = i;
                } else {
                    flag = ParseFlag.calculateRealLength;
                    length += line.getBytes("utf-8").length + "\n".length();
                }
            } else if (flag == ParseFlag.calculateRealLength) {
                length += line.getBytes("utf-8").length + "\n".length();
            } else {
                throw new RuntimeException("unsupport content! " + line);
            }
        }

        if (flag != ParseFlag.calculateRealLength) {
            throw new RuntimeException(
                    "can not parse content length from response! flag = " + flag
                            + "\n" + response);
        }

        log.debug(lines[contentLengthIndex] + " in template is set to " + length);
        lines[contentLengthIndex] = lines[contentLengthIndex].substring(0,
                CONTENT_LENGTH.length()) + length;

        StringBuilder stringBuilder = new StringBuilder();
        for (String string : lines) {
            stringBuilder.append(string).append("\n");
        }
        return stringBuilder.toString();

    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    LinkedHashMap sortLinkedHashMap(LinkedHashMap map) {
        List<String> keys = new ArrayList<String>(map.keySet());
        //排序
        Collections.sort(keys, new Comparator<String>() {

            @Override
            public int compare(String o1, String o2) {
                if (o2.length() != o1.length()) {
                    return  o2.length() - o1.length();
                } else {
                    return o2.compareTo(o1);
                }
            }

        });
       //转换成新map输出
        LinkedHashMap newMap = new LinkedHashMap();

        for(String key : keys){
            newMap.put(key, map.get(key));
//            System.out.println(entity.getKey());
        }
        return newMap;
    }
    
    class ResponseFileListener implements FileChangeListener {

        @SuppressWarnings("unchecked")
        public void fileChanged(File file) {

            String key = getResponseKeyByFileName(file);
            List<String> value = readFileContent(file);
            log.info("refresh by response key: " + key);
            ContentBean content = new ContentBean();
            content.setKeyName(key);
            content.setList(value);
            responseMap.put(key, content);
            responseMap = sortLinkedHashMap(responseMap);

        }

        /* (non-Javadoc)
         * @see SocketServer.FileChangeListener#fileDeleted(java.io.File)
         */
        @Override
        public void fileDeleted(File file) {
            String key = getResponseKeyByFileName(file);
            log.info("remove by response key: " + key);
            responseMap.remove(key);
        }
    }

    class ConfListener implements FileChangeListener {

        public void fileChanged(File file) {
            log.info("reload conf!");
            loadConf(file);
        }

        /* (non-Javadoc)
         * @see SocketServer.FileChangeListener#fileDeleted(java.io.File)
         */
        @Override
        public void fileDeleted(File file) {
            throw new RuntimeException("conf can not be delete! " + file);
        }
    }

    public void loadConf(File file) {
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(file);
            conf.load(fileInputStream);

            log.setLogLevel(
                    Integer.parseInt(conf.getProperty("logLevel", "2")));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static class FileMonitor extends Thread {

        FileChangeListener listener;

        Map<File, Long> files = new HashMap<File, Long>();

        FileLister fileLister;

        public FileMonitor() {
            super();
        }

        @Override
        public void run() {

            // 初始化
            if (fileLister != null) {
                log.info("init by fileLister " + fileLister);

                File[] filesInit = fileLister.listFile();
                for (File file : filesInit) {
                    files.put(file, null);
                    try {
                        listener.fileChanged(file);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            try {
                sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            while (true) {
                if (fileLister != null) {
                    // log.debug("relist by fileLister " + fileLister);
                    File[] filesTmp = fileLister.listFile();

                    for (File file : filesTmp) {
                        if (!files.containsKey(file)) {
                            log.info("add new file " + file);
                            files.put(file, null);
                            try {
                                listener.fileChanged(file);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    Set<File> removedFiles = new HashSet<File>();
                    for (File file2 : files.keySet()) {
                        int got = 0;
                        for (File fileTmp : filesTmp) {
                            if (fileTmp.getName().equals(file2.getName())) {
                                got = 1;
                                break;
                            }
                        }
                        if (got == 0) {
                            removedFiles.add(file2);
                        }
                    }

                    for (File remove : removedFiles) {
                        log.info("file was removed " + remove);
                        files.remove(remove);
                        listener.fileDeleted(remove);
                    }
                }

                for (Entry<File, Long> fileEntry : files.entrySet()) {

                    File file = fileEntry.getKey();
                    if (fileEntry.getValue() == null) {
                        fileEntry.setValue(file.lastModified());
                    } else {

                        if (fileEntry.getValue() != file.lastModified()) {
                            fileEntry.setValue(file.lastModified());
                            try {
                                log.info("file was changed " + file);
                                listener.fileChanged(file);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }

                try {
                    sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        }
    }

    Map<Integer, Integer> rtConfMap = new HashMap<Integer, Integer>(); // 保存配置的响应时间比例
    Map<Integer, Integer> rtCacheMap = new HashMap<Integer, Integer>(); // 保存响应时间已使用的比例

    /**
     * 加载响应时间配置文件
     */
    void loadRTConf() {
        try {
            File file = new File("conf/respTime.properties");
            Properties properties = new Properties();
            properties.load(new FileInputStream(file));
            for (Entry<Object, Object> entry : properties.entrySet()) {
                rtConfMap.put(Integer.parseInt(entry.getKey().toString()), 
                        Integer.parseInt(entry.getValue().toString()));
            }
            
            log.info("respTimes = " + rtConfMap);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 随机获取响应时间，并按比例限制，如果已经达到分配的比例则重新获取。
     * 
     * @return
     */
    synchronized int getSoTime(String key) {

        String soTimeStr = null;
        if (key != null && key.trim().length() > 0) {
            soTimeStr = conf.getProperty("soTimeout." + key);
            if (soTimeStr == null || soTimeStr.trim().length() == 0) {
                soTimeStr = conf.getProperty("soTimeout");
            }
        } else {
            soTimeStr = conf.getProperty("soTimeout");
        }

        return Integer.parseInt(soTimeStr);
    }

    synchronized int getRTSoTime() {
        clearSoTime();

        int value = 10;
        int key = 1000;

        int r = new Random().nextInt(rtConfMap.size());
        int i = 0;

        Iterator<Map.Entry<Integer, Integer>> keys = rtConfMap.entrySet().iterator();
        while (keys.hasNext()) {
            if (r == i) {
                Map.Entry<Integer, Integer> entry = keys.next();
                value = entry.getValue();
                key = entry.getKey();
                if (rtCacheMap.containsKey(key)) {
                    // 已经达到分配的比例则重新获取
                    if (rtCacheMap.get(key) >= value) {
                        // r = new Random().nextInt(rt1.size());
                        return getRTSoTime();
                    }
                    rtCacheMap.put(key, rtCacheMap.get(key) + 1);
                } else {
                    rtCacheMap.put(key, 1);
                }
                break;
            } else {
                keys.next();
                i++;
            }
        }

        return key;
    }

    /**
     * 如果所有响应时间的使用比例已达到100%，则全部清0，用于重新开始分配
     */
    void clearSoTime() {
        int rt1Total = 0;
        int rt2Total = 0;

        Iterator<Integer> rt1Set = rtConfMap.keySet().iterator();
        while (rt1Set.hasNext()) {
            rt1Total += rtConfMap.get(rt1Set.next());
        }

        Iterator<Integer> rt2Set = rtCacheMap.keySet().iterator();
        while (rt2Set.hasNext()) {
            rt2Total += rtCacheMap.get(rt2Set.next());
        }

        if (rt2Total >= rt1Total) {
            rt2Set = rtCacheMap.keySet().iterator();
            while (rt2Set.hasNext()) {
                rtCacheMap.put(rt2Set.next(), 0);
            }
        }
    }
}
