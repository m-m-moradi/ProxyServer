import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

public class HttpParser {
    private static final String[][] HttpReplies = {{"100", "Continue"},
            {"101", "Switching Protocols"},
            {"200", "OK"},
            {"201", "Created"},
            {"202", "Accepted"},
            {"203", "Non-Authoritative Information"},
            {"204", "No Content"},
            {"205", "Reset Content"},
            {"206", "Partial Content"},
            {"300", "Multiple Choices"},
            {"301", "Moved Permanently"},
            {"302", "Found"},
            {"303", "See Other"},
            {"304", "Not Modified"},
            {"305", "Use Proxy"},
            {"306", "(Unused)"},
            {"307", "Temporary Redirect"},
            {"400", "Bad Request"},
            {"401", "Unauthorized"},
            {"402", "Payment Required"},
            {"403", "Forbidden"},
            {"404", "Not Found"},
            {"405", "Method Not Allowed"},
            {"406", "Not Acceptable"},
            {"407", "Proxy Authentication Required"},
            {"408", "Request Timeout"},
            {"409", "Conflict"},
            {"410", "Gone"},
            {"411", "Length Required"},
            {"412", "Precondition Failed"},
            {"413", "Request Entity Too Large"},
            {"414", "Request-URI Too Long"},
            {"415", "Unsupported Media Type"},
            {"416", "Requested Range Not Satisfiable"},
            {"417", "Expectation Failed"},
            {"500", "Internal Server Error"},
            {"501", "Not Implemented"},
            {"502", "Bad Gateway"},
            {"503", "Service Unavailable"},
            {"504", "Gateway Timeout"},
            {"505", "HTTP Version Not Supported"}};

    private final InputStream inputStream;
    private String method;
    private String host;
    private String version;
    private int port;

    private String requestLine;
    private ArrayList<String> headers;
    private ArrayList<Byte> body;

    public HttpParser(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public int readRequest() throws IOException, URISyntaxException {

        StringBuilder requestBuilder = new StringBuilder();
        StringBuilder lineBuilder = new StringBuilder();
        StringBuilder bodyBuilder = new StringBuilder();
        ArrayList<Character> array_list_char = new ArrayList<Character>();

        char c1, c2, c3, c4;
        int b1, b2, b3, b4;

        while (true) {
            while ((b1 = this.inputStream.read()) != '\r') {
                if (b1 == -1) break;
                c1 = (char) b1;
                array_list_char.add(c1);
            }

            b2 = this.inputStream.read(); // reading LF
            if (array_list_char.size() == 0) // reached last CRLF
                break;

            for (Character c : array_list_char)
                lineBuilder.append(c);
            requestBuilder.append(lineBuilder.toString()).append("\r\n");
            lineBuilder = new StringBuilder();
            array_list_char.clear();
        }


        String http_request = requestBuilder.toString();
        if (http_request.isBlank() || http_request.isEmpty()) {
            return -1;
        }

        String[] lines = http_request.split("\r\n");
        String requestLine = lines[0];
        String[] requestLineParts = requestLine.split(" ");
        ArrayList<String> headers = new ArrayList<>();
        ArrayList<Byte> body = new ArrayList<>();

        String method = requestLineParts[0];
        String path = requestLineParts[1];
        String version = requestLineParts[2];
        String remote_host;
        int remote_port = 80;


        // https://datatracker.ietf.org/doc/html/rfc2616#section-5.1.2
        if (method.equals("CONNECT")) {
            String[] authority = path.split(":");
            remote_host = authority[0];
            remote_port = Integer.parseInt(authority[1]);
        } else {
            URI uri = new URI(path);
            remote_host = uri.getHost();
            if (uri.getPort() != -1)
                remote_port = uri.getPort();
        }

        // headers
        for (int h = 2; h < lines.length; h++) {
            String header = lines[h];
            headers.add(header);
        }

        // here must implement all proxy things
        boolean there_is_host = false;
        ArrayList<Integer> indices = new ArrayList<>();
        for (String h : headers) {
            if (h.contains("Content-Length:")) {
                String byte_num_str = h.replace("Content-Length:", "");
                byte[] chunk = new byte[1024];
                int byte_num = Integer.parseInt(byte_num_str.trim().strip());
                int counter = 0;
                while (counter != byte_num) {
                    System.out.println(String.format("byte_num : %d , counter : %d , difference : %d", byte_num, counter, byte_num - counter));
                    counter += this.inputStream.read(chunk, 0, Math.min(1024, byte_num - counter));
                    body.addAll(Arrays.asList(ArrayUtils.toObject(chunk)));
                }
            }
            if (h.contains("Upgrade-Insecure-Requests"))
                indices.add(headers.indexOf(h));

            if (h.contains("Host:"))
                there_is_host = true;
        }

        if(!there_is_host) headers.add("Host:");

        for (Integer index : indices)
            headers.remove((int) index);

        this.method = method;
        this.port = remote_port;
        this.host = remote_host;
        this.version = version;
        this.requestLine = requestLine;
        this.headers = headers;
        this.body = body;

        return 0;
    }

    public byte[] toBytes() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(this.requestLine).append("\r\n");
        for (String h : this.headers)
            stringBuilder.append(h).append("\r\n");
        stringBuilder.append("\r\n");
        ArrayList<Byte> bytes = new ArrayList<>(Arrays.asList(ArrayUtils.toObject(stringBuilder.toString().getBytes())));
        if (this.body.size() != 0)
            bytes.addAll(this.body);
        return ArrayUtils.toPrimitive(bytes.toArray(new Byte[0]));
    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(this.requestLine).append("\r\n");
        for (String h : this.headers)
            stringBuilder.append(h).append("\r\n");
        stringBuilder.append("\r\n");
        stringBuilder.append(getBodyAsString());
        return stringBuilder.toString();
    }

    public String getBodyAsString() {
        return new String(ArrayUtils.toPrimitive(this.body.toArray(new Byte[0])), StandardCharsets.UTF_8);
    }

    public static String[][] getHttpReplies() {
        return HttpReplies;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public String getMethod() {
        return method;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getRequestLine() {
        return requestLine;
    }

    public ArrayList<String> getHeaders() {
        return headers;
    }

    public ArrayList<Byte> getBody() {
        return body;
    }

    public String getVersion() {
        return version;
    }
}

