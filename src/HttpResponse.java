/**
 * HttpResponse - HTTP 回應的處理器
 */

import java.io.*;

public class HttpResponse {
    final static String CRLF = "\r\n";  // 換行符
    final static int BUF_SIZE = 8192; // 讀取緩衝區大小（8KB）
    final static int MAX_OBJECT_SIZE = 100000; // 最大物件大小 - 100KB

    String version;            // HTTP 版本
    int status;                // 狀態碼
    String statusLine = "";    // 狀態行
    String headers = "";       // 回應標頭

    byte[] body = new byte[MAX_OBJECT_SIZE]; // 回應主體

    /**
     * 從伺服器讀取回應
     */
    public HttpResponse(DataInputStream fromServer) {
        /* 回應內容長度 */
        int length = -1;
        boolean gotStatusLine = false;

        /* 讀取狀態行和回應標頭 */
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(fromServer));
            String line = reader.readLine();
            
            // 逐行讀取直到遇到空行（標頭結束）
            while (line != null && line.length() != 0) {
                if (!gotStatusLine) {
                    // 第一行是狀態行
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
                    // 其餘行是標頭
                    headers += line + CRLF;
                }

                // 解析 Content-Length 標頭
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
                // 有 Content-Length 的情況
                int remainingBytes = length;
                while (remainingBytes > 0) {
                    bytesRead = fromServer.read(buf, 0, Math.min(BUF_SIZE, remainingBytes));
                    if (bytesRead == -1) break;
                    bodyStream.write(buf, 0, bytesRead);
                    remainingBytes -= bytesRead;
                }
            } else {
                // 沒有 Content-Length 的情況
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