import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


public class ProxyServer {
    public static void main(String[] args) throws Exception {
        System.out.println("The Proxy server is running.");
        int connection_number = 1;
        try (ServerSocket listener = new ServerSocket(9998)) {
            while (true) {
                System.out.println("Waiting for a client to connect...");
                new RequestHandler(listener.accept(), connection_number++).start();
            }
        }
    }
}

class RequestHandler extends Thread {
    private final Socket socket;
    private final int connection_number;
    FileWriter log;

    public RequestHandler(Socket socket, int connection_number) throws IOException {
        this.socket = socket;
        this.connection_number = connection_number;
        System.out.println(String.format("\n----------- [connection: %d | socket: %s] ------------",
                connection_number,
                socket));

        String file_name = String.format("request_%d.txt", this.connection_number);
        String path = "D:" + File.separator + "logs" + File.separator + file_name;
        // Use relative path for Unix systems
        File f = new File(path);
        f.getParentFile().mkdirs();
        f.createNewFile();
        this.log = new FileWriter(f);
    }
    public void print(String message) throws IOException {
        System.out.println(message);
        this.log.write(message);
        this.log.write("\n");
    }

    public void run() {
        try {

            this.print(String.format("Connection number: %d", this.connection_number));
            this.print(String.format("Socket: %s", this.socket));

            InputStream from_client = socket.getInputStream();
            OutputStream to_client = socket.getOutputStream();

            BufferedReader br = new BufferedReader(new InputStreamReader(from_client));
            StringBuilder requestBuilder = new StringBuilder();

            String line;
            Thread.sleep(10);
            while (true) {
                line = br.readLine();
//                if (line == null)
//                    break;
                if (line.isBlank())
                    break;
                requestBuilder.append(line).append("\r\n");
            }

            String http_request = requestBuilder.toString();
            if (http_request.isBlank() || http_request.isEmpty()) {
                socket.close();
                log.close();
                return;
            }

            this.print(String.format("HTTP Request:\n%s", http_request));

            String[] lines = http_request.split("\r\n");
            // request line
            String[] requestLine = lines[0].split(" ");
            String method = requestLine[0];
            String path = requestLine[1]; // todo : this can cause problem
            String version = requestLine[2];

            String remote_host = path;
            if (remote_host.contains("://")){
                int index = remote_host.indexOf("://");
                remote_host = remote_host.substring(index + 3);
            }

            int remote_port = 80;
            String[] remote_host_split_by_colon;
            if (remote_host.contains(":")) {
                remote_host_split_by_colon = remote_host.split(":");
                remote_host = remote_host_split_by_colon[0];
                int len = remote_host_split_by_colon.length;
                String temp_port = remote_host_split_by_colon[1];
                if (StringUtils.isNumeric(temp_port))
                    remote_port = Integer.parseInt(temp_port);
            }

            if (remote_host.contains("/")){
                remote_host = remote_host.replace("/", "");
            }

            // headers
            List<String> headers = new ArrayList<>();
            for (int h = 2; h < lines.length; h++) {
                String header = lines[h];
                headers.add(header);
            }

            this.print("\n");
            this.print(String.format("Method : %s", method));
            this.print(String.format("Path : %s", path));
            this.print(String.format("Remote host: %s", remote_host));
            this.print(String.format("version : %s", version));
            this.print(String.format("headers : %s", headers));


            Socket remote_socket = new Socket(remote_host, remote_port);
            InputStream from_remote_server = remote_socket.getInputStream();
            OutputStream to_remote_server = remote_socket.getOutputStream();


            String successful_message = "HTTP/1.1 200 OK\r\n\r\n";
            byte[] bytes = successful_message.getBytes();
            to_client.write(bytes, 0, bytes.length);

            this.print("sent successful message");

            // for reading from client and sending it to remote server asynchronously
            new Thread(() -> {
                System.out.println("sending on " + connection_number);
                int request_bytes_read;
                final byte[] request = new byte[1024];
                try { // sending Http request to remote server
                    while ((request_bytes_read = from_client.read(request)) != -1) { // todo : when is the -1?
                        this.print(new String(request, StandardCharsets.UTF_8));
                        to_remote_server.write(request, 0, request_bytes_read);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();


            System.out.println("receiving");
            final byte[] reply = new byte[1024];
            int response_bytes_read;
            try {

                while(br.ready()){

                }

                while ((response_bytes_read = from_remote_server.read(reply)) != -1) {
                    this.print(new String(reply, StandardCharsets.UTF_8));
                    to_client.write(reply, 0, response_bytes_read);
                }
            } catch (IOException e) {
            }


        } catch (Exception e) {

            System.out.println("Error handling client #" + connection_number);
            e.printStackTrace();
        } finally {
            try {
                socket.close();
                this.log.close();
            } catch (IOException e) {
            }
            System.out.println("Connection with client #" + connection_number + " closed");
        }
    }
}
