/**
 * HttpRequest - HTTP 請求的 container & parser
 */

import java.io.*;

public class HttpRequest {
    final static String CRLF = "\r\n";  // HTTP 標頭分隔符
    final static int HTTP_PORT = 80;    // 預設 HTTP port

    String method;          // HTTP 方法
    String URI;             // 請求的資源標識符
    String version;         // HTTP 版本
    String headers = "";    // 請求標頭

    private String host;    // 目標主機名稱
    private int port;       // 目標 port

    /**
     * 從客戶端 Socket 讀取並解析 HTTP 請求
     */
    public HttpRequest(BufferedReader from) throws IOException {
        String firstLine = null;
        try {
            firstLine = from.readLine();
            
            // 驗證請求行不為空
            if (firstLine == null || firstLine.trim().isEmpty()) {
                 throw new IOException("Empty request line");
            }
            
            // 解析請求行
            String[] tmp = firstLine.split(" ");
            if (tmp.length != 3) {
                 throw new IOException("Invalid request line format: " + firstLine);
            }
            
            method = tmp[0];
            URI = tmp[1];
            version = tmp[2];
            
             System.out.println("URI is: " + URI);
            
            // 驗證 HTTP 方法
            if (!method.equals("GET") && !method.equals("CONNECT")) {
                 throw new IOException("Only GET and CONNECT methods are supported");
            }
            
            // 讀取請求標頭
            String line;
            boolean foundHost = false;
            while ((line = from.readLine()) != null) {
                if (line.isEmpty()) {
                    break;  // 標頭結束
                }
                headers += line + CRLF;
                
                // 解析 Host 標頭
                if (line.startsWith("Host:")) {
                    foundHost = true;
                    String[] hostParts = line.substring(6).trim().split(":");
                    host = hostParts[0];
                    // 設定 port：HTTPS - 443，HTTP - 80
                    port = (hostParts.length > 1) ? Integer.parseInt(hostParts[1]) : 
                          (method.equals("CONNECT") ? 443 : HTTP_PORT);
                }
            }
            
            // 確保請求包含 Host 標頭
            if (!foundHost) {
                 throw new IOException("No Host header found in request");
            }
            
             System.out.println("Host to contact is: " + host + " at port " + port);
            
        } catch (IOException e) {
             System.out.println("Error reading request: " + e.getMessage());
            throw e;
        }
    }

    /** 取得目標主機名稱 */
    public String getHost() {
        return host;
    }

    /** 取得目標 port */
    public int getPort() {
        return port;
    }

    /**
     * 將請求轉換為字串格式以便重新發送
     */
    public String toString() {
        String req = "";

        req = method + " " + URI + " " + version + CRLF;
        req += headers;
        req += "Connection: close" + CRLF;
        req += CRLF;
        
        return req;
    }
}