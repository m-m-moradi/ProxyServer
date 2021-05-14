import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) throws Exception {
        System.out.println("Enter the IP address of a machine running the capitalize server:");
        Scanner scanner = new Scanner(System.in);
        String serverAddress = scanner.nextLine();
        Socket socket = new Socket(serverAddress, 9898);

        // Streams for communication with server
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

        // Consume and display welcome message from the server
        System.out.println(in.readLine());

        while (true) {
            System.out.println("\nEnter a string to send to the server (enter \"Quit!\" to quit):");
            String message = scanner.nextLine();
            if (message.equals("Quit!")) {
                out.println("Goodbye!");
                break;
            } else if (message == null || message.isEmpty()) {
                break;
            }
            out.println(message);
            System.out.println(in.readLine());
        }
        System.out.println("Disconnecting from server....");
        scanner.close();
        socket.close();
        System.out.println("Connection closed. Exiting the program.");
    }
}