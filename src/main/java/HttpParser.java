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
    private boolean isChunkEncoding = false;

    // only when isResponse is true
    private String status = null;
    private String phase = null;

    // only when isRequest is true
    private String host = null;
    private int port = 80;

    private String mainLine;
    private ArrayList<String> headers;
    private ArrayList<Byte> body;
    private ArrayList<ArrayList<Byte>> chunked_body;
    private ArrayList<Byte> original_data;


    public HttpParser(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public int readRequest() throws IOException, URISyntaxException, InterruptedException {

        StringBuilder requestBuilder = new StringBuilder();
        StringBuilder lineBuilder = new StringBuilder();
        StringBuilder bodyBuilder = new StringBuilder();
        ArrayList<Character> array_list_char = new ArrayList<Character>();
        ArrayList<Byte> original_data = new ArrayList<>();


        char c1, c2, c3, c4;
        int b1, b2, b3, b4;

        while (true) {
            while ((b1 = this.inputStream.read()) != '\r') {
                if (b1 == -1) return -2;
                c1 = (char) b1;
                array_list_char.add(c1);
                original_data.add((byte) b1);

            }

            original_data.add((byte) '\r');
            original_data.add((byte) '\n');

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
        ArrayList<ArrayList<Byte>> chunked_body = new ArrayList<>();

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
        ArrayList<Integer> request_filtered_indices = new ArrayList<>();
        Integer chunked_header_index = null;
        int content_size = 0;

        for (String h : headers) {
            if (isResponse) {
                if (h.contains("Content-Length:")) {
                    String byte_num_str = h.replace("Content-Length:", "");
//                byte[] chunk = new byte[1024];
                    int byte_num = Integer.parseInt(byte_num_str.trim().strip());
                    int counter = 0;
                    while (counter != byte_num) {
//                    System.out.println(String.format("byte_num : %d , counter : %d , difference : %d", byte_num, counter, byte_num - counter));
                        int difference = byte_num - counter;
                        byte[] chunk;
                        if (difference < 1024)
                            chunk = new byte[difference];
                        else
                            chunk = new byte[1024];

                        counter += this.inputStream.read(chunk, 0, chunk.length);

                        body.addAll(Arrays.asList(ArrayUtils.toObject(chunk)));
                        original_data.addAll(Arrays.asList(ArrayUtils.toObject(chunk)));
                    }
                }
            }
            if (isResponse) {
                if (h.contains("Transfer-Encoding:")) {
                    isChunkEncoding = true;
                    if (h.contains("chunked")) {
//                    int b, c, d, e, f;
//                    while (true) {
//                        while ((b = this.inputStream.read()) != '\r') {
//                            integers_body.add(b);
//                            body.add((byte) b);
//                        }
//
//                        c = this.inputStream.read();
//                        integers_body.add(c);
//                        body.add((byte) c);
//                        if (c != '\n')
//                            continue;
//
//                        d = this.inputStream.read();
//                        integers_body.add(d);
//                        body.add((byte) d);
//                        if (d != '\r')
//                            continue;
//
//                        e = this.inputStream.read();
//                        integers_body.add(e);
//                        body.add((byte) e);
//                        if (e == '\n')
//                            break;
//                        }
                        while (true) {
                            int b;
                            int chunkSize = 0;
                            StringBuilder hex_str_builder = new StringBuilder();

                            while ((b = this.inputStream.read()) != '\r') {
                                hex_str_builder.append((char) b);
                                original_data.add((byte) b);
                            }

                            original_data.add((byte) '\r');
                            original_data.add((byte) '\n');

                            this.inputStream.read(); // Consume the trailing '\n'

                            String hex_str = hex_str_builder.toString();
                            chunkSize = Integer.parseInt(hex_str, 16);
//                        System.out.println(String.format("hex: %s , number : %d", hex_str, chunkSize));

                            if (chunkSize == 0) {

//                            String s = Integer.toHexString(chunkSize);
//                            body.addAll(Arrays.asList(ArrayUtils.toObject(s.getBytes(StandardCharsets.US_ASCII))));
//                            body.add((byte) '\r');
//                            body.add((byte) '\n');
//
//                            body.add((byte) '\r');
//                            body.add((byte) '\n');

                                original_data.add((byte) '\r');
                                original_data.add((byte) '\n');

                                this.inputStream.read();
                                this.inputStream.read();

                                break;
                            } else {

//                            String s = Integer.toHexString(chunkSize);
//                            body.addAll(Arrays.asList(ArrayUtils.toObject(s.getBytes(StandardCharsets.US_ASCII))));
//                            body.add((byte) '\r');
//                            body.add((byte) '\n');

                                int counter = 0;
                                ArrayList<Byte> real_chunk = new ArrayList<>();
                                while (counter != chunkSize) {
//                                System.out.println(String.format("byte_num : %d , counter : %d , difference : %d", chunkSize, counter, chunkSize - counter));
                                    int difference = chunkSize - counter;
                                    byte[] little_chunk;
                                    if (difference < 1024)
                                        little_chunk = new byte[difference];
                                    else
                                        little_chunk = new byte[1024];
                                    counter += this.inputStream.read(little_chunk, 0, little_chunk.length);
                                    content_size += little_chunk.length;
                                    real_chunk.addAll(Arrays.asList(ArrayUtils.toObject(little_chunk)));
                                    original_data.addAll(Arrays.asList(ArrayUtils.toObject(little_chunk)));
                                }
                                chunked_body.add(real_chunk);

                                original_data.add((byte) '\r');
                                original_data.add((byte) '\n');

                                this.inputStream.read();
                                this.inputStream.read();
                            }
                        }
                    }
                }
            }


            if (isRequest) {
                if (h.contains("Upgrade-Insecure-Requests"))
                    request_filtered_indices.add(headers.indexOf(h));

                if (h.contains("If-"))
                    request_filtered_indices.add(headers.indexOf(h));

                if (h.contains("Host:"))
                    there_is_host = true;
            }

            if (isResponse) {
                if (h.contains("Transfer-Encoding:"))
                    if (h.contains("chunked"))
                        chunked_header_index = headers.indexOf(h);
            }
        }
        // end of iteration over headers

        if (isResponse)
            if (isChunkEncoding) {
                if (chunked_header_index != null) {
                    headers.remove((int) chunked_header_index);
                    headers.add("Content-Length: " + content_size);
                }
            }

        if (isRequest) {
            if (!there_is_host) headers.add("Host:");
            for (int x = headers.size() - 1; x > 0; x--) {
                for (int y : request_filtered_indices) {
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
        this.chunked_body = chunked_body;
        this.original_data = original_data;

        if (isChunkEncoding){
            for (ArrayList<Byte> a : chunked_body)
                body.addAll(a);
        }

        return 0;
    }

    public ArrayList<Byte> toBytes() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(this.mainLine).append("\r\n");
        for (String h : this.headers)
            stringBuilder.append(h).append("\r\n");
        stringBuilder.append("\r\n");
        ArrayList<Byte> bytes = new ArrayList<>(Arrays.asList(ArrayUtils.toObject(stringBuilder.toString().getBytes(StandardCharsets.US_ASCII))));
//        if (isChunkEncoding) {
//            for (ArrayList<Byte> chunk : this.chunked_body) {
//                int chunk_size = chunk.size();
//                String s = Integer.toHexString(chunk_size);
//                bytes.addAll(Arrays.asList(ArrayUtils.toObject(s.getBytes(StandardCharsets.US_ASCII))));
//                bytes.add((byte) '\r');
//                bytes.add((byte) '\n');
//                bytes.addAll(chunk);
//                bytes.add((byte) '\r');
//                bytes.add((byte) '\n');
//            }
//            String s = Integer.toHexString(0);
//            bytes.addAll(Arrays.asList(ArrayUtils.toObject(s.getBytes(StandardCharsets.US_ASCII))));
//            bytes.add((byte) '\r');
//            bytes.add((byte) '\n');
//            bytes.add((byte) '\r');
//            bytes.add((byte) '\n');

        if (isChunkEncoding) {
            for (ArrayList<Byte> chunk : this.chunked_body) {
                bytes.addAll(chunk);
            }
        } else {
            if (this.body.size() != 0)
                bytes.addAll(this.body);
        }
//        if (this.body.size() != 0)
//            bytes.addAll(this.body);
//        return ArrayUtils.toPrimitive(bytes.toArray(new Byte[0]));
        return bytes;
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

    public ArrayList<Byte> getOriginal_data() {
        return original_data;
    }
}

