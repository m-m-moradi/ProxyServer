import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * A server program which accepts requests from a client to capitalize strings.
 * When a client connects, the client sends in a string and the server sends
 * back the capitalized version of the string.
 *
 * The program is runs in an infinite loop, so shutdown is platform dependent.
 * If you ran it from a console window with the "java" interpreter, Ctrl+C will
 * shut it down.
 */
public class CapitalizerServerSingleThreaded {

    public static void main(String[] args) throws Exception {
        System.out.println("The capitalization server is running.");
        int clientNumber = 0;
        try (ServerSocket listener = new ServerSocket(9998)) {
            while (true) {
                System.out.println("Waiting for a client to connect...");
                Socket socket = listener.accept();
                clientNumber++;
                try {
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

                    // Send a welcome message to the client.
                    out.println("Hello, you are client #" + clientNumber);

                    // Get messages from the client, line by line; return them capitalized
                    while (true) {
                        String input = in.readLine();
                        if (input == null || input.isEmpty()) {
                            break;
                        }
                        if (input.equals("Quit!")) {
                            out.println("Goodbye!");
                            break;
                        }
                        out.println(input.toUpperCase());
                    }
                } catch (IOException e) {
                    System.out.println("Error handling client #" + clientNumber);
                } finally {
                    socket.close();
                    System.out.println("Connection with client #" + clientNumber + " closed");
                }
            }
        }
    }
}
