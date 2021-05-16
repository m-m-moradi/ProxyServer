import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

public class HttpParser {
    private final InputStream inputStream;
    private String firstLinePart1;
    private String firstLinePart2;
    private String firstLinePart3;

    private boolean isRequest = false;
    private boolean isResponse = false;

    // only when isResponse is true
    private String status = null;
    private String phase = null;

    // only when isRequest is true
    private String host = null;
    private int port = 80;

    private String mainLine;
    private ArrayList<String> headers;
    private ArrayList<Byte> body;

    public HttpParser(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public int readRequest() throws IOException, URISyntaxException, InterruptedException {

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
            if (array_list_char.size() == 0) // reached last CRLF (last CRLF consumed)
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
        String mainLine = lines[0];
        String[] mainLineParts = mainLine.split(" ");
        ArrayList<String> headers = new ArrayList<>();
        ArrayList<Byte> body = new ArrayList<>();

        if (mainLineParts.length == 1) {
            System.out.println("******************************************");
            System.out.println(mainLine);
            System.out.println("******************************************");
            return -1;
        }

        String firstPart = mainLineParts[0];
        String secondPart = mainLineParts[1];
        String thirdPart = mainLineParts[2];

        if (firstPart.contains("HTTP")) {
            isRequest = false;
            isResponse = true;

            this.status = secondPart.strip();
            this.phase = thirdPart.strip();

        } else {
            isRequest = true;
            isResponse = false;

            if (firstPart.equals("CONNECT")) {
                String[] authority = secondPart.split(":");
                this.host = authority[0];
                this.port = Integer.parseInt(authority[1]);
            } else {
                URI uri = new URI(secondPart);
                this.host = uri.getHost();
                if (uri.getPort() != -1)
                    this.port = uri.getPort();
            }
        }

        // headers
        for (int h = 1; h < lines.length; h++) {
            String header = lines[h];
            headers.add(header);
        }
        // https://datatracker.ietf.org/doc/html/rfc2616#section-5.1.2
        // here must implement all proxy things
        boolean there_is_host = false;
        ArrayList<Integer> indices = new ArrayList<>();

        for (String h : headers) {
            if (h.contains("Content-Length:")) {
                String byte_num_str = h.replace("Content-Length:", "");
//                byte[] chunk = new byte[1024];
                int byte_num = Integer.parseInt(byte_num_str.trim().strip());
                int counter = 0;
                while (counter != byte_num) {
                    System.out.println(String.format("byte_num : %d , counter : %d , difference : %d", byte_num, counter, byte_num - counter));
                    int difference = byte_num - counter;
                    byte[] chunk;
                    if (difference < 1024)
                        chunk = new byte[difference];
                    else
                        chunk = new byte[1024];
                    counter += this.inputStream.read(chunk, 0, chunk.length);
                    body.addAll(Arrays.asList(ArrayUtils.toObject(chunk)));
                }
            }
            if (h.contains("Transfer-Encoding:")) {
                if (h.contains("chunked")) {
                    while (true) {
                        int b;
                        int chunkSize = 0;
                        StringBuilder hex_str_builder = new StringBuilder();

                        while ((b = this.inputStream.read()) != '\r') {
                            hex_str_builder.append((char) b);
                        }

                        String hex_str = hex_str_builder.toString();
                        chunkSize = Integer.parseInt(hex_str, 16);
//                        System.out.println(String.format("hex: %s , number : %d", hex_str, chunkSize));

                        // Consume the trailing '\n'
                        this.inputStream.read();
                        if (chunkSize == 0) {
                            this.inputStream.read();
                            this.inputStream.read();

                            break;
                        } else {
                            int counter = 0;
                            while (counter != chunkSize) {
//                                System.out.println(String.format("byte_num : %d , counter : %d , difference : %d", chunkSize, counter, chunkSize - counter));
                                int difference = chunkSize - counter;
                                byte[] chunk;
                                if (difference < 1024)
                                    chunk = new byte[difference];
                                else
                                    chunk = new byte[1024];
                                counter += this.inputStream.read(chunk, 0, chunk.length);
                                body.addAll(Arrays.asList(ArrayUtils.toObject(chunk)));
                            }
                            this.inputStream.read();
                            this.inputStream.read();
                        }
                    }
                }
            }

            if (isRequest) {
                if (h.contains("Upgrade-Insecure-Requests"))
                    indices.add(headers.indexOf(h));

                if (h.contains("If-"))
                    indices.add(headers.indexOf(h));

                if (h.contains("Host:"))
                    there_is_host = true;
            }
        }

        if (isRequest) {
            if (!there_is_host) headers.add("Host:");

            for (int x = headers.size() - 1; x > 0; x--) {
                for (int y : indices) {
                    if (x == y)
                        headers.remove(x);
                }
            }
        }

        this.firstLinePart1 = firstPart;
        this.firstLinePart2 = secondPart;
        this.firstLinePart3 = thirdPart;

        this.mainLine = mainLine;
        this.headers = headers;
        this.body = body;

        return 0;
    }

    public byte[] toBytes() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(this.mainLine).append("\r\n");
        for (String h : this.headers)
            stringBuilder.append(h).append("\r\n");
        stringBuilder.append("\r\n");
        ArrayList<Byte> bytes = new ArrayList<>(Arrays.asList(ArrayUtils.toObject(stringBuilder.toString().getBytes())));
        if (this.body.size() != 0)
            bytes.addAll(this.body);
        return ArrayUtils.toPrimitive(bytes.toArray(new Byte[0]));
    }

    public String makeString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(this.mainLine).append("\r\n");
        for (String h : this.headers)
            stringBuilder.append(h).append("\r\n");
        stringBuilder.append("\r\n");
        stringBuilder.append(getBodyAsString());
        return stringBuilder.toString();
    }

    public String getBodyAsString() {
        return new String(ArrayUtils.toPrimitive(this.body.toArray(new Byte[0])), StandardCharsets.UTF_8);
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public String getFirstLinePart1() {
        return firstLinePart1;
    }

    public String getFirstLinePart2() {
        return firstLinePart2;
    }

    public String getFirstLinePart3() {
        return firstLinePart3;
    }

    public boolean isRequest() {
        return isRequest;
    }

    public boolean isResponse() {
        return isResponse;
    }

    public String getStatus() {
        return status;
    }

    public String getPhase() {
        return phase;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getMainLine() {
        return mainLine;
    }

    public ArrayList<String> getHeaders() {
        return headers;
    }

    public ArrayList<Byte> getBody() {
        return body;
    }
}

