import org.apache.commons.lang3.ArrayUtils;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
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
                System.out.println(this.connection_number);
                e.printStackTrace();
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
            System.out.println(this.connection_number);
            e.printStackTrace();
        } finally {
            try {
                this.log.close();
                this.socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void handleHTTP(String remote_host, int remote_port, HttpParser httpParser) throws IOException, InterruptedException {


        ArrayList<Byte> monitoring = new ArrayList<>();
        Socket remote_socket = new Socket(remote_host, remote_port);
        InputStream from_remote_server = remote_socket.getInputStream();
        OutputStream to_remote_server = remote_socket.getOutputStream();


        this.print("SENDING: --");
        ArrayList<Byte> message = httpParser.toBytes();
        int chunk_size = 4096;
        for (int i = 0; i < message.size(); i += chunk_size) {
            List<Byte> sublist = message.subList(i, Math.min(i + chunk_size, message.size()));
            byte[] bytes = ArrayUtils.toPrimitive(sublist.toArray(new Byte[0]));
            to_remote_server.write(bytes, 0, bytes.length);
        }


        new Thread(() -> {
            try {
                while (true) {


                    HttpParser from_client_to_server_msg = new HttpParser(this.from_client);
                    if (from_client_to_server_msg.readRequest() > 0) {

//                    this.print("\n[[[Client to Server]]]");
//                    this.print(from_client_to_server_msg.makeString());
//                    this.print("\n");

                        ArrayList<Byte> CS_message = from_client_to_server_msg.toBytes();
                        for (int i = 0; i < CS_message.size(); i += chunk_size) {
                            List<Byte> sublist = CS_message.subList(i, Math.min(i + chunk_size, CS_message.size()));
                            byte[] bytes = ArrayUtils.toPrimitive(sublist.toArray(new Byte[0]));
                            to_remote_server.write(bytes, 0, bytes.length);
                        }
                    }


                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    this.log.close();
                    this.socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();


        Thread.sleep(2000);
        try {
            while (true) {
//                HttpParser from_server_to_client_msg = new HttpParser(from_remote_server);
//                from_server_to_client_msg.readRequest();

//                this.print("\n[[[Server to Client]]]");
//                this.print(from_server_to_client_msg.makeString());
//                this.print("\n");


//                message = from_server_to_client_msg.original();

//                this.to_client.write(ArrayUtils.toPrimitive(message.toArray(new Byte[0])), 0, message.size());

//                for (int i = 0; i < message.size(); i += chunk_size) {
//                    List<Byte> sublist = message.subList(i, Math.min(i + chunk_size, message.size()));
//                    byte[] bytes = ArrayUtils.toPrimitive(sublist.toArray(new Byte[0]));
//                    this.to_client.write(bytes, 0, bytes.length);
//                }

//                byte[][] all = splitArray(ArrayUtils.toPrimitive(message.toArray(new Byte[0])) , message.size());
//                for (byte[] record : all)
//                    this.to_client.write(record, 0, record.length);
                // ------------------


                HttpParser from_server_to_client_msg = new HttpParser(from_remote_server);
                from_server_to_client_msg.readRequest();
//
////                    this.print("\n[[[Server to Client]]]");
////                    this.print(from_server_to_client_msg.makeString());
////                    this.print("\n");
//
                ArrayList<Byte> SC_message = from_server_to_client_msg.toBytes();
//
                for (int i = 0; i < SC_message.size(); i += chunk_size) {
                    List<Byte> sublist = SC_message.subList(i, Math.min(i + chunk_size, SC_message.size()));
                    byte[] bytes = ArrayUtils.toPrimitive(sublist.toArray(new Byte[0]));
                    System.out.println(bytes.length);
                    this.to_client.write(bytes, 0, bytes.length);
                }
//
//                    int[][] all = splitArray(ArrayUtils.toPrimitive(SC_message.toArray(new Integer[0])), 4096);
//                    for (int[] record : all) {
//                        System.out.println(record.length);
////                        System.out.println(new String(record, StandardCharsets.US_ASCII));
//                        this.to_client.write(record, 0, record.length);
//                    }

//                    for (int i=0 ; i< SC_message.size(); i++){
//                        this.to_client.write(SC_message.get(i));
//                    }

//                    byte[] what = ArrayUtils.toPrimitive(SC_message.toArray(new Byte[0]));
//                    this.to_client.write(what, 0, SC_message.size());

                System.out.println("lkasdfl;jsdl;jf;lsadjf;sadjf;lksadjg;olaerhjsfg;lsdrhf;lkasdfhjf'l;hkjrsd");
                break;
            }
//                int b;
//                char c;
//                while ((b = from_remote_server.read()) != -1) {
//                    c = (char) b;
//                    this.to_client.write(b);
//
//                    monitoring.add((byte) c);
//                }


        } catch (IOException | URISyntaxException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            try {
                this.log.close();
                this.socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public static int[][] splitArray(int[] arrayToSplit, int chunkSize) {
        if (chunkSize <= 0) {
            return null;  // just in case :)
        }
        // first we have to check if the array can be split in multiple
        // arrays of equal 'chunk' size
        int rest = arrayToSplit.length % chunkSize;  // if rest>0 then our last array will have less elements than the others
        // then we check in how many arrays we can split our input array
        int chunks = arrayToSplit.length / chunkSize + (rest > 0 ? 1 : 0); // we may have to add an additional array for the 'rest'
        // now we know how many arrays we need and create our result array
        int[][] arrays = new int[chunks][];
        // we create our resulting arrays by copying the corresponding
        // part from the input array. If we have a rest (rest>0), then
        // the last array will have less elements than the others. This
        // needs to be handled separately, so we iterate 1 times less.
        for (int i = 0; i < (rest > 0 ? chunks - 1 : chunks); i++) {
            // this copies 'chunk' times 'chunkSize' elements into a new array
            arrays[i] = Arrays.copyOfRange(arrayToSplit, i * chunkSize, i * chunkSize + chunkSize);
        }
        if (rest > 0) { // only when we have a rest
            // we copy the remaining elements into the last chunk
            arrays[chunks - 1] = Arrays.copyOfRange(arrayToSplit, (chunks - 1) * chunkSize, (chunks - 1) * chunkSize + rest);
        }
        return arrays; // that's it
    }

    public static byte[][] splitArray(byte[] arrayToSplit, int chunkSize) {
        if (chunkSize <= 0) {
            return null;  // just in case :)
        }
        // first we have to check if the array can be split in multiple
        // arrays of equal 'chunk' size
        int rest = arrayToSplit.length % chunkSize;  // if rest>0 then our last array will have less elements than the others
        // then we check in how many arrays we can split our input array
        int chunks = arrayToSplit.length / chunkSize + (rest > 0 ? 1 : 0); // we may have to add an additional array for the 'rest'
        // now we know how many arrays we need and create our result array
        byte[][] arrays = new byte[chunks][];
        // we create our resulting arrays by copying the corresponding
        // part from the input array. If we have a rest (rest>0), then
        // the last array will have less elements than the others. This
        // needs to be handled separately, so we iterate 1 times less.
        for (int i = 0; i < (rest > 0 ? chunks - 1 : chunks); i++) {
            // this copies 'chunk' times 'chunkSize' elements into a new array
            arrays[i] = Arrays.copyOfRange(arrayToSplit, i * chunkSize, i * chunkSize + chunkSize);
        }
        if (rest > 0) { // only when we have a rest
            // we copy the remaining elements into the last chunk
            arrays[chunks - 1] = Arrays.copyOfRange(arrayToSplit, (chunks - 1) * chunkSize, (chunks - 1) * chunkSize + rest);
        }
        return arrays; // that's it
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

            this.print(String.format("Main Line : %s", httpParser.getMainLine()));
            this.print(String.format("headers : %s", httpParser.getHeaders()));
            this.print(String.format("body : [%s]", httpParser.getBodyAsString()));

            // we are sure this is request
            this.print(String.format("Method : %s", httpParser.getFirstLinePart1()));
            this.print(String.format("Remote host: %s", httpParser.getHost()));
            this.print(String.format("version : %s", httpParser.getFirstLinePart3()));
            this.print(String.format("Remote port: %s", httpParser.getPort()));

            if (httpParser.getFirstLinePart1().equals("CONNECT"))
                this.handleHTTPS(httpParser.getHost(), httpParser.getPort());
            else
                this.handleHTTP(httpParser.getHost(), httpParser.getPort(), httpParser);


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
