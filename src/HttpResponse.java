/**
 * HttpResponse - HTTP 回應的處理器
 */

import java.io.*;

public class HttpResponse {
    /** HTTP 標頭分隔符：回車換行 */
    final static String CRLF = "\r\n";  
    /** 讀取緩衝區大小（8KB）*/
    final static int BUF_SIZE = 8192; 
    /** 最大回應主體大小（100KB）*/
    final static int MAX_OBJECT_SIZE = 100000; 

    /** HTTP 協定版本 */
    String version;            
    /** HTTP 狀態碼（如 200, 404, 500 等）*/
    int status;                
    /** HTTP 狀態行（包含版本、狀態碼和狀態描述）*/
    String statusLine = "";   
    /** 回應標頭的完整文字 */
    String headers = "";       

    /** 回應主體的位元組陣列 */
    byte[] body = new byte[MAX_OBJECT_SIZE]; 

    /**
     * 從伺服器讀取回應
     */
    public HttpResponse(DataInputStream fromServer) {
        /* 回應內容長度，-1 表示未指定長度 */
        int length = -1;
        boolean gotStatusLine = false;

        /* 讀取狀態行和回應標頭 */
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(fromServer));
            String line = reader.readLine();
            
            // 逐行讀取直到遇到空行（標頭結束）
            while (line != null && line.length() != 0) {
                if (!gotStatusLine) {
                    // 解析第一行（狀態行）
                    statusLine = line;
                    // 解析狀態碼
                    String[] parts = statusLine.split(" ");
                    if (parts.length >= 2) {
                        try {
                            status = Integer.parseInt(parts[1]);
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid status code in response: " + statusLine);
                        }
                    }
                    gotStatusLine = true;
                } else {
                    // 儲存標頭行
                    headers += line + CRLF;
                }

                // 解析 Content-Length 標頭以確定回應主體大小
                if (line.toLowerCase().startsWith("content-length:")) {
                    String[] tmp = line.split(" ");
                    length = Integer.parseInt(tmp[1].trim());
                }
                line = reader.readLine();
            }
        } catch (IOException e) {
            System.out.println("Error reading headers from server: " + e);
            return;
        }

        /* 讀取回應主體 */
        ByteArrayOutputStream bodyStream = new ByteArrayOutputStream();
        try {
            byte[] buf = new byte[BUF_SIZE];
            int bytesRead;
            
            if (length != -1) {
                // 處理有 Content-Length 的回應
                int remainingBytes = length;
                while (remainingBytes > 0) {
                    bytesRead = fromServer.read(buf, 0, Math.min(BUF_SIZE, remainingBytes));
                    if (bytesRead == -1) break;  // 連接提前關閉
                    bodyStream.write(buf, 0, bytesRead);
                    remainingBytes -= bytesRead;
                }
            } else {
                // 處理沒有 Content-Length 的回應（讀取直到連接關閉）
                while ((bytesRead = fromServer.read(buf)) != -1) {
                    bodyStream.write(buf, 0, bytesRead);
                }
            }
            body = bodyStream.toByteArray();
        } catch (IOException e) {
            System.out.println("Error reading response body: " + e);
        }
    }

    /**
     * 將回應轉換為字串格式以便重新發送
     */
    public String toString() {
        String res = "";

        res = statusLine + CRLF;
        res += headers;
        res += CRLF;
        
        return res;
    }
}