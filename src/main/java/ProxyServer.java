import org.apache.commons.lang3.ArrayUtils;
import org.w3c.dom.html.HTMLTableCaptionElement;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;


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
    private final int connection_number;
    private final Socket socket;
    InputStream from_client;
    OutputStream to_client;
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

    public void handleHTTPS(String remote_host, int remote_port) throws IOException {

        Socket remote_socket = new Socket(remote_host, remote_port);
        InputStream from_remote_server = remote_socket.getInputStream();
        OutputStream to_remote_server = remote_socket.getOutputStream();

        String successful_message = "HTTP/1.1 200 OK\r\n\r\n";
        byte[] bytes = successful_message.getBytes();
        this.to_client.write(bytes, 0, bytes.length);

        this.print("sent successful message");

        // for reading from client and sending it to remote server asynchronously
        new Thread(() -> {
            System.out.println("sending on " + connection_number);
            int request_bytes_read;
            final byte[] request = new byte[1024];
            try { // sending Http request to remote server
                int b;
                char c;
                while ((b = this.from_client.read()) != -1) {
                    c = (char) b;
                    to_remote_server.write(b);
                }

//                while ((request_bytes_read = this.from_client.read(request)) != -1) { // todo : when is the -1?
////                    this.print(new String(request, StandardCharsets.UTF_8));
//                    to_remote_server.write(request, 0, request_bytes_read);
//                }
            } catch (IOException e) {
                try {
                    e.printStackTrace();
                    this.log.close();
                    this.socket.close();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                    try {
                        this.log.close();
                        this.socket.close();
                    } catch (IOException ee) {
                        ee.printStackTrace();
                    }
                }
            } finally {
                try {
                    this.log.close();
                    this.socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        this.print("receiving");
        final byte[] reply = new byte[1024];
        int response_bytes_read;
        try {
            while ((response_bytes_read = from_remote_server.read(reply)) != -1) {
//                this.print(new String(reply, StandardCharsets.UTF_8));
                this.to_client.write(reply, 0, response_bytes_read);
            }
        } catch (IOException e) {
            try {
                this.print((Arrays.toString(e.getStackTrace())));
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        } finally {
            try {
                this.log.close();
                this.socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void handleHTTP(String remote_host, int remote_port, HttpParser httpParser) throws IOException {

        Socket remote_socket = new Socket(remote_host, remote_port);
        InputStream from_remote_server = remote_socket.getInputStream();
        OutputStream to_remote_server = remote_socket.getOutputStream();

        this.print("SENDING: --");
        int len = httpParser.toBytes().length;
        to_remote_server.write(httpParser.toBytes(), 0, len);

        new Thread(() -> {
            try {
                HttpParser from_client_to_server_msg = new HttpParser(this.from_client);
                while(true){
                    from_client_to_server_msg.readRequest();
                    int msg_len = from_client_to_server_msg.toBytes().length;
                    to_remote_server.write(from_client_to_server_msg.toBytes(),0, msg_len);
                }
//                int request_bytes_read;
//                final byte[] request = new byte[1024];
//                while ((request_bytes_read = this.from_client.read(request)) != -1) { // todo : when is the -1?
//                    this.print(new String(request, StandardCharsets.UTF_8));
//                    to_remote_server.write(request, 0, request_bytes_read);
//                }
            } catch (IOException | URISyntaxException e) {
                try {
                    this.print((Arrays.toString(e.getStackTrace())));
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            } finally {
                try {
                    this.log.close();
                    this.socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        try {
            HttpParser from_server_to_client_msg = new HttpParser(from_remote_server);
            while(true){
                from_server_to_client_msg.readRequest();
                int msg_len = from_server_to_client_msg.toBytes().length;
                this.to_client.write(from_server_to_client_msg.toBytes(), 0, msg_len);
            }
//            final byte[] reply = new byte[1024];
//            int response_bytes_read;
//            while ((response_bytes_read = from_remote_server.read(reply)) != -1) {
//                this.print(new String(reply, StandardCharsets.UTF_8));
//                this.to_client.write(reply, 0, response_bytes_read);
//            }
        } catch (IOException | URISyntaxException e) {
            try {
                this.print((Arrays.toString(e.getStackTrace())));
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        } finally {
            try {
                this.log.close();
                this.socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void run() {
        try {

            this.print(String.format("Connection number: %d", this.connection_number));
            this.print(String.format("Socket: %s", this.socket));

            this.from_client = socket.getInputStream();
            this.to_client = socket.getOutputStream();

            HttpParser httpParser = new HttpParser(this.from_client);
            if (httpParser.readRequest() == -1) {
                socket.close();
                log.close();
                return;
            }

            this.print(String.format("Request Line : %s", httpParser.getRequestLine()));
            this.print(String.format("Method : %s", httpParser.getMethod()));
            this.print(String.format("Remote host: %s", httpParser.getHost()));
            this.print(String.format("Remote port: %s", httpParser.getPort()));
            this.print(String.format("version : %s", httpParser.getVersion()));
            this.print(String.format("headers : %s", httpParser.getHeaders()));
            this.print(String.format("body : %s", httpParser.getBodyAsString()));

            if (httpParser.getMethod().equals("CONNECT"))
                this.handleHTTPS(httpParser.getHost(), httpParser.getPort());
            else
                this.handleHTTP(httpParser.getHost(), httpParser.getPort(), httpParser);

            // if method == connect
            //      handle https
            //      like below
            // else
            //      handle http
            //      send lines + \r\n to remote server
            //      make new thread to handle from sever to client (responses)
            //      in below of that in main thread handle requests


        } catch (Exception e) {
            System.out.println("Error handling client #" + connection_number);
            e.printStackTrace();
        } finally {
            try {
                this.socket.close();
                this.log.close();
            } catch (IOException e) {
            }
            System.out.println("Connection with client #" + connection_number + " closed");
        }
    }
}
