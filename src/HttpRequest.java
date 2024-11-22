/**
 * HttpRequest - HTTP 請求的 container & parser
 */

import java.io.*;

public class HttpRequest {
    /** HTTP 標頭分隔符：回車換行 */
    final static String CRLF = "\r\n";
    
    /** 預設 HTTP 通訊埠 */
    final static int HTTP_PORT = 80;

    /** HTTP 請求方法（GET 或 CONNECT） */
    String method;
    
    /** 請求的統一資源標識符 */
    String URI;
    
    /** HTTP 協定版本（如 HTTP/1.0, HTTP/1.1） */
    String version;
    
    /** 請求標頭的完整文字，包含所有標頭欄位 */
    String headers = "";

    /** 目標伺服器的主機名稱，從 Host 標頭解析而來 */
    private String host;
    
    /** 目標伺服器的通訊埠，從 Host 標頭解析或使用預設值 */
    private int port;

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
            
            // 解析請求行的三個組件：方法、URI、HTTP版本
            String[] tmp = firstLine.split(" ");
            if (tmp.length != 3) {
                throw new IOException("Invalid request line format: " + firstLine);
            }
            
            method = tmp[0];
            URI = tmp[1];
            version = tmp[2];
            
            System.out.println("URI is: " + URI);
            
            // 驗證 HTTP 方法是否支援
            if (!method.equals("GET") && !method.equals("CONNECT")) {
                throw new IOException("Only GET and CONNECT methods are supported");
            }
            
            // 讀取並解析所有請求標頭
            String line;
            boolean foundHost = false;
            while ((line = from.readLine()) != null) {
                if (line.isEmpty()) {
                    break;  // 遇到空行表示標頭部分結束
                }
                headers += line + CRLF;
                
                // 解析 Host 標頭以獲取目標伺服器資訊
                if (line.startsWith("Host:")) {
                    foundHost = true;
                    String[] hostParts = line.substring(6).trim().split(":");
                    host = hostParts[0];
                    // 設定通訊埠：HTTPS 使用 443，HTTP 使用 80
                    port = (hostParts.length > 1) ? Integer.parseInt(hostParts[1]) : 
                          (method.equals("CONNECT") ? 443 : HTTP_PORT);
                }
            }
            
            // Host 標頭是 HTTP/1.1 必要欄位
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

    /**
     * 取得目標伺服器的 port
     */
    public int getPort() {
        return port;
    }

    /**
     * 將 HTTP 請求轉換為字串格式
     * 
     * 用於將請求轉發給目標伺服器時重建請求內容
     * 自動添加 Connection: close 標頭以確保連接正確關閉
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