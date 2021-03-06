import org.apache.commons.lang3.ArrayUtils;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


public class ProxyServer {
    static DefaultListModel<String> blocked_urls;
    static String padding = "    ";
    static JLabel connections_num_label;
    static JLabel blocked_num_label;
    static JLabel cache_size;
    static DefaultListModel<String> cache_list;
    static volatile AtomicInteger connections;
    static volatile HashMap<String, ArrayList<Byte>> cache;

    static class OptionPaneExample {
        JFrame f;

        OptionPaneExample(String message, String title, int type) {
            f = new JFrame();
            JOptionPane.showMessageDialog(f, message, title, type);
        }
    }

    static void updateStatus() {
        connections_num_label.setText("connections: " + connections.get() + padding);
        blocked_num_label.setText("Blocked:" + blocked_urls.getSize() + padding);
    }

    static void updateCacheView(){
        cache_size.setText("Cached:" + cache.size() + padding);
    }

    public static void main(String[] args) throws Exception {

        blocked_urls = new DefaultListModel<>();
        cache = new HashMap<>();

        int frame_width = 500;
        int frame_height = 300;

        //Creating the Frame
        JFrame frame = new JFrame("Proxy Setting");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(frame_width, frame_height);

        //Creating the MenuBar and adding components
        JMenuBar mb = new JMenuBar();
        JMenu m1 = new JMenu("FILE");
        JMenu m2 = new JMenu("Help");
        mb.add(m1);
        mb.add(m2);
        JMenuItem m11 = new JMenuItem("Open");
        JMenuItem m22 = new JMenuItem("Save as");
        m1.add(m11);
        m1.add(m22);


        JPanel center = new JPanel(new BorderLayout());
        JPanel status_bar = new JPanel(new GridLayout(10, 1));

        JList<String> list = new JList<>(blocked_urls);
        list.setFixedCellWidth(frame_width - 100);
        JScrollPane scrollPane = new JScrollPane(list);

        connections_num_label = new JLabel("connections: " + 0 + padding);
        blocked_num_label = new JLabel("Blocked:" + blocked_urls.getSize() + padding);

//        status_bar.add(connections_num_label);
        status_bar.add(blocked_num_label);

        center.add(scrollPane, BorderLayout.WEST);
        center.add(status_bar, BorderLayout.EAST);


        JPanel panel = new JPanel();
        JLabel label = new JLabel("Enter URL");
        JTextField tf = new JTextField(20);
        JButton block = new JButton("Block");
        block.addActionListener(e -> {
            String url = tf.getText();
            for (int i = 0; i < list.getModel().getSize(); i++)
                if (((String) list.getModel().getElementAt(i)).strip().equals(url)) {
                    new OptionPaneExample("This URL is already blocked", "Alert", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            if (!url.isBlank() || !url.isEmpty()) {
                blocked_urls.addElement(padding + url);
                updateStatus();
            }
        });
        JButton unblock = new JButton("Unblock");
        unblock.addActionListener(e -> {
            List<String> selected = (List<String>) list.getSelectedValuesList();
            for (String s : selected)
                blocked_urls.removeElement(s);
            updateStatus();
        });
        panel.add(label); // Components Added using Flow Layout
        panel.add(tf);
        panel.add(block);
        panel.add(unblock);

        //Adding Components to the frame.
        frame.getContentPane().add(BorderLayout.SOUTH, panel);
        frame.getContentPane().add(BorderLayout.NORTH, mb);
        frame.getContentPane().add(BorderLayout.CENTER, center);
        frame.setVisible(true);




        int cache_frame_width = 700;
        int cache_frame_height = 300;
        //Creating the Frame
        JFrame cache_frame = new JFrame("Cache Setting");
        cache_frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        cache_frame.setSize(cache_frame_width, cache_frame_height);


        cache_list = new DefaultListModel<String>();
        JList<String> cache_list_component = new JList<>(cache_list);
        cache_list_component.setFixedCellWidth(cache_frame_width - 100);
        JScrollPane cache_scrollPane = new JScrollPane(cache_list_component);

        cache_size = new JLabel("Cached:" + cache.size() + padding);


        JPanel cache_panel = new JPanel();
        JButton clearAll = new JButton("clear all");
        clearAll.addActionListener(e -> {
            cache.clear();
            cache_list.clear();
        });
        JButton delete = new JButton("delete");
        delete.addActionListener(e -> {
            List<String> selected = cache_list_component.getSelectedValuesList();
            for (String s : selected) {
                cache.remove(s);
                cache_list.removeElement(s);
            }
        });

        cache_panel.add(clearAll);
        cache_panel.add(delete);

        //Adding Components to the frame.
        cache_frame.getContentPane().add(BorderLayout.SOUTH, cache_panel);
        cache_frame.getContentPane().add(BorderLayout.NORTH, mb);
        cache_frame.getContentPane().add(BorderLayout.CENTER, cache_scrollPane);
        cache_frame.setVisible(true);

        System.out.println("The Proxy server is running.");
        connections = new AtomicInteger(0);
        try (ServerSocket listener = new ServerSocket(9998)) {
            while (true) {
                System.out.println("Waiting for a client to connect...");
                new RequestHandler(listener.accept(), connections.getAndIncrement(), blocked_urls, connections, cache, cache_list).start();
                updateStatus();
            }
        }
    }
}

class RequestHandler extends Thread {
    private final int connection_number;
    private final Socket socket;
    private final DefaultListModel<String> blocked_urls;
    private AtomicInteger connections;
    HashMap<String, ArrayList<Byte>> cache;
    InputStream from_client;
    OutputStream to_client;
    FileWriter log;
    DefaultListModel<String> cache_list;

    public RequestHandler(Socket socket, int connection_number, DefaultListModel<String> blocked_urls, AtomicInteger connections, HashMap<String, ArrayList<Byte>> cache, DefaultListModel<String> cache_list) throws IOException {
        this.cache = cache;
        this.socket = socket;
        this.connection_number = connection_number;
        this.blocked_urls = blocked_urls;
        this.connections = connections;
        this.cache_list = cache_list;

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

    public void print(String message, boolean terminal) throws IOException {
        if (terminal)
            System.out.println(message);
        this.log.write(message);
    }

    public void handleHTTPS(String remote_host, int remote_port) throws IOException {

        Socket remote_socket = new Socket(remote_host, remote_port);
        InputStream from_remote_server = remote_socket.getInputStream();
        OutputStream to_remote_server = remote_socket.getOutputStream();


        String successful_message = "HTTP/1.1 200 OK\r\n\r\n";
        byte[] bytes = successful_message.getBytes();
        this.to_client.write(bytes, 0, bytes.length);

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
            } catch (IOException e) {
                System.out.println("Connection with client #" + connection_number + " closed");
                this.connections.getAndDecrement();
                try {
                    this.log.close();
                    this.socket.close();
                } catch (IOException ignored) {
                }
            }
        }).start();


        final byte[] reply = new byte[1024];
        int response_bytes_read;
        while ((response_bytes_read = from_remote_server.read(reply)) != -1) {
//                this.print(new String(reply, StandardCharsets.UTF_8));
            this.to_client.write(reply, 0, response_bytes_read);
        }
    }

    public void handleHTTP(String remote_host, int remote_port, HttpParser httpParser) throws IOException, InterruptedException, URISyntaxException {

        Socket remote_socket = new Socket(remote_host, remote_port);
        InputStream from_remote_server = remote_socket.getInputStream();
        OutputStream to_remote_server = remote_socket.getOutputStream();

        ArrayList<Byte> message = httpParser.toBytes();
        this.print(httpParser.makeString(), true);

        int chunk_size = 1024;
        for (int i = 0; i < message.size(); i += chunk_size) {
            List<Byte> sublist = message.subList(i, Math.min(i + chunk_size, message.size()));
            byte[] bytes = ArrayUtils.toPrimitive(sublist.toArray(new Byte[0]));
            to_remote_server.write(bytes, 0, bytes.length);
        }

        HttpParser from_server_to_client_msg = new HttpParser(from_remote_server);
        HttpParser from_client_to_server_msg = new HttpParser(this.from_client);
        boolean cache_it = false;

        while (true) {
            if (from_server_to_client_msg.readRequest() != -2) {
                ArrayList<Byte> SC_message = from_server_to_client_msg.toBytes();
                this.print(from_server_to_client_msg.makeString(), true);

                if (!cache.containsKey(httpParser.getFirstLinePart2()) &&
                        Integer.parseInt(from_server_to_client_msg.getFirstLinePart2()) / 100 == 2) {
                    cache.put(httpParser.getFirstLinePart2(), SC_message);
                    cache_list.addElement(httpParser.getFirstLinePart2());
                }

                if (cache_it && Integer.parseInt(from_server_to_client_msg.getFirstLinePart2()) / 100 == 2) {
                    cache.put(from_client_to_server_msg.getFirstLinePart2(), SC_message);
                    cache_list.addElement(from_client_to_server_msg.getFirstLinePart2());
                    cache_it = false;
                }

                for (int i = 0; i < SC_message.size(); i += chunk_size) {
                    List<Byte> sublist = SC_message.subList(i, Math.min(i + chunk_size, SC_message.size()));
                    byte[] bytes = ArrayUtils.toPrimitive(sublist.toArray(new Byte[0]));
                    this.to_client.write(bytes, 0, bytes.length);
                }
            }


            while (true) {
                if (from_client_to_server_msg.readRequest() != -2) {
                    ArrayList<Byte> CS_message = from_client_to_server_msg.toBytes();
                    this.print(from_client_to_server_msg.makeString(), true);
                    String resource = from_client_to_server_msg.getFirstLinePart2();
                    if (cache.containsKey(resource)) {
                        ArrayList<Byte> cached_message = cache.get(resource);
                        for (int i = 0; i < cached_message.size(); i += chunk_size) {
                            List<Byte> sublist = cached_message.subList(i, Math.min(i + chunk_size, cached_message.size()));
                            byte[] bytes = ArrayUtils.toPrimitive(sublist.toArray(new Byte[0]));
                            this.to_client.write(bytes, 0, bytes.length);
                        }
                    } else {
                        cache.put(resource, null);
                        cache_it = true;
                        for (int i = 0; i < CS_message.size(); i += chunk_size) {
                            List<Byte> sublist = CS_message.subList(i, Math.min(i + chunk_size, CS_message.size()));
                            byte[] bytes = ArrayUtils.toPrimitive(sublist.toArray(new Byte[0]));
                            to_remote_server.write(bytes, 0, bytes.length);
                        }
                        break;
                    }
                }
            }
        }
    }

    public String stringify(ArrayList<Byte> bytes) {
        return new String(ArrayUtils.toPrimitive(bytes.toArray(new Byte[0])), StandardCharsets.US_ASCII);
    }

    public void run() {
        try {

            this.print(String.format("Connection number: %d", this.connection_number), true);
            this.print(String.format("Socket: %s", this.socket), true);

            this.from_client = socket.getInputStream();
            this.to_client = socket.getOutputStream();

            HttpParser httpParser = new HttpParser(this.from_client);
            if (httpParser.readRequest() == -1) {
                socket.close();
                log.close();
                return;
            }

            this.print(String.format("Main Line : %s", httpParser.getMainLine()), true);
            this.print(String.format("headers : %s", httpParser.getHeaders()), true);
            this.print(String.format("body : [%s]", httpParser.getBodyAsString()), true);

            // we are sure this is request
            this.print(String.format("Method : %s", httpParser.getFirstLinePart1()), true);
            this.print(String.format("Remote host: %s", httpParser.getHost()), true);
            this.print(String.format("version : %s", httpParser.getFirstLinePart3()), true);
            this.print(String.format("Remote port: %s", httpParser.getPort()), true);

            System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
            System.out.println(this.blocked_urls);
            System.out.println(httpParser.getHost());
            System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");


            if (this.blocked_urls.contains("    " + httpParser.getHost())) {
                String forbidden_message = "HTTP/1.1 403 Forbidden\r\n\r\n";
                byte[] bytes = forbidden_message.getBytes();
                this.to_client.write(bytes, 0, bytes.length);
            } else {
                if (httpParser.getFirstLinePart1().equals("CONNECT"))
                    this.handleHTTPS(httpParser.getHost(), httpParser.getPort());
                else
                    this.handleHTTP(httpParser.getHost(), httpParser.getPort(), httpParser);
            }

            System.out.println("Connection with client #" + connection_number + " closed");
            this.socket.close();
            this.log.close();

        } catch (Exception e) {
            System.out.println("Error handling client #" + connection_number);
            System.out.println(e.getLocalizedMessage());
//            e.printStackTrace();
        } finally {
            connections.getAndDecrement();
        }
    }
}
