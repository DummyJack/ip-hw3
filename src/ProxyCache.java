/**
 * ProxyCache.java - 簡單的 Cache 代理伺服器
 */

import java.net.*;
import java.io.*;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyCache {
    // 代理伺服器 port
    private static int port;
    
    /** 用於接受客戶端連接的伺服器 Socket */
    private static ServerSocket socket;
    
    /** 用於儲存 HTTP 回應的快取，key 為請求 URI，value 為回應內容 */
    private static HashMap<String, byte[]> cache = new HashMap<>();
    
    /** 設定執行緒池大小，限制同時處理的最大連接數 */
    private static final int THREAD_POOL_SIZE = 10;
    
    /** 執行緒池，用於管理並發的客戶端請求 */
    private static final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    /** 建立 ProxyCache 物件和 Socket */
    public static void init(int p) {
        port = p;
        try {
            socket = new ServerSocket(port); // 建立一個綁定到指定 port 的伺服器 Socket
        } catch (IOException e) {
            System.out.println("Error creating socket: " + e);
            System.exit(-1);
        }
    }

    /**
     * RequestHandler 類別負責處理單一客戶端連接的請求
     * 實現 Runnable 介面以支援多執行緒處理
     * 
     * 主要功能：
     * - 處理 HTTP 請求和回應
     * - 管理快取機制
     * - 建立 HTTPS 通道
     * - 錯誤處理
     */
    private static class RequestHandler implements Runnable {
        /** 當前處理的客戶端連接 Socket */
        private final Socket client;

        public RequestHandler(Socket client) {
            this.client = client;
        }

        /**
         * 執行緒的主要處理邏輯
         */
        @Override
        public void run() {
            Socket server = null;
            HttpRequest request = null;

            try {
                // 設定 Socket 超時
                client.setSoTimeout(5000);
                
                // 建立 UTF-8 編碼的讀取器
                BufferedReader fromClient = new BufferedReader(
                    new InputStreamReader(client.getInputStream(), "UTF-8")
                );

                // 解析 HTTP 請求
                request = new HttpRequest(fromClient);

                // 根據請求方法選擇處理方式
                if (request.method.equals("CONNECT")) {
                    // HTTPS 請求處理
                    handleHttpsRequest(client, request);
                    return;
                }

                // HTTP 請求處理
                handleHttpRequest(client, request);

            } catch (Exception e) {
                System.out.println("Error processing request: " + e.getMessage());
                try {
                    if (!client.isClosed()) {
                        // 發送錯誤回應給客戶端
                        sendErrorResponse(client, "500 Internal Server Error", e.getMessage());
                    }
                } catch (IOException ignored) {}
            } finally {
                // 確保所有連接都被關閉
                closeConnections(client, server);
            }
        }

        /**
         * 處理 HTTPS 請求的方法
         */
        private void handleHttpsRequest(Socket client, HttpRequest request) throws IOException {
            // 建立到目標伺服器的連接
            Socket server = new Socket(request.getHost(), request.getPort());

            // 向客戶端發送 200 Connection established
            DataOutputStream toClient = new DataOutputStream(client.getOutputStream());
            toClient.writeBytes("HTTP/1.0 200 Connection established\r\n\r\n");
            toClient.flush();

            /** 建立雙向資料通道（兩個 StreamForwarder）*/
            // 建立客戶端到伺服器的通道
            Thread clientToServer = new Thread(new StreamForwarder(client.getInputStream(), 
                                                                 server.getOutputStream()));
            // 建立伺服器到客戶端的通道
            Thread serverToClient = new Thread(new StreamForwarder(server.getInputStream(), 
                                                                 client.getOutputStream()));

            // 啟動雙向通道
            clientToServer.start();
            serverToClient.start();

            // 等待通訊完成或連接中斷
            try {
                clientToServer.join();
                serverToClient.join();
            } catch (InterruptedException e) {
                System.out.println("HTTPS tunnel interrupted: " + e.getMessage());
            } finally {
                server.close();
                client.close();
            }
        }

        /**
         * StreamForwarder 類別用於在兩個串流之間轉發資料
         * 用於 HTTPS 通道的資料傳輸，實現了全雙工通訊
         */
        private static class StreamForwarder implements Runnable {
            /** 來源資料串流 */
            private final InputStream in;
            /** 目標資料串流 */
            private final OutputStream out;

            public StreamForwarder(InputStream in, OutputStream out) {
                this.in = in;
                this.out = out;
            }

            @Override
            public void run() {
                byte[] buffer = new byte[8192];
                int length;
                try {
                    while ((length = in.read(buffer)) != -1) {
                        out.write(buffer, 0, length);
                        out.flush();
                    }
                } catch (IOException ignored) {
                    // 連接關閉
                }
            }
        }

        /**
         * 發送錯誤回應給客戶端
         */
        private void sendErrorResponse(Socket client, String status, String message) throws IOException {
            String response = "HTTP/1.0 " + status + "\r\n" +
                             "Content-Type: text/plain\r\n" +
                             "Connection: close\r\n\r\n" +
                             message;
            DataOutputStream toClient = new DataOutputStream(client.getOutputStream());
            toClient.writeBytes(response);
        }

        /**
         * 發送一般 HTTP 回應給客戶端
         */
        private void sendResponse(Socket client, HttpResponse response) throws IOException {
            DataOutputStream toClient = new DataOutputStream(client.getOutputStream());
            toClient.writeBytes(response.toString());
            toClient.write(response.body);
        }

        /**
         * 發送快取的回應內容給客戶端
         */
        private void sendCachedResponse(Socket client, byte[] cachedContent) throws IOException {
            if (client.isClosed()) {
                throw new IOException("Client connection is closed");
            }
            DataOutputStream toClient = new DataOutputStream(client.getOutputStream());
            toClient.writeBytes("HTTP/1.0 200 OK\r\n");
            toClient.writeBytes("Content-Length: " + cachedContent.length + "\r\n");
            toClient.writeBytes("\r\n");
            toClient.write(cachedContent);
            toClient.flush();
        }

        /**
         * 關閉所有相關的網路連接
         */
        private void closeConnections(Socket client, Socket server) {
            try {
                if (client != null) client.close();
                if (server != null) server.close();
            } catch (IOException e) {
                System.out.println("Error closing connections: " + e);
            }
        }

        /**
         * 處理 HTTP 請求的主要方法
         */
        private void handleHttpRequest(Socket client, HttpRequest request) throws IOException {
            Socket server = null;
            HttpResponse response = null;
            String requestURI = request.URI;

            try {
                // 先檢求的 URI 是否在 Cache 中
                byte[] cachedContent = null;
                synchronized (cache) {
                    cachedContent = cache.get(requestURI);
                }

                // 如果 Cache 命中，直接返回 Cache 的回應
                if (cachedContent != null) {
                    System.out.println("Cache hit for: " + requestURI);
                    sendCachedResponse(client, cachedContent);
                    return;
                }

                // 如果 Cache 未命中，向原始伺服器發送請求
                System.out.println("Cache miss for: " + requestURI);
                server = new Socket(request.getHost(), request.getPort());
                server.setSoTimeout(5000);

                // 發送請求到目標伺服器
                DataOutputStream toServer = new DataOutputStream(server.getOutputStream());
                toServer.writeBytes(request.toString());
                toServer.flush();

                // 讀取伺服器回應
                DataInputStream fromServer = new DataInputStream(server.getInputStream());
                response = new HttpResponse(fromServer);

                // 檢查回應狀態碼
                if (response.status == 404) {
                    System.out.println("Resource not found: " + requestURI);
                    String errorMessage = "404 Not Found - The requested resource '" + requestURI + "' was not found on this server.";
                    sendErrorResponse(client, "404 Not Found", errorMessage);
                    return;
                }

                // 發送回應到客戶端
                sendResponse(client, response);

                // 儲存到 Cache
                if (response.status == 200) {  // 只快取成功的回應
                    synchronized (cache) {
                        ByteArrayOutputStream fullResponseStream = new ByteArrayOutputStream();
                        fullResponseStream.write(response.toString().getBytes());
                        fullResponseStream.write(response.body);
                        cache.put(requestURI, fullResponseStream.toByteArray());
                    }
                }

            } catch (Exception e) {
                System.out.println("Error processing request for: " + requestURI + " - " + e.getMessage());
                sendErrorResponse(client, "500 Internal Server Error", e.getMessage());
            } finally {
                if (server != null) {
                    server.close();
                }
            }
        }
    }

    /**
     * 關閉代理伺服器
     * 關閉執行緒池和伺服器 Socket
     */
    public static void shutdown() {
        System.out.println("Shutting down proxy server...");
        executorService.shutdown();
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.out.println("Error closing server socket: " + e);
        }
    }

    /**
     * 主程式進入點
     */
    public static void main(String args[]) {
        int myPort = 0;

        try {
            myPort = Integer.parseInt(args[0]);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("Need port number as argument");
            System.exit(-1);
        } catch (NumberFormatException e) {
            System.out.println("Please give port number as integer.");
            System.exit(-1);
        }

        init(myPort);
        System.out.println("Proxy server started on port " + myPort);

        while (true) {
            try {
                // 接受客戶端連接並提交至執行緒池處理
                Socket client = socket.accept();
                System.out.println("New client connection accepted");
                executorService.execute(new RequestHandler(client));
            } catch (IOException e) {
                System.out.println("Error accepting client connection: " + e);
                continue;
            }
        }
    }
}